package com.java.yincools.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Two PIN-only accounts -- "shop" (Dad, day-to-day) and "owner" (extra
 * oversight access, e.g. /suppliers/**). Both share the exact same login
 * page and PIN field; PinAuthenticationProvider is what actually figures
 * out which account a submitted PIN belongs to. Remember-me is always on
 * for both, so nobody sees the login screen again after unlocking once on
 * their own phone; there's no "forgot password" flow because there's no
 * password to forget, just a PIN each of them was told once.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final int REMEMBER_ME_VALIDITY_SECONDS = 60 * 60 * 24 * 365;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, @Value("${app.remember-me-key}") String rememberMeKey) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/login", "/actuator/health",
                                // Static assets -- not per-user data, and the login page itself
                                // (rendered before authentication) needs the logo/tokens/manifest.
                                "/css/**", "/images/**", "/manifest.webmanifest",
                                "/icon-192.png", "/icon-512.png", "/sw.js",
                                "/vehicle-picker.js", "/parts-chips.js", "/offline-queue.js", "/quote-items.js",
                                "/offline-quote-queue.js",
                                "/vehicle-seed.json", "/parts-seed.json"
                        ).permitAll()
                        // Owner-only oversight report -- not linked from Dad's nav; the
                        // "shop" account gets a plain 403 if it ever hits this URL.
                        .requestMatchers("/suppliers/**").hasRole("OWNER")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/quotes/new", true)
                        .permitAll())
                .rememberMe(remember -> remember
                        .key(rememberMeKey)
                        .tokenValiditySeconds(REMEMBER_ME_VALIDITY_SECONDS)
                        .alwaysRemember(true))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**", "/api/jobs", "/api/quotes"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    /**
     * Registers PinAuthenticationProvider as the thing that actually
     * authenticates a login attempt, instead of the default
     * DaoAuthenticationProvider (which would look a single account up by
     * the submitted username -- not what we want, since the login form
     * never says which account it's for).
     */
    @Bean
    AuthenticationManager authenticationManager(HttpSecurity http, PinAuthenticationProvider pinAuthenticationProvider) throws Exception {
        AuthenticationManagerBuilder authBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authBuilder.authenticationProvider(pinAuthenticationProvider);
        return authBuilder.build();
    }

    /**
     * Still needed even though login itself goes through
     * PinAuthenticationProvider -- remember-me cookies on later requests
     * are validated by looking the remembered username back up here.
     */
    @Bean
    UserDetailsService userDetailsService(@Value("${app.pin}") String shopPin,
                                           @Value("${app.owner-pin}") String ownerPin,
                                           PasswordEncoder encoder) {
        UserDetails shop = User.withUsername("shop")
                .password(encoder.encode(shopPin))
                .roles("USER")
                .build();
        UserDetails owner = User.withUsername("owner")
                .password(encoder.encode(ownerPin))
                .roles("USER", "OWNER")
                .build();
        return new InMemoryUserDetailsManager(shop, owner);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
