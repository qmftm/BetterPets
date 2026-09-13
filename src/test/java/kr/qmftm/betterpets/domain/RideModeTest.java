package kr.qmftm.betterpets.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
