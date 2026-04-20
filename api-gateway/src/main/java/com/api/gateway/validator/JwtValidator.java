package com.api.gateway.validator;

import com.auth.service.dto.ClaimsResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
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

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    @SuppressWarnings("unchecked")
    public Mono<ClaimsResponse> validate(String token) {
        return Mono.fromCallable(() -> {
            Claims claims = Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            List<String> roles = claims.get("roles", List.class);

            // ✅ Parse permissions từ JWT claim
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