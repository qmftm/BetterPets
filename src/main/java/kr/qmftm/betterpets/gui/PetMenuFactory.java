package kr.qmftm.betterpets.gui;

import kr.qmftm.betterpets.config.Messages;
import kr.qmftm.betterpets.config.PetCatalog;
import kr.qmftm.betterpets.domain.LifeStage;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetType;
import kr.qmftm.betterpets.service.GrowthService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** 보관함·상세 GUI 를 조립한다. */
public final class PetMenuFactory {

    public static final int BOX_SIZE = 54;
    public static final int BOX_CONTENT = 45;          // 마지막 줄은 네비게이션
    public static final int SLOT_PREV = 45;
    public static final int SLOT_NEXT = 53;

    public static final int DETAIL_SIZE = 27;
    public static final int SLOT_SUMMON = 11;
    public static final int SLOT_RENAME = 13;
    public static final int SLOT_RELEASE = 15;
    public static final int SLOT_BACK = 22;

    private final PetCatalog catalog;
    private final GrowthService growth;

    public PetMenuFactory(final PetCatalog catalog, final GrowthService growth) {
        this.catalog = catalog;
        this.growth = growth;
    }

    public Inventory box(final List<PetData> pets, final int page) {
        final Menus.Box holder = new Menus.Box();
        holder.page(page);

        final Inventory inventory = Bukkit.createInventory(
            holder, BOX_SIZE, Messages.plain("<dark_gray>펫 보관함 <gray>(" + (page + 1) + ")"));
        holder.inventory(inventory);

        final int from = page * BOX_CONTENT;
        for (int i = 0; i < BOX_CONTENT && from + i < pets.size(); i++) {
            final PetData pet = pets.get(from + i);
            inventory.setItem(i, icon(pet));
            holder.slots.put(i, pet);
        }
        if (page > 0) {
            inventory.setItem(SLOT_PREV, simple(Material.ARROW, "<gray>이전 쪽"));
        }
        if (from + BOX_CONTENT < pets.size()) {
            inventory.setItem(SLOT_NEXT, simple(Material.ARROW, "<gray>다음 쪽"));
        }
        return inventory;
    }

    public Inventory detail(final PetData pet) {
        final Menus.Detail holder = new Menus.Detail(pet);
        final PetType type = catalog.type(pet.typeId()).orElse(null);
        final String name = pet.displayNameOr(type == null ? pet.typeId() : type.displayName());

        final Inventory inventory = Bukkit.createInventory(
            holder, DETAIL_SIZE, Messages.plain("<dark_gray>" + stripTags(name)));
        holder.inventory(inventory);

        inventory.setItem(4, icon(pet));
        inventory.setItem(SLOT_SUMMON, simple(Material.LEAD,
            pet.active() ? "<red>소환 해제" : "<green>소환하기"));
        inventory.setItem(SLOT_RENAME, simple(Material.NAME_TAG, "<yellow>이름 변경"));
        inventory.setItem(SLOT_RELEASE, simple(Material.BARRIER, "<red>놓아주기"));
        inventory.setItem(SLOT_BACK, simple(Material.ARROW, "<gray>돌아가기"));
        return inventory;
    }

    /** 펫 하나를 나타내는 아이콘. 등급·생애주기·성장도를 한눈에 보여준다. */
    public ItemStack icon(final PetData pet) {
        final PetType type = catalog.type(pet.typeId()).orElse(null);
        final Material material = pet.stage() == LifeStage.EGG || pet.stage() == LifeStage.HATCHING
            ? Material.TURTLE_EGG
            : Material.LEAD;

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
        if (pet.canFly()) {
            lore.add(Messages.plain("<aqua>비행 가능"));
        }
        if (pet.active()) {
            lore.add(Messages.plain("<green>소환 중"));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static String stageLabel(final LifeStage stage) {
        return switch (stage) {
            case EGG -> "알";
            case HATCHING -> "부화 중";
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
