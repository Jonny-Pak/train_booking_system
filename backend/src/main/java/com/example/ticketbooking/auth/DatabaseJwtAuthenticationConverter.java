package com.example.ticketbooking.auth;

import com.example.ticketbooking.user.UserRepository;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class DatabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserRepository users;

    public DatabaseJwtAuthenticationConverter(UserRepository users) { this.users = users; }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        long id;
        try {
            id = Long.parseLong(jwt.getSubject());
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
        }
        var user = users.findById(id).orElseThrow(() ->
                new OAuth2AuthenticationException(new OAuth2Error("invalid_token")));
        // Role from DB takes precedence over a token's stale role claim.
        return new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())), Long.toString(id));
    }
}
