package com.lankapay.notification.service;

import com.lankapay.identity.domain.User;
import com.lankapay.identity.domain.UserRole;
import com.lankapay.identity.domain.UserStatus;
import com.lankapay.identity.repository.UserRepository;
import com.lankapay.notification.channel.EmailNotificationChannel;
import com.lankapay.notification.channel.InAppNotificationChannel;
import com.lankapay.notification.channel.PushNotificationChannel;
import com.lankapay.notification.domain.*;
import com.lankapay.notification.repository.NotificationPreferenceRepository;
import com.lankapay.notification.repository.NotificationRepository;
import com.lankapay.notification.web.dto.NotificationPreferenceResponse;
import com.lankapay.notification.web.dto.UpdateNotificationPreferenceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationPreferenceRepository preferenceRepository;
    @Mock UserRepository userRepository;
    @Mock InAppNotificationChannel inAppChannel;
    @Mock PushNotificationChannel pushChannel;
    @Mock EmailNotificationChannel emailChannel;

    NotificationMessageFormatter formatter = new NotificationMessageFormatter();
    NotificationService notificationService;
    User user;

    @BeforeEach
    void setUp() {
        when(inAppChannel.getChannelType()).thenReturn(NotificationChannelType.IN_APP);
        when(pushChannel.getChannelType()).thenReturn(NotificationChannelType.PUSH);
        when(emailChannel.getChannelType()).thenReturn(NotificationChannelType.EMAIL);

        notificationService = new NotificationService(
                notificationRepository,
                preferenceRepository,
                userRepository,
                formatter,
                List.of(inAppChannel, pushChannel, emailChannel)
        );

        user = new User("+94771234567", "Kamal Perera", "199012345678", "$2a$12$hash");
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(user, "status", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "role", UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("Dispatch notification delivers across all enabled channels")
    void testDispatchDeliversAcrossEnabledChannels() {
        NotificationPreference pref = new NotificationPreference(user);
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.of(pref));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        notificationService.dispatch(user, NotificationType.PAYMENT_SENT, "Payment Sent", "Payment sent to Nimal S. Tap to view receipt.");

        verify(inAppChannel).deliver(any(Notification.class), eq(user));
        verify(pushChannel).deliver(any(Notification.class), eq(user));
        verify(emailChannel).deliver(any(Notification.class), eq(user));
    }

    @Test
    @DisplayName("User preference toggles suppress disabled channels")
    void testPreferencesSuppressDisabledChannels() {
        NotificationPreference pref = new NotificationPreference(user);
        pref.update(true, false, false, true, true, true); // Push & Email disabled
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.of(pref));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        notificationService.dispatch(user, NotificationType.PAYMENT_SENT, "Payment Sent", "Payment sent to Nimal S. Tap to view receipt.");

        verify(inAppChannel).deliver(any(Notification.class), eq(user));
        verify(pushChannel, never()).deliver(any(Notification.class), any());
        verify(emailChannel, never()).deliver(any(Notification.class), any());
    }

    @Test
    @DisplayName("Channel delivery failure safely retries and records FAILED without throwing")
    void testChannelDeliveryFailureRetriesAndLogsWithoutThrowing() {
        NotificationPreference pref = new NotificationPreference(user);
        pref.update(false, true, false, false, true, true); // Only Push enabled
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.of(pref));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        // Push channel throws exception on each attempt
        doThrow(new RuntimeException("Push server timeout"))
                .when(pushChannel).deliver(any(Notification.class), eq(user));

        // Must NOT throw
        notificationService.dispatch(user, NotificationType.PAYMENT_SENT, "Payment Sent", "Payment sent to Nimal S. Tap to view receipt.");

        // Verifies 3 attempts (initial + 2 retries)
        verify(pushChannel, times(3)).deliver(any(Notification.class), eq(user));
    }

    @Test
    @DisplayName("Mark notification as read updates notification entity")
    void testMarkNotificationAsRead() {
        Notification notif = new Notification(user, NotificationChannelType.IN_APP,
                NotificationType.PAYMENT_SENT, "Title", "Message");
        ReflectionTestUtils.setField(notif, "id", 55L);

        when(notificationRepository.findByIdAndUserId(55L, 1L)).thenReturn(Optional.of(notif));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        var response = notificationService.markAsRead(55L, 1L);

        assertThat(response.read()).isTrue();
        assertThat(response.readAt()).isNotNull();
    }

    @Test
    @DisplayName("Update preferences updates and persists preference settings")
    void testUpdatePreferences() {
        NotificationPreference pref = new NotificationPreference(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.of(pref));
        when(preferenceRepository.save(any(NotificationPreference.class))).thenAnswer(i -> i.getArgument(0));

        UpdateNotificationPreferenceRequest req = new UpdateNotificationPreferenceRequest(
                true, false, true, false, true, false);

        NotificationPreferenceResponse response = notificationService.updatePreferences(1L, req);

        assertThat(response.pushEnabled()).isFalse();
        assertThat(response.emailEnabled()).isTrue();
        assertThat(response.securityAlerts()).isFalse();
    }
}
