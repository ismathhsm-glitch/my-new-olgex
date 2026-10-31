package com.olgav.invoicesecurity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olgav.invoicesecurity.model.FaceTemplate;
import com.olgav.invoicesecurity.model.User;
import com.olgav.invoicesecurity.repository.FaceTemplateRepository;
import com.olgav.invoicesecurity.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FaceVerificationServiceTest {
    private final FaceTemplateRepository templates = mock(FaceTemplateRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ClientEmbeddingFaceVerificationService service =
            new ClientEmbeddingFaceVerificationService(templates, users, new ObjectMapper(), 3, 0.92);

    @Test
    void acceptsMatchingFaceEmbedding() throws Exception {
        List<Float> expected = vector(0);
        FaceTemplate template = new FaceTemplate();
        template.setEmbedding(new ObjectMapper().writeValueAsString(expected));
        when(templates.findByUserIdOrderBySampleIndex(12L)).thenReturn(List.of(template));

        assertTrue(service.verify(user(12L), expected));
    }

    @Test
    void rejectsDifferentFaceEmbedding() throws Exception {
        FaceTemplate template = new FaceTemplate();
        template.setEmbedding(new ObjectMapper().writeValueAsString(vector(0)));
        when(templates.findByUserIdOrderBySampleIndex(12L)).thenReturn(List.of(template));

        assertFalse(service.verify(user(12L), vector(1)));
    }

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static List<Float> vector(int activeIndex) {
        List<Float> vector = new ArrayList<>(java.util.Collections.nCopies(128, 0.0f));
        vector.set(activeIndex, 1.0f);
        return vector;
    }
}
