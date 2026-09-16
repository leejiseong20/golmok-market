package com.golmok.market.domain.image;

/**
 * 허용하는 이미지 형식. 확장자나 Content-Type 이 아니라 **파일 앞부분의 시그니처**로 판별한다.
 *
 * 확장자와 Content-Type 은 클라이언트가 정하는 값이라 믿을 수 없다.
 * 실행 파일의 이름만 .jpg 로 바꿔 올리는 것을 막으려면 내용을 봐야 한다.
 */
public enum ImageType {

    JPEG("jpg", new int[]{0xFF, 0xD8, 0xFF}),
    PNG("png", new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
    // WEBP 은 "RIFF" 다음 4바이트가 파일 크기라 건너뛰고, 8번째부터 "WEBP" 인지 본다.
    WEBP("webp", new int[]{'R', 'I', 'F', 'F'}, 8, new int[]{'W', 'E', 'B', 'P'});

    private final String extension;
    private final int[] signature;
    private final int secondOffset;
    private final int[] secondSignature;

    ImageType(String extension, int[] signature) {
        this(extension, signature, 0, null);
    }

    ImageType(String extension, int[] signature, int secondOffset, int[] secondSignature) {
        this.extension = extension;
        this.signature = signature;
        this.secondOffset = secondOffset;
        this.secondSignature = secondSignature;
    }

    public String getExtension() {
        return extension;
    }

    public static ImageType detect(byte[] content) {
        for (ImageType type : values()) {
            if (type.matches(content)) {
                return type;
            }
        }
        return null;
    }

    private boolean matches(byte[] content) {
        return startsWith(content, 0, signature)
                && (secondSignature == null || startsWith(content, secondOffset, secondSignature));
    }

    private static boolean startsWith(byte[] content, int offset, int[] expected) {
        if (content.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((content[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
