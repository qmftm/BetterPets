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
     * 성장도에 비례해 커지는 수치를 계산한다.
     *
     * <p>{@code base + perGrowth * growth} 에 등급 배율을 곱한다.
     * 대부분의 능력이 이 형태라 여기 모아둔다.
     */
    public double scaled(final int growth, final double rarityMultiplier) {
        return (value("base", 0.0) + value("per-growth", 0.0) * growth) * rarityMultiplier;
    }
}
