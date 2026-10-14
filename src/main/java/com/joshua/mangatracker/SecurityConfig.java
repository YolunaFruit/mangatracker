package com.joshua.mangatracker;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, SecurityContextRepository repository) throws Exception {
        return http
                .securityContext(c -> c.securityContextRepository(repository))
                .requestCache(c -> c.disable())
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/static/**", "/*.ico", "/*.png", "/*.svg", "/*.json", "/robots.txt", "/search/**", "/user/session", "/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/user/register", "/user/login").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> res.sendError(401, "Please sign in."))
                        .accessDeniedHandler((req, res, ex) -> res.sendError(403, "Access denied. Refresh and try again.")))
                .logout(l -> l.logoutUrl("/user/logout").logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
                .build();
    }
}
