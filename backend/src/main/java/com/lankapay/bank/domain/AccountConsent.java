package com.lankapay.bank.domain;

import com.lankapay.identity.domain.User;
import java.time.Instant;

/**
 * Domain entity for bnk_account_consents.
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
public class AccountConsent {

    private Long id;

    private User user;

    private BankAccount bankAccount;

    private ConsentType consentType;

    private boolean consentGiven;

    /** IPv4 or IPv6 address of the device that submitted the consent. */
    private String consentIp;

    private Instant consentedAt = Instant.now();

    public AccountConsent() {}

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
