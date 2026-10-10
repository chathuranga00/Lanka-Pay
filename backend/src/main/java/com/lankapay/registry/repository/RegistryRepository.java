package com.lankapay.registry.repository;

import com.lankapay.registry.domain.Registry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RegistryRepository extends JpaRepository<Registry, Long> {

    /**
     * Lookup by phone — only returns entry if discoverable = true.
     * Used for the conflict check in PhoneVerificationService.
     */
    Optional<Registry> findByPhoneAndDiscoverableTrue(String phone);

    /** Fetch the registry entry for a specific user regardless of discoverability. */
    Optional<Registry> findByUserId(Long userId);

    /**
     * Lookup by the user's E.164 phone, ignoring discoverability.
     * Used in LookupService to classify the exact lookup result type
     * (NOT_FOUND vs DISCOVERY_DISABLED vs FOUND).
     */
    @Query("SELECT r FROM Registry r WHERE r.user.phone = :phone")
    Optional<Registry> findByUserId_Phone(String phone);
}
