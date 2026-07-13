package com.flowops.service;

import com.flowops.dto.dashboard.DashboardSummary;
import com.flowops.dto.dashboard.DashboardWorkOrderItem;
import com.flowops.enums.WorkOrderStatus;
import com.flowops.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modulo 5 do Roadmap (Sprint 4). Ate a Sprint 3 o resumo vivia direto no
 * Controller (endpoint minimo antecipado na Sprint 1 so como prova de leitura
 * protegida do banco); a partir daqui segue o mesmo padrao Controller -> Service
 * -> Repository do resto do projeto (ver Arquitetura Tecnica no Notion).
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    // Nao faz sentido anunciar como "proxima entrega" uma WorkOrder que ja
    // chegou a um estado terminal - FINALIZADO ja foi entregue, RECUSADO nunca
    // sera (ver WorkOrderStatusTransitions).
    private static final List<WorkOrderStatus> TERMINAL_STATUSES =
            List.of(WorkOrderStatus.FINALIZADO, WorkOrderStatus.RECUSADO);

    private final WorkOrderRepository workOrderRepository;

    @Transactional(readOnly = true)
    public DashboardSummary summary(Long companyId, String companyName) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (WorkOrderStatus status : WorkOrderStatus.values()) {
            long count = workOrderRepository.countByCompanyIdAndStatusAndDeletedAtIsNull(companyId, status);
            if (count > 0) {
                byStatus.put(status.name(), count);
            }
        }

        long total = workOrderRepository.findByCompanyIdAndDeletedAtIsNull(companyId, PageRequest.of(0, 1))
                .getTotalElements();

        List<DashboardWorkOrderItem> recentWorkOrders = workOrderRepository
                .findTop5ByCompanyIdAndDeletedAtIsNullOrderByCreatedAtDesc(companyId)
                .stream()
                .map(DashboardWorkOrderItem::from)
                .toList();

        List<DashboardWorkOrderItem> upcomingDeliveries = workOrderRepository
                .findTop5ByCompanyIdAndDeletedAtIsNullAndScheduledEndGreaterThanEqualAndStatusNotInOrderByScheduledEndAsc(
                        companyId, LocalDate.now(), TERMINAL_STATUSES)
                .stream()
                .map(DashboardWorkOrderItem::from)
                .toList();

        return new DashboardSummary(companyName, total, byStatus, recentWorkOrders, upcomingDeliveries);
    }
}
