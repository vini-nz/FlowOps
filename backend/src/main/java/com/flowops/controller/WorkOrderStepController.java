package com.flowops.controller;

import com.flowops.dto.workorderstep.WorkOrderStepResponse;
import com.flowops.dto.workorderstep.WorkOrderStepStatusUpdateRequest;
import com.flowops.entity.User;
import com.flowops.service.WorkOrderStepService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
}
