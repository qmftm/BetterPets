package kr.qmftm.betterpets.item;

import kr.qmftm.betterpets.config.Messages;
import kr.qmftm.betterpets.config.PetCatalog;
import kr.qmftm.betterpets.config.Tags;
import kr.qmftm.betterpets.domain.EggDefinition;
import kr.qmftm.betterpets.domain.FeedDefinition;
import kr.qmftm.betterpets.service.GrowthService;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 알과 먹이 아이템의 생성·식별.
 *
 * <p><b>식별은 반드시 PDC 로 한다.</b> 이름이나 로어 문자열을 비교하면 플레이어가 모루로
 * 이름을 바꾸는 순간 인식이 깨진다. 실제로 흔한 버그다.
 */
public final class PetItems {

    /** 로어에 적을 최대 줄 수. 넘치면 "그 외 N종" 으로 접는다. */
    private static final int LORE_LIMIT = 6;

    private final NamespacedKey eggKey;
    private final NamespacedKey feedKey;
    private final PetCatalog catalog;
    private final Messages messages;

    /**
     * {@code growth} 를 적지 않은 먹이가 쓸 값을 물어볼 곳.
     *
     * <p>숫자를 복사해 들고 있었다. {@code /betterpets reload} 로 {@code growth.feed-amount}
     * 를 바꿔도 아이템 로어에는 예전 값이 계속 적혔다는 뜻이다 — 그리고 실제 효과와
     * 표시가 갈렸다. <b>들고 있으면 다시 밀어 넣어야 한다.</b> 물어보면 그럴 일이 없다.
     */
    private final GrowthService growth;

    public PetItems(final Plugin plugin, final PetCatalog catalog, final Messages messages,
                    final GrowthService growth) {
        this.eggKey = new NamespacedKey(plugin, "egg_id");
        this.feedKey = new NamespacedKey(plugin, "feed");
        this.catalog = catalog;
        this.messages = messages;
        this.growth = growth;
    }

