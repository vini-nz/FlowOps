package com.flowops.dto.workflow;

import com.flowops.entity.WorkflowStepChecklistItem;

import java.util.UUID;

public record ChecklistItemResponse(
        UUID uuid,
        Integer itemOrder,
        String description
) {
    public static ChecklistItemResponse from(WorkflowStepChecklistItem item) {
        return new ChecklistItemResponse(item.getUuid(), item.getItemOrder(), item.getDescription());
    }
}
