package com.flowops.controller;

import com.flowops.dto.dashboard.DashboardSummary;
import com.flowops.entity.User;
import com.flowops.enums.WorkOrderStatus;
import com.flowops.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint minimo do modulo Dashboard (Sprint 4 no Roadmap completo).
 * Antecipado aqui no Sprint 1 apenas como prova viva de que uma rota
 * protegida por JWT consegue ler dados reais do banco respeitando o
 * isolamento multi-tenant (company_id do usuario autenticado).
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final WorkOrderRepository workOrderRepository;

    @GetMapping("/summary")
    public DashboardSummary summary(@AuthenticationPrincipal User user) {
        Long companyId = user.getCompany().getId();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (WorkOrderStatus status : WorkOrderStatus.values()) {
            long count = workOrderRepository.countByCompanyIdAndStatusAndDeletedAtIsNull(companyId, status);
            if (count > 0) {
                byStatus.put(status.name(), count);
            }
        }

        long total = workOrderRepository.findByCompanyIdAndDeletedAtIsNull(companyId, PageRequest.of(0, 1))
                .getTotalElements();

        return new DashboardSummary(user.getCompany().getName(), total, byStatus);
    }
}
