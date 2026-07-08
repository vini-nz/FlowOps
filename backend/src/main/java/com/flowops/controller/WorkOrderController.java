package com.flowops.controller;

import com.flowops.dto.workorder.WorkOrderAssignRequest;
import com.flowops.dto.workorder.WorkOrderRequest;
import com.flowops.dto.workorder.WorkOrderResponse;
import com.flowops.dto.workorder.WorkOrderStatusUpdateRequest;
import com.flowops.entity.User;
import com.flowops.enums.WorkOrderStatus;
import com.flowops.service.WorkOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    @GetMapping
    public Page<WorkOrderResponse> list(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) WorkOrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return workOrderService.list(user.getCompany().getId(), status, pageable);
    }

    @GetMapping("/{uuid}")
    public WorkOrderResponse get(@AuthenticationPrincipal User user, @PathVariable UUID uuid) {
        return workOrderService.get(user.getCompany().getId(), uuid);
    }

    // Matriz de permissões (Negócio e Domínio, Notion): criar WorkOrder é
    // restrito a Admin Empresa e Operador - Técnico não cria, apenas executa.
    @PreAuthorize("hasAnyRole('ADMIN_EMPRESA', 'OPERADOR')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkOrderResponse create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody WorkOrderRequest request) {
        return workOrderService.create(user.getCompany().getId(), user, request);
    }

    @PreAuthorize("hasAnyRole('ADMIN_EMPRESA', 'OPERADOR')")
    @PatchMapping("/{uuid}/status")
    public WorkOrderResponse updateStatus(
            @AuthenticationPrincipal User user,
            @PathVariable UUID uuid,
            @Valid @RequestBody WorkOrderStatusUpdateRequest request) {
        return workOrderService.updateStatus(user.getCompany().getId(), uuid, request.status(), user);
    }

    @PreAuthorize("hasAnyRole('ADMIN_EMPRESA', 'OPERADOR')")
    @PatchMapping("/{uuid}/assign")
    public WorkOrderResponse assign(
            @AuthenticationPrincipal User user,
            @PathVariable UUID uuid,
            @Valid @RequestBody WorkOrderAssignRequest request) {
        return workOrderService.assign(user.getCompany().getId(), uuid, request.assignedToUuid(), user);
    }
}
