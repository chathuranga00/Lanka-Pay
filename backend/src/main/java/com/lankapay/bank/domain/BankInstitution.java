package com.lankapay.bank.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * JPA entity for bnk_bank_institutions.
 * Seeded by V2 migration with 3 SIMULATED banks.
 */
@Entity
@Table(name = "bnk_bank_institutions")
public class BankInstitution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** e.g. SIMULATED_BANK_A. Always prefixed SIMULATED_ in this prototype. */
    @Column(name = "bank_code", nullable = false, unique = true, length = 50)
    private String bankCode;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    /** Always true in this prototype. */
    @Column(nullable = false)
    private boolean simulated = true;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public BankInstitution() {}

    public BankInstitution(String bankCode, String displayName, boolean simulated, boolean active) {
        this.bankCode = bankCode;
        this.displayName = displayName;
        this.simulated = simulated;
        this.active = active;
    }

    public Long getId() { return id; }
    public String getBankCode() { return bankCode; }
    public String getDisplayName() { return displayName; }
    public boolean isSimulated() { return simulated; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setActive(boolean active) { this.active = active; }
}

