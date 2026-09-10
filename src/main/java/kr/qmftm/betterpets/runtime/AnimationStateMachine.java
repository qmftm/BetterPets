package kr.qmftm.betterpets.runtime;

import kr.qmftm.betterpets.domain.PetType;
import kr.qmftm.betterpets.render.PetRenderHandle;

/**
 * 이동 상태를 애니메이션으로 옮긴다.
 *
 * <p><b>이 클래스의 존재 이유는 단 한 줄이다</b> — {@code if (next == current) return;}
 *
 * <p>{@code animate()} 는 패킷을 만든다. 매 틱 부르면 동접에 비례해 트래픽이 터진다.
 * 원작이 렉으로 곤란을 겪은 지점이 여기다. 상태가 <b>바뀐 프레임에서만</b> 호출한다.
 */
public final class AnimationStateMachine {

    /** 오버레이 애니메이션의 우선순위. 이동 루프보다 높아야 위에 얹힌다. */
    private static final int OVERLAY_PRIORITY = 10;

    private final PetRenderHandle handle;
    private final PetType type;

    /** 지금 실제로 재생 중인 애니메이션. 모델에 없어서 못 걸었으면 비어 있다. */
    private String current;

    /**
     * 마지막으로 <b>걸어보려 한</b> 이름.
     *
     * <p>{@link #current} 와 나눠 두는 이유는 실패를 기억하기 위해서다. 모델에 없는
     * 애니메이션은 {@code play} 가 false 를 돌려주는데, 성공한 것만 기억하면 상태가
     * 그대로인 동안 <b>매 틱 다시 시도한다</b> — 이 클래스가 막으려던 바로 그 트래픽이다.
     */
    private String attempted;

    public AnimationStateMachine(final PetRenderHandle handle, final PetType type) {
        this.handle = handle;
        this.type = type;
    }

    /** 소환 직후 한 번. 기본 애니메이션을 건다. */
    public void start() {
        apply(baseFor(MovementController.Mode.GROUND, MovementController.State.IDLE));
    }

    public void tick(final MovementController.Mode mode,
                     final MovementController.State state) {
        apply(baseFor(mode, state));
    }

    /**
     * 논리 이름을 실제 애니메이션으로 옮겨 건다.
     *
     * <p><b>{@code play} 의 반환값을 버리면 안 된다.</b> 모델에 없는 애니메이션이면
     * false 가 오는데, 그걸 무시하고 {@code current} 에 이름을 넣어두면 아무것도
     * 재생되지 않는 채로 "재생 중"이라고 믿게 된다. {@code fly} 와 {@code ride} 는
     * 필수가 아니라서 없는 모델이 정상적으로 존재한다 — 흔히 일어날 수 있는 일이다.
     */
    private void apply(final String logical) {
        final String resolved = type.animations().resolve(logical);
        if (resolved.equals(attempted)) {
            return;     // ★ 변화가 없으면 아무것도 하지 않는다
        }
        attempted = resolved;

        if (current != null) {
            handle.stop(current);
            current = null;
        }
        if (handle.play(resolved, true)) {
            current = resolved;
            return;
        }
        // 모델에 없다. MODELING 이 약속한 대로 idle 로 접는다 —
        // 멈춰 선 것처럼 보이는 편이 아무것도 안 도는 것보다 낫다.
        final String fallback = type.animations().resolve(PetType.AnimationSet.IDLE);
        if (!fallback.equals(resolved) && handle.play(fallback, true)) {
            current = fallback;
        }
    }

    /**
     * 일회성 애니메이션을 이동 루프 위에 얹는다.
     *
     * <p>재생이 끝나면 콜백에서 아무것도 하지 않아도 된다 — 아래 루프가 계속 돌고 있으므로
     * 오버레이가 걷히면 자연히 원래 모습으로 돌아간다.
     */
    public void overlay(final String logical) {
        handle.playOverlay(type.animations().resolve(logical), OVERLAY_PRIORITY, null);
    }

    private String baseFor(final MovementController.Mode mode,
                           final MovementController.State state) {
        return switch (mode) {
            case RIDDEN -> PetType.AnimationSet.RIDE;
            case RIDDEN_FLYING -> PetType.AnimationSet.FLY;
            case GROUND -> switch (state) {
                case RUN -> PetType.AnimationSet.RUN;
                case WALK -> PetType.AnimationSet.WALK;
                case IDLE, TELEPORT -> PetType.AnimationSet.IDLE;
            };
        };
    }
}
