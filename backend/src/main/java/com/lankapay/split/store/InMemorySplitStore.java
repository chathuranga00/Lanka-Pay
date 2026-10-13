package com.lankapay.split.store;

import com.lankapay.split.domain.BillSplit;
import com.lankapay.split.domain.SplitStore;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemorySplitStore implements SplitStore {

    private final ConcurrentHashMap<String, BillSplit> splits = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(100);

    @Override
    public BillSplit save(BillSplit split) {
        if (split.getId() == null || split.getId().isBlank()) {
            split.setId(String.valueOf(idSequence.incrementAndGet()));
        }
        splits.put(split.getId(), split);
        return split;
    }

    @Override
    public Optional<BillSplit> findById(String id) {
        return Optional.ofNullable(splits.get(id));
    }

    @Override
    public List<BillSplit> findByUserId(Long userId) {
        return splits.values().stream()
                .filter(s -> s.getOwnerUserId().equals(userId) ||
                        (s.getParticipants() != null && s.getParticipants().stream()
                                .anyMatch(p -> p.getUserId() != null && p.getUserId().equals(userId))))
                .sorted(Comparator.comparing(BillSplit::getCreatedAt).reversed())
                .toList();
    }

    @Override
    public List<BillSplit> findByOwnerId(Long ownerId) {
        return splits.values().stream()
                .filter(s -> s.getOwnerUserId().equals(ownerId))
                .sorted(Comparator.comparing(BillSplit::getCreatedAt).reversed())
                .toList();
    }

    @Override
    public Optional<BillSplit> findByPaymentRequestReference(String prRef) {
        if (prRef == null || prRef.isBlank()) return Optional.empty();
        return splits.values().stream()
                .filter(s -> s.getParticipants() != null && s.getParticipants().stream()
                        .anyMatch(p -> prRef.equals(p.getPaymentRequestReference())))
                .findFirst();
    }

    @Override
    public void deleteById(String id) {
        splits.remove(id);
    }
}
