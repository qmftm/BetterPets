package kr.qmftm.betterpets.runtime;

import kr.qmftm.betterpets.domain.LifeStage;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.service.BroadcastService;
import kr.qmftm.betterpets.service.GrowthService;
import kr.qmftm.betterpets.service.GrowthService.StageResult;
import kr.qmftm.betterpets.service.PetService;
import kr.qmftm.betterpets.storage.PetStore;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * 전역 틱 루프.
 *
 * <p><b>펫마다 태스크를 만들지 않는다.</b> 태스크 수가 동접에 비례해 늘면 스케줄러가
 * 먼저 무너진다. 5마리든 100마리든 루프 하나로 도는 게 더 단순하기도 하다.
 *
 * <p>추종은 2틱(0.1초) 주기로 충분하지만, 탑승 조향은 매 틱 돌아야 조작이 뻑뻑하지 않다.
 * 그래서 두 태스크로 나눈다.
 */
public final class PetTicker {

    private static final long FOLLOW_PERIOD_TICKS = 2L;
    private static final long RIDE_PERIOD_TICKS = 1L;

    private final Plugin plugin;
    private final PetRegistry registry;
    private final RideController rides;
    private final GrowthService growth;
    private final PetStore store;
    private final PetService pets;
    private final BroadcastService broadcasts;

    private BukkitTask followTask;
    private BukkitTask rideTask;

    public PetTicker(final Plugin plugin,
                     final PetRegistry registry,
                     final RideController rides,
                     final GrowthService growth,
                     final PetStore store,
                     final PetService pets,
                     final BroadcastService broadcasts) {
        this.plugin = plugin;
        this.registry = registry;
        this.rides = rides;
        this.growth = growth;
        this.store = store;
        this.pets = pets;
        this.broadcasts = broadcasts;
    }

    public void start() {
        followTask = plugin.getServer().getScheduler()
            .runTaskTimer(plugin, this::tickFollow, 20L, FOLLOW_PERIOD_TICKS);
        rideTask = plugin.getServer().getScheduler()
            .runTaskTimer(plugin, this::tickRides, 20L, RIDE_PERIOD_TICKS);
    }

    public void stop() {
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
        if (rideTask != null) {
            rideTask.cancel();
            rideTask = null;
        }
    }

    private void tickFollow() {
        registry.forEach(pet -> {
            final Player owner = plugin.getServer().getPlayer(pet.ownerId());

            // 소유자가 없으면 소환된 채로 둘 이유가 없다. 즉시 정리한다.
            if (owner == null || !owner.isOnline()) {
                // 소유자가 나갔다. 능력 모디파이어는 다음 접속의 purge 가 걷는다.
                registry.remove(pet.ownerId(), pet.petId());
                return;
            }
            // 캐리어가 어떤 이유로든 사라졌다(청크 언로드, 외부 플러그인).
            if (pet.isClosed() || pet.carrier().isDead() || !pet.carrier().isValid()) {
                // 여기서 registry.remove 로 끝내면 안 된다. 소유자가 접속 중인데
                // 능력을 떼지 않아, 펫 없는 플레이어에게 버프가 남는다. 탑승 중이었다면
                // 마운트도 정리해야 한다. dismiss 가 그 순서를 지킨다.
                pets.dismiss(owner, pet.petId());
                return;
            }
            // 월드가 갈리면 추종으로는 못 따라간다. 즉시 옮긴다.
            if (!owner.getWorld().equals(pet.carrier().getWorld())) {
                pet.carrier().teleport(owner.getLocation());
                return;
            }

            applyTimeGrowth(owner, pet.data());
            pet.tick(owner);
        });
    }

    /**
     * 탑승 조향. 매 틱 돈다.
     *
     * <p><b>소환된 펫이 아니라 타고 있는 사람을 훑는다.</b> 탑승자는 보통 0~1명인데
     * 소환된 펫은 그보다 훨씬 많다 — 펫을 훑으면 매 틱 전부 확인하고 대부분 건너뛴다.
     * 아무도 안 타고 있으면 이 루프는 한 바퀴도 돌지 않는다.
     */
    private void tickRides() {
        for (final UUID riderId : rides.riderIds()) {
            final Player owner = plugin.getServer().getPlayer(riderId);
            if (owner == null) {
                continue;
            }
            final RideController.Ride ride = rides.rideOf(owner);
            if (ride == null) {
                continue;
            }
            final ActivePet pet = registry.of(riderId, ride.petId()).orElse(null);
            if (pet == null) {
                // 탄 펫이 사라졌다. 공중에 남기지 않고 내려준다.
                rides.stop(owner);
                continue;
            }
            if (rides.tick(owner)) {
                // 탑승 중에는 펫 본체가 마운트를 밀착 추적한다.
                pet.carrier().teleport(ride.mount().getLocation());
            } else {
                // 하차했다. 추종으로 되돌린다.
                pet.movement().mode(MovementController.Mode.GROUND);
            }
        }
    }

    /**
     * 시간 경과분 성장도를 반영한다.
     *
     * <p>지연 계산이라 여기서 하는 일은 값 갱신뿐이고, 주기적 DB 쓰기가 없다.
     * 성장이 실제로 일어났을 때만 저장을 건다.
     */
    private void applyTimeGrowth(final Player owner, final PetData data) {
        if (data.stage() != LifeStage.BABY) {
            return;     // 성체와 돼지는 더 자라지 않는다
        }
        final int before = data.growth();
        growth.refresh(data);
        if (data.growth() == before) {
            return;
        }
        store.saveAsync(data);

        final GrowthService.StageResult result = growth.promoteIfGrown(data);
        if (result == StageResult.NONE) {
            return;     // 성장도만 올랐다. 화면에 바뀔 것이 없다
        }
        store.saveAsync(data);

        // 종류가 바뀌었으면 모델을, 성체가 됐으면 능력을 지금 상태에 맞춘다.
        // 성장은 재소환 없이 일어나므로 이 마무리가 없으면 데이터만 바뀐다.
        pets.refreshAfterGrowth(owner, data);

        // 먹여서 자란 경우는 InteractionListener 가 알린다. 시간이 흘러 자란 경우가
        // 여기다 — 두 경로 모두 알려야 "가만히 뒀더니 조용히 성체가 됐다"가 없다.
        pets.catalog().type(data.typeId()).ifPresent(type -> {
            if (result == GrowthService.StageResult.GREW_UP) {
                broadcasts.onGrown(owner, data, type);
            } else {
                broadcasts.onStageUp(owner, data, type);
            }
        });
    }
}
