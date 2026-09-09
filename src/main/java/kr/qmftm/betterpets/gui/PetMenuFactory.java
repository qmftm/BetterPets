package kr.qmftm.betterpets.gui;

import kr.qmftm.betterpets.config.Messages;
import kr.qmftm.betterpets.config.PetCatalog;
import kr.qmftm.betterpets.domain.LifeStage;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetLimits;
import kr.qmftm.betterpets.domain.PetType;
import kr.qmftm.betterpets.runtime.PetRegistry;
import kr.qmftm.betterpets.service.GrowthService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 보관함·상세 GUI 를 조립한다. */
public final class PetMenuFactory {

    public static final int BOX_SIZE = 54;
    public static final int BOX_CONTENT = 45;          // 마지막 줄은 네비게이션
    public static final int SLOT_PREV = 45;
    public static final int SLOT_SUMMARY = 49;         // 보유·소환 현황
    public static final int SLOT_NEXT = 53;

    public static final int DETAIL_SIZE = 27;
    public static final int SLOT_SUMMON = 11;
    public static final int SLOT_RENAME = 13;
    public static final int SLOT_RELEASE = 15;
    public static final int SLOT_BACK = 22;

    private final PetCatalog catalog;
    private final GrowthService growth;
    private final PetRegistry registry;
    private final PetLimits limits;

    public PetMenuFactory(final PetCatalog catalog,
                          final GrowthService growth,
                          final PetRegistry registry,
                          final PetLimits limits) {
        this.catalog = catalog;
        this.growth = growth;
        this.registry = registry;
        this.limits = limits;
    }

    /**
     * 보유 목록.
     *
     * <p>한 쪽에 {@value #BOX_CONTENT} 마리씩 보여주고, 마지막 줄에 쪽 넘김과
     * 현황({@link #SLOT_SUMMARY})을 둔다. 보유 한도가 있는 서버에서는 "몇 마리를
     * 더 받을 수 있는지"가 알을 까기 전에 보여야 한다.
     */
    public Inventory box(final UUID ownerId, final List<PetData> pets, final int page) {
        // 마지막 쪽에서 펫을 놓아주면 그 쪽이 통째로 비어버린다. 빈 화면 대신
        // 존재하는 마지막 쪽으로 접어준다.
        final int lastPage = Math.max(0, (pets.size() - 1) / BOX_CONTENT);
        final int shown = Math.max(0, Math.min(page, lastPage));

        final Menus.Box holder = new Menus.Box();
        holder.page(shown);

        final Inventory inventory = Bukkit.createInventory(holder, BOX_SIZE, Messages.plain(
            "<dark_gray>펫 보관함 <gray>" + (shown + 1) + "<dark_gray>/" + (lastPage + 1)));
        holder.inventory(inventory);

        final int from = shown * BOX_CONTENT;
        for (int i = 0; i < BOX_CONTENT && from + i < pets.size(); i++) {
            final PetData pet = pets.get(from + i);
            inventory.setItem(i, icon(pet));
            holder.slots.put(i, pet);
        }
        if (shown > 0) {
            inventory.setItem(SLOT_PREV, simple(Material.ARROW, "<gray>이전 쪽"));
        }
        if (from + BOX_CONTENT < pets.size()) {
            inventory.setItem(SLOT_NEXT, simple(Material.ARROW, "<gray>다음 쪽"));
        }
        inventory.setItem(SLOT_SUMMARY, summary(ownerId, pets.size()));
        return inventory;
    }

