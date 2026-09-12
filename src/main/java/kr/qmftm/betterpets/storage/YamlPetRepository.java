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
            // 파일이 있는데 pets 섹션이 없다. 정상적으로 비어 있을 수도 있고, YAML 이
            // 깨져 Bukkit 이 빈 설정을 돌려준 것일 수도 있다. 후자라면 이대로 두면
            // 다음 저장이 원본을 덮어써 데이터를 영영 잃는다. 구분이 안 되니 옮겨 둔다.
            quarantineIfSuspicious(file);
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
        yaml.set(path + ".fullness", pet.fullness());
        yaml.set(path + ".can-fly", pet.canFly());
        // active 는 저장하지 않는다. 런타임 상태라서 기동 시엔 언제나 false 다 —
        // 예전 파일에 남아 있는 값도 아래 read 가 무시한다.
        yaml.set(path + ".active", null);
        yaml.set(path + ".acquired-at", pet.acquiredAt());
        yaml.set(path + ".updated-at", pet.updatedAt());
    }

    @Override
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
            node.getInt("fullness", 0),
            node.getBoolean("can-fly"),
            false,      // 소환 상태는 저장 대상이 아니다. 기동 직후엔 아무것도 소환돼 있지 않다

            node.getLong("acquired-at", now),
            node.getLong("updated-at", now)
        );
    }

    /**
     * 읽히지 않은 파일을 옆으로 치워 둔다.
     *
     * <p>Bukkit 의 {@code YamlConfiguration.loadConfiguration} 은 <b>파싱에 실패해도
     * 예외를 던지지 않고 빈 설정을 돌려준다.</b> 그래서 "펫이 한 마리도 없는 플레이어"와
     * "파일이 깨진 플레이어"가 호출부에서 똑같이 보인다. 그대로 두면 다음 저장이 원본을
     * 덮어써 복구할 길이 사라진다.
     *
     * <p>내용이 있는 파일만 옮긴다. 진짜로 비어 있는 파일까지 옮기면 매 접속마다
     * 쓰레기 파일이 쌓인다.
     */
    private void quarantineIfSuspicious(final File file) {
        if (file.length() == 0) {
            return;     // 정말 빈 파일이다. 옮길 것도 없다
        }
        final File backup = new File(file.getParentFile(),
            file.getName() + ".broken-" + System.currentTimeMillis());
        if (file.renameTo(backup)) {
            logger.warning("펫 데이터를 읽지 못했습니다: " + file.getName()
                + " → " + backup.getName() + " 로 옮겼습니다."
                + " 내용이 남아 있으니 직접 확인해 복구할 수 있습니다.");
        } else {
            // 옮기지 못했으면 덮어쓰기가 더 위험하다. 사람이 개입해야 한다.
            logger.severe("펫 데이터를 읽지도 옮기지도 못했습니다: " + file
                + " — 이 파일을 직접 확인하세요. 그대로 두면 다음 저장에 덮어써집니다.");
        }
    }

    /**
     * 실제 쓰기.
     *
     * <p><b>실패를 삼키면 안 된다.</b> {@link PetStore} 는 예외가 났을 때만 dirty 를
     * 유지해 다음 기회에 다시 쓴다. 여기서 로그만 남기고 정상 종료하면 저장에 실패한
     * 펫이 깨끗한 것으로 표시돼 <b>그 변경은 영영 사라진다</b> — 디스크가 차거나 권한이
     * 없을 때 조용히 데이터를 잃는다는 뜻이다. 로그는 호출부가 남긴다.
     */
    private void write(final File file, final YamlConfiguration yaml) {
        try {
            yaml.save(file);
        } catch (final IOException error) {
            throw new IllegalStateException("펫 데이터를 저장하지 못했습니다: " + file, error);
        }
    }

    private File fileOf(final UUID ownerId) {
        return new File(directory, ownerId + ".yml");
    }
}
