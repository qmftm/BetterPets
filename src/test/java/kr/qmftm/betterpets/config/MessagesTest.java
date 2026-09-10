package kr.qmftm.betterpets.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessagesTest {

    private static final String FALLBACK = Messages.FALLBACK_LANGUAGE;

    @Test
    @DisplayName("흔한 표기 차이는 받아준다")
    void acceptsCommonSpellings() {
        assertEquals("en_us", Messages.normalizeLanguage("en_us"));
        assertEquals("en_us", Messages.normalizeLanguage("en-US"), "하이픈 표기");
        assertEquals("en_us", Messages.normalizeLanguage("EN_US"), "대문자");
        assertEquals("en_us", Messages.normalizeLanguage("  en_us  "), "앞뒤 공백");
    }

    @Test
    @DisplayName("비어 있으면 기본 언어")
    void blankFallsBack() {
        assertEquals(FALLBACK, Messages.normalizeLanguage(null));
        assertEquals(FALLBACK, Messages.normalizeLanguage(""));
        assertEquals(FALLBACK, Messages.normalizeLanguage("   "));
    }

    @Test
    @DisplayName("경로로 쓸 수 없는 값은 기본 언어로 접는다")
    void rejectsPathTraversal() {
        // 이 값들은 그대로 lang/<값>.yml 이 되므로, 통과시키면 데이터 폴더 밖을 읽는다.
        assertEquals(FALLBACK, Messages.normalizeLanguage("../../config"));
        assertEquals(FALLBACK, Messages.normalizeLanguage("/etc/passwd"));
        assertEquals(FALLBACK, Messages.normalizeLanguage("ko/../../secret"));
        assertEquals(FALLBACK, Messages.normalizeLanguage("ko kr"), "공백은 파일명으로 부적절");
        assertEquals(FALLBACK, Messages.normalizeLanguage("a".repeat(33)), "길이 제한");
    }
}
