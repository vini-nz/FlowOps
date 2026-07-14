package com.flowops.dto.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CatalogItemRequest(
        @NotBlank @Size(max = 150) String name,
        String description,
        @NotNull @DecimalMin(value = "0.00") BigDecimal unitPrice,
        @Size(max = 20) String unit
) {}
