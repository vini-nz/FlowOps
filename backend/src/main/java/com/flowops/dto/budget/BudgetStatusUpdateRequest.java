package com.flowops.dto.budget;

import com.flowops.enums.BudgetStatus;
import jakarta.validation.constraints.NotNull;

public record BudgetStatusUpdateRequest(@NotNull BudgetStatus status) {}
