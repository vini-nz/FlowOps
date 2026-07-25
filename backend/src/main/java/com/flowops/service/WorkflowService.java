package com.flowops.service;

import com.flowops.dto.workflow.ChecklistItemRequest;
import com.flowops.dto.workflow.ChecklistItemResponse;
import com.flowops.dto.workflow.WorkflowStepRequest;
import com.flowops.dto.workflow.WorkflowStepResponse;
import com.flowops.dto.workflow.WorkflowTemplateRequest;
import com.flowops.dto.workflow.WorkflowTemplateResponse;
import com.flowops.entity.Company;
import com.flowops.entity.WorkflowStep;
import com.flowops.entity.WorkflowStepChecklistItem;
import com.flowops.entity.WorkflowTemplate;
import com.flowops.exception.BusinessRuleException;
import com.flowops.exception.ResourceNotFoundException;
import com.flowops.repository.WorkflowStepChecklistItemRepository;
import com.flowops.repository.WorkflowStepRepository;
import com.flowops.repository.WorkflowTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Configuração de workflow por empresa (V2.5). Até aqui os moldes só existiam
 * via {@code seed.sql} — uma empresa nova nunca teria etapa nenhuma, porque
 * {@code WorkOrderService.create} depende de um template padrão que nada no
 * código criava. Este serviço fecha esse buraco e cumpre o "Workflow é
 * configurável por empresa" já documentado em Negócio e Domínio.
 * <p>
 * Editar um molde nunca afeta OS em andamento: as etapas e o checklist são
 * copiados na criação da WorkOrder (ver {@code WorkOrderService.create}),
 * mesmo princípio de snapshot usado em {@code budget_items}.
 */
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowTemplateRepository templateRepository;
    private final WorkflowStepRepository stepRepository;
    private final WorkflowStepChecklistItemRepository checklistItemRepository;

    @Transactional(readOnly = true)
    public List<WorkflowTemplateResponse> list(Long companyId) {
        return templateRepository.findByCompanyIdOrderByNameAsc(companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowTemplateResponse get(Long companyId, UUID templateUuid) {
        return toResponse(findTemplateOrThrow(companyId, templateUuid));
    }

    @Transactional
    public WorkflowTemplateResponse createTemplate(Long companyId, WorkflowTemplateRequest request) {
        WorkflowTemplate template = new WorkflowTemplate();
        template.setCompany(refCompany(companyId));
        template.setName(request.name());

        // Primeiro molde da empresa vira o padrao automaticamente: sem isso o
        // admin criaria um workflow e as WorkOrders continuariam nascendo sem
        // etapas, sem nenhuma pista do porque.
        boolean isFirst = templateRepository.findByCompanyId(companyId).isEmpty();
        boolean wantsDefault = Boolean.TRUE.equals(request.isDefault()) || isFirst;

        if (wantsDefault) {
            clearCurrentDefault(companyId);
        }
        template.setDefault(wantsDefault);

        return toResponse(templateRepository.save(template));
    }

    @Transactional
    public WorkflowTemplateResponse updateTemplate(Long companyId, UUID templateUuid, WorkflowTemplateRequest request) {
        WorkflowTemplate template = findTemplateOrThrow(companyId, templateUuid);
        template.setName(request.name());

        if (Boolean.TRUE.equals(request.isDefault()) && !template.isDefault()) {
            clearCurrentDefault(companyId);
            template.setDefault(true);
        } else if (Boolean.FALSE.equals(request.isDefault()) && template.isDefault()) {
            throw new BusinessRuleException(
                    "Defina outro workflow como padrão em vez de remover o padrão deste");
        }

        return toResponse(templateRepository.save(template));
    }

    @Transactional
    public void deleteTemplate(Long companyId, UUID templateUuid) {
        WorkflowTemplate template = findTemplateOrThrow(companyId, templateUuid);

        // Deixar a empresa sem padrao devolveria o sistema ao estado que a
        // V2.5 veio corrigir: WorkOrders nascendo sem etapa nenhuma.
        if (template.isDefault() && templateRepository.findByCompanyId(companyId).size() > 1) {
            throw new BusinessRuleException(
                    "Defina outro workflow como padrão antes de excluir este");
        }

        // As etapas somem por CASCADE; as OS que as usavam mantêm título e
        // ordem (cópias próprias) e perdem só o ponteiro de origem, graças ao
        // ON DELETE SET NULL em work_order_steps.workflow_step_id.
        templateRepository.delete(template);
    }

    @Transactional
    public WorkflowTemplateResponse addStep(Long companyId, UUID templateUuid, WorkflowStepRequest request) {
        WorkflowTemplate template = findTemplateOrThrow(companyId, templateUuid);

        WorkflowStep step = new WorkflowStep();
        step.setWorkflowTemplate(template);
        step.setTitle(request.title());
        step.setStepOrder(nextStepOrder(template.getId()));
        stepRepository.save(step);

        return toResponse(template);
    }

    @Transactional
    public WorkflowTemplateResponse updateStep(
            Long companyId, UUID templateUuid, UUID stepUuid, WorkflowStepRequest request) {
        WorkflowTemplate template = findTemplateOrThrow(companyId, templateUuid);
        WorkflowStep step = findStepOrThrow(template, stepUuid);

        step.setTitle(request.title());
        stepRepository.save(step);

        return toResponse(template);
    }

    @Transactional
    public WorkflowTemplateResponse deleteStep(Long companyId, UUID templateUuid, UUID stepUuid) {
        WorkflowTemplate template = findTemplateOrThrow(companyId, templateUuid);
        WorkflowStep step = findStepOrThrow(template, stepUuid);

        stepRepository.delete(step);
        resequenceSteps(template.getId());

        return toResponse(template);
    }

    /**
     * Move a etapa uma posição para cima ou para baixo. Reordenar importa mais
     * desde a V2.4: {@code step_order} é o que define a ordem obrigatória de
     * execução (ADR-0003).
     */
    @Transactional
    public WorkflowTemplateResponse moveStep(Long companyId, UUID templateUuid, UUID stepUuid, boolean up) {
        WorkflowTemplate template = findTemplateOrThrow(companyId, templateUuid);
        WorkflowStep step = findStepOrThrow(template, stepUuid);

        List<WorkflowStep> steps = stepRepository.findByWorkflowTemplateIdOrderByStepOrderAsc(template.getId());
        int index = steps.indexOf(steps.stream()
                .filter(s -> s.getId().equals(step.getId()))
                .findFirst()
                .orElseThrow());
        int targetIndex = up ? index - 1 : index + 1;

        if (targetIndex < 0 || targetIndex >= steps.size()) {
            throw new BusinessRuleException("A etapa já está no limite da ordem");
        }

        WorkflowStep other = steps.get(targetIndex);
        // Passo intermediario com ordem negativa: a constraint
        // uq_workflow_step_order e imediata, entao trocar os dois valores
        // direto violaria a unicidade no meio da operacao.
        int stepOrder = step.getStepOrder();
        int otherOrder = other.getStepOrder();

        step.setStepOrder(-1);
        stepRepository.saveAndFlush(step);
        other.setStepOrder(stepOrder);
        stepRepository.saveAndFlush(other);
        step.setStepOrder(otherOrder);
        stepRepository.saveAndFlush(step);

        return toResponse(template);
    }

    @Transactional
    public WorkflowTemplateResponse addChecklistItem(
            Long companyId, UUID templateUuid, UUID stepUuid, ChecklistItemRequest request) {
        WorkflowTemplate template = findTemplateOrThrow(companyId, templateUuid);
        WorkflowStep step = findStepOrThrow(template, stepUuid);

        WorkflowStepChecklistItem item = new WorkflowStepChecklistItem();
        item.setWorkflowStep(step);
        item.setDescription(request.description());
        item.setItemOrder(nextChecklistOrder(step.getId()));
        checklistItemRepository.save(item);

        return toResponse(template);
    }

    @Transactional
    public WorkflowTemplateResponse deleteChecklistItem(
            Long companyId, UUID templateUuid, UUID stepUuid, UUID itemUuid) {
        WorkflowTemplate template = findTemplateOrThrow(companyId, templateUuid);
        WorkflowStep step = findStepOrThrow(template, stepUuid);

        WorkflowStepChecklistItem item = checklistItemRepository
                .findByUuidAndWorkflowStepId(itemUuid, step.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Item de checklist não encontrado"));

        checklistItemRepository.delete(item);

        return toResponse(template);
    }

    // ---- apoio ----

    private void clearCurrentDefault(Long companyId) {
        templateRepository.findByCompanyIdAndIsDefaultTrue(companyId).ifPresent(current -> {
            current.setDefault(false);
            // Flush imediato: o indice parcial uq_workflow_templates_single_default
            // rejeitaria dois padroes coexistindo, mesmo que por um instante.
            templateRepository.saveAndFlush(current);
        });
    }

    private int nextStepOrder(Long templateId) {
        return stepRepository.findFirstByWorkflowTemplateIdOrderByStepOrderDesc(templateId)
                .map(last -> last.getStepOrder() + 1)
                .orElse(1);
    }

    private int nextChecklistOrder(Long stepId) {
        return checklistItemRepository.findFirstByWorkflowStepIdOrderByItemOrderDesc(stepId)
                .map(last -> last.getItemOrder() + 1)
                .orElse(1);
    }

    // Reordena apos exclusao para nao deixar buracos (1,3,4 -> 1,2,3). A
    // ordem so precisa ser relativa, mas buracos confundem quem le o molde.
    private void resequenceSteps(Long templateId) {
        List<WorkflowStep> steps = stepRepository.findByWorkflowTemplateIdOrderByStepOrderAsc(templateId);
        // Desloca todas para uma faixa livre antes de renumerar, senao a
        // constraint de unicidade bate no meio do caminho.
        for (int i = 0; i < steps.size(); i++) {
            steps.get(i).setStepOrder(-(i + 1));
        }
        stepRepository.saveAllAndFlush(steps);
        for (int i = 0; i < steps.size(); i++) {
            steps.get(i).setStepOrder(i + 1);
        }
        stepRepository.saveAllAndFlush(steps);
    }

    private WorkflowTemplate findTemplateOrThrow(Long companyId, UUID templateUuid) {
        return templateRepository.findByUuidAndCompanyId(templateUuid, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow não encontrado"));
    }

    private WorkflowStep findStepOrThrow(WorkflowTemplate template, UUID stepUuid) {
        return stepRepository.findByUuidAndWorkflowTemplateId(stepUuid, template.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Etapa do workflow não encontrada"));
    }

    private WorkflowTemplateResponse toResponse(WorkflowTemplate template) {
        List<WorkflowStepResponse> steps =
                stepRepository.findByWorkflowTemplateIdOrderByStepOrderAsc(template.getId()).stream()
                        .map(step -> WorkflowStepResponse.from(step,
                                checklistItemRepository.findByWorkflowStepIdOrderByItemOrderAsc(step.getId()).stream()
                                        .map(ChecklistItemResponse::from)
                                        .toList()))
                        .toList();

        return WorkflowTemplateResponse.from(template, steps);
    }

    private Company refCompany(Long companyId) {
        Company company = new Company();
        company.setId(companyId);
        return company;
    }
}
