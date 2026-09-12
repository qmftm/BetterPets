package kr.qmftm.betterpets.config;

import kr.qmftm.betterpets.ability.AbilityDefinition;
import kr.qmftm.betterpets.ability.AbilityRegistry;
import kr.qmftm.betterpets.domain.EggDefinition;
import kr.qmftm.betterpets.domain.FeedDefinition;
import kr.qmftm.betterpets.domain.PetType;
import kr.qmftm.betterpets.domain.Rarity;
import kr.qmftm.betterpets.domain.RarityStats;
import kr.qmftm.betterpets.domain.RarityTable;
import kr.qmftm.betterpets.domain.RideMode;
import kr.qmftm.betterpets.render.PetRenderer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code pets/*.yml} 과 {@code items.yml} 을 읽어 정의를 만든다.
 *
 * <p><b>검증 방침</b> — 첫 오류에서 멈추지 않고 <b>전부 모아 한 번에</b> 보고한다.
 * 관리자가 고치고 재시작하기를 반복하게 만들지 않기 위해서다. 문제가 있는 항목만
 * 건너뛰고 나머지는 정상 로드한다.
 */
public final class PetCatalog {

    private final Map<String, PetType> types = new LinkedHashMap<>();
    private final Map<String, EggDefinition> eggs = new LinkedHashMap<>();
    private final Map<String, FeedDefinition> feeds = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();

    /** 등급 수치. {@code load} 가 채우기 전에도 읽힐 수 있어서 기본값으로 시작한다. */
    private RarityTable rarities = RarityTable.defaults();

    public Optional<PetType> type(final String id) {
        return Optional.ofNullable(types.get(id));
    }

    public Optional<EggDefinition> egg(final String id) {
        return Optional.ofNullable(eggs.get(id));
    }

    public Optional<FeedDefinition> feed(final String id) {
        return Optional.ofNullable(feeds.get(id));
    }

    public Map<String, PetType> types() {
        return Map.copyOf(types);
    }

    public Map<String, EggDefinition> eggs() {
        return Map.copyOf(eggs);
    }

    public Map<String, FeedDefinition> feeds() {
        return Map.copyOf(feeds);
    }

    /** 로드 중 발견한 문제. 비어 있으면 정상이다. */
    public List<String> problems() {
        return List.copyOf(problems);
    }

    public void load(final File dataFolder, final AbilityRegistry abilities, final PetRenderer renderer) {
        types.clear();
        eggs.clear();
        feeds.clear();
        problems.clear();

        // 등급 수치를 먼저 읽는다. 펫 정의가 이 값을 박아 넣기 때문이다.
        rarities = loadRarities(new File(dataFolder, "rarity.yml"));
        loadTypes(new File(dataFolder, "pets"), abilities);
        loadItems(dataFolder);

        crossValidate(renderer);
    }

