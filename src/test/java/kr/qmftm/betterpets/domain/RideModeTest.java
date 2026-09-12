package kr.qmftm.betterpets.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RideModeTest {

    @Test
    @DisplayName("NONE 만 탑승 불가다")
    void onlyNoneBlocksRiding() {
        assertFalse(RideMode.NONE.canRide());
        assertTrue(RideMode.GROUND.canRide());
        assertTrue(RideMode.FLY.canRide());
    }

    @Test
    @DisplayName("FLY 종류라도 비행 추첨에 실패하면 걷는 탑승으로 내려간다")
    void flyFallsBackToGroundWithoutFlight() {
        assertEquals(RideMode.FLY, RideMode.FLY.effective(true));
        assertEquals(RideMode.GROUND, RideMode.FLY.effective(false),
            "추첨에 실패했다고 아예 못 타게 되면 안 된다");
    }

    @Test
    @DisplayName("GROUND 와 NONE 은 비행 여부와 무관하다")
    void groundAndNoneIgnoreFlightRoll() {
        assertEquals(RideMode.GROUND, RideMode.GROUND.effective(true),
            "걷는 탑승 종류가 비행 플래그만으로 날아서는 안 된다");
        assertEquals(RideMode.GROUND, RideMode.GROUND.effective(false));

        assertEquals(RideMode.NONE, RideMode.NONE.effective(true));
        assertEquals(RideMode.NONE, RideMode.NONE.effective(false));
    }
}
