package kr.qmftm.betterpets.domain;

import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * 알 아이템 정의. {@code eggs.yml} 에서 로드한다.
 *
 * <p>두 종류를 지원한다:
 * <ul>
 *   <li><b>고정 알</b> — {@code gives} 로 지정한 펫 하나를 준다
 *   <li><b>랜덤 알</b> — {@code weights} 의 가중치 추첨. 원작의 뽑기 감각을 아이템으로 대체한다
 * </ul>
 */
public record EggDefinition(
    String id,
    String displayName,
    String material,
    String itemModel,                // nullable — 리소스팩 모델 키 (예: betterpets:egg_wolf)
    String gives,                    // nullable — 고정 알
    Map<String, Integer> weights     // 비어 있으면 고정 알
) {

    public EggDefinition {
        weights = Map.copyOf(weights);
    }

    public boolean isRandom() {
        return gives == null || gives.isBlank();
    }

    /**
     * 이 알이 줄 펫 종류를 결정한다.
     *
     * @return 펫 종류 id. 랜덤 알인데 가중치가 비었거나 전부 0 이하면 null
     */
    public String roll(final RandomGenerator random) {
        if (!isRandom()) {
            return gives;
        }
        final List<Map.Entry<String, Integer>> entries = weights.entrySet().stream()
            .filter(e -> e.getValue() != null && e.getValue() > 0)
            .toList();
        if (entries.isEmpty()) {
            return null;
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
