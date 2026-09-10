package kr.qmftm.betterpets.domain;

/**
 * 등급 하나의 수치. {@code rarity.yml} 에서 읽고, 없으면 {@link Rarity#defaults()} 를 쓴다.
 *
 * <p><b>등급과 수치를 분리한 이유.</b> 등급({@link Rarity})은 서열이자 식별자라 코드가
 * 알아야 한다 — {@code min-rarity: A} 같은 설정이 이름으로 비교된다. 반면 "A등급이 얼마나
 * 빠른가"는 순전히 밸런싱이고, 서버마다 다를 수 있으며, 원작의 실제 수치를 우리가 모른다.
 * 후자를 코드에 박아두면 숫자 하나 고치는 데 재컴파일이 필요하다.
 */
public record RarityStats(
    String displayName,
    double moveSpeedMultiplier,
    double rideSpeed,
    double flyChance,
    int color
) {

    public RarityStats {
        // 음수 속도는 펫을 뒤로 걷게 만든다. 0은 아예 못 움직인다 — 설정 실수의 흔한 형태다.
        moveSpeedMultiplier = Math.max(0.01, moveSpeedMultiplier);
        rideSpeed = Math.max(0.01, rideSpeed);
        flyChance = Math.max(0.0, Math.min(1.0, flyChance));
        color = color & 0xFFFFFF;
    }

    /** 이 등급에서 비행 펫이 나올 수 있는가. */
    public boolean canRollFlight() {
        return flyChance > 0.0;
    }
}
