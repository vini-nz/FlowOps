package com.flowops.controller;

import com.flowops.dto.workorderstep.StepChecklistItemRequest;
import com.flowops.dto.workorderstep.StepChecklistItemToggleRequest;
import com.flowops.dto.workorderstep.WorkOrderStepResponse;
import com.flowops.dto.workorderstep.WorkOrderStepStatusUpdateRequest;
import com.flowops.entity.User;
import com.flowops.service.WorkOrderStepService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-orders/{workOrderUuid}/steps")
@RequiredArgsConstructor
public class WorkOrderStepController {

    private final WorkOrderStepService workOrderStepService;

    @GetMapping
    public List<WorkOrderStepResponse> list(
            @AuthenticationPrincipal User user,
            @PathVariable UUID workOrderUuid) {
        return workOrderStepService.list(user.getCompany().getId(), workOrderUuid);
    }

    // Matriz de permissoes (Negocio e Dominio, Notion): "Atualizar Etapas" e
    // permitido para Admin Empresa, Operador e Tecnico - diferente de criar/
    // avancar/atribuir WorkOrder, que exclui Tecnico.
    @PreAuthorize("hasAnyRole('ADMIN_EMPRESA', 'OPERADOR', 'TECNICO')")
    @PatchMapping("/{stepUuid}/status")
    public WorkOrderStepResponse updateStatus(
            @AuthenticationPrincipal User user,
            @PathVariable UUID workOrderUuid,
            @PathVariable UUID stepUuid,
            @Valid @RequestBody WorkOrderStepStatusUpdateRequest request) {
        return workOrderStepService.updateStatus(
                user.getCompany().getId(), workOrderUuid, stepUuid, request, user);
    }

    // Checklist (V2.5) segue a mesma permissao de "Atualizar Etapas": e o
    // Tecnico em campo quem marca o que foi feito.
    @PreAuthorize("hasAnyRole('ADMIN_EMPRESA', 'OPERADOR', 'TECNICO')")
    @PatchMapping("/{stepUuid}/checklist/{itemUuid}")
    public WorkOrderStepResponse toggleChecklistItem(
            @AuthenticationPrincipal User user,
            @PathVariable UUID workOrderUuid,
            @PathVariable UUID stepUuid,
            @PathVariable UUID itemUuid,
            @Valid @RequestBody StepChecklistItemToggleRequest request) {
        return workOrderStepService.toggleChecklistItem(
                user.getCompany().getId(), workOrderUuid, stepUuid, itemUuid, request.done(), user);
    }

    @PreAuthorize("hasAnyRole('ADMIN_EMPRESA', 'OPERADOR', 'TECNICO')")
    @PostMapping("/{stepUuid}/checklist")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkOrderStepResponse addChecklistItem(
            @AuthenticationPrincipal User user,
            @PathVariable UUID workOrderUuid,
            @PathVariable UUID stepUuid,
            @Valid @RequestBody StepChecklistItemRequest request) {
        return workOrderStepService.addChecklistItem(
                user.getCompany().getId(), workOrderUuid, stepUuid, request.description(), user);
    }

    @PreAuthorize("hasAnyRole('ADMIN_EMPRESA', 'OPERADOR', 'TECNICO')")
    @DeleteMapping("/{stepUuid}/checklist/{itemUuid}")
    public WorkOrderStepResponse removeChecklistItem(
            @AuthenticationPrincipal User user,
            @PathVariable UUID workOrderUuid,
            @PathVariable UUID stepUuid,
            @PathVariable UUID itemUuid) {
        return workOrderStepService.removeChecklistItem(
                user.getCompany().getId(), workOrderUuid, stepUuid, itemUuid, user);
    }
}
