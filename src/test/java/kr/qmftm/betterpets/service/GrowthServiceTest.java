package kr.qmftm.betterpets.service;

import kr.qmftm.betterpets.domain.FeedDefinition;
import kr.qmftm.betterpets.domain.LifeStage;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetType;
import kr.qmftm.betterpets.domain.Rarity;
import kr.qmftm.betterpets.domain.RideMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 성장·진화·과급식. <b>플러그인이 하는 일의 핵심</b>인데 서버 없이는 한 줄도 검증되지
 * 않던 부분이다. {@link GrowthService} 가 카탈로그 대신 조회 함수를 받게 하면서 열렸다.
 *
 * <p>아기·성체 생애주기 구분은 없다 — 성장도의 유일한 역할은 next-stage 로 진화하기까지
 * 걸리는 시간이다. {@code wolf}/{@code pig} 는 next-stage 가 없는(=절대 안 자라는) 종류,
 * {@code hatchling} 은 next-stage 가 있어(=dragon) 실제로 진화하는 종류로 나눠 쓴다.
 */
class GrowthServiceTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final long DAY = 24L * 60 * 60 * 1000;

    private static PetType type(final String id, final int growthMax,
                                final RideMode ride, final Map<String, Integer> nextStage) {
        return new PetType(id, id, "model_" + id, Rarity.A, Rarity.A.defaults(),
            growthMax, ride, Rarity.A.defaults().rideSpeed(), -1.0, -1.0, "LEAD", 1.0,
            PetType.AnimationSet.defaults(), PetType.MovementProfile.defaults(),
            nextStage, 0);
    }

    private static final Map<String, PetType> WORLD = new LinkedHashMap<>();

    static {
        // next-stage 가 없다 — 이 둘은 절대 자라지 않는다.
        WORLD.put("wolf", type("wolf", 100, RideMode.GROUND, Map.of()));
        WORLD.put("pig", type("pig", 100, RideMode.GROUND, Map.of()));
        // next-stage 가 있다 — 자라면 반드시 dragon 이 된다 (가중치가 하나뿐).
        WORLD.put("hatchling", type("hatchling", 100, RideMode.GROUND, Map.of("dragon", 1)));
        // 진화의 끝 — 이 종류에도 next-stage 가 없으므로 더는 자라지 않는다.
        WORLD.put("dragon", type("dragon", 100, RideMode.FLY, Map.of()));
    }

    private static GrowthService service() {
        return service(false, 10, 60_000L);
    }

    private static GrowthService service(final boolean overfeed,
                                         final int overfeedCount, final long windowMillis) {
        return new GrowthService(
            id -> Optional.ofNullable(WORLD.get(id)),
            // 포만도·감소는 여기서 다루지 않는 테스트들이 걸리지 않게 넉넉히/꺼둔 채로 둔다.
            // 그 자체를 보는 테스트는 별도 Tuning 을 직접 만든다.
            new GrowthService.Tuning(10, overfeed, overfeedCount, windowMillis, "pig", 0, 0, 1_000, 0));
    }

    private static final FeedDefinition MILK =
        new FeedDefinition("milk", "우유", "MILK_BUCKET", null, null);

    /** 성장도를 한 번에 채우는 먹이. 100번 먹이는 테스트를 쓰지 않으려고. */
    private static final FeedDefinition FEAST =
        new FeedDefinition("feast", "잔치", "CAKE", null, 100);

    private static PetData baby(final String typeId) {
        return PetData.newBaby(OWNER, typeId, System.currentTimeMillis());
    }

    /** 보관함에 있는(꺼내지 않은) 펫. 시간이 지나도 자라지 않아야 한다. */
    private static PetData babyFrom(final String typeId, final long agoMillis) {
        return PetData.newBaby(OWNER, typeId, System.currentTimeMillis() - agoMillis);
    }

    /** 꺼내둔 채 한동안 아무도 들여다보지 않은 펫. 시간 경과가 성장도로 잡혀야 한다. */
    private static PetData activeBabyFrom(final String typeId, final long agoMillis) {
        final PetData data = babyFrom(typeId, agoMillis);
        data.active(true);
        return data;
    }

    @Test
    @DisplayName("설정을 갈아끼우면 그 뒤 판정부터 새 값을 쓴다 — 리로드가 먹어야 한다")
    void tuningIsSwappable() {
        final GrowthService service = service();
        assertEquals(1_000, service.fullnessMax());

        service.tuning(new GrowthService.Tuning(10, false, 10, 60_000L, "pig", 0, 0, 500, 0));

        assertEquals(500, service.fullnessMax(), "리로드하면 반영돼야 한다");
        assertEquals(10, service.feedAmount());
    }

    @Test
    @DisplayName("설정 묶음이 값을 접는다 — 접는 자리가 하나여야 새는 경로가 없다")
    void tuningClampsBadValues() {
        final var broken = new GrowthService.Tuning(10, true, 0, 60_000L, "pig", -5, -1, 0, -100L);

        assertEquals(2, broken.overfeedCount(), "0 이면 첫 급여에 바로 돼지가 된다");
        assertEquals(0, broken.fullnessMinGain(), "음수 증가량은 먹일수록 깎는다는 뜻이라 안 된다");
        assertEquals(0, broken.fullnessMaxGain(), "최소보다 작은 최대는 nextInt 를 터뜨린다");
        assertEquals(1, broken.fullnessMax(), "0 이면 첫 급여부터 막힌다");
        assertEquals(0, broken.fullnessDecayMillis(), "음수 간격은 의미가 없다 — 0(비활성)으로 접는다");
    }

    @Test
    @DisplayName("꺼내둔 채 하루 지난 펫은 열어 보는 순간 진화해 있어야 한다")
    void activePetCatchesUpAfterADay() {
        final PetData pet = activeBabyFrom("hatchling", DAY);

        assertEquals(GrowthService.StageResult.STAGE_UP, service().catchUp(pet));
        assertEquals("dragon", pet.typeId());
    }

    @Test
    @DisplayName("보관함에 있는(꺼내지 않은) 펫은 시간이 지나도 자라지 않는다")
    void inactivePetsDoNotGrowOverTime() {
        final PetData pet = babyFrom("hatchling", DAY);

        assertEquals(GrowthService.StageResult.NONE, service().catchUp(pet));
        assertEquals(0, pet.growth());
        assertEquals("hatchling", pet.typeId());
    }

    @Test
    @DisplayName("next-stage 가 없는 종류는 꺼내 둬도 시간이 지나 자라지 않는다")
    void terminalTypesNeverGrowOverTime() {
        final PetData pet = activeBabyFrom("wolf", DAY);

        assertEquals(GrowthService.StageResult.NONE, service().catchUp(pet));
        assertEquals(0, pet.growth());
    }

    @Test
    @DisplayName("이미 상한에 붙어 있어도 확인은 일어난다 — 여기서 진화가 멈춰 있었다")
    void promotionDoesNotDependOnGrowthChanging() {
        final GrowthService service = service();
        final PetData pet = activeBabyFrom("hatchling", DAY);

        // 첫 확인 직전 상태를 흉내 낸다: 성장도는 이미 상한, 종류는 아직 그대로.
        service.refresh(pet);
        assertEquals(100, pet.growth());
        assertEquals("hatchling", pet.typeId());

        // 여기서 refresh 를 다시 불러도 값이 안 바뀐다. "값이 바뀌었을 때만 확인" 이면
        // 이 펫은 영원히 진화하지 못한다.
        final int before = pet.growth();
        service.refresh(pet);
        assertEquals(before, pet.growth(), "상한에 붙으면 경과 반영은 아무것도 바꾸지 않는다");

        assertEquals(GrowthService.StageResult.STAGE_UP, service.catchUp(pet));
        assertEquals("dragon", pet.typeId());
    }

    @Test
    @DisplayName("확인을 반복해도 한 번만 진화한다")
    void catchUpIsIdempotent() {
        final GrowthService service = service();
        final PetData pet = activeBabyFrom("hatchling", DAY);

        assertEquals(GrowthService.StageResult.STAGE_UP, service.catchUp(pet));
        assertEquals(GrowthService.StageResult.NONE, service.catchUp(pet),
            "진화한 뒤에도 자꾸 STAGE_UP 을 돌려주면 알림이 매 틱 나간다");
    }

    @Test
    @DisplayName("아직 덜 자란 펫은 확인해도 그대로다")
    void catchUpLeavesYoungPetsAlone() {
        final PetData pet = activeBabyFrom("hatchling", 5L * 60 * 1000);   // 5분 = 성장도 5

        assertEquals(GrowthService.StageResult.NONE, service().catchUp(pet));
        assertEquals("hatchling", pet.typeId());
        assertEquals(5, pet.growth());
    }

    @Test
    @DisplayName("먹이면 성장도가 오른다 — next-stage 가 있는 종류만")
    void feedingRaisesGrowth() {
        final PetData pet = baby("hatchling");

        assertEquals(GrowthService.FeedResult.FED, service().feed(pet, MILK));
        assertEquals(10, pet.growth());
    }

    @Test
    @DisplayName("next-stage 가 없는 종류는 먹여도 성장도가 안 오른다 — 포만도는 오른다")
    void feedingTerminalTypeDoesNotRaiseGrowth() {
        final GrowthService growth = new GrowthService(
            id -> Optional.ofNullable(WORLD.get(id)),
            new GrowthService.Tuning(10, false, 10, 60_000L, "pig", 5, 5, 1_000, 0));
        final PetData pet = baby("wolf");

        assertEquals(GrowthService.FeedResult.FED, growth.feed(pet, MILK));
        assertEquals(0, pet.growth());
        assertEquals(5, pet.fullness(), "포만도는 진화 여부와 무관하게 오른다");
    }

    @Test
    @DisplayName("포만도는 먹일 때마다 오르고, 상한에 닿으면 더 못 먹인다")
    void fullnessBlocksFeedingAtThreshold() {
        final GrowthService growth = new GrowthService(
            id -> Optional.ofNullable(WORLD.get(id)),
            new GrowthService.Tuning(10, false, 10, 60_000L, "pig", 10, 10, 15, 0));
        final PetData pet = baby("hatchling");

        assertEquals(GrowthService.FeedResult.FED, growth.feed(pet, MILK));
        assertEquals(10, pet.fullness());
        assertEquals(10, pet.growth());

        assertEquals(GrowthService.FeedResult.FED, growth.feed(pet, MILK));
        assertEquals(20, pet.fullness());
        assertEquals(20, pet.growth());

        assertEquals(GrowthService.FeedResult.TOO_FULL, growth.feed(pet, MILK));
        assertEquals(20, pet.fullness(), "거절되면 포만도도 안 바뀐다");
        assertEquals(20, pet.growth(), "거절되면 성장도도 안 바뀐다");
    }

    @Test
    @DisplayName("시간이 지나면 포만도가 저절로 내려간다 — catchUp 이 반영해야 한다")
    void fullnessDecaysOverTime() {
        final long decayMillis = 30_000L;
        final GrowthService growth = new GrowthService(
            id -> Optional.ofNullable(WORLD.get(id)),
            new GrowthService.Tuning(10, false, 10, 60_000L, "pig", 0, 0, 1_000, decayMillis));

        final long now = System.currentTimeMillis();
        // 다섯 간격 전에 포만도 20으로 저장돼 있던 펫 — 지금 보면 15여야 한다.
        final PetData pet = new PetData(UUID.randomUUID(), OWNER, "wolf", null,
            LifeStage.NORMAL, 0, 1, 20, now - 5 * decayMillis, false, now, now);

        growth.refreshFullness(pet);
        assertEquals(15, pet.fullness());
    }

    @Test
    @DisplayName("감소를 끄면(0) 시간이 지나도 포만도가 그대로다")
    void fullnessDecayCanBeDisabled() {
        final GrowthService growth = new GrowthService(
            id -> Optional.ofNullable(WORLD.get(id)),
            new GrowthService.Tuning(10, false, 10, 60_000L, "pig", 0, 0, 1_000, 0));

        final long now = System.currentTimeMillis();
        final PetData pet = new PetData(UUID.randomUUID(), OWNER, "wolf", null,
            LifeStage.NORMAL, 0, 1, 20, now - DAY, false, now, now);

        growth.refreshFullness(pet);
        assertEquals(20, pet.fullness());
    }

    @Test
    @DisplayName("먹이에 growth 를 적으면 그 값을, 아니면 전역 기본값을 쓴다")
    void feedAmountComesFromTheFood() {
        final GrowthService growth = service();
        final PetData pet = baby("hatchling");

        growth.feed(pet, MILK);
        assertEquals(10, pet.growth(), "전역 기본값");

        final PetData other = baby("hatchling");
        growth.feed(other, new FeedDefinition("cake", "케이크", "CAKE", null, 35));
        assertEquals(35, other.growth(), "먹이가 적은 값");
    }

    @Test
    @DisplayName("성장도가 차면 next-stage 로 진화한다")
    void fillingGrowthEvolvesToNextStage() {
        final PetData pet = baby("hatchling");

        assertEquals(GrowthService.FeedResult.STAGE_UP, service().feed(pet, FEAST));
        assertEquals("dragon", pet.typeId());
        assertEquals(0, pet.growth(), "성장도는 0부터 다시 채운다");
    }

    @Test
    @DisplayName("여러 단계를 거쳐 진화하는 사슬도 한 단계씩만 나아간다")
    void multiHopChainAdvancesOneStepAtATime() {
        final Map<String, PetType> chain = new LinkedHashMap<>();
        chain.put("egg", type("egg", 100, RideMode.NONE, Map.of("juvenile", 1)));
        chain.put("juvenile", type("juvenile", 100, RideMode.GROUND, Map.of("dragon", 1)));
        chain.put("dragon", type("dragon", 100, RideMode.FLY, Map.of()));
        final GrowthService growth = new GrowthService(id -> Optional.ofNullable(chain.get(id)),
            new GrowthService.Tuning(10, false, 10, 60_000L, "pig", 0, 0, 1_000, 0));
        final PetData pet = PetData.newBaby(OWNER, "egg", System.currentTimeMillis());

        assertEquals(GrowthService.FeedResult.STAGE_UP, growth.feed(pet, FEAST));
        assertEquals("juvenile", pet.typeId());
        assertEquals(2, pet.growthStage());
        assertEquals(0, pet.growth());

        assertEquals(GrowthService.FeedResult.STAGE_UP, growth.feed(pet, FEAST));
        assertEquals("dragon", pet.typeId());
        assertEquals(3, pet.growthStage());

        // dragon 은 next-stage 가 없으니 더는 자라지 않는다.
        assertEquals(GrowthService.FeedResult.FED, growth.feed(pet, MILK));
        assertEquals(0, pet.growth());
    }

    @Test
    @DisplayName("next-stage 가 자기 자신을 가리키면 종류는 그대로, 성장도만 다시 채운다")
    void nextStageCanPointToItself() {
        final Map<String, PetType> world = new LinkedHashMap<>();
        world.put("hatchling", type("hatchling", 100, RideMode.GROUND, Map.of("hatchling", 1)));
        final GrowthService growth = new GrowthService(id -> Optional.ofNullable(world.get(id)),
            new GrowthService.Tuning(10, false, 10, 60_000L, "pig", 0, 0, 1_000, 0));
        final PetData pet = PetData.newBaby(OWNER, "hatchling", System.currentTimeMillis());

        assertEquals(GrowthService.FeedResult.STAGE_UP, growth.feed(pet, FEAST));
        assertEquals("hatchling", pet.typeId(), "자기 자신이 뽑히면 종류는 그대로다");
        assertEquals(2, pet.growthStage());
        assertEquals(0, pet.growth());
    }

    @Test
    @DisplayName("진화가 끝난(next-stage 없는) 펫은 계속 먹여도 성장도가 그대로다")
    void terminalPetsStayAtZeroGrowth() {
        final GrowthService growth = service();
        final PetData pet = baby("wolf");

        for (int i = 0; i < 5; i++) {
            growth.feed(pet, FEAST);
        }
        assertEquals(0, pet.growth());
        assertEquals("wolf", pet.typeId());
    }

    @Test
    @DisplayName("짧은 시간에 몰아 먹이면 돼지가 된다 — 종류까지 바뀐다")
    void overfeedingTurnsIntoAPig() {
        final GrowthService growth = service(true, 3, 60_000L);
        final PetData pet = baby("wolf");

        assertEquals(GrowthService.FeedResult.FED, growth.feed(pet, MILK));
        assertEquals(GrowthService.FeedResult.FED, growth.feed(pet, MILK));
        assertEquals(GrowthService.FeedResult.BECAME_PIG, growth.feed(pet, MILK));

        assertEquals(LifeStage.PIG, pet.stage());
        assertEquals("pig", pet.typeId(), "상태만 바꾸면 메시지와 화면이 어긋난다");
    }

    @Test
    @DisplayName("기믹을 끄면 아무리 먹여도 돼지가 되지 않는다")
    void overfeedCanBeDisabled() {
        final GrowthService growth = service(false, 2, 60_000L);
        final PetData pet = baby("wolf");

        for (int i = 0; i < 10; i++) {
            growth.feed(pet, MILK);
        }
        assertFalse(pet.stage() == LifeStage.PIG);
    }

    @Test
    @DisplayName("과급식 카운터는 펫마다 따로다 — 여러 마리를 번갈아 먹여도 안 뭉친다")
    void burstCounterIsPerPet() {
        // 카운터를 소유자 단위로 세면, 펫 세 마리에게 한 번씩 준 사람이 돼지를 얻는다.
        final GrowthService growth = service(true, 3, 60_000L);
        final PetData a = baby("wolf");
        final PetData b = baby("wolf");
        final PetData c = baby("wolf");

        assertEquals(GrowthService.FeedResult.FED, growth.feed(a, MILK));
        assertEquals(GrowthService.FeedResult.FED, growth.feed(b, MILK));
        assertEquals(GrowthService.FeedResult.FED, growth.feed(c, MILK));
        assertEquals(GrowthService.FeedResult.FED, growth.feed(a, MILK));
    }

    @Test
    @DisplayName("돼지가 된 뒤 카운터가 비워진다 — 아니면 한 번만 먹여도 또 걸린다")
    void burstCounterResetsAfterTheGimmickFires() {
        final GrowthService growth = service(true, 2, 60_000L);
        final PetData pet = baby("wolf");
        growth.feed(pet, MILK);
        assertEquals(GrowthService.FeedResult.BECAME_PIG, growth.feed(pet, MILK));

        // 돼지는 과급식 대상이 아니므로(stage != NORMAL) 다시 걸리지 않는다.
        // 카운터가 남아 있었다면 여기서 다시 BECAME_PIG 가 나온다.
        assertEquals(GrowthService.FeedResult.FED, growth.feed(pet, MILK));
    }

    @Test
    @DisplayName("꺼내져 있으면 시간이 흐른 만큼 성장도가 오른다 — 1분당 1")
    void timePassesIntoGrowth() {
        final GrowthService growth = service();
        // 하루 전에 마지막으로 갱신된 펫. 상한(100)에 닿아 진화할 준비가 되어 있어야 한다.
        final PetData pet = activeBabyFrom("hatchling", DAY);

        growth.refresh(pet);
        assertEquals(100, pet.growth());
        assertEquals(GrowthService.StageResult.STAGE_UP, growth.promoteIfGrown(pet));
    }

    @Test
    @DisplayName("설정에 없는 종류는 상한 100으로 본다 — 펫을 못 자라게 만들지 않는다")
    void unknownTypeFallsBack() {
        assertEquals(100, service().maxOf(baby("그런펫없음")));
    }
}
