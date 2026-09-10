package kr.qmftm.betterpets.gui;

import kr.qmftm.betterpets.config.Messages;
import kr.qmftm.betterpets.config.PetCatalog;
import kr.qmftm.betterpets.config.Tags;
import kr.qmftm.betterpets.domain.LifeStage;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetLimits;
import kr.qmftm.betterpets.domain.PetOrder;
import kr.qmftm.betterpets.domain.PetType;
import kr.qmftm.betterpets.runtime.PetRegistry;
import kr.qmftm.betterpets.service.GrowthService;
import kr.qmftm.betterpets.service.PetService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
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

    // 첫 줄은 정보 둘을 가운데 기준으로 나란히 둔다. 아래 줄이 조작 버튼이다.
    public static final int SLOT_ICON = 3;
    public static final int SLOT_ABILITIES = 5;

    public static final int SLOT_SUMMON = 11;
    public static final int SLOT_RENAME = 13;
    public static final int SLOT_RELEASE = 15;
    public static final int SLOT_BACK = 22;

    private final PetCatalog catalog;
    private final GrowthService growth;
    private final PetRegistry registry;
    private final PetService pets;

    /**
     * GUI 문구도 번역 대상이다.
     *
     * <p>전에는 이 클래스가 한국어를 그대로 들고 있었다. {@code language: en_us} 로
     * 바꾸면 채팅만 영어가 되고 <b>보관함은 한국어로 남았다</b> — 반쯤 번역된 화면이
     * 아예 번역이 없는 것보다 나쁘다.
     */
    private final Messages messages;

    public PetMenuFactory(final PetCatalog catalog,
                          final GrowthService growth,
                          final PetRegistry registry,
                          final PetService pets,
                          final Messages messages) {
        this.catalog = catalog;
        this.growth = growth;
        this.registry = registry;
        this.pets = pets;
        this.messages = messages;
    }

    /**
     * 인벤토리 제목.
     *
     * <p>여기서 태그를 걷어내지 않는다 — 그러면 번역자가 제목에 색을 쓸 수 없다.
     * 대신 <b>플레이어가 정한 값</b>(펫 이름)은 넘기기 전에 호출부가 걷어낸다.
     * 위험한 건 서식 자체가 아니라, 남이 정한 문자열이 서식으로 읽히는 것이다.
     */
    private Component title(final String key, final String... placeholders) {
        return messages.item(key, placeholders);
    }

    /**
     * 현재 한도. 값을 들고 있지 않고 매번 서비스에 묻는다 —
     * {@code /petadmin reload} 로 바뀐 값이 GUI 에도 바로 보여야 한다.
     */
    private PetLimits limits() {
        return pets.limits();
    }

    /**
     * 보관함에 보여줄 순서.
     *
     * <p>기준은 {@link PetOrder} 에 있다. 이 순서가 GUI 와 {@code /pet list} 양쪽에
     * 쓰인다 — 두 곳이 다르면 "보관함에서 세 번째"가 채팅에서는 다른 펫을 가리킨다.
     */
    public List<PetData> ordered(final Collection<PetData> owned) {
        final List<PetData> list = new ArrayList<>(owned);
        list.sort(PetOrder.forBox(this::rarityRank));
        return list;
    }

    /** 등급 서열. 종류를 못 찾으면 맨 뒤로 보낸다 — 설정이 깨진 펫이다. */
    private int rarityRank(final PetData pet) {
        return catalog.type(pet.typeId()).map(type -> type.rarity().ordinal()).orElse(-1);
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

        final Inventory inventory = Bukkit.createInventory(holder, BOX_SIZE,
            title("gui.box-title",
                "page", String.valueOf(shown + 1),
                "pages", String.valueOf(lastPage + 1)));
        holder.inventory(inventory);

        final int from = shown * BOX_CONTENT;
        for (int i = 0; i < BOX_CONTENT && from + i < pets.size(); i++) {
            final PetData pet = pets.get(from + i);
            inventory.setItem(i, icon(pet));
            holder.slots.put(i, pet);
        }
        if (shown > 0) {
            inventory.setItem(SLOT_PREV, simple(Material.ARROW, "gui.prev-page"));
        }
        if (from + BOX_CONTENT < pets.size()) {
            inventory.setItem(SLOT_NEXT, simple(Material.ARROW, "gui.next-page"));
        }
        inventory.setItem(SLOT_SUMMARY, summary(ownerId, pets.size()));
        return inventory;
    }

    /** 보유·소환 현황 아이콘. 목록 아래에 항상 떠 있다. */
    private ItemStack summary(final UUID ownerId, final int owned) {
        final ItemStack stack = new ItemStack(Material.BOOK);
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(line("gui.summary-title"));

        final int active = registry.countOf(ownerId);
        final List<Component> lore = new ArrayList<>();
        lore.add(line("gui.summary-owned", "value", limits().ownedLabel(owned)));
        lore.add(line("gui.summary-active", "value", limits().activeLabel(active)));
        if (!limits().canOwnMore(owned)) {
            lore.add(line("gui.summary-full"));
        }
        if (owned == 0) {
            lore.add(line("gui.summary-empty"));
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
            holder, DETAIL_SIZE, title("gui.detail-title", "name", Tags.strip(name)));
        holder.inventory(inventory);

        inventory.setItem(SLOT_ICON, icon(pet));
        inventory.setItem(SLOT_ABILITIES, abilityBook(pet, type));
        inventory.setItem(SLOT_SUMMON, summonButton(pet));
        inventory.setItem(SLOT_RENAME, simple(Material.NAME_TAG, "gui.rename"));
        inventory.setItem(SLOT_RELEASE, simple(Material.BARRIER, "gui.release"));
        inventory.setItem(SLOT_BACK, simple(Material.ARROW, "gui.back"));
        return inventory;
    }

    /**
     * 이 펫이 가진 능력.
     *
     * <p>지금까지 능력은 화면 어디에도 보이지 않았다. 설정 파일을 열어보지 않으면
     * S등급 펫이 무엇을 해 주는지 알 방법이 없었다는 뜻이다.
     *
     * <p>수치는 <b>지금 성장도 기준</b>으로 계산해 보여준다. 능력이 성장도에 비례해
     * 커지는데 설정값만 보여주면 "왜 이만큼 안 오르지"가 된다.
     */
    private ItemStack abilityBook(final PetData pet, final PetType type) {
        final ItemStack stack = new ItemStack(Material.ENCHANTED_BOOK);
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(line("gui.abilities"));

        final List<Component> lore = new ArrayList<>();
        if (type == null || type.abilities().isEmpty()) {
            lore.add(line("gui.abilities-none"));
        } else if (!pet.stage().abilitiesActive()) {
            // 아기와 돼지는 능력이 없다. 왜 안 보이는지 알려줘야 한다.
            lore.add(line("gui.abilities-locked"));
            type.abilities().forEach(ability ->
                lore.add(line("gui.abilities-locked-entry", "ability", ability.id())));
        } else {
            for (final var ability : type.abilities()) {
                final double value = ability.scaled(pet.growth(), type.stats().moveSpeedMultiplier());
                lore.add(line("gui.abilities-entry",
                    "ability", ability.id(),
                    "value", String.format(java.util.Locale.ROOT, "%.2f", value)));
            }
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * 상세 화면의 소환 버튼.
     *
     * <p>동시 소환 한도가 차 있으면 "가장 오래된 펫이 돌아간다"는 사실을 <b>누르기 전에</b>
     * 알려준다. 누른 뒤에 채팅으로 통보하면 이미 되돌릴 수 없다.
     */
    private ItemStack summonButton(final PetData pet) {
        if (pet.active()) {
            return simple(Material.LEAD, "gui.dismiss");
        }
        final ItemStack stack = simple(Material.LEAD, "gui.summon");
        if (!limits().canSummonMore(registry.countOf(pet.ownerId()))) {
            final ItemMeta meta = stack.getItemMeta();
            meta.lore(List.of(line("gui.summon-limit")));
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
            lore.add(line("gui.pet-rarity",
                "rarity", type.rarity().name(),
                "name", Tags.strip(type.stats().displayName())));
        }
        lore.add(line("gui.pet-stage", "stage", stageLabel(pet.stage())));

        if (pet.stage() != LifeStage.ADULT && pet.stage() != LifeStage.PIG) {
            // max-stage 가 1(기본값)이면 단계 개념이 의미가 없으니 줄을 하나 아낀다.
            if (growth.maxStage() > 1) {
                lore.add(line("gui.pet-growth-stage",
                    "stage", String.valueOf(pet.growthStage()),
                    "max", String.valueOf(growth.maxStage())));
            }
            lore.add(line("gui.pet-growth",
                "growth", String.valueOf(pet.growth()),
                "max", String.valueOf(growth.maxOf(pet))));
            lore.add(line("gui.pet-growth-bar", "bar", bar(growth.progressOf(pet))));
            // 시간만 흘려도 자란다는 걸 여기서 알 수 있어야 한다.
            // 안 그러면 먹이를 줘야만 크는 줄 알고 계속 먹이러 다닌다.
            final long until = growth.millisUntilNextPoint(pet);
            if (until > 0) {
                lore.add(line("gui.pet-next-growth", "time", humanize(until)));
            }
        }
        if (type != null && type.ride().canRide()) {
            // 성체가 되어야 실제로 탈 수 있고, FLY 종류라도 추첨에 실패했으면 걷는 탑승이다.
            final var effective = type.ride().effective(pet.canFly());
            lore.add(line(pet.stage().rideable() ? "gui.pet-ride" : "gui.pet-ride-locked",
                "mode", Tags.strip(effective.displayName())));
        }
        lore.add(line("gui.pet-id", "id", pet.petId().toString().substring(0, 8)));
        if (pet.active()) {
            lore.add(line("gui.pet-active"));
            // 목록에서 한눈에 구분되게 반짝이게 한다. 로어 한 줄보다 눈에 먼저 들어온다.
            meta.setEnchantmentGlintOverride(true);
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    /** 생애주기 이름. enum 이름을 그대로 쓰면 영어가 튀어나온다. */
    private String stageLabel(final LifeStage stage) {
        return Tags.strip(messages.raw("stage." + stage.name().toLowerCase(java.util.Locale.ROOT)));
    }

    /** 남은 시간을 사람이 읽는 형태로. 초 단위까지만 — 그보다 정밀할 이유가 없다. */
    private String humanize(final long millis) {
        final long seconds = Math.max(0, millis / 1000L);
        if (seconds < 60) {
            return Tags.strip(messages.raw("time.seconds", "s", String.valueOf(seconds)));
        }
        final long minutes = seconds / 60;
        if (minutes < 60) {
            return Tags.strip(messages.raw("time.minutes",
                "m", String.valueOf(minutes), "s", String.valueOf(seconds % 60)));
        }
        return Tags.strip(messages.raw("time.hours",
            "h", String.valueOf(minutes / 60), "m", String.valueOf(minutes % 60)));
    }

    /** 성장도 막대. 20칸을 채운다. */
    private static String bar(final double progress) {
        final int filled = (int) Math.round(Math.max(0.0, Math.min(1.0, progress)) * 20);
        return "<green>" + "■".repeat(filled) + "<dark_gray>" + "■".repeat(20 - filled);
    }

    private ItemStack simple(final Material material, final String key) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(line(key));
        stack.setItemMeta(meta);
        return stack;
    }

    /** 언어 파일의 한 줄을 아이템 이름·로어용 {@link Component} 로. */
    private Component line(final String key, final String... placeholders) {
        return messages.item(key, placeholders);
    }

}
