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
 * 성장도와 진화.
 *
 * <p><b>성장도의 역할은 하나뿐이다 — 다음 종류로 진화하기까지 걸리는 시간.</b>
 * 아기·성체 같은 생애주기 구분은 없다({@link LifeStage} 참고) — 능력치 스케일링도,
 * 비행 여부 추첨도, 더는 성장도가 하지 않는다. {@code next-stage} 가 있는 종류만
 * 먹이·시간 경과로 성장도가 오르고, 다 차면 그 가중치로 다음 형태를 추첨한다(비어
 * 있지 않은 자기 자신을 넣으면 "한 단계 더 기다린다"가 된다). {@code next-stage} 가
 * 없는 종류는 <b>애초에 기다릴 게 없으므로</b> 성장도가 아예 오르지 않는다.
 *
 * <p>알 아이템은 펫을 곧바로 꺼내주므로 부화 단계는 없다.
 *
 * <p><b>돼지가 되는 것도 이 진화의 갈림길에서만 일어난다.</b> next-stage 로 넘어가는
 * 바로 그 순간, 포만도가 {@code gimmick.overfeed.min-fullness} 이상이면 다음 형태
 * 대신 돼지가 된다({@link #promoteIfGrown} 참고). 그래서 next-stage 가 없는 종류
 * (예: 이미 다 자란 성체, 처음부터 진화하지 않는 종류)는 아무리 먹여도 돼지가 되지
 * 않는다 — 자랄 기회가 없으면 그 기회를 가로챌 것도 없다.
 */
public final class GrowthService {

    /**
     * 종류 id → 정의.
     *
     * <p>{@link PetCatalog} 를 통째로 받지 않고 조회 함수만 받는다. 카탈로그는 파일과
     * {@code Plugin}(리소스 로딩)에 묶여 있어서, 그대로 두면
     * <b>플러그인의 핵심 로직인 성장·진화·과급식이 서버 없이는 한 줄도 검증되지 않는다.</b>
     * 필요한 건 조회 하나뿐이라 값이 맞지 않는다.
     */
    private final java.util.function.Function<String, Optional<PetType>> types;

    /**
     * 성장·기믹 설정.
     *
     * <p>예전에는 이 값들을 생성자에서 붙박아 두고 있었다. {@code /betterpets reload} 로는
     * 바꿀 수 없었다는 뜻이다 — 과급식 기믹도 그랬다. 심지어 기동 코드는 리로드 때마다
     * {@code gimmick.overfeed.min-fullness} 를 다시 읽어 경고까지 냈으면서 정작 그 값을
     * 쓰는 이쪽에는 밀어 넣지 않았다.
     *
     * <p>{@link kr.qmftm.betterpets.domain.PetLimits}·{@code BroadcastService.Rules} 와
     * 같은 방식으로 묶는다 — 레코드 하나를 통째로 갈아끼우면 절반만 반영된 상태가
     * 생기지 않는다.
     */
    private volatile Tuning tuning;

    /** 설정 묶음. 값을 접는 규칙도 여기 둔다 — 접는 자리가 하나면 새는 경로가 없다. */
    public record Tuning(int feedAmount,
                         boolean overfeedGimmick,
                         String overfeedBecomes,
                         String overfeedModel,
                         int overfeedChance,
                         int overfeedMinFullness,
                         int fullnessMinGain,
                         int fullnessMaxGain,
                         int fullnessMax,
                         long fullnessDecayMillis) {
        public Tuning {
            // 100 이상이면 항상 발동, 0 이하면 조건을 채워도 절대 발동하지 않는다는 뜻이라
            // 그대로 둔다 — enabled 와 달리 "거의 안 터지게" 도 의도일 수 있다.
            overfeedChance = Math.min(100, Math.max(0, overfeedChance));
            // 음수는 "조건 없음"과 구분이 안 되므로 0으로 접는다.
            overfeedMinFullness = Math.max(0, overfeedMinFullness);
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

    /** 종류를 찾지 못했을 때 쓰는 성장 상한. 설정이 깨져도 0으로 나누거나 즉시 진화하지 않게 한다. */
    private static final int DEFAULT_GROWTH_MAX = 100;

    /** 운영용. 조회를 카탈로그에 맡긴다. */
    public GrowthService(final PetCatalog catalog, final Tuning tuning) {
        this(catalog::type, tuning);
    }

    public GrowthService(final java.util.function.Function<String, Optional<PetType>> types,
                         final Tuning tuning) {
        this.types = types;
        this.tuning = tuning;
    }

    /** {@code /betterpets reload} 가 부른다. 통째로 갈아끼운다. */
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
     *
     * <p><b>꺼내져 있는 펫만 시간으로 자란다.</b> 보관함에 넣어둔 동안은 시간이 얼마나
     * 지났든 자라지 않는다 — {@link #growsOverTime} 이 그 판정이다. 자라지 않는
     * 상태에서는 값 대신 <b>기준 시각만 지금으로 당긴다.</b> 안 그러면 나중에 조건이
     * 바뀌었을 때(다시 꺼내거나, next-stage 가 생기거나) 그동안 쌓인 시간이 한꺼번에
     * 성장도로 잡힌다.
     */
    public void refresh(final PetData data) {
        refresh(data, types.apply(data.typeId()).orElse(null));
    }

    /**
     * 종류를 이미 찾아둔 호출부용({@link #catchUp}).
     *
     * <p>예전에는 이 안에서 growsOverTime 이 한 번, maxOf 가 최대 두 번 같은 id 를 다시
     * 조회했다: 조회마다 {@code Optional} 두 개와 박싱이 따라붙는데, 여기는 소환된
     * 펫마다 초당 5번 도는 자리라 그 쓰레기가 그대로 쌓인다.
     */
    private void refresh(final PetData data, final PetType type) {
        final long now = System.currentTimeMillis();
        if (!growsOverTime(data, type)) {
            if (data.updatedAt() != now) {
                data.applyGrowth(new GrowthCurve.Projection(data.growth(), now));
            }
            return;
        }
        final int max = maxOf(type);
        final long elapsed = now - data.updatedAt();
        // elapsed 가 음수면 시계가 뒤로 간 것이다. 그 처리는 project 에 맡긴다.
        // growth 가 상한을 넘어 있으면(설정에서 growth-max 를 낮춘 경우) 깎아야 하므로
        // 이때도 건너뛰지 않는다.
        if (elapsed >= 0 && elapsed < GrowthCurve.MILLIS_PER_POINT && data.growth() <= max) {
            return;
        }
        data.applyGrowth(GrowthCurve.project(data.growth(), data.updatedAt(), now, max));
    }

    /**
     * 시간이 지나 성장도가 오를 수 있는 상태인가.
     *
     * <p>둘 다 필요하다 — <b>꺼내져 있어야</b> 하고(보관함에 있는 동안은 자라지 않는다),
     * {@link PetType#growsToNextStage} 여야 한다(next-stage 가 없거나 {@code growth-max}
     * 로 껐으면 성장도가 할 일이 없다).
     *
     * <p>종류는 호출부가 찾아서 넘긴다. 이 판정과 성장 상한이 같은 종류를 보는데, 여기서
     * 다시 찾으면 틱마다 같은 조회가 두 번씩 난다.
     */
    private boolean growsOverTime(final PetData data, final PetType type) {
        if (!data.active()) {
            return false;
        }
        return type != null && type.growsToNextStage();
    }

    /**
     * 경과 시간을 반영하고, 그 결과 상한에 닿았으면 다음 종류로 진화시킨다.
     *
     * <p><b>둘을 짝으로 묶는 게 요점이다.</b> {@link #refresh} 만 부르면 성장도는 맞지만
     * 진화는 일어나지 않는다. 그리고 성장도가 상한에 붙은 뒤에는 {@code refresh} 가 값을
     * 바꾸지 않으므로, "값이 바뀌었을 때만 확인한다"는 식으로 둘을 이으면 <b>딱 그
     * 펫들이 영원히 진화하지 못한다</b> — 보관함에 오래 넣어둔 펫과 접속하지 않은 동안
     * 자란 펫이 전부 그랬다.
     *
     * @return 이번 확인으로 일어난 일
     */
    public StageResult catchUp(final PetData data) {
        // 종류를 한 번만 찾아 둘에게 넘긴다. refresh 는 성장도와 기준 시각만 건드리고
        // typeId 는 손대지 않으므로, promoteIfGrown 이 나중에 다시 찾아도 같은 종류가
        // 나온다. 틱 루프가 펫마다 부르는 자리라 조회 한 번이 그대로 줄어든다.
        final PetType type = types.apply(data.typeId()).orElse(null);
        refresh(data, type);
        refreshFullness(data);
        return promoteIfGrown(data, type);
    }

    /**
     * 저장된 포만도에 경과 시간을 반영해 깎는다. {@link #refresh} 와 같은 지연 계산
     * 패턴이다 — 틱마다 저장하는 대신 읽는 시점(급여·{@code catchUp})마다 부른다.
     */
    public void refreshFullness(final PetData data) {
        final long now = System.currentTimeMillis();
        final int fullness = data.fullness();
        final long updatedAt = data.fullnessUpdatedAt();
        final long interval = tuning.fullnessDecayMillis();

        // 흔한 경우를 먼저 쳐낸다. {@link #refresh} 와 같은 이유다: 여기도 소환된 펫마다
        // 초당 5번 도는데, 실제로 값이 바뀌는 건 decay-seconds 에 한 번뿐이다. 나머지
        // 호출은 입력과 똑같은 Projection 을 만들어 그대로 버린다.
        //
        // 아래 세 조건은 project 가 "값도 기준 시각도 그대로"를 돌려주는 경우와 정확히
        // 같다. 포만도가 0 이하인 경우는 일부러 제외한다: 그때는 project 가 기준 시각을
        // 지금으로 당기므로 건너뛰면 안 된다.
        if (fullness > 0
            && (interval <= 0 || now <= updatedAt || now - updatedAt < interval)) {
            return;
        }
        data.applyFullness(FullnessCurve.project(fullness, updatedAt, now, interval));
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
        data.addFullness(randomFullnessGain());

        // 진화할 곳이 없거나 growth-max 로 꺼둔 종류는 성장도가 할 일이 없다 — 포만도는
        // 오르지만 성장도는 건드리지 않는다. 과급식 판정은 성장도와 무관하므로 그대로 돈다.
        final PetType type = types.apply(data.typeId()).orElse(null);
        if (type != null && type.growsToNextStage()) {
            refresh(data);
            data.addGrowth(feed.growthOr(tuning.feedAmount()), type.growthMax());
        }

        final StageResult stageResult = promoteIfGrown(data);
        return switch (stageResult) {
            case STAGE_UP -> FeedResult.STAGE_UP;
            case BECAME_PIG -> FeedResult.BECAME_PIG;
            case NONE -> FeedResult.FED;
        };
    }

    /**
     * 성장도가 상한에 닿아 다음 형태로 진화할 때가 됐는지 확인한다.
     *
     * <p><b>아기·성체 구분은 없다.</b> 성장도의 유일한 역할은 다음 종류로 진화하기까지
     * 걸리는 시간이다. <b>{@link PetType#growsToNextStage} 가 아닌 종류는 이 메서드가
     * 할 일이 없다.</b> 성장도 자체가 오르지 않으므로(={@link #growsOverTime})
     * {@code growth() < growthMax()} 조건에 항상 걸려 {@link StageResult#NONE} 만
     * 돌려준다 — 계속 {@link LifeStage#NORMAL} 로 남는다.
     *
     * <p>{@code growsToNextStage} 인 종류만 성장도가 상한에 닿을 때까지 기다린다. 그 순간
     * <b>다음 형태로 진화하는 대신 돼지가 될 수도 있다</b> — 포만도가 {@code min-fullness}
     * 이상이고 기믹이 켜져 있으면(그리고 {@code chance} 를 통과하면) {@link #becomePig} 로
     * 빠진다. 그러지 못했으면 {@code next-stage} 가중치로 종류를 다시 뽑고(자기 자신이
     * 나오면 "한 단계 더 기다린다") 성장도를 0부터 다시 채운다. <b>next-stage 가 없는
     * 종류는 자랄 기회 자체가 없으므로 이 경로로도 돼지가 되지 않는다</b> — 돼지가 되는
     * 건 진화가 갈라지는 그 순간뿐이지, 과급식 자체가 별도의 사건은 아니다.
     *
     * @return 이번 호출로 일어난 일. 아직 자랄 게 남았으면 {@link StageResult#NONE}
     */
    public StageResult promoteIfGrown(final PetData data) {
        return promoteIfGrown(data, types.apply(data.typeId()).orElse(null));
    }

    /** 종류를 이미 찾아둔 호출부용({@link #catchUp}). 같은 조회를 두 번 하지 않으려고 나눠 뒀다. */
    private StageResult promoteIfGrown(final PetData data, final PetType type) {
        if (data.stage() != LifeStage.NORMAL) {
            return StageResult.NONE;
        }
        if (type == null || !type.growsToNextStage() || data.growth() < type.growthMax()) {
            return StageResult.NONE;
        }

        if (tuning.overfeedGimmick() && meetsOverfeedFullness(data) && rollOverfeedChance()) {
            becomePig(data);
            return StageResult.BECAME_PIG;
        }

        final String nextTypeId = Weighted.pick(type.nextStage(), ThreadLocalRandom.current(), type.id());
        data.typeId(nextTypeId);
        data.growthStage(data.growthStage() + 1);
        // 성장도와 기준 시각은 항상 함께 갱신한다 — applyGrowth 가 그 계약을 지킨다.
        data.applyGrowth(new GrowthCurve.Projection(0, System.currentTimeMillis()));
        return StageResult.STAGE_UP;
    }

    /**
     * 과급식 이스터에그. 상태를 {@link LifeStage#PIG} 로 바꾸고, 설정에 따라 종류나
     * 모습(또는 둘 다)을 바꾼다.
     *
     * <p><b>둘은 서로 다른 일을 한다.</b> {@code becomes} 는 종류 자체를 다른 펫으로
     * 완전히 갈아끼운다(능력치·성장 상한·라이드 설정까지 전부 그 종류를 따른다).
     * {@code model} 은 종류는 그대로 두고 <b>모습만</b> 바꾼다 — 별도의 {@code pets/*.yml}
     * 없이 겉모습만 바꾸고 싶을 때 쓴다. 뭐가 안 통하든 기믹은 못 살려도 펫을 망가뜨리지는
     * 않는다(그 경우 기동 시 경고가 뜬다).
     *
     * <p>화면 반영은 호출부가 {@code PetService.refreshAfterGrowth} 로 마무리한다.
     * 소환 중인 개체는 소환 시점의 모델을 들고 있어서, 데이터만 바꾸면 화면이 안 바뀐다.
     */
    private void becomePig(final PetData data) {
        data.stage(LifeStage.PIG);
        final String becomes = tuning.overfeedBecomes();
        if (becomes != null && !becomes.isBlank() && types.apply(becomes).isPresent()) {
            data.typeId(becomes);
        }
        final String model = tuning.overfeedModel();
        if (model != null && !model.isBlank()) {
            data.modelOverride(model);
        }
    }

    /** 진화 순간의 포만도가 이 기준을 넘어야 돼지가 된다. 0 이면 조건 없음(항상 통과). */
    private boolean meetsOverfeedFullness(final PetData data) {
        return data.fullness() >= tuning.overfeedMinFullness();
    }

    /**
     * 조건을 다 채워도 이 확률로만 실제 발동한다. 100 이상이면 항상 발동, 0이면 절대
     * 발동하지 않는다. 실패하면 원래대로 next-stage 진화가 그대로 일어난다.
     */
    private boolean rollOverfeedChance() {
        final int chance = tuning.overfeedChance();
        if (chance >= 100) {
            return true;
        }
        if (chance <= 0) {
            return false;
        }
        return ThreadLocalRandom.current().nextInt(100) < chance;
    }

    /** 이번 급여로 오를 포만도. {@code min}~{@code max} 사이에서 고른다(양끝 포함). */
    private int randomFullnessGain() {
        final int min = tuning.fullnessMinGain();
        final int max = tuning.fullnessMaxGain();
        return min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public int maxOf(final PetData data) {
        return maxOf(types.apply(data.typeId()).orElse(null));
    }

    /** 종류를 이미 찾아둔 호출부용. 종류를 모르면 {@value #DEFAULT_GROWTH_MAX} 로 본다. */
    private int maxOf(final PetType type) {
        return type == null ? DEFAULT_GROWTH_MAX : type.growthMax();
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
        FED, STAGE_UP, BECAME_PIG,
        /** 포만도가 상한에 닿아 먹이를 거절했다. 성장도도 포만도도 안 바뀌었다. */
        TOO_FULL
    }

    /** {@link #promoteIfGrown} 의 결과. */
    public enum StageResult {
        /** 진화가 일어나지 않았다 — 아직 안 찼거나, 애초에 진화할 곳이 없는 종류다. */
        NONE,
        /** 다음 종류로 진화했다. */
        STAGE_UP,
        /** 진화하는 순간 다음 형태 대신 돼지가 됐다. */
        BECAME_PIG
    }
}
