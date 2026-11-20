package com.lankapay.bank.domain;

import com.lankapay.identity.domain.User;
import java.time.Instant;

/**
 * Domain entity for bnk_bank_accounts.
 *
 * Security rules:
 * - accountNumberHash:          SHA-256(bankCode + accountNumber) — for duplicate detection.
 *                               Plaintext account number is NEVER stored.
 * - maskedNumber:               Last 4 digits only, e.g. "****7890". Safe to log and display.
 * - encryptedAccountReference:  AES-GCM encrypted bank reference stored at rest.
 *                               Decrypted in memory only when needed for adapter calls.
 *                               NEVER logged or returned in API responses.
 * - version:                    Optimistic locking / concurrency control.
 */
public class BankAccount {

    private Long id;

    private User user;

    private String bankCode;

    private String accountNumberHash;

    /** Last 4 digits only — safe to display and log. */
    private String maskedNumber;

    private String accountName;

    /** Optional user-assigned label, e.g. "My Salary Account". */
    private String label;

    /** Whether this account appears in the payment registry for lookup. Default true. */
    private boolean discoverable = true;

    /** Whether incoming transfers to this account are permitted. Default true. */
    private boolean receivingEnabled = true;

    /**
     * AES-GCM encrypted bank-side account reference (e.g. account number or token).
     * NEVER log or return this value. Decrypt only in memory for adapter calls.
     */
    private String encryptedAccountReference;

    private BankAccountStatus status = BankAccountStatus.ACTIVE;

    private boolean isDefault = false;

    private Long version;

    private Instant linkedAt = Instant.now();

    private Instant updatedAt = Instant.now();

    public BankAccount() {}

    public BankAccount(User user, String bankCode, String accountNumberHash,
                       String maskedNumber, String accountName,
                       String encryptedAccountReference) {
        this.user                       = user;
        this.bankCode                   = bankCode;
        this.accountNumberHash          = accountNumberHash;
        this.maskedNumber               = maskedNumber;
        this.accountName                = accountName;
        this.encryptedAccountReference  = encryptedAccountReference;
    }

    // ── Getters ────────────────────────────────────────────────

    public Long getId()                           { return id; }
    public User getUser()                         { return user; }
    public String getBankCode()                   { return bankCode; }
    public String getAccountNumberHash()          { return accountNumberHash; }
    public String getMaskedNumber()               { return maskedNumber; }
    public String getAccountName()                { return accountName; }
    public String getLabel()                      { return label; }
    public boolean isDiscoverable()               { return discoverable; }
    public boolean isReceivingEnabled()           { return receivingEnabled; }
    public String getEncryptedAccountReference()  { return encryptedAccountReference; }
    public BankAccountStatus getStatus()          { return status; }
    public boolean isDefault()                    { return isDefault; }
    public Long getVersion()                      { return version; }
    public Instant getLinkedAt()                  { return linkedAt; }
    public Instant getUpdatedAt()                 { return updatedAt; }

    // ── Setters ────────────────────────────────────────────────

    public void setStatus(BankAccountStatus status)   { this.status = status; }
    public void setDefault(boolean isDefault)         { this.isDefault = isDefault; }
    public void setAccountName(String accountName)    { this.accountName = accountName; }
    public void setLabel(String label)                { this.label = label; }
    public void setDiscoverable(boolean discoverable) { this.discoverable = discoverable; }
    public void setReceivingEnabled(boolean enabled)  { this.receivingEnabled = enabled; }
    public void setId(Long id)                        { this.id = id; }
    public void setLinkedAt(Instant linkedAt)          { this.linkedAt = linkedAt; }
    public void setUpdatedAt(Instant updatedAt)        { this.updatedAt = updatedAt; }

    // ── Business helpers ───────────────────────────────────────

    /** True only when the account is ACTIVE and receiving is enabled. */
    public boolean canReceive() {
        return status == BankAccountStatus.ACTIVE && receivingEnabled;
    }

    public boolean isActive() { return status == BankAccountStatus.ACTIVE; }
}
