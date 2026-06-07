package com.auth.service.hanđler;

import com.auth.service.entity.SsoProvider;
import com.auth.service.repository.SsoProviderRepository;
import com.auth.service.service.JwtService;
import com.auth.service.service.RefreshTokenService;
import com.auth.service.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles successful OAuth2/OIDC logins.
 *
 * <p>Works for both:
 * <ul>
 *   <li>Google (static Spring Security registration)</li>
 *   <li>Dynamic SSO providers registered in the sso_providers table</li>
 * </ul>
 *
 * <p>After provisioning/finding the user, issues JWT + refresh token
 * and writes them to the response body (SPA-friendly JSON).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService           userService;
    private final JwtService            jwtService;
    private final RefreshTokenService   refreshTokenService;
    private final SsoProviderRepository ssoProviderRepository;

    @Override
    public void onAuthenticationSuccess(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull Authentication      authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email   = oAuth2User.getAttribute("email");
        String name    = oAuth2User.getAttribute("name");
        String subject = oAuth2User.getAttribute("sub"); // OIDC standard subject

        // Detect provider
        String          registrationId = extractRegistrationId(authentication);
        SsoProvider     ssoProvider    = null;
        String          providerTag;

        if ("google".equalsIgnoreCase(registrationId)) {
            providerTag = "GOOGLE";
        } else {
            // Dynamic SSO provider — look up from DB
            ssoProvider = ssoProviderRepository
                    .findByProviderIdAndEnabledTrue(registrationId)
                    .orElse(null);
            providerTag = registrationId != null ? registrationId.toUpperCase() : "SSO";
        }

        log.debug("OAuth2 success: provider={}, email={}, sub={}", registrationId, email, subject);

        UserDetails userDetails = userService.findOrCreateOAuth2User(
                email, name, providerTag, ssoProvider, subject);

        String accessToken  = jwtService.generateToken(userDetails);
        String refreshToken = refreshTokenService.createRefreshToken(email).getToken();

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
            {
                "accessToken": "%s",
                "refreshToken": "%s"
            }
        """.formatted(accessToken, refreshToken));
    }

    private String extractRegistrationId(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken token) {
            return token.getAuthorizedClientRegistrationId();
        }
        return null;
    }
}
