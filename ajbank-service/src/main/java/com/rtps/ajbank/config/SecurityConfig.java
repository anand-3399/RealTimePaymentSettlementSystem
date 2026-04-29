package com.rtps.ajbank.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.client.RestTemplate;
import org.springframework.context.annotation.Bean;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${rtps.inbound-secret}")
    private String internalSecret;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-ui.html").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(new InternalSecretFilter(internalSecret), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static class InternalSecretFilter extends OncePerRequestFilter {
        private final String secret;

        public InternalSecretFilter(String secret) {
            this.secret = secret;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            
            String requestSecret = request.getHeader("X-Internal-Secret");
            // System.out.println("Expected: " + secret);
            // System.out.println("Received: " + requestSecret);
            if (secret.equals(requestSecret)) {
                // Manually set authentication for the internal service
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        "INTERNAL_SERVICE", null, java.util.Collections.emptyList());
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
            } else{
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            filterChain.doFilter(request, response);
        }
    }
}
