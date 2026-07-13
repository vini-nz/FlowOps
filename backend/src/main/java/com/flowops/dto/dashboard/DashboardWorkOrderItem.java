package com.flowops.dto.dashboard;

import com.flowops.entity.WorkOrder;
import com.flowops.enums.WorkOrderStatus;

import java.time.LocalDate;
import java.util.UUID;

// DTO minimo para as listas do Dashboard (recentes / proximas entregas) -
// so o suficiente para renderizar um item de lista, nunca a WorkOrder
// completa (mesmo principio de UserOption: evita over-fetching).
public record DashboardWorkOrderItem(
        UUID uuid,
        String title,
        String clientName,
        WorkOrderStatus status,
        LocalDate scheduledEnd
) {
    public static DashboardWorkOrderItem from(WorkOrder wo) {
        return new DashboardWorkOrderItem(
                wo.getUuid(),
                wo.getTitle(),
                wo.getClient().getName(),
                wo.getStatus(),
                wo.getScheduledEnd()
        );
    }
}
