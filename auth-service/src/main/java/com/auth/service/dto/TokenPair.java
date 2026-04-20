package com.auth.service.dto;

public record TokenPair(
        String accessToken,
        String refreshToken
) {}
