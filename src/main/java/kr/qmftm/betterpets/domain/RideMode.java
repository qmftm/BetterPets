package kr.qmftm.betterpets.domain;

/**
 * 펫 종류가 지원하는 탑승 방식. {@code pets/*.yml} 의 {@code ride:}/{@code flying:} 두
 * 불리언에서 계산한다({@code ride: false} → {@link #NONE}, {@code ride: true, flying: false}
 * → {@link #GROUND}, {@code ride: true, flying: true} → {@link #FLY}).
 *
 * <p>개체별 추첨은 없다 — {@link #FLY} 종류는 전부 난다. 예전에는 등급·개체별
 * {@code fly-chance} 로 일부만 날게 했지만, 우클릭 한 번으로 바로 타고 이륙하는 쪽이
 * 더 명확하다는 요청으로 걷어냈다. <b>생애주기도 탑승을 막지 않는다</b> — 아기든 성체든
 * 이 값이 {@link #NONE} 이 아니면 탈 수 있다. 진화(성장)는 이것과 별개다.
 */
public enum RideMode {

    /** 탈 수 없다. */
    NONE,

    /** 걷는 탑승만. 지형을 따라 달린다. */
    GROUND,

    /** 나는 탑승. 이 종류는 전부 난다. */
    FLY;

    public boolean canRide() {
        return this != NONE;
    }
}
