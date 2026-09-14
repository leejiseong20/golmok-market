package com.golmok.market.global.security;

import com.golmok.market.domain.user.UserRole;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

/**
 * access token 발급·검증.
 *
 * refresh token 은 여기서 다루지 않는다. refresh token 은 어차피 DB 에서 조회해 검증하므로
 * 자체 서명(JWT)이 필요 없고, 무작위 문자열로 발급한다. (TokenHasher 참고)
 */
@Component
public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final JwtProperties properties;
    private final Clock clock;
    private final JwtParser parser;

    public JwtTokenProvider(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        // 256비트 미만 키는 여기서 WeakKeyException 으로 기동 자체가 실패한다. 약한 키로 운영되는 것보다 낫다.
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.parser = Jwts.parser()
                .verifyWith(key)          // 서명 검증 필수. 서명 없는(alg=none) 토큰은 parseSignedClaims 에서 거부된다.
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public String createAccessToken(Long userId, UserRole role) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenValidity())))
                .signWith(key)
                .compact();
    }

    /**
     * @throws BusinessException 만료면 EXPIRED_TOKEN, 그 외 서명·형식 오류는 INVALID_TOKEN
     */
    public AuthUser parseAccessToken(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            UserRole role = UserRole.valueOf(claims.get(ROLE_CLAIM, String.class));
            return new AuthUser(userId, role);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            // IllegalArgument: 빈 문자열, subject 가 숫자 아님, role 값 이상
            // NullPointer: subject / role 클레임 누락
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }
}
