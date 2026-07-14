package com.flowops.dto.budget;

import com.flowops.entity.BudgetItem;

import java.math.BigDecimal;
import java.util.UUID;

public record BudgetItemResponse(
        UUID uuid,
        UUID catalogItemUuid,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
    public static BudgetItemResponse from(BudgetItem item) {
        return new BudgetItemResponse(
                item.getUuid(),
                item.getCatalogItem() != null ? item.getCatalogItem().getUuid() : null,
                item.getDescription(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal()
        );
    }
}
