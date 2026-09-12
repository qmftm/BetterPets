package kr.qmftm.betterpets.domain;

/**
 * 포만도 감소 계산. {@link GrowthCurve} 와 같은 지연 계산 방식이지만 방향이 반대다 —
 * 시간이 지나면 오르는 게 아니라 <b>내린다.</b>
 *
 * <p>읽는 시점에 경과 시간을 환산하고, 저장할 때만 반영한다. 1점이 깎이는 데 걸리는
 * 시간은 {@code growth.fullness.decay-seconds} 로 서버마다 다르게 잡을 수 있어서
 * {@link GrowthCurve#MILLIS_PER_POINT} 처럼 상수로 박아두지 않고 인자로 받는다.
 *
 * <p>이 클래스는 순수 함수만 담는다 — Bukkit 에 의존하지 않으므로 단위 테스트가 가능하다.
 */
public final class FullnessCurve {

    private FullnessCurve() {
        throw new AssertionError("유틸리티 클래스");
    }

    /**
     * 시간 경과를 반영한 포만도와, 그에 맞춰 전진시킨 기준 시각.
     *
     * @param fullness  반영 후 포만도 (0 이상)
     * @param updatedAt 다음 계산의 기준이 될 시각. 그대로 저장하면 된다
     */
    public record Projection(int fullness, long updatedAt) {}

    /**
     * 저장된 포만도에 경과 시간을 반영해 깎는다.
     *
     * <p>다음 경우를 모두 방어한다: 이미 0 / 감소 꺼짐(interval ≤ 0) / 시계 역행 /
     * 미래 타임스탬프 / 한 틱 미만 경과.
     *
     * @param storedFullness DB 에 저장돼 있던 포만도
     * @param updatedAt      그 값이 기록된 시각 (epoch millis)
     * @param now            현재 시각 (epoch millis)
     * @param intervalMillis 1점이 깎이는 데 걸리는 시간. 0 이하면 감소하지 않는다
     */
    public static Projection project(final int storedFullness,
                                     final long updatedAt,
                                     final long now,
                                     final long intervalMillis) {
        final int current = Math.max(0, storedFullness);

        // 감소를 꺼둔 설정이다. 값도 기준 시각도 건드리지 않는다.
        if (intervalMillis <= 0) {
            return new Projection(current, updatedAt);
        }
        // 이미 0이면 더 깎을 게 없다. 기준 시각만 현재로 당겨둔다 — 나중에 포만도가
        // 다시 쌓였을 때(급여) 예전 시각 기준으로 몰아서 깎이지 않게 하려는 것이다.
        if (current <= 0) {
            return new Projection(0, now);
        }
        // 시계가 뒤로 갔거나 기준 시각이 미래다. 아무것도 하지 않는 편이 안전하다.
        if (now <= updatedAt) {
            return new Projection(current, updatedAt);
        }

        final long lost = (now - updatedAt) / intervalMillis;
        if (lost <= 0) {
            // 한 틱이 안 지났다. 기준 시각을 그대로 둬야 나머지 시간이 살아남는다.
            return new Projection(current, updatedAt);
        }

        final int applied = (int) Math.min(lost, current);
        final int next = current - applied;

        // 0에 닿았으면 남은 경과 시간은 버린다. 아니면 소비한 만큼만 기준을 전진시킨다.
        final long nextUpdatedAt = next <= 0
            ? now
            : updatedAt + applied * intervalMillis;

        return new Projection(next, nextUpdatedAt);
    }
}
