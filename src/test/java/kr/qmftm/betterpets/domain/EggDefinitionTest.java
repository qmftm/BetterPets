package kr.qmftm.betterpets.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EggDefinitionTest {

    private static EggDefinition fixed(final String gives) {
        return new EggDefinition("egg", "알", "EGG", null, gives, Map.of());
    }

    private static EggDefinition random(final Map<String, Integer> weights) {
        return new EggDefinition("egg", "알", "EGG", null, null, weights);
    }

    /** 항상 같은 값을 주는 난수. 추첨 경계를 정확히 겨냥한다. */
    private static RandomGenerator fixedRandom(final long value) {
        return new RandomGenerator() {
            @Override public long nextLong() { return value; }
            @Override public long nextLong(final long bound) { return Math.min(value, bound - 1); }
        };
    }

    @Test
    @DisplayName("고정 알은 항상 같은 펫을 준다")
    void fixedEggAlwaysGivesSame() {
        final EggDefinition egg = fixed("dragon");
        assertFalse(egg.isRandom());
        assertEquals("dragon", egg.roll(fixedRandom(0)));
        assertEquals("dragon", egg.roll(fixedRandom(999)));
    }

    @Test
    @DisplayName("랜덤 알은 가중치 순서대로 구간을 나눈다")
    void randomEggRespectsWeights() {
        final Map<String, Integer> weights = new java.util.LinkedHashMap<>();
        weights.put("wolf", 3);
        weights.put("dragon", 1);
        final EggDefinition egg = random(weights);

        assertTrue(egg.isRandom());
        // 총 가중치 4. 0~2 는 wolf, 3 은 dragon.
        assertEquals("wolf", egg.roll(fixedRandom(0)));
        assertEquals("wolf", egg.roll(fixedRandom(2)));
        assertEquals("dragon", egg.roll(fixedRandom(3)));
    }

    @Test
    @DisplayName("가중치 0 이하인 항목은 뽑히지 않는다")
    void ignoresNonPositiveWeights() {
        final Map<String, Integer> weights = new java.util.LinkedHashMap<>();
        weights.put("never", 0);
        weights.put("always", 5);
        final EggDefinition egg = random(weights);

        for (long i = 0; i < 5; i++) {
            assertEquals("always", egg.roll(fixedRandom(i)));
        }
    }

    @Test
    @DisplayName("가중치가 비었거나 전부 0이면 null — 조용히 아무거나 주지 않는다")
    void emptyWeightsYieldNull() {
        assertNull(random(Map.of()).roll(fixedRandom(0)));
        assertNull(random(Map.of("a", 0, "b", -3)).roll(fixedRandom(0)));
    }

    @Test
    @DisplayName("실제 난수로 여러 번 뽑아도 정의된 것만 나온다")
    void rollStaysWithinDefinedTypes() {
        final Map<String, Integer> weights = new HashMap<>();
        weights.put("wolf", 50);
        weights.put("bear", 30);
        weights.put("dragon", 1);
        final EggDefinition egg = random(weights);

        final RandomGenerator random = RandomGenerator.getDefault();
        for (int i = 0; i < 500; i++) {
            final String rolled = egg.roll(random);
            assertNotNull(rolled);
            assertTrue(weights.containsKey(rolled), "정의되지 않은 '" + rolled + "' 가 나왔다");
        }
    }

    @Test
    @DisplayName("가중치 맵은 방어적으로 복사된다")
    void weightsAreDefensivelyCopied() {
        final Map<String, Integer> mutable = new HashMap<>();
        mutable.put("wolf", 1);
        final EggDefinition egg = random(mutable);

        mutable.put("injected", 999);
        assertFalse(egg.weights().containsKey("injected"),
            "외부에서 정의를 바꿀 수 있으면 안 된다");
    }
}
