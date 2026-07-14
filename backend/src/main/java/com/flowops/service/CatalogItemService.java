package com.flowops.service;

import com.flowops.dto.catalog.CatalogItemRequest;
import com.flowops.dto.catalog.CatalogItemResponse;
import com.flowops.entity.CatalogItem;
import com.flowops.entity.Company;
import com.flowops.exception.ResourceNotFoundException;
import com.flowops.repository.CatalogItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogItemService {

    private final CatalogItemRepository catalogItemRepository;

    @Transactional(readOnly = true)
    public Page<CatalogItemResponse> list(Long companyId, String search, Pageable pageable) {
        Page<CatalogItem> page = StringUtils.hasText(search)
                ? catalogItemRepository.findByCompanyIdAndActiveTrueAndNameContainingIgnoreCase(companyId, search, pageable)
                : catalogItemRepository.findByCompanyIdAndActiveTrue(companyId, pageable);

        return page.map(CatalogItemResponse::from);
    }

    @Transactional
    public CatalogItemResponse create(Long companyId, CatalogItemRequest request) {
        CatalogItem item = new CatalogItem();
        item.setCompany(refCompany(companyId));
        applyRequest(item, request);

        return CatalogItemResponse.from(catalogItemRepository.save(item));
    }

    @Transactional
    public CatalogItemResponse update(Long companyId, UUID uuid, CatalogItemRequest request) {
        CatalogItem item = findOwnedOrThrow(companyId, uuid);
        applyRequest(item, request);

        return CatalogItemResponse.from(catalogItemRepository.save(item));
    }

    @Transactional
    public void deactivate(Long companyId, UUID uuid) {
        CatalogItem item = findOwnedOrThrow(companyId, uuid);
        item.setActive(false);
        catalogItemRepository.save(item);
    }

    private CatalogItem findOwnedOrThrow(Long companyId, UUID uuid) {
        return catalogItemRepository.findByUuidAndCompanyId(uuid, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Item de catálogo não encontrado"));
    }

    private void applyRequest(CatalogItem item, CatalogItemRequest request) {
        item.setName(request.name());
        item.setDescription(request.description());
        item.setUnitPrice(request.unitPrice());
        item.setUnit(StringUtils.hasText(request.unit()) ? request.unit() : "UN");
    }

    // Referencia leve de Company sem round-trip ao banco: o id ja veio
    // validado do JWT (usuario autenticado), so precisamos de uma referencia
    // para o JPA montar a FK no INSERT/UPDATE (mesmo padrao de ClientService).
    private Company refCompany(Long companyId) {
        Company company = new Company();
        company.setId(companyId);
        return company;
    }
}
