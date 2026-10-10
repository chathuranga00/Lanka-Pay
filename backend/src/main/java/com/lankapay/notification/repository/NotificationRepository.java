package com.lankapay.notification.repository;

import com.lankapay.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Scheduled purge: delete notifications older than 90 days. */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
