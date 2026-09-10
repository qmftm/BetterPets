package kr.qmftm.betterpets.command;

import kr.qmftm.betterpets.config.Messages;
import kr.qmftm.betterpets.config.PetCatalog;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.item.PetItems;
import kr.qmftm.betterpets.render.PetRenderer;
import kr.qmftm.betterpets.runtime.ActivePet;
import kr.qmftm.betterpets.runtime.PetRegistry;
import kr.qmftm.betterpets.service.GrowthCatchUp;
import kr.qmftm.betterpets.service.PetService;
import kr.qmftm.betterpets.storage.PetStore;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** {@code /petadmin} — 관리자 명령. */
public final class PetAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
        List.of("give", "egg", "feed", "growth", "reload", "debug");

    private final PetService pets;
    private final PetStore store;
    private final PetCatalog catalog;
    private final PetItems items;
    private final PetRegistry registry;
    private final PetRenderer renderer;
    private final Messages messages;
    private final Runnable reloadAction;

    /**
     * 성장 뒤처리를 여기에 또 적지 않기 위해서다.
     *
     * <p>성장이 일어나는 경로가 셋(급여·시간 경과·이 명령)인데, 마무리(단계 전이·모델·
     * 능력·알림)를 각각에 적으면 셋이 갈린다. 실제로 이 명령만 주인에게 아무 말도
     * 하지 않고 있었다 — 관리자가 성장도를 부어 성체로 만들어도 본인은 몰랐다.
     */
    private final GrowthCatchUp catchUp;

    public PetAdminCommand(final PetService pets,
                           final PetStore store,
                           final PetCatalog catalog,
                           final PetItems items,
                           final PetRegistry registry,
                           final PetRenderer renderer,
                           final Messages messages,
                           final GrowthCatchUp catchUp,
                           final Runnable reloadAction) {
        this.pets = pets;
        this.store = store;
        this.catalog = catalog;
        this.items = items;
        this.registry = registry;
        this.renderer = renderer;
        this.messages = messages;
        this.catchUp = catchUp;
        this.reloadAction = reloadAction;
    }

    @Override
    public boolean onCommand(final @NotNull CommandSender sender,
                             final @NotNull Command command,
                             final @NotNull String label,
                             final String @NotNull [] args) {
        if (args.length == 0) {
            messages.send(sender, "admin.usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "egg" -> egg(sender, args);
            case "feed" -> feed(sender, args);
            case "growth" -> growth(sender, args);
            case "reload" -> reload(sender);
            case "debug" -> debug(sender);
            default -> messages.send(sender, "admin.usage");
        }
        return true;
    }

    private void give(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            messages.send(sender, "admin.usage-give");
            return;
        }
        final Player target = sender.getServer().getPlayer(args[1]);
        if (target == null) {
            messages.send(sender, "admin.player-not-found");
            return;
        }
        if (catalog.type(args[2]).isEmpty()) {
            messages.send(sender, "admin.unknown-pet-type", "id", args[2]);
            return;
        }
        if (pets.grantPet(target, args[2]).isEmpty()) {
            messages.send(sender, "admin.box-full", "player", target.getName());
            return;
        }
        messages.send(sender, "admin.given", "player", target.getName(), "type", args[2]);
    }

    private void egg(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            messages.send(sender, "admin.usage-egg");
            return;
        }
        final Player target = sender.getServer().getPlayer(args[1]);
        if (target == null) {
            messages.send(sender, "admin.player-not-found");
            return;
        }
        final var definition = catalog.egg(args[2]);
        if (definition.isEmpty()) {
            messages.send(sender, "admin.unknown-egg", "id", args[2]);
            return;
        }
        final int amount = args.length > 3 ? parseInt(args[3], 1) : 1;
        deliver(sender, target, items.createEgg(definition.get(), amount));
        messages.send(sender, "admin.egg-given", "player", target.getName(), "id", args[2]);
    }

    private void feed(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            messages.send(sender, "admin.usage-feed");
            return;
        }
        final Player target = sender.getServer().getPlayer(args[1]);
        if (target == null) {
            messages.send(sender, "admin.player-not-found");
            return;
        }
        final var definition = catalog.feed(args[2]);
        if (definition.isEmpty()) {
            messages.send(sender, "admin.unknown-feed", "id", args[2]);
            return;
        }
        final int amount = args.length > 3 ? parseInt(args[3], 1) : 1;
        deliver(sender, target, items.createFeed(definition.get(), amount));
        messages.send(sender, "admin.feed-given", "player", target.getName(), "id", args[2]);
    }

    private void growth(final CommandSender sender, final String[] args) {
        if (args.length < 4) {
            messages.send(sender, "admin.usage-growth");
            return;
        }
        final Player target = sender.getServer().getPlayer(args[1]);
        if (target == null) {
            messages.send(sender, "admin.player-not-found");
            return;
        }
        final String needle = args[2].toLowerCase(Locale.ROOT);
        final Optional<PetData> pet = store.owned(target.getUniqueId()).stream()
            .filter(p -> p.petId().toString().toLowerCase(Locale.ROOT).startsWith(needle))
            .findFirst();
        if (pet.isEmpty()) {
            messages.send(sender, "pet.not-found");
            return;
        }
        // 0 이하는 GrowthCurve.feed 가 조용히 무시한다. 그대로 두면 "성장도 -50 을
        // 줬습니다" 라고 답하면서 아무 일도 안 하게 된다. 오타를 되돌려준다.
        final int amount = parseInt(args[3], 0);
        if (amount <= 0) {
            messages.send(sender, "admin.growth-not-positive", "amount", args[3]);
            return;
        }
        final PetData grown = pet.get();
        // 급여 경로와 같은 순서다. 경과분을 먼저 반영해야 준 만큼이 정확히 얹힌다.
        pets.growth().refresh(grown);
        grown.addGrowth(amount, pets.growth().maxOf(grown));
        store.saveAsync(grown);

        // 나머지 마무리(단계 전이·모델·능력·주인 알림·방송)는 성장이 일어나는 다른 두
        // 경로와 같은 곳에 맡긴다. 여기에 따로 적으면 셋이 갈린다.
        final boolean wasOut = registry.of(target.getUniqueId(), grown.petId()).isPresent();
        catchUp.one(target, grown);
        if (wasOut && registry.of(target.getUniqueId(), grown.petId()).isEmpty()) {
            // 나와 있던 펫이 사라졌다 = 진화한 종류의 모델을 못 붙였다는 뜻이다.
            // 주인에게는 catchUp 이 알렸고, 여기서는 설정을 고칠 수 있는 사람에게 알린다.
            messages.send(sender, "admin.growth-model-missing", "type", grown.typeId());
        }
        messages.send(sender, "admin.growth-given", "amount", String.valueOf(amount));
    }

    /**
     * 아이템을 건넨다.
     *
     * <p><b>{@code addItem} 의 반환값을 버리면 안 된다.</b> 인벤토리가 꽉 찼을 때
     * 들어가지 못한 아이템이 거기 담겨 오는데, 그걸 무시하면 아이템은 사라지고
     * 화면에는 "줬습니다"가 뜬다. 관리자는 준 줄 알고, 플레이어는 못 받는다.
     */
    private void deliver(final CommandSender sender, final Player target,
                         final org.bukkit.inventory.ItemStack stack) {
        final var leftover = target.getInventory().addItem(stack);
        if (leftover.isEmpty()) {
            return;
        }
        // 발밑에 떨어뜨린다. 거절하고 되돌리면 관리자가 다시 쳐야 하는데,
        // 그 사이에 인벤토리가 비어 있으리라는 보장이 없다.
        leftover.values().forEach(rest ->
            target.getWorld().dropItemNaturally(target.getLocation(), rest));
        messages.send(sender, "admin.inventory-full", "player", target.getName());
    }

    /**
     * 설정을 다시 읽는다.
     *
     * <p><b>이미 소환된 펫은 예전 정의를 그대로 들고 있다.</b> {@code ActivePet} 은 소환
     * 시점의 {@link kr.qmftm.betterpets.domain.PetType} 을 붙들고, 이동 속도는 그때
     * 계산해 굳혀둔다. 여기서 전부 재소환하면 화면이 반짝이고, 무엇보다 <b>설정 오타
     * 하나로 전원의 펫이 사라진다</b> — 새 모델 id 가 없으면 재소환이 실패한다.
     * 그래서 건드리지 않고, 대신 그 사실을 알린다. 모르고 "왜 안 바뀌지"로 헤매는 것이
     * 이 기능에서 제일 흔한 오해다.
     */
    private void reload(final CommandSender sender) {
        reloadAction.run();
        final int stillOut = registry.size();
        if (stillOut > 0) {
            messages.send(sender, "admin.reloaded-active-note", "count", String.valueOf(stillOut));
        }
        final List<String> problems = catalog.problems();
        if (problems.isEmpty()) {
            messages.send(sender, "admin.reloaded",
                "pets", String.valueOf(catalog.types().size()),
                "eggs", String.valueOf(catalog.eggs().size()),
                "feeds", String.valueOf(catalog.feeds().size()));
        } else {
            messages.send(sender, "admin.reloaded-with-problems",
                "count", String.valueOf(problems.size()));
            problems.forEach(problem -> sender.sendMessage(Component.text(" - " + problem)));
        }
    }

    /**
     * 누수 진단.
     *
     * <p>세 숫자가 일치해야 정상이다. 어긋나면 트래커나 캐리어가 새고 있다는 뜻이고,
     * 그대로 두면 유령 모델이 쌓인다.
     */
    private void debug(final CommandSender sender) {
        final int active = registry.size();
        final int trackers = renderer.activeTrackerCount();
        final long carriers = registry.all().stream()
            .filter(pet -> !pet.carrier().isDead() && pet.carrier().isValid())
            .count();

        // active 는 서버 전체 합이다. 1인 한도와 직접 비교하면 안 되므로 따로 적는다.
        final long owners = registry.all().stream().map(ActivePet::ownerId).distinct().count();
        final var limits = pets.limits();
        sender.sendMessage(messages.bare("admin.debug-pets",
            "active", String.valueOf(active),
            "owners", String.valueOf(owners),
            "max-active", limits.maxActiveLabel(),
            "max-owned", limits.maxOwnedLabel()));
        sender.sendMessage(messages.bare("admin.debug-carriers", "count", String.valueOf(carriers)));
        sender.sendMessage(messages.bare("admin.debug-trackers", "count", String.valueOf(trackers)));
        sender.sendMessage(messages.bare(active == carriers && active == trackers
            ? "admin.debug-consistent" : "admin.debug-leak"));

        // 연동은 전부 선택 사항이라 "왜 Discord 로 안 가지" 같은 질문이 나온다.
        // 무엇이 붙었는지 여기서 한 번에 보여준다.
        sender.sendMessage(messages.bare("admin.debug-integrations",
            "list", plugged(sender, "DiscordSRV") + ", "
                + plugged(sender, "PlaceholderAPI") + ", "
                + plugged(sender, "floodgate") + ", "
                + plugged(sender, "GeyserModelEngine")));
    }

    /** 플러그인 하나의 활성 여부를 "이름(O/X)" 로 적는다. 이름은 번역 대상이 아니다. */
    private static String plugged(final CommandSender sender, final String name) {
        return name + (sender.getServer().getPluginManager().isPluginEnabled(name) ? "(O)" : "(X)");
    }

    private static int parseInt(final String raw, final int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (final NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(final @NotNull CommandSender sender,
                                      final @NotNull Command command,
                                      final @NotNull String label,
                                      final String @NotNull [] args) {
        if (args.length == 1) {
            return matching(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload") && !args[0].equalsIgnoreCase("debug")) {
            return matching(
                sender.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give")) {
                return matching(catalog.types().keySet(), args[2]);
            }
            if (args[0].equalsIgnoreCase("egg")) {
                return matching(catalog.eggs().keySet(), args[2]);
            }
            if (args[0].equalsIgnoreCase("feed")) {
                return matching(catalog.feeds().keySet(), args[2]);
            }
            if (args[0].equalsIgnoreCase("growth")) {
                // /pet summon 은 펫 id 를 완성해 주는데 여기만 손으로 치게 하고 있었다.
                // 여덟 자리 16진수를 옮겨 적는 건 오타를 부르는 일이다.
                final Player owner = sender.getServer().getPlayer(args[1]);
                if (owner != null) {
                    return matching(store.owned(owner.getUniqueId()).stream()
                        .map(pet -> pet.petId().toString().substring(0, 8))
                        .toList(), args[2]);
                }
            }
        }
        return List.of();
    }

    /**
     * 입력한 앞자리로 걸러 정렬해 준다.
     *
     * <p>전부 돌려주면 펫 종류가 스무 개쯤 되는 서버에서 제안 목록이 화면을 덮고,
     * 무엇을 더 쳐야 좁혀지는지 알 수 없다. {@code /pet} 쪽도 같은 문제가 있었다.
     */
    private static List<String> matching(final java.util.Collection<String> options, final String typed) {
        final String needle = typed.toLowerCase(Locale.ROOT);
        return options.stream()
            .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(needle))
            .sorted()
            .toList();
    }
}
