package com.lankapay.bill.domain;

import java.util.List;
import java.util.Optional;

/**
 * Storage contract for SavedBiller entities.
 */
public interface SavedBillerStore {

    SavedBiller save(SavedBiller savedBiller);

    Optional<SavedBiller> findById(Long id);

    Optional<SavedBiller> findByIdAndUserId(Long id, Long userId);

    List<SavedBiller> findByUserId(Long userId);

    void deleteById(Long id);

    boolean existsByUserIdAndBillerIdAndMaskedReference(Long userId, String billerId, String maskedReference);
}
