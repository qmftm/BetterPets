package kr.qmftm.betterpets.item;

import kr.qmftm.betterpets.config.Messages;
import kr.qmftm.betterpets.domain.EggDefinition;
import kr.qmftm.betterpets.domain.FeedDefinition;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * 알과 먹이 아이템의 생성·식별.
 *
 * <p><b>식별은 반드시 PDC 로 한다.</b> 이름이나 로어 문자열을 비교하면 플레이어가 모루로
 * 이름을 바꾸는 순간 인식이 깨진다. 실제로 흔한 버그다.
 */
public final class PetItems {

    private final NamespacedKey eggKey;
    private final NamespacedKey feedKey;

    public PetItems(final Plugin plugin) {
        this.eggKey = new NamespacedKey(plugin, "egg_id");
        this.feedKey = new NamespacedKey(plugin, "feed");
    }

    public ItemStack createEgg(final EggDefinition definition, final int amount) {
        final Material material = Material.matchMaterial(definition.material());
        final ItemStack stack = new ItemStack(
            material == null || !material.isItem() ? Material.EGG : material, clamp(amount));
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(Messages.plain(definition.displayName()));
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
        applyItemModel(meta, definition.itemModel());
        // 먹이마다 성장도가 다르므로 어떤 먹이인지 id 로 남긴다.
        meta.getPersistentDataContainer()
            .set(feedKey, PersistentDataType.STRING, definition.id());
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * 개수를 1..64 로 접는다.
     *
     * <p>{@code /petadmin egg <플레이어> <알> -5} 처럼 음수가 오면 {@link ItemStack}
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
