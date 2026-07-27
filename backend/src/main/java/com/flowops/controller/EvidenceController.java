package com.flowops.controller;

import com.flowops.dto.evidence.EvidenceResponse;
import com.flowops.dto.evidence.EvidenceUploadRequest;
import com.flowops.dto.evidence.EvidenceUploadUrlResponse;
import com.flowops.entity.User;
import com.flowops.service.EvidenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Evidências de uma etapa (V2.6). Mesma permissão de "Anexar Evidências" na
 * matriz RBAC (Negócio e Domínio): Admin Empresa, Operador e Técnico.
 * <p>
 * O fluxo de envio tem dois passos porque o arquivo não passa pela API:
 * {@code POST /upload-url} devolve uma URL pré-assinada, o navegador faz
 * {@code PUT} direto no storage, e {@code POST /{uuid}/confirm} valida que o
 * objeto chegou antes de a evidência aparecer na galeria.
 */
@RestController
@RequestMapping("/api/v1/work-orders/{workOrderUuid}/steps/{stepUuid}/evidences")
@RequiredArgsConstructor
public class EvidenceController {

    private final EvidenceService evidenceService;

    @GetMapping
    public List<EvidenceResponse> list(
            @AuthenticationPrincipal User user,
            @PathVariable UUID workOrderUuid,
            @PathVariable UUID stepUuid) {
        return evidenceService.list(user.getCompany().getId(), workOrderUuid, stepUuid);
    }

    @PreAuthorize("hasAnyRole('ADMIN_EMPRESA', 'OPERADOR', 'TECNICO')")
    @PostMapping("/upload-url")
    @ResponseStatus(HttpStatus.CREATED)
    public EvidenceUploadUrlResponse createUploadUrl(
            @AuthenticationPrincipal User user,
            @PathVariable UUID workOrderUuid,
            @PathVariable UUID stepUuid,
            @Valid @RequestBody EvidenceUploadRequest request) {
        return evidenceService.createUploadUrl(
                user.getCompany().getId(), workOrderUuid, stepUuid, request, user);
    }

    @PreAuthorize("hasAnyRole('ADMIN_EMPRESA', 'OPERADOR', 'TECNICO')")
    @PostMapping("/{evidenceUuid}/confirm")
    public EvidenceResponse confirm(
            @AuthenticationPrincipal User user,
            @PathVariable UUID workOrderUuid,
            @PathVariable UUID stepUuid,
            @PathVariable UUID evidenceUuid) {
        return evidenceService.confirm(
                user.getCompany().getId(), workOrderUuid, stepUuid, evidenceUuid, user);
    }

    // Devolve a URL assinada em vez de redirecionar: o frontend precisa dela
    // para abrir numa aba nova sem carregar o header Authorization junto.
    @GetMapping("/{evidenceUuid}/download-url")
    public Map<String, String> downloadUrl(
            @AuthenticationPrincipal User user,
            @PathVariable UUID workOrderUuid,
            @PathVariable UUID stepUuid,
            @PathVariable UUID evidenceUuid) {
        return Map.of("url", evidenceService.downloadUrl(
                user.getCompany().getId(), workOrderUuid, stepUuid, evidenceUuid));
    }

    @PreAuthorize("hasAnyRole('ADMIN_EMPRESA', 'OPERADOR', 'TECNICO')")
    @DeleteMapping("/{evidenceUuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal User user,
            @PathVariable UUID workOrderUuid,
            @PathVariable UUID stepUuid,
            @PathVariable UUID evidenceUuid) {
        evidenceService.delete(user.getCompany().getId(), workOrderUuid, stepUuid, evidenceUuid, user);
    }
}
