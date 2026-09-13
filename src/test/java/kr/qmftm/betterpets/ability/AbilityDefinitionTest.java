package kr.qmftm.betterpets.ability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbilityDefinitionTest {

    @Test
    @DisplayName("없는 키는 기본값으로 떨어진다 — 능력마다 아는 키가 다르다")
    void missingKeyFallsBack() {
        final AbilityDefinition speed = new AbilityDefinition("speed", Map.of("base", 0.1));

        assertEquals(0.1, speed.value("base", 999.0));
        assertEquals(999.0, speed.value("per-growth", 999.0));
    }

    @Test
    @DisplayName("base 에 등급 배율이 곱해진다 — 성장도는 더 이상 관여하지 않는다")
    void scalesWithRarityOnly() {
        final AbilityDefinition ability = new AbilityDefinition("speed", Map.of("base", 1.0));

        assertEquals(1.0, ability.scaled(1.0), 1.0e-9, "배율 1.0 이면 base 그대로");
        assertEquals(2.0, ability.scaled(2.0), 1.0e-9, "등급 배율이 곱해진다");
    }

    @Test
    @DisplayName("수치를 하나도 적지 않으면 0이다 — 조용히 세지 않는다")
    void emptyDefinitionScalesToZero() {
        assertEquals(0.0, new AbilityDefinition("x", Map.of()).scaled(5.0));
    }

    @Test
    @DisplayName("수치 맵은 밖에서 고칠 수 없다")
    void valuesAreImmutable() {
        final AbilityDefinition ability = new AbilityDefinition("x", Map.of("base", 1.0));
        assertThrows(UnsupportedOperationException.class, () -> ability.values().put("base", 99.0));
    }
}
