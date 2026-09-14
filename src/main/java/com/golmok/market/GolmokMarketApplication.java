package com.golmok.market;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * UserDetailsServiceAutoConfiguration 제외: 인증은 JWT 로만 하므로 Spring Security 의 사용자 저장소를 쓰지 않는다.
 * 제외하지 않으면 Boot 가 "user" 계정과 임의 비밀번호를 메모리에 만들고 로그에 비밀번호를 출력한다.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class GolmokMarketApplication {

    public static void main(String[] args) {
        SpringApplication.run(GolmokMarketApplication.class, args);
    }
}
