package kr.qmftm.betterpets.runtime;

import org.bukkit.entity.Entity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 소유자 → 소환된 펫. 소유자당 하나다. */
public final class PetRegistry {

    private final Map<UUID, ActivePet> active = new ConcurrentHashMap<>();

    /** 이미 있으면 먼저 닫고 교체한다. 교체 시 이전 것을 흘리지 않기 위해서다. */
    public void put(final ActivePet pet) {
        final ActivePet previous = active.put(pet.ownerId(), pet);
        if (previous != null && previous != pet) {
            previous.close();
        }
    }

    public Optional<ActivePet> of(final UUID ownerId) {
        return Optional.ofNullable(active.get(ownerId));
    }

    /** 캐리어 엔티티로 역참조. 펫을 우클릭했을 때 쓴다. */
    public Optional<ActivePet> byCarrier(final Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        return active.values().stream()
            .filter(pet -> entity.equals(pet.carrier()))
            .findFirst();
    }

    public Collection<ActivePet> all() {
        return List.copyOf(active.values());
    }

    public int size() {
        return active.size();
    }

    /** 해제하고 정리한다. */
    public void remove(final UUID ownerId) {
        final ActivePet pet = active.remove(ownerId);
        if (pet != null) {
            pet.close();
        }
    }

    /** 서버 종료 시. 남은 것을 전부 닫는다. */
    public void closeAll() {
        for (final ActivePet pet : List.copyOf(active.values())) {
            pet.close();
        }
        active.clear();
    }
}
