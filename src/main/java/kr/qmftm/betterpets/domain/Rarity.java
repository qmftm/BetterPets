package kr.qmftm.betterpets.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * 펫 등급. 원작(악어의 놀이터)의 D~S 5단계를 따른다.
 *
 * <p><b>이 enum 은 서열과 식별자만 갖는다.</b> 표시 이름은 {@link RarityStats} 로 빠져
 * {@code rarity.yml} 에서 온다 — 여기 값은 설정이 없을 때의 기본값이다.
 *
 * <p><b>등급은 순전히 표기다.</b> 이동속도·탑승속도 같은 실제 능력치에는 영향을 주지
 * 않는다 — 등급이 실력 차이가 아니라 이름표 하나로만 쓰이도록 요청받아, 등급별 배율을
 * 걷어냈다. 서열은 {@code min-rarity: A} 같은 문턱 판정에서만 의미를 갖는다. 비행 여부는
 * 펫 종류의 {@code flying:} 설정으로만 정해진다 — 등급에 따른 확률 추첨은 없다.
 */
public enum Rarity {

    D("일반"),
    C("고급"),
    B("희귀"),
    A("영웅"),
    S("전설");

    private final RarityStats defaults;

    Rarity(final String displayName) {
        this.defaults = new RarityStats(displayName);
    }

    /** {@code rarity.yml} 이 없거나 이 등급을 적지 않았을 때 쓸 값. */
    public RarityStats defaults() {
        return defaults;
    }

    /**
     * 이 등급이 기준 이상인가. 선언 순서(D→S)가 곧 서열이라 ordinal 로 비교한다.
     *
     * <p>등급 사이에 새 등급을 끼워 넣으려면 <b>선언 위치</b>를 지켜야 한다 —
     * 이름이 아니라 순서가 의미를 갖는다.
     */
    public boolean atLeast(final Rarity floor) {
        return floor == null || ordinal() >= floor.ordinal();
    }

    /**
     * 설정 파일에서 읽은 문자열을 등급으로 해석한다.
     *
     * <p>대소문자를 가리지 않으며, 알 수 없는 값이면 비어 있는 Optional 을 준다.
     * 잘못된 설정을 조용히 D로 떨어뜨리지 않기 위해 기본값을 넣지 않는다.
     */
    public static Optional<Rarity> parse(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (final IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
