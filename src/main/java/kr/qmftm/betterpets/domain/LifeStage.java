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

    /**
     * 아기. 소환해 데리고 다닐 수 있다. 탑승도 능력도 생애주기와 무관하게 처음부터
     * 된다 — {@link RideMode} 와 {@link #abilitiesActive()} 참고. 여기 남는 건 순전히
     * 성장도(먹이·시간 경과)뿐이고, {@code next-stage} 가 있는 펫만 그걸로 다른 종류가
     * 된다. 나머지 펫에게 성장도는 진행 표시일 뿐 아무것도 잠그지 않는다.
     */
    BABY,

    /** 성체. 성장도가 다 찼다는 뜻일 뿐, 능력은 이미 아기 때부터 붙어 있었다. */
    ADULT,

    /** 과급식 기믹으로 변한 상태. 능력이 없는 게 이 상태의 대가다. */
    PIG;

    /**
     * 등급별 능력이 발현되는 상태인가.
     *
     * <p>{@code PIG} 만 제외한다 — 과급식으로 이 꼴이 된 대가로 능력을 잃는다는
     * 설계다. {@code BABY}/{@code ADULT} 는 구분 없이 둘 다 능력이 붙는다: "다 자라야
     * 쓸모 있어진다"는 대기 시간이 모든 펫에게 있을 이유가 없고, 그건 다른 종류로
     * 진화하는 펫({@code next-stage} 가 있는 쪽)만의 재미로 남겨둔다.
     */
    public boolean abilitiesActive() {
        return this != PIG;
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
