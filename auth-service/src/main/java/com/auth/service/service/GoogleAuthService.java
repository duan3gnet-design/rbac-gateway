package com.auth.service.service;

import com.auth.service.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ott.InvalidOneTimeTokenException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    private static final String GOOGLE_TOKEN_INFO_URL =
            "https://oauth2.googleapis.com/tokeninfo?id_token=";

    public TokenResponse authenticate(String idToken) {
        GoogleUserInfo googleUser = verifyIdToken(idToken);
        UserDetails userDetails = userService.findOrCreateOAuth2User(
                googleUser.email(),
                googleUser.name()
        );
        String accessToken  = jwtService.generateToken(userDetails);
        String refreshToken = refreshTokenService
                .createRefreshToken(googleUser.email()).getToken();

        return new TokenResponse(accessToken, refreshToken);
    }

    private GoogleUserInfo verifyIdToken(String idToken) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            Map<?, ?> response = restTemplate.getForObject(
                    GOOGLE_TOKEN_INFO_URL + idToken, Map.class);

            if (response == null) {
                throw new InvalidOneTimeTokenException("Không thể verify Google token");
            }

            String email = (String) response.get("email");
            String name  = (String) response.get("name");
            String emailVerified = (String) response.get("email_verified");

            if (!"true".equals(emailVerified)) {
                throw new InvalidOneTimeTokenException("Email Google chưa được xác thực");
            }

            return new GoogleUserInfo(email, name);

        } catch (HttpClientErrorException e) {
            throw new InvalidOneTimeTokenException("Google ID Token không hợp lệ");
        }
    }

    private record GoogleUserInfo(String email, String name) {}
}
