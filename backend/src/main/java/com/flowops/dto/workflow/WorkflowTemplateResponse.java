package com.flowops.dto.workflow;

import com.flowops.entity.WorkflowTemplate;

import java.util.List;
import java.util.UUID;

public record WorkflowTemplateResponse(
        UUID uuid,
        String name,
        boolean isDefault,
        List<WorkflowStepResponse> steps
) {
    public static WorkflowTemplateResponse from(WorkflowTemplate template, List<WorkflowStepResponse> steps) {
        return new WorkflowTemplateResponse(
                template.getUuid(), template.getName(), template.isDefault(), steps);
    }
}
