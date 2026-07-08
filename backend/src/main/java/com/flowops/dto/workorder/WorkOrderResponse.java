package com.flowops.dto.workorder;

import com.flowops.entity.WorkOrder;
import com.flowops.enums.Priority;
import com.flowops.enums.WorkOrderStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkOrderResponse(
        UUID uuid,
        String title,
        String description,
        WorkOrderStatus status,
        Priority priority,
        UUID clientUuid,
        String clientName,
        UUID assignedToUuid,
        String assignedToName,
        String createdByName,
        LocalDate scheduledStart,
        LocalDate scheduledEnd,
        OffsetDateTime actualStart,
        OffsetDateTime actualEnd,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static WorkOrderResponse from(WorkOrder wo) {
        return new WorkOrderResponse(
                wo.getUuid(),
                wo.getTitle(),
                wo.getDescription(),
                wo.getStatus(),
                wo.getPriority(),
                wo.getClient().getUuid(),
                wo.getClient().getName(),
                wo.getAssignedTo() != null ? wo.getAssignedTo().getUuid() : null,
                wo.getAssignedTo() != null ? wo.getAssignedTo().getName() : null,
                wo.getCreatedBy().getName(),
                wo.getScheduledStart(),
                wo.getScheduledEnd(),
                wo.getActualStart(),
                wo.getActualEnd(),
                wo.getCreatedAt(),
                wo.getUpdatedAt()
        );
    }
}
