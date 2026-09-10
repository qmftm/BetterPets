package kr.qmftm.betterpets.domain;

import java.util.Comparator;
import java.util.function.ToIntFunction;

/**
 * 보관함에 펫을 늘어놓는 순서.
 *
 * <p><b>소환 중 → 등급 높은 순 → 획득 순.</b> 예전엔 획득 순 하나뿐이었는데, 20마리쯤
 * 되면 지금 데리고 있는 펫과 제일 좋은 펫이 몇 쪽 뒤로 밀린다. 찾을 일이 가장 잦은
 * 둘을 앞으로 올린다.
 *
 * <p>등급을 직접 보지 않고 함수로 받는 이유는 {@link PetData} 가 등급을 모르기 때문이다 —
 * 개체는 종류 id 만 들고 있고, 등급은 {@code PetType} 에 있다. 그 조회를 밖에 맡기면
 * 이 정렬은 Bukkit 도 설정도 없이 검증할 수 있다.
 */
public final class PetOrder {

    private PetOrder() {
        throw new AssertionError("유틸리티 클래스");
    }

    /**
     * @param rarityRank 펫 → 등급 서열(높을수록 좋은 등급). 종류를 못 찾으면 음수를 주면
     *                   맨 뒤로 간다 — 설정이 깨진 펫을 앞에 둘 이유가 없다
     */
    public static Comparator<PetData> forBox(final ToIntFunction<PetData> rarityRank) {
        // 부정으로 뒤집는다. comparing(..., reverseOrder()) 는 boolean 을 돌려주는
        // 메서드 참조에서 타입 추론이 되지 않는다.
        return Comparator
            .comparing((PetData pet) -> !pet.active())              // 소환 중이 먼저
            .thenComparing(pet -> -rarityRank.applyAsInt(pet))      // 등급 높은 순
            .thenComparingLong(PetData::acquiredAt)                 // 오래 데리고 있던 순
            // 같은 순간에 받은 두 마리가 새로고침마다 자리를 바꾸지 않게 못을 박는다.
            .thenComparing(pet -> pet.petId().toString());
    }
}
