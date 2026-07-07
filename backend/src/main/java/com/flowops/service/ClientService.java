package com.flowops.service;

import com.flowops.dto.client.ClientRequest;
import com.flowops.dto.client.ClientResponse;
import com.flowops.entity.Client;
import com.flowops.entity.Company;
import com.flowops.exception.BusinessRuleException;
import com.flowops.exception.ResourceNotFoundException;
import com.flowops.repository.ClientRepository;
import com.flowops.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final WorkOrderRepository workOrderRepository;

    @Transactional(readOnly = true)
    public Page<ClientResponse> list(Long companyId, String search, Pageable pageable) {
        Page<Client> page = StringUtils.hasText(search)
                ? clientRepository.findByCompanyIdAndDeletedAtIsNullAndNameContainingIgnoreCase(companyId, search, pageable)
                : clientRepository.findByCompanyIdAndDeletedAtIsNull(companyId, pageable);

        return page.map(ClientResponse::from);
    }

    @Transactional(readOnly = true)
    public ClientResponse get(Long companyId, UUID clientUuid) {
        Client client = findOwnedOrThrow(companyId, clientUuid);
        return ClientResponse.from(client);
    }

    @Transactional
    public ClientResponse create(Long companyId, ClientRequest request) {
        assertDocumentAvailable(companyId, request.document(), null);

        Client client = new Client();
        client.setCompany(refCompany(companyId));
        applyRequest(client, request);

        Client saved = clientRepository.save(client);
        return ClientResponse.from(saved);
    }

    @Transactional
    public ClientResponse update(Long companyId, UUID clientUuid, ClientRequest request) {
        Client client = findOwnedOrThrow(companyId, clientUuid);
        assertDocumentAvailable(companyId, request.document(), clientUuid);
        applyRequest(client, request);

        Client saved = clientRepository.save(client);
        return ClientResponse.from(saved);
    }

    /**
     * Exclusao inteligente: um cliente sem nenhuma WorkOrder associada e
     * removido de verdade (DELETE fisico); um cliente com histórico
     * operacional é apenas desativado (soft delete), preservando o
     * histórico e a integridade referencial.
     * <p>
     * A checagem via {@code existsBy} é feita antes de decidir a ação —
     * de propósito, para não depender de capturar uma violação de FK como
     * controle de fluxo. A constraint de FK do banco continua existindo
     * como rede de segurança (ver docs/architecture.md), mas o caminho
     * normal nunca deveria acioná-la.
     */
    @Transactional
    public void deactivate(Long companyId, UUID clientUuid) {
        Client client = findOwnedOrThrow(companyId, clientUuid);

        boolean hasWorkOrders = workOrderRepository.existsByClientId(client.getId());

        if (!hasWorkOrders) {
            clientRepository.delete(client);
            return;
        }

        client.setActive(false);
        client.setDeletedAt(OffsetDateTime.now());
        clientRepository.save(client);
    }

    private Client findOwnedOrThrow(Long companyId, UUID clientUuid) {
        return clientRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(clientUuid, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado"));
    }

    private void assertDocumentAvailable(Long companyId, String document, UUID currentClientUuid) {
        if (!StringUtils.hasText(document)) {
            return;
        }

        // Ao editar, o proprio cliente pode manter seu documento original -
        // so bloqueamos se o documento pertencer a OUTRO cliente da empresa.
        boolean documentInUse = clientRepository.existsByCompanyIdAndDocumentAndDeletedAtIsNull(companyId, document);
        boolean isOwnDocument = currentClientUuid != null
                && clientRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(currentClientUuid, companyId)
                        .map(c -> document.equals(c.getDocument()))
                        .orElse(false);

        if (documentInUse && !isOwnDocument) {
            throw new BusinessRuleException("Já existe um cliente com este documento");
        }
    }

    private void applyRequest(Client client, ClientRequest request) {
        client.setName(request.name());
        client.setEmail(request.email());
        client.setPhone(request.phone());
        client.setDocument(request.document());
        client.setNotes(request.notes());
    }

    // Referencia leve de Company sem round-trip ao banco: o id ja veio
    // validado do JWT (usuario autenticado), so precisamos de uma referencia
    // para o JPA montar a FK no INSERT/UPDATE.
    private Company refCompany(Long companyId) {
        Company company = new Company();
        company.setId(companyId);
        return company;
    }
}
