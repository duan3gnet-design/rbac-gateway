package com.auth.service.oidc;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OAuth 2.0 Authorization Server Metadata (RFC 8414) + OIDC Discovery
 * (OpenID Connect Discovery 1.0).
 * <p>
 * Kubernetes API server reads this document when configured with:
 *   --service-account-issuer=<issuerUri>
 *   --service-account-jwks-uri=<jwksUri>   (optional override)
 *   --oidc-issuer-url=<issuerUri>           (for OIDC auth webhook)
 */
public record OidcDiscoveryDocument(

        @JsonProperty("issuer")
        String issuer,

        @JsonProperty("authorization_endpoint")
        String authorizationEndpoint,

        @JsonProperty("token_endpoint")
        String tokenEndpoint,

        @JsonProperty("userinfo_endpoint")
        String userinfoEndpoint,

        @JsonProperty("jwks_uri")
        String jwksUri,

        @JsonProperty("response_types_supported")
        List<String> responseTypesSupported,

        @JsonProperty("subject_types_supported")
        List<String> subjectTypesSupported,

        @JsonProperty("id_token_signing_alg_values_supported")
        List<String> idTokenSigningAlgValuesSupported,

        @JsonProperty("scopes_supported")
        List<String> scopesSupported,

        @JsonProperty("token_endpoint_auth_methods_supported")
        List<String> tokenEndpointAuthMethodsSupported,

        @JsonProperty("claims_supported")
        List<String> claimsSupported,

        @JsonProperty("grant_types_supported")
        List<String> grantTypesSupported
) {

    public static OidcDiscoveryDocument of(String issuer) {
        return new OidcDiscoveryDocument(
                issuer,
                issuer + "/oauth2/authorize",
                issuer + "/oauth2/token",
                issuer + "/oauth2/userinfo",
                issuer + "/oauth2/jwks",
                List.of("code", "token", "id_token"),
                List.of("public"),
                List.of("RS256"),
                List.of("openid", "profile", "email"),
                List.of("client_secret_basic", "client_secret_post"),
                List.of("sub", "iss", "aud", "exp", "iat", "email", "name", "roles", "permissions"),
                List.of("authorization_code", "refresh_token", "password", "client_credentials")
        );
    }
}
