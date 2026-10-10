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
 * - accountNumberHash stores SHA-256(bankCode + accountNumber). Plaintext never persisted.
 * - maskedNumber stores last 4 digits only (e.g. ****7890).
 * - @Version enables optimistic locking to prevent concurrent balance / status changes.
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

    /** References bnk_bank_institutions.bank_code. */
    @Column(name = "bank_code", nullable = false, length = 50)
    private String bankCode;

    /** SHA-256(bankCode + accountNumber). Unique constraint prevents duplicates. */
    @Column(name = "account_number_hash", nullable = false, unique = true, length = 255)
    private String accountNumberHash;

    /** Last 4 digits only, e.g. ****7890. Safe to display in UI and logs. */
    @Column(name = "masked_number", nullable = false, length = 10)
    private String maskedNumber;

    @Column(name = "account_name", nullable = false, length = 100)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BankAccountStatus status = BankAccountStatus.ACTIVE;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    /** Optimistic locking – prevents lost updates on concurrent requests. */
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
                       String maskedNumber, String accountName) {
        this.user = user;
        this.bankCode = bankCode;
        this.accountNumberHash = accountNumberHash;
        this.maskedNumber = maskedNumber;
        this.accountName = accountName;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getBankCode() { return bankCode; }
    public String getAccountNumberHash() { return accountNumberHash; }
    public String getMaskedNumber() { return maskedNumber; }
    public String getAccountName() { return accountName; }
    public BankAccountStatus getStatus() { return status; }
    public boolean isDefault() { return isDefault; }
    public Long getVersion() { return version; }
    public Instant getLinkedAt() { return linkedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(BankAccountStatus status) { this.status = status; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
}
