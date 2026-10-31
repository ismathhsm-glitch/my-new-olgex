package com.olgav.invoicesecurity.service;

import com.olgav.invoicesecurity.model.User;

import java.util.List;

public interface FaceVerificationService {
    void enroll(User user, List<List<Float>> embeddings);
    boolean verify(User user, List<Float> embedding);
    boolean isEnrolled(Long userId);
}
