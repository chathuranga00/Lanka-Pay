package com.lankapay.bill.store;

import com.lankapay.bill.domain.SavedBiller;
import com.lankapay.bill.domain.SavedBillerStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory thread-safe storage for SavedBiller entities.
 */
@Component
public class InMemorySavedBillerStore implements SavedBillerStore {

    private final Map<Long, SavedBiller> store = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(100);

    @Override
    public SavedBiller save(SavedBiller savedBiller) {
        Long id = savedBiller.id() != null ? savedBiller.id() : idGen.incrementAndGet();
        Instant now = Instant.now();
        Instant created = savedBiller.createdAt() != null ? savedBiller.createdAt() : now;
        SavedBiller entity = new SavedBiller(
                id,
                savedBiller.userId(),
                savedBiller.billerId(),
                savedBiller.billerName(),
                savedBiller.category(),
                savedBiller.nickname(),
                savedBiller.encryptedReference(),
                savedBiller.maskedReference(),
                created,
                now
        );
        store.put(id, entity);
        return entity;
    }

    @Override
    public Optional<SavedBiller> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<SavedBiller> findByIdAndUserId(Long id, Long userId) {
        if (id == null || userId == null) return Optional.empty();
        SavedBiller b = store.get(id);
        if (b != null && userId.equals(b.userId())) {
            return Optional.of(b);
        }
        return Optional.empty();
    }

    @Override
    public List<SavedBiller> findByUserId(Long userId) {
        if (userId == null) return Collections.emptyList();
        return store.values().stream()
                .filter(b -> userId.equals(b.userId()))
                .sorted(Comparator.comparing(SavedBiller::createdAt).reversed())
                .toList();
    }

    @Override
    public void deleteById(Long id) {
        if (id != null) {
            store.remove(id);
        }
    }

    @Override
    public boolean existsByUserIdAndBillerIdAndMaskedReference(Long userId, String billerId, String maskedReference) {
        if (userId == null || billerId == null || maskedReference == null) return false;
        return store.values().stream().anyMatch(b ->
                userId.equals(b.userId()) &&
                billerId.equalsIgnoreCase(b.billerId()) &&
                maskedReference.equalsIgnoreCase(b.maskedReference())
        );
    }

    public void clear() {
        store.clear();
    }
}
