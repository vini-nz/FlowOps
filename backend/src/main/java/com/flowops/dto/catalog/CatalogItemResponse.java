package com.flowops.dto.catalog;

import com.flowops.entity.CatalogItem;

import java.math.BigDecimal;
import java.util.UUID;

public record CatalogItemResponse(
        UUID uuid,
        String name,
        String description,
        BigDecimal unitPrice,
        String unit,
        boolean active
) {
    public static CatalogItemResponse from(CatalogItem item) {
        return new CatalogItemResponse(
                item.getUuid(),
                item.getName(),
                item.getDescription(),
                item.getUnitPrice(),
                item.getUnit(),
                item.isActive()
        );
    }
}
