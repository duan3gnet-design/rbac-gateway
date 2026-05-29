package com.auth.service.oidc;

import com.auth.service.service.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * OIDC UserInfo Endpoint – GET /oauth2/userinfo
 * <p>
 * Accepts a Bearer access_token and returns standard OIDC claims.
 * Used by k8s OIDC authenticator webhook and other relying parties
 * that need to look up user attributes after token validation.
 * <p>
 * <a href="https://openid.net/specs/openid-connect-core-1_0.html#UserInfo">...</a>
 */
@RestController
@RequestMapping("/oauth2/userinfo")
@RequiredArgsConstructor
public class OidcUserInfoController {

    private final JwtService jwtService;

    @GetMapping
    public Map<String, Object> userInfo(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {

        String token = extractBearer(authHeader);

        if (!jwtService.isValid(token)) {
            throw new SecurityException("Invalid or expired access token");
        }

        Claims claims = jwtService.extractClaims(token);

        Map<String, Object> info = new HashMap<>();
        info.put("sub",   claims.getSubject());
        info.put("iss",   claims.getIssuer());
        info.put("email", claims.getOrDefault("email", claims.getSubject()));

        Object name = claims.get("name");
        if (name != null) info.put("name", name);

        Object roles = claims.get("roles");
        if (roles != null) info.put("roles", roles);

        Object permissions = claims.get("permissions");
        if (permissions != null) info.put("permissions", permissions);

        return info;
    }

    private String extractBearer(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization header must be 'Bearer <token>'");
        }
        return header.substring(7);
    }
}
