package com.lankapay.bank.service;

import com.lankapay.audit.service.AuditService;
import com.lankapay.bank.adapter.AccountOwnershipRequest;
import com.lankapay.bank.adapter.AdapterResponse;
import com.lankapay.bank.adapter.BankAdapterRegistry;
import com.lankapay.bank.crypto.AccountReferenceEncryptor;
import com.lankapay.bank.domain.*;
import com.lankapay.bank.repository.AccountConsentRepository;
import com.lankapay.bank.repository.BankAccountRepository;
import com.lankapay.bank.repository.BankInstitutionRepository;
import com.lankapay.common.exception.AuthorizationException;
import com.lankapay.common.exception.ConflictException;
import com.lankapay.common.exception.ResourceNotFoundException;
import com.lankapay.common.exception.ValidationException;
import com.lankapay.identity.domain.User;
import com.lankapay.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Core business logic for bank account management.
 *
 * Security rules enforced here:
 * - Object-level authorization: every mutating operation verifies the account
 *   belongs to the requesting user before proceeding.
 * - Plaintext account numbers are NEVER stored or logged.
 * - encryptedAccountReference is decrypted only when needed for adapter calls.
 * - Consent records are written before each significant action.
 * - Only one default account per user is enforced atomically.
 * - Removing an account sets status=REMOVED (soft-delete); pending transactions
 *   are preserved by design (FK still valid, transfer references still resolve).
 */
@Service
public class BankAccountService {

    private static final Logger log = LoggerFactory.getLogger(BankAccountService.class);

    private final BankAccountRepository     accountRepository;
    private final BankInstitutionRepository institutionRepository;
    private final AccountConsentRepository  consentRepository;
    private final UserRepository            userRepository;
    private final BankAdapterRegistry       adapterRegistry;
    private final AccountReferenceEncryptor encryptor;
    private final AuditService              auditService;

    public BankAccountService(
            BankAccountRepository accountRepository,
            BankInstitutionRepository institutionRepository,
            AccountConsentRepository consentRepository,
            UserRepository userRepository,
            BankAdapterRegistry adapterRegistry,
            AccountReferenceEncryptor encryptor,
            AuditService auditService) {

        this.accountRepository    = accountRepository;
        this.institutionRepository = institutionRepository;
        this.consentRepository    = consentRepository;
        this.userRepository       = userRepository;
        this.adapterRegistry      = adapterRegistry;
        this.encryptor            = encryptor;
        this.auditService         = auditService;
    }

    // ── Link account ──────────────────────────────────────────

