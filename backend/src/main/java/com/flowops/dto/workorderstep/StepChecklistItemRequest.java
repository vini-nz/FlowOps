package com.flowops.dto.workorderstep;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Item avulso acrescentado a uma etapa específica durante a execução. */
public record StepChecklistItemRequest(
        @NotBlank @Size(max = 200) String description
) {}
