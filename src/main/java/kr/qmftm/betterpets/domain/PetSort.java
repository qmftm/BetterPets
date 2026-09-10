package kr.qmftm.betterpets.domain;

import java.util.Comparator;
import java.util.function.ToIntFunction;

/**
 * 보관함 정렬 방식.
 *
 * <p>20마리쯤 되면 기본 순서 하나로는 부족하다. 찾는 이유가 그때그때 다르기 때문이다 —
 * "방금 깐 알이 뭐였지"와 "다 커가는 게 뭐지"는 다른 순서를 원한다.
 *
 * <p><b>정렬 기준을 여기 모아 두는 이유는 검증 때문이다.</b> {@link PetData} 만 있으면
 * 판정할 수 있어서 Bukkit 도 설정도 없이 돌려볼 수 있다. GUI 쪽에 두면 그게 안 된다.
 */
public enum PetSort {

    /** 소환 중 → 등급 높은 순 → 오래 데리고 있던 순. 찾을 일이 가장 잦은 둘을 앞에 둔다. */
    DEFAULT,

    /** 최근에 얻은 순. 방금 깐 알이 무엇이었는지 확인할 때. */
    NEWEST,

    /** 성장도 높은 순. 다 커가는 펫을 골라 먹이러 갈 때. */
    GROWTH;

    /** 버튼을 누를 때마다 다음 방식으로. 마지막에서 처음으로 돌아온다. */
    public PetSort next() {
        final PetSort[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** 화면 문구 키. {@code gui.sort-default} 처럼 쓴다. */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * @param rarityRank 펫 → 등급 서열(높을수록 좋은 등급). 종류를 못 찾으면 음수를 주면
     *                   맨 뒤로 간다 — 설정이 깨진 펫을 앞에 둘 이유가 없다
     */
    public Comparator<PetData> comparator(final ToIntFunction<PetData> rarityRank) {
        final Comparator<PetData> primary = switch (this) {
            // 부정으로 뒤집는다. comparing(..., reverseOrder()) 는 boolean 을 돌려주는
            // 메서드 참조에서 타입 추론이 되지 않는다.
            case DEFAULT -> Comparator
                .comparing((PetData pet) -> !pet.active())          // 소환 중이 먼저
                .thenComparing(pet -> -rarityRank.applyAsInt(pet))  // 등급 높은 순
                .thenComparingLong(PetData::acquiredAt);            // 오래 데리고 있던 순
            case NEWEST -> Comparator
                .comparingLong((PetData pet) -> -pet.acquiredAt());
            case GROWTH -> Comparator
                .comparingInt((PetData pet) -> -pet.growthStage())  // 단계가 먼저다
                .thenComparingInt(pet -> -pet.growth());
        };
        // 같은 값을 가진 두 마리가 새로고침마다 자리를 바꾸지 않게 못을 박는다.
        return primary.thenComparing(pet -> pet.petId().toString());
    }
}
