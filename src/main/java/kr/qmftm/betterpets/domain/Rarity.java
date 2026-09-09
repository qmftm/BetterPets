package kr.qmftm.betterpets.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * 펫 등급. 원작(악어의 놀이터)의 D~S 5단계를 따른다.
 *
 * <p>수치는 모두 플레이스홀더다. 밸런싱은 M7에서 rarity.yml 로 외부화하며,
 * 여기 값은 설정이 없을 때의 기본값 역할만 한다.
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

    private final String displayName;
    private final double moveSpeedMultiplier;
    private final double rideSpeed;
    private final double flyChance;
    private final int color;

    Rarity(final String displayName,
           final double moveSpeedMultiplier,
           final double rideSpeed,
           final double flyChance,
           final int color) {
        this.displayName = displayName;
        this.moveSpeedMultiplier = moveSpeedMultiplier;
        this.rideSpeed = rideSpeed;
        this.flyChance = flyChance;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    /** 추종 이동 속도 배율. 등급이 오를수록 커진다. */
    public double moveSpeedMultiplier() {
        return moveSpeedMultiplier;
    }

    /** 탑승 시 기본 이동 속도(블록/틱). */
    public double rideSpeed() {
        return rideSpeed;
    }

    /** 부화 시 비행 능력이 부여될 확률. A등급 미만은 0이다. */
    public double flyChance() {
        return flyChance;
    }

    /** 표시용 RGB. 모델 틴트와 GUI 테두리에 함께 쓴다. */
    public int color() {
        return color;
    }

    /** 이 등급에서 비행 펫이 나올 수 있는가. */
    public boolean canRollFlight() {
        return flyChance > 0.0;
    }

    /**
     * 설정 파일에서 읽은 문자열을 등급으로 해석한다.
     * 대소문자를 가리지 않으며, 알 수 없는 값이면 비어 있는 Optional 을 준다.
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
