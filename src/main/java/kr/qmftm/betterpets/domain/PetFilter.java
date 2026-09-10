package kr.qmftm.betterpets.domain;

import java.util.function.Predicate;

/**
 * 보관함에서 어떤 펫만 볼지.
 *
 * <p>정렬만으로는 부족한 순간이 있다. "먹이 줄 아기만" 보고 싶을 때 성체 열댓 마리를
 * 넘겨가며 고르게 하면, 쪽 넘김 버튼이 곧 이 기능의 대체품이 된다.
 */
public enum PetFilter implements Predicate<PetData> {

    /** 전부. */
    ALL,

    /** 지금 소환해 둔 것만. */
    ACTIVE,

    /** 아직 자라는 중. 먹이를 줄 대상이다. */
    BABY,

    /** 다 자란 것. 탈 수 있고 능력이 붙어 있다. */
    ADULT;

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
            case BABY -> pet.stage() == LifeStage.BABY;
            // 돼지는 성체 쪽에 둔다. 더 자라지 않고 탈 수 있다는 점이 같고,
            // 이스터에그 하나를 위해 버튼을 한 칸 더 늘릴 이유가 없다.
            case ADULT -> pet.stage() != LifeStage.BABY;
        };
    }
}
