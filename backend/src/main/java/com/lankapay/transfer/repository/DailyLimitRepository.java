package com.lankapay.transfer.repository;

import com.lankapay.transfer.domain.DailyLimit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyLimitRepository extends JpaRepository<DailyLimit, Long> {

    /**
     * Fetch today's limit row with a pessimistic write lock.
     * Prevents two concurrent transfers from both reading the same totalSent
     * and both passing the cap check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT dl FROM DailyLimit dl WHERE dl.user.id = :userId AND dl.limitDate = :date")
    Optional<DailyLimit> findByUserIdAndDateForUpdate(Long userId, LocalDate date);

    Optional<DailyLimit> findByUserIdAndLimitDate(Long userId, LocalDate limitDate);
}
