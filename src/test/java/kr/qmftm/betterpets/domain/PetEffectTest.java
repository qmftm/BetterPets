package kr.qmftm.betterpets.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * config.yml 의 {@code effects.<이벤트>} 하나를 나타내는 값. Bukkit 의 {@code Sound}·
 * {@code Particle} 조회는 {@code EffectService} 가 하므로, 여기서는 서버 없이 검증할 수
 * 있는 것만 본다 — 값이 그대로 들어오는지, 음수가 접히는지, "켜져 있는가" 판정이다.
 */
class PetEffectTest {

    @Test
    @DisplayName("아무것도 안 적으면(none) 사운드도 파티클도 켜지지 않는다")
    void noneHasNothing() {
        final PetEffect effect = PetEffect.none();

        assertFalse(effect.hasSound());
        assertFalse(effect.hasParticle());
    }

    @Test
    @DisplayName("사운드 이름이 없거나 빈 문자열이면 꺼진 것으로 본다")
    void blankSoundIsOff() {
        assertFalse(new PetEffect(null, 1.0, 1.0, null, 0).hasSound());
        assertFalse(new PetEffect("", 1.0, 1.0, null, 0).hasSound());
        assertFalse(new PetEffect("   ", 1.0, 1.0, null, 0).hasSound());
        assertTrue(new PetEffect("ENTITY_GENERIC_EAT", 1.0, 1.0, null, 0).hasSound());
    }

    @Test
    @DisplayName("파티클은 이름과 개수가 둘 다 있어야 켜진 것으로 본다")
    void particleNeedsNameAndCount() {
        assertFalse(new PetEffect(null, 1.0, 1.0, null, 8).hasParticle(),
            "이름이 없으면 개수가 있어도 꺼진 것이다");
        assertFalse(new PetEffect(null, 1.0, 1.0, "TOTEM_OF_UNDYING", 0).hasParticle(),
            "개수가 0이면 이름이 있어도 꺼진 것이다 — 아무것도 안 뿜는다");
        assertTrue(new PetEffect(null, 1.0, 1.0, "TOTEM_OF_UNDYING", 8).hasParticle());
    }

    @Test
    @DisplayName("음수 volume·pitch·particle-count 는 0으로 접힌다 — 실수로 적은 값이 조용히 무시되지 않는다")
    void negativeValuesFloorToZero() {
        final PetEffect effect = new PetEffect("ENTITY_GENERIC_EAT", -1.0, -2.0, "TOTEM_OF_UNDYING", -5);

        assertEquals(0.0, effect.volume());
        assertEquals(0.0, effect.pitch());
        assertEquals(0, effect.particleCount());
        assertFalse(effect.hasParticle(), "접힌 뒤 0이 됐으니 꺼진 것으로 봐야 한다");
    }
}
