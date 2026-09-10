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
import kr.qmftm.betterpets.runtime.Vectors;
import kr.qmftm.betterpets.storage.PetStore;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

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
    /**
     * 보유·동시 소환 한도.
     *
     * <p>{@code final} 이 아닌 이유는 {@code /petadmin reload} 때문이다. 설정을 다시
     * 읽었는데 한도만 예전 값으로 남으면, "리로드했다"는 메시지가 거짓말이 된다.
     * 그런 침묵은 관리자의 오후를 통째로 잡아먹는다.
     */
    private volatile PetLimits limits;

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

    /** {@code /petadmin reload} 가 부른다. */
    public void limits(final PetLimits value) {
        limits = value;
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
        reindexFollowers(owner.getUniqueId());
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
        reindexFollowers(owner.getUniqueId());
        return true;
    }

    /**
     * 추종 슬롯을 다시 매긴다.
     *
     * <p>여러 마리가 같은 한 점을 목표로 삼으면 서로 겹쳐 떤다. 슬롯마다 주인 뒤
     * 각도를 달리 줘서 부채꼴로 세우는데, 그 번호를 소환·해제 때마다 0부터 다시
     * 붙여야 중간이 비지 않는다.
     */
    private void reindexFollowers(final UUID ownerId) {
        int slot = 0;
        for (final ActivePet pet : registry.allOf(ownerId)) {
            pet.movement().followSlot(slot++);
        }
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
        growth.forget(data);    // 과급식 카운터를 들고 있을 이유가 없다
        store.remove(data);
    }

    /**
     * 성장으로 펫이 달라졌을 때 화면과 능력을 현재 상태에 맞춘다.
     *
     * <p>성장은 <b>재소환 없이</b> 일어난다. 그래서 이 마무리를 빠뜨리면 데이터만 바뀌고
     * 나머지가 예전 상태로 남는다. 두 가지가 어긋난다:
     *
     * <ul>
     *   <li><b>모델</b> — {@link ActivePet} 은 소환 시점의 {@link PetType} 을 붙들고 있어서,
     *       {@code typeId} 만 바뀌면 예전 모델과 예전 애니메이션 이름을 계속 쓴다
     *   <li><b>능력</b> — {@code equip} 은 {@link #summon} 에서만 불린다. 아기로 소환해 둔
     *       채 성체가 되면 능력이 영영 안 붙는다. "펫을 데리고 다니며 키운다"는 가장
     *       자연스러운 경로에서 등급별 능력이 통째로 조용히 안 도는 상태였다
     * </ul>
     *
     * <p>능력은 떼었다 다시 붙인다. {@code unequip} 은 생애주기와 무관하게 돌고
     * {@code equip} 은 성체에게만 붙으므로, 이 한 쌍이 <b>어느 방향의 변화든</b> 맞춘다 —
     * 아기→성체는 붙고, 성체→돼지는 떨어진다.
     *
     * <p>탑승 중에 종류가 바뀌었다면 {@link #summon} 안의 {@link #dismiss} 가 안전하게
     * 내려준다 — 드래곤이 돼지가 됐는데 그대로 하늘에 떠 있으면 곤란하다.
     *
     * @return 이번 마무리의 결과. 호출부는 {@link RefreshResult#DETACHED} 를 반드시
     *         플레이어에게 알려야 한다 — 눈앞의 펫이 사라진 상황이다
     */
    public RefreshResult refreshAfterGrowth(final Player owner, final PetData data) {
        final ActivePet current = registry.of(owner.getUniqueId(), data.petId()).orElse(null);
        if (current == null) {
            return RefreshResult.NOT_ACTIVE;   // 소환 중이 아니다. 다음 소환 때 맞춰진다
        }
        if (!current.type().id().equals(data.typeId())) {
            // 종류가 바뀌었다. 모델부터 다시 붙여야 하고, 그 과정에서 능력도 다시 붙는다.
            // 이미 소환 중인 펫이라 동시 소환 한도를 새로 잡아먹지 않는다 — summon 이
            // 같은 petId 를 먼저 해제하고 그 자리에 다시 넣는다.
            final SummonResult result = summon(owner, data);
            if (result == SummonResult.OK || result == SummonResult.OK_REPLACED) {
                return RefreshResult.OK;
            }
            // 새 종류의 모델이 없다. summon 이 이미 예전 개체를 해제했으므로 눈앞에서
            // 펫이 사라진 상태다. 조용히 넘기면 "다 자랐습니다!" 와 빈자리만 남는다.
            return RefreshResult.DETACHED;
        }
        abilities.unequip(owner, data, current.type());
        abilities.equip(owner, data, current.type());
        return RefreshResult.OK;
    }

    /** {@link #refreshAfterGrowth} 의 결과. */
    public enum RefreshResult {
        /** 소환 중이 아니었다. 할 일이 없었다. */
        NOT_ACTIVE,
        /** 모델과 능력을 지금 상태에 맞췄다. */
        OK,
        /** 새 종류를 붙이지 못해 펫이 보관함으로 돌아갔다. 설정이나 모델이 빠진 것이다. */
        DETACHED
    }

    /** 소환 지점을 찾을 때 소유자 뒤에서부터 돌려볼 각도. 뒤 → 좌우 → 앞 순이다. */
    private static final int[] SPAWN_ANGLES = {0, 45, -45, 90, -90, 135, -135, 180};

    /** 소유자로부터 떨어뜨릴 거리. 너무 붙으면 시야를 가리고, 멀면 소환한 티가 안 난다. */
    private static final double SPAWN_DISTANCE = 1.5;

    /**
     * 펫을 내려놓을 자리를 찾는다.
     *
     * <p>기본은 소유자 뒤 {@value #SPAWN_DISTANCE} 블록이지만, <b>거기가 벽이나 바닥
     * 속일 수 있다.</b> 캐리어는 충돌하지 않으므로 끼이지는 않지만 모델이 블록에 파묻혀
     * 보이고, {@code MovementController} 의 갇힘 폴백이 3초 뒤에야 꺼내준다. 소환하자마자
     * 3초간 벽에 박혀 있는 건 첫인상으로 최악이다.
     *
     * <p>그래서 소유자를 중심으로 각도를 돌려가며 빈 자리를 찾는다. 전부 막혔으면
     * <b>소유자가 서 있는 자리</b>를 쓴다 — 사람이 서 있으니 반드시 비어 있다.
     */
    private Location spawnLocation(final Player owner) {
        final Location base = owner.getLocation();
        final Vector facing = base.getDirection().setY(0);
        if (facing.lengthSquared() < 1.0e-4) {
            facing.setX(0).setZ(1);
        }
        facing.normalize().multiply(-SPAWN_DISTANCE);   // 뒤쪽

        for (final int degrees : SPAWN_ANGLES) {
            final Vector offset = facing.clone();
            Vectors.rotateAroundY(offset, Math.toRadians(degrees));
            final Location candidate = base.clone().add(offset);
            if (isOpen(candidate)) {
                return candidate;
            }
        }
        return base.clone();
    }

    /** 발치와 머리 높이가 모두 비어 있는가. 한 칸만 보면 반쯤 파묻힌다. */
    private static boolean isOpen(final Location at) {
        return at.getBlock().isPassable() && at.clone().add(0, 1, 0).getBlock().isPassable();
    }


    public PetCatalog catalog() {
        return catalog;
    }

    public GrowthService growth() {
        return growth;
    }
}
