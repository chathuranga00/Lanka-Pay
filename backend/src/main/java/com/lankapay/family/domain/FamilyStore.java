package com.lankapay.family.domain;

import java.util.List;
import java.util.Optional;

public interface FamilyStore {

    FamilyMember save(FamilyMember member);

    Optional<FamilyMember> findById(String id);

    List<FamilyMember> findByGuardianUserId(Long guardianUserId);

    void delete(String id);

    FamilyTransaction saveTransaction(FamilyTransaction transaction);

    List<FamilyTransaction> findTransactionsByMemberId(String memberId);
}
