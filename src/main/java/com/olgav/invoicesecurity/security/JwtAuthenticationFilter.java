package com.olgav.invoicesecurity.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olgav.invoicesecurity.dto.ApiResponse;
import com.olgav.invoicesecurity.model.User;
import com.olgav.invoicesecurity.repository.OrganizationMemberRepository;
import com.olgav.invoicesecurity.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper,
                                   UserRepository userRepository,
                                   OrganizationMemberRepository memberRepository) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));
                Long userId = Long.valueOf(claims.getSubject());
                User user = userRepository.findById(userId).orElse(null);
                if (user == null || !user.isEmailVerified()
                        || user.getStatus() != User.AccountStatus.ACTIVE
                        || !Boolean.TRUE.equals(user.getIsActive())) {
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                            "Access token is no longer valid");
                    return;
                }
                String organizationRole = user.getSystemRole() == User.SystemRole.SUPER_ADMIN ? "SUPER_ADMIN"
                        : memberRepository.findByUserIdAndOrganizationId(userId, user.getOrganization().getId())
                        .filter(member -> "ACTIVE".equals(member.getStatus()))
                        .map(member -> member.getRole().name()).orElse("VIEWER");
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_" + organizationRole));
                authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getSystemRole().name()));
                var authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                        "Invalid or expired access token");
                return;
            } catch (IllegalStateException e) {
                SecurityContextHolder.clearContext();
                writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "AUTH_NOT_CONFIGURED",
                        "Authentication is not configured");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(code, message));
    }
}
