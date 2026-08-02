package com.java.yincools.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Both accounts ("shop" for Dad, "owner" for the extra oversight access)
 * unlock with a PIN alone -- the login form never asks which one you are,
 * it's the exact same "type your PIN" screen either way. This tries the
 * submitted PIN against every known account and authenticates as whichever
 * one matches, instead of looking a single account up by a submitted
 * username the way DaoAuthenticationProvider does.
 */
@Component
@RequiredArgsConstructor
public class PinAuthenticationProvider implements AuthenticationProvider {

    private static final List<String> KNOWN_USERNAMES = List.of("shop", "owner");

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder encoder;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String pin = String.valueOf(authentication.getCredentials());

        return KNOWN_USERNAMES.stream()
                .map(userDetailsService::loadUserByUsername)
                .filter(user -> encoder.matches(pin, user.getPassword()))
                .findFirst()
                .map(this::authenticated)
                .orElseThrow(() -> new BadCredentialsException("Invalid PIN"));
    }

    private Authentication authenticated(UserDetails user) {
        return new UsernamePasswordAuthenticationToken(user, user.getPassword(), user.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
