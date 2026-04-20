package com.auth.service.dto;

public record TokenResponse(String token, String refreshToken) {
}