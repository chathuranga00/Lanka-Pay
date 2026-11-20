package com.lankapay.family.store;

import com.lankapay.family.domain.FamilyMember;
import com.lankapay.family.domain.FamilyMemberStatus;
import com.lankapay.family.domain.FamilyStore;
import com.lankapay.family.domain.FamilyTransaction;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * In-memory thread-safe implementation of FamilyStore.
 */
@Repository
public class InMemoryFamilyStore implements FamilyStore {

    private final Map<String, FamilyMember> members = new ConcurrentHashMap<>();
    private final Map<String, List<FamilyTransaction>> transactions = new ConcurrentHashMap<>();

    @Override
    public FamilyMember save(FamilyMember member) {
        if (member == null || member.getId() == null) {
            throw new IllegalArgumentException("FamilyMember and its id must not be null");
        }
        members.put(member.getId(), member);
        return member;
    }

    @Override
    public Optional<FamilyMember> findById(String id) {
        if (id == null) return Optional.empty();
        FamilyMember member = members.get(id);
        if (member != null && member.getStatus() == FamilyMemberStatus.ACTIVE) {
            return Optional.of(member);
        }
        return Optional.empty();
    }

    @Override
    public List<FamilyMember> findByGuardianUserId(Long guardianUserId) {
        if (guardianUserId == null) return Collections.emptyList();
        return members.values().stream()
                .filter(m -> guardianUserId.equals(m.getGuardianUserId()))
                .filter(m -> m.getStatus() == FamilyMemberStatus.ACTIVE)
                .sorted(Comparator.comparing(FamilyMember::getCreatedAt))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String id) {
        if (id == null) return;
        // Data minimisation & retention enforcement:
        // Purge member data and transaction history completely
        members.remove(id);
        transactions.remove(id);
    }

    @Override
    public FamilyTransaction saveTransaction(FamilyTransaction transaction) {
        if (transaction == null || transaction.getMemberId() == null) {
            throw new IllegalArgumentException("Transaction and memberId must not be null");
        }
        transactions.computeIfAbsent(transaction.getMemberId(), k -> new CopyOnWriteArrayList<>())
                .add(transaction);
        return transaction;
    }

    @Override
    public List<FamilyTransaction> findTransactionsByMemberId(String memberId) {
        if (memberId == null) return Collections.emptyList();
        List<FamilyTransaction> list = transactions.getOrDefault(memberId, Collections.emptyList());
        return list.stream()
                .sorted(Comparator.comparing(FamilyTransaction::getTimestamp).reversed())
                .collect(Collectors.toList());
    }

    public void clear() {
        members.clear();
        transactions.clear();
    }
}
