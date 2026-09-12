package kr.qmftm.betterpets.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FullnessCurveTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final long INTERVAL = 30_000L;   // 기본값 30초

    @Test
    @DisplayName("한 간격이 지나면 1 내려간다")
    void losesOnePointPerInterval() {
        final var result = FullnessCurve.project(10, T0, T0 + INTERVAL, INTERVAL);
        assertEquals(9, result.fullness());
    }

    @Test
    @DisplayName("다섯 간격이 지나면 5 내려간다")
    void losesProportionally() {
        final var result = FullnessCurve.project(20, T0, T0 + 5 * INTERVAL, INTERVAL);
        assertEquals(15, result.fullness());
    }

    @Test
    @DisplayName("한 간격 미만이면 안 내려가고, 기준 시각도 그대로다")
    void keepsRemainderUnderOneInterval() {
        final long now = T0 + 29_000L;
        final var result = FullnessCurve.project(10, T0, now, INTERVAL);

        assertEquals(10, result.fullness());
        // 기준을 now 로 밀어버리면 29초가 증발한다. 그대로 있어야 한다.
        assertEquals(T0, result.updatedAt());
    }

    @Test
    @DisplayName("저장을 반복해도 나머지 시간이 쌓여 결국 내려간다")
    void remainderSurvivesRepeatedSaves() {
        // 10초 간격으로 세 번 확인 = 총 30초 = 1간격. 순진한 구현이면 영원히 그대로다.
        int fullness = 10;
        long updatedAt = T0;
        for (int i = 1; i <= 3; i++) {
            final var result = FullnessCurve.project(fullness, updatedAt, T0 + i * 10_000L, INTERVAL);
            fullness = result.fullness();
            updatedAt = result.updatedAt();
        }
        assertEquals(9, fullness, "30초가 지났으므로 1 내려가야 한다");
    }

    @Test
    @DisplayName("한 간격 반이 지나면 1만 내려가고 남은 반은 기준 시각에 남는다")
    void carriesPartialProgressForward() {
        final var result = FullnessCurve.project(10, T0, T0 + INTERVAL + INTERVAL / 2, INTERVAL);

        assertEquals(9, result.fullness());
        assertEquals(T0 + INTERVAL, result.updatedAt(), "소비한 한 간격만 전진해야 한다");
    }

    @Test
    @DisplayName("0 밑으로는 안 내려간다")
    void clampsAtZero() {
        final var result = FullnessCurve.project(2, T0, T0 + 1000 * INTERVAL, INTERVAL);
        assertEquals(0, result.fullness());
    }

    @Test
    @DisplayName("이미 0이면 그대로 두고 기준 시각만 당긴다")
    void alreadyZeroStaysZero() {
        final long now = T0 + 500 * INTERVAL;
        final var result = FullnessCurve.project(0, T0, now, INTERVAL);

        assertEquals(0, result.fullness());
        assertEquals(now, result.updatedAt());
    }

    @Test
    @DisplayName("감소 간격이 0 이하면 꺼진 것으로 본다 — 값도 기준 시각도 안 바뀐다")
    void nonPositiveIntervalDisablesDecay() {
        final var zero = FullnessCurve.project(10, T0, T0 + 1000 * INTERVAL, 0);
        assertEquals(10, zero.fullness());
        assertEquals(T0, zero.updatedAt());

        final var negative = FullnessCurve.project(10, T0, T0 + 1000 * INTERVAL, -1L);
        assertEquals(10, negative.fullness());
        assertEquals(T0, negative.updatedAt());
    }

    @Test
    @DisplayName("시계가 뒤로 가도 포만도가 오르지 않는다")
    void survivesClockGoingBackwards() {
        final var result = FullnessCurve.project(10, T0, T0 - 10 * INTERVAL, INTERVAL);

        assertEquals(10, result.fullness());
        assertEquals(T0, result.updatedAt(), "기준 시각을 과거로 되돌리면 안 된다");
    }

    @Test
    @DisplayName("음수 포만도는 0으로 보정된다")
    void clampsNegativeInput() {
        final var result = FullnessCurve.project(-50, T0, T0, INTERVAL);
        assertEquals(0, result.fullness());
    }

    @Test
    @DisplayName("아주 긴 경과 시간에도 int 오버플로가 없다")
    void handlesHugeElapsedTime() {
        final var result = FullnessCurve.project(100, 0L, Long.MAX_VALUE / 2, INTERVAL);
        assertEquals(0, result.fullness());
    }

    @Test
    @DisplayName("유틸리티 클래스는 인스턴스화할 수 없다")
    void cannotInstantiate() throws Exception {
        final var constructor = FullnessCurve.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(Exception.class, constructor::newInstance);
    }
}
