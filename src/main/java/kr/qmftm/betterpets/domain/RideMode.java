package kr.qmftm.betterpets.domain;

/**
 * 펫 종류가 지원하는 탑승 방식. {@code pets/*.yml} 의 {@code ride:}/{@code fly:} 두 불리언에서
 * 계산한다({@code ride: false} → {@link #NONE}, {@code ride: true, fly: false} → {@link #GROUND},
 * {@code ride: true, fly: true} → {@link #FLY}).
 *
 * <p>개체가 실제로 무엇을 할 수 있는지는 여기에 한 가지가 더 곱해진다 — 비행 추첨.
 * {@link #FLY} 종류라도 개체별 {@code fly-chance} 에 실패하면 걷는 탑승까지만 된다
 * ({@link #effective(boolean)}). <b>생애주기는 더 이상 탑승을 막지 않는다</b> — 아기든
 * 성체든 이 값이 {@link #NONE} 이 아니면 탈 수 있다. 진화(성장)는 이것과 별개다.
 */
public enum RideMode {

    /** 탈 수 없다. */
    NONE,

    /** 걷는 탑승만. 지형을 따라 달린다. */
    GROUND,

    /** 나는 탑승. 개체별 비행 추첨에 성공해야 실제로 난다. */
    FLY;

    public boolean canRide() {
        return this != NONE;
    }

    /**
     * 이 개체가 실제로 할 수 있는 탑승 방식.
     *
     * <p>{@link #FLY} 종류인데 비행 추첨에 실패한 개체는 <b>걷는 탑승으로 내려간다.</b>
     * 원작에서도 같은 종이라도 일부만 날탈이 되고 나머지는 지상 탈것으로 쓴다.
     */
    public RideMode effective(final boolean canFly) {
        return this == FLY && !canFly ? GROUND : this;
    }
}
