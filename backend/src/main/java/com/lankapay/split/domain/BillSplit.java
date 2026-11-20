package com.lankapay.split.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BillSplit {
    private String id;
    private Long ownerUserId;
    private String ownerName;
    private String title;
    private Long totalMinor;
    private Long ownerShareMinor;
    private String currency = "LKR";
    private List<SplitParticipant> participants = new ArrayList<>();
    private Instant expiresAt;
    private SplitStatus status = SplitStatus.ACTIVE;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public BillSplit() {}

    public BillSplit(String id, Long ownerUserId, String ownerName, String title, Long totalMinor, Long ownerShareMinor, Instant expiresAt) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.ownerName = ownerName;
        this.title = title;
        this.totalMinor = totalMinor;
        this.ownerShareMinor = ownerShareMinor;
        this.expiresAt = expiresAt;
        this.status = SplitStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getTotalMinor() {
        return totalMinor;
    }

    public void setTotalMinor(Long totalMinor) {
        this.totalMinor = totalMinor;
    }

    public Long getOwnerShareMinor() {
        return ownerShareMinor;
    }

    public void setOwnerShareMinor(Long ownerShareMinor) {
        this.ownerShareMinor = ownerShareMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public List<SplitParticipant> getParticipants() {
        return participants;
    }

    public void setParticipants(List<SplitParticipant> participants) {
        this.participants = participants;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public SplitStatus getStatus() {
        return status;
    }

    public void setStatus(SplitStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public int getPaidCount() {
        if (participants == null) return 0;
        return (int) participants.stream().filter(p -> p.getStatus() == ParticipantStatus.PAID).count();
    }

    public int getTotalParticipantsCount() {
        return participants != null ? participants.size() : 0;
    }

    public long getCollectedMinor() {
        if (participants == null) return 0L;
        return participants.stream()
                .filter(p -> p.getStatus() == ParticipantStatus.PAID)
                .mapToLong(SplitParticipant::getShareMinor)
                .sum();
    }

    public void checkAndUpdateStatus() {
        if (status == SplitStatus.CANCELLED) return;
        if (isExpired()) {
            this.status = SplitStatus.EXPIRED;
            if (participants != null) {
                for (var p : participants) {
                    if (p.getStatus() == ParticipantStatus.INVITED) {
                        p.setStatus(ParticipantStatus.EXPIRED);
                    }
                }
            }
            return;
        }

        if (participants != null && !participants.isEmpty()) {
            boolean allPaid = participants.stream().allMatch(p -> p.getStatus() == ParticipantStatus.PAID);
            if (allPaid) {
                this.status = SplitStatus.COMPLETED;
            }
        }
    }
}
