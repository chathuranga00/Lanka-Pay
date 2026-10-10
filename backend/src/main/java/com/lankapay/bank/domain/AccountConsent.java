package com.lankapay.bank.domain;

import com.lankapay.identity.domain.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * JPA entity for bnk_account_consents.
 *
 * Records an explicit user consent for a specific action on a bank account.
 * Consent rows are append-only — never updated after creation.
 *
 * A consent record is required before:
 *   - Linking a new bank account (LINK_ACCOUNT)
 *   - Removing a bank account (REMOVE_ACCOUNT)
 *   - Setting an account as the payment default (SET_DEFAULT)
 *   - Enabling incoming transfers (RECEIVE_FUNDS)
 */
@Entity
@Table(name = "bnk_account_consents",
       indexes = {
           @Index(name = "idx_bnk_consents_account", columnList = "bank_account_id"),
           @Index(name = "idx_bnk_consents_user",    columnList = "user_id")
       })
public class AccountConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_account_id", nullable = false)
    private BankAccount bankAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 40)
    private ConsentType consentType;

    @Column(name = "consent_given", nullable = false)
    private boolean consentGiven;

    /** IPv4 or IPv6 address of the device that submitted the consent. */
    @Column(name = "consent_ip", length = 45)
    private String consentIp;

    @CreationTimestamp
    @Column(name = "consented_at", nullable = false, updatable = false)
    private Instant consentedAt;

    protected AccountConsent() {}

    public AccountConsent(User user, BankAccount bankAccount,
                          ConsentType consentType, boolean consentGiven, String consentIp) {
        this.user        = user;
        this.bankAccount = bankAccount;
        this.consentType = consentType;
        this.consentGiven = consentGiven;
        this.consentIp   = consentIp;
    }

    public Long getId()               { return id; }
    public User getUser()             { return user; }
    public BankAccount getBankAccount(){ return bankAccount; }
    public ConsentType getConsentType(){ return consentType; }
    public boolean isConsentGiven()   { return consentGiven; }
    public String getConsentIp()      { return consentIp; }
    public Instant getConsentedAt()   { return consentedAt; }
}
