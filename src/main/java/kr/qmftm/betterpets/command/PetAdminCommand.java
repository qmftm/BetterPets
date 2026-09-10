package kr.qmftm.betterpets.command;

import kr.qmftm.betterpets.config.Messages;
import kr.qmftm.betterpets.config.PetCatalog;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.item.PetItems;
import kr.qmftm.betterpets.render.PetRenderer;
import kr.qmftm.betterpets.runtime.ActivePet;
import kr.qmftm.betterpets.runtime.PetRegistry;
import kr.qmftm.betterpets.service.PetService;
import kr.qmftm.betterpets.storage.PetStore;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** {@code /petadmin} — 관리자 명령. */
public final class PetAdminCommand implements CommandExecutor, TabCompleter {

    private final PetService pets;
    private final PetStore store;
    private final PetCatalog catalog;
    private final PetItems items;
    private final PetRegistry registry;
    private final PetRenderer renderer;
    private final Messages messages;
    private final Runnable reloadAction;

    public PetAdminCommand(final PetService pets,
                           final PetStore store,
                           final PetCatalog catalog,
                           final PetItems items,
                           final PetRegistry registry,
                           final PetRenderer renderer,
                           final Messages messages,
                           final Runnable reloadAction) {
        this.pets = pets;
        this.store = store;
        this.catalog = catalog;
        this.items = items;
        this.registry = registry;
        this.renderer = renderer;
        this.messages = messages;
        this.reloadAction = reloadAction;
    }

    @Override
    public boolean onCommand(final @NotNull CommandSender sender,
                             final @NotNull Command command,
                             final @NotNull String label,
                             final String @NotNull [] args) {
        if (args.length == 0) {
            sender.sendMessage("/petadmin give|egg|feed|growth|reload|debug");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "egg" -> egg(sender, args);
            case "feed" -> feed(sender, args);
            case "growth" -> growth(sender, args);
            case "reload" -> reload(sender);
            case "debug" -> debug(sender);
            default -> sender.sendMessage("/petadmin give|egg|feed|growth|reload|debug");
        }
        return true;
    }

    private void give(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage("/petadmin give <플레이어> <펫종류>");
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
            sender.sendMessage("/petadmin egg <플레이어> <알id> [개수]");
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
        target.getInventory().addItem(items.createEgg(definition.get(), amount));
        messages.send(sender, "admin.egg-given", "player", target.getName(), "id", args[2]);
    }

    private void feed(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage("/petadmin feed <플레이어> <먹이id> [개수]");
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
        target.getInventory().addItem(items.createFeed(definition.get(), amount));
        messages.send(sender, "admin.feed-given", "player", target.getName(), "id", args[2]);
    }

    private void growth(final CommandSender sender, final String[] args) {
        if (args.length < 4) {
            sender.sendMessage("/petadmin growth <플레이어> <펫id앞자리> <양>");
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
        final int amount = parseInt(args[3], 0);
        pet.get().addGrowth(amount, pets.growth().maxOf(pet.get()));
        pets.growth().promoteIfGrown(pet.get());
        store.saveAsync(pet.get());
        messages.send(sender, "admin.growth-given", "amount", String.valueOf(amount));
    }

    private void reload(final CommandSender sender) {
        reloadAction.run();
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
        sender.sendMessage(Component.text("활성 펫: " + active + " (소유자 " + owners
            + "명, 1인 동시 소환 한도 "
            + (limits.activeUnlimited() ? "무제한" : limits.maxActive() + "마리")
            + ", 1인 보유 한도 "
            + (limits.ownedUnlimited() ? "무제한" : limits.maxOwned() + "마리") + ")"));
        sender.sendMessage(Component.text("살아있는 캐리어: " + carriers));
        sender.sendMessage(Component.text("BetterModel 트래커: " + trackers));
        sender.sendMessage(Component.text(
            active == carriers && active == trackers
                ? "→ 일치. 누수 없음."
                : "→ 불일치! 누수 의심. 트래커 수가 활성 펫 수와 달라야 할 이유가 없습니다."));

        // 연동은 전부 선택 사항이라 "왜 Discord 로 안 가지" 같은 질문이 나온다.
        // 무엇이 붙었는지 여기서 한 번에 보여준다.
        sender.sendMessage(Component.text("연동: "
            + plugged(sender, "DiscordSRV") + ", "
            + plugged(sender, "PlaceholderAPI") + ", "
            + plugged(sender, "floodgate") + ", "
            + plugged(sender, "GeyserModelEngine")));
    }

    /** 플러그인 하나의 활성 여부를 "이름(O/X)" 로 적는다. */
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
            return List.of("give", "egg", "feed", "growth", "reload", "debug").stream()
                .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .toList();
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload") && !args[0].equalsIgnoreCase("debug")) {
            return sender.getServer().getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give")) {
                return new ArrayList<>(catalog.types().keySet());
            }
            if (args[0].equalsIgnoreCase("egg")) {
                return new ArrayList<>(catalog.eggs().keySet());
            }
            if (args[0].equalsIgnoreCase("feed")) {
                return new ArrayList<>(catalog.feeds().keySet());
            }
        }
        return List.of();
    }
}
