package kr.qmftm.betterpets.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static kr.qmftm.betterpets.domain.GrowthCurve.MILLIS_PER_POINT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthCurveTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final int MAX = 100;

    @Test
    @DisplayName("1분이 지나면 1 오른다")
    void gainsOnePointPerMinute() {
        final var result = GrowthCurve.project(0, T0, T0 + MILLIS_PER_POINT, MAX);
        assertEquals(1, result.growth());
    }

    @Test
    @DisplayName("10분이 지나면 10 오른다")
    void gainsProportionally() {
        final var result = GrowthCurve.project(5, T0, T0 + 10 * MILLIS_PER_POINT, MAX);
        assertEquals(15, result.growth());
    }

    @Test
    @DisplayName("1분 미만이면 오르지 않고, 기준 시각도 그대로다")
    void keepsRemainderWhenUnderOneMinute() {
        final long now = T0 + 59_000L;
        final var result = GrowthCurve.project(3, T0, now, MAX);

        assertEquals(3, result.growth());
        // 기준을 now 로 밀어버리면 59초가 증발한다. 그대로 있어야 한다.
        assertEquals(T0, result.updatedAt());
    }

    @Test
    @DisplayName("저장을 반복해도 나머지 시간이 쌓여 결국 성장한다")
    void remainderSurvivesRepeatedSaves() {
        // 30초 간격으로 네 번 저장 = 총 2분. 순진한 구현이면 영원히 0에 머문다.
        int growth = 0;
        long updatedAt = T0;
        for (int i = 1; i <= 4; i++) {
            final var result = GrowthCurve.project(growth, updatedAt, T0 + i * 30_000L, MAX);
            growth = result.growth();
            updatedAt = result.updatedAt();
        }
        assertEquals(2, growth, "2분이 지났으므로 2 올라야 한다");
    }

    @Test
    @DisplayName("90초 경과 시 1만 오르고 남은 30초는 기준 시각에 남는다")
    void carriesPartialProgressForward() {
        final var result = GrowthCurve.project(0, T0, T0 + 90_000L, MAX);

        assertEquals(1, result.growth());
        assertEquals(T0 + MILLIS_PER_POINT, result.updatedAt(), "소비한 60초만 전진해야 한다");
    }

    @Test
    @DisplayName("상한을 넘지 않는다")
    void clampsToMax() {
        final var result = GrowthCurve.project(95, T0, T0 + 1000 * MILLIS_PER_POINT, MAX);
        assertEquals(MAX, result.growth());
    }

    @Test
    @DisplayName("이미 만렙이면 그대로 두고 기준 시각만 당긴다")
    void alreadyFullStaysFull() {
        final long now = T0 + 500 * MILLIS_PER_POINT;
        final var result = GrowthCurve.project(MAX, T0, now, MAX);

        assertEquals(MAX, result.growth());
        assertEquals(now, result.updatedAt());
    }

    @Test
    @DisplayName("시계가 뒤로 가도 성장도가 줄지 않는다")
    void survivesClockGoingBackwards() {
        final var result = GrowthCurve.project(20, T0, T0 - 10 * MILLIS_PER_POINT, MAX);

        assertEquals(20, result.growth());
        assertEquals(T0, result.updatedAt(), "기준 시각을 과거로 되돌리면 안 된다");
    }

    @Test
    @DisplayName("음수 성장도는 0으로 보정된다")
    void clampsNegativeInput() {
        final var result = GrowthCurve.project(-50, T0, T0, MAX);
        assertEquals(0, result.growth());
    }

    @Test
    @DisplayName("아주 긴 경과 시간에도 int 오버플로가 없다")
    void handlesHugeElapsedTime() {
        final var result = GrowthCurve.project(0, 0L, Long.MAX_VALUE / 2, MAX);
        assertEquals(MAX, result.growth());
    }

    @Test
    @DisplayName("상한이 0이면 항상 0이다")
    void zeroMaxYieldsZero() {
        final var result = GrowthCurve.project(50, T0, T0 + 100 * MILLIS_PER_POINT, 0);
        assertEquals(0, result.growth());
    }

    @Test
    @DisplayName("먹이는 지정한 만큼 올리고 상한을 넘지 않는다")
    void feedRaisesAndClamps() {
        assertEquals(10, GrowthCurve.feed(0, 10, MAX));
        assertEquals(MAX, GrowthCurve.feed(95, 10, MAX));
        assertEquals(MAX, GrowthCurve.feed(0, Integer.MAX_VALUE, MAX), "오버플로 없이 상한에 멈춰야 한다");
    }

    @Test
    @DisplayName("먹이 증가량이 0 이하면 아무 일도 없다")
    void feedIgnoresNonPositive() {
        assertEquals(7, GrowthCurve.feed(7, 0, MAX));
        assertEquals(7, GrowthCurve.feed(7, -5, MAX));
    }

    @Test
    @DisplayName("다음 성장까지 남은 시간이 줄어든다")
    void reportsTimeUntilNextPoint() {
        assertEquals(MILLIS_PER_POINT, GrowthCurve.millisUntilNextPoint(0, T0, T0, MAX));
        assertEquals(30_000L, GrowthCurve.millisUntilNextPoint(0, T0, T0 + 30_000L, MAX));
    }

    @Test
    @DisplayName("만렙이면 남은 시간은 -1")
    void reportsNoNextPointWhenFull() {
        assertEquals(-1L, GrowthCurve.millisUntilNextPoint(MAX, T0, T0, MAX));
    }

    @Test
    @DisplayName("진행률은 0.0~1.0 범위다")
    void progressIsNormalized() {
        assertEquals(0.0, GrowthCurve.progress(0, MAX));
        assertEquals(0.5, GrowthCurve.progress(50, MAX));
        assertEquals(1.0, GrowthCurve.progress(MAX, MAX));
        assertEquals(1.0, GrowthCurve.progress(50, 0), "상한이 0이면 완료로 본다");
        assertTrue(GrowthCurve.progress(-10, MAX) >= 0.0);
    }

    @Test
    @DisplayName("유틸리티 클래스는 인스턴스화할 수 없다")
    void cannotInstantiate() throws Exception {
        final var constructor = GrowthCurve.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(Exception.class, constructor::newInstance);
    }
}