    /**
     * {@code rarity.yml} 을 읽는다. 없으면 내장 기본값만으로 돈다 — 문제로 치지 않는다.
     * 이 파일은 밸런싱용이라 없어도 플러그인이 정상 동작한다.
     */
    private RarityTable loadRarities(final File file) {
        if (!file.isFile()) {
            return RarityTable.defaults();
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection root = yaml.getConfigurationSection("rarities");
        if (root == null) {
            problems.add("rarity.yml 에 'rarities:' 섹션이 없습니다. 기본 수치로 진행합니다.");
            return RarityTable.defaults();
        }
        final Map<Rarity, RarityStats> overrides = new EnumMap<>(Rarity.class);
        for (final String key : root.getKeys(false)) {
            final Optional<Rarity> rarity = Rarity.parse(key);
            if (rarity.isEmpty()) {
                problems.add("rarity.yml: '" + key + "' 는 등급이 아닙니다. D/C/B/A/S 중 하나여야 합니다.");
                continue;
            }
            final ConfigurationSection node = root.getConfigurationSection(key);
            if (node == null) {
                continue;
            }
            // 적지 않은 항목은 그 등급의 내장 기본값을 그대로 쓴다. 다섯 줄을 전부
            // 적게 만들 이유가 없다 — S등급의 fly-chance 만 손보는 게 흔한 경우다.
            final RarityStats fallback = rarity.get().defaults();
            overrides.put(rarity.get(), new RarityStats(
                node.getString("display-name", fallback.displayName()),
                node.getDouble("move-speed", fallback.moveSpeedMultiplier()),
                node.getDouble("ride-speed", fallback.rideSpeed()),
                node.getDouble("fly-chance", fallback.flyChance())));
        }
        return RarityTable.of(overrides);
    }

    /**
     * {@code item-model:} 을 읽는다.
     *
     * <p><b>형식이 틀리면 조용히 무시된다.</b> {@code NamespacedKey.fromString} 이 null 을
     * 돌려주고, 아이템은 리소스팩 모델 없이 기본 모양으로 나온다. 관리자 입장에서는
     * "모델을 지정했는데 안 나온다"가 되는데 로그에도 아무것도 없다. 흔한 실수는
     * 대문자다 — {@code betterpets:Egg_Wolf} 은 잘못된 키다.
     *
     * @return 쓸 수 있는 키 문자열. 비었거나 형식이 틀리면 null (모델 없이 진행)
     */
    private String readItemModel(final ConfigurationSection node, final String where) {
        final String raw = node.getString("item-model");
        if (raw == null || raw.isBlank()) {
            return null;    // 안 적은 것은 실수가 아니다
        }
        if (NamespacedKey.fromString(raw) == null) {
            problems.add(where + ": item-model '" + raw
                + "' 은 잘못된 키입니다. 소문자로 '네임스페이스:경로' 형식이어야 합니다"
                + " (예: betterpets:egg_wolf).");
            return null;
        }
        return raw;
    }

    private void loadTypes(final File folder, final AbilityRegistry abilities) {
        final File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            problems.add("pets/ 폴더에 펫 정의가 없습니다.");
            return;
        }
        // 파일 순서를 고정한다. listFiles 는 파일시스템이 주는 순서라 기계마다 다른데,
        // 이 순서가 곧 types 의 반복 순서이고 뽑기 표의 구간 순서가 된다.
        // 같은 설정·같은 시드에서 서버마다 다른 펫이 나오면 재현이 불가능해진다.
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
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

            final RideMode ride = readRideMode(file.getName(), yaml);
            if (ride == null) {
                continue;
            }

            final PetType type = new PetType(
                id,
                yaml.getString("display-name", id),
                model,
                rarity.get(),
                rarities.stats(rarity.get()),
                yaml.getInt("growth-max", 100),
                ride,
                yaml.getDouble("fly-chance", rarities.stats(rarity.get()).flyChance()),
                yaml.getDouble("ride-speed", rarities.stats(rarity.get()).rideSpeed()),
                readAnimations(file.getName(), yaml.getConfigurationSection("animations")),
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

    /**
     * {@code ride:} 를 읽는다. 값이 없으면 {@link RideMode#NONE} 이다.
     *
     * @return 해석한 값. 오타 등으로 해석에 실패했으면 null (호출부가 이 펫을 건너뛴다)
     */
    private RideMode readRideMode(final String fileName, final YamlConfiguration yaml) {
        // 예전 스키마를 그대로 둔 파일이 조용히 "탑승 불가"가 되면 원인을 찾기 어렵다.
        if (yaml.contains("rideable")) {
            problems.add(fileName + ": 'rideable' 은 'ride' 로 바뀌었습니다."
                + " NONE(탑승 불가) / GROUND(걷는 탑승) / FLY(나는 탑승) 중 하나를 쓰세요.");
        }
        final String raw = yaml.getString("ride");
        if (raw == null || raw.isBlank()) {
            return RideMode.NONE;
        }
        final Optional<RideMode> parsed = RideMode.parse(raw);
        if (parsed.isEmpty()) {
            problems.add(fileName + ": ride 가 잘못됐습니다 ('" + raw
                + "'). NONE / GROUND / FLY 중 하나여야 합니다.");
            return null;
        }
        return parsed.get();
    }

    /**
     * {@code animations:} 매핑을 읽는다.
     *
     * <p><b>모르는 키는 알린다.</b> 여기 키는 플러그인이 쓰는 논리 이름이라 정해져 있는데,
     * 오타를 내도 맵에 조용히 들어갔다가 아무도 찾지 않는다. 증상은 "이름을 바꿨는데
     * 안 먹는다"로 나오고, 그때 들여다볼 곳은 이 파일이 아니라 모델 쪽이라고 착각하게 된다.
     */
    private PetType.AnimationSet readAnimations(final String fileName,
                                                final ConfigurationSection section) {
        if (section == null) {
            return PetType.AnimationSet.defaults();
        }
        final Map<String, String> mapping = new HashMap<>();
        for (final String key : section.getKeys(false)) {
            final String value = section.getString(key);
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!PetType.AnimationSet.KNOWN.contains(key)) {
                problems.add(fileName + ": animations 의 '" + key
                    + "' 는 플러그인이 쓰지 않는 이름입니다. 사용 가능: "
                    + PetType.AnimationSet.KNOWN);
                continue;
            }
            mapping.put(key, value);
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

    /**
     * {@code items.yml} 에서 알과 먹이를 읽는다.
     *
     * <p>둘은 성격이 비슷하고 개수도 적어서 한 파일에 둔다 — 관리자가 아이템을 손볼 때
     * 파일 두 개를 왔다 갔다 하지 않아도 된다.
     */
    private void loadItems(final File dataFolder) {
        // 예전에는 알만 eggs.yml 에 있었다. 그 파일이 남아 있으면 조용히 무시하지 않는다 —
        // 거기 적어둔 알이 사라진 것처럼 보여 원인을 찾기 어렵다.
        if (new File(dataFolder, "eggs.yml").exists()) {
            problems.add("eggs.yml 은 items.yml 의 'eggs:' 섹션으로 옮겨졌습니다."
                + " 내용을 옮긴 뒤 eggs.yml 을 지우세요. 지금은 무시됩니다.");
        }

        final File file = new File(dataFolder, "items.yml");
        if (!file.exists()) {
            problems.add("items.yml 이 없습니다. 알과 먹이 아이템을 쓸 수 없습니다.");
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        final ConfigurationSection eggSection = yaml.getConfigurationSection("eggs");
        if (eggSection == null) {
            problems.add("items.yml 에 'eggs:' 섹션이 없습니다.");
        } else {
            eggSection.getKeys(false).forEach(id -> readEgg(eggSection, id));
        }

        final ConfigurationSection feedSection = yaml.getConfigurationSection("feeds");
        if (feedSection == null) {
            problems.add("items.yml 에 'feeds:' 섹션이 없습니다. 먹이를 줄 수 없으면 펫이 자라지 않습니다.");
        } else {
            feedSection.getKeys(false).forEach(id -> readFeed(feedSection, id));
        }
    }

    private void readEgg(final ConfigurationSection parent, final String id) {
        final ConfigurationSection node = parent.getConfigurationSection(id);
        if (node == null) {
            return;
        }
        final String materialName = readMaterial(node, "EGG", "items.yml/eggs/" + id);
        if (materialName == null) {
            return;
        }
        final String gives = node.getString("gives");
        Map<String, Integer> weights = readWeights(node.getConfigurationSection("weights"));

        // gives 도 weights 도 없는 알은 "그냥 뽑기 알"이다. 그때는 각 펫이 자기 파일에
        // 적어둔 acquire.gacha-weight 를 표로 쓴다.
        //
        // 그 값은 여태 읽어만 두고 아무도 쓰지 않았다. pets/*.yml 의 주석과 사용 안내는
        // "랜덤 알에서 뽑힐 가중치"라고 적고 있었으니, gacha-weight: 0 으로 막아뒀다고
        // 믿은 펫이 실제로는 아무 영향도 못 받고 있었다. 문서가 약속한 대로 되돌린다.
        if ((gives == null || gives.isBlank()) && weights.isEmpty()) {
            weights = gachaTable();
            if (weights.isEmpty()) {
                problems.add("items.yml/eggs/" + id + ": gives 도 weights 도 없는데,"
                    + " acquire.gacha-weight 가 0 보다 큰 펫이 하나도 없습니다."
                    + " 이 알은 아무것도 주지 못합니다.");
            }
        }

        eggs.put(id, new EggDefinition(
            id,
            node.getString("display-name", id),
            materialName,
            readItemModel(node, "items.yml/eggs/" + id),
            gives,
            weights
        ));
    }

    /**
     * 펫들이 스스로 적어둔 뽑기 가중치를 모은다.
     *
     * <p>{@code acquire.gacha-weight} 가 0 이하인 펫은 빠진다 — 과급식으로만 얻는 돼지,
     * 전용 알로만 주는 새끼가 그렇다. 이 표를 쓰면 <b>펫을 추가할 때 items.yml 을 다시
     * 손대지 않아도</b> 뽑기 알에 자동으로 들어간다.
     *
     * <p>{@code types} 는 {@link LinkedHashMap} 이고 펫 파일을 이름순으로 읽으므로,
     * 여기서 나오는 순서도 기계와 무관하게 같다 — {@code Weighted.pick} 이 순서대로
     * 구간을 나누기 때문에 그게 곧 재현 가능성이다.
     */
    private Map<String, Integer> gachaTable() {
        return gachaTable(types.values());
    }

    /** 표를 만드는 규칙만 따로. 파일도 Bukkit 도 필요 없어서 이쪽만 검증할 수 있다. */
    static Map<String, Integer> gachaTable(final java.util.Collection<PetType> all) {
        final Map<String, Integer> table = new LinkedHashMap<>();
        for (final PetType type : all) {
            if (type.gachaWeight() > 0) {
                table.put(type.id(), type.gachaWeight());
            }
        }
        return table;
    }

    private void readFeed(final ConfigurationSection parent, final String id) {
        final ConfigurationSection node = parent.getConfigurationSection(id);
        if (node == null) {
            return;
        }
        final String materialName = readMaterial(node, "MILK_BUCKET", "items.yml/feeds/" + id);
        if (materialName == null) {
            return;
        }
        // growth 를 적지 않으면 config.yml 의 전역 기본값을 쓴다는 뜻이라 null 로 남긴다.
        final Integer growth = node.contains("growth") ? node.getInt("growth") : null;
        if (growth != null && growth <= 0) {
            problems.add("items.yml/feeds/" + id + ": growth 가 " + growth
                + " 입니다. 0 이하면 먹여도 자라지 않습니다.");
        }
        feeds.put(id, new FeedDefinition(
            id,
            node.getString("display-name", id),
            materialName,
            readItemModel(node, "items.yml/feeds/" + id),
            growth
        ));
    }

    /**
     * {@code items.yml} 의 알 하나에 {@code material:} 을 다시 써넣는다.
     * {@code /petadmin eggmaterial} 이 손에 든 아이템으로 지정할 때 쓴다.
     *
     * <p>메모리 상의 {@link #eggs} 는 여기서 안 건드린다 — 파일과 메모리가 잠깐이라도
     * 갈리는 순간을 만들지 않으려고다. 호출자가 이어서 {@link #load} 를 다시 태워
     * 파일과 메모리를 한 번에 맞춘다({@code /petadmin reload} 와 같은 경로).
     *
     * @return 알 id 가 없거나 파일 쓰기에 실패하면 false
     */
    public boolean writeEggMaterial(final File dataFolder, final String eggId, final Material material) {
        final File file = new File(dataFolder, "items.yml");
        if (!file.isFile()) {
            return false;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection eggSection = yaml.getConfigurationSection("eggs");
        final ConfigurationSection node = eggSection == null
            ? null : eggSection.getConfigurationSection(eggId);
        if (node == null) {
            return false;
        }
        node.set("material", material.name());
        try {
            yaml.save(file);
            return true;
        } catch (final java.io.IOException error) {
            return false;
        }
    }

    /**
     * {@code material:} 을 읽는다.
     *
     * <p><b>존재하는 것만으로는 모자라다. 아이템으로 들 수 있어야 한다.</b>
     * {@code AIR} 은 {@code matchMaterial} 을 통과하지만 {@code ItemStack} 으로 만들면
     * {@code getItemMeta()} 가 null 이라, 지급 명령이 NPE 로 죽는다. 블록 전용
     * material 도 마찬가지 부류다. 설정 오타가 명령어 크래시로 나타나면 원인을
     * 찾기 어려우니 여기서 잡는다.
     */
    private String readMaterial(final ConfigurationSection node, final String fallback, final String where) {
        final String materialName = node.getString("material", fallback);
        final Material material = Material.matchMaterial(materialName);
        if (material == null) {
            problems.add(where + ": 알 수 없는 material '" + materialName + "'");
            return null;
        }
        if (!material.isItem()) {
            problems.add(where + ": material '" + materialName
                + "' 은 아이템으로 들 수 없습니다. 손에 쥘 수 있는 것을 지정하세요.");
            return null;
        }
        return materialName;
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
            // FLY 인데 확률이 0이면 어떤 개체도 날지 못하고 전부 걷는 탑승이 된다.
            // 의도한 것일 수도 있지만 대개는 fly-chance 를 빠뜨린 실수다.
            if (type.rollsFlight() && type.flyChance() <= 0.0) {
                problems.add("펫 '" + type.id() + "': ride 가 FLY 인데 fly-chance 가 0 입니다."
                    + " 이대로면 항상 걷는 탑승이 됩니다 (등급 " + type.rarity().name()
                    + " 의 기본 비행 확률은 " + type.stats().flyChance() + ").");
            }
        }
        for (final EggDefinition egg : eggs.values()) {
            if (!egg.isRandom() && !types.containsKey(egg.gives())) {
                problems.add("items.yml/eggs/" + egg.id() + ": gives 가 가리키는 펫 '"
                    + egg.gives() + "' 가 없습니다.");
            }
            for (final String key : egg.weights().keySet()) {
                if (!types.containsKey(key)) {
                    problems.add("items.yml/eggs/" + egg.id() + ": weights 의 '" + key + "' 가 없는 펫입니다.");
                }
            }
        }
    }
}
