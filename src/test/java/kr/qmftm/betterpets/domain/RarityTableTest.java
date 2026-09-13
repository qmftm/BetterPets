package kr.qmftm.betterpets.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RarityTableTest {

    @Test
    @DisplayName("설정이 없으면 전부 내장 기본값")
    void defaultsFillEverything() {
        final RarityTable table = RarityTable.defaults();
        for (final Rarity rarity : Rarity.values()) {
            assertEquals(rarity.defaults(), table.stats(rarity));
        }
    }

    @Test
    @DisplayName("적지 않은 등급은 내장 기본값으로 채운다")
    void missingRaritiesFallBack() {
        // S만 손보는 게 흔한 경우다. 나머지 넷을 전부 적게 만들면 안 된다.
        final RarityTable table = RarityTable.of(Map.of(Rarity.S, new RarityStats("바꾼 이름")));

        assertEquals("바꾼 이름", table.stats(Rarity.S).displayName());
        assertEquals(Rarity.D.defaults(), table.stats(Rarity.D));
        assertEquals(Rarity.A.defaults(), table.stats(Rarity.A));
    }

    @Test
    @DisplayName("모든 등급에 값이 있다 — of() 는 절대 null 이 아니다")
    void everyRarityIsPresent() {
        final RarityTable table = RarityTable.of(Map.of(Rarity.B, new RarityStats("바꾼 이름")));
        for (final Rarity rarity : Rarity.values()) {
            assertNotNull(table.stats(rarity), rarity + " 의 수치가 없다");
        }
    }
}
