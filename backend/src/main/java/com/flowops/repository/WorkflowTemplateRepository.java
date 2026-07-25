package com.flowops.repository;

import com.flowops.entity.WorkflowTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowTemplateRepository extends JpaRepository<WorkflowTemplate, Long> {
    List<WorkflowTemplate> findByCompanyIdOrderByNameAsc(Long companyId);

    List<WorkflowTemplate> findByCompanyId(Long companyId);

    // Sempre escopado por company_id: um uuid de outra empresa nunca resolve
    // (mesmo padrao de Client/WorkOrder - ver docs/architecture.md).
    Optional<WorkflowTemplate> findByUuidAndCompanyId(UUID uuid, Long companyId);

    // Usado na criacao da WorkOrder (Sprint 4): se a empresa tem um workflow
    // padrao configurado, as etapas da WorkOrder sao instanciadas a partir dele
    // automaticamente (Fluxo 3 - Planejamento, Negocio e Dominio no Notion).
    // Se nenhum template default existir, a WorkOrder nasce sem etapas - estado
    // valido, nao um erro.
    Optional<WorkflowTemplate> findByCompanyIdAndIsDefaultTrue(Long companyId);
}
