package kr.qmftm.betterpets.event;

import kr.qmftm.betterpets.domain.PetData;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 펫이 소환될 때(보관함 소환 버튼, {@code /pet summon}) 발생한다.
 *
 * <p>{@link kr.qmftm.betterpets.service.PetService#summon} 성공 경로에서만 쏜다 —
 * 모델을 못 찾거나 종류를 알 수 없어 실패했을 때는 안 쏜다. 이미 끝난 일을 알리는
 * 통지라 취소할 수 없다.
 */
public final class PetSummonedEvent extends PlayerEvent implements PetEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PetData pet;

    public PetSummonedEvent(final Player owner, final PetData pet) {
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
