package com.lankapay.split.domain;

import java.time.Instant;

public class SplitParticipant {
    private Long userId;
    private String phone;
    private String displayName;
    private Long shareMinor;
    private ParticipantStatus status;
    private Long paymentRequestId;
    private String paymentRequestReference;
    private String qrReference;
    private String qrPayload;
    private Long transferId;
    private Instant paidAt;
    private Instant lastRemindedAt;

    public SplitParticipant() {
        this.status = ParticipantStatus.INVITED;
    }

    public SplitParticipant(Long userId, String phone, String displayName, Long shareMinor) {
        this.userId = userId;
        this.phone = phone;
        this.displayName = displayName;
        this.shareMinor = shareMinor;
        this.status = ParticipantStatus.INVITED;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Long getShareMinor() {
        return shareMinor;
    }

    public void setShareMinor(Long shareMinor) {
        this.shareMinor = shareMinor;
    }

    public ParticipantStatus getStatus() {
        return status;
    }

    public void setStatus(ParticipantStatus status) {
        this.status = status;
    }

    public Long getPaymentRequestId() {
        return paymentRequestId;
    }

    public void setPaymentRequestId(Long paymentRequestId) {
        this.paymentRequestId = paymentRequestId;
    }

    public String getPaymentRequestReference() {
        return paymentRequestReference;
    }

    public void setPaymentRequestReference(String paymentRequestReference) {
        this.paymentRequestReference = paymentRequestReference;
    }

    public String getQrReference() {
        return qrReference;
    }

    public void setQrReference(String qrReference) {
        this.qrReference = qrReference;
    }

    public String getQrPayload() {
        return qrPayload;
    }

    public void setQrPayload(String qrPayload) {
        this.qrPayload = qrPayload;
    }

    public Long getTransferId() {
        return transferId;
    }

    public void setTransferId(Long transferId) {
        this.transferId = transferId;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public Instant getLastRemindedAt() {
        return lastRemindedAt;
    }

    public void setLastRemindedAt(Instant lastRemindedAt) {
        this.lastRemindedAt = lastRemindedAt;
    }

    public boolean isPaid() {
        return status == ParticipantStatus.PAID;
    }
}
