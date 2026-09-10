package kr.qmftm.betterpets.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * 펫 등급. 원작(악어의 놀이터)의 D~S 5단계를 따른다.
 *
 * <p><b>이 enum 은 서열과 식별자만 갖는다.</b> 실제 수치(속도·비행 확률·색)는
 * {@link RarityStats} 로 빠져 {@code rarity.yml} 에서 온다 — 여기 값은 설정이 없을 때의
 * 기본값일 뿐이고, 그마저도 <b>플레이스홀더다.</b> 원작의 실제 수치는 확인하지 못했다.
 *
 * <p>원작에서 확정된 규칙은 두 가지뿐이다:
 * 등급이 오를수록 이동속도가 빨라지고, 비행은 A등급부터 확률적으로 나온다.
 */
public enum Rarity {

    D("일반", 1.00, 0.20, 0.00, 0xAAAAAA),
    C("고급", 1.10, 0.24, 0.00, 0xFFFFFF),
    B("희귀", 1.25, 0.28, 0.00, 0x55FF55),
    A("영웅", 1.45, 0.34, 0.10, 0x5555FF),
    S("전설", 1.70, 0.42, 0.35, 0xFFAA00);

    private final RarityStats defaults;

    Rarity(final String displayName,
           final double moveSpeedMultiplier,
           final double rideSpeed,
           final double flyChance,
           final int color) {
        this.defaults = new RarityStats(displayName, moveSpeedMultiplier, rideSpeed, flyChance, color);
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
