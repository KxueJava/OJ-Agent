package com.codeagentoj.server.identity;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codeagentoj.server.identity.AuthDtos.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private static final long USER_ROLE_ID = 1L;
    private final UserMapper users;
    private final RefreshTokenMapper refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final SecureRandom random = new SecureRandom();
    private final long refreshDays;

    public AuthService(UserMapper users, RefreshTokenMapper refreshTokens, PasswordEncoder passwordEncoder,
                       TokenService tokenService, StringRedisTemplate redis, ObjectMapper mapper,
                       @Value("${app.security.refresh-token-days:30}") long refreshDays) {
        this.users = users; this.refreshTokens = refreshTokens; this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService; this.redis = redis; this.mapper = mapper; this.refreshDays = refreshDays;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (users.selectCount(new QueryWrapper<User>().eq("username", request.username()).or().eq("email", request.email())) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名或邮箱已存在");
        }
        User user = new User();
        user.setUsername(request.username()); user.setEmail(request.email().toLowerCase());
        user.setDisplayName(request.displayName() == null || request.displayName().isBlank() ? request.username() : request.displayName());
        user.setPasswordHash(passwordEncoder.encode(request.password())); user.setRoleId(USER_ROLE_ID); user.setEnabled(true);
        try { users.insert(user); } catch (DuplicateKeyException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名或邮箱已存在"); }
        return issue(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = users.findByUsernameOrEmail(request.identifier());
        if (user == null || !user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        return issue(user);
    }

    @Transactional
    public TokenResponse refresh(String rawToken) {
        RefreshToken stored = refreshTokens.findByHash(hash(rawToken));
        if (stored == null || stored.getRevokedAt() != null || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("刷新令牌无效或已过期");
        }
        stored.setRevokedAt(Instant.now()); refreshTokens.updateById(stored);
        User user = users.selectById(stored.getUserId());
        if (user == null || !user.isEnabled()) throw new BadCredentialsException("用户不可用");
        return issue(user);
    }

    @Transactional
    public void logout(String rawToken) {
        RefreshToken stored = refreshTokens.findByHash(hash(rawToken));
        if (stored != null && stored.getRevokedAt() == null) { stored.setRevokedAt(Instant.now()); refreshTokens.updateById(stored); }
    }

    public UserView view(User user) { return new UserView(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(), role(user)); }
    public UserView cachedView(Long id) {
        String key = accountKey(id);
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) return mapper.readValue(cached, UserView.class);
        } catch (Exception ignored) { }
        User user = requireUser(id);
        UserView value = view(user);
        cacheView(key, value);
        return value;
    }
    public void evictAccount(Long id) { try { redis.delete(accountKey(id)); } catch (Exception ignored) { } }
    public User requireUser(Long id) {
        User user = users.selectById(id);
        if (user == null || !user.isEnabled()) throw new BadCredentialsException("用户不可用");
        return user;
    }
    private TokenResponse issue(User user) {
        String raw = randomToken(); Instant expires = Instant.now().plus(Duration.ofDays(refreshDays));
        RefreshToken token = new RefreshToken(); token.setUserId(user.getId()); token.setTokenHash(hash(raw)); token.setExpiresAt(expires); refreshTokens.insert(token);
        TokenService.AccessToken access = tokenService.create(user);
        UserView account = view(user);
        cacheView(accountKey(user.getId()), account);
        return new TokenResponse(access.value(), raw, access.expiresAt(), account);
    }
    private void cacheView(String key, UserView value) {
        try { redis.opsForValue().set(key, mapper.writeValueAsString(value), Duration.ofDays(3)); }
        catch (JsonProcessingException | RuntimeException ignored) { }
    }
    private String accountKey(Long id) { return "codeagent:account:" + id; }
    private Role role(User user) { return user.getRoleId() != null && user.getRoleId() == 2L ? Role.ADMIN : Role.USER; }
    private String randomToken() { byte[] bytes = new byte[48]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
