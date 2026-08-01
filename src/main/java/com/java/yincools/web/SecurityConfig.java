package com.java.yincools.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * Single-user PIN login -- there's only ever one person using this app.
 * Remember-me is always on so Dad logs in once on his phone and never sees
 * the login screen again; there's no "forgot password" flow because there's
 * no password to forget, just a PIN he was told once.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String USERNAME = "shop";
    private static final int REMEMBER_ME_VALIDITY_SECONDS = 60 * 60 * 24 * 365;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, @Value("${app.remember-me-key}") String rememberMeKey) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/jobs/new", true)
                        .permitAll())
                .rememberMe(remember -> remember
                        .key(rememberMeKey)
                        .tokenValiditySeconds(REMEMBER_ME_VALIDITY_SECONDS)
                        .alwaysRemember(true))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**", "/api/jobs"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(@Value("${app.pin}") String pin, PasswordEncoder encoder) {
        UserDetails user = User.withUsername(USERNAME)
                .password(encoder.encode(pin))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
