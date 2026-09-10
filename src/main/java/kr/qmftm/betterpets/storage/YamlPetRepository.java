package kr.qmftm.betterpets.storage;

import kr.qmftm.betterpets.domain.LifeStage;
import kr.qmftm.betterpets.domain.PetData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 플레이어 한 명당 파일 하나로 저장한다. {@code playerdata/<uuid>.yml}
 *
 * <p>동접 5명 규모라 DB가 과잉이다. YAML은 의존성이 없고, 문제가 생겼을 때 사람이 직접
 * 열어 고칠 수 있다는 실질적 장점이 있다.
 *
 * <p>플레이어별 파일로 나눈 이유는 한 파일에 몰면 저장할 때마다 전체를 다시 쓰게 되고,
 * 한 곳이 깨지면 전원의 데이터를 잃기 때문이다.
 */
public final class YamlPetRepository implements PetRepository {

    private final File directory;
    private final Logger logger;

    public YamlPetRepository(final File directory, final Logger logger) {
        this.directory = directory;
        this.logger = logger;
        if (!directory.exists() && !directory.mkdirs()) {
            logger.warning("펫 데이터 폴더를 만들지 못했습니다: " + directory);
        }
    }

    @Override
    public List<PetData> loadOwner(final UUID ownerId) {
        final File file = fileOf(ownerId);
        if (!file.exists()) {
            return List.of();
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection root = yaml.getConfigurationSection("pets");
        if (root == null) {
            return List.of();
        }
        final List<PetData> result = new ArrayList<>();
        for (final String key : root.getKeys(false)) {
            final ConfigurationSection node = root.getConfigurationSection(key);
            if (node == null) {
                continue;
            }
            try {
                result.add(read(ownerId, key, node));
            } catch (final RuntimeException error) {
                // 한 마리가 깨졌다고 나머지까지 버리지 않는다. 그 펫만 건너뛰고 로그를 남긴다.
                logger.log(Level.WARNING, "펫 데이터를 읽지 못했습니다: " + ownerId + "/" + key, error);
            }
        }
        return result;
    }

    @Override
    public void save(final PetData pet) {
        saveAll(List.of(pet));
    }

    /**
     * 소유자별로 묶어 <b>파일당 한 번만</b> 읽고 쓴다.
     *
     * <p>한 마리씩 저장하면 같은 {@code <uuid>.yml} 을 마리 수만큼 파싱하고 직렬화한다.
     * 20마리가 한꺼번에 더러워지는 경우(퇴장·종료)가 실제로 있어서, 그때 파일 하나를
     * 20번 다시 쓰게 된다. 묶으면 한 번이다.
     */
    @Override
    public void saveAll(final java.util.Collection<PetData> pets) {
        if (pets.isEmpty()) {
            return;
        }
        final Map<UUID, List<PetData>> byOwner = new LinkedHashMap<>();
        for (final PetData pet : pets) {
            byOwner.computeIfAbsent(pet.ownerId(), key -> new ArrayList<>()).add(pet);
        }
        for (final var entry : byOwner.entrySet()) {
            final File file = fileOf(entry.getKey());
            final YamlConfiguration yaml = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
            for (final PetData pet : entry.getValue()) {
                writeInto(yaml, pet);
            }
            write(file, yaml);
        }
    }

    private void writeInto(final YamlConfiguration yaml, final PetData pet) {
        final String path = "pets." + pet.petId();
        yaml.set(path + ".type", pet.typeId());
        yaml.set(path + ".nickname", pet.nickname());
        yaml.set(path + ".stage", pet.stage().name());
        yaml.set(path + ".growth", pet.growth());
        yaml.set(path + ".growth-stage", pet.growthStage());
        yaml.set(path + ".can-fly", pet.canFly());
        // active 는 저장하지 않는다. 런타임 상태라서 기동 시엔 언제나 false 다 —
        // 예전 파일에 남아 있는 값도 아래 read 가 무시한다.
        yaml.set(path + ".active", null);
        yaml.set(path + ".acquired-at", pet.acquiredAt());
        yaml.set(path + ".updated-at", pet.updatedAt());
    }

    @Override
    public void delete(final UUID petId) {
        // 소유자를 모르면 파일을 특정할 수 없다. 전체를 훑는 대신 호출부가
        // deleteFrom 을 쓰게 한다. 이 오버로드는 인터페이스 호환을 위해 남긴다.
        for (final File file : listFiles()) {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            if (yaml.contains("pets." + petId)) {
                yaml.set("pets." + petId, null);
                write(file, yaml);
                return;
            }
        }
    }

    /** 소유자를 아는 경우. 파일 하나만 건드리므로 이쪽이 훨씬 싸다. */
    public void delete(final UUID ownerId, final UUID petId) {
        final File file = fileOf(ownerId);
        if (!file.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("pets." + petId, null);
        write(file, yaml);
    }

    private PetData read(final UUID ownerId, final String key, final ConfigurationSection node) {
        final long now = System.currentTimeMillis();
        return new PetData(
            UUID.fromString(key),
            ownerId,
            node.getString("type", ""),
            node.getString("nickname"),
            LifeStage.parse(node.getString("stage")).orElse(LifeStage.BABY),
            node.getInt("growth"),
            node.getInt("growth-stage", 1),
            node.getBoolean("can-fly"),
            false,      // 소환 상태는 저장 대상이 아니다. 기동 직후엔 아무것도 소환돼 있지 않다

            node.getLong("acquired-at", now),
            node.getLong("updated-at", now)
        );
    }

    private void write(final File file, final YamlConfiguration yaml) {
        try {
            yaml.save(file);
        } catch (final IOException error) {
            logger.log(Level.SEVERE, "펫 데이터를 저장하지 못했습니다: " + file, error);
        }
    }

    private File fileOf(final UUID ownerId) {
        return new File(directory, ownerId + ".yml");
    }

    private File[] listFiles() {
        final File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        return files == null ? new File[0] : files;
    }
}
