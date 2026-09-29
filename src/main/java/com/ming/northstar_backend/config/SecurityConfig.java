package com.ming.northstar_backend.config;

import com.ming.northstar_backend.security.JwtFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthRateLimitProperties.class)
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtFilter jwtFilter,
                          @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.jwtFilter = jwtFilter;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfig()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/send-code",
                        "/api/auth/forgot-password/lookup", "/api/auth/reset-password").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/leaderboard").permitAll()
                .requestMatchers("/api/stats/**").permitAll()
                .requestMatchers("/api/profile/**").permitAll()
                .requestMatchers("/api/minecraft/avatar/**").permitAll()
                .requestMatchers("/api/rooms").permitAll()
                .requestMatchers("/api/beta/check").permitAll()
                .requestMatchers("/api/beta/verify").permitAll()
                .requestMatchers("/api/beta/plans").permitAll()
                .requestMatchers("/api/matches/recent").permitAll()
                .requestMatchers("/api/feedback").permitAll()
                .requestMatchers("/api/platform/stats").permitAll()
                .requestMatchers("/api/bridge/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // 错误页必须放行：否则任何 controller 异常转发到 /error 时都会被
                // 当成匿名请求拒绝，真实错误被掩盖成无消息的 403。
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 跨域策略。
     *
     * <p>改动点（NS-07）：{@code allowedHeaders} 由 {@code *} 收敛为白名单，
     * 并显式暴露 {@code Retry-After} 供 429 提示用。允许的来源由
     * {@code app.cors.allowed-origins} 决定——生产 profile 里只有一个正式域名，
     * localhost 只存在于（不随构建产物分发的）开发 profile。</p>
     */
    @Bean
    public CorsConfigurationSource corsConfig() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "Origin",
                "X-Requested-With", "X-Northstar-Bridge-Token"));
        config.setExposedHeaders(List.of("Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(1800L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
