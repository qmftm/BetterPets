package kr.qmftm.betterpets.domain;

import java.util.EnumMap;
import java.util.Map;

/**
 * 등급별 수치 표. {@code rarity.yml} 을 읽어 만든다.
 *
 * <p><b>빠진 등급은 내장 기본값으로 채운다.</b> 언어 파일과 같은 원칙이다 — 관리자가
 * S등급만 손보고 싶을 때 다섯 등급을 전부 적게 만들 이유가 없고, 나중에 등급이 늘어도
 * 기존 설정 파일이 깨지지 않는다.
 */
public final class RarityTable {

    private final Map<Rarity, RarityStats> stats;

    private RarityTable(final Map<Rarity, RarityStats> stats) {
        this.stats = stats;
    }

    /** 설정 파일이 없을 때. 전부 내장 기본값이다. */
    public static RarityTable defaults() {
        return of(Map.of());
    }

    /**
     * @param overrides 설정에서 읽은 값. 빠진 등급은 {@link Rarity#defaults()} 로 채운다
     */
    public static RarityTable of(final Map<Rarity, RarityStats> overrides) {
        final Map<Rarity, RarityStats> merged = new EnumMap<>(Rarity.class);
        for (final Rarity rarity : Rarity.values()) {
            merged.put(rarity, overrides.getOrDefault(rarity, rarity.defaults()));
        }
        return new RarityTable(merged);
    }

    /** 절대 null 이 아니다 — 모든 등급이 채워져 있다. */
    public RarityStats stats(final Rarity rarity) {
        return stats.get(rarity);
    }
}
