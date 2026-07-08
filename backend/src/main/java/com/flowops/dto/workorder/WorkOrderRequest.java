package com.flowops.dto.workorder;

import com.flowops.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record WorkOrderRequest(
        @NotNull UUID clientUuid,
        @NotBlank @Size(max = 150) String title,
        String description,
        Priority priority,
        LocalDate scheduledStart,
        LocalDate scheduledEnd,
        UUID assignedToUuid
) {}
