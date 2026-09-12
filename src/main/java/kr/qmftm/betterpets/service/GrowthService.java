package kr.qmftm.betterpets.service;

import kr.qmftm.betterpets.config.PetCatalog;
import kr.qmftm.betterpets.domain.FeedDefinition;
import kr.qmftm.betterpets.domain.FullnessCurve;
import kr.qmftm.betterpets.domain.GrowthCurve;
import kr.qmftm.betterpets.domain.LifeStage;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetType;
import kr.qmftm.betterpets.domain.Weighted;

import java.util.Optional;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 성장도와 생애주기 전이.
 *
 * <p>원작 시즌 2를 따른다 — 시간 경과 1분당 +1, 먹이 +10, 상한 도달 시 성체.
 * 알 아이템은 아기를 바로 꺼내주므로 부화 단계는 없다.
 *
 * <p><b>성장 단계(growth stage)</b> — 성장도가 상한에 닿아도 곧바로 성체가 되지 않을 수 있다.
 * {@code max-stage} 설정값(기본 1)에 못 미쳤으면 <b>다음 단계로 넘어간다</b>: 펫 종류의
 * {@code next-stage} 가중치로 다음 형태를 추첨하고(비어 있으면 같은 종류 유지), 성장도를
 * 0부터 다시 채운다. 설정된 최대 단계에 이르러서야 성체가 되고, 그 뒤로는 더 자라지 않는다.
 * 기본값 1은 이전 동작(성장도가 차면 바로 성체)과 같다.
 */
public final class GrowthService {

    /**
     * 종류 id → 정의.
     *
     * <p>{@link PetCatalog} 를 통째로 받지 않고 조회 함수만 받는다. 카탈로그는 파일과
     * {@code AbilityRegistry}(그리고 그 뒤의 {@code Plugin})에 묶여 있어서, 그대로 두면
     * <b>플러그인의 핵심 로직인 성장·진화·과급식이 서버 없이는 한 줄도 검증되지 않는다.</b>
     * 필요한 건 조회 하나뿐이라 값이 맞지 않는다.
     */
    private final java.util.function.Function<String, Optional<PetType>> types;

    /**
     * 성장·기믹 설정.
     *
     * <p>여섯 값을 생성자에서 붙박아 두고 있었다. {@code /petadmin reload} 로는 바꿀 수
     * 없었다는 뜻이다 — {@code growth.max-stage} 도, 과급식 기믹도. 심지어 기동 코드는
     * 리로드 때마다 {@code gimmick.overfeed.count} 를 다시 읽어 경고까지 냈으면서
     * 정작 그 값을 쓰는 이쪽에는 밀어 넣지 않았다.
     *
     * <p>{@link kr.qmftm.betterpets.domain.PetLimits}·{@code BroadcastService.Rules} 와
     * 같은 방식으로 묶는다 — 레코드 하나를 통째로 갈아끼우면 절반만 반영된 상태가
     * 생기지 않는다.
     */
    private volatile Tuning tuning;

    /** 설정 묶음. 값을 접는 규칙도 여기 둔다 — 접는 자리가 하나면 새는 경로가 없다. */
    public record Tuning(int feedAmount,
                         boolean overfeedGimmick,
                         int overfeedCount,
                         long overfeedWindowMillis,
                         String overfeedBecomes,
                         int maxStage,
                         int fullnessMinGain,
                         int fullnessMaxGain,
                         int fullnessMax,
                         long fullnessDecayMillis) {
        public Tuning {
            // 0 이하로 두면 registerBurst 가 첫 급여에서 바로 참이 된다 — 먹이 한 번에
            // 모든 펫이 돼지가 된다는 뜻이다. 끄고 싶으면 enabled: false 를 쓴다.
            overfeedCount = Math.max(2, overfeedCount);
            // 0 이하로 설정되면 아무도 성체가 될 수 없다. 최소 1로 막는다.
            maxStage = Math.max(1, maxStage);
            // 음수 증가량은 포만도를 먹일수록 깎는다는 뜻이라 의도가 아니다.
            fullnessMinGain = Math.max(0, fullnessMinGain);
            // 최소가 최대보다 크면 ThreadLocalRandom.nextInt(min, max+1) 이 예외를 던진다.
            fullnessMaxGain = Math.max(fullnessMinGain, fullnessMaxGain);
            // 0 이하로 두면 첫 급여부터 막힌다 — 급여 자체가 안 되는 펫이 나온다.
            fullnessMax = Math.max(1, fullnessMax);
            // 음수는 FullnessCurve 에서 "꺼짐"과 같은 뜻으로 처리하지만, 여기서 0으로
            // 접어두면 그 사실을 몰라도 되는 값이 하나 줄어든다.
            fullnessDecayMillis = Math.max(0, fullnessDecayMillis);
        }
    }

