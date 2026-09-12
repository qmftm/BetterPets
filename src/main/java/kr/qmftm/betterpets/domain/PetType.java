package kr.qmftm.betterpets.domain;

import kr.qmftm.betterpets.ability.AbilityDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 펫 "종류" 정의. {@code pets/*.yml} 에서 로드하는 불변 객체다.
 *
 * <p>개체({@link PetData})와 구분한다 — 같은 종류의 펫을 여러 마리 가질 수 있고,
 * 개체마다 레벨과 이름이 다르다.
 *
 * @param stats     이 펫의 등급 수치. {@code rarity.yml} 에서 온 값을 로드 시점에
 *                  박아 넣는다. {@code rarity()} 로 표를 다시 찾지 않는 이유는, 그러면
 *                  수치를 읽는 모든 자리에 표를 들고 다녀야 하기 때문이다 — 이동
 *                  컨트롤러, 능력, GUI, 알림. 리로드하면 어차피 펫 정의를 통째로 다시
 *                  만드므로 값이 굳어 있어도 문제없다
 * @param flyChance {@code ride} 가 {@link RideMode#FLY} 일 때만 의미가 있다
 * @param rideSpeed    등급별 기본값을 이 펫만 다르게 쓰고 싶을 때 {@code pets/*.yml} 의
 *                     {@code ride-speed} 로 덮어쓴다. {@code fly-chance} 와 같은 자리다
 * @param iconMaterial 보관함 아이콘 재질. {@code pets/*.yml} 의 {@code icon} 으로 정한다 —
 *                     안 적으면 {@code LEAD}
 * @param nextStage    비어 있으면 다음 단계에서도 같은 종류를 유지한다
 */
public record PetType(
    String id,
    String displayName,
    String modelId,
    Rarity rarity,
    RarityStats stats,
    int growthMax,
    RideMode ride,
    double flyChance,
    double rideSpeed,
    String iconMaterial,
    AnimationSet animations,
    MovementProfile movement,
    List<AbilityDefinition> abilities,
    Map<String, Integer> nextStage,
    int gachaWeight
) {

    public PetType {
        abilities = List.copyOf(abilities);
        // Map.copyOf 는 아니다 — Weighted.pick 이 순서에 따라 구간을 나누므로,
        // 설정 파일에 적은 순서를 그대로 지켜야 재현 가능하다. EggDefinition 과 동일한 이유.
        nextStage = Collections.unmodifiableMap(new LinkedHashMap<>(nextStage));
        growthMax = Math.max(1, growthMax);
        flyChance = Math.max(0.0, Math.min(1.0, flyChance));
        rideSpeed = Math.max(0.01, rideSpeed);
    }

    /**
     * 다음 성장 단계에서 종류가 바뀔 수 있는가.
     *
     * <p>{@code false} 면 성장 단계가 올라도 같은 종류를 유지한 채 성장도만 다시 채운다.
     */
    public boolean hasNextStage() {
        return !nextStage.isEmpty();
    }

    /** 비행 추첨을 돌려야 하는 종류인가. {@link RideMode#FLY} 가 아니면 굴릴 이유가 없다. */
    public boolean rollsFlight() {
        return ride == RideMode.FLY;
    }

    /**
     * 논리 애니메이션 이름 → 모델의 실제 애니메이션 이름 매핑.
     *
     * <p>플러그인은 {@code idle} · {@code walk} 같은 논리 이름으로 말하고, 모델 제작자는
     * 원하는 이름을 쓸 수 있게 한다. 매핑이 없으면 논리 이름을 그대로 쓴다.
     */
    public record AnimationSet(Map<String, String> mapping) {

        public static final String IDLE = "idle";
        public static final String WALK = "walk";
        public static final String RUN = "run";
        public static final String FLY = "fly";
        public static final String RIDE = "ride";
        public static final String EAT = "eat";

        /**
         * 플러그인이 아는 논리 이름.
         *
         * <p>{@code animations:} 의 <b>키</b>는 "바꿀 이름"이 아니라 "바꿀 대상"이다.
         * 거기에 오타를 내면 매핑이 조용히 무시되고, 증상은 "이름을 바꿨는데 안 먹는다"로
         * 나온다 — 원인에서 한참 떨어진 자리다. 로드할 때 걸러내려고 목록을 둔다.
         *
         * <p>뒤 넷은 <b>아직 재생되지 않지만</b> MODELING 이 자리를 잡아둔 이름이라
         * 오타로 치지 않는다. 미리 만들어 둔 사람에게 경고를 띄울 이유가 없다.
         */
        public static final Set<String> KNOWN = Set.of(
            IDLE, WALK, RUN, RIDE, FLY, EAT,
            "fly_idle", "sit", "attack", "spawn");

        public AnimationSet {
            mapping = Map.copyOf(mapping);
        }

        public static AnimationSet defaults() {
            return new AnimationSet(Map.of());
        }

        public String resolve(final String logical) {
            return mapping.getOrDefault(logical, logical);
        }
    }

    /** 추종 이동 파라미터. 등급 배율은 여기에 곱해진다. */
    public record MovementProfile(
        double followDistance,
        double walkSpeed,
        double runSpeed,
        double teleportDistance
    ) {
        /**
         * 설정값을 쓸 수 있는 범위로 접는다.
         *
         * <p>0이나 음수가 들어오면 <b>증상이 원인에서 아주 멀다.</b> 음수 속도는 펫을
         * 주인 반대쪽으로 밀어내고, {@code teleport-distance: 0} 은 매 틱 텔레포트가
         * 된다 — 원작이 렉으로 무너진 바로 그 종류의 일이다. 설정 실수 하나로 서버가
         * 느려지게 두지 않는다.
         *
         * <p>달리기가 걷기보다 느린 것도 접는다. 뜻이 없는 조합이고, 그대로 두면
         * "멀어질수록 느려지는" 펫이 된다.
         */
        public MovementProfile {
            followDistance = Math.max(0.5, followDistance);
            walkSpeed = Math.max(0.01, walkSpeed);
            runSpeed = Math.max(walkSpeed, runSpeed);
            teleportDistance = Math.max(4.0, teleportDistance);
        }

        public static MovementProfile defaults() {
            return new MovementProfile(2.0, 0.25, 0.45, 24.0);
        }
    }
}
