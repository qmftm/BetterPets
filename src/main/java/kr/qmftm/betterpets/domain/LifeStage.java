package kr.qmftm.betterpets.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * 펫 생애주기.
 *
 * <pre>
 *   BABY ──성장도 100──▶ ADULT
 *     │
 *     └──과급식──▶ PIG (이스터에그)
 * </pre>
 *
 * <p>알은 생애주기 상태가 아니다 — 알 아이템은 그 안에 담긴 펫을 곧바로 아기로 꺼내주는
 * 아이템이고, 보관함에 "알" 상태로 남지 않는다.
 */
public enum LifeStage {

    /** 아기. 소환해 데리고 다닐 수 있으나 탑승은 불가. */
    BABY(false),

    /** 성체. 탑승 가능하며 등급별 능력이 발현된다. */
    ADULT(true),

    /** 과급식 기믹으로 변한 상태. 탑승은 되지만 능력은 없다. */
    PIG(true);

    private final boolean rideable;

    LifeStage(final boolean rideable) {
        this.rideable = rideable;
    }

    /**
     * 이 상태에서 탑승할 수 있는가.
     * 펫 종류의 {@link RideMode} 와 AND 로 묶여야 최종 판정이 된다 —
     * 성체라고 해서 모든 펫을 탈 수 있는 것은 아니다.
     */
    public boolean rideable() {
        return rideable;
    }

    /** 등급별 능력이 발현되는 상태인가. */
    public boolean abilitiesActive() {
        return this == ADULT;
    }

    public static Optional<LifeStage> parse(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (final IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
