package kr.qmftm.betterpets.event;

import kr.qmftm.betterpets.domain.PetData;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 펫에게 먹이를 줘서 실제로 성장도가 반영됐을 때(포만도가 꽉 차 거절된 경우는 제외)
 * 발생한다.
 *
 * <p>{@link kr.qmftm.betterpets.listener.InteractionListener} 의 급여 처리에서
 * {@code TOO_FULL} 이 아닌 결과(성장·진화·과급식 전부 포함)일 때 쏜다. 이미 끝난
 * 일을 알리는 통지라 취소할 수 없다.
 */
public final class PetFedEvent extends PlayerEvent implements PetEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PetData pet;

    public PetFedEvent(final Player owner, final PetData pet) {
        super(owner);
        this.pet = pet;
    }

    @Override
    public PetData pet() {
        return pet;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
