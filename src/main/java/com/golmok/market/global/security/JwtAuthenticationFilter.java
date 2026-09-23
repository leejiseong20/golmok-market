package com.golmok.market.global.security;

import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.security.UserAccessCache.UserAccess;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Authorization: Bearer {accessToken} 을 읽어 SecurityContext 에 인증 정보를 넣는다.
 *
 * 동작 규칙
 * - 헤더 없음       → 익명으로 통과. 보호된 경로면 EntryPoint 가 401(UNAUTHORIZED).
 * - 토큰 만료/위조  → 공개 경로라도 즉시 401(EXPIRED_TOKEN / INVALID_TOKEN).
 *   클라이언트가 자격증명을 보냈는데 틀렸다면 조용히 익명 처리하지 않고 알려준다.
 *   조용히 넘기면 상품 목록의 isLiked 가 이유 없이 false 로 보이고, 프론트는 재발급할 기회를 놓친다.
 * - 토큰 정상인데 정지·탈퇴한 회원 → 403(USER_NOT_ACTIVE). 공개 경로도 같다(토큰을 보냈으니 그 계정으로 쓰려는 것이다).
 *   토큰은 만료(최대 30분)까지 유효하므로, 상태는 토큰이 아니라 DB 기준으로 본다(UserAccessCache, 회원당 60초 기억).
 * - 토큰 정상       → 인증 설정 후 통과. 권한도 토큰이 아니라 DB 기준이다(관리자에서 내리면 바로 반영).
 *
 * {@code @Component} 로 등록하지 않는다. 등록하면 Spring Boot 가 서블릿 필터로도 자동 등록해
 * Security 체인 밖에서 한 번 더 실행된다. SecurityConfig 에서 직접 생성해 체인에만 넣는다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 토큰 검사를 건너뛰는 경로.
     * 특히 reissue 가 중요하다. 프론트가 모든 요청에 Authorization 헤더를 붙이면,
     * 만료된 access token 때문에 재발급 요청 자체가 401 로 막혀 영영 재발급을 못 한다.
     */
    private static final Set<String> SKIP_PATHS = Set.of(
            "/api/auth/signup",
            "/api/auth/login",
            "/api/auth/reissue",
            "/api/auth/check-email",
            "/api/auth/check-nickname"
    );

    private final JwtTokenProvider tokenProvider;
    private final SecurityErrorWriter errorWriter;
    private final UserAccessCache userAccessCache;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, SecurityErrorWriter errorWriter,
                                   UserAccessCache userAccessCache) {
        this.tokenProvider = tokenProvider;
        this.errorWriter = errorWriter;
        this.userAccessCache = userAccessCache;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SKIP_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthUser authUser;
        try {
            authUser = tokenProvider.parseAccessToken(header.substring(BEARER_PREFIX.length()).trim());
        } catch (BusinessException e) {
            errorWriter.write(response, e.getErrorCode());
            return;
        }

        Optional<UserAccess> access = userAccessCache.get(authUser.id());
        if (access.isEmpty() || !access.get().active()) {
            errorWriter.write(response, ErrorCode.USER_NOT_ACTIVE);
            return;
        }
        // 컨트롤러가 받는 권한도 지금 DB 값으로 바꾼다(토큰에 적힌 권한은 발급 시점 값이다).
        authUser = new AuthUser(authUser.id(), access.get().role());

        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                authUser, null, List.of(new SimpleGrantedAuthority("ROLE_" + authUser.role().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        filterChain.doFilter(request, response);
    }
}
