package com.golmok.market.global.config;

import com.golmok.market.global.security.JwtAuthenticationFilter;
import com.golmok.market.global.security.JwtProperties;
import com.golmok.market.global.security.JwtTokenProvider;
import com.golmok.market.global.security.RestAccessDeniedHandler;
import com.golmok.market.global.security.RestAuthenticationEntryPoint;
import com.golmok.market.global.security.SecurityErrorWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * JWT 기반 stateless 보안 설정.
 *
 * - 세션을 만들지 않는다. 인증 상태는 매 요청의 access token 이 전부다.
 *   서버를 여러 대로 늘려도 세션 공유가 필요 없다.
 * - CSRF 는 끈다. CSRF 는 브라우저가 쿠키를 자동으로 붙이는 것을 악용하는 공격인데,
 *   이 API 는 쿠키가 아니라 Authorization 헤더로 인증하므로 해당되지 않는다.
 * - 경로 권한은 "기본 차단, 공개할 것만 명시"로 둔다. 새 API 를 추가하고 설정을 잊으면
 *   공개되는 게 아니라 막히는 쪽이 안전하다.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtTokenProvider tokenProvider,
                                                   SecurityErrorWriter errorWriter,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())

                .authorizeHttpRequests(auth -> auth
                        // "/me" 는 아래 공개 GET 패턴(/api/users/*, /api/products/*)에 걸리므로 먼저 막는다.
                        .requestMatchers(HttpMethod.GET, "/api/users/me", "/api/products/me").authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login", "/api/auth/reissue").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/check-email", "/api/auth/check-nickname").permitAll()

                        // 업로드한 이미지 파일. 목록·상세에서 비로그인도 봐야 한다.
                        .requestMatchers(HttpMethod.GET, "/api/images/**").permitAll()

                        // 비로그인 사용자도 둘러볼 수 있는 조회 API
                        .requestMatchers(HttpMethod.GET,
                                "/api/products", "/api/products/*",
                                "/api/categories",
                                "/api/regions", "/api/regions/nearby",
                                "/api/users/*",
                                "/api/search/keywords/popular").permitAll()

                        // 컨트롤러 밖 오류가 /error 로 포워드될 때 401 로 덮이지 않도록
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider, errorWriter),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 스키마 주석대로 BCrypt. 느린 해시라 DB 가 유출돼도 비밀번호 대입 비용이 크다.
     * BCrypt 는 72바이트까지만 처리하므로 비밀번호를 ASCII 64자 이하로 제한한다(SignupRequest).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 프론트(Vite 개발 서버 기본 5173)에서의 호출 허용.
     * 토큰은 쿠키가 아닌 헤더로 주고받으므로 allowCredentials 는 필요 없다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
