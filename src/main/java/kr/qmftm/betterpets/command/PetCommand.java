package kr.qmftm.betterpets.command;

import kr.qmftm.betterpets.config.Messages;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.gui.PetMenuFactory;
import kr.qmftm.betterpets.runtime.RideController;
import kr.qmftm.betterpets.service.PetService;
import kr.qmftm.betterpets.storage.PetStore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code /pet} — 플레이어용 명령. */
public final class PetCommand implements CommandExecutor, TabCompleter {

    private final PetService pets;
    private final PetStore store;
    private final PetMenuFactory menus;
    private final RideController rides;
    private final Messages messages;

    public PetCommand(final PetService pets,
                      final PetStore store,
                      final PetMenuFactory menus,
                      final RideController rides,
                      final Messages messages) {
        this.pets = pets;
        this.store = store;
        this.menus = menus;
        this.rides = rides;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(final @NotNull CommandSender sender,
                             final @NotNull Command command,
                             final @NotNull String label,
                             final String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length == 0) {
            openBox(player);
            return true;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "summon" -> summon(player, args);
            case "dismiss" -> dismiss(player);
            case "dismount" -> dismount(player);
            case "rename" -> rename(player, args);
            default -> messages.send(player, "command.unknown");
        }
        return true;
    }

    private void openBox(final Player player) {
        player.openInventory(menus.box(sorted(player.getUniqueId()), 0));
    }

    private void summon(final Player player, final String[] args) {
        if (args.length < 2) {
            messages.send(player, "command.summon-usage");
            return;
        }
        final Optional<PetData> target = resolve(player, args[1]);
        if (target.isEmpty()) {
            messages.send(player, "pet.not-found");
            return;
        }
        final PetService.SummonResult result = pets.summon(player, target.get());
        switch (result) {
            case OK -> messages.send(player, "pet.summoned");
            case NOT_SUMMONABLE -> messages.send(player, "pet.not-summonable");
            case MODEL_MISSING -> messages.send(player, "pet.model-missing");
            case UNKNOWN_TYPE -> messages.send(player, "pet.unknown-type");
        }
    }

    private void dismiss(final Player player) {
        messages.send(player, pets.dismiss(player) ? "pet.dismissed" : "pet.none-active");
    }

    private void dismount(final Player player) {
        if (rides.isRiding(player)) {
            rides.stop(player);
            messages.send(player, "ride.stopped");
        } else {
            messages.send(player, "ride.not-riding");
        }
    }

    private void rename(final Player player, final String[] args) {
        if (args.length < 2) {
            messages.send(player, "command.rename-usage");
            return;
        }
        final Optional<PetData> active = store.activePet(player.getUniqueId());
        if (active.isEmpty()) {
            messages.send(player, "pet.none-active");
            return;
        }
        final String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        if (name.length() > 32) {
            messages.send(player, "pet.name-too-long");
            return;
        }
        active.get().nickname(name);
        store.saveAsync(active.get());
        messages.send(player, "pet.renamed", "name", name);
    }

    /**
     * 펫 식별자를 해석한다. 전체 UUID 든 앞 8자리든 받는다 —
     * 채팅에 UUID 를 통째로 치게 하는 건 가혹하다.
     */
    private Optional<PetData> resolve(final Player player, final String token) {
        final String needle = token.toLowerCase(java.util.Locale.ROOT);
        return store.owned(player.getUniqueId()).stream()
            .filter(pet -> pet.petId().toString().toLowerCase(java.util.Locale.ROOT).startsWith(needle))
            .findFirst();
    }

    private List<PetData> sorted(final UUID ownerId) {
        final List<PetData> list = new ArrayList<>(store.owned(ownerId));
        list.sort(Comparator.comparingLong(PetData::acquiredAt));
        return list;
    }

    @Override
    public List<String> onTabComplete(final @NotNull CommandSender sender,
                                      final @NotNull Command command,
                                      final @NotNull String label,
                                      final String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("summon", "dismiss", "dismount", "rename").stream()
                .filter(option -> option.startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
                .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("summon")) {
            return store.owned(player.getUniqueId()).stream()
                .map(pet -> pet.petId().toString().substring(0, 8))
                .toList();
        }
        return List.of();
    }
}
