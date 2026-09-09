package kr.qmftm.betterpets.runtime;

import kr.qmftm.betterpets.domain.GrowthCurve;
import kr.qmftm.betterpets.domain.LifeStage;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.service.GrowthService;
import kr.qmftm.betterpets.service.PetService;
import kr.qmftm.betterpets.storage.PetStore;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

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

    private BukkitTask followTask;
    private BukkitTask rideTask;

    public PetTicker(final Plugin plugin,
                     final PetRegistry registry,
                     final RideController rides,
                     final GrowthService growth,
                     final PetStore store,
                     final PetService pets) {
        this.plugin = plugin;
        this.registry = registry;
        this.rides = rides;
        this.growth = growth;
        this.store = store;
        this.pets = pets;
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
        for (final ActivePet pet : registry.all()) {
            final Player owner = plugin.getServer().getPlayer(pet.ownerId());

            // 소유자가 없으면 소환된 채로 둘 이유가 없다. 즉시 정리한다.
            if (owner == null || !owner.isOnline()) {
                registry.remove(pet.ownerId(), pet.petId());
                continue;
            }
            if (pet.isClosed()) {
                registry.remove(pet.ownerId(), pet.petId());
                continue;
            }
            // 캐리어가 어떤 이유로든 사라졌다면(청크 언로드, 외부 플러그인) 정리한다.
            if (pet.carrier().isDead() || !pet.carrier().isValid()) {
                registry.remove(pet.ownerId(), pet.petId());
                continue;
            }
            // 월드가 갈리면 추종으로는 못 따라간다. 즉시 옮긴다.
            if (!owner.getWorld().equals(pet.carrier().getWorld())) {
                pet.carrier().teleport(owner.getLocation());
                continue;
            }

            applyTimeGrowth(owner, pet.data());
            pet.tick(owner);
        }
    }

    private void tickRides() {
        for (final ActivePet pet : registry.all()) {
            final Player owner = plugin.getServer().getPlayer(pet.ownerId());
            if (owner == null) {
                continue;
            }
            // 여러 마리를 소환해 뒀을 수 있다. 실제로 타고 있는 그 한 마리만 처리한다 —
            // 이 확인이 없으면 같이 나와 있는 펫들이 전부 마운트로 순간이동한다.
            if (!rides.isRiding(owner, pet.petId())) {
                continue;
            }
            if (rides.tick(owner)) {
                // 탑승 중에는 펫 본체가 마운트를 밀착 추적한다.
                pet.carrier().teleport(rides.rideOf(owner).mount().getLocation());
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

        final String typeBefore = data.typeId();
        growth.promoteIfGrown(data);

        // 성장 단계 진화로 종류가 바뀌었으면 모델을 갈아끼운다. 이걸 빼면 데이터만
        // 바뀌고 화면은 예전 모습 그대로다.
        if (!typeBefore.equals(data.typeId())) {
            store.saveAsync(data);
            pets.refreshIfTypeChanged(owner, data);
        }
    }

    /** 성장도 지연 계산의 상수를 노출한다. GUI 에서 "다음 성장까지"를 보여줄 때 쓴다. */
    public static long millisPerGrowthPoint() {
        return GrowthCurve.MILLIS_PER_POINT;
    }
}
