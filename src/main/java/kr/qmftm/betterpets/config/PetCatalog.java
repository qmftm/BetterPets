package kr.qmftm.betterpets.config;

import kr.qmftm.betterpets.ability.AbilityDefinition;
import kr.qmftm.betterpets.ability.AbilityRegistry;
import kr.qmftm.betterpets.domain.EggDefinition;
import kr.qmftm.betterpets.domain.PetType;
import kr.qmftm.betterpets.domain.Rarity;
import kr.qmftm.betterpets.render.PetRenderer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code pets/*.yml} 과 {@code eggs.yml} 을 읽어 정의를 만든다.
 *
 * <p><b>검증 방침</b> — 첫 오류에서 멈추지 않고 <b>전부 모아 한 번에</b> 보고한다.
 * 관리자가 고치고 재시작하기를 반복하게 만들지 않기 위해서다. 문제가 있는 항목만
 * 건너뛰고 나머지는 정상 로드한다.
 */
public final class PetCatalog {

    private final Map<String, PetType> types = new LinkedHashMap<>();
    private final Map<String, EggDefinition> eggs = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();

    public Optional<PetType> type(final String id) {
        return Optional.ofNullable(types.get(id));
    }

    public Optional<EggDefinition> egg(final String id) {
        return Optional.ofNullable(eggs.get(id));
    }

    public Map<String, PetType> types() {
        return Map.copyOf(types);
    }

    public Map<String, EggDefinition> eggs() {
        return Map.copyOf(eggs);
    }

    /** 로드 중 발견한 문제. 비어 있으면 정상이다. */
    public List<String> problems() {
        return List.copyOf(problems);
    }

    public void load(final File dataFolder, final AbilityRegistry abilities, final PetRenderer renderer) {
        types.clear();
        eggs.clear();
        problems.clear();

        loadTypes(new File(dataFolder, "pets"), abilities);
        loadEggs(new File(dataFolder, "eggs.yml"));

        crossValidate(renderer);
    }

