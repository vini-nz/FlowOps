package com.flowops.dto.notification;

import com.flowops.entity.Notification;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID uuid,
        String eventType,
        String message,
        boolean read,
        UUID workOrderUuid,
        OffsetDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getUuid(),
                notification.getEventType(),
                notification.getMessage(),
                notification.isRead(),
                notification.getWorkOrder() != null ? notification.getWorkOrder().getUuid() : null,
                notification.getCreatedAt()
        );
    }
}
