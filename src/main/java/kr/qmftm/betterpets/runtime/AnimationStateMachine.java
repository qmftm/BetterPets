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

    private String current;

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

    private void apply(final String logical) {
        final String resolved = type.animations().resolve(logical);
        if (resolved.equals(current)) {
            return;     // ★ 변화가 없으면 아무것도 하지 않는다
        }
        if (current != null) {
            handle.stop(current);
        }
        handle.play(resolved, true);
        current = resolved;
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
            case FLY -> PetType.AnimationSet.FLY;
            case GROUND -> switch (state) {
                case RUN -> PetType.AnimationSet.RUN;
                case WALK -> PetType.AnimationSet.WALK;
                case IDLE, TELEPORT -> PetType.AnimationSet.IDLE;
            };
        };
    }
}
