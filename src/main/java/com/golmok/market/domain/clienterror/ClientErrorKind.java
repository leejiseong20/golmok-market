package com.golmok.market.domain.clienterror;

/** 화면에서 난 오류의 종류. */
public enum ClientErrorKind {

    /** 화면을 그리다 던졌다(오류 경계가 잡았다). */
    RENDER,
    /** 나눠 불러오는 파일을 받지 못했다. 배포 뒤 예전 화면을 켜 둔 경우가 대부분이다. */
    CHUNK
}
