package kr.qmftm.betterpets.event;

import kr.qmftm.betterpets.domain.PetData;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 펫이 소환 해제될 때(보관함 소환 해제 버튼, {@code /pet dismiss}, 퇴장) 발생한다.
 *
 * <p>{@link kr.qmftm.betterpets.service.PetService#dismiss} 하나로 모인다 —
 * {@code dismissAll}·재소환 시의 자동 정리·{@code release}(놓아주기 전에 먼저
 * 해제한다)까지 전부 이 메서드를 거치므로, 여기서 한 번만 쏘면 다 잡는다.
 * 이미 끝난 일을 알리는 통지라 취소할 수 없다.
 */
public final class PetDismissedEvent extends PlayerEvent implements PetEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PetData pet;

    public PetDismissedEvent(final Player owner, final PetData pet) {
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
