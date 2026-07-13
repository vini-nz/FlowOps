package com.flowops.dto.workorderstep;

import com.flowops.enums.StepStatus;
import jakarta.validation.constraints.NotNull;

// notes e opcional de proposito: o mesmo PATCH cobre tanto "avancar status"
// (CU-019/CU-020) quanto "registrar observacao" (CU-022) - ver
// StepStatusTransitions para o motivo de isValid(x, x) ser permitido.
public record WorkOrderStepStatusUpdateRequest(
        @NotNull StepStatus status,
        String notes
) {}
