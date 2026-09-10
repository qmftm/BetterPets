package kr.qmftm.betterpets.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RarityTest {

    @Test
    @DisplayName("등급이 오를수록 이동속도가 빨라진다 — 원작 규칙")
    void moveSpeedIncreasesWithRarity() {
        final Rarity[] ascending = {Rarity.D, Rarity.C, Rarity.B, Rarity.A, Rarity.S};
        for (int i = 1; i < ascending.length; i++) {
            assertTrue(
                ascending[i].moveSpeedMultiplier() > ascending[i - 1].moveSpeedMultiplier(),
                ascending[i] + " 은 " + ascending[i - 1] + " 보다 빨라야 한다"
            );
        }
    }

    @Test
    @DisplayName("등급이 오를수록 탑승속도도 빨라진다")
    void rideSpeedIncreasesWithRarity() {
        final Rarity[] ascending = {Rarity.D, Rarity.C, Rarity.B, Rarity.A, Rarity.S};
        for (int i = 1; i < ascending.length; i++) {
            assertTrue(ascending[i].rideSpeed() > ascending[i - 1].rideSpeed());
        }
    }

    @Test
    @DisplayName("비행은 A등급부터만 나온다 — 원작 규칙")
    void flightOnlyFromRarityA() {
        assertFalse(Rarity.D.canRollFlight());
        assertFalse(Rarity.C.canRollFlight());
        assertFalse(Rarity.B.canRollFlight());
        assertTrue(Rarity.A.canRollFlight());
        assertTrue(Rarity.S.canRollFlight());
    }

    @Test
    @DisplayName("비행 확률은 0.0~1.0 범위다")
    void flyChanceIsProbability() {
        for (final Rarity rarity : Rarity.values()) {
            assertTrue(rarity.flyChance() >= 0.0 && rarity.flyChance() <= 1.0,
                rarity + " 의 비행 확률이 범위를 벗어난다");
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
    @DisplayName("생애주기: 아기는 탑승 불가, 성체만 능력 발현")
    void lifeStageCapabilities() {
        assertFalse(LifeStage.BABY.rideable(), "아기는 탈 수 없다");
        assertTrue(LifeStage.ADULT.rideable());
        assertTrue(LifeStage.PIG.rideable(), "돼지도 탈 수는 있다");

        assertTrue(LifeStage.ADULT.abilitiesActive());
        assertFalse(LifeStage.BABY.abilitiesActive());
        assertFalse(LifeStage.PIG.abilitiesActive(), "돼지는 기믹이라 능력이 없다");
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
