package com.olgav.invoicesecurity.service;

import com.olgav.invoicesecurity.model.User;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("external-face")
public class ExternalFaceVerificationService implements FaceVerificationService {
    @Override
    public void enroll(User user, List<List<Float>> embeddings) {
        throw new UnsupportedOperationException("Configure an external face provider before selecting external-face");
    }

    @Override
    public boolean verify(User user, List<Float> embedding) {
        throw new UnsupportedOperationException("Configure an external face provider before selecting external-face");
    }

    @Override
    public boolean isEnrolled(Long userId) {
        throw new UnsupportedOperationException("Configure an external face provider before selecting external-face");
    }
}
