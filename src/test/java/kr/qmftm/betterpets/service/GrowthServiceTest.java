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
 */
class GrowthServiceTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final long DAY = 24L * 60 * 60 * 1000;

    private static PetType type(final String id, final int growthMax,
                                final RideMode ride, final double flyChance,
                                final Map<String, Integer> nextStage) {
        return new PetType(id, id, "model_" + id, Rarity.A, Rarity.A.defaults(),
            growthMax, ride, flyChance, PetType.AnimationSet.defaults(),
            PetType.MovementProfile.defaults(), List.of(), nextStage, 0);
    }

    private static final Map<String, PetType> WORLD = new LinkedHashMap<>();

    static {
        WORLD.put("wolf", type("wolf", 100, RideMode.GROUND, 0.0, Map.of()));
        WORLD.put("pig", type("pig", 100, RideMode.GROUND, 0.0, Map.of()));
        // 알에서 나온 형태. 자라면 반드시 dragon 이 된다 (가중치가 하나뿐).
        WORLD.put("hatchling", type("hatchling", 100, RideMode.GROUND, 0.0, Map.of("dragon", 1)));
        // 반드시 나는 펫 — 추첨을 결정적으로 만들려고 확률을 1.0 으로 둔다.
        WORLD.put("dragon", type("dragon", 100, RideMode.FLY, 1.0, Map.of()));
    }

    private static GrowthService service(final int maxStage) {
        return service(maxStage, false, 10, 60_000L);
    }

    private static GrowthService service(final int maxStage, final boolean overfeed,
                                         final int overfeedCount, final long windowMillis) {
        return new GrowthService(
            id -> Optional.ofNullable(WORLD.get(id)),
            new GrowthService.Tuning(10, overfeed, overfeedCount, windowMillis, "pig", maxStage));
    }

    private static final FeedDefinition MILK =
        new FeedDefinition("milk", "우유", "MILK_BUCKET", null, null);

    /** 성장도를 한 번에 채우는 먹이. 100번 먹이는 테스트를 쓰지 않으려고. */
    private static final FeedDefinition FEAST =
        new FeedDefinition("feast", "잔치", "CAKE", null, 100);

    private static PetData baby(final String typeId) {
        return PetData.newBaby(OWNER, typeId, System.currentTimeMillis());
    }

    /** 하루 전에 얻어 그 뒤로 아무도 들여다보지 않은 펫. */
    private static PetData babyFrom(final String typeId, final long agoMillis) {
        return PetData.newBaby(OWNER, typeId, System.currentTimeMillis() - agoMillis);
    }

    @Test
    @DisplayName("설정을 갈아끼우면 그 뒤 판정부터 새 값을 쓴다 — 리로드가 먹어야 한다")
    void tuningIsSwappable() {
        final GrowthService service = service(1);
        assertEquals(1, service.maxStage());

        service.tuning(new GrowthService.Tuning(10, false, 10, 60_000L, "pig", 3));

        assertEquals(3, service.maxStage(), "growth.max-stage 를 고치고 리로드하면 반영돼야 한다");
        assertEquals(10, service.feedAmount());
    }

    @Test
    @DisplayName("설정 묶음이 값을 접는다 — 접는 자리가 하나여야 새는 경로가 없다")
    void tuningClampsBadValues() {
        final var broken = new GrowthService.Tuning(10, true, 0, 60_000L, "pig", 0);

        assertEquals(2, broken.overfeedCount(), "0 이면 첫 급여에 바로 돼지가 된다");
        assertEquals(1, broken.maxStage(), "0 이면 아무도 성체가 될 수 없다");
    }

    @Test
    @DisplayName("보관함에 하루 넣어둔 펫은 꺼내 보는 순간 성체가 되어 있어야 한다")
    void cappedGrowthStillPromotes() {
        final PetData pet = babyFrom("wolf", DAY);

        assertEquals(GrowthService.StageResult.GREW_UP, service(1).catchUp(pet));
        assertEquals(LifeStage.ADULT, pet.stage());
    }

    @Test
    @DisplayName("이미 상한에 붙어 있어도 확인은 일어난다 — 여기서 아기가 굳어 있었다")
    void promotionDoesNotDependOnGrowthChanging() {
        final GrowthService service = service(1);
        final PetData pet = babyFrom("wolf", DAY);

        // 첫 확인 직전 상태를 흉내 낸다: 성장도는 이미 상한, 단계는 아직 아기.
        service.refresh(pet);
        assertEquals(100, pet.growth());
        assertEquals(LifeStage.BABY, pet.stage());

        // 여기서 refresh 를 다시 불러도 값이 안 바뀐다. "값이 바뀌었을 때만 확인" 이면
        // 이 펫은 영원히 아기다.
        final int before = pet.growth();
        service.refresh(pet);
        assertEquals(before, pet.growth(), "상한에 붙으면 경과 반영은 아무것도 바꾸지 않는다");

        assertEquals(GrowthService.StageResult.GREW_UP, service.catchUp(pet));
        assertEquals(LifeStage.ADULT, pet.stage());
    }

    @Test
    @DisplayName("확인을 반복해도 한 번만 자란다")
    void catchUpIsIdempotent() {
        final GrowthService service = service(1);
        final PetData pet = babyFrom("wolf", DAY);

        assertEquals(GrowthService.StageResult.GREW_UP, service.catchUp(pet));
        assertEquals(GrowthService.StageResult.NONE, service.catchUp(pet),
            "성체가 된 뒤에도 자꾸 GREW_UP 을 돌려주면 알림이 매 틱 나간다");
    }

    @Test
    @DisplayName("아직 덜 자란 펫은 확인해도 아기 그대로다")
    void catchUpLeavesYoungPetsAlone() {
        final PetData pet = babyFrom("wolf", 5L * 60 * 1000);   // 5분 = 성장도 5

        assertEquals(GrowthService.StageResult.NONE, service(1).catchUp(pet));
        assertEquals(LifeStage.BABY, pet.stage());
        assertEquals(5, pet.growth());
    }

    @Test
    @DisplayName("먹이면 성장도가 오른다")
    void feedingRaisesGrowth() {
        final PetData pet = baby("wolf");

        assertEquals(GrowthService.FeedResult.FED, service(1).feed(pet, MILK));
        assertEquals(10, pet.growth());
    }

    @Test
    @DisplayName("먹이에 growth 를 적으면 그 값을, 아니면 전역 기본값을 쓴다")
    void feedAmountComesFromTheFood() {
        final GrowthService growth = service(1);
        final PetData pet = baby("wolf");

        growth.feed(pet, MILK);
        assertEquals(10, pet.growth(), "전역 기본값");

        final PetData other = baby("wolf");
        growth.feed(other, new FeedDefinition("cake", "케이크", "CAKE", null, 35));
        assertEquals(35, other.growth(), "먹이가 적은 값");
    }

    @Test
    @DisplayName("성장도가 차면 성체가 되고, 나는 종류면 비행이 확정된다")
    void fillingGrowthMakesAnAdult() {
        final PetData pet = baby("dragon");

        assertEquals(GrowthService.FeedResult.GREW_UP, service(1).feed(pet, FEAST));
        assertEquals(LifeStage.ADULT, pet.stage());
        assertTrue(pet.canFly(), "fly-chance 가 1.0 이면 반드시 난다");
    }

    @Test
    @DisplayName("걷는 탑승 종류는 비행 추첨을 아예 돌리지 않는다")
    void groundTypesNeverGainFlight() {
        final PetData pet = baby("wolf");
        service(1).feed(pet, FEAST);

        assertEquals(LifeStage.ADULT, pet.stage());
        assertFalse(pet.canFly());
    }

    @Test
    @DisplayName("최대 단계에 못 미치면 성체가 아니라 다음 단계로 넘어간다")
    void growthStageAdvancesBeforeAdulthood() {
        final GrowthService growth = service(2);
        final PetData pet = baby("hatchling");

        assertEquals(GrowthService.FeedResult.STAGE_UP, growth.feed(pet, FEAST));
        assertEquals(LifeStage.BABY, pet.stage(), "아직 아기다");
        assertEquals(2, pet.growthStage());
        assertEquals("dragon", pet.typeId(), "next-stage 로 종류가 바뀐다");
        assertEquals(0, pet.growth(), "성장도는 0부터 다시 채운다");

        // 2단계가 최대라 이번엔 성체가 된다.
        assertEquals(GrowthService.FeedResult.GREW_UP, growth.feed(pet, FEAST));
        assertEquals(LifeStage.ADULT, pet.stage());
    }

    @Test
    @DisplayName("next-stage 가 비면 단계만 오르고 종류는 그대로다")
    void emptyNextStageKeepsTheType() {
        final PetData pet = baby("wolf");

        assertEquals(GrowthService.FeedResult.STAGE_UP, service(3).feed(pet, FEAST));
        assertEquals("wolf", pet.typeId());
        assertEquals(2, pet.growthStage());
    }

    @Test
    @DisplayName("다 자란 펫은 더 자라지 않는다")
    void adultsStopGrowing() {
        final GrowthService growth = service(1);
        final PetData pet = baby("wolf");
        growth.feed(pet, FEAST);

        assertEquals(GrowthService.FeedResult.FED, growth.feed(pet, MILK));
        assertEquals(LifeStage.ADULT, pet.stage());
        assertEquals(100, pet.growth(), "상한을 넘지 않는다");
    }

    @Test
    @DisplayName("짧은 시간에 몰아 먹이면 돼지가 된다 — 종류까지 바뀐다")
    void overfeedingTurnsIntoAPig() {
        final GrowthService growth = service(1, true, 3, 60_000L);
        final PetData pet = baby("wolf");

        assertEquals(GrowthService.FeedResult.FED, growth.feed(pet, MILK));
        assertEquals(GrowthService.FeedResult.FED, growth.feed(pet, MILK));
        assertEquals(GrowthService.FeedResult.BECAME_PIG, growth.feed(pet, MILK));

        assertEquals(LifeStage.PIG, pet.stage());
        assertEquals("pig", pet.typeId(), "상태만 바꾸면 메시지와 화면이 어긋난다");
        assertFalse(pet.canFly(), "돼지가 날아다니면 곤란하다");
    }

    @Test
    @DisplayName("기믹을 끄면 아무리 먹여도 돼지가 되지 않는다")
    void overfeedCanBeDisabled() {
        final GrowthService growth = service(1, false, 2, 60_000L);
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
        final GrowthService growth = service(1, true, 3, 60_000L);
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
        final GrowthService growth = service(1, true, 2, 60_000L);
        final PetData pet = baby("wolf");
        growth.feed(pet, MILK);
        assertEquals(GrowthService.FeedResult.BECAME_PIG, growth.feed(pet, MILK));

        // 돼지는 더 자라지 않으므로 급여 자체가 성장으로 이어지지 않는다.
        // 카운터가 남아 있었다면 여기서 다시 BECAME_PIG 가 나온다.
        assertEquals(GrowthService.FeedResult.FED, growth.feed(pet, MILK));
    }

    @Test
    @DisplayName("성체는 과급식 대상이 아니다")
    void adultsCannotBecomePigs() {
        final GrowthService growth = service(1, true, 2, 60_000L);
        final PetData pet = baby("wolf");
        growth.feed(pet, FEAST);        // 바로 성체

        for (int i = 0; i < 10; i++) {
            assertEquals(GrowthService.FeedResult.FED, growth.feed(pet, MILK));
        }
        assertEquals(LifeStage.ADULT, pet.stage());
    }

    @Test
    @DisplayName("시간이 흐른 만큼 성장도가 오른다 — 1분당 1")
    void timePassesIntoGrowth() {
        final GrowthService growth = service(1);
        // 하루 전에 마지막으로 갱신된 펫. 상한(100)에 닿아 성체가 되어 있어야 한다.
        final PetData pet = PetData.newBaby(OWNER, "wolf", System.currentTimeMillis() - DAY);

        growth.refresh(pet);
        assertEquals(100, pet.growth());
        assertEquals(GrowthService.StageResult.GREW_UP, growth.promoteIfGrown(pet));
    }

    @Test
    @DisplayName("설정에 없는 종류는 상한 100으로 본다 — 펫을 못 자라게 만들지 않는다")
    void unknownTypeFallsBack() {
        assertEquals(100, service(1).maxOf(baby("그런펫없음")));
    }

    @Test
    @DisplayName("최대 단계를 0 이하로 설정해도 1로 막는다 — 아무도 성체가 못 되면 안 된다")
    void maxStageFloorsAtOne() {
        assertEquals(1, service(0).maxStage());
        assertEquals(1, service(-5).maxStage());
    }
}
