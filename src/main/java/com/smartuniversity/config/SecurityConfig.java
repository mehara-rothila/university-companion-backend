package com.smartuniversity.config;

import com.smartuniversity.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${spring.web.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - Guest access for approved content
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/setup/**").permitAll()
                .requestMatchers("/health", "/api/health/**", "/api/status").permitAll()
                .requestMatchers("/ws/**").permitAll() // WebSocket endpoints
                .requestMatchers("/api/upload/**").permitAll() // Image serving endpoints
                .requestMatchers("/api/events/approved", "/api/events/upcoming").permitAll()
                .requestMatchers("/api/events/{id}").permitAll()
                .requestMatchers("/api/achievements/approved").permitAll()
                .requestMatchers("/api/achievements/{id}").permitAll()
                .requestMatchers("/api/competitions/approved").permitAll()
                .requestMatchers("/api/competitions/{id}").permitAll()
                .requestMatchers("/api/financial-aid/donations/eligible").permitAll()
                .requestMatchers("/api/payment/webhook").permitAll() // Payment gateway webhook
                .requestMatchers("/api/payment/callback").permitAll() // Payment callback
                .requestMatchers("/api/payment/cancel").permitAll() // Payment cancel callback
                .requestMatchers("/api/payment/checkout").permitAll() // Payment checkout redirect
                .requestMatchers("/api/weather/current").permitAll()
                .requestMatchers("/api/emergency/active").permitAll() // Active emergency notifications - public

                // Admin-only endpoints
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/events/pending").hasRole("ADMIN")
                .requestMatchers("/api/achievements/pending").hasRole("ADMIN")
                .requestMatchers("/api/competitions/pending").hasRole("ADMIN")
                .requestMatchers("/api/emergency/**").hasRole("ADMIN") // Other emergency endpoints - admin only

                // Authenticated user endpoints
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Parse allowed origins from application.properties
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        
        // Use allowedOriginPatterns to support wildcards like *.netlify.app
        configuration.setAllowedOriginPatterns(origins);
        
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}