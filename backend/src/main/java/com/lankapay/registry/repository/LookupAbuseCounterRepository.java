package com.lankapay.registry.repository;

import com.lankapay.registry.domain.LookupAbuseCounter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface LookupAbuseCounterRepository extends JpaRepository<LookupAbuseCounter, Long> {

    Optional<LookupAbuseCounter> findByActorIdAndCounterDate(Long actorId, LocalDate date);
}
