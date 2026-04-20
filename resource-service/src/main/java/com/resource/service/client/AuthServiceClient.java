package com.resource.service.client;

import com.resource.service.dto.UserSummary;
import com.resource.service.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class AuthServiceClient {

    private final WebClient webClient;
    private final String internalSecret;

    public AuthServiceClient(
            WebClient.Builder builder,
            @Value("${app.auth-service.url}") String authServiceUrl,
            @Value("${app.internal.secret}") String internalSecret) {
        this.webClient = builder
                .baseUrl(authServiceUrl)
                .defaultHeader("X-Internal-Secret", internalSecret)
                .build();
        this.internalSecret = internalSecret;
    }

    public List<UserSummary> getAllUsers() {
        try {
            List<UserSummary> users = webClient.get()
                    .uri("/internal/users")
                    .retrieve()
                    .bodyToFlux(UserSummary.class)
                    .collectList()
                    .block();
            return users != null ? users : List.of();
        } catch (WebClientResponseException e) {
            log.error("auth-service getAllUsers failed: {} {}", e.getStatusCode(), e.getMessage());
            throw new RuntimeException("Failed to fetch users from auth-service: " + e.getMessage());
        }
    }

    public Optional<UserSummary> getUserByUsername(String username) {
        try {
            UserSummary user = webClient.get()
                    .uri("/internal/users/{username}", username)
                    .retrieve()
                    .bodyToMono(UserSummary.class)
                    .block();
            return Optional.ofNullable(user);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            log.error("auth-service getUserByUsername failed: {} {}", e.getStatusCode(), e.getMessage());
            throw new RuntimeException("Failed to fetch user from auth-service: " + e.getMessage());
        }
    }

    public void deleteUser(Long id) {
        try {
            webClient.delete()
                    .uri("/internal/users/{id}", id)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("User not found: " + id);
            }
            log.error("auth-service deleteUser failed: {} {}", e.getStatusCode(), e.getMessage());
            throw new RuntimeException("Failed to delete user from auth-service: " + e.getMessage());
        }
    }
}
