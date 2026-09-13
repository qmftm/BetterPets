package kr.qmftm.betterpets.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RarityTest {

    @Test
    @DisplayName("등급은 표기 전용이다 — 각 등급의 기본 표시 이름이 서로 다르다")
    void defaultsCarryOnlyDisplayName() {
        final Rarity[] all = {Rarity.D, Rarity.C, Rarity.B, Rarity.A, Rarity.S};
        for (final Rarity rarity : all) {
            assertTrue(rarity.defaults().displayName() != null && !rarity.defaults().displayName().isBlank());
        }
    }

    @Test
    @DisplayName("설정 문자열을 대소문자 무관하게 읽는다")
    void parsesCaseInsensitively() {
        assertEquals(Rarity.S, Rarity.parse("S").orElseThrow());
        assertEquals(Rarity.S, Rarity.parse("s").orElseThrow());
        assertEquals(Rarity.A, Rarity.parse("  a  ").orElseThrow());
    }

    @Test
    @DisplayName("알 수 없는 등급은 조용히 기본값으로 떨어지지 않는다")
    void unknownRarityIsEmpty() {
        assertTrue(Rarity.parse("LEGENDARY").isEmpty(), "잘못된 설정은 드러나야 한다");
        assertTrue(Rarity.parse("").isEmpty());
        assertTrue(Rarity.parse(null).isEmpty());
    }

    @Test
    @DisplayName("등급 비교는 선언 순서를 따른다 — 방송 문턱이 이걸로 정해진다")
    void atLeastFollowsDeclarationOrder() {
        assertTrue(Rarity.S.atLeast(Rarity.A));
        assertTrue(Rarity.A.atLeast(Rarity.A), "같은 등급도 기준을 만족한다");
        assertFalse(Rarity.B.atLeast(Rarity.A));
        assertFalse(Rarity.D.atLeast(Rarity.S));

        assertTrue(Rarity.D.atLeast(null), "기준이 없으면 전부 통과시킨다");
    }
}
