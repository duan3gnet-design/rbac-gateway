package com.auth.service.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleTokenRequest(@NotBlank String idToken) {}