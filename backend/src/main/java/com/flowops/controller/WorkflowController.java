package com.flowops.controller;

import com.flowops.dto.workflow.ChecklistItemRequest;
import com.flowops.dto.workflow.WorkflowStepRequest;
import com.flowops.dto.workflow.WorkflowTemplateRequest;
import com.flowops.dto.workflow.WorkflowTemplateResponse;
import com.flowops.entity.User;
import com.flowops.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Configuração do workflow da empresa (V2.5). Toda escrita é restrita a
 * ADMIN_EMPRESA: mudar o molde afeta todas as WorkOrders futuras da empresa,
 * o que é decisão administrativa, não operacional. A leitura é aberta aos
 * demais papéis porque Operador e Técnico precisam saber quais etapas
 * existem.
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @GetMapping
    public List<WorkflowTemplateResponse> list(@AuthenticationPrincipal User user) {
        return workflowService.list(user.getCompany().getId());
    }

    @GetMapping("/{uuid}")
    public WorkflowTemplateResponse get(@AuthenticationPrincipal User user, @PathVariable UUID uuid) {
        return workflowService.get(user.getCompany().getId(), uuid);
    }

    @PreAuthorize("hasRole('ADMIN_EMPRESA')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowTemplateResponse create(
            @AuthenticationPrincipal User user, @Valid @RequestBody WorkflowTemplateRequest request) {
        return workflowService.createTemplate(user.getCompany().getId(), request);
    }

    @PreAuthorize("hasRole('ADMIN_EMPRESA')")
    @PutMapping("/{uuid}")
    public WorkflowTemplateResponse update(
            @AuthenticationPrincipal User user,
            @PathVariable UUID uuid,
            @Valid @RequestBody WorkflowTemplateRequest request) {
        return workflowService.updateTemplate(user.getCompany().getId(), uuid, request);
    }

    @PreAuthorize("hasRole('ADMIN_EMPRESA')")
    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable UUID uuid) {
        workflowService.deleteTemplate(user.getCompany().getId(), uuid);
    }

    @PreAuthorize("hasRole('ADMIN_EMPRESA')")
    @PostMapping("/{uuid}/steps")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowTemplateResponse addStep(
            @AuthenticationPrincipal User user,
            @PathVariable UUID uuid,
            @Valid @RequestBody WorkflowStepRequest request) {
        return workflowService.addStep(user.getCompany().getId(), uuid, request);
    }

    @PreAuthorize("hasRole('ADMIN_EMPRESA')")
    @PutMapping("/{uuid}/steps/{stepUuid}")
    public WorkflowTemplateResponse updateStep(
            @AuthenticationPrincipal User user,
            @PathVariable UUID uuid,
            @PathVariable UUID stepUuid,
            @Valid @RequestBody WorkflowStepRequest request) {
        return workflowService.updateStep(user.getCompany().getId(), uuid, stepUuid, request);
    }

    @PreAuthorize("hasRole('ADMIN_EMPRESA')")
    @DeleteMapping("/{uuid}/steps/{stepUuid}")
    public WorkflowTemplateResponse deleteStep(
            @AuthenticationPrincipal User user,
            @PathVariable UUID uuid,
            @PathVariable UUID stepUuid) {
        return workflowService.deleteStep(user.getCompany().getId(), uuid, stepUuid);
    }

    @PreAuthorize("hasRole('ADMIN_EMPRESA')")
    @PatchMapping("/{uuid}/steps/{stepUuid}/move")
    public WorkflowTemplateResponse moveStep(
            @AuthenticationPrincipal User user,
            @PathVariable UUID uuid,
            @PathVariable UUID stepUuid,
            @RequestParam(defaultValue = "up") String direction) {
        return workflowService.moveStep(user.getCompany().getId(), uuid, stepUuid, "up".equals(direction));
    }

    @PreAuthorize("hasRole('ADMIN_EMPRESA')")
    @PostMapping("/{uuid}/steps/{stepUuid}/checklist")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowTemplateResponse addChecklistItem(
            @AuthenticationPrincipal User user,
            @PathVariable UUID uuid,
            @PathVariable UUID stepUuid,
            @Valid @RequestBody ChecklistItemRequest request) {
        return workflowService.addChecklistItem(user.getCompany().getId(), uuid, stepUuid, request);
    }

    @PreAuthorize("hasRole('ADMIN_EMPRESA')")
    @DeleteMapping("/{uuid}/steps/{stepUuid}/checklist/{itemUuid}")
    public WorkflowTemplateResponse deleteChecklistItem(
            @AuthenticationPrincipal User user,
            @PathVariable UUID uuid,
            @PathVariable UUID stepUuid,
            @PathVariable UUID itemUuid) {
        return workflowService.deleteChecklistItem(user.getCompany().getId(), uuid, stepUuid, itemUuid);
    }
}