    /**
     * Link a new bank account after ownership verification and explicit consent.
     *
     * Steps:
     *  1. Validate institution is active and supported.
     *  2. Compute SHA-256 hash; reject duplicates.
     *  3. Call BankAdapter.verifyOwnership — fail fast on mismatch.
     *  4. Encrypt account reference at rest.
     *  5. Persist account (status=ACTIVE).
     *  6. Record LINK_ACCOUNT consent.
     *  7. Audit BANK_ACCOUNT_LINKED.
     *
     * @param userId           Authenticated user's ID
     * @param institutionCode  e.g. "SIMULATED_BANK_A"
     * @param accountNumber    Plaintext account number — never stored
     * @param accountName      Account holder name for ownership check
     * @param label            Optional user-assigned label
     * @param consentGiven     Must be true; caller attests user saw and accepted consent text
     * @param ipAddress        Caller IP for audit + consent record
     * @return                 The saved BankAccount (encryptedAccountReference excluded)
     */
    @Transactional
    public BankAccount linkAccount(Long userId, String institutionCode,
                                   String accountNumber, String accountName,
                                   String label, boolean consentGiven,
                                   String ipAddress) {

        if (!consentGiven) {
            throw new ValidationException("CONSENT_REQUIRED",
                    "Explicit consent is required to link a bank account");
        }

        User user = requireUser(userId);

        // 1. Validate institution
        institutionRepository.findByBankCodeAndActiveTrue(institutionCode)
                .orElseThrow(() -> new ResourceNotFoundException("INSTITUTION_NOT_FOUND",
                        "Institution not found or inactive: " + institutionCode));

        // 2. Duplicate check via SHA-256 hash
        String hash = sha256(institutionCode + accountNumber);
        if (accountRepository.findByAccountNumberHash(hash).isPresent()) {
            throw new ConflictException("ACCOUNT_ALREADY_LINKED",
                    "This bank account is already linked");
        }

        // 3. Ownership verification via adapter
        String opRef = UUID.randomUUID().toString();
        AdapterResponse verifyResp = adapterRegistry.get(institutionCode)
                .verifyOwnership(new AccountOwnershipRequest(
                        institutionCode, accountNumber, accountName, opRef));

        if (!verifyResp.success()) {
            auditService.log(userId, "BANK_ACCOUNT_LINK_FAILED", "BANK_ACCOUNT",
                    null,
                    "{\"institutionCode\":\"" + institutionCode
                    + "\",\"reason\":\"" + verifyResp.errorCode() + "\"}",
                    ipAddress);
            throw new ValidationException("OWNERSHIP_VERIFICATION_FAILED",
                    "Bank could not verify account ownership: "
                    + verifyResp.errorMessage());
        }

        // 4. Encrypt account reference
        String encrypted = encryptor.encrypt(accountNumber);

        // 5. Determine masked number (last 4 digits)
        String masked = "****" + accountNumber.substring(
                Math.max(0, accountNumber.length() - 4));

        // 6. Persist
        BankAccount account = new BankAccount(
                user, institutionCode, hash, masked, accountName, encrypted);
        if (label != null && !label.isBlank()) {
            account.setLabel(label.trim());
        }
        BankAccount saved = accountRepository.save(account);

        // 7. Consent record
        consentRepository.save(new AccountConsent(
                user, saved, ConsentType.LINK_ACCOUNT, true, ipAddress));

        // 8. Audit
        auditService.log(userId, "BANK_ACCOUNT_LINKED", "BANK_ACCOUNT",
                saved.getId(),
                "{\"institutionCode\":\"" + institutionCode
                + "\",\"maskedNumber\":\"" + masked
                + "\",\"simulated\":" + verifyResp.simulated() + "}",
                ipAddress);

        log.info("Bank account linked userId={} maskedNumber={} institution={}",
                userId, masked, institutionCode);
        return saved;
    }

    // ── List accounts ─────────────────────────────────────────

    /**
     * Return all bank accounts for the authenticated user (all statuses).
     */
    @Transactional(readOnly = true)
    public List<BankAccount> listAccounts(Long userId) {
        return accountRepository.findByUserIdOrderByLinkedAtDesc(userId);
    }

    // ── Get single account (with ownership check) ─────────────

    @Transactional(readOnly = true)
    public BankAccount getAccount(Long accountId, Long userId) {
        return requireOwnedAccount(accountId, userId);
    }

    // ── Patch account ─────────────────────────────────────────

    /**
     * Update mutable account metadata: label, discoverable, receivingEnabled.
     * Each changed field is audited individually.
     */
    @Transactional
    public BankAccount patchAccount(Long accountId, Long userId,
                                    String label, Boolean discoverable,
                                    Boolean receivingEnabled, String ipAddress) {

        BankAccount account = requireOwnedActiveAccount(accountId, userId);
        boolean changed = false;

        if (label != null) {
            account.setLabel(label.isBlank() ? null : label.trim());
            changed = true;
        }
        if (discoverable != null) {
            account.setDiscoverable(discoverable);
            changed = true;
        }
        if (receivingEnabled != null) {
            account.setReceivingEnabled(receivingEnabled);
            // Record consent when enabling receive
            if (receivingEnabled) {
                consentRepository.save(new AccountConsent(
                        account.getUser(), account,
                        ConsentType.RECEIVE_FUNDS, true, ipAddress));
            }
            changed = true;
        }

        if (!changed) {
            return account; // nothing to update
        }

        BankAccount saved = accountRepository.save(account);

        auditService.log(userId, "BANK_ACCOUNT_UPDATED", "BANK_ACCOUNT",
                accountId,
                "{\"label\":" + jsonStr(account.getLabel())
                + ",\"discoverable\":" + account.isDiscoverable()
                + ",\"receivingEnabled\":" + account.isReceivingEnabled() + "}",
                ipAddress);

        return saved;
    }

    // ── Set default ───────────────────────────────────────────

