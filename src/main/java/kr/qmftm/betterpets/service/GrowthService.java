package kr.qmftm.betterpets.service;

import kr.qmftm.betterpets.config.PetCatalog;
import kr.qmftm.betterpets.domain.GrowthCurve;
import kr.qmftm.betterpets.domain.LifeStage;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 성장도와 생애주기 전이.
 *
 * <p>원작 시즌 2를 따른다 — 시간 경과 1분당 +1, 먹이 +10, 상한 도달 시 성체.
 */
public final class GrowthService {

    private final PetCatalog catalog;
    private final int feedAmount;
    private final boolean overfeedGimmick;
    private final int overfeedCount;
    private final long overfeedWindowMillis;

    /** 펫별 과급식 카운터. 짧은 시간에 몰아 먹이면 돼지가 된다. */
    private final java.util.Map<java.util.UUID, FeedBurst> bursts = new java.util.concurrent.ConcurrentHashMap<>();

    private record FeedBurst(int count, long since) {}

    public GrowthService(final PetCatalog catalog,
                         final int feedAmount,
                         final boolean overfeedGimmick,
                         final int overfeedCount,
                         final long overfeedWindowMillis) {
        this.catalog = catalog;
        this.feedAmount = feedAmount;
        this.overfeedGimmick = overfeedGimmick;
        this.overfeedCount = overfeedCount;
        this.overfeedWindowMillis = overfeedWindowMillis;
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
        if (promoteIfGrown(data)) {
            return FeedResult.GREW_UP;
        }
        if (data.stage() == LifeStage.HATCHING) {
            // 부화는 즉시 끝난다. 연출은 애니메이션이 담당한다.
            data.stage(LifeStage.BABY);
            return FeedResult.HATCHED;
        }
        return FeedResult.FED;
    }

    /**
     * 성장도가 상한에 닿았으면 성체로 올린다.
     *
     * <p>이때 비행 가능 여부가 확정된다 — 원작은 A등급부터 확률적으로 비행 펫이 나온다.
     *
     * @return 이번 호출로 성체가 됐으면 true
     */
    public boolean promoteIfGrown(final PetData data) {
        if (data.stage() != LifeStage.BABY && data.stage() != LifeStage.HATCHING) {
            return false;
        }
        final PetType type = catalog.type(data.typeId()).orElse(null);
        if (type == null || data.growth() < type.growthMax()) {
            return false;
        }
        data.stage(LifeStage.ADULT);
        if (type.rarity().canRollFlight()) {
            data.canFly(ThreadLocalRandom.current().nextDouble() < type.flyChance());
        }
        return true;
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

    public double progressOf(final PetData data) {
        return GrowthCurve.progress(data.growth(), maxOf(data));
    }

    /** 다음 성장까지 남은 밀리초. 만렙이면 -1. */
    public long millisUntilNextPoint(final PetData data) {
        return GrowthCurve.millisUntilNextPoint(
            data.growth(), data.updatedAt(), System.currentTimeMillis(), maxOf(data));
    }

    public enum FeedResult {
        FED, HATCHED, GREW_UP, BECAME_PIG
    }
}
