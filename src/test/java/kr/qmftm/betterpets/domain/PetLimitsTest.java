package kr.qmftm.betterpets.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetLimitsTest {

    @Test
    @DisplayName("보유 한도는 한도 직전까지만 더 받는다")
    void ownedStopsAtLimit() {
        final PetLimits limits = new PetLimits(3, 1);

        assertTrue(limits.canOwnMore(0));
        assertTrue(limits.canOwnMore(2), "2마리면 한 마리 더 받을 수 있다");
        assertFalse(limits.canOwnMore(3), "한도에 닿으면 더 못 받는다");
        assertFalse(limits.canOwnMore(4), "어쩌다 넘겼어도 더 받으면 안 된다");
    }

    @Test
    @DisplayName("동시 소환도 같은 규칙을 따른다")
    void activeStopsAtLimit() {
        final PetLimits limits = new PetLimits(20, 2);

        assertTrue(limits.canSummonMore(1));
        assertFalse(limits.canSummonMore(2));
    }

    @Test
    @DisplayName("0 은 무제한이다")
    void zeroMeansUnlimited() {
        final PetLimits limits = new PetLimits(0, 0);

        assertTrue(limits.ownedUnlimited());
        assertTrue(limits.activeUnlimited());
        assertTrue(limits.canOwnMore(9_999));
        assertTrue(limits.canSummonMore(9_999));
    }

    @Test
    @DisplayName("음수는 0(무제한)으로 접는다 — 오타와 의도를 구분할 수 없다")
    void negativeFoldsToUnlimited() {
        final PetLimits limits = new PetLimits(-5, -1);

        assertEquals(0, limits.maxOwned());
        assertEquals(0, limits.maxActive());
        assertTrue(limits.canOwnMore(100));
    }

    @Test
    @DisplayName("표시 문자열은 무제한을 ∞ 로 쓴다")
    void labelsShowInfinity() {
        assertEquals("3/20", new PetLimits(20, 1).ownedLabel(3));
        assertEquals("1/1", new PetLimits(20, 1).activeLabel(1));
        assertEquals("3/∞", new PetLimits(0, 0).ownedLabel(3));
        assertEquals("3/∞", new PetLimits(0, 0).activeLabel(3));
    }

    @Test
    @DisplayName("한도만 따로 적을 때도 같은 ∞ 를 쓴다 — 화면마다 0 과 ∞ 가 섞이면 안 된다")
    void maxLabelsMatchTheCombinedOnes() {
        final PetLimits limited = new PetLimits(20, 2);
        final PetLimits unlimited = new PetLimits(0, 0);

        assertEquals("20", limited.maxOwnedLabel());
        assertEquals("2", limited.maxActiveLabel());
        assertEquals("∞", unlimited.maxOwnedLabel());
        assertEquals("∞", unlimited.maxActiveLabel());

        assertTrue(limited.ownedLabel(3).endsWith("/" + limited.maxOwnedLabel()));
        assertTrue(unlimited.activeLabel(3).endsWith("/" + unlimited.maxActiveLabel()));
    }

    @Test
    @DisplayName("기본값은 20마리 보유 · 1마리 소환")
    void defaultsMatchDocumentedConfig() {
        assertEquals(20, PetLimits.defaults().maxOwned());
        assertEquals(1, PetLimits.defaults().maxActive());
    }
}
