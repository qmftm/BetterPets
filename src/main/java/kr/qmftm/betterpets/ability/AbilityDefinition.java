package kr.qmftm.betterpets.ability;

import java.util.Map;

/**
 * 설정에서 읽은 능력 하나의 정의.
 *
 * <p>수치를 이름 → 값 맵으로 들고 있어서, 새 능력을 추가할 때 이 클래스를 고칠 필요가 없다.
 * 능력 구현체가 자기가 아는 키를 꺼내 쓴다.
 */
public record AbilityDefinition(String id, Map<String, Double> values) {

    public AbilityDefinition {
        values = Map.copyOf(values);
    }

    public double value(final String key, final double fallback) {
        final Double found = values.get(key);
        return found == null ? fallback : found;
    }

    /**
     * 등급 배율을 반영한 수치를 계산한다. {@code base} 에 등급 배율을 곱한다.
     *
     * <p>예전에는 성장도에 비례해 커지는 {@code per-growth} 항이 있었다. 성장도의
     * 역할을 진화 하나로 좁히면서 걷어냈다 — 능력치는 이제 소환하는 순간부터
     * {@code base} 값 그대로 붙는다.
     */
    public double scaled(final double rarityMultiplier) {
        return value("base", 0.0) * rarityMultiplier;
    }
}
