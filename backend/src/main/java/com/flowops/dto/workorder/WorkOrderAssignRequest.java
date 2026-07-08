package com.flowops.dto.workorder;

import java.util.UUID;

// assignedToUuid nulo remove a atribuicao atual (WorkOrder volta a ficar sem responsavel).
public record WorkOrderAssignRequest(
        UUID assignedToUuid
) {}
