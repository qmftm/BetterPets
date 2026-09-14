package kr.qmftm.betterpets.event;

import kr.qmftm.betterpets.domain.PetData;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 펫을 놓아줬을 때({@code /pet release}) 발생한다 — "작별".
 *
 * <p>소환 중이었다면 {@link PetDismissedEvent} 가 먼저 나가고(놓아주기 전에 먼저
 * 해제한다), 그다음 이 이벤트가 나간다. {@link kr.qmftm.betterpets.service.PetService#release}
 * 는 <b>되돌릴 수 없는</b> 유일한 조작이다 — 이미 끝난 일을 알리는 통지라 이 이벤트도
 * 취소할 수 없다.
 */
public final class PetReleasedEvent extends PlayerEvent implements PetEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PetData pet;

    public PetReleasedEvent(final Player owner, final PetData pet) {
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
