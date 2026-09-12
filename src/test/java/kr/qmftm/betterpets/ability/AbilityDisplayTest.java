package kr.qmftm.betterpets.ability;

import kr.qmftm.betterpets.ability.impl.ExtraDropAbility;
import kr.qmftm.betterpets.domain.LifeStage;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetType;
import kr.qmftm.betterpets.domain.Rarity;
import kr.qmftm.betterpets.domain.RideMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 보관함이 보여주는 수치가 능력이 실제로 쓰는 수치와 같은가.
 *
 * <p>보관함이 {@code base}/{@code per-growth} 를 직접 계산하고 있었다. 다른 키를 읽는
 * 능력은 그 키가 없어 언제나 {@code +0.00} 으로 보였다 — 화면이 조용히 거짓말을 했다.
 */
class AbilityDisplayTest {

    private static PetData pet(final int growth) {
        return new PetData(UUID.randomUUID(), UUID.randomUUID(), "wolf", null,
            LifeStage.ADULT, growth, 1, false, false, 0L, 0L);
    }

    private static PetType type(final Rarity rarity) {
        return new PetType("wolf", "<white>늑대", "pet_wolf",
            rarity, rarity.defaults(), 100, RideMode.GROUND, 0.0, rarity.defaults().rideSpeed(),
            "LEAD", PetType.AnimationSet.defaults(), PetType.MovementProfile.defaults(),
            List.of(), Map.of(), 0);
    }

    @Test
    @DisplayName("추가 드랍은 자기 키(chance-*)를 읽는다 — 0 으로 보이면 안 된다")
    void extraDropReadsItsOwnKeys() {
        final AbilityDefinition definition = new AbilityDefinition("on_kill_extra_drop",
            Map.of("chance-base", 0.05, "chance-per-growth", 0.001));
        final ExtraDropAbility ability = new ExtraDropAbility();

        assertEquals(0.05, ability.displayValue(pet(0), type(Rarity.B), definition), 1.0e-9);
        assertEquals(0.15, ability.displayValue(pet(100), type(Rarity.B), definition), 1.0e-9);
    }

    @Test
    @DisplayName("추가 드랍에는 등급 배율이 곱해지지 않는다 — 표시도 그래야 한다")
    void extraDropIgnoresRarityMultiplier() {
        final AbilityDefinition definition =
            new AbilityDefinition("on_kill_extra_drop", Map.of("chance-base", 0.2));
        final ExtraDropAbility ability = new ExtraDropAbility();

        assertEquals(ability.displayValue(pet(50), type(Rarity.D), definition),
            ability.displayValue(pet(50), type(Rarity.S), definition), 1.0e-9);
    }

    @Test
    @DisplayName("확률은 1을 넘지 않는다 — 표시와 실제가 같은 상한을 쓴다")
    void chanceIsCapped() {
        final AbilityDefinition definition = new AbilityDefinition("on_kill_extra_drop",
            Map.of("chance-base", 0.9, "chance-per-growth", 0.01));

        assertEquals(1.0, new ExtraDropAbility().displayValue(pet(100), type(Rarity.B), definition));
    }

    @Test
    @DisplayName("확률형 능력은 백분율로 적으라고 스스로 알린다")
    void chanceAbilitiesAreMarked() {
        assertTrue(new ExtraDropAbility().displayAsChance());
    }

    @Test
    @DisplayName("기본 구현은 능력치 계열 공식 그대로 — 붙는 값과 보이는 값이 한 줄에서 나온다")
    void defaultDisplayMatchesScaledValue() {
        final AbilityDefinition definition =
            new AbilityDefinition("attribute_speed", Map.of("base", 1.0, "per-growth", 0.01));
        final PetAbility plain = new PetAbility() {
            @Override public String id() { return "attribute_speed"; }
            @Override public Type type() { return Type.PASSIVE; }
        };
        final PetData grown = pet(100);
        final PetType wolf = type(Rarity.B);

        assertEquals(AbilityContext.scaledValue(grown, wolf, definition),
            plain.displayValue(grown, wolf, definition), 1.0e-9);
        assertFalse(plain.displayAsChance());
    }

    @Test
    @DisplayName("능력치 계열은 등급 배율을 받는다 — 확률형과 갈리는 지점이다")
    void attributeAbilitiesTakeRarityMultiplier() {
        final AbilityDefinition definition =
            new AbilityDefinition("attribute_speed", Map.of("base", 1.0));

        assertNotEquals(AbilityContext.scaledValue(pet(0), type(Rarity.D), definition),
            AbilityContext.scaledValue(pet(0), type(Rarity.S), definition),
            "등급표가 등급마다 다른 배율을 준다는 전제가 깨졌다");
    }
}
