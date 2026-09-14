package kr.qmftm.betterpets.event;

import kr.qmftm.betterpets.domain.PetData;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 펫을 새로 얻었을 때(알을 까거나 관리자가 지급했을 때) 발생한다.
 *
 * <p>{@link kr.qmftm.betterpets.service.PetService#grantPet} 하나로 알 우클릭과
 * {@code /betterpets give}·{@code egg} 지급이 전부 모이므로, 그 자리에서 한 번만
 * 쏘면 모든 획득 경로를 다 잡는다.
 *
 * <p>이미 지급이 끝난 뒤에 알리는 <b>결과 통지</b>라 취소할 수 없다 — 취소하고 싶은
 * 지점(보유 한도 등)은 {@code grantPet} 자신이 그 전에 이미 판단한다.
 */
public final class PetObtainedEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PetData pet;

    public PetObtainedEvent(final Player owner, final PetData pet) {
        super(owner);
        this.pet = pet;
    }

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
