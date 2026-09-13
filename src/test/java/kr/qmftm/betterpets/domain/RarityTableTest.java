package kr.qmftm.betterpets.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RarityTableTest {

    private static RarityStats stats(final double moveSpeed) {
        return new RarityStats("바꾼 이름", moveSpeed, 0.5);
    }

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
        final RarityTable table = RarityTable.of(Map.of(Rarity.S, stats(9.0)));

        assertEquals(9.0, table.stats(Rarity.S).moveSpeedMultiplier());
        assertEquals(Rarity.D.defaults(), table.stats(Rarity.D));
        assertEquals(Rarity.A.defaults(), table.stats(Rarity.A));
    }

    @Test
    @DisplayName("모든 등급에 값이 있다 — of() 는 절대 null 이 아니다")
    void everyRarityIsPresent() {
        final RarityTable table = RarityTable.of(Map.of(Rarity.B, stats(2.0)));
        for (final Rarity rarity : Rarity.values()) {
            assertNotNull(table.stats(rarity), rarity + " 의 수치가 없다");
        }
    }

    @Test
    @DisplayName("설정 실수로 펫이 멈추거나 뒤로 걷지 않게 막는다")
    void clampsBrokenNumbers() {
        final RarityStats broken = new RarityStats("x", -5.0, 0.0);

        assertTrue(broken.moveSpeedMultiplier() > 0.0, "0 이면 아예 못 움직인다");
        assertTrue(broken.rideSpeed() > 0.0);
    }
}
