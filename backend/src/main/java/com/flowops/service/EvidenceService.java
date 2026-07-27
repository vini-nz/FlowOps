package com.flowops.service;

import com.flowops.config.StorageProperties;
import com.flowops.dto.evidence.EvidenceResponse;
import com.flowops.dto.evidence.EvidenceUploadRequest;
import com.flowops.dto.evidence.EvidenceUploadUrlResponse;
import com.flowops.entity.Company;
import com.flowops.entity.DomainEvent;
import com.flowops.entity.Evidence;
import com.flowops.entity.User;
import com.flowops.entity.WorkOrder;
import com.flowops.entity.WorkOrderStep;
import com.flowops.enums.StepStatus;
import com.flowops.enums.WorkOrderStatus;
import com.flowops.exception.BusinessRuleException;
import com.flowops.exception.ResourceNotFoundException;
import com.flowops.repository.DomainEventRepository;
import com.flowops.repository.EvidenceRepository;
import com.flowops.repository.WorkOrderRepository;
import com.flowops.repository.WorkOrderStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Evidências por etapa (V2.6 — Backlog Detalhado, item 3). O upload usa URL
 * pré-assinada: o arquivo vai do navegador direto ao storage, sem passar pelo
 * backend. Em troca, o registro nasce "pendente" e só entra na galeria depois
 * de confirmado — ver {@link #confirm}.
 */
@Service
@RequiredArgsConstructor
public class EvidenceService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/heic", "application/pdf");

    private final EvidenceRepository evidenceRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderStepRepository workOrderStepRepository;
    private final DomainEventRepository domainEventRepository;
    private final StorageService storageService;
    private final StorageProperties storageProperties;

    @Transactional(readOnly = true)
    public List<EvidenceResponse> list(Long companyId, UUID workOrderUuid, UUID stepUuid) {
        WorkOrderStep step = findStepOrThrow(companyId, workOrderUuid, stepUuid);
        return evidenceRepository
                .findByWorkOrderStepIdAndUploadedAtIsNotNullOrderByCreatedAtAsc(step.getId())
                .stream()
                .map(EvidenceResponse::from)
                .toList();
    }

    @Transactional
    public EvidenceUploadUrlResponse createUploadUrl(
            Long companyId, UUID workOrderUuid, UUID stepUuid, EvidenceUploadRequest request, User actor) {
        WorkOrder workOrder = findWorkOrderOrThrow(companyId, workOrderUuid);
        WorkOrderStep step = findStepOrThrow(companyId, workOrderUuid, stepUuid);

        assertStepAcceptsEvidence(workOrder, step);
        assertContentTypeAllowed(request.contentType());
        assertSizeAllowed(request.sizeBytes());

        // Chave inclui company e work order: facilita auditoria e uma eventual
        // exclusao em massa por empresa direto no storage. O uuid no nome
        // evita colisao entre arquivos homonimos na mesma etapa.
        String objectKey = "company-%d/work-order-%s/step-%s/%s-%s".formatted(
                companyId, workOrderUuid, stepUuid, UUID.randomUUID(), sanitize(request.fileName()));

        Evidence evidence = new Evidence();
        evidence.setCompany(refCompany(companyId));
        evidence.setWorkOrderStep(step);
        evidence.setObjectKey(objectKey);
        evidence.setFileName(request.fileName());
        evidence.setContentType(request.contentType());
        evidence.setSizeBytes(request.sizeBytes());
        evidence.setUploadedBy(actor);
        // uploadedAt fica nulo ate a confirmacao.
        Evidence saved = evidenceRepository.save(evidence);

        return new EvidenceUploadUrlResponse(
                saved.getUuid(),
                storageService.presignUpload(objectKey, request.contentType()),
                storageProperties.getUploadUrlExpirationMinutes());
    }

    /**
     * Chamado depois que o navegador concluiu o PUT. Verifica no storage que
     * o objeto realmente existe antes de tornar a evidência visível — não dá
     * para confiar na palavra do cliente sobre um upload que o backend não
     * presenciou.
     */
    @Transactional
    public EvidenceResponse confirm(Long companyId, UUID workOrderUuid, UUID stepUuid, UUID evidenceUuid, User actor) {
        WorkOrder workOrder = findWorkOrderOrThrow(companyId, workOrderUuid);
        WorkOrderStep step = findStepOrThrow(companyId, workOrderUuid, stepUuid);
        Evidence evidence = findEvidenceOrThrow(companyId, evidenceUuid, step);

        if (evidence.getUploadedAt() != null) {
            return EvidenceResponse.from(evidence);
        }

        Long actualSize = storageService.findObjectSize(evidence.getObjectKey())
                .orElseThrow(() -> new BusinessRuleException(
                        "O arquivo não chegou ao storage — refaça o envio"));

        evidence.setSizeBytes(actualSize);
        evidence.setUploadedAt(OffsetDateTime.now());
        Evidence saved = evidenceRepository.save(evidence);

        recordEvent(workOrder, "EVIDENCIA_ANEXADA", actor,
                "{\"etapa\":\"%s\",\"arquivo\":\"%s\"}".formatted(step.getTitle(), evidence.getFileName()));

        return EvidenceResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public String downloadUrl(Long companyId, UUID workOrderUuid, UUID stepUuid, UUID evidenceUuid) {
        WorkOrderStep step = findStepOrThrow(companyId, workOrderUuid, stepUuid);
        Evidence evidence = findEvidenceOrThrow(companyId, evidenceUuid, step);

        if (evidence.getUploadedAt() == null) {
            throw new ResourceNotFoundException("Evidência não encontrada");
        }

        return storageService.presignDownload(evidence.getObjectKey(), evidence.getFileName());
    }

    @Transactional
    public void delete(Long companyId, UUID workOrderUuid, UUID stepUuid, UUID evidenceUuid, User actor) {
        WorkOrder workOrder = findWorkOrderOrThrow(companyId, workOrderUuid);
        WorkOrderStep step = findStepOrThrow(companyId, workOrderUuid, stepUuid);
        Evidence evidence = findEvidenceOrThrow(companyId, evidenceUuid, step);

        assertStepAcceptsEvidence(workOrder, step);

        // Remove o metadado primeiro: se a exclusao no storage falhar, sobra
        // um objeto orfao (custo de armazenamento) em vez de um registro
        // apontando para um arquivo que nao existe mais (erro na galeria).
        evidenceRepository.delete(evidence);
        storageService.delete(evidence.getObjectKey());

        recordEvent(workOrder, "EVIDENCIA_REMOVIDA", actor,
                "{\"etapa\":\"%s\",\"arquivo\":\"%s\"}".formatted(step.getTitle(), evidence.getFileName()));
    }

    // ---- regras ----

    private void assertStepAcceptsEvidence(WorkOrder workOrder, WorkOrderStep step) {
        WorkOrderStatus status = workOrder.getStatus();
        if (status != WorkOrderStatus.APROVADO && status != WorkOrderStatus.EM_EXECUCAO) {
            throw new BusinessRuleException(
                    "Evidências só podem ser anexadas com o orçamento aprovado e a Ordem de Serviço em execução");
        }
        if (step.getStatus() == StepStatus.CONCLUIDA) {
            throw new BusinessRuleException("Etapa concluída — as evidências não podem mais ser alteradas");
        }
    }

    private void assertContentTypeAllowed(String contentType) {
        if (!ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessRuleException(
                    "Tipo de arquivo não permitido. Aceitos: imagem (JPEG, PNG, WebP, HEIC) ou PDF");
        }
    }

    private void assertSizeAllowed(Long sizeBytes) {
        long maxBytes = (long) storageProperties.getMaxFileSizeMb() * 1024 * 1024;
        if (sizeBytes > maxBytes) {
            throw new BusinessRuleException(
                    "Arquivo maior que o limite de %d MB".formatted(storageProperties.getMaxFileSizeMb()));
        }
    }

    // A chave do objeto nao pode carregar o nome cru: barra criaria pasta
    // inesperada e caracteres fora do ASCII complicam a assinatura da URL.
    private String sanitize(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private WorkOrder findWorkOrderOrThrow(Long companyId, UUID workOrderUuid) {
        return workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(workOrderUuid, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkOrder não encontrada"));
    }

    private WorkOrderStep findStepOrThrow(Long companyId, UUID workOrderUuid, UUID stepUuid) {
        WorkOrder workOrder = findWorkOrderOrThrow(companyId, workOrderUuid);
        return workOrderStepRepository.findByUuidAndWorkOrderId(stepUuid, workOrder.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Etapa não encontrada"));
    }

    // Escopado por company_id E pela etapa: nem um uuid de outra empresa nem
    // um de outra etapa da mesma empresa resolvem.
    private Evidence findEvidenceOrThrow(Long companyId, UUID evidenceUuid, WorkOrderStep step) {
        Evidence evidence = evidenceRepository.findByUuidAndCompanyId(evidenceUuid, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Evidência não encontrada"));

        if (!evidence.getWorkOrderStep().getId().equals(step.getId())) {
            throw new ResourceNotFoundException("Evidência não encontrada");
        }
        return evidence;
    }

    private void recordEvent(WorkOrder workOrder, String eventType, User actor, String payload) {
        DomainEvent event = new DomainEvent();
        event.setWorkOrder(workOrder);
        event.setEventType(eventType);
        event.setActor(actor);
        event.setPayload(payload);
        domainEventRepository.save(event);
    }

    private Company refCompany(Long companyId) {
        Company company = new Company();
        company.setId(companyId);
        return company;
    }
}
