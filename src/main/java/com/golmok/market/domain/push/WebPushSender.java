package com.golmok.market.domain.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 실제로 푸시 서비스에 보낸다(RFC 8030). JDK HttpClient 를 쓴다.
 *
 * 머리값: TTL(기기가 꺼져 있으면 기다릴 시간), Content-Encoding: aes128gcm(RFC 8291 암호문),
 * Authorization: vapid …(RFC 8292), Urgency.
 */
@Slf4j
@Component
public class WebPushSender implements PushGateway {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            // 푸시 서비스는 리다이렉트하지 않는다. 따라가면 허용 목록(PushEndpoints)을 우회할 수 있다.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public Result send(String endpoint, byte[] encryptedBody, String authorization, Duration ttl, Urgency urgency) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("TTL", String.valueOf(ttl.toSeconds()))
                .header("Urgency", urgency.header)
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofByteArray(encryptedBody))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return Result.SENT;
            }
            if (status == 404 || status == 410) {
                return Result.GONE;
            }
            // 주소 전체는 기기 식별자라 남기지 않는다. 어느 서비스인지만 남긴다.
            log.warn("푸시 전송 실패: {} {} {}", URI.create(endpoint).getHost(), status,
                    response.body().length() > 200 ? response.body().substring(0, 200) : response.body());
            return Result.FAILED;
        } catch (IOException e) {
            log.warn("푸시 전송 실패: {} {}", URI.create(endpoint).getHost(), e.toString());
            return Result.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        }
    }
}
