package com.flowops.dto.workorderstep;

import com.flowops.entity.WorkOrderStepChecklistItem;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StepChecklistItemResponse(
        UUID uuid,
        Integer itemOrder,
        String description,
        boolean done,
        OffsetDateTime doneAt,
        String doneByName,
        // false = item avulso, criado durante a execução desta OS
        boolean fromTemplate
) {
    public static StepChecklistItemResponse from(WorkOrderStepChecklistItem item) {
        return new StepChecklistItemResponse(
                item.getUuid(),
                item.getItemOrder(),
                item.getDescription(),
                item.isDone(),
                item.getDoneAt(),
                item.getDoneBy() != null ? item.getDoneBy().getName() : null,
                item.getWorkflowChecklistItem() != null
        );
    }
}
