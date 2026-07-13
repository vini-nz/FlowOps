package com.flowops.dto.dashboard;

import java.util.List;
import java.util.Map;

public record DashboardSummary(
        String companyName,
        long totalWorkOrders,
        Map<String, Long> byStatus,
        List<DashboardWorkOrderItem> recentWorkOrders,
        List<DashboardWorkOrderItem> upcomingDeliveries
) {}
