package com.flowops.dto.dashboard;

import java.util.Map;

public record DashboardSummary(
        String companyName,
        long totalWorkOrders,
        Map<String, Long> byStatus
) {}
