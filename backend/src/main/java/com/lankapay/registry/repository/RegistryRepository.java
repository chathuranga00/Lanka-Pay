package com.lankapay.registry.repository;

import com.lankapay.registry.domain.Registry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegistryRepository extends JpaRepository<Registry, Long> {

    /** Lookup by phone – only returns entry if discoverable = true. */
    Optional<Registry> findByPhoneAndDiscoverableTrue(String phone);

    Optional<Registry> findByUserId(Long userId);
}
