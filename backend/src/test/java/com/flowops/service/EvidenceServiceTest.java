package com.flowops.service;

import com.flowops.config.StorageProperties;
import com.flowops.dto.evidence.EvidenceUploadRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regras de evidência (V2.6). O foco é o que o fluxo de URL pré-assinada
 * introduz de próprio: validação antes de emitir a URL, e a confirmação que
 * não confia na palavra do cliente sobre um upload que o backend não viu.
 */
@ExtendWith(MockitoExtension.class)
class EvidenceServiceTest {

    @Mock private EvidenceRepository evidenceRepository;
    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private WorkOrderStepRepository workOrderStepRepository;
    @Mock private DomainEventRepository domainEventRepository;
    @Mock private StorageService storageService;

    private EvidenceService service;
    private StorageProperties properties;

    private static final Long COMPANY_ID = 1L;
    private User actor;
    private WorkOrder workOrder;
    private WorkOrderStep step;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties();
        properties.setBucket("test");
        properties.setMaxFileSizeMb(15);
        properties.setUploadUrlExpirationMinutes(10);

        service = new EvidenceService(evidenceRepository, workOrderRepository,
                workOrderStepRepository, domainEventRepository, storageService, properties);

        actor = new User();
        actor.setId(9L);

        workOrder = new WorkOrder();
        workOrder.setId(10L);
        workOrder.setUuid(UUID.randomUUID());
        workOrder.setStatus(WorkOrderStatus.EM_EXECUCAO);

        step = new WorkOrderStep();
        step.setId(20L);
        step.setUuid(UUID.randomUUID());
        step.setWorkOrder(workOrder);
        step.setTitle("Produção");
        step.setStatus(StepStatus.EM_ANDAMENTO);
    }

    private void givenWorkOrderAndStep() {
        when(workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(workOrder.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(workOrder));
        when(workOrderStepRepository.findByUuidAndWorkOrderId(step.getUuid(), workOrder.getId()))
                .thenReturn(Optional.of(step));
    }

    private EvidenceUploadRequest request(String fileName, String contentType, long size) {
        return new EvidenceUploadRequest(fileName, contentType, size);
    }

    @Test
    void createUploadUrl_registersPendingEvidenceAndReturnsSignedUrl() {
        givenWorkOrderAndStep();
        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(inv -> {
            Evidence e = inv.getArgument(0);
            e.setUuid(UUID.randomUUID());
            return e;
        });
        when(storageService.presignUpload(anyString(), anyString())).thenReturn("http://localhost:9000/assinada");

        var response = service.createUploadUrl(
                COMPANY_ID, workOrder.getUuid(), step.getUuid(),
                request("foto.png", "image/png", 1024), actor);

        assertThat(response.uploadUrl()).isEqualTo("http://localhost:9000/assinada");
        assertThat(response.expiresInMinutes()).isEqualTo(10);
    }

    @Test
    void createUploadUrl_rejectsDisallowedContentType() {
        givenWorkOrderAndStep();

        assertThatThrownBy(() -> service.createUploadUrl(
                COMPANY_ID, workOrder.getUuid(), step.getUuid(),
                request("virus.exe", "application/x-msdownload", 100), actor))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Tipo de arquivo não permitido");

        verify(evidenceRepository, never()).save(any());
        verify(storageService, never()).presignUpload(anyString(), anyString());
    }

    @Test
    void createUploadUrl_rejectsFileOverSizeLimit() {
        givenWorkOrderAndStep();

        assertThatThrownBy(() -> service.createUploadUrl(
                COMPANY_ID, workOrder.getUuid(), step.getUuid(),
                request("grande.png", "image/png", 20L * 1024 * 1024), actor))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("15 MB");

        verify(storageService, never()).presignUpload(anyString(), anyString());
    }

    @Test
    void createUploadUrl_rejectsWhenWorkOrderNotExecutable() {
        workOrder.setStatus(WorkOrderStatus.SOLICITACAO_RECEBIDA);
        givenWorkOrderAndStep();

        assertThatThrownBy(() -> service.createUploadUrl(
                COMPANY_ID, workOrder.getUuid(), step.getUuid(),
                request("foto.png", "image/png", 1024), actor))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("orçamento aprovado");
    }

    @Test
    void createUploadUrl_rejectsWhenStepAlreadyCompleted() {
        step.setStatus(StepStatus.CONCLUIDA);
        givenWorkOrderAndStep();

        assertThatThrownBy(() -> service.createUploadUrl(
                COMPANY_ID, workOrder.getUuid(), step.getUuid(),
                request("foto.png", "image/png", 1024), actor))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Etapa concluída");
    }

    @Test
    void confirm_failsWhenObjectNeverReachedStorage() {
        givenWorkOrderAndStep();
        Evidence evidence = pendingEvidence();
        when(evidenceRepository.findByUuidAndCompanyId(evidence.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(evidence));
        when(storageService.findObjectSize(evidence.getObjectKey())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(
                COMPANY_ID, workOrder.getUuid(), step.getUuid(), evidence.getUuid(), actor))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("não chegou ao storage");

        assertThat(evidence.getUploadedAt()).isNull();
    }

    @Test
    void confirm_usesRealSizeFromStorageNotTheClaimedOne() {
        givenWorkOrderAndStep();
        Evidence evidence = pendingEvidence();
        evidence.setSizeBytes(1L); // tamanho informado pelo cliente
        when(evidenceRepository.findByUuidAndCompanyId(evidence.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(evidence));
        when(storageService.findObjectSize(evidence.getObjectKey())).thenReturn(Optional.of(4096L));
        when(evidenceRepository.save(any(Evidence.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.confirm(
                COMPANY_ID, workOrder.getUuid(), step.getUuid(), evidence.getUuid(), actor);

        assertThat(response.sizeBytes()).isEqualTo(4096L);
        assertThat(evidence.getUploadedAt()).isNotNull();
        verify(domainEventRepository).save(any());
    }

    @Test
    void downloadUrl_refusesEvidenceThatWasNeverConfirmed() {
        givenWorkOrderAndStep();
        Evidence evidence = pendingEvidence();
        when(evidenceRepository.findByUuidAndCompanyId(evidence.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(evidence));

        assertThatThrownBy(() -> service.downloadUrl(
                COMPANY_ID, workOrder.getUuid(), step.getUuid(), evidence.getUuid()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void evidenceFromAnotherStepIsNotFound() {
        givenWorkOrderAndStep();
        Evidence evidence = pendingEvidence();
        WorkOrderStep otherStep = new WorkOrderStep();
        otherStep.setId(999L);
        evidence.setWorkOrderStep(otherStep);
        when(evidenceRepository.findByUuidAndCompanyId(evidence.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(evidence));

        assertThatThrownBy(() -> service.downloadUrl(
                COMPANY_ID, workOrder.getUuid(), step.getUuid(), evidence.getUuid()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Evidence pendingEvidence() {
        Evidence evidence = new Evidence();
        evidence.setId(30L);
        evidence.setUuid(UUID.randomUUID());
        evidence.setWorkOrderStep(step);
        evidence.setObjectKey("company-1/wo/step/arquivo.png");
        evidence.setFileName("arquivo.png");
        evidence.setContentType("image/png");
        evidence.setUploadedBy(actor);
        return evidence;
    }
}
