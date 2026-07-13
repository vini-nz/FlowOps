package com.flowops.controller;

import com.flowops.dto.dashboard.DashboardSummary;
import com.flowops.entity.User;
import com.flowops.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Modulo Dashboard (Sprint 4 no Roadmap completo). Endpoint minimo antecipado
 * desde a Sprint 1 como prova de leitura protegida do banco isolada por
 * company_id; a partir da Sprint 4 devolve o resumo operacional completo
 * (contadores por status, WorkOrders recentes e proximas entregas agendadas)
 * via DashboardService, seguindo o mesmo padrao Controller -> Service ->
 * Repository do resto do projeto.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public DashboardSummary summary(@AuthenticationPrincipal User user) {
        return dashboardService.summary(user.getCompany().getId(), user.getCompany().getName());
    }
}
