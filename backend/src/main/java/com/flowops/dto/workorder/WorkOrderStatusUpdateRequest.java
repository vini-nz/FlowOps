package com.flowops.dto.workorder;

import com.flowops.enums.WorkOrderStatus;
import jakarta.validation.constraints.NotNull;

public record WorkOrderStatusUpdateRequest(
        @NotNull WorkOrderStatus status
) {}
