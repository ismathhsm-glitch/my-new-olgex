package com.olgav.invoicesecurity.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olgav.invoicesecurity.model.FaceTemplate;
import com.olgav.invoicesecurity.model.User;
import com.olgav.invoicesecurity.repository.FaceTemplateRepository;
import com.olgav.invoicesecurity.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("!external-face")
public class ClientEmbeddingFaceVerificationService implements FaceVerificationService {
    private static final int EMBEDDING_SIZE = 128;

    private final FaceTemplateRepository faceTemplateRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final int samplesRequired;
    private final double similarityThreshold;

    public ClientEmbeddingFaceVerificationService(
            FaceTemplateRepository faceTemplateRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            @Value("${app.security.face.samples-required:3}") int samplesRequired,
            @Value("${app.security.face.similarity-threshold:0.92}") double similarityThreshold) {
        this.faceTemplateRepository = faceTemplateRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.samplesRequired = samplesRequired;
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    @Transactional
    public void enroll(User user, List<List<Float>> embeddings) {
        if (embeddings == null || embeddings.size() != samplesRequired) {
            throw new IllegalArgumentException("Exactly " + samplesRequired + " face samples are required");
        }
        for (List<Float> embedding : embeddings) validate(embedding);
        faceTemplateRepository.deleteByUserId(user.getId());
        for (int index = 0; index < embeddings.size(); index++) {
            try {
                FaceTemplate template = new FaceTemplate();
                template.setUser(user);
                template.setSampleIndex(index);
                template.setEmbedding(objectMapper.writeValueAsString(embeddings.get(index)));
                faceTemplateRepository.save(template);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to encode face embedding for storage", e);
            }
        }
        user.setFaceEnrolled(true);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public boolean verify(User user, List<Float> embedding) {
        validate(embedding);
        List<FaceTemplate> templates = faceTemplateRepository.findByUserIdOrderBySampleIndex(user.getId());
        if (templates.isEmpty()) return false;
        double highestSimilarity = -1;
        for (FaceTemplate template : templates) {
            try {
                List<Float> stored = objectMapper.readValue(template.getEmbedding(), new TypeReference<>() {});
                highestSimilarity = Math.max(highestSimilarity, cosineSimilarity(embedding, stored));
            } catch (Exception e) {
                throw new IllegalStateException("Unable to read stored face embedding", e);
            }
        }
        return highestSimilarity >= similarityThreshold;
    }

    @Override
    public boolean isEnrolled(Long userId) {
        return !faceTemplateRepository.findByUserIdOrderBySampleIndex(userId).isEmpty();
    }

    private static void validate(List<Float> embedding) {
        if (embedding == null || embedding.size() != EMBEDDING_SIZE
                || embedding.stream().anyMatch(value -> value == null || !Float.isFinite(value)
                || value < -1.0f || value > 1.0f)) {
            throw new IllegalArgumentException("Face embedding must contain 128 finite values in the range [-1, 1]");
        }
    }

    private static double cosineSimilarity(List<Float> left, List<Float> right) {
        validate(right);
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < EMBEDDING_SIZE; i++) {
            double a = left.get(i);
            double b = right.get(i);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm == 0 || rightNorm == 0) return -1;
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
