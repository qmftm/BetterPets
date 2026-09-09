package kr.qmftm.betterpets.domain;

import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * 가중치 추첨.
 *
 * <p>알의 랜덤 뽑기와 성장 단계에서 다음 형태를 정하는 것이 같은 알고리즘이라 공유한다.
 *
 * <p><b>맵의 반복 순서가 결과에 영향을 준다</b> — 먼저 나온 항목이 더 낮은 구간을 차지한다.
 * 확률 자체(가중치 비율)는 순서와 무관하게 항상 맞지만, 같은 시드로도 순서가 다르면 다른
 * 결과가 나온다. {@code Map.copyOf} 는 JVM 마다 랜덤한 순서로 반복시키므로, 호출부는
 * {@code LinkedHashMap} 처럼 <b>순서를 보존하는 맵</b>을 넘겨야 한다.
 */
public final class Weighted {

    private Weighted() {
        throw new AssertionError("유틸리티 클래스");
    }

    /**
     * 가중치에 따라 하나를 고른다.
     *
     * @param weights 순서를 보존하는 맵이어야 한다 (클래스 문서 참고)
     * @return 가중치가 비었거나 전부 0 이하면 {@code fallback}
     */
    public static <T> T pick(final Map<T, Integer> weights, final RandomGenerator random, final T fallback) {
        final List<Map.Entry<T, Integer>> entries = weights.entrySet().stream()
            .filter(e -> e.getValue() != null && e.getValue() > 0)
            .toList();
        if (entries.isEmpty()) {
            return fallback;
        }
        long total = 0;
        for (final var entry : entries) {
            total += entry.getValue();
        }
        long pick = random.nextLong(total);
        for (final var entry : entries) {
            pick -= entry.getValue();
            if (pick < 0) {
                return entry.getKey();
            }
        }
        // 부동소수점이 아니라 정수 누적이므로 여기 도달하지 않지만, 방어적으로 마지막을 준다.
        return entries.get(entries.size() - 1).getKey();
    }
}
