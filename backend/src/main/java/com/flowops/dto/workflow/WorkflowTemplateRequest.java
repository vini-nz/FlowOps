package com.flowops.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkflowTemplateRequest(
        @NotBlank @Size(max = 100) String name,
        Boolean isDefault
) {}
