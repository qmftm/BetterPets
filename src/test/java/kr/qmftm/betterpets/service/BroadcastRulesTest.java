package kr.qmftm.betterpets.service;

import kr.qmftm.betterpets.domain.Rarity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BroadcastRulesTest {

    private static BroadcastService.Rules rules(final boolean enabled,
                                                final Rarity floor,
                                                final int minStage) {
        return new BroadcastService.Rules(enabled, floor, minStage, true, true, true, true);
    }

    @Test
    @DisplayName("문턱 등급 미만은 알리지 않는다 — 채팅이 밀리지 않게 하는 게 목적이다")
    void rarityFloorHolds() {
        final BroadcastService.Rules onlyHeroes = rules(true, Rarity.A, 1);

        assertTrue(onlyHeroes.qualifies(Rarity.S, 1));
        assertTrue(onlyHeroes.qualifies(Rarity.A, 1), "문턱과 같은 등급은 통과한다");
        assertFalse(onlyHeroes.qualifies(Rarity.B, 1));
        assertFalse(onlyHeroes.qualifies(Rarity.D, 1));
    }

    @Test
    @DisplayName("성장 단계 문턱도 함께 걸린다 — 둘 다 넘겨야 한다")
    void bothConditionsMustPass() {
        final BroadcastService.Rules strict = rules(true, Rarity.A, 3);

        assertTrue(strict.qualifies(Rarity.S, 3));
        assertFalse(strict.qualifies(Rarity.S, 2), "등급은 되지만 단계가 모자라다");
        assertFalse(strict.qualifies(Rarity.C, 5), "단계는 되지만 등급이 모자라다");
    }

    @Test
    @DisplayName("꺼져 있으면 무엇도 통과하지 않는다")
    void disabledBlocksEverything() {
        assertFalse(rules(false, Rarity.D, 1).qualifies(Rarity.S, 99));
    }

    @Test
    @DisplayName("성장 단계 문턱은 1 미만으로 내려가지 않는다")
    void stageFloorsAtOne() {
        // 단계는 1부터 시작한다. 0이나 음수를 넣어도 "조건 없음"과 같아야 한다.
        assertEquals(1, rules(true, Rarity.D, 0).minGrowthStage());
        assertEquals(1, rules(true, Rarity.D, -5).minGrowthStage());
        assertTrue(rules(true, Rarity.D, 0).qualifies(Rarity.D, 1));
    }

    @Test
    @DisplayName("문턱 등급을 비우면 모든 등급이 통과한다")
    void nullFloorAcceptsEverything() {
        final BroadcastService.Rules open = rules(true, null, 1);
        for (final Rarity rarity : Rarity.values()) {
            assertTrue(open.qualifies(rarity, 1), rarity + " 이 막혔다");
        }
    }

    @Test
    @DisplayName("등급을 모르는 펫은 알리지 않는다")
    void unknownRarityIsNotAnnounced() {
        // 설정이 깨져 등급을 못 읽은 경우다. 조용히 넘어가는 편이 낫다.
        assertFalse(rules(true, Rarity.D, 1).qualifies(null, 1));
    }
}
