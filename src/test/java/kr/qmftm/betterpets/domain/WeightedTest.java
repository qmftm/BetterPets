package kr.qmftm.betterpets.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 알의 랜덤 뽑기와 성장 단계의 다음 형태 결정이 공유하는 추첨 알고리즘.
 * {@link EggDefinitionTest} 가 상위 계층(EggDefinition)에서 이미 일부 겹쳐 검증하지만,
 * 여기서는 경계값과 분포 자체를 직접 겨냥한다.
 */
class WeightedTest {

    /** 항상 같은 값을 주는 난수. 추첨 구간의 경계를 정확히 겨냥한다. */
    private static RandomGenerator fixed(final long value) {
        return new RandomGenerator() {
            @Override public long nextLong() { return value; }
            @Override public long nextLong(final long bound) { return Math.min(value, bound - 1); }
        };
    }

    @Test
    @DisplayName("가중치 구간의 시작과 끝을 정확히 나눈다")
    void splitsWeightRangesExactly() {
        final Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("a", 3);
        weights.put("b", 1);
        // 총 4. 0~2 는 a, 3 은 b.
        assertEquals("a", Weighted.pick(weights, fixed(0), null));
        assertEquals("a", Weighted.pick(weights, fixed(2), null));
        assertEquals("b", Weighted.pick(weights, fixed(3), null));
    }

    @Test
    @DisplayName("가중치가 하나뿐이면 항상 그것만 나온다")
    void singleEntryAlwaysWins() {
        final Map<String, Integer> weights = Map.of("only", 5);
        for (long i = 0; i < 5; i++) {
            assertEquals("only", Weighted.pick(weights, fixed(i), null));
        }
    }

    @Test
    @DisplayName("가중치 0 이하는 후보에서 제외된다")
    void excludesNonPositiveWeights() {
        final Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("zero", 0);
        weights.put("negative", -5);
        weights.put("real", 10);

        for (long i = 0; i < 10; i++) {
            assertEquals("real", Weighted.pick(weights, fixed(i), null));
        }
    }

    @Test
    @DisplayName("후보가 없으면 fallback 을 준다 — 조용히 아무거나 고르지 않는다")
    void emptyYieldsFallback() {
        assertEquals("fallback", Weighted.pick(Map.of(), fixed(0), "fallback"));
        assertNull(Weighted.pick(Map.of(), fixed(0), null));
    }

    @Test
    @DisplayName("전부 0 이하면 후보가 없는 것과 같다")
    void allNonPositiveYieldsFallback() {
        final Map<String, Integer> weights = Map.of("a", 0, "b", -1);
        assertEquals("fallback", Weighted.pick(weights, fixed(0), "fallback"));
    }

    @Test
    @DisplayName("실제 난수로 반복 추첨해도 정의된 후보만 나온다")
    void realRandomStaysWithinCandidates() {
        final Map<String, Integer> weights = new HashMap<>();
        weights.put("common", 70);
        weights.put("rare", 25);
        weights.put("legendary", 5);

        final RandomGenerator random = RandomGenerator.getDefault();
        for (int i = 0; i < 500; i++) {
            final String picked = Weighted.pick(weights, random, null);
            assertNotNull(picked);
            assertTrue(weights.containsKey(picked));
        }
    }

    @Test
    @DisplayName("순서를 보존하는 맵이면 결과가 재현 가능하다 — Map.copyOf 였다면 이게 흔들렸을 것")
    void resultIsReproducibleWithOrderPreservingMap() {
        // 이 테스트가 실제로 잡아낸 버그: EggDefinition/PetType 이 Map.copyOf 를 쓰면
        // JVM 마다(심지어 같은 JVM 재실행마다) 반복 순서가 랜덤해져, 여기서 하드코딩한
        // 경계값 자체가 매번 달라진다. LinkedHashMap 으로 순서를 보존해야 고정된다.
        final Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("first", 3);
        weights.put("second", 1);

        assertEquals("first", Weighted.pick(weights, fixed(0), null));
        assertEquals("first", Weighted.pick(weights, fixed(2), null));
        assertEquals("second", Weighted.pick(weights, fixed(3), null));
    }

    @Test
    @DisplayName("입력 맵을 바꾸지 않는다")
    void doesNotMutateInput() {
        final Map<String, Integer> weights = new HashMap<>(Map.of("a", 1, "b", 2));
        final Map<String, Integer> snapshot = new HashMap<>(weights);

        Weighted.pick(weights, fixed(0), null);

        assertEquals(snapshot, weights);
    }
}
