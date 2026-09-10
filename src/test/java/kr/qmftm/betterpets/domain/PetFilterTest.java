package kr.qmftm.betterpets.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetFilterTest {

    private static PetData pet(final LifeStage stage, final boolean active) {
        final PetData data = PetData.newBaby(UUID.randomUUID(), "wolf", 0L);
        data.stage(stage);
        data.active(active);
        return data;
    }

    @Test
    @DisplayName("전체는 무엇이든 통과시킨다")
    void allPassesEverything() {
        for (final LifeStage stage : LifeStage.values()) {
            assertTrue(PetFilter.ALL.test(pet(stage, false)));
        }
    }

    @Test
    @DisplayName("소환 중 필터는 소환 상태만 본다 — 생애주기와 무관하다")
    void activeLooksOnlyAtActiveFlag() {
        assertTrue(PetFilter.ACTIVE.test(pet(LifeStage.BABY, true)));
        assertFalse(PetFilter.ACTIVE.test(pet(LifeStage.ADULT, false)));
    }

    @Test
    @DisplayName("아기 필터는 아직 자라는 펫만 — 먹이를 줄 대상이다")
    void babyIsOnlyBaby() {
        assertTrue(PetFilter.BABY.test(pet(LifeStage.BABY, false)));
        assertFalse(PetFilter.BABY.test(pet(LifeStage.ADULT, false)));
        assertFalse(PetFilter.BABY.test(pet(LifeStage.PIG, false)));
    }

    @Test
    @DisplayName("돼지는 성체 쪽에 둔다 — 더 안 자라고 탈 수 있다는 점이 같다")
    void pigCountsAsGrown() {
        assertTrue(PetFilter.ADULT.test(pet(LifeStage.PIG, false)));
        assertTrue(PetFilter.ADULT.test(pet(LifeStage.ADULT, false)));
        assertFalse(PetFilter.ADULT.test(pet(LifeStage.BABY, false)));
    }

    @Test
    @DisplayName("아기와 성체는 서로를 빠짐없이 덮는다 — 어느 필터로도 안 보이는 펫이 없어야 한다")
    void babyAndAdultCoverEveryStage() {
        for (final LifeStage stage : LifeStage.values()) {
            final PetData data = pet(stage, false);
            assertTrue(PetFilter.BABY.test(data) || PetFilter.ADULT.test(data),
                stage + " 가 어느 쪽에도 안 걸린다");
        }
    }

    @Test
    @DisplayName("필터는 한 바퀴 돌아 처음으로 온다")
    void filterCyclesBack() {
        PetFilter filter = PetFilter.ALL;
        for (int i = 0; i < PetFilter.values().length; i++) {
            filter = filter.next();
        }
        assertEquals(PetFilter.ALL, filter);
    }
}
