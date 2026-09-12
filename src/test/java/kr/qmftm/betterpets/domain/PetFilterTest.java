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
    @DisplayName("필터는 한 바퀴 돌아 처음으로 온다")
    void filterCyclesBack() {
        PetFilter filter = PetFilter.ALL;
        for (int i = 0; i < PetFilter.values().length; i++) {
            filter = filter.next();
        }
        assertEquals(PetFilter.ALL, filter);
    }
}
