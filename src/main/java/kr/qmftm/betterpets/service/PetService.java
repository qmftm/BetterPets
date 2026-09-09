package kr.qmftm.betterpets.service;

import kr.qmftm.betterpets.config.PetCatalog;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetType;
import kr.qmftm.betterpets.render.PetRenderHandle;
import kr.qmftm.betterpets.render.PetRenderer;
import kr.qmftm.betterpets.runtime.ActivePet;
import kr.qmftm.betterpets.runtime.CarrierFactory;
import kr.qmftm.betterpets.runtime.PetRegistry;
import kr.qmftm.betterpets.runtime.RideController;
import kr.qmftm.betterpets.storage.PetStore;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * 펫 소환·해제·획득·해방. 플러그인의 중심 서비스다.
 *
 * <p>소환은 여러 자원을 연달아 잡는다(캐리어 엔티티 → 렌더 트래커 → 능력). 중간에 실패하면
 * <b>이미 잡은 것을 반드시 되돌린다</b> — 안 그러면 유령 엔티티나 트래커가 남는다.
 */
public final class PetService {

    private final PetCatalog catalog;
    private final PetStore store;
    private final PetRenderer renderer;
    private final CarrierFactory carriers;
    private final PetRegistry registry;
    private final RideController rides;
    private final AbilityService abilities;
    private final GrowthService growth;

    public PetService(final PetCatalog catalog,
                      final PetStore store,
                      final PetRenderer renderer,
                      final CarrierFactory carriers,
                      final PetRegistry registry,
                      final RideController rides,
                      final AbilityService abilities,
                      final GrowthService growth) {
        this.catalog = catalog;
        this.store = store;
        this.renderer = renderer;
        this.carriers = carriers;
        this.registry = registry;
        this.rides = rides;
        this.abilities = abilities;
        this.growth = growth;
    }

    public enum SummonResult {
        OK,
        UNKNOWN_TYPE,
        MODEL_MISSING
    }

    /**
     * 펫을 소환한다. 이미 소환 중이면 교체한다.
     */
    public SummonResult summon(final Player owner, final PetData data) {
        final PetType type = catalog.type(data.typeId()).orElse(null);
        if (type == null) {
            return SummonResult.UNKNOWN_TYPE;
        }
        growth.refresh(data);

        dismiss(owner);     // 기존 것을 먼저 정리한다. 교체 시 흘리지 않기 위해서다

        final Location at = spawnLocation(owner);
        final Mob carrier = carriers.spawn(at, data.petId());

        final Optional<PetRenderHandle> handle = renderer.attach(carrier, type.modelId());
        if (handle.isEmpty()) {
            // 모델이 없다. 이미 스폰한 캐리어를 되돌린다.
            carrier.remove();
            return SummonResult.MODEL_MISSING;
        }

        final ActivePet pet = new ActivePet(owner.getUniqueId(), data, type, carrier, handle.get());
        pet.applyRarityTint();
        registry.put(pet);

        markActive(owner, data);
        abilities.equip(owner, data, type);
        return SummonResult.OK;
    }

    /** 소환 해제. 소환 중이 아니면 아무 일도 하지 않는다. */
    public boolean dismiss(final Player owner) {
        final Optional<ActivePet> current = registry.of(owner.getUniqueId());
        if (current.isEmpty()) {
            return false;
        }
        final ActivePet pet = current.get();

        // 순서가 중요하다. 탑승 중이면 먼저 내려야 플레이어가 공중에 남지 않는다.
        if (rides.isRiding(owner)) {
            rides.stop(owner);
        }
        abilities.unequip(owner, pet.data(), pet.type());
        pet.data().active(false);
        store.saveAsync(pet.data());

        registry.remove(owner.getUniqueId());
        return true;
    }

    /** 퇴장·종료 경로. 플레이어 객체 없이도 정리할 수 있어야 한다. */
    public void releaseQuietly(final UUID ownerId) {
        registry.remove(ownerId);
    }

    /** 펫을 지급한다. 알 아이템을 깠을 때와 관리자 지급이 같은 경로를 탄다. */
    public PetData grantPet(final Player owner, final String typeId) {
        final PetData data = PetData.newBaby(owner.getUniqueId(), typeId, System.currentTimeMillis());
        store.add(data);
        return data;
    }

    /** 펫을 놓아준다. 소환 중이면 먼저 해제한다. */
    public void release(final Player owner, final PetData data) {
        if (data.active()) {
            dismiss(owner);
        }
        store.remove(data);
    }

    /**
     * 소환 중인 펫의 종류가 바뀌었으면 모델을 다시 붙인다.
     *
     * <p><b>이게 없으면 종류 변경이 화면에 반영되지 않는다.</b> {@link ActivePet} 은 소환
     * 시점의 {@link PetType} 을 붙들고 있어서, {@code data.typeId()} 만 바뀌면 예전 모델과
     * 예전 애니메이션 이름을 계속 쓴다. 성장 단계 진화와 과급식 변신 두 경로 모두
     * 이 마무리가 필요하다.
     *
     * <p>탑승 중이었다면 {@link #summon} 안의 {@link #dismiss} 가 안전하게 내려준다 —
     * 드래곤이 돼지가 됐는데 그대로 하늘에 떠 있으면 곤란하다.
     *
     * @return 모델을 다시 붙였으면 true
     */
    public boolean refreshIfTypeChanged(final Player owner, final PetData data) {
        final ActivePet current = registry.of(owner.getUniqueId()).orElse(null);
        if (current == null || !current.data().petId().equals(data.petId())) {
            return false;   // 소환 중이 아니거나, 소환된 건 다른 펫이다
        }
        if (current.type().id().equals(data.typeId())) {
            return false;   // 종류가 그대로다
        }
        return summon(owner, data) == SummonResult.OK;
    }

    /** 소유자당 활성 펫은 하나. DB 제약 대신 여기서 강제한다. */
    private void markActive(final Player owner, final PetData data) {
        for (final PetData other : store.owned(owner.getUniqueId())) {
            if (!other.petId().equals(data.petId()) && other.active()) {
                other.active(false);
                store.saveAsync(other);
            }
        }
        data.active(true);
        store.saveAsync(data);
    }

    private Location spawnLocation(final Player owner) {
        final Location base = owner.getLocation();
        final var behind = base.getDirection().setY(0);
        if (behind.lengthSquared() < 1.0e-4) {
            behind.setX(0).setZ(1);
        }
        return base.clone().add(behind.normalize().multiply(-1.5));
    }

    public PetCatalog catalog() {
        return catalog;
    }

    public GrowthService growth() {
        return growth;
    }
}
