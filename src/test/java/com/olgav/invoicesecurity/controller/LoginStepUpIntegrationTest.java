package com.olgav.invoicesecurity.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olgav.invoicesecurity.model.*;
import com.olgav.invoicesecurity.repository.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginStepUpIntegrationTest {
    private static final String PASSWORD = "Strong-Pass123!";
    private static final String DEVICE_HASH = "a".repeat(64);
    private static final String USER_AGENT = "OlgaxIntegrationTest";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired UserRepository userRepository;
    @Autowired OrganizationMemberRepository memberRepository;
    @Autowired TrustedDeviceRepository deviceRepository;
    @Autowired FaceTemplateRepository faceTemplateRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeAll
    static void configureEncryptionKey() {
        System.setProperty("AES_GCM_KEY", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }

    @Test
    void logsInAndUsesFaceVerifiedSingleUseStepUpToken() throws Exception {
        List<Float> embedding = faceEmbedding();
        User user = createUser(embedding);

        String loginBody = objectMapper.writeValueAsString(java.util.Map.of(
                "email", user.getEmail(), "password", PASSWORD, "deviceHash", DEVICE_HASH));
        String loginResponse = mvc.perform(post("/api/v1/auth/login")
                        .header("User-Agent", USER_AGENT)
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andReturn().getResponse().getContentAsString();
        JsonNode loginData = objectMapper.readTree(loginResponse).path("data");
        String accessToken = loginData.path("accessToken").asText();
        String originalRefreshToken = loginData.path("refreshToken").asText();
        String refreshResponse = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "refreshToken", originalRefreshToken))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        accessToken = objectMapper.readTree(refreshResponse).path("data").path("accessToken").asText();
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "refreshToken", originalRefreshToken))))
                .andExpect(status().isBadRequest());

        String initResponse = mvc.perform(post("/api/v1/auth/step-up/initiate")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"CHANGE_BANK_DETAILS\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode challenge = objectMapper.readTree(initResponse).path("data");
        String timestamp = java.time.Instant.now().toString();
        String verifyBody = objectMapper.writeValueAsString(java.util.Map.of(
                "challengeId", challenge.path("challengeId").asText(),
                "action", "CHANGE_BANK_DETAILS",
                "nonce", challenge.path("nonce").asText(),
                "timestamp", timestamp,
                "embedding", embedding));
        String verifyResponse = mvc.perform(post("/api/v1/auth/step-up/verify")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content(verifyBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String stepUpToken = objectMapper.readTree(verifyResponse).path("data").path("stepUpToken").asText();

        mvc.perform(put("/api/v1/organizations/bank-details")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-StepUp-Token", stepUpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"{\\\"accountNumber\\\":\\\"masked-in-storage\\\"}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maskedBankDetails").value("****"));

        mvc.perform(put("/api/v1/organizations/bank-details")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-StepUp-Token", stepUpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"{}\"}"))
                .andExpect(status().isForbidden());
    }

    private User createUser(List<Float> embedding) throws Exception {
        Organization organization = new Organization();
        organization.setName("Test Org");
        organization.setEmail("org-" + UUID.randomUUID() + "@example.test");
        organization.setCurrency("USD");
        organization = organizationRepository.save(organization);

        User user = new User();
        user.setOrganization(organization);
        user.setName("Test User");
        user.setEmail("user-" + UUID.randomUUID() + "@example.test");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(User.Role.ORG_ADMIN);
        user.setStatus(User.AccountStatus.ACTIVE);
        user.setEmailVerified(true);
        user = userRepository.save(user);

        organization.setAdminUserId(user.getId());
        organizationRepository.save(organization);
        OrganizationMember member = new OrganizationMember();
        member.setUser(user);
        member.setOrganization(organization);
        member.setRole(OrgRole.ORG_ADMIN);
        member.setStatus("ACTIVE");
        memberRepository.save(member);

        FaceTemplate template = new FaceTemplate();
        template.setUser(user);
        template.setSampleIndex(0);
        template.setEmbedding(objectMapper.writeValueAsString(embedding));
        faceTemplateRepository.save(template);

        TrustedDevice device = new TrustedDevice();
        device.setUser(user);
        device.setDeviceIdentifier(DEVICE_HASH);
        device.setDeviceHash(DEVICE_HASH);
        device.setIpAddress("127.0.0.1");
        device.setUserAgent(USER_AGENT);
        device.setTrusted(true);
        device.setFirstSeen(LocalDateTime.now());
        device.setLastSeen(LocalDateTime.now().minusDays(1));
        device.setLastUsedAt(LocalDateTime.now());
        deviceRepository.save(device);
        return user;
    }

    private static List<Float> faceEmbedding() {
        List<Float> embedding = new ArrayList<>(java.util.Collections.nCopies(128, 0.0f));
        embedding.set(0, 1.0f);
        return embedding;
    }
}
