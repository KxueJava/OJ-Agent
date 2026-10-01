package com.codeagentoj.server.infra;

import java.util.Collection;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    @Override public AbstractAuthenticationToken convert(Jwt jwt) {
        var roles = jwt.getClaimAsStringList("roles");
        Collection<SimpleGrantedAuthority> authorities = (roles == null ? java.util.List.<String>of() : roles).stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
