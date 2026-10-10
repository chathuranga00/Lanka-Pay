package com.lankapay.bank.domain;

import com.lankapay.identity.domain.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * JPA entity for bnk_bank_accounts.
 *
 * Security rules:
 * - accountNumberHash:          SHA-256(bankCode + accountNumber) — for duplicate detection.
 *                               Plaintext account number is NEVER stored.
 * - maskedNumber:               Last 4 digits only, e.g. "****7890". Safe to log and display.
 * - encryptedAccountReference:  AES-GCM encrypted bank reference stored at rest.
 *                               Decrypted in memory only when needed for adapter calls.
 *                               NEVER logged or returned in API responses.
 * - @Version:                   Optimistic locking — prevents lost updates under concurrency.
 */
@Entity
@Table(name = "bnk_bank_accounts",
       indexes = @Index(name = "idx_bnk_bank_accounts_user", columnList = "user_id, status"))
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "bank_code", nullable = false, length = 50)
    private String bankCode;

    @Column(name = "account_number_hash", nullable = false, unique = true, length = 255)
    private String accountNumberHash;

    /** Last 4 digits only — safe to display and log. */
    @Column(name = "masked_number", nullable = false, length = 10)
    private String maskedNumber;

    @Column(name = "account_name", nullable = false, length = 100)
    private String accountName;

    /** Optional user-assigned label, e.g. "My Salary Account". */
    @Column(name = "label", length = 80)
    private String label;

    /** Whether this account appears in the payment registry for lookup. Default true. */
    @Column(name = "discoverable", nullable = false)
    private boolean discoverable = true;

    /** Whether incoming transfers to this account are permitted. Default true. */
    @Column(name = "receiving_enabled", nullable = false)
    private boolean receivingEnabled = true;

    /**
     * AES-GCM encrypted bank-side account reference (e.g. account number or token).
     * NEVER log or return this value. Decrypt only in memory for adapter calls.
     */
    @Column(name = "encrypted_account_reference", columnDefinition = "TEXT")
    private String encryptedAccountReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BankAccountStatus status = BankAccountStatus.ACTIVE;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BankAccount() {}

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

    // ── Business helpers ───────────────────────────────────────

    /** True only when the account is ACTIVE and receiving is enabled. */
    public boolean canReceive() {
        return status == BankAccountStatus.ACTIVE && receivingEnabled;
    }

    public boolean isActive() { return status == BankAccountStatus.ACTIVE; }
}
