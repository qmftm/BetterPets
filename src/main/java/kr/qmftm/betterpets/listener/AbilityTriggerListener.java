package kr.qmftm.betterpets.listener;

import kr.qmftm.betterpets.runtime.PetRegistry;
import kr.qmftm.betterpets.service.AbilityService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * TRIGGER 능력에 이벤트를 전달한다.
 *
 * <p>능력 구현체가 자기가 관심 있는 이벤트만 처리하므로, 여기서는 소환된 펫이 있는지만
 * 확인하고 넘긴다.
 */
public final class AbilityTriggerListener implements Listener {

    private final PetRegistry registry;
    private final AbilityService abilities;

    public AbilityTriggerListener(final PetRegistry registry, final AbilityService abilities) {
        this.registry = registry;
        this.abilities = abilities;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(final EntityDeathEvent event) {
        final Player killer = event.getEntity().getKiller();
        if (killer != null) {
            dispatch(killer, event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(final EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim) {
            dispatch(victim, event);
        }
    }

    /**
     * 소환 중인 펫 전부에게 넘긴다. 여러 마리를 데리고 다닐 수 있어서다.
     *
     * <p>전투 중이면 피격 이벤트마다 들어온다. 목록을 복사하지 않는 순회를 쓴다.
     */
    private void dispatch(final Player player, final org.bukkit.event.Event event) {
        registry.forEachOf(player.getUniqueId(),
            pet -> abilities.dispatch(player, pet.data(), pet.type(), event));
    }
}
