package com.flowops.dto.budget;

import com.flowops.entity.Budget;
import com.flowops.enums.BudgetStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record BudgetResponse(
        UUID uuid,
        UUID workOrderUuid,
        BudgetStatus status,
        BigDecimal totalAmount,
        String createdByName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<BudgetItemResponse> items
) {
    public static BudgetResponse from(Budget budget, List<BudgetItemResponse> items) {
        return new BudgetResponse(
                budget.getUuid(),
                budget.getWorkOrder().getUuid(),
                budget.getStatus(),
                budget.getTotalAmount(),
                budget.getCreatedBy().getName(),
                budget.getCreatedAt(),
                budget.getUpdatedAt(),
                items
        );
    }
}
