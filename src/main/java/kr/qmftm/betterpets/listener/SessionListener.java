package kr.qmftm.betterpets.listener;

import kr.qmftm.betterpets.runtime.ActivePet;
import kr.qmftm.betterpets.runtime.PetRegistry;
import kr.qmftm.betterpets.service.AbilityService;
import kr.qmftm.betterpets.service.GrowthCatchUp;
import kr.qmftm.betterpets.service.PetService;
import kr.qmftm.betterpets.storage.PetStore;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

/**
 * 접속·퇴장·월드 언로드에서 자원을 정리한다.
 *
 * <p>펫 정리가 필요한 네 경로 중 셋이 여기 있다 (나머지 하나는 서버 종료 —
 * 그건 플러그인 onDisable 이 맡는다). 하나라도 빠지면 유령 모델이 남는다.
 */
public final class SessionListener implements Listener {

    private final Plugin plugin;
    private final PetStore store;
    private final PetService pets;
    private final PetRegistry registry;
    private final AbilityService abilities;
    private final GrowthCatchUp catchUp;

    public SessionListener(final Plugin plugin,
                           final PetStore store,
                           final PetService pets,
                           final PetRegistry registry,
                           final AbilityService abilities,
                           final GrowthCatchUp catchUp) {
        this.plugin = plugin;
        this.store = store;
        this.pets = pets;
        this.registry = registry;
        this.abilities = abilities;
        this.catchUp = catchUp;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        // 서버가 비정상 종료됐다면 해제되지 않은 모디파이어가 남아 있다.
        // 데이터를 읽기 전에 무조건 걷어낸다 — 누적 버그의 마지막 방어선이다.
        abilities.purge(event.getPlayer());

        // 읽기가 끝나면 접속하지 않은 동안 흐른 시간을 반영한다. 성장도 자체는 지연
        // 계산이라 저절로 맞지만, 성체가 되는 것은 누가 확인해 줘야 일어나는 사건이다.
        // 확인하는 자리가 틱 루프뿐이었고 틱은 소환된 펫만 돈다 — 보관함에 넣어둔 펫이
        // 상한에 붙은 채 아기로 굳어 있던 이유다.
        final Player player = event.getPlayer();
        store.loadAsync(player.getUniqueId(), () -> {
            // loadAsync 의 콜백은 IO 스레드에서 돈다. Bukkit 을 건드리기 전에 넘어온다.
            if (!plugin.isEnabled()) {
                return;     // 종료 중이면 스케줄러가 거절한다
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    catchUp.all(player);
                }
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) {
        final var player = event.getPlayer();
        pets.dismissAll(player);                    // 트래커·캐리어·능력 정리
        pets.releaseQuietly(player.getUniqueId());  // 혹시 남았으면 한 번 더
        store.unload(player.getUniqueId());         // 변경분 저장 후 캐시에서 제거
    }

    /**
     * 월드가 내려가면 그 월드의 캐리어 엔티티도 사라진다.
     * 트래커는 그대로 남으므로 여기서 닫지 않으면 고아 스케줄이 된다.
     */
    @EventHandler
    public void onWorldUnload(final WorldUnloadEvent event) {
        final World world = event.getWorld();
        for (final ActivePet pet : registry.all()) {
            if (!world.equals(pet.carrier().getWorld())) {
                continue;
            }
            // 소유자가 접속 중이면 dismiss 로 내려야 능력 모디파이어까지 걷힌다.
            // registry.remove 만 부르면 펫 없는 플레이어에게 버프가 남는다.
            final Player owner = org.bukkit.Bukkit.getPlayer(pet.ownerId());
            if (owner != null && owner.isOnline()) {
                pets.dismiss(owner, pet.petId());
            } else {
                registry.remove(pet.ownerId(), pet.petId());
            }
        }
    }
}
