package com.flowops.controller;

import com.flowops.dto.catalog.CatalogItemRequest;
import com.flowops.dto.catalog.CatalogItemResponse;
import com.flowops.entity.User;
import com.flowops.service.CatalogItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog-items")
@RequiredArgsConstructor
public class CatalogItemController {

    private final CatalogItemService catalogItemService;

    @GetMapping
    public Page<CatalogItemResponse> list(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return catalogItemService.list(user.getCompany().getId(), search, pageable);
    }

    // Matriz de permissões (Negócio e Domínio, Notion): mesma restrição de
    // "Criar Orçamentos" - Técnico não gerencia catálogo.
    @PreAuthorize("hasAnyRole('ADMIN_EMPRESA', 'OPERADOR')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogItemResponse create(@AuthenticationPrincipal User user, @Valid @RequestBody CatalogItemRequest request) {
        return catalogItemService.create(user.getCompany().getId(), request);
    }

    @PreAuthorize("hasAnyRole('ADMIN_EMPRESA', 'OPERADOR')")
    @PutMapping("/{uuid}")
    public CatalogItemResponse update(
            @AuthenticationPrincipal User user,
            @PathVariable UUID uuid,
            @Valid @RequestBody CatalogItemRequest request) {
        return catalogItemService.update(user.getCompany().getId(), uuid, request);
    }

    @PreAuthorize("hasAnyRole('ADMIN_EMPRESA', 'OPERADOR')")
    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal User user, @PathVariable UUID uuid) {
        catalogItemService.deactivate(user.getCompany().getId(), uuid);
    }
}
