package kr.qmftm.betterpets.domain;

import kr.qmftm.betterpets.ability.AbilityDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetTypeTest {

    private static PetType type(final RideMode ride,
                                final double flyChance,
                                final int growthMax,
                                final Map<String, Integer> nextStage) {
        return new PetType("wolf", "<white>늑대", "pet_wolf",
            Rarity.B, Rarity.B.defaults(), growthMax, ride, flyChance, Rarity.B.defaults().rideSpeed(),
            "LEAD", PetType.AnimationSet.defaults(), PetType.MovementProfile.defaults(),
            List.of(), nextStage, 0);
    }

    private static PetType simple() {
        return type(RideMode.GROUND, 0.0, 100, Map.of());
    }

    @Test
    @DisplayName("비행 추첨은 FLY 종류만 돌린다")
    void onlyFlyTypesRollForFlight() {
        assertTrue(type(RideMode.FLY, 0.3, 100, Map.of()).rollsFlight());
        assertFalse(type(RideMode.GROUND, 0.3, 100, Map.of()).rollsFlight(),
            "걷는 탑승인데 비행을 굴리면 안 된다");
        assertFalse(type(RideMode.NONE, 0.3, 100, Map.of()).rollsFlight());
    }

    @Test
    @DisplayName("비행 확률은 0.0~1.0 으로 접는다")
    void flyChanceIsClamped() {
        assertEquals(1.0, type(RideMode.FLY, 5.0, 100, Map.of()).flyChance());
        assertEquals(0.0, type(RideMode.FLY, -1.0, 100, Map.of()).flyChance());
    }

    @Test
    @DisplayName("탑승 속도는 등급 기본값을 펫별로 덮어쓸 수 있고, 0 이하로는 안 내려간다")
    void rideSpeedOverridesRarityDefaultAndFloors() {
        final PetType custom = new PetType("dragon", "<gold>드래곤", "pet_dragon",
            Rarity.S, Rarity.S.defaults(), 100, RideMode.FLY, 0.5, 0.9,
            "LEAD", PetType.AnimationSet.defaults(), PetType.MovementProfile.defaults(),
            List.of(), Map.of(), 0);
        assertEquals(0.9, custom.rideSpeed(), "등급 기본값과 달라도 그대로 쓰여야 한다");
        assertNotEquals(Rarity.S.defaults().rideSpeed(), custom.rideSpeed());

        final PetType broken = new PetType("dragon", "<gold>드래곤", "pet_dragon",
            Rarity.S, Rarity.S.defaults(), 100, RideMode.FLY, 0.5, -1.0,
            "LEAD", PetType.AnimationSet.defaults(), PetType.MovementProfile.defaults(),
            List.of(), Map.of(), 0);
        assertTrue(broken.rideSpeed() > 0.0, "0 이하면 탑승해도 안 움직인다");
    }

    @Test
    @DisplayName("성장 상한은 1 미만으로 내려가지 않는다 — 0이면 아무도 성체가 못 된다")
    void growthMaxFloorsAtOne() {
        assertEquals(1, type(RideMode.NONE, 0.0, 0, Map.of()).growthMax());
        assertEquals(1, type(RideMode.NONE, 0.0, -50, Map.of()).growthMax());
    }

    @Test
    @DisplayName("next-stage 가 비면 다음 단계에서도 같은 종류를 유지한다")
    void emptyNextStageMeansNoChange() {
        assertFalse(simple().hasNextStage());
        assertTrue(type(RideMode.NONE, 0.0, 100, Map.of("dragon", 1)).hasNextStage());
    }

    @Test
    @DisplayName("next-stage 는 설정에 적은 순서를 지킨다")
    void nextStageKeepsInsertionOrder() {
        // Weighted.pick 이 순서대로 구간을 나눈다. Map.copyOf 를 쓰면 JVM 마다 순서가
        // 달라져 같은 시드에서도 결과가 갈린다 — EggDefinition 에서 실제로 겪은 버그다.
        final Map<String, Integer> declared = new LinkedHashMap<>();
        declared.put("a", 1);
        declared.put("b", 1);
        declared.put("c", 1);
        declared.put("d", 1);

        assertEquals(List.of("a", "b", "c", "d"),
            List.copyOf(type(RideMode.NONE, 0.0, 100, declared).nextStage().keySet()));
    }

    @Test
    @DisplayName("next-stage 는 밖에서 고칠 수 없다")
    void nextStageIsImmutable() {
        final PetType wolf = type(RideMode.NONE, 0.0, 100, Map.of("dragon", 1));
        assertThrows(UnsupportedOperationException.class, () -> wolf.nextStage().put("pig", 99));
    }

    @Test
    @DisplayName("애니메이션 이름은 매핑이 있으면 바꾸고 없으면 그대로 쓴다")
    void animationsFallBackToLogicalName() {
        final var mapped = new PetType.AnimationSet(Map.of(PetType.AnimationSet.WALK, "wolf_walk"));

        assertEquals("wolf_walk", mapped.resolve(PetType.AnimationSet.WALK));
        assertEquals("idle", mapped.resolve(PetType.AnimationSet.IDLE),
            "매핑이 없으면 논리 이름을 그대로 쓴다 — 모델 제작자가 전부 적을 필요가 없다");
    }

    @Test
    @DisplayName("이동 수치는 쓸 수 있는 범위로 접힌다 — 설정 실수로 서버가 느려지면 안 된다")
    void movementProfileClampsBadValues() {
        final var broken = new PetType.MovementProfile(-5.0, -1.0, 0.0, 0.0);

        assertTrue(broken.followDistance() >= 0.5, "0이면 펫들이 주인 위에 겹친다");
        assertTrue(broken.walkSpeed() > 0.0, "음수 속도는 펫을 주인 반대쪽으로 민다");
        assertTrue(broken.teleportDistance() >= 4.0, "0이면 매 틱 텔레포트가 된다");
    }

    @Test
    @DisplayName("달리기가 걷기보다 느릴 수는 없다")
    void runIsNeverSlowerThanWalk() {
        final var reversed = new PetType.MovementProfile(2.0, 0.5, 0.1, 24.0);

        assertEquals(0.5, reversed.runSpeed(), 1.0e-9,
            "그대로 두면 멀어질수록 느려지는 펫이 된다");
    }

    @Test
    @DisplayName("기본값은 접히지 않는다 — 접는 범위가 기본값을 삼키면 안 된다")
    void defaultsSurviveClamping() {
        final var defaults = PetType.MovementProfile.defaults();

        assertEquals(2.0, defaults.followDistance());
        assertEquals(0.25, defaults.walkSpeed());
        assertEquals(0.45, defaults.runSpeed());
        assertEquals(24.0, defaults.teleportDistance());
    }

    @Test
    @DisplayName("플러그인이 아는 애니메이션 이름에는 실제로 쓰는 여섯이 다 들어 있다")
    void knownAnimationsCoverWhatWePlay() {
        final var known = PetType.AnimationSet.KNOWN;

        for (final String name : List.of("idle", "walk", "run", "ride", "fly", "eat")) {
            assertTrue(known.contains(name), name + " 은 실제로 재생하는 이름이다");
        }
        assertFalse(known.contains("wlak"), "오타를 통과시키면 경고를 낼 수 없다");
    }

    @Test
    @DisplayName("능력 목록도 밖에서 고칠 수 없다")
    void abilitiesAreImmutable() {
        final PetType wolf = simple();
        assertThrows(UnsupportedOperationException.class,
            () -> wolf.abilities().add(new AbilityDefinition("x", Map.of())));
    }
}
