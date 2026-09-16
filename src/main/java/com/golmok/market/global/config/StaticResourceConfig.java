package com.golmok.market.global.config;

import com.golmok.market.domain.image.ImageProperties;
import com.golmok.market.domain.image.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 업로드한 이미지를 그대로 내려준다.
 *
 * /api 아래로 서빙하는 이유: 프론트 개발 서버는 /api 만 백엔드로 프록시한다.
 * 다른 경로를 쓰면 프록시 설정과 배포 시 리버스 프록시 규칙을 하나 더 만들어야 한다.
 *
 * 경로 조작(../)은 Spring 의 리소스 핸들러가 막는다. 요청 경로가 실제로 지정한 디렉터리 안인지 검사한다.
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(ImageProperties.class)
public class StaticResourceConfig implements WebMvcConfigurer {

    private final ImageProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(properties.uploadDir()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(ImageService.URL_PREFIX + "**")
                .addResourceLocations(location)
                // 파일명이 UUID 라 내용이 바뀌지 않는다. 오래 캐시해도 안전하다.
                .setCacheControl(org.springframework.http.CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
    }
}
