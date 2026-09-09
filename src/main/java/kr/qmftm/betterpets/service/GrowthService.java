package kr.qmftm.betterpets.service;

import kr.qmftm.betterpets.config.PetCatalog;
import kr.qmftm.betterpets.domain.GrowthCurve;
import kr.qmftm.betterpets.domain.LifeStage;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetType;
import kr.qmftm.betterpets.domain.Weighted;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 성장도와 생애주기 전이.
 *
 * <p>원작 시즌 2를 따른다 — 시간 경과 1분당 +1, 먹이 +10, 상한 도달 시 성체.
 *
 * <p><b>성장 단계(growth stage)</b> — 성장도가 상한에 닿아도 곧바로 성체가 되지 않을 수 있다.
 * {@code max-stage} 설정값(기본 1)에 못 미쳤으면 <b>다음 단계로 넘어간다</b>: 펫 종류의
 * {@code next-stage} 가중치로 다음 형태를 추첨하고(비어 있으면 같은 종류 유지), 성장도를
 * 0부터 다시 채운다. 설정된 최대 단계에 이르러서야 성체가 되고, 그 뒤로는 더 자라지 않는다.
 * 기본값 1은 이전 동작(성장도가 차면 바로 성체)과 같다.
 */
public final class GrowthService {

    private final PetCatalog catalog;
    private final int feedAmount;
    private final boolean overfeedGimmick;
    private final int overfeedCount;
    private final long overfeedWindowMillis;
    private final int maxStage;

    /** 펫별 과급식 카운터. 짧은 시간에 몰아 먹이면 돼지가 된다. */
    private final java.util.Map<java.util.UUID, FeedBurst> bursts = new java.util.concurrent.ConcurrentHashMap<>();

    private record FeedBurst(int count, long since) {}

    public GrowthService(final PetCatalog catalog,
                         final int feedAmount,
                         final boolean overfeedGimmick,
                         final int overfeedCount,
                         final long overfeedWindowMillis,
                         final int maxStage) {
        this.catalog = catalog;
        this.feedAmount = feedAmount;
        this.overfeedGimmick = overfeedGimmick;
        this.overfeedCount = overfeedCount;
        this.overfeedWindowMillis = overfeedWindowMillis;
        // 0 이하로 설정되면 아무도 성체가 될 수 없다. 최소 1로 막는다.
        this.maxStage = Math.max(1, maxStage);
    }

    /** 저장된 값에 경과 시간을 반영한다. 읽는 시점마다 부르면 된다. */
    public void refresh(final PetData data) {
        final int max = maxOf(data);
        data.applyGrowth(GrowthCurve.project(
            data.growth(), data.updatedAt(), System.currentTimeMillis(), max));
    }

    /**
     * 먹이를 준다.
     *
     * @return 이번 급여로 일어난 일
     */
    public FeedResult feed(final PetData data) {
        final int max = maxOf(data);
        refresh(data);

        if (data.stage() == LifeStage.EGG) {
            // 알에 처음 먹이를 주면 부화가 시작된다.
            data.stage(LifeStage.HATCHING);
        }

        data.addGrowth(feedAmount, max);

        if (overfeedGimmick && registerBurst(data)) {
            data.stage(LifeStage.PIG);
            bursts.remove(data.petId());
            return FeedResult.BECAME_PIG;
        }

        final StageResult stageResult = promoteIfGrown(data);
        if (stageResult == StageResult.GREW_UP) {
            return FeedResult.GREW_UP;
        }
        if (stageResult == StageResult.STAGE_UP) {
            return FeedResult.STAGE_UP;
        }
        if (data.stage() == LifeStage.HATCHING) {
            // 부화는 즉시 끝난다. 연출은 애니메이션이 담당한다.
            data.stage(LifeStage.BABY);
            return FeedResult.HATCHED;
        }
        return FeedResult.FED;
    }

    /**
     * 성장도가 상한에 닿았을 때의 처리.
     *
     * <p>이미 최대 단계면 성체가 되고 끝난다 — 이때 비행 가능 여부가 확정된다 (원작은
     * A등급부터 확률적으로 비행 펫이 나온다). 최대 단계에 못 미쳤으면 다음 단계로 넘어간다:
     * {@code next-stage} 가중치로 종류를 다시 정하고(비어 있으면 그대로), 성장도를 0부터
     * 다시 채운다.
     *
     * @return 이번 호출로 일어난 일. 상한에 닿지 않았으면 {@link StageResult#NONE}
     */
    public StageResult promoteIfGrown(final PetData data) {
        if (data.stage() != LifeStage.BABY && data.stage() != LifeStage.HATCHING) {
            return StageResult.NONE;
        }
        final PetType type = catalog.type(data.typeId()).orElse(null);
        if (type == null || data.growth() < type.growthMax()) {
            return StageResult.NONE;
        }

        if (data.growthStage() >= maxStage) {
            data.stage(LifeStage.ADULT);
            if (type.rarity().canRollFlight()) {
                data.canFly(ThreadLocalRandom.current().nextDouble() < type.flyChance());
            }
            return StageResult.GREW_UP;
        }

        final String nextTypeId = type.hasNextStage()
            ? Weighted.pick(type.nextStage(), ThreadLocalRandom.current(), type.id())
            : type.id();
        data.typeId(nextTypeId);
        data.growthStage(data.growthStage() + 1);
        // 성장도와 기준 시각은 항상 함께 갱신한다 — applyGrowth 가 그 계약을 지킨다.
        data.applyGrowth(new GrowthCurve.Projection(0, System.currentTimeMillis()));
        return StageResult.STAGE_UP;
    }

    /** 과급식 판정. 창 안에서 기준 횟수를 넘으면 true. */
    private boolean registerBurst(final PetData data) {
        if (data.stage() != LifeStage.BABY && data.stage() != LifeStage.HATCHING) {
            return false;
        }
        final long now = System.currentTimeMillis();
        final FeedBurst updated = bursts.compute(data.petId(), (key, existing) -> {
            if (existing == null || now - existing.since() > overfeedWindowMillis) {
                return new FeedBurst(1, now);
            }
            return new FeedBurst(existing.count() + 1, existing.since());
        });
        return updated.count() >= overfeedCount;
    }

    public int maxOf(final PetData data) {
        return catalog.type(data.typeId()).map(PetType::growthMax).orElse(100);
    }

    /** 설정된 최대 성장 단계. GUI 에서 "성장 단계 N/max" 표시에 쓴다. */
    public int maxStage() {
        return maxStage;
    }

    public double progressOf(final PetData data) {
        return GrowthCurve.progress(data.growth(), maxOf(data));
    }

    /** 다음 성장까지 남은 밀리초. 만렙이면 -1. */
    public long millisUntilNextPoint(final PetData data) {
        return GrowthCurve.millisUntilNextPoint(
            data.growth(), data.updatedAt(), System.currentTimeMillis(), maxOf(data));
    }

    public enum FeedResult {
        FED, HATCHED, STAGE_UP, GREW_UP, BECAME_PIG
    }

    /** {@link #promoteIfGrown} 의 결과. */
    public enum StageResult {
        /** 아직 성장도가 상한에 닿지 않았다. */
        NONE,
        /** 다음 단계로 넘어갔다. 아직 성체는 아니다. */
        STAGE_UP,
        /** 최대 단계에 도달해 성체가 됐다. */
        GREW_UP
    }
}
