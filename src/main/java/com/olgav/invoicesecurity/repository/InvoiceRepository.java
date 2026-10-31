package com.olgav.invoicesecurity.repository;

import com.olgav.invoicesecurity.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByOrganizationId(Long organizationId);
    Optional<Invoice> findByIdAndOrganizationId(Long id, Long organizationId);
    void deleteByIdAndOrganizationId(Long id, Long organizationId);
}