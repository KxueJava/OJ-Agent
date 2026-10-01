package com.codeagentoj.server.api;

import com.codeagentoj.server.identity.AuthDtos.*;
import com.codeagentoj.server.identity.AuthService;
import com.codeagentoj.server.identity.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }
    @PostMapping("/register") public ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request) { return ApiResponse.ok(service.register(request)); }
    @PostMapping("/login") public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) { return ApiResponse.ok(service.login(request)); }
    @PostMapping("/refresh") public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) { return ApiResponse.ok(service.refresh(request.refreshToken())); }
    @PostMapping("/logout") public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) { service.logout(request.refreshToken()); return ApiResponse.ok(null); }
    @GetMapping("/me") public ApiResponse<UserView> me(@AuthenticationPrincipal Jwt jwt) { return ApiResponse.ok(service.cachedView(Long.valueOf(jwt.getSubject()))); }
}
