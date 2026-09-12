package kr.qmftm.betterpets.gui;

import kr.qmftm.betterpets.config.Messages;
import kr.qmftm.betterpets.config.PetCatalog;
import kr.qmftm.betterpets.config.Tags;
import kr.qmftm.betterpets.domain.LifeStage;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetFilter;
import kr.qmftm.betterpets.domain.PetLimits;
import kr.qmftm.betterpets.domain.PetSort;
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
    public static final int SLOT_SORT = 47;            // 정렬 바꾸기
    public static final int SLOT_SUMMARY = 49;         // 보유·소환 현황
    public static final int SLOT_FILTER = 51;          // 무엇만 볼지
    public static final int SLOT_NEXT = 53;

    public static final int DETAIL_SIZE = 27;

    // 첫 줄 가운데에 정보, 둘째 줄에 조작 버튼. 돌아가기는 마지막 줄 오른쪽 끝 —
    // 다른 조작과 안 섞이게 구석으로 뺐다.
    public static final int SLOT_ICON = 4;

    public static final int SLOT_SUMMON = 11;
    public static final int SLOT_RENAME = 13;
    public static final int SLOT_RELEASE = 15;
    public static final int SLOT_BACK = 26;

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
     * 보여줄 목록을 걸러 정렬한다.
     *
     * <p>기준은 {@link PetSort}·{@link PetFilter} 에 있다 — Bukkit 없이 검증할 수 있는
     * 쪽에 둔다. {@code /pet list} 는 {@link PetSort#DEFAULT} 로 고정이다. 보관함의
     * 정렬은 <b>보는 사람의 선택</b>이라 채팅과 같을 이유가 없다. 어느 펫인지는 양쪽 다
     * 여덟 자리 id 로 가리키므로 순서가 달라도 헷갈리지 않는다.
     */
    public List<PetData> ordered(final Collection<PetData> owned,
                                 final PetSort sort,
                                 final PetFilter filter) {
        final List<PetData> list = new ArrayList<>(owned.size());
        for (final PetData pet : owned) {
            if (filter.test(pet)) {
                list.add(pet);
            }
        }
        list.sort(sort.comparator(this::rarityRank));
        return list;
    }

    /** {@code /pet list} 처럼 고정 순서가 필요한 곳. */
    public List<PetData> ordered(final Collection<PetData> owned) {
        return ordered(owned, PetSort.DEFAULT, PetFilter.ALL);
    }

    /** 등급 서열. 종류를 못 찾으면 맨 뒤로 보낸다 — 설정이 깨진 펫이다. */
    private int rarityRank(final PetData pet) {
        return catalog.type(pet.typeId()).map(type -> type.rarity().ordinal()).orElse(-1);
    }

    /**
     * 보유 목록.
     *
     * <p>한 쪽에 {@value #BOX_CONTENT} 마리씩 보여주고, 마지막 줄에 쪽 넘김·정렬·보기와
     * 현황({@link #SLOT_SUMMARY})을 둔다. 보유 한도가 있는 서버에서는 "몇 마리를
     * 더 받을 수 있는지"가 알을 까기 전에 보여야 한다.
     *
     * @param owned 소유한 펫 <b>전부</b>. 거르고 정렬하는 건 여기서 한다 — 호출부가
     *              미리 걸러 넘기면 현황에 적을 전체 마릿수를 잃는다
     * @param view  보고 있는 쪽·정렬·보기. 쪽 번호는 실제 마지막 쪽으로 접어서 쓴다
     */
    public Inventory box(final UUID ownerId,
                         final Collection<PetData> owned,
                         final Menus.View view) {
        final List<PetData> pets = ordered(owned, view.sort(), view.filter());

        // 마지막 쪽에서 펫을 놓아주면 그 쪽이 통째로 비어버린다. 필터를 바꿔도 마찬가지다.
        // 빈 화면 대신 존재하는 마지막 쪽으로 접어준다.
        final int lastPage = Math.max(0, (pets.size() - 1) / BOX_CONTENT);
        final Menus.View shown = view.page(Math.min(view.page(), lastPage));

        final Menus.Box holder = new Menus.Box();
        holder.view(shown);

        final Inventory inventory = Bukkit.createInventory(holder, BOX_SIZE,
            title("gui.box-title",
                "page", String.valueOf(shown.page() + 1),
                "pages", String.valueOf(lastPage + 1)));
        holder.inventory(inventory);

        final int from = shown.page() * BOX_CONTENT;
        for (int i = 0; i < BOX_CONTENT && from + i < pets.size(); i++) {
            final PetData pet = pets.get(from + i);
            inventory.setItem(i, icon(pet));
            holder.slots.put(i, pet);
        }
        if (shown.page() > 0) {
            inventory.setItem(SLOT_PREV, simple(Material.ARROW, "gui.prev-page"));
        }
        if (from + BOX_CONTENT < pets.size()) {
            inventory.setItem(SLOT_NEXT, simple(Material.ARROW, "gui.next-page"));
        }
        inventory.setItem(SLOT_SORT, toggle(Material.HOPPER, "gui.sort",
            "gui.sort-" + shown.sort().key(), "gui.sort-" + shown.sort().next().key()));
        inventory.setItem(SLOT_FILTER, toggle(Material.SPYGLASS, "gui.filter",
            "gui.filter-" + shown.filter().key(), "gui.filter-" + shown.filter().next().key()));
        inventory.setItem(SLOT_SUMMARY, summary(ownerId, owned.size(), pets.size(), shown.filter()));
        return inventory;
    }

    /**
     * 정렬·필터 버튼.
     *
     * <p>지금 값과 <b>다음에 눌렀을 때의 값</b>을 같이 적는다. 지금 값만 보여주면
     * 무엇이 나올지 몰라서 원하는 게 나올 때까지 누르게 된다.
     */
    private ItemStack toggle(final Material material,
                             final String titleKey,
                             final String currentKey,
                             final String nextKey) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(line(titleKey, "value", labelOf(currentKey)));
        meta.lore(List.of(line("gui.toggle-hint", "next", labelOf(nextKey))));
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * 문구 하나를 태그 없는 평문으로.
     *
     * <p>버튼 이름 <b>안에 끼워 넣을</b> 값이라 서식이 들어가면 안 된다 — 색 태그가
     * 그대로 가면 바깥 문구의 색을 중간에서 덮어쓴다.
     */
    private String labelOf(final String key) {
        return Tags.strip(messages.raw(key));
    }

    /**
     * 보유·소환 현황 아이콘. 목록 아래에 항상 떠 있다.
     *
     * @param owned 필터와 무관한 <b>전체</b> 마릿수. 한도는 걸러진 수와 비교할 값이 아니다
     * @param shown 지금 화면에 걸린 수
     */
    private ItemStack summary(final UUID ownerId, final int owned,
                              final int shown, final PetFilter filter) {
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
        } else if (shown == 0) {
            // 펫은 있는데 화면이 비었다. 필터 때문이라고 말해주지 않으면
            // "펫이 사라졌다"로 읽힌다.
            lore.add(line("gui.summary-filtered-empty"));
        } else if (filter != PetFilter.ALL) {
            lore.add(line("gui.summary-shown", "count", String.valueOf(shown)));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * 펫 한 마리의 상세 화면.
     *
     * @param view 이 펫을 고른 보관함 화면. "돌아가기"가 그대로 되돌아간다
     */
    public Inventory detail(final PetData pet, final Menus.View view) {
        final Menus.Detail holder = new Menus.Detail(pet, view);
        final PetType type = catalog.type(pet.typeId()).orElse(null);
        final String name = pet.displayNameOr(type == null ? pet.typeId() : type.displayName());

        final Inventory inventory = Bukkit.createInventory(
            holder, DETAIL_SIZE, title("gui.detail-title", "name", Tags.strip(name)));
        holder.inventory(inventory);

        inventory.setItem(SLOT_ICON, icon(pet));
        inventory.setItem(SLOT_SUMMON, summonButton(pet));
        inventory.setItem(SLOT_RENAME, simple(Material.NAME_TAG, "gui.rename"));
        inventory.setItem(SLOT_RELEASE, releaseButton());
        inventory.setItem(SLOT_BACK, simple(Material.ARROW, "gui.back"));
        return inventory;
    }

    /**
     * 놓아주기 버튼.
     *
     * <p><b>되돌릴 수 없는 유일한 버튼이다.</b> 그런데 소환 버튼 바로 옆에 있고, 한 번
     * 누르면 키우던 펫이 그대로 사라졌다. 그래서 쉬프트를 요구하고, 그 사실을 로어에
     * 적는다 — 눌러보고 알게 되는 규칙은 규칙이 아니다.
     */
    private ItemStack releaseButton() {
        final ItemStack stack = simple(Material.BARRIER, "gui.release");
        final ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(line("gui.release-hint")));
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

    /**
     * 펫 하나를 나타내는 아이콘. 등급·성장도를 한눈에 보여준다.
     *
     * <p><b>아기·성체 구분은 안 보여준다.</b> 능력도 탑승도 생애주기와 무관하게 처음부터
     * 되므로, 그 구분이 더 이상 "이 펫으로 뭘 할 수 있는가"를 말해주지 않는다.
     */
    public ItemStack icon(final PetData pet) {
        final PetType type = catalog.type(pet.typeId()).orElse(null);

        final ItemStack stack = new ItemStack(Material.LEAD);
        final ItemMeta meta = stack.getItemMeta();

        final String displayName = pet.displayNameOr(type == null ? pet.typeId() : type.displayName());
        meta.displayName(Messages.plain(displayName));

        final List<Component> lore = new ArrayList<>();
        if (type != null) {
            lore.add(line("gui.pet-rarity", "rarity", type.rarity().name()));
        }

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
        }
        if (type != null && type.ride().canRide()) {
            // 생애주기와 무관하게 탈 수 있다. FLY 종류라도 추첨에 실패했으면 걷는 탑승이다.
            final var effective = type.ride().effective(pet.canFly());
            lore.add(line("gui.pet-ride", "mode", Tags.strip(effective.displayName())));
        }
        if (pet.active()) {
            lore.add(line("gui.pet-active"));
            // 목록에서 한눈에 구분되게 반짝이게 한다. 로어 한 줄보다 눈에 먼저 들어온다.
            meta.setEnchantmentGlintOverride(true);
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
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
