package kr.qmftm.betterpets.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeedDefinitionTest {

    private static FeedDefinition feed(final Integer growth) {
        return new FeedDefinition("milk", "우유", "MILK_BUCKET", null, growth);
    }

    @Test
    @DisplayName("growth 를 지정하면 그 값을 쓴다")
    void explicitGrowthWins() {
        assertEquals(30, feed(30).growthOr(10));
    }

    @Test
    @DisplayName("growth 를 지정하지 않으면 전역 기본값을 쓴다")
    void unsetGrowthFallsBack() {
        assertEquals(10, feed(null).growthOr(10));
    }

    @Test
    @DisplayName("0 도 지정한 값으로 인정한다 — 기본값으로 덮어쓰지 않는다")
    void zeroIsAnExplicitValue() {
        // 설정 검증이 0 이하를 경고하지만, 관리자가 일부러 0을 넣었다면
        // 조용히 기본값으로 바꿔치기하는 편이 더 헷갈린다.
        assertEquals(0, feed(0).growthOr(10));
    }
}
