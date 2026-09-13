package kr.qmftm.betterpets.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetDataTest {

    private static final long T0 = 1_700_000_000_000L;

    private static PetData sample() {
        final PetData data = PetData.newBaby(UUID.randomUUID(), "wolf", T0);
        data.clearDirty();
        return data;
    }

    @Test
    @DisplayName("새 펫은 평범한 상태, 성장 단계 1에서 시작한다")
    void newPetStartsAsBaby() {
        final PetData data = PetData.newBaby(UUID.randomUUID(), "wolf", T0);
        assertEquals(LifeStage.NORMAL, data.stage());
        assertEquals(0, data.growth());
        assertEquals(1, data.growthStage());
        assertFalse(data.active());
    }

    @Test
    @DisplayName("성장 단계는 1 미만으로 떨어지지 않는다")
    void growthStageFloorsAtOne() {
        final PetData data = sample();
        data.growthStage(0);
        assertEquals(1, data.growthStage());

        data.growthStage(-5);
        assertEquals(1, data.growthStage());
    }

    @Test
    @DisplayName("성장 단계가 실제로 바뀔 때만 dirty 가 선다")
    void growthStageDirtyOnlyOnChange() {
        final PetData data = sample();

        data.growthStage(1);   // 이미 1
        assertFalse(data.isDirty());

        data.growthStage(2);
        assertTrue(data.isDirty());
    }

    @Test
    @DisplayName("값이 실제로 바뀔 때만 dirty 가 선다")
    void dirtyOnlyOnRealChange() {
        final PetData data = sample();

        data.stage(LifeStage.NORMAL);     // 같은 값
        assertFalse(data.isDirty(), "같은 값을 넣었는데 저장을 유발하면 안 된다");

        data.stage(LifeStage.PIG);
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
    @DisplayName("포만도는 쌓이고, 음수로는 안 내려간다")
    void fullnessAccumulatesAndFloorsAtZero() {
        final PetData data = sample();
        assertEquals(0, data.fullness());

        data.addFullness(10);
        data.addFullness(5);
        assertEquals(15, data.fullness());

        data.addFullness(-100);
        assertEquals(0, data.fullness());
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
    @DisplayName("별명의 MiniMessage 태그는 저장 전에 걷어낸다 — 서식은 플레이어 몫이 아니다")
    void nicknameStripsTags() {
        final PetData data = sample();

        data.nickname("<rainbow>왕</rainbow>");
        assertEquals("왕", data.nickname());

        data.nickname("<red>붉은 <bold>늑대");
        assertEquals("붉은 늑대", data.nickname(), "여는 태그가 여럿이어도 전부 걷어낸다");

        data.nickname("<gradient:red:blue>");
        assertNull(data.nickname(), "태그만 남으면 별명이 없는 것으로 본다");
    }

    @Test
    @DisplayName("파일에서 읽어 온 별명도 같은 문을 지난다")
    void nicknameFromStorageIsSanitized() {
        final PetData loaded = new PetData(UUID.randomUUID(), UUID.randomUUID(), "wolf",
            "<red>옛날에 저장된 이름", LifeStage.NORMAL, 0, 1, 0, 0L, false, 0L, 0L);

        assertEquals("옛날에 저장된 이름", loaded.nickname(),
            "이 방어가 생기기 전 파일이나 관리자가 손으로 고친 파일에도 태그가 있을 수 있다");
    }

    @Test
    @DisplayName("같은 별명으로 다시 바꿔도 저장 대상이 되지 않는다")
    void renamingToSameNameIsNotDirty() {
        final PetData data = sample();
        data.nickname("바둑이");
        data.clearDirty();

        data.nickname("바둑이");
        assertFalse(data.isDirty());
    }

    @Test
    @DisplayName("능력은 소환하는 순간부터 붙는다 — 돼지가 되면 잃는다")
    void lifeStageGating() {
        final PetData data = sample();

        assertTrue(data.stage().abilitiesActive());

        data.stage(LifeStage.PIG);
        assertFalse(data.stage().abilitiesActive());
    }
}
