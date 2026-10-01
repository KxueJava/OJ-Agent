package com.codeagentoj.server.identity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final long accessMinutes;
    public TokenService(JwtEncoder encoder, @Value("${app.security.access-token-minutes:30}") long accessMinutes) { this.encoder = encoder; this.accessMinutes = accessMinutes; }
    public AccessToken create(User user) {
        Instant now = Instant.now(), expires = now.plus(accessMinutes, ChronoUnit.MINUTES);
        String role = user.getRoleId() != null && user.getRoleId() == 2L ? "ADMIN" : "USER";
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("codeagent-oj").issuedAt(now).expiresAt(expires).subject(user.getId().toString()).claim("username", user.getUsername()).claim("roles", List.of(role)).build();
        String value = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AccessToken(value, expires);
    }
    public record AccessToken(String value, Instant expiresAt) {}
}
