package com.flowops.service;

import com.flowops.dto.notification.NotificationResponse;
import com.flowops.entity.Notification;
import com.flowops.exception.ResourceNotFoundException;
import com.flowops.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Notificações do usuário autenticado (V2.7).
 * <p>
 * Todo método recebe {@code userId} além de {@code companyId}: diferente dos
 * outros módulos, aqui o isolamento por empresa não basta — uma notificação
 * pertence a uma pessoa, e um colega da mesma empresa não pode ler nem marcar
 * como lida a notificação de outro.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(Long companyId, Long userId, Pageable pageable) {
        return notificationRepository
                .findByUserIdAndCompanyIdOrderByCreatedAtDesc(userId, companyId, pageable)
                .map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long companyId, Long userId) {
        return notificationRepository.countByUserIdAndCompanyIdAndReadFalse(userId, companyId);
    }

    @Transactional
    public NotificationResponse markAsRead(Long companyId, Long userId, UUID notificationUuid) {
        Notification notification = notificationRepository
                .findByUuidAndUserIdAndCompanyId(notificationUuid, userId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificação não encontrada"));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(OffsetDateTime.now());
            notificationRepository.save(notification);
        }

        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllAsRead(Long companyId, Long userId) {
        return notificationRepository.markAllAsRead(userId, companyId, OffsetDateTime.now());
    }
}
