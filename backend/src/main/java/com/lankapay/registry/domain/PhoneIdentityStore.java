package com.lankapay.registry.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Storage port interface for phone registry and lookup abuse tracking.
 * Decouples services from specific database persistence technologies.
 */
public interface PhoneIdentityStore {

    PhoneIdentity save(PhoneIdentity phoneIdentity);

    Optional<PhoneIdentity> findByPhoneAndDiscoverableTrue(String phone);

    Optional<PhoneIdentity> findByUserId(Long userId);

    Optional<PhoneIdentity> findByPhone(String phone);

    Optional<AbuseCounterRecord> findAbuseCounter(Long actorId, LocalDate date);

    AbuseCounterRecord saveAbuseCounter(AbuseCounterRecord counter);

    List<RegistryAccountRecord> findAccountsInRegistry(String phone);
}
