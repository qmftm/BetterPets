package kr.qmftm.betterpets.runtime;

import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 소유자 → 소환된 펫들.
 *
 * <p>한 명이 여러 마리를 동시에 소환할 수 있다. 몇 마리까지인지는 이 클래스가 아니라
 * {@code config.yml} 의 {@code pets.max-active} 가 정하고, 강제는 {@code PetService}
 * 가 한다 — 레지스트리는 담아둘 뿐이다.
 *
 * <p><b>소환 순서를 지킨다.</b> 한도가 찼을 때 "가장 먼저 소환한 펫을 돌려보낸다"는
 * 규칙이 순서에 기대고 있어서, 소유자별 목록은 삽입 순서를 보존하는
 * {@link CopyOnWriteArrayList} 다. 마리 수가 한 자릿수라 복사 비용은 문제되지 않고,
 * 대신 순회 중 제거(틱 루프가 실제로 그렇게 한다)가 안전해진다.
 */
public final class PetRegistry {

    private final Map<UUID, List<ActivePet>> active = new ConcurrentHashMap<>();

    /** 같은 펫이 이미 있으면 먼저 닫고 교체한다. 교체 시 이전 것을 흘리지 않기 위해서다. */
    public void put(final ActivePet pet) {
        final List<ActivePet> owned =
            active.computeIfAbsent(pet.ownerId(), key -> new CopyOnWriteArrayList<>());
        for (final ActivePet existing : owned) {
            if (existing.petId().equals(pet.petId())) {
                owned.remove(existing);
                if (existing != pet) {
                    existing.close();
                }
            }
        }
        owned.add(pet);
    }

    public Optional<ActivePet> of(final UUID ownerId, final UUID petId) {
        for (final ActivePet pet : allOf(ownerId)) {
            if (pet.petId().equals(petId)) {
                return Optional.of(pet);
            }
        }
        return Optional.empty();
    }

    /**
     * 한 소유자의 펫을 복사 없이 훑는다.
     *
     * <p>{@link #allOf} 는 호출마다 {@code List.copyOf} 를 뜬다. 능력 트리거가
     * <b>전투 중 피격·처치 이벤트마다</b> 부르는 자리라 그 복사가 실제로 쌓인다.
     */
    public void forEachOf(final UUID ownerId, final java.util.function.Consumer<ActivePet> action) {
        final List<ActivePet> owned = active.get(ownerId);
        if (owned == null) {
            return;
        }
        for (final ActivePet pet : owned) {
            action.accept(pet);
        }
    }

    /** 소유자가 소환 중인 펫 전부. 소환한 순서다 — 앞쪽이 가장 오래됐다. */
    public List<ActivePet> allOf(final UUID ownerId) {
        final List<ActivePet> owned = active.get(ownerId);
        return owned == null ? List.of() : List.copyOf(owned);
    }

    public int countOf(final UUID ownerId) {
        final List<ActivePet> owned = active.get(ownerId);
        return owned == null ? 0 : owned.size();
    }

    /** 캐리어 엔티티로 역참조. 펫을 우클릭했을 때 쓴다. */
    public Optional<ActivePet> byCarrier(final Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        for (final List<ActivePet> owned : active.values()) {
            for (final ActivePet pet : owned) {
                if (entity.equals(pet.carrier())) {
                    return Optional.of(pet);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 복사 없이 전부 훑는다.
     *
     * <p>{@link #all()} 은 호출마다 {@link ArrayList} 를 새로 만든다. 틱 루프가 초당
     * 30번 부르는 자리라 그 쓰레기가 그대로 GC 압력이 된다. 소유자별 목록이
     * {@link CopyOnWriteArrayList} 라 순회 중 변경이 안전하므로 — 실제로 틱 루프가
     * 순회하면서 죽은 펫을 지운다 — 스냅샷을 뜰 이유가 없다.
     */
    public void forEach(final java.util.function.Consumer<ActivePet> action) {
        for (final List<ActivePet> owned : active.values()) {
            for (final ActivePet pet : owned) {
                action.accept(pet);
            }
        }
    }

    /** 스냅샷이 필요할 때만. 틱 루프는 {@link #forEach} 를 쓴다. */
    public Collection<ActivePet> all() {
        final List<ActivePet> everything = new ArrayList<>();
        for (final List<ActivePet> owned : active.values()) {
            everything.addAll(owned);
        }
        return everything;
    }

    public int size() {
        int total = 0;
        for (final List<ActivePet> owned : active.values()) {
            total += owned.size();
        }
        return total;
    }

    /** 한 마리를 해제하고 정리한다. */
    public boolean remove(final UUID ownerId, final UUID petId) {
        final List<ActivePet> owned = active.get(ownerId);
        if (owned == null) {
            return false;
        }
        for (final ActivePet pet : owned) {
            if (pet.petId().equals(petId)) {
                owned.remove(pet);
                pet.close();
                if (owned.isEmpty()) {
                    active.remove(ownerId, owned);
                }
                return true;
            }
        }
        return false;
    }

    /** 소유자의 펫을 전부 해제한다. 퇴장 경로에서 쓴다. */
    public int removeAll(final UUID ownerId) {
        final List<ActivePet> owned = active.remove(ownerId);
        if (owned == null) {
            return 0;
        }
        for (final ActivePet pet : owned) {
            pet.close();
        }
        return owned.size();
    }

    /** 서버 종료 시. 남은 것을 전부 닫는다. */
    public void closeAll() {
        for (final List<ActivePet> owned : active.values()) {
            for (final ActivePet pet : owned) {
                pet.close();
            }
        }
        active.clear();
    }
}
