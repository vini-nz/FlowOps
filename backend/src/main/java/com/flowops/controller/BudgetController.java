package com.flowops.controller;

import com.flowops.dto.budget.BudgetItemRequest;
import com.flowops.dto.budget.BudgetResponse;
import com.flowops.dto.budget.BudgetStatusUpdateRequest;
import com.flowops.entity.User;
import com.flowops.service.BudgetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-orders/{workOrderUuid}/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping
    public BudgetResponse get(@AuthenticationPrincipal User user, @PathVariable UUID workOrderUuid) {
        return budgetService.get(user.getCompany().getId(), workOrderUuid);
    }

    // Matriz de permissões (Negócio e Domínio, Notion): "Criar Orçamentos" é
    // restrito a Admin Empresa e Operador.
    @PreAuthorize("hasAnyRole('ADMIN_EMPRESA', 'OPERADOR')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetResponse create(@AuthenticationPrincipal User user, @PathVariable UUID workOrderUuid) {
        return budgetService.create(user.getCompany().getId(), workOrderUuid, user);
    }

    @PreAuthorize("hasAnyRole('ADMIN_EMPRESA', 'OPERADOR')")
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetResponse addItem(
            @AuthenticationPrincipal User user,
            @PathVariable UUID workOrderUuid,
            @Valid @RequestBody BudgetItemRequest request) {
        return budgetService.addItem(user.getCompany().getId(), workOrderUuid, request, user);
    }

    @PreAuthorize("hasAnyRole('ADMIN_EMPRESA', 'OPERADOR')")
    @DeleteMapping("/items/{itemUuid}")
    public BudgetResponse removeItem(
            @AuthenticationPrincipal User user,
            @PathVariable UUID workOrderUuid,
            @PathVariable UUID itemUuid) {
        return budgetService.removeItem(user.getCompany().getId(), workOrderUuid, itemUuid, user);
    }

    // Decisão registrada internamente pelo Operador - aprovação pública pelo
    // Cliente fica para o Portal (V3, ver Backlog Detalhado item 2).
    @PreAuthorize("hasAnyRole('ADMIN_EMPRESA', 'OPERADOR')")
    @PatchMapping("/status")
    public BudgetResponse updateStatus(
            @AuthenticationPrincipal User user,
            @PathVariable UUID workOrderUuid,
            @Valid @RequestBody BudgetStatusUpdateRequest request) {
        return budgetService.updateStatus(user.getCompany().getId(), workOrderUuid, request.status(), user);
    }

    // Critério de aceitação (Backlog Detalhado, item 2): "Download do PDF
    // pelo Operador/Admin Empresa" - mesma restrição das demais escritas do
    // módulo, já que o PDF expõe o valor comercial completo do orçamento.
    @PreAuthorize("hasAnyRole('ADMIN_EMPRESA', 'OPERADOR')")
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> downloadPdf(@AuthenticationPrincipal User user, @PathVariable UUID workOrderUuid) {
        byte[] pdf = budgetService.generatePdf(user.getCompany().getId(), workOrderUuid);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("orcamento-%s.pdf".formatted(workOrderUuid))
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(pdf);
    }
}
