package kr.qmftm.betterpets.integration;

import kr.qmftm.betterpets.config.PetCatalog;
import kr.qmftm.betterpets.config.Tags;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetLimits;
import kr.qmftm.betterpets.domain.PetType;
import kr.qmftm.betterpets.runtime.ActivePet;
import kr.qmftm.betterpets.runtime.PetRegistry;
import kr.qmftm.betterpets.service.GrowthService;
import kr.qmftm.betterpets.storage.PetStore;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * PlaceholderAPI 확장. {@code %betterpets_<키>%}
 *
 * <p><b>여기만 컴파일 의존을 건다.</b> {@link PlaceholderExpansion} 을 상속해야 해서
 * 리플렉션으로는 등록조차 할 수 없다 — DiscordSRV·Floodgate 와 다른 점이다. 대신
 * {@code provided} 스코프라 jar 에 들어가지 않고, PlaceholderAPI 가 없는 서버에서는
 * {@link #tryRegister} 가 이 클래스를 아예 건드리지 않는다.
 *
 * <p>스코어보드가 초당 여러 번 부르는 자리다. <b>디스크를 보지 않는다</b> — 값은 전부
 * 메모리 캐시({@link PetStore})와 레지스트리에서 나온다.
 *
 * <table>
 *   <caption>제공하는 키</caption>
 *   <tr><td>{@code owned}</td><td>보유 마릿수</td></tr>
 *   <tr><td>{@code owned_max}</td><td>보유 한도. 무제한이면 {@code ∞}</td></tr>
 *   <tr><td>{@code active}</td><td>소환 중인 마릿수</td></tr>
 *   <tr><td>{@code active_max}</td><td>동시 소환 한도</td></tr>
 *   <tr><td>{@code pet_name}</td><td>소환 중인 첫 펫의 이름</td></tr>
 *   <tr><td>{@code pet_rarity}</td><td>그 펫의 등급 (S · A …)</td></tr>
 *   <tr><td>{@code pet_stage}</td><td>생애주기 (BABY · ADULT · PIG)</td></tr>
 *   <tr><td>{@code pet_growth} · {@code pet_growth_max} · {@code pet_growth_percent}</td><td>성장도</td></tr>
 * </table>
 */
public final class PetPlaceholders extends PlaceholderExpansion {

    /** 소환 중인 펫이 없을 때 돌려줄 값. 빈 문자열이면 스코어보드 줄이 무너져 보인다. */
    private static final String NONE = "-";

    private final Plugin plugin;
    private final PetStore store;
    private final PetRegistry registry;
    private final PetCatalog catalog;
    private final GrowthService growth;
    private final PetLimits limits;

    private PetPlaceholders(final Plugin plugin,
                            final PetStore store,
                            final PetRegistry registry,
                            final PetCatalog catalog,
                            final GrowthService growth,
                            final PetLimits limits) {
        this.plugin = plugin;
        this.store = store;
        this.registry = registry;
        this.catalog = catalog;
        this.growth = growth;
        this.limits = limits;
    }

    /**
     * PlaceholderAPI 가 있으면 확장을 등록한다.
     *
     * <p>클래스 참조가 이 메서드 안에서만 일어나도록 감싼다. 없는 서버에서는
     * {@code isPluginEnabled} 가 먼저 막아서 {@link NoClassDefFoundError} 가 나지 않는다.
     *
     * @return 등록했으면 true
     */
    public static boolean tryRegister(final Plugin plugin,
                                      final PetStore store,
                                      final PetRegistry registry,
                                      final PetCatalog catalog,
                                      final GrowthService growth,
                                      final PetLimits limits) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return false;
        }
        try {
            final boolean ok = new PetPlaceholders(plugin, store, registry, catalog, growth, limits)
                .register();
            plugin.getLogger().info(ok
                ? "PlaceholderAPI 연동됨. %betterpets_...% 를 쓸 수 있습니다."
                : "PlaceholderAPI 확장 등록에 실패했습니다.");
            return ok;
        } catch (final LinkageError | RuntimeException error) {
            plugin.getLogger().warning("PlaceholderAPI 연동에 실패했습니다: " + error);
            return false;
        }
    }

    @Override
    public @NotNull String getIdentifier() {
        return "betterpets";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /**
     * 플러그인이 리로드돼도 확장을 살려 둔다.
     *
     * <p>기본값(false)이면 PlaceholderAPI 가 리로드 때 확장을 내려버려서, 그 뒤로
     * 플레이스홀더가 원문 그대로 화면에 남는다.
     */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(final Player player, final @NotNull String params) {
        if (player == null) {
            return null;    // null 이면 PlaceholderAPI 가 원문을 그대로 둔다
        }
        final String key = params.toLowerCase(java.util.Locale.ROOT);

        // 마릿수 계열은 소환 중인 펫이 없어도 답할 수 있다. 먼저 처리한다.
        switch (key) {
            case "owned" -> {
                return String.valueOf(store.owned(player.getUniqueId()).size());
            }
            case "owned_max" -> {
                return limits.ownedUnlimited() ? "∞" : String.valueOf(limits.maxOwned());
            }
            case "active" -> {
                return String.valueOf(registry.countOf(player.getUniqueId()));
            }
            case "active_max" -> {
                return limits.activeUnlimited() ? "∞" : String.valueOf(limits.maxActive());
            }
            default -> { /* 아래 펫별 키로 넘어간다 */ }
        }

        final List<ActivePet> active = registry.allOf(player.getUniqueId());
        if (active.isEmpty()) {
            return NONE;
        }
        final PetData data = active.getFirst().data();
        final PetType type = catalog.type(data.typeId()).orElse(null);

        return switch (key) {
            case "pet_name" -> strip(data.displayNameOr(
                type == null ? data.typeId() : type.displayName()));
            case "pet_type" -> data.typeId();
            case "pet_rarity" -> type == null ? NONE : type.rarity().name();
            case "pet_rarity_name" -> type == null ? NONE : strip(type.rarity().displayName());
            case "pet_stage" -> data.stage().name();
            case "pet_growth" -> String.valueOf(data.growth());
            case "pet_growth_max" -> String.valueOf(growth.maxOf(data));
            case "pet_growth_percent" -> Math.round(growth.progressOf(data) * 100) + "%";
            case "pet_growth_stage" -> String.valueOf(data.growthStage());
            case "pet_growth_stage_max" -> String.valueOf(growth.maxStage());
            case "pet_id" -> data.petId().toString().substring(0, 8);
            // 모르는 키에 "-" 를 주면 오타가 조용히 묻힌다. null 이어야 원문이 남아 눈에 띈다.
            default -> null;
        };
    }

    /** 태그를 걷어내되, 결과가 비면 {@link #NONE} 을 준다 — 빈 줄은 스코어보드를 무너뜨린다. */
    private static String strip(final String raw) {
        final String stripped = Tags.strip(raw);
        return stripped.isBlank() ? NONE : stripped;
    }
}
