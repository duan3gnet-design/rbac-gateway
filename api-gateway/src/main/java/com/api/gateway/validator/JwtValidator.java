package com.api.gateway.validator;

import com.auth.service.dto.ClaimsResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtValidator {

    @Value("${jwt.secret}")
    private String secret;

    // Build parser 1 lần khi startup, tái dụng cho mọi request
    // → tránh decode Base64 + allocate SecretKey mỗi lần validate
    private JwtParser jwtParser;

    @PostConstruct
    public void init() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.jwtParser = Jwts.parser().verifyWith(key).build();
    }

    @SuppressWarnings("unchecked")
    public Mono<ClaimsResponse> validate(String token) {
        return Mono.fromCallable(() -> {
            Claims claims = jwtParser
                    .parseSignedClaims(token)
                    .getPayload();

            List<String> roles = claims.get("roles", List.class);

            List<String> rawPermissions = claims.get("permissions", List.class);
            Set<String> permissions = rawPermissions != null
                    ? new HashSet<>(rawPermissions)
                    : Set.of();

            return new ClaimsResponse(
                    claims.getSubject(),
                    roles,
                    permissions
            );
        });
    }
}
