package kr.qmftm.betterpets.domain;

/**
 * 등급 하나의 표시 정보. {@code rarity.yml} 에서 읽고, 없으면 {@link Rarity#defaults()} 를 쓴다.
 *
 * <p><b>등급은 표기 전용이다.</b> 이동속도·탑승속도 같은 실제 능력치에는 관여하지 않는다.
 * 예전에는 여기에 등급별 배율을 뒀지만, 등급이 실력이 아니라 순전히 표시(이름·서열)만
 * 나타내도록 요청받아 걷어냈다 — 지금 {@code moveSpeedMultiplier}·{@code rideSpeed}는
 * 없고, 실제 속도는 펫 종류마다({@code pets/*.yml}) 정한다.
 */
public record RarityStats(
    String displayName
) {
}
