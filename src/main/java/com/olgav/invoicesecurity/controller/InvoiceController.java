package com.olgav.invoicesecurity.controller;

import com.olgav.invoicesecurity.dto.ApiResponse;
import com.olgav.invoicesecurity.model.Invoice;
import com.olgav.invoicesecurity.model.SensitiveAction;
import com.olgav.invoicesecurity.model.User;
import com.olgav.invoicesecurity.repository.UserRepository;
import com.olgav.invoicesecurity.security.RequiresStepUp;
import com.olgav.invoicesecurity.security.StepUpTokenConsumer;
import com.olgav.invoicesecurity.service.InvoiceService;
import com.olgav.invoicesecurity.service.AuditLoggingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/invoices")
@PreAuthorize("hasAnyRole('ORG_ADMIN', 'ACCOUNTANT', 'STAFF')")
@SecurityRequirement(name = "bearerAuth")
public class InvoiceController {
    private final InvoiceService invoiceService;
    private final StepUpTokenConsumer stepUpTokenConsumer;
    private final UserRepository userRepository;
    private final AuditLoggingService auditLoggingService;
    private final BigDecimal highValueThreshold;

    public InvoiceController(InvoiceService invoiceService,
                             StepUpTokenConsumer stepUpTokenConsumer,
                             UserRepository userRepository,
                             AuditLoggingService auditLoggingService,
                             @Value("${app.security.step-up.high-value-invoice-threshold:10000}")
                             BigDecimal highValueThreshold) {
        this.invoiceService = invoiceService;
        this.stepUpTokenConsumer = stepUpTokenConsumer;
        this.userRepository = userRepository;
        this.auditLoggingService = auditLoggingService;
        this.highValueThreshold = highValueThreshold;
    }

    @Operation(summary = "Create an invoice")
    @PostMapping
    public ApiResponse<InvoiceDto> create(@Valid @RequestBody InvoiceRequest request, Authentication authentication) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(request.invoiceNumber());
        invoice.setAmount(request.amount());
        invoice.setDueDate(request.dueDate());
        invoice.setStatus("DRAFT");
        Long organizationId = organizationId(authentication);
        Invoice created = invoiceService.createInvoice(invoice, organizationId);
        auditLoggingService.record((Long) authentication.getPrincipal(), organizationId,
                "INVOICE_CREATED", "invoices", created.getId().toString(), null,
                com.olgav.invoicesecurity.model.VerificationResult.NOT_REQUIRED, null);
        return ApiResponse.ok(InvoiceDto.from(created));
    }

    @Operation(summary = "List invoices")
    @GetMapping
    public ApiResponse<List<InvoiceDto>> list(Authentication authentication) {
        return ApiResponse.ok(invoiceService.getAllInvoices(organizationId(authentication)).stream()
                .map(InvoiceDto::from).toList());
    }

    @Operation(summary = "Get an invoice")
    @GetMapping("/{id}")
    public ApiResponse<InvoiceDto> get(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(InvoiceDto.from(findInvoice(id, organizationId(authentication))));
    }

    @Operation(summary = "Approve an invoice; high-value approvals require fresh face verification")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ACCOUNTANT')")
    public ApiResponse<InvoiceDto> approve(@PathVariable Long id,
                                           @RequestHeader(name = "X-StepUp-Token", required = false) String stepUpToken,
                                           Authentication authentication) {
        Long organizationId = organizationId(authentication);
        Invoice invoice = findInvoice(id, organizationId);
        if (invoice.getAmount() != null && invoice.getAmount().compareTo(highValueThreshold) >= 0) {
            if (stepUpToken == null || stepUpToken.isBlank()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "X-StepUp-Token is required for a high-value approval");
            }
            stepUpTokenConsumer.consume(stepUpToken, (Long) authentication.getPrincipal(),
                    SensitiveAction.APPROVE_HIGH_VALUE_INVOICE);
        }
        invoice.setStatus("APPROVED");
        Invoice approved = invoiceService.createInvoice(invoice, organizationId);
        auditLoggingService.record((Long) authentication.getPrincipal(), organizationId,
                "INVOICE_APPROVED", "invoices", approved.getId().toString(), null,
                com.olgav.invoicesecurity.model.VerificationResult.NOT_REQUIRED, null);
        return ApiResponse.ok(InvoiceDto.from(approved));
    }

    @Operation(summary = "Delete an invoice (requires fresh face verification)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ACCOUNTANT')")
    @RequiresStepUp(action = SensitiveAction.DELETE_INVOICE)
    public ApiResponse<Map<String, String>> delete(@PathVariable Long id, Authentication authentication) {
        Long organizationId = organizationId(authentication);
        findInvoice(id, organizationId);
        invoiceService.deleteInvoice(id, organizationId);
        auditLoggingService.record((Long) authentication.getPrincipal(), organizationId,
                "INVOICE_DELETED", "invoices", id.toString(), null,
                com.olgav.invoicesecurity.model.VerificationResult.SUCCESS, null);
        return ApiResponse.ok(Map.of("status", "INVOICE_DELETED"));
    }

    private Invoice findInvoice(Long id, Long organizationId) {
        Invoice invoice = invoiceService.getInvoiceById(id, organizationId);
        if (invoice == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found");
        return invoice;
    }

    private Long organizationId(Authentication authentication) {
        User user = userRepository.findById((Long) authentication.getPrincipal()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getOrganization() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No current organization");
        }
        return user.getOrganization().getId();
    }

    public record InvoiceRequest(@NotBlank String invoiceNumber,
                                 @NotNull @DecimalMin("0.01") BigDecimal amount,
                                 @NotNull LocalDate dueDate) {
    }

    public record InvoiceDto(Long id, String invoiceNumber, BigDecimal amount, String status, LocalDate dueDate) {
        static InvoiceDto from(Invoice invoice) {
            return new InvoiceDto(invoice.getId(), invoice.getInvoiceNumber(), invoice.getAmount(),
                    invoice.getStatus(), invoice.getDueDate());
        }
    }
}
