package com.olgav.invoicesecurity.service;

import com.olgav.invoicesecurity.model.SensitiveAction;
import com.olgav.invoicesecurity.model.StepUpToken;
import com.olgav.invoicesecurity.model.Organization;
import com.olgav.invoicesecurity.model.User;
import com.olgav.invoicesecurity.repository.StepUpTokenRepository;
import com.olgav.invoicesecurity.security.StepUpTokenConsumer;
import com.olgav.invoicesecurity.service.AuditLoggingService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class StepUpTokenConsumerTest {
    private final StepUpTokenRepository repository = mock(StepUpTokenRepository.class);
    private final StepUpTokenConsumer consumer = new StepUpTokenConsumer(repository, mock(AuditLoggingService.class));

    @Test
    void consumesAnActionBoundTokenExactlyOnce() throws Exception {
        StepUpToken token = new StepUpToken();
        token.setId(1L);
        User user = new User();
        user.setId(15L);
        Organization organization = new Organization();
        organization.setId(2L);
        user.setOrganization(organization);
        token.setUser(user);
        token.setAction(SensitiveAction.CHANGE_BANK_DETAILS);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(2));
        when(repository.findForUpdate(hash("raw-token"), 15L)).thenReturn(java.util.Optional.of(token));

        consumer.consume("raw-token", 15L, SensitiveAction.CHANGE_BANK_DETAILS);

        org.junit.jupiter.api.Assertions.assertTrue(token.isUsed());
        verify(repository).save(token);
    }

    @Test
    void rejectsTokenBoundToAnotherAction() throws Exception {
        StepUpToken token = new StepUpToken();
        User user = new User();
        user.setId(15L);
        token.setUser(user);
        token.setAction(SensitiveAction.DELETE_INVOICE);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(2));
        when(repository.findForUpdate(hash("raw-token"), 15L)).thenReturn(java.util.Optional.of(token));

        assertThrows(ResponseStatusException.class,
                () -> consumer.consume("raw-token", 15L, SensitiveAction.CHANGE_BANK_DETAILS));
        verify(repository, never()).save(token);
    }

    private static String hash(String raw) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }
}
