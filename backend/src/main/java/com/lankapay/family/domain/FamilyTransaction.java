package com.lankapay.family.domain;

import java.time.Instant;

/**
 * Append-only ledger transaction for family member simulated activity.
 */
public class FamilyTransaction {

    private String id;
    private String memberId;
    private Long guardianUserId;
    private FamilyTransactionType type;
    private long amountMinor;
    private long balanceAfterMinor;
    private String category;
    private String merchantOrSource;
    private String note;
    private FamilyTransactionStatus status;
    private String declineReason;
    private Instant timestamp;

    public FamilyTransaction() {}

    public FamilyTransaction(String id, String memberId, Long guardianUserId,
                             FamilyTransactionType type, long amountMinor,
                             long balanceAfterMinor, String category,
                             String merchantOrSource, String note,
                             FamilyTransactionStatus status, String declineReason,
                             Instant timestamp) {
        this.id = id;
        this.memberId = memberId;
        this.guardianUserId = guardianUserId;
        this.type = type;
        this.amountMinor = amountMinor;
        this.balanceAfterMinor = balanceAfterMinor;
        this.category = category;
        this.merchantOrSource = merchantOrSource;
        this.note = note;
        this.status = status;
        this.declineReason = declineReason;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public Long getGuardianUserId() {
        return guardianUserId;
    }

    public void setGuardianUserId(Long guardianUserId) {
        this.guardianUserId = guardianUserId;
    }

    public FamilyTransactionType getType() {
        return type;
    }

    public void setType(FamilyTransactionType type) {
        this.type = type;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(long amountMinor) {
        this.amountMinor = amountMinor;
    }

    public long getBalanceAfterMinor() {
        return balanceAfterMinor;
    }

    public void setBalanceAfterMinor(long balanceAfterMinor) {
        this.balanceAfterMinor = balanceAfterMinor;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMerchantOrSource() {
        return merchantOrSource;
    }

    public void setMerchantOrSource(String merchantOrSource) {
        this.merchantOrSource = merchantOrSource;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public FamilyTransactionStatus getStatus() {
        return status;
    }

    public void setStatus(FamilyTransactionStatus status) {
        this.status = status;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public void setDeclineReason(String declineReason) {
        this.declineReason = declineReason;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
