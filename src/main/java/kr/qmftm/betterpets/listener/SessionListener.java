package kr.qmftm.betterpets.listener;

import kr.qmftm.betterpets.runtime.ActivePet;
import kr.qmftm.betterpets.runtime.PetRegistry;
import kr.qmftm.betterpets.service.AbilityService;
import kr.qmftm.betterpets.service.PetService;
import kr.qmftm.betterpets.storage.PetStore;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/**
 * 접속·퇴장·월드 언로드에서 자원을 정리한다.
 *
 * <p>펫 정리가 필요한 네 경로 중 셋이 여기 있다 (나머지 하나는 서버 종료 —
 * 그건 플러그인 onDisable 이 맡는다). 하나라도 빠지면 유령 모델이 남는다.
 */
public final class SessionListener implements Listener {

    private final PetStore store;
    private final PetService pets;
    private final PetRegistry registry;
    private final AbilityService abilities;

    public SessionListener(final PetStore store,
                           final PetService pets,
                           final PetRegistry registry,
                           final AbilityService abilities) {
        this.store = store;
        this.pets = pets;
        this.registry = registry;
        this.abilities = abilities;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        // 서버가 비정상 종료됐다면 해제되지 않은 모디파이어가 남아 있다.
        // 데이터를 읽기 전에 무조건 걷어낸다 — 누적 버그의 마지막 방어선이다.
        abilities.purge(event.getPlayer());
        store.loadAsync(event.getPlayer().getUniqueId(), null);
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
            if (world.equals(pet.carrier().getWorld())) {
                registry.remove(pet.ownerId(), pet.petId());
            }
        }
    }
}
