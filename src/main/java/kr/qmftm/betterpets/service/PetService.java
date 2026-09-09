package kr.qmftm.betterpets.service;

import kr.qmftm.betterpets.config.PetCatalog;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetLimits;
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

import java.util.List;
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
    private final PetLimits limits;

    public PetService(final PetCatalog catalog,
                      final PetStore store,
                      final PetRenderer renderer,
                      final CarrierFactory carriers,
                      final PetRegistry registry,
                      final RideController rides,
                      final AbilityService abilities,
                      final GrowthService growth,
                      final PetLimits limits) {
        this.catalog = catalog;
        this.store = store;
        this.renderer = renderer;
        this.carriers = carriers;
        this.registry = registry;
        this.rides = rides;
        this.abilities = abilities;
        this.growth = growth;
        this.limits = limits;
    }

    /** 설정된 보유·동시 소환 한도. GUI 표시와 지급 판정이 같은 값을 본다. */
    public PetLimits limits() {
        return limits;
    }

    public enum SummonResult {
        OK,
        /** 소환했지만 동시 소환 한도가 차 있어서 가장 오래된 펫을 돌려보냈다. */
        OK_REPLACED,
        UNKNOWN_TYPE,
        MODEL_MISSING
    }

    /**
     * 펫을 소환한다.
     *
     * <p>동시 소환 한도({@code pets.max-active})가 차 있으면 <b>가장 먼저 소환했던 펫을
     * 돌려보내고</b> 자리를 만든다. 거절하지 않는 이유는 기본값이 1이기 때문이다 —
     * 거절하면 한 마리만 두고 쓰는 서버에서 소환할 때마다 먼저 해제해야 한다.
     * 한도를 올린 서버에서도 같은 규칙(오래된 것부터)이 그대로 적용된다.
     */
    public SummonResult summon(final Player owner, final PetData data) {
        final PetType type = catalog.type(data.typeId()).orElse(null);
        if (type == null) {
            return SummonResult.UNKNOWN_TYPE;
        }
        growth.refresh(data);

        // 같은 펫을 다시 소환하는 경우(종류 변경 등)는 자리를 새로 차지하지 않는다.
        // 먼저 정리해야 트래커와 캐리어를 흘리지 않는다.
        dismiss(owner, data.petId());

        final Location at = spawnLocation(owner);
        final Mob carrier = carriers.spawn(at, data.petId());

        final Optional<PetRenderHandle> handle = renderer.attach(carrier, type.modelId());
        if (handle.isEmpty()) {
            // 모델이 없다. 이미 스폰한 캐리어를 되돌린다.
            carrier.remove();
            return SummonResult.MODEL_MISSING;
        }

        // 자리 만들기는 모델이 붙은 뒤에 한다. 먼저 비웠다가 모델이 없어 실패하면
        // 플레이어는 멀쩡히 나와 있던 펫만 잃는다.
        boolean replaced = false;
        if (!limits.canSummonMore(registry.countOf(owner.getUniqueId()))) {
            final List<ActivePet> current = registry.allOf(owner.getUniqueId());
            if (!current.isEmpty()) {
                dismiss(owner, current.getFirst().petId());
                replaced = true;
            }
        }

        final ActivePet pet = new ActivePet(owner.getUniqueId(), data, type, carrier, handle.get());
        pet.applyRarityTint();
        registry.put(pet);

        data.active(true);
        abilities.equip(owner, data, type);
        return replaced ? SummonResult.OK_REPLACED : SummonResult.OK;
    }

    /** 한 마리를 해제한다. 소환 중이 아니면 아무 일도 하지 않는다. */
    public boolean dismiss(final Player owner, final UUID petId) {
        final ActivePet pet = registry.of(owner.getUniqueId(), petId).orElse(null);
        if (pet == null) {
            return false;
        }
        // 순서가 중요하다. 그 펫에 타고 있었다면 먼저 내려야 플레이어가 공중에 남지 않는다.
        if (rides.isRiding(owner, petId)) {
            rides.stop(owner);
        }
        abilities.unequip(owner, pet.data(), pet.type());
        pet.data().active(false);
        store.saveAsync(pet.data());

        registry.remove(owner.getUniqueId(), petId);
        return true;
    }

    /** 소환 중인 펫을 전부 해제한다. 퇴장과 {@code /pet dismiss} 경로다. */
    public int dismissAll(final Player owner) {
        int count = 0;
        for (final ActivePet pet : registry.allOf(owner.getUniqueId())) {
            if (dismiss(owner, pet.petId())) {
                count++;
            }
        }
        return count;
    }

    /** 퇴장·종료 경로. 플레이어 객체 없이도 정리할 수 있어야 한다. */
    public void releaseQuietly(final UUID ownerId) {
        registry.removeAll(ownerId);
    }

    /**
     * 펫을 지급한다. 알 아이템을 깠을 때와 관리자 지급이 같은 경로를 탄다.
     *
     * @return 새 펫. 보유 한도({@code pets.max-owned})가 찼으면 비어 있다 —
     *         <b>호출부는 이때 알 아이템을 소비하면 안 된다</b>
     */
    public Optional<PetData> grantPet(final Player owner, final String typeId) {
        if (!limits.canOwnMore(store.owned(owner.getUniqueId()).size())) {
            return Optional.empty();
        }
        final PetData data = PetData.newBaby(owner.getUniqueId(), typeId, System.currentTimeMillis());
        store.add(data);
        return Optional.of(data);
    }

    /** 펫을 놓아준다. 소환 중이면 먼저 해제한다. */
    public void release(final Player owner, final PetData data) {
        dismiss(owner, data.petId());
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
        final ActivePet current = registry.of(owner.getUniqueId(), data.petId()).orElse(null);
        if (current == null) {
            return false;   // 소환 중이 아니다
        }
        if (current.type().id().equals(data.typeId())) {
            return false;   // 종류가 그대로다
        }
        // 이미 소환 중인 펫을 다시 소환하는 것이라 한도를 새로 잡아먹지 않는다 —
        // summon 이 같은 petId 를 먼저 해제하고 그 자리에 다시 넣는다.
        final SummonResult result = summon(owner, data);
        return result == SummonResult.OK || result == SummonResult.OK_REPLACED;
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
