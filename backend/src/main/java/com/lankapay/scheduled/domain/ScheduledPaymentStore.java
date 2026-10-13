package com.lankapay.scheduled.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ScheduledPaymentStore {

    ScheduledPayment save(ScheduledPayment payment);

    Optional<ScheduledPayment> findById(Long id);

    List<ScheduledPayment> findByUserId(Long userId);

    List<ScheduledPayment> findDueSchedules(Instant asOf);

    List<ScheduledPayment> findUpcomingForReminders(Instant from, Instant to);

    boolean delete(Long id);
}
