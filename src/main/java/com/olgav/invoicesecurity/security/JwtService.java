package com.olgav.invoicesecurity.security;

import com.olgav.invoicesecurity.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final String secret;
    private final Duration accessTtl;

    public JwtService(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.access-ttl:PT15M}") Duration accessTtl) {
        this.secret = secret;
        this.accessTtl = accessTtl;
    }

    public String issueAccessToken(User user, String organizationRole) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", organizationRole)
                .claim("systemRole", user.getSystemRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(signingKey())
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(signingKey()).build()
                .parseSignedClaims(token).getPayload();
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    private SecretKey signingKey() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be set to a base64-encoded key of at least 32 bytes");
        }
        try {
            byte[] key = Decoders.BASE64.decode(secret);
            if (key.length < 32) throw new IllegalArgumentException("Key is shorter than 32 bytes");
            return Keys.hmacShaKeyFor(key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET must be a valid base64-encoded key of at least 32 bytes", e);
        }
    }
}
