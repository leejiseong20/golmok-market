package com.golmok.market.domain.admin;

/**
 * 관리자 화면의 글자 다루기. 이메일 가리기와 검색어 이스케이프.
 */
public final class AdminText {

    /** LIKE 검색의 이스케이프 문자. 역슬래시는 DB·문자열마다 해석이 달라 쓰지 않는다. */
    public static final char LIKE_ESCAPE = '!';

    private AdminText() {
    }

    /**
     * 이메일을 가린다. demo4@golmok.test → de***@golmok.test
     *
     * 관리자라도 필요 이상으로 개인정보를 보지 않게 한다. 사람을 가리는 데는 닉네임과 id 로 충분하고,
     * 찾을 때는 이메일 전체를 입력하면 된다(검색은 원래 값과 비교한다).
     * 앞부분이 두 글자 이하면 한 글자만 남긴다(두 글자를 다 보이면 가린 뜻이 없다).
     */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        int visible = at <= 2 ? 1 : 2;
        return email.substring(0, visible) + "***" + email.substring(at);
    }

    /** 사용자가 적은 % · _ 를 글자 그대로 찾도록 이스케이프한다. 비어 있으면 null(검색하지 않음). */
    public static String likeKeyword(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String escape = String.valueOf(LIKE_ESCAPE);
        return raw.strip()
                .replace(escape, escape + escape)
                .replace("%", escape + "%")
                .replace("_", escape + "_");
    }
}
