package kr.qmftm.betterpets.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetDataTest {

    private static final long T0 = 1_700_000_000_000L;

    private static PetData sample() {
        final PetData data = PetData.newEgg(UUID.randomUUID(), "wolf", T0);
        data.clearDirty();
        return data;
    }

    @Test
    @DisplayName("새 펫은 알 상태에서 시작한다")
    void newPetStartsAsEgg() {
        final PetData data = PetData.newEgg(UUID.randomUUID(), "wolf", T0);
        assertEquals(LifeStage.EGG, data.stage());
        assertEquals(0, data.growth());
        assertFalse(data.active());
        assertFalse(data.canFly());
    }

    @Test
    @DisplayName("값이 실제로 바뀔 때만 dirty 가 선다")
    void dirtyOnlyOnRealChange() {
        final PetData data = sample();

        data.stage(LifeStage.EGG);      // 같은 값
        assertFalse(data.isDirty(), "같은 값을 넣었는데 저장을 유발하면 안 된다");

        data.stage(LifeStage.BABY);
        assertTrue(data.isDirty());
    }

    @Test
    @DisplayName("성장도와 기준 시각은 항상 함께 갱신된다")
    void growthAndTimestampMoveTogether() {
        final PetData data = sample();
        final long later = T0 + 5 * GrowthCurve.MILLIS_PER_POINT;

        data.applyGrowth(GrowthCurve.project(data.growth(), data.updatedAt(), later, 100));

        assertEquals(5, data.growth());
        assertEquals(later, data.updatedAt());
        assertTrue(data.isDirty());
    }

    @Test
    @DisplayName("변화가 없으면 applyGrowth 는 dirty 를 세우지 않는다")
    void applyGrowthNoOpStaysClean() {
        final PetData data = sample();
        // 1분이 안 지났으므로 성장도도 기준 시각도 그대로다.
        data.applyGrowth(GrowthCurve.project(data.growth(), data.updatedAt(), T0 + 1_000L, 100));
        assertFalse(data.isDirty(), "불필요한 저장을 유발하면 안 된다");
    }

    @Test
    @DisplayName("먹이는 상한을 넘지 않는다")
    void feedRespectsMax() {
        final PetData data = sample();
        data.addGrowth(150, 100);
        assertEquals(100, data.growth());
    }

    @Test
    @DisplayName("별명이 없으면 폴백 이름을 쓴다")
    void displayNameFallsBack() {
        final PetData data = sample();
        assertEquals("늑대", data.displayNameOr("늑대"));

        data.nickname("바둑이");
        assertEquals("바둑이", data.displayNameOr("늑대"));

        data.nickname("   ");
        assertEquals("늑대", data.displayNameOr("늑대"), "공백뿐인 별명은 없는 것으로 본다");
    }

    @Test
    @DisplayName("생애주기별 소환·탑승 가능 여부")
    void lifeStageGating() {
        final PetData data = sample();

        assertFalse(data.stage().summonable(), "알은 소환할 수 없다");

        data.stage(LifeStage.BABY);
        assertTrue(data.stage().summonable());
        assertFalse(data.stage().rideable(), "아기는 탈 수 없다");

        data.stage(LifeStage.ADULT);
        assertTrue(data.stage().rideable());
        assertTrue(data.stage().abilitiesActive());
    }
}
