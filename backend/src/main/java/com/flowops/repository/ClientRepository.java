package com.flowops.repository;

import com.flowops.entity.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, Long> {
    Page<Client> findByCompanyIdAndDeletedAtIsNull(Long companyId, Pageable pageable);

    Page<Client> findByCompanyIdAndDeletedAtIsNullAndNameContainingIgnoreCase(
            Long companyId, String search, Pageable pageable);

    // Busca por UUID, nunca pelo id sequencial interno: o id BIGSERIAL nunca
    // e exposto na API (decisao de arquitetura - ver Arquitetura Tecnica no
    // Notion), entao toda rota que recebe um identificador do cliente HTTP
    // usa o uuid. O filtro por company_id reforca o isolamento multi-tenant (D-06).
    Optional<Client> findByUuidAndCompanyIdAndDeletedAtIsNull(UUID uuid, Long companyId);

    // Espelha o indice parcial uq_clients_company_document do DDL: documento
    // e opcional, mas quando informado nao pode se repetir na mesma empresa.
    boolean existsByCompanyIdAndDocumentAndDeletedAtIsNull(Long companyId, String document);
}