    /**
     * 펫별 과급식 카운터. 짧은 시간에 몰아 먹이면 돼지가 된다.
     *
     * <p>항목은 창(window)이 지나면 의미가 없어지는데, 지우는 곳이 "실제로 돼지가 됐을 때"
     * 하나뿐이었다. 먹이를 준 모든 펫의 항목이 서버가 살아 있는 내내 남았다는 뜻이다 —
     * 놓아준 펫도, 퇴장한 플레이어의 펫도. 항목 하나는 작지만 상한이 없는 게 문제다.
     * {@link #pruneExpired} 가 가끔 훑어 지운다.
     */
    private final java.util.Map<java.util.UUID, FeedBurst> bursts = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 이 개수를 넘으면 만료된 항목을 훑어 지운다.
     *
     * <p>급여마다 전체를 훑으면 마리 수에 비례하는 일을 매번 하게 된다. 반대로 아예 안
     * 훑으면 무한히 쌓인다. 5명 서버에서 동시에 과급식 중인 펫이 이 수를 넘을 일은
     * 없으므로, 넘었다는 건 곧 대부분이 만료된 찌꺼기라는 뜻이다.
     */
    private static final int BURST_PRUNE_THRESHOLD = 64;

    private record FeedBurst(int count, long since) {}

    /** 운영용. 조회를 카탈로그에 맡긴다. */
    public GrowthService(final PetCatalog catalog, final Tuning tuning) {
        this(catalog::type, tuning);
    }

    public GrowthService(final java.util.function.Function<String, Optional<PetType>> types,
                         final Tuning tuning) {
        this.types = types;
        this.tuning = tuning;
    }

    /** {@code /petadmin reload} 가 부른다. 통째로 갈아끼운다. */
    public void tuning(final Tuning value) {
        tuning = value;
    }

    public Tuning tuning() {
        return tuning;
    }

    /** 먹이에 {@code growth} 를 적지 않았을 때 쓰는 전역 기본값. */
    public int feedAmount() {
        return tuning.feedAmount();
    }

    /**
     * 저장된 값에 경과 시간을 반영한다. 읽는 시점마다 부르면 된다.
     *
     * <p>흔한 경우를 먼저 쳐낸다. 1분(성장도 1점)이 안 지났으면 {@link GrowthCurve#project}
     * 는 같은 값을 돌려주면서 {@code Projection} 을 하나 만들 뿐이다. <b>틱 루프가 초당
     * 5번, 소환된 펫마다 부르는 자리</b>라 그 할당이 고스란히 쓰레기가 된다. 실제로 값이
     * 바뀌는 건 1분에 한 번이다.
     */
    public void refresh(final PetData data) {
        final long now = System.currentTimeMillis();
        final long elapsed = now - data.updatedAt();
        // elapsed 가 음수면 시계가 뒤로 간 것이다. 그 처리는 project 에 맡긴다.
        // growth 가 상한을 넘어 있으면(설정에서 growth-max 를 낮춘 경우) 깎아야 하므로
        // 이때도 건너뛰지 않는다.
        if (elapsed >= 0 && elapsed < GrowthCurve.MILLIS_PER_POINT && data.growth() <= maxOf(data)) {
            return;
        }
        data.applyGrowth(GrowthCurve.project(data.growth(), data.updatedAt(), now, maxOf(data)));
    }

