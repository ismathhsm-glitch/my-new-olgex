package com.olgav.invoicesecurity.service;

import com.olgav.invoicesecurity.model.Invoice;
import com.olgav.invoicesecurity.repository.InvoiceRepository;
import com.olgav.invoicesecurity.repository.OrganizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final OrganizationRepository organizationRepository;

    public InvoiceService(InvoiceRepository invoiceRepository, OrganizationRepository organizationRepository) {
        this.invoiceRepository = invoiceRepository;
        this.organizationRepository = organizationRepository;
    }

    public Invoice createInvoice(Invoice invoice, Long organizationId) {
        invoice.setOrganization(organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found")));
        return invoiceRepository.save(invoice);
    }

    public List<Invoice> getAllInvoices(Long organizationId) {
        return invoiceRepository.findByOrganizationId(organizationId);
    }

    public Invoice getInvoiceById(Long id, Long organizationId) {
        return invoiceRepository.findByIdAndOrganizationId(id, organizationId).orElse(null);
    }

    public void deleteInvoice(Long id, Long organizationId) {
        invoiceRepository.deleteByIdAndOrganizationId(id, organizationId);
    }
}