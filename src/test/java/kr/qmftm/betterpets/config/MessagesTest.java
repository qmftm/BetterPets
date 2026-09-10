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

    // ── 치환 ──────────────────────────────────────────────────────────
    //
    // 언어 파일이 없어도 raw() 는 동작한다 — 키를 못 찾으면 키 이름을 틀로 쓰기 때문에,
    // "%name%" 같은 키를 넘기면 그게 그대로 틀이 된다. 덕분에 서버 없이 검증할 수 있다.

    private static String fill(final String template, final String... placeholders) {
        return new Messages().raw(template, placeholders);
    }

    @Test
    @DisplayName("자리를 값으로 바꾼다")
    void substitutesPlaceholders() {
        assertEquals("안녕 철수", fill("안녕 %name%", "name", "철수"));
        assertEquals("철수/영희", fill("%a%/%b%", "a", "철수", "b", "영희"));
    }

    @Test
    @DisplayName("모르는 자리는 원문 그대로 남긴다 — 빈칸이면 오타를 못 알아챈다")
    void unknownPlaceholderSurvives() {
        assertEquals("%틀린이름%", fill("%틀린이름%", "name", "철수"));
        assertEquals("철수 %없음%", fill("%name% %없음%", "name", "철수"));
    }

    @Test
    @DisplayName("치환한 값 안의 %자리% 는 다시 치환되지 않는다")
    void substitutionDoesNotChain() {
        // 펫 이름은 플레이어가 정한다. "%rarity%" 로 지어두면 등급이 박히는 셈이었다.
        assertEquals("%rarity% 는 S 등급",
            fill("%pet% 는 %rarity% 등급", "pet", "%rarity%", "rarity", "S"));
    }

    @Test
    @DisplayName("자리 순서를 바꿔도 결과가 같다")
    void orderOfPlaceholdersDoesNotMatter() {
        assertEquals(fill("%pet% %rarity%", "pet", "%rarity%", "rarity", "S"),
            fill("%pet% %rarity%", "rarity", "S", "pet", "%rarity%"));
    }

    @Test
    @DisplayName("값에 든 $ 나 백슬래시가 깨지지 않는다")
    void replacementIsLiteral() {
        // Matcher.appendReplacement 는 $1 같은 걸 그룹 참조로 읽는다. 이름에 실제로 들어간다.
        assertEquals("돈$1백슬래시\\끝", fill("%n%끝", "n", "돈$1백슬래시\\"));
    }

    @Test
    @DisplayName("자리 바로 뒤에 글자가 붙어도 잘린다 — 영어 시간 표기가 이 성질을 쓴다")
    void placeholderCanBeFollowedByText() {
        // en_us 의 time.seconds 가 "%s%s" 다. 앞의 %s% 만 자리로 읽고 뒤의 s 는 단위로 남아야
        // "30s" 가 된다. 정규식이 욕심을 부리면 "%s%s" 를 통째로 삼켜 깨진다.
        assertEquals("30s", fill("%s%s", "s", "30"));
        assertEquals("5m 30s", fill("%m%m %s%s", "m", "5", "s", "30"));
    }

    @Test
    @DisplayName("자리를 하나도 안 주면 틀을 그대로 준다")
    void noPlaceholdersLeavesTemplateAlone() {
        assertEquals("%name%", fill("%name%"));
    }
}
