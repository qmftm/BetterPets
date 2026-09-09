package kr.qmftm.betterpets.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
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
        // Map.copyOf 는 아니다 — 그건 JVM 마다 랜덤한 순서로 반복시킨다.
        // Weighted.pick 은 순서에 따라 구간을 나누므로, 설정 파일에 적은 순서
        // (LinkedHashMap 이 보존하는 순서)를 그대로 지켜야 결과가 재현 가능하다.
        weights = Collections.unmodifiableMap(new LinkedHashMap<>(weights));
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
        return isRandom() ? Weighted.pick(weights, random, null) : gives;
    }
}
