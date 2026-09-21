package com.golmok.market.domain.push;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * 구독 주소 허용 목록.
 *
 * endpoint 는 브라우저가 준 값을 클라이언트가 그대로 보내는 것이라 믿을 수 없다. 검사하지 않으면
 * 서버가 아무 주소로나 POST 를 보내게 된다(SSRF). 예컨대 http://169.254.169.254/ 를 넣으면
 * 서버 안에서 클라우드 메타데이터 주소를 두드린다. 실제 브라우저가 발급하는 푸시 서비스만 허용한다.
 */
final class PushEndpoints {

    private static final Set<String> HOSTS = Set.of(
            "fcm.googleapis.com",                // Chrome · Android · 삼성 인터넷
            "updates.push.services.mozilla.com", // Firefox
            "web.push.apple.com"                 // Safari · iOS 홈 화면 앱
    );
    /** Edge 는 지역마다 호스트가 다르다(wns2-xxx.notify.windows.com). */
    private static final String WINDOWS_SUFFIX = ".notify.windows.com";

    private PushEndpoints() {
    }

    static boolean allowed(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            return false;
        }
        // 다른 포트는 쓰지 않는다. 허용된 호스트의 다른 포트로 우회하는 것을 막는다.
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return HOSTS.contains(host) || host.endsWith(WINDOWS_SUFFIX);
    }
}