    /**
     * Set this account as the user's default payment account.
     * Atomically clears the flag on all other accounts first.
     * Only ACTIVE accounts can be made default.
     */
    @Transactional
    public BankAccount setDefault(Long accountId, Long userId, String ipAddress) {
        BankAccount account = requireOwnedActiveAccount(accountId, userId);

        // Consent record
        consentRepository.save(new AccountConsent(
                account.getUser(), account,
                ConsentType.SET_DEFAULT, true, ipAddress));

        // Clear all existing defaults then set the new one
        accountRepository.clearDefaultForUser(userId);
        account.setDefault(true);
        BankAccount saved = accountRepository.save(account);

        auditService.log(userId, "BANK_ACCOUNT_SET_DEFAULT", "BANK_ACCOUNT",
                accountId, "{\"maskedNumber\":\"" + account.getMaskedNumber() + "\"}",
                ipAddress);

        log.info("Default account set userId={} accountId={}", userId, accountId);
        return saved;
    }

    // ── Remove account ────────────────────────────────────────

    /**
     * Soft-delete (revoke) a bank account.
     *
     * Rules:
     * - Status is set to REMOVED — never physically deleted (pending transfers preserved).
     * - If this was the default, the default flag is cleared (no automatic re-assignment).
     * - receivingEnabled is forced to false to block new routing.
     * - Consent record written; audit logged.
     */
    @Transactional
    public void removeAccount(Long accountId, Long userId,
                              boolean consentGiven, String ipAddress) {

        if (!consentGiven) {
            throw new ValidationException("CONSENT_REQUIRED",
                    "Explicit consent is required to remove a bank account");
        }

        BankAccount account = requireOwnedAccount(accountId, userId);

        if (account.getStatus() == BankAccountStatus.REMOVED) {
            throw new ConflictException("ACCOUNT_ALREADY_REMOVED",
                    "This bank account has already been removed");
        }

        account.setStatus(BankAccountStatus.REMOVED);
        account.setReceivingEnabled(false);   // block new routing
        account.setDefault(false);            // clear default if it was set
        accountRepository.save(account);

        consentRepository.save(new AccountConsent(
                account.getUser(), account,
                ConsentType.REMOVE_ACCOUNT, true, ipAddress));

        auditService.log(userId, "BANK_ACCOUNT_REMOVED", "BANK_ACCOUNT",
                accountId,
                "{\"maskedNumber\":\"" + account.getMaskedNumber() + "\"}",
                ipAddress);

        log.info("Bank account removed userId={} accountId={}", userId, accountId);
    }

    // ── Routing helper used by transfer module ─────────────────

    /**
     * Return an account that is eligible to receive funds.
     * Throws if the account is REMOVED or receivingEnabled=false.
     *
     * This is the public entry point for the transfer module to verify
     * a destination account before initiating a debit/credit.
     */
    @Transactional(readOnly = true)
    public BankAccount requireReceivableAccount(Long accountId, Long userId) {
        BankAccount account = requireOwnedAccount(accountId, userId);
        if (!account.canReceive()) {
            throw new ValidationException("ACCOUNT_CANNOT_RECEIVE",
                    "This bank account cannot receive transfers. "
                    + "It may be removed or have receiving disabled.");
        }
        return account;
    }

    // ── Helpers ───────────────────────────────────────────────

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER", userId));
    }

    /**
     * Load account and enforce object-level ownership.
     * Returns 404 for unknown IDs and 403 for accounts owned by another user
     * — both errors look identical to the caller to prevent enumeration.
     */
    private BankAccount requireOwnedAccount(Long accountId, Long userId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new AuthorizationException("BANK_ACCOUNT_ACCESS_DENIED",
                        "Bank account not found or does not belong to this user"));
    }

    private BankAccount requireOwnedActiveAccount(Long accountId, Long userId) {
        BankAccount account = requireOwnedAccount(accountId, userId);
        if (!account.isActive()) {
            throw new ValidationException("ACCOUNT_NOT_ACTIVE",
                    "This bank account is no longer active");
        }
        return account;
    }

    /**
     * SHA-256(input), Base64-URL encoded — used for account number hashing.
     * Matches the scheme used in the existing entity.
     */
    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String jsonStr(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