    public ItemStack createEgg(final EggDefinition definition, final int amount) {
        final Material material = Material.matchMaterial(definition.material());
        final ItemStack stack = new ItemStack(
            material == null || !material.isItem() ? Material.EGG : material, clamp(amount));
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(Messages.plain(definition.displayName()));
        meta.lore(contentsLore(definition));
        applyItemModel(meta, definition.itemModel());
        meta.getPersistentDataContainer()
            .set(eggKey, PersistentDataType.STRING, definition.id());
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack createFeed(final FeedDefinition definition, final int amount) {
        final Material material = Material.matchMaterial(definition.material());
        final ItemStack stack = new ItemStack(
            material == null || !material.isItem() ? Material.MILK_BUCKET : material, clamp(amount));
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(Messages.plain(definition.displayName()));
        // 알과 대칭이다. 먹이도 성장도가 제각각인데 이름만 보고는 알 수 없다 —
        // "펫 우유"와 "고급 사료" 중 뭐가 나은지 물어볼 일이 없어야 한다.
        meta.lore(List.of(messages.item("feed.lore-growth",
            "growth", String.valueOf(definition.growthOr(growth.feedAmount())))));
        applyItemModel(meta, definition.itemModel());
        // 먹이마다 성장도가 다르므로 어떤 먹이인지 id 로 남긴다.
        meta.getPersistentDataContainer()
            .set(feedKey, PersistentDataType.STRING, definition.id());
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * 알에 무엇이 들었는지 로어로 보여준다.
     *
     * <p><b>랜덤 알은 확률을 모르면 뽑을 이유가 없다.</b> 기본 설정의 "수상한 알"은
     * 늑대 50 : 드래곤 1 인데, 손에 든 플레이어에게는 그걸 알 방법이 아예 없었다.
     * 설정 파일을 열어볼 수 있는 건 관리자뿐이다.
     *
     * <p>가중치가 큰 순으로 적는다 — 흔한 것부터 보여야 "이 알이 대체로 뭘 주는지"가
     * 한눈에 들어온다. 종류가 많으면 뒤는 접는다. 로어가 화면을 덮으면 아이템을
     * 못 본다.
     */
    private List<Component> contentsLore(final EggDefinition definition) {
        final List<Component> lore = new ArrayList<>();
        if (!definition.isRandom()) {
            lore.add(messages.item("egg.lore-guaranteed", "pet", petName(definition.gives())));
            return lore;
        }
        final int total = definition.weights().values().stream()
            .filter(w -> w != null && w > 0)
            .mapToInt(Integer::intValue)
            .sum();
        if (total <= 0) {
            return lore;    // 설정이 깨진 알. 기동 시 경고가 이미 나갔다
        }
        lore.add(messages.item("egg.lore-header"));

        final List<Map.Entry<String, Integer>> sorted = definition.weights().entrySet().stream()
            .filter(e -> e.getValue() != null && e.getValue() > 0)
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .toList();

        for (int i = 0; i < Math.min(LORE_LIMIT, sorted.size()); i++) {
            final var entry = sorted.get(i);
            lore.add(messages.item("egg.lore-entry",
                "pet", petName(entry.getKey()),
                "chance", percent(entry.getValue(), total)));
        }
        if (sorted.size() > LORE_LIMIT) {
            lore.add(messages.item("egg.lore-more",
                "count", String.valueOf(sorted.size() - LORE_LIMIT)));
        }
        return lore;
    }

    /** 소수점 한 자리까지. 0.05% 같은 값이 "0%" 로 보이면 뽑을 마음이 사라진다. */
    static String percent(final int weight, final int total) {
        final double ratio = 100.0 * weight / total;
        return (ratio < 0.1
            ? String.format(java.util.Locale.ROOT, "%.2f", ratio)
            : String.format(java.util.Locale.ROOT, "%.1f", ratio)) + "%";
    }

    /** 펫 종류의 표시 이름. 설정에서 지워진 종류면 id 를 그대로 보여준다. */
    private String petName(final String typeId) {
        return catalog.type(typeId).map(type -> Tags.strip(type.displayName())).orElse(typeId);
    }

    /**
     * 개수를 1..64 로 접는다.
     *
     * <p>{@code /betterpets egg <플레이어> <알> -5} 처럼 음수가 오면 {@link ItemStack}
     * 생성 자체가 예외를 던져 명령이 스택트레이스로 끝난다. 관리자 오타를
     * 크래시로 돌려주지 않는다.
     */
    private static int clamp(final int amount) {
        return Math.max(1, Math.min(64, amount));
    }

    /**
     * 리소스팩 모델을 지정한다.
     *
     * <p>{@code setCustomModelData(int)} 는 지원 중단됐다. MC 1.21.4+ 는 네임스페이스 키로
     * 모델을 직접 가리킨다. 키 형식이 잘못되면 {@code fromString} 이 null 을 주므로
     * 아이템 자체가 깨지지 않게 그냥 건너뛴다.
     */
    private static void applyItemModel(final ItemMeta meta, final String itemModel) {
        if (itemModel == null || itemModel.isBlank()) {
            return;
        }
        final NamespacedKey key = NamespacedKey.fromString(itemModel);
        if (key != null) {
            meta.setItemModel(key);
        }
    }

    /** 이 아이템이 알이면 그 알 id. */
    public Optional<String> eggIdOf(final ItemStack stack) {
        return read(stack).map(pdc -> pdc.get(eggKey, PersistentDataType.STRING));
    }

    /** 이 아이템이 먹이면 그 먹이 id. */
    public Optional<String> feedIdOf(final ItemStack stack) {
        return read(stack).map(pdc -> pdc.get(feedKey, PersistentDataType.STRING));
    }

    private Optional<PersistentDataContainer> read(final ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.of(stack.getItemMeta().getPersistentDataContainer());
    }
}
