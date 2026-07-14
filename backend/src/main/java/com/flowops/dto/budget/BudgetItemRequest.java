package com.flowops.dto.budget;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Se catalogItemUuid for informado, description/unitPrice sao opcionais
 * (herdam o snapshot do item de catalogo). Caso contrario, os dois sao
 * obrigatorios - validado em BudgetService, nao aqui, porque a obrigatoriedade
 * depende do valor de outro campo (Bean Validation puro nao expressa isso
 * de forma legivel sem uma anotacao customizada).
 */
public record BudgetItemRequest(
        UUID catalogItemUuid,
        @Size(max = 150) String description,
        @NotNull @DecimalMin(value = "0.01") BigDecimal quantity,
        @DecimalMin(value = "0.00") BigDecimal unitPrice
) {}