    private void loadTypes(final File folder, final AbilityRegistry abilities) {
        final File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            problems.add("pets/ 폴더에 펫 정의가 없습니다.");
            return;
        }
        for (final File file : files) {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            final String id = yaml.getString("id", file.getName().replaceAll("\\.yml$", ""));

            final Optional<Rarity> rarity = Rarity.parse(yaml.getString("rarity"));
            if (rarity.isEmpty()) {
                problems.add(file.getName() + ": rarity 가 잘못됐습니다 ('"
                    + yaml.getString("rarity") + "'). D/C/B/A/S 중 하나여야 합니다.");
                continue;
            }
            final String model = yaml.getString("model");
            if (model == null || model.isBlank()) {
                problems.add(file.getName() + ": model 이 비어 있습니다.");
                continue;
            }

            final PetType type = new PetType(
                id,
                yaml.getString("display-name", id),
                model,
                yaml.getString("egg-model"),
                rarity.get(),
                yaml.getInt("growth-max", 100),
                yaml.getBoolean("rideable", false),
                yaml.getDouble("fly-chance", rarity.get().flyChance()),
                readAnimations(yaml.getConfigurationSection("animations")),
                readMovement(yaml.getConfigurationSection("movement")),
                readAbilities(file.getName(), yaml.getMapList("abilities"), abilities),
                readWeights(yaml.getConfigurationSection("next-stage")),
                yaml.getInt("acquire.gacha-weight", 0)
            );
            if (types.putIfAbsent(id, type) != null) {
                problems.add(file.getName() + ": id '" + id + "' 가 중복입니다.");
            }
        }
    }

    private PetType.AnimationSet readAnimations(final ConfigurationSection section) {
        if (section == null) {
            return PetType.AnimationSet.defaults();
        }
        final Map<String, String> mapping = new HashMap<>();
        for (final String key : section.getKeys(false)) {
            final String value = section.getString(key);
            if (value != null && !value.isBlank()) {
                mapping.put(key, value);
            }
        }
        return new PetType.AnimationSet(mapping);
    }

    private PetType.MovementProfile readMovement(final ConfigurationSection section) {
        final PetType.MovementProfile defaults = PetType.MovementProfile.defaults();
        if (section == null) {
            return defaults;
        }
        return new PetType.MovementProfile(
            section.getDouble("follow-distance", defaults.followDistance()),
            section.getDouble("walk-speed", defaults.walkSpeed()),
            section.getDouble("run-speed", defaults.runSpeed()),
            section.getDouble("teleport-distance", defaults.teleportDistance())
        );
    }

    private List<AbilityDefinition> readAbilities(final String fileName,
                                                  final List<Map<?, ?>> raw,
                                                  final AbilityRegistry abilities) {
        final List<AbilityDefinition> result = new ArrayList<>();
        for (final Map<?, ?> entry : raw) {
            final Object idValue = entry.get("id");
            if (idValue == null) {
                problems.add(fileName + ": 능력에 id 가 없습니다.");
                continue;
            }
            final String id = idValue.toString();
            if (abilities.find(id).isEmpty()) {
                problems.add(fileName + ": 알 수 없는 능력 '" + id + "'. 사용 가능: " + abilities.ids());
                continue;
            }
            final Map<String, Double> values = new HashMap<>();
            for (final var pair : entry.entrySet()) {
                if (pair.getValue() instanceof Number number && !"id".equals(pair.getKey())) {
                    values.put(pair.getKey().toString(), number.doubleValue());
                }
            }
            result.add(new AbilityDefinition(id, values));
        }
        return result;
    }

    private void loadEggs(final File file) {
        if (!file.exists()) {
            problems.add("eggs.yml 이 없습니다. 알 아이템을 쓸 수 없습니다.");
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (final String id : yaml.getKeys(false)) {
            final ConfigurationSection node = yaml.getConfigurationSection(id);
            if (node == null) {
                continue;
            }
            final String materialName = node.getString("material", "EGG");
            if (Material.matchMaterial(materialName) == null) {
                problems.add("eggs.yml/" + id + ": 알 수 없는 material '" + materialName + "'");
                continue;
            }
            eggs.put(id, new EggDefinition(
                id,
                node.getString("display-name", id),
                materialName,
                node.getString("item-model"),
                node.getString("gives"),
                readWeights(node.getConfigurationSection("weights"))
            ));
        }
    }

    /**
     * {@code id: 가중치} 형태의 섹션을 맵으로 읽는다.
     *
     * <p>알의 랜덤 뽑기(weights)와 펫의 다음 성장 단계(next-stage)가 같은 형식을 쓴다.
     */
    private Map<String, Integer> readWeights(final ConfigurationSection section) {
        final Map<String, Integer> weights = new LinkedHashMap<>();
        if (section != null) {
            for (final String key : section.getKeys(false)) {
                weights.put(key, section.getInt(key));
            }
        }
        return weights;
    }

    /**
     * 정의끼리의 참조를 검사한다. 개별 파일만 봐서는 알 수 없는 오류들이다.
     *
     * <p>모델 존재 여부는 BetterModel 에 물어본다 — 오타를 여기서 잡지 못하면
     * 소환할 때가 되어서야 "모델이 없다"는 걸 알게 된다.
     */
    private void crossValidate(final PetRenderer renderer) {
        for (final PetType type : types.values()) {
            if (!renderer.modelExists(type.modelId())) {
                problems.add("펫 '" + type.id() + "': 모델 '" + type.modelId()
                    + "' 을 BetterModel 에서 찾을 수 없습니다.");
            }
            for (final String key : type.nextStage().keySet()) {
                if (!types.containsKey(key)) {
                    problems.add("펫 '" + type.id() + "': next-stage 의 '" + key + "' 가 없는 펫입니다.");
                }
            }
            for (final String required : PetType.AnimationSet.REQUIRED) {
                if (!type.animations().mapping().containsKey(required)) {
                    // 매핑이 없으면 논리 이름을 그대로 쓴다. 경고만 하고 막지는 않는다.
                    continue;
                }
            }
        }
        for (final EggDefinition egg : eggs.values()) {
            if (!egg.isRandom() && !types.containsKey(egg.gives())) {
                problems.add("알 '" + egg.id() + "': gives 가 가리키는 펫 '"
                    + egg.gives() + "' 가 없습니다.");
            }
            for (final String key : egg.weights().keySet()) {
                if (!types.containsKey(key)) {
                    problems.add("알 '" + egg.id() + "': weights 의 '" + key + "' 가 없는 펫입니다.");
                }
            }
        }
    }
}
