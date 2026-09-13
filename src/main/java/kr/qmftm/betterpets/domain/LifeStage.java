package kr.qmftm.betterpets.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * 펫 생애주기.
 *
 * <pre>
 *   NORMAL ──과급식──▶ PIG (이스터에그)
 * </pre>
 *
 * <p><b>아기·성체 구분은 없다.</b> 탑승은 소환하는 순간부터 전부 붙는다
 * ({@link RideMode} 참고). 성장도는 "다 자랐다"는 상태 전이가 아니라 <b>다음 종류로
 * 진화하기까지 걸리는 시간</b>일 뿐이다 — {@code next-stage} 가 있는 펫만 성장도가
 * 오르고, 다 차면 {@link kr.qmftm.betterpets.service.GrowthService} 가 다음 종류로
 * 바꾼다. {@code next-stage} 가 없는 펫은 성장도 자체가 오르지 않고, 그 펫에게는
 * 그걸로 끝이다.
 *
 * <p>알은 생애주기 상태가 아니다 — 알 아이템은 그 안에 담긴 펫을 곧바로 꺼내주는
 * 아이템이고, 보관함에 "알" 상태로 남지 않는다.
 */
public enum LifeStage {

    /** 평범한 상태. 진화 여부와 무관하게, 과급식으로 돼지가 되기 전까지는 전부 이 상태다. */
    NORMAL,

    /** 과급식 기믹으로 변한 상태. */
    PIG;

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
