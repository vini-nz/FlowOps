package com.flowops.dto.workflow;

import com.flowops.entity.WorkflowStep;

import java.util.List;
import java.util.UUID;

public record WorkflowStepResponse(
        UUID uuid,
        Integer stepOrder,
        String title,
        List<ChecklistItemResponse> checklistItems
) {
    public static WorkflowStepResponse from(WorkflowStep step, List<ChecklistItemResponse> checklistItems) {
        return new WorkflowStepResponse(step.getUuid(), step.getStepOrder(), step.getTitle(), checklistItems);
    }
}
