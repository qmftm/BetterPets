package kr.qmftm.betterpets.runtime;

import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetType;
import kr.qmftm.betterpets.render.PetRenderHandle;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 소환되어 월드에 존재하는 펫 하나.
 *
 * <p>캐리어 엔티티 · 렌더 핸들 · 이동/애니메이션 상태를 한 덩어리로 묶는다.
 *
 * <p><b>{@link #close()} 가 이 클래스에서 가장 중요하다.</b> 트래커를 닫지 않으면 유령 모델과
 * 고아 스케줄 태스크가 남는다. 소유자 퇴장 · 서버 종료 · 월드 언로드 · 캐리어 사망
 * 네 경로 모두에서 불려야 한다.
 */
public final class ActivePet implements AutoCloseable {

    private final UUID ownerId;
    private final PetData data;
    private final PetType type;
    private final Mob carrier;
    private final PetRenderHandle handle;
    private final MovementController movement;
    private final AnimationStateMachine animation;

    private boolean closed;

    public ActivePet(final UUID ownerId,
                     final PetData data,
                     final PetType type,
                     final Mob carrier,
                     final PetRenderHandle handle) {
        this.ownerId = ownerId;
        this.data = data;
        this.type = type;
        this.carrier = carrier;
        this.handle = handle;
        this.movement = new MovementController(carrier, type);
        this.animation = new AnimationStateMachine(handle, type);
        this.animation.start();
    }

    public UUID ownerId() { return ownerId; }
    /** 이 개체의 펫 id. 레지스트리가 소유자별로 여러 마리를 구분하는 키다. */
    public UUID petId() { return data.petId(); }
    public PetData data() { return data; }
    public PetType type() { return type; }
    public Mob carrier() { return carrier; }
    public MovementController movement() { return movement; }
    public AnimationStateMachine animation() { return animation; }
    public boolean isClosed() { return closed; }

    public void tick(final Player owner) {
        if (closed) {
            return;
        }
        movement.tick(owner);
        animation.tick(movement.mode(), movement.state());
    }

    /**
     * 정리. 여러 번 불려도 안전하다 — 퇴장과 월드 언로드가 겹치는 경우가 실제로 있다.
     *
     * <p>순서가 중요하다. 트래커를 먼저 닫아야 캐리어가 사라진 뒤에도 엔진이
     * 죽은 엔티티를 참조하지 않는다.
     *
     * <p>여기서 {@code active} 를 내리는 이유는, 레지스트리가
     * {@code PetService.dismiss} 를 거치지 않고 바로 정리하는 경로가 있기 때문이다
     * (캐리어 사망, 월드 언로드, 서버 종료). 그 경로를 빼먹으면 소환되지도 않은 펫이
     * 보관함에 계속 "소환 중"으로 보인다. 모든 정리가 지나가는 길목이 여기다.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        data.active(false);
        handle.close();
        if (!carrier.isDead()) {
            carrier.remove();
        }
    }
}
