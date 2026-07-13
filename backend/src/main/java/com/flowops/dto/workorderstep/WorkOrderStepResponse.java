package com.flowops.dto.workorderstep;

import com.flowops.entity.WorkOrderStep;
import com.flowops.enums.StepStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkOrderStepResponse(
        UUID uuid,
        Integer stepOrder,
        String title,
        StepStatus status,
        UUID assignedToUuid,
        String assignedToName,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static WorkOrderStepResponse from(WorkOrderStep step) {
        return new WorkOrderStepResponse(
                step.getUuid(),
                step.getStepOrder(),
                step.getTitle(),
                step.getStatus(),
                step.getAssignedTo() != null ? step.getAssignedTo().getUuid() : null,
                step.getAssignedTo() != null ? step.getAssignedTo().getName() : null,
                step.getStartedAt(),
                step.getCompletedAt(),
                step.getNotes(),
                step.getCreatedAt(),
                step.getUpdatedAt()
        );
    }
}