    /**
     * 경과 시간을 반영하고, 그 결과 상한에 닿았으면 다음 단계로 넘긴다.
     *
     * <p><b>둘을 짝으로 묶는 게 요점이다.</b> {@link #refresh} 만 부르면 성장도는 맞지만
     * 성체가 되지는 않는다. 그리고 성장도가 상한에 붙은 뒤에는 {@code refresh} 가 값을
     * 바꾸지 않으므로, "값이 바뀌었을 때만 확인한다"는 식으로 둘을 이으면 <b>딱 그
     * 펫들이 영원히 아기로 남는다</b> — 보관함에 오래 넣어둔 펫과 접속하지 않은 동안
     * 자란 펫이 전부 그랬다.
     *
     * @return 이번 확인으로 일어난 일
     */
    public StageResult catchUp(final PetData data) {
        refresh(data);
        refreshFullness(data);
        return promoteIfGrown(data);
    }

    /**
     * 저장된 포만도에 경과 시간을 반영해 깎는다. {@link #refresh} 와 같은 지연 계산
     * 패턴이다 — 틱마다 저장하는 대신 읽는 시점(급여·{@code catchUp})마다 부른다.
     */
    public void refreshFullness(final PetData data) {
        final long now = System.currentTimeMillis();
        data.applyFullness(FullnessCurve.project(
            data.fullness(), data.fullnessUpdatedAt(), now, tuning.fullnessDecayMillis()));
    }

