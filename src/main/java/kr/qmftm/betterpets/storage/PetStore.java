package kr.qmftm.betterpets.storage;

import kr.qmftm.betterpets.domain.PetData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 메모리 캐시 + 비동기 저장.
 *
 * <p><b>단일 스레드 실행자를 쓴다.</b> 5명 규모에 커넥션 풀은 과잉이고, 스레드가 하나면
 * 파일 쓰기 순서가 보장돼 경합 자체가 없다. 동시성 버그를 코드가 아니라 구조로 없앤다.
 *
 * <p>읽기는 전부 메모리에서 일어난다. 접속 시 한 번 로드하고, 그 뒤로는 디스크를 보지 않는다.
 */
public final class PetStore {

    private final PetRepository repository;
    private final Logger logger;
    private final ExecutorService io;

    /** 소유자 → (펫 id → 데이터). 온라인 플레이어의 것만 들고 있는다. */
    private final Map<UUID, Map<UUID, PetData>> cache = new ConcurrentHashMap<>();

    public PetStore(final PetRepository repository, final Logger logger) {
        this.repository = repository;
        this.logger = logger;
        this.io = Executors.newSingleThreadExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "BetterPets-IO");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 접속 시 호출. 비동기로 읽어 캐시에 채운다. */
    public void loadAsync(final UUID ownerId, final Runnable afterLoad) {
        io.execute(() -> {
            try {
                final Map<UUID, PetData> loaded = new ConcurrentHashMap<>();
                for (final PetData pet : repository.loadOwner(ownerId)) {
                    loaded.put(pet.petId(), pet);
                }
                cache.put(ownerId, loaded);
            } catch (final RuntimeException error) {
                logger.log(Level.SEVERE, "펫 데이터 로드 실패: " + ownerId, error);
                cache.putIfAbsent(ownerId, new ConcurrentHashMap<>());
            }
            if (afterLoad != null) {
                afterLoad.run();
            }
        });
    }

    /** 퇴장 시 호출. 남은 변경을 밀어내고 캐시에서 뺀다. */
    public void unload(final UUID ownerId) {
        final Map<UUID, PetData> owned = cache.remove(ownerId);
        if (owned == null) {
            return;
        }
        final List<PetData> dirty = owned.values().stream().filter(PetData::isDirty).toList();
        if (dirty.isEmpty()) {
            return;
        }
        io.execute(() -> persist(dirty));
    }

    public Collection<PetData> owned(final UUID ownerId) {
        final Map<UUID, PetData> owned = cache.get(ownerId);
        return owned == null ? List.of() : List.copyOf(owned.values());
    }

    public Optional<PetData> find(final UUID ownerId, final UUID petId) {
        final Map<UUID, PetData> owned = cache.get(ownerId);
        return owned == null ? Optional.empty() : Optional.ofNullable(owned.get(petId));
    }

    /** 현재 장착 중인 펫. 소유자당 최대 하나다. */
    public Optional<PetData> activePet(final UUID ownerId) {
        return owned(ownerId).stream().filter(PetData::active).findFirst();
    }

    public void add(final PetData pet) {
        cache.computeIfAbsent(pet.ownerId(), key -> new ConcurrentHashMap<>())
            .put(pet.petId(), pet);
        saveAsync(pet);
    }

    public void remove(final PetData pet) {
        final Map<UUID, PetData> owned = cache.get(pet.ownerId());
        if (owned != null) {
            owned.remove(pet.petId());
        }
        final UUID ownerId = pet.ownerId();
        final UUID petId = pet.petId();
        io.execute(() -> {
            if (repository instanceof YamlPetRepository yaml) {
                yaml.delete(ownerId, petId);
            } else {
                repository.delete(petId);
            }
        });
    }

    /**
     * 변경된 펫을 비동기로 저장한다.
     *
     * <p>5명 규모라 배치 flush 없이 변경 시마다 바로 쓴다. 파일 하나가 작고 쓰기가
     * 드물어서 이게 더 단순하고 데이터를 잃을 창도 좁다.
     */
    public void saveAsync(final PetData pet) {
        if (!pet.isDirty()) {
            return;
        }
        io.execute(() -> persist(List.of(pet)));
    }

    /** 소유자의 변경분 전체를 저장한다. */
    public void saveOwnerAsync(final UUID ownerId) {
        final List<PetData> dirty = owned(ownerId).stream().filter(PetData::isDirty).toList();
        if (!dirty.isEmpty()) {
            io.execute(() -> persist(dirty));
        }
    }

    /**
     * 종료 시 호출. <b>반드시 동기로 기다린다.</b>
     * 비동기로 두면 서버가 먼저 죽어 마지막 쓰기를 잃는다.
     */
    public void shutdown() {
        for (final Map<UUID, PetData> owned : cache.values()) {
            final List<PetData> dirty = owned.values().stream().filter(PetData::isDirty).toList();
            if (!dirty.isEmpty()) {
                io.execute(() -> persist(dirty));
            }
        }
        io.execute(repository::flush);
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("펫 데이터 저장이 10초 안에 끝나지 않았습니다. 일부를 잃었을 수 있습니다.");
            }
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            logger.warning("펫 데이터 저장 대기가 중단됐습니다.");
        }
        cache.clear();
    }

    private void persist(final Collection<PetData> pets) {
        for (final PetData pet : new ArrayList<>(pets)) {
            try {
                repository.save(pet);
                pet.clearDirty();
            } catch (final RuntimeException error) {
                logger.log(Level.SEVERE, "펫 저장 실패: " + pet.petId(), error);
            }
        }
    }
}
