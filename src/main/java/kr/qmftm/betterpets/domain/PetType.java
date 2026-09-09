package kr.qmftm.betterpets.domain;

import kr.qmftm.betterpets.ability.AbilityDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 펫 "종류" 정의. {@code pets/*.yml} 에서 로드하는 불변 객체다.
 *
 * <p>개체({@link PetData})와 구분한다 — 같은 종류의 펫을 여러 마리 가질 수 있고,
 * 개체마다 레벨과 이름이 다르다.
 */
public record PetType(
    String id,
    String displayName,
    String modelId,
    String eggModelId,          // nullable — 없으면 본 모델의 egg 애니메이션을 쓴다
    Rarity rarity,
    int growthMax,
    boolean rideable,
    double flyChance,
    AnimationSet animations,
    MovementProfile movement,
    List<AbilityDefinition> abilities,
    Map<String, Integer> nextStage,   // 비어 있으면 다음 단계에서도 같은 종류를 유지한다
    int gachaWeight
) {

    public PetType {
        abilities = List.copyOf(abilities);
        // Map.copyOf 는 아니다 — Weighted.pick 이 순서에 따라 구간을 나누므로,
        // 설정 파일에 적은 순서를 그대로 지켜야 재현 가능하다. EggDefinition 과 동일한 이유.
        nextStage = Collections.unmodifiableMap(new LinkedHashMap<>(nextStage));
        growthMax = Math.max(1, growthMax);
        flyChance = Math.max(0.0, Math.min(1.0, flyChance));
    }

    /** 알 상태에 쓸 모델. 별도 지정이 없으면 본 모델을 그대로 쓴다. */
    public String eggOrBaseModel() {
        return eggModelId == null || eggModelId.isBlank() ? modelId : eggModelId;
    }

    /**
     * 다음 성장 단계에서 종류가 바뀔 수 있는가.
     *
     * <p>{@code false} 면 성장 단계가 올라도 같은 종류를 유지한 채 성장도만 다시 채운다.
     */
    public boolean hasNextStage() {
        return !nextStage.isEmpty();
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
        public static final String EGG = "egg";
        public static final String HATCH = "hatch";
        public static final String EAT = "eat";

        /** 이게 없으면 펫이 정지 상태로만 보인다. 로드 시 경고 대상. */
        public static final List<String> REQUIRED = List.of(IDLE, WALK, RUN);

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
        public static MovementProfile defaults() {
            return new MovementProfile(2.0, 0.25, 0.45, 24.0);
        }
    }
}