    /** 보유·소환 현황 아이콘. 목록 아래에 항상 떠 있다. */
    private ItemStack summary(final UUID ownerId, final int owned) {
        final ItemStack stack = new ItemStack(Material.BOOK);
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(Messages.plain("<yellow>내 펫"));

        final int active = registry.countOf(ownerId);
        final List<Component> lore = new ArrayList<>();
        lore.add(Messages.plain("<gray>보유 <white>" + limits.ownedLabel(owned)));
        lore.add(Messages.plain("<gray>소환 중 <white>" + limits.activeLabel(active)));
        if (!limits.canOwnMore(owned)) {
            lore.add(Messages.plain("<red>보유 한도가 찼습니다. 놓아줘야 더 받습니다."));
        }
        if (owned == 0) {
            lore.add(Messages.plain("<dark_gray>알을 우클릭해 펫을 얻으세요."));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    public Inventory detail(final PetData pet) {
        final Menus.Detail holder = new Menus.Detail(pet);
        final PetType type = catalog.type(pet.typeId()).orElse(null);
        final String name = pet.displayNameOr(type == null ? pet.typeId() : type.displayName());

        final Inventory inventory = Bukkit.createInventory(
            holder, DETAIL_SIZE, Messages.plain("<dark_gray>" + stripTags(name)));
        holder.inventory(inventory);

        inventory.setItem(4, icon(pet));
        inventory.setItem(SLOT_SUMMON, summonButton(pet));
        inventory.setItem(SLOT_RENAME, simple(Material.NAME_TAG, "<yellow>이름 변경"));
        inventory.setItem(SLOT_RELEASE, simple(Material.BARRIER, "<red>놓아주기"));
        inventory.setItem(SLOT_BACK, simple(Material.ARROW, "<gray>돌아가기"));
        return inventory;
    }

    /**
     * 상세 화면의 소환 버튼.
     *
     * <p>동시 소환 한도가 차 있으면 "가장 오래된 펫이 돌아간다"는 사실을 <b>누르기 전에</b>
     * 알려준다. 누른 뒤에 채팅으로 통보하면 이미 되돌릴 수 없다.
     */
    private ItemStack summonButton(final PetData pet) {
        if (pet.active()) {
            return simple(Material.LEAD, "<red>소환 해제");
        }
        final ItemStack stack = simple(Material.LEAD, "<green>소환하기");
        if (!limits.canSummonMore(registry.countOf(pet.ownerId()))) {
            final ItemMeta meta = stack.getItemMeta();
            meta.lore(List.of(Messages.plain(
                "<yellow>동시 소환 한도가 찼습니다. <gray>가장 먼저 부른 펫이 돌아갑니다.")));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** 펫 하나를 나타내는 아이콘. 등급·생애주기·성장도를 한눈에 보여준다. */
    public ItemStack icon(final PetData pet) {
        final PetType type = catalog.type(pet.typeId()).orElse(null);
        final Material material = pet.stage() == LifeStage.BABY ? Material.BONE : Material.LEAD;

        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();

        final String displayName = pet.displayNameOr(type == null ? pet.typeId() : type.displayName());
        meta.displayName(Messages.plain(displayName));

        final List<Component> lore = new ArrayList<>();
        if (type != null) {
            lore.add(Messages.plain("<gray>등급 <white>" + type.rarity().name()
                + " <dark_gray>(" + type.rarity().displayName() + ")"));
        }
        lore.add(Messages.plain("<gray>상태 <white>" + stageLabel(pet.stage())));

        if (pet.stage() != LifeStage.ADULT && pet.stage() != LifeStage.PIG) {
            // max-stage 가 1(기본값)이면 단계 개념이 의미가 없으니 줄을 하나 아낀다.
            if (growth.maxStage() > 1) {
                lore.add(Messages.plain("<gray>성장 단계 <white>"
                    + pet.growthStage() + "<dark_gray>/" + growth.maxStage()));
            }
            final int max = growth.maxOf(pet);
            lore.add(Messages.plain("<gray>성장도 <white>" + pet.growth() + "<dark_gray>/" + max));
            lore.add(Messages.plain("<dark_gray>" + bar(growth.progressOf(pet))));
        }
        if (type != null && type.ride().canRide()) {
            // 성체가 되어야 실제로 탈 수 있고, FLY 종류라도 추첨에 실패했으면 걷는 탑승이다.
            final var effective = type.ride().effective(pet.canFly());
            final String label = pet.stage().rideable()
                ? effective.displayName()
                : effective.displayName() + " <dark_gray>(성체부터)";
            lore.add(Messages.plain("<gray>탑승 <white>" + label));
        }
        lore.add(Messages.plain("<dark_gray>id " + pet.petId().toString().substring(0, 8)));
        if (pet.active()) {
            lore.add(Messages.plain("<green>소환 중"));
            // 목록에서 한눈에 구분되게 반짝이게 한다. 로어 한 줄보다 눈에 먼저 들어온다.
            meta.setEnchantmentGlintOverride(true);
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static String stageLabel(final LifeStage stage) {
        return switch (stage) {
            case BABY -> "아기";
            case ADULT -> "성체";
            case PIG -> "돼지";
        };
    }

    /** 성장도 막대. 20칸을 채운다. */
    private static String bar(final double progress) {
        final int filled = (int) Math.round(Math.max(0.0, Math.min(1.0, progress)) * 20);
        return "<green>" + "■".repeat(filled) + "<dark_gray>" + "■".repeat(20 - filled);
    }

    private static ItemStack simple(final Material material, final String name) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(Messages.plain(name));
        stack.setItemMeta(meta);
        return stack;
    }

    /** 인벤토리 제목에 태그가 그대로 들어가지 않게 한다. */
    private static String stripTags(final String raw) {
        return raw.replaceAll("<[^>]*>", "");
    }
}
