package com.auth.service.hanđler;

import com.auth.service.service.JwtService;
import com.auth.service.service.RefreshTokenService;
import com.auth.service.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler
        implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Override
    public void onAuthenticationSuccess(@NonNull HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User != null ? oAuth2User.getAttribute("email") : null;
        String name  = oAuth2User != null ? oAuth2User.getAttribute("name") : null;

        UserDetails userDetails = userService.findOrCreateOAuth2User(email, name);

        String accessToken  = jwtService.generateToken(userDetails);
        String refreshToken = refreshTokenService.createRefreshToken(email).getToken();

        response.setContentType("application/json");
        response.getWriter().write("""
            {
                "token": "%s",
                "refreshToken": "%s"
            }
        """.formatted(accessToken, refreshToken));
    }
}
