package kr.qmftm.betterpets.domain;

import java.util.function.Predicate;

/**
 * 보관함에서 어떤 펫만 볼지.
 *
 * <p><b>아기·성체 필터는 없다.</b> 능력도 탑승도 생애주기와 무관하게 처음부터 되므로,
 * "다 자란 것만 보기"가 더 이상 실용적인 구분이 아니다. 남은 건 소환 여부뿐이다.
 */
public enum PetFilter implements Predicate<PetData> {

    /** 전부. */
    ALL,

    /** 지금 소환해 둔 것만. */
    ACTIVE;

    public PetFilter next() {
        final PetFilter[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** 화면 문구 키. {@code gui.filter-all} 처럼 쓴다. */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public boolean test(final PetData pet) {
        return switch (this) {
            case ALL -> true;
            case ACTIVE -> pet.active();
        };
    }
}
