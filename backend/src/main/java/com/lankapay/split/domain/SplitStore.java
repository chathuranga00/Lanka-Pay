package com.lankapay.split.domain;

import java.util.List;
import java.util.Optional;

public interface SplitStore {
    BillSplit save(BillSplit split);
    Optional<BillSplit> findById(String id);
    List<BillSplit> findByUserId(Long userId);
    List<BillSplit> findByOwnerId(Long ownerId);
    Optional<BillSplit> findByPaymentRequestReference(String prRef);
    void deleteById(String id);
}
