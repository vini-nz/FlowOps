package com.flowops.controller;

import com.flowops.dto.timeline.TimelineEventResponse;
import com.flowops.entity.User;
import com.flowops.service.TimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-orders/{workOrderUuid}/timeline")
@RequiredArgsConstructor
public class TimelineController {

    private final TimelineService timelineService;

    // Sem @PreAuthorize: leitura do historico e visivel a qualquer papel
    // autenticado da empresa, mesma regra do GET de WorkOrders e Etapas -
    // o isolamento multi-tenant vem do filtro por company_id no Service.
    @GetMapping
    public List<TimelineEventResponse> list(
            @AuthenticationPrincipal User user,
            @PathVariable UUID workOrderUuid) {
        return timelineService.list(user.getCompany().getId(), workOrderUuid);
    }
}
