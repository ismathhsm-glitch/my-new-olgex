package com.olgav.invoicesecurity.service;

import com.olgav.invoicesecurity.dto.OrganizationDtos;
import com.olgav.invoicesecurity.model.*;
import com.olgav.invoicesecurity.repository.OrganizationMemberRepository;
import com.olgav.invoicesecurity.repository.OrganizationRepository;
import com.olgav.invoicesecurity.repository.OrganizationInvitationTokenRepository;
import com.olgav.invoicesecurity.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class OrganizationService {
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final OrganizationInvitationTokenRepository invitationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailNotificationService emailService;
    private final AuthService authService;
    private final AuditLoggingService auditLoggingService;
    private final SecureRandom random = new SecureRandom();

    public OrganizationService(OrganizationRepository organizationRepository,
                               OrganizationMemberRepository memberRepository,
                               UserRepository userRepository,
                               OrganizationInvitationTokenRepository invitationTokenRepository,
                               PasswordEncoder passwordEncoder,
                               EmailNotificationService emailService,
                               AuthService authService,
                               AuditLoggingService auditLoggingService) {
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.invitationTokenRepository = invitationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.authService = authService;
        this.auditLoggingService = auditLoggingService;
    }

    public OrganizationDtos.OrganizationView current(Long userId) {
        Organization organization = currentOrganization(userId);
        String maskedBankDetails = organization.getBankDetails() == null ? null : "****";
        return new OrganizationDtos.OrganizationView(organization.getId(), organization.getName(),
                organization.getLogoUrl(), organization.getCurrency(), organization.getTaxSettings(), maskedBankDetails);
    }

    @Transactional
    public OrganizationDtos.OrganizationView updateSettings(Long userId, OrganizationDtos.SettingsRequest request) {
        Organization organization = requireAdmin(userId);
        organization.setName(request.name());
        organization.setLogoUrl(request.logoUrl());
        organization.setCurrency(request.currency());
        organization.setTaxSettings(request.taxSettings());
        organizationRepository.save(organization);
        audit(userId, organization.getId(), "ORGANIZATION_SETTINGS_UPDATED");
        return current(userId);
    }

    @Transactional
    public OrganizationDtos.OrganizationView updateBankDetails(Long userId, OrganizationDtos.BankDetailsRequest request) {
        Organization organization = requireAdmin(userId);
        organization.setBankDetails(request.details());
        organizationRepository.save(organization);
        audit(userId, organization.getId(), "ORGANIZATION_BANK_DETAILS_UPDATED");
        return current(userId);
    }

    @Transactional
    public OrganizationDtos.MemberView invite(Long userId, OrganizationDtos.InviteMemberRequest request) {
        Organization organization = requireAdmin(userId);
        String email = request.email().trim().toLowerCase();
        User member = userRepository.findByEmail(email).orElseGet(() -> createInvitedUser(organization, email, request.fullName()));
        if (memberRepository.findByUserIdAndOrganizationId(member.getId(), organization.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member of this organization");
        }
        OrganizationMember membership = new OrganizationMember();
        membership.setUser(member);
        membership.setOrganization(organization);
        membership.setRole(request.role() == null ? OrgRole.VIEWER : request.role());
        membership.setStatus("INVITED");
        membership = memberRepository.save(membership);
        String invitationToken = randomPassword();
        OrganizationInvitationToken invitation = new OrganizationInvitationToken();
        invitation.setUser(member);
        invitation.setOrganization(organization);
        invitation.setTokenHash(sha256(invitationToken));
        invitation.setExpiresAt(LocalDateTime.now().plusDays(7));
        invitationTokenRepository.save(invitation);
        emailService.sendInvitation(member.getEmail(), organization.getName(), invitationToken);
        audit(userId, organization.getId(), "MEMBER_INVITED");
        return memberView(membership);
    }

    @Transactional
    public void acceptInvitation(Long userId, String rawToken) {
        OrganizationInvitationToken invitation = invitationTokenRepository
                .findUnusedForUpdate(userId, sha256(rawToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invitation is invalid or expired"));
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is invalid or expired");
        }
        Long organizationId = invitation.getOrganization().getId();
        OrganizationMember member = membership(organizationId, userId);
        if (!"INVITED".equals(member.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation was already accepted");
        }
        invitation.setUsed(true);
        invitationTokenRepository.save(invitation);
        member.setStatus("ACTIVE");
        memberRepository.save(member);
        audit(userId, organizationId, "MEMBER_INVITATION_ACCEPTED");
    }

    @Transactional
    public List<OrganizationDtos.MemberView> members(Long userId) {
        Organization organization = currentOrganization(userId);
        return memberRepository.findByOrganizationId(organization.getId()).stream()
                .map(this::memberView).toList();
    }

    @Transactional
    public OrganizationDtos.MemberView changeRole(Long userId, Long memberId, OrgRole role) {
        Organization organization = requireAdmin(userId);
        if (role == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is required");
        OrganizationMember member = membership(organization.getId(), memberId);
        if (member.getRole() == OrgRole.ORG_ADMIN && role != OrgRole.ORG_ADMIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transfer organization admin before changing this role");
        }
        if (role == OrgRole.ORG_ADMIN && member.getRole() != OrgRole.ORG_ADMIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Use the admin transfer endpoint to assign an administrator");
        }
        member.setRole(role);
        memberRepository.save(member);
        audit(userId, organization.getId(), "MEMBER_ROLE_CHANGED");
        return memberView(member);
    }

    @Transactional
    public void removeMember(Long userId, Long memberId) {
        Organization organization = requireAdmin(userId);
        OrganizationMember membership = membership(organization.getId(), memberId);
        if (membership.getRole() == OrgRole.ORG_ADMIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transfer organization admin before removing this member");
        }
        memberRepository.delete(membership);
        audit(userId, organization.getId(), "MEMBER_REMOVED");
    }

    @Transactional
    public void transferAdmin(Long userId, OrganizationDtos.TransferAdminRequest request) {
        Organization organization = requireAdmin(userId);
        if (request.newAdminUserId() == null || request.newAdminUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a different existing member");
        }
        OrganizationMember previous = membership(organization.getId(), userId);
        OrganizationMember next = membership(organization.getId(), request.newAdminUserId());
        if (!"ACTIVE".equals(next.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The new administrator must be an active member");
        }
        previous.setRole(OrgRole.ACCOUNTANT);
        next.setRole(OrgRole.ORG_ADMIN);
        organization.setAdminUserId(request.newAdminUserId());
        memberRepository.save(previous);
        memberRepository.save(next);
        organizationRepository.save(organization);
        audit(userId, organization.getId(), "ORGANIZATION_ADMIN_TRANSFERRED");
    }

    private Organization requireAdmin(Long userId) {
        Organization organization = currentOrganization(userId);
        User user = userRepository.findById(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        boolean superAdmin = user.getSystemRole() == User.SystemRole.SUPER_ADMIN;
        boolean orgAdmin = memberRepository.findByUserIdAndOrganizationId(userId, organization.getId())
                .map(member -> member.getRole() == OrgRole.ORG_ADMIN && "ACTIVE".equals(member.getStatus()))
                .orElse(false);
        if (!superAdmin && !orgAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization admin role is required");
        }
        return organization;
    }

    private Organization currentOrganization(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getOrganization() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No current organization");
        }
        return organizationRepository.findById(user.getOrganization().getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
    }

    private OrganizationMember membership(Long organizationId, Long userId) {
        return memberRepository.findByUserIdAndOrganizationId(userId, organizationId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization member not found"));
    }

    private User createInvitedUser(Organization organization, String email, String fullName) {
        User invited = new User();
        invited.setOrganization(organization);
        invited.setName(fullName);
        invited.setEmail(email);
        invited.setPasswordHash(passwordEncoder.encode(randomPassword()));
        invited.setRole(User.Role.VIEWER);
        invited.setStatus(User.AccountStatus.PENDING_VERIFICATION);
        invited.setEmailVerified(false);
        invited = userRepository.save(invited);
        authService.sendVerificationCode(invited);
        return invited;
    }

    private OrganizationDtos.MemberView memberView(OrganizationMember member) {
        return new OrganizationDtos.MemberView(member.getUser().getId(), member.getUser().getEmail(),
                member.getUser().getName(), member.getRole(), member.getStatus(), member.getInvitedAt());
    }

    private void audit(Long actorId, Long organizationId, String action) {
        auditLoggingService.record(actorId, organizationId, action);
    }

    private String randomPassword() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

}
