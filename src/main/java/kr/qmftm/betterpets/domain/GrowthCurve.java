package kr.qmftm.betterpets.domain;

/**
 * 성장도 계산. 원작 시즌 2를 따라 <b>1분에 1씩</b> 오르고, 먹이를 주면 한 번에 더 오른다.
 *
 * <h2>왜 지연 계산인가</h2>
 * 1분마다 모든 펫을 순회하며 +1 하고 DB에 쓰는 방식은 보유 펫 수에 비례해 쓰기가 늘어난다.
 * 대신 {@code updatedAt} 을 기준으로 <b>읽는 시점에 경과 시간을 환산</b>하고, 저장할 때만
 * 반영한다. 주기 작업이 아예 없어져서 구현도 더 단순하다.
 *
 * <p>핵심은 {@link #project}가 새 성장도와 <b>새 기준 시각을 함께</b> 돌려준다는 것이다.
 * 기준 시각을 무조건 {@code now} 로 밀면 1분에 못 미친 나머지 시간이 매번 버려져,
 * 저장이 잦을수록 펫이 영원히 자라지 않는 버그가 된다.
 *
 * <p>이 클래스는 순수 함수만 담는다 — Bukkit 에 의존하지 않으므로 단위 테스트가 가능하다.
 */
public final class GrowthCurve {

    /** 성장도 1을 얻는 데 걸리는 시간. */
    public static final long MILLIS_PER_POINT = 60_000L;

    private GrowthCurve() {
        throw new AssertionError("유틸리티 클래스");
    }

    /**
     * 시간 경과를 반영한 성장도와, 그에 맞춰 전진시킨 기준 시각.
     *
     * @param growth    반영 후 성장도 (0 이상 max 이하)
     * @param updatedAt 다음 계산의 기준이 될 시각. 그대로 저장하면 된다
     */
    public record Projection(int growth, long updatedAt) {}

    /**
     * 저장된 성장도에 경과 시간을 반영한다.
     *
     * <p>다음 경우를 모두 방어한다:
     * 이미 만렙 / 시계 역행 / 미래 타임스탬프 / 1분 미만 경과 / 음수 입력 / 상한 초과.
     *
     * @param storedGrowth DB 에 저장돼 있던 성장도
     * @param updatedAt    그 값이 기록된 시각 (epoch millis)
     * @param now          현재 시각 (epoch millis)
     * @param max          이 펫의 성장도 상한
     */
    public static Projection project(final int storedGrowth,
                                     final long updatedAt,
                                     final long now,
                                     final int max) {
        final int cappedMax = Math.max(0, max);
        final int current = clamp(storedGrowth, 0, cappedMax);

        // 이미 다 자랐다면 시간을 누적할 이유가 없다. 기준 시각만 현재로 당겨둔다.
        if (current >= cappedMax) {
            return new Projection(cappedMax, now);
        }

        // 시계가 뒤로 갔거나 기준 시각이 미래다. 아무것도 하지 않는 편이 안전하다.
        if (now <= updatedAt) {
            return new Projection(current, updatedAt);
        }

        final long gained = (now - updatedAt) / MILLIS_PER_POINT;
        if (gained <= 0) {
            // 1분이 안 지났다. 기준 시각을 그대로 둬야 나머지 시간이 살아남는다.
            return new Projection(current, updatedAt);
        }

        final int room = cappedMax - current;
        final int applied = (int) Math.min(gained, room);
        final int next = current + applied;

        // 상한에 닿았으면 남은 경과 시간은 버린다. 아니면 소비한 만큼만 기준을 전진시킨다.
        final long nextUpdatedAt = next >= cappedMax
            ? now
            : updatedAt + applied * MILLIS_PER_POINT;

        return new Projection(next, nextUpdatedAt);
    }

    /**
     * 먹이를 줘서 성장도를 올린다. 상한을 넘지 않는다.
     *
     * @param amount 증가량. 원작의 우유는 10이다
     */
    public static int feed(final int current, final int amount, final int max) {
        final int cappedMax = Math.max(0, max);
        final int base = clamp(current, 0, cappedMax);
        if (amount <= 0) {
            return base;
        }
        // int 덧셈 오버플로를 피해 long 으로 더한 뒤 좁힌다.
        final long raised = (long) base + amount;
        return (int) Math.min(raised, cappedMax);
    }

    /**
     * 다음 성장도 1점까지 남은 시간(밀리초). GUI 의 "다음 성장까지" 표시에 쓴다.
     * 이미 만렙이거나 계산할 수 없으면 -1 을 준다.
     */
    public static long millisUntilNextPoint(final int storedGrowth,
                                            final long updatedAt,
                                            final long now,
                                            final int max) {
        final Projection projected = project(storedGrowth, updatedAt, now, max);
        if (projected.growth() >= Math.max(0, max)) {
            return -1L;
        }
        if (now <= projected.updatedAt()) {
            return MILLIS_PER_POINT;
        }
        final long sinceLastPoint = (now - projected.updatedAt()) % MILLIS_PER_POINT;
        return MILLIS_PER_POINT - sinceLastPoint;
    }

    /** 성장 진행률 0.0~1.0. 상한이 0이면 1.0(완료)으로 본다. */
    public static double progress(final int growth, final int max) {
        if (max <= 0) {
            return 1.0;
        }
        return clamp(growth, 0, max) / (double) max;
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }
}
