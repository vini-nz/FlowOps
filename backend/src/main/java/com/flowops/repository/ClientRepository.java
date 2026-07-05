package com.flowops.repository;

import com.flowops.entity.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {
    Page<Client> findByCompanyIdAndDeletedAtIsNull(Long companyId, Pageable pageable);
}
