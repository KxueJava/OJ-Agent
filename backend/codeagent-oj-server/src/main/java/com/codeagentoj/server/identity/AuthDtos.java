package com.codeagentoj.server.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthDtos {
    private AuthDtos() {}
    public record RegisterRequest(@NotBlank @Size(min = 3, max = 32) String username,
                                  @NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  @Size(max = 64) String displayName) {}
    public record LoginRequest(@NotBlank String identifier, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record TokenResponse(String accessToken, String refreshToken, Instant expiresAt, UserView user) {}
    public record UserView(Long id, String username, String email, String displayName, Role role) {}
}
