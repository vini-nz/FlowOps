package com.flowops.repository;

import com.flowops.entity.CatalogItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, Long> {

    Page<CatalogItem> findByCompanyIdAndActiveTrue(Long companyId, Pageable pageable);

    Page<CatalogItem> findByCompanyIdAndActiveTrueAndNameContainingIgnoreCase(
            Long companyId, String name, Pageable pageable);

    Optional<CatalogItem> findByUuidAndCompanyId(UUID uuid, Long companyId);
}