    /**
     * 먹이를 준다.
     *
     * <p><b>포만도부터 본다.</b> 상한에 닿은 펫은 성장도를 건드리기 전에 거절한다 —
     * 거절할 거면 아이템을 쓰기 전에 알아야 호출부가 소비하지 않고 돌려줄 수 있다.
     *
     * @param feed 먹인 먹이. 성장도 증가량이 여기서 나온다 —
     *             {@code growth} 를 적지 않은 먹이는 전역 기본값을 쓴다
     * @return 이번 급여로 일어난 일
     */
    public FeedResult feed(final PetData data, final FeedDefinition feed) {
        // 상한 판정 전에 먼저 깎는다 — 안 그러면 한참 굶겨둔 펫도 마지막으로 저장된
        // (아직 안 깎인) 값으로 판정돼 먹일 수 있어야 할 상황에서 거절당한다.
        refreshFullness(data);
        if (data.fullness() >= tuning.fullnessMax()) {
            return FeedResult.TOO_FULL;
        }
        final int max = maxOf(data);
        refresh(data);
        data.addGrowth(feed.growthOr(tuning.feedAmount()), max);
        data.addFullness(randomFullnessGain());

        if (tuning.overfeedGimmick() && registerBurst(data)) {
            becomePig(data);
            bursts.remove(data.petId());
            return FeedResult.BECAME_PIG;
        }

        final StageResult stageResult = promoteIfGrown(data);
        if (stageResult == StageResult.GREW_UP) {
            bursts.remove(data.petId());    // 다 자랐다. 더는 과급식 대상이 아니다
            return FeedResult.GREW_UP;
        }
        if (stageResult == StageResult.STAGE_UP) {
            return FeedResult.STAGE_UP;
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
        if (data.stage() != LifeStage.BABY) {
            return StageResult.NONE;
        }
        final PetType type = types.apply(data.typeId()).orElse(null);
        if (type == null || data.growth() < type.growthMax()) {
            return StageResult.NONE;
        }

        if (data.growthStage() >= tuning.maxStage()) {
            data.stage(LifeStage.ADULT);
            // 나는 탑승 종류만 추첨한다. 실패하면 걷는 탑승으로 내려간다 (RideMode.effective).
            if (type.rollsFlight()) {
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

    /**
     * 과급식 이스터에그. 상태를 {@link LifeStage#PIG} 로 바꾸고 종류도 돼지로 갈아끼운다.
     *
     * <p>상태만 바꾸면 겉모습은 그대로라 "돼지가 됐다"는 메시지와 화면이 어긋난다.
     * 설정된 돼지 종류가 없으면 상태만 바꾼다 — 기믹은 못 살려도 펫을 망가뜨리지는 않는다.
     * (그 경우 기동 시 경고가 뜬다)
     *
     * <p>모델 교체는 호출부가 {@code PetService.refreshAfterGrowth} 로 마무리한다.
     * 소환 중인 개체는 소환 시점의 종류를 들고 있어서, 데이터만 바꾸면 화면이 안 바뀐다.
     */
    private void becomePig(final PetData data) {
        data.stage(LifeStage.PIG);
        final String becomes = tuning.overfeedBecomes();
        if (becomes == null || becomes.isBlank()) {
            return;
        }
        if (types.apply(becomes).isEmpty()) {
            return;
        }
        data.typeId(becomes);
        // 돼지가 날아다니면 곤란하다. 나중에 돼지 종류를 FLY 로 바꿔도
        // 추첨 없이 비행이 딸려가지 않도록 여기서 지운다.
        data.canFly(false);
    }

    /** 이번 급여로 오를 포만도. {@code min}~{@code max} 사이에서 고른다(양끝 포함). */
    private int randomFullnessGain() {
        final int min = tuning.fullnessMinGain();
        final int max = tuning.fullnessMaxGain();
        return min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /** 과급식 판정. 창 안에서 기준 횟수를 넘으면 true. */
    private boolean registerBurst(final PetData data) {
        if (data.stage() != LifeStage.BABY) {
            return false;
        }
        final long now = System.currentTimeMillis();
        if (bursts.size() > BURST_PRUNE_THRESHOLD) {
            pruneExpired(now);
        }
        final FeedBurst updated = bursts.compute(data.petId(), (key, existing) -> {
            if (existing == null || now - existing.since() > tuning.overfeedWindowMillis()) {
                return new FeedBurst(1, now);
            }
            return new FeedBurst(existing.count() + 1, existing.since());
        });
        return updated.count() >= tuning.overfeedCount();
    }

    /** 창이 지난 항목을 지운다. 지나면 어차피 새 창으로 다시 시작하므로 값이 없다. */
    private void pruneExpired(final long now) {
        // 창 길이를 한 번만 읽는다. 람다 안에서 읽으면 항목마다 volatile 을 다시 읽고,
        // 훑는 도중 리로드가 끼면 앞뒤 항목이 다른 기준으로 지워진다.
        final long window = tuning.overfeedWindowMillis();
        bursts.entrySet().removeIf(entry -> now - entry.getValue().since() > window);
    }

    /**
     * 이 펫의 과급식 카운터를 버린다.
     *
     * <p>더 자랄 수 없게 된 펫(성체·돼지)과 놓아준 펫의 항목은 남겨둘 이유가 없다.
     */
    public void forget(final PetData data) {
        bursts.remove(data.petId());
    }

    public int maxOf(final PetData data) {
        return types.apply(data.typeId()).map(PetType::growthMax).orElse(100);
    }

    /** 설정된 최대 성장 단계. GUI 에서 "성장 단계 N/max" 표시에 쓴다. */
    public int maxStage() {
        return tuning.maxStage();
    }

    /** 포만도 상한. GUI 에서 "포만도 N/max" 표시에 쓴다. */
    public int fullnessMax() {
        return tuning.fullnessMax();
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
        FED, STAGE_UP, GREW_UP, BECAME_PIG,
        /** 포만도가 상한에 닿아 먹이를 거절했다. 성장도도 포만도도 안 바뀌었다. */
        TOO_FULL
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
