package kr.qmftm.betterpets.config;

import kr.qmftm.betterpets.domain.PetType;
import kr.qmftm.betterpets.domain.Rarity;
import kr.qmftm.betterpets.domain.RideMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code gives} 도 {@code weights} 도 없는 알이 쓰는 표.
 *
 * <p>{@code acquire.gacha-weight} 는 여태 읽어만 두고 아무도 쓰지 않는 값이었다.
 * 그런데 {@code pets/*.yml} 의 주석과 사용 안내는 "랜덤 알에서 뽑힐 가중치"라고 적고
 * 있었으니, <b>0 으로 막아뒀다고 믿은 펫이 실제로는 아무 영향도 못 받고 있었다.</b>
 */
class GachaTableTest {

    private static PetType type(final String id, final int gachaWeight) {
        return new PetType(id, id, "model_" + id, Rarity.B, Rarity.B.defaults(),
            100, RideMode.NONE, 0.0, Rarity.B.defaults().rideSpeed(), "LEAD",
            PetType.AnimationSet.defaults(), PetType.MovementProfile.defaults(),
            List.of(), Map.of(), gachaWeight);
    }

    @Test
    @DisplayName("가중치가 0 이하인 펫은 빠진다 — 그게 '뽑기로는 안 나온다'는 뜻이다")
    void zeroWeightIsExcluded() {
        final var table = PetCatalog.gachaTable(List.of(
            type("wolf", 50), type("dragon", 1), type("pig", 0), type("hatchling", -3)));

        assertEquals(Map.of("wolf", 50, "dragon", 1), table);
        assertFalse(table.containsKey("pig"), "과급식으로만 얻는 펫이 알에서 나오면 안 된다");
        assertFalse(table.containsKey("hatchling"));
    }

    @Test
    @DisplayName("적힌 순서가 그대로 유지된다 — 구간 순서가 곧 재현 가능성이다")
    void orderIsPreserved() {
        final var table = PetCatalog.gachaTable(List.of(
            type("a", 1), type("b", 2), type("c", 3)));

        assertEquals(List.of("a", "b", "c"), List.copyOf(table.keySet()),
            "Weighted.pick 이 순서대로 구간을 나누므로, 순서가 흔들리면 같은 시드에서도"
                + " 다른 펫이 나온다");
    }

    @Test
    @DisplayName("전부 0 이면 빈 표다 — 호출부가 그걸 보고 설정 문제를 알린다")
    void allZeroGivesEmptyTable() {
        assertTrue(PetCatalog.gachaTable(List.of(type("pig", 0), type("hatchling", 0))).isEmpty());
    }
}
