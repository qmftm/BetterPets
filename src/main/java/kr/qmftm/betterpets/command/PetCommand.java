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
        player.openInventory(menus.box(player.getUniqueId(), sorted(player.getUniqueId()), 0));
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
            case OK_REPLACED -> messages.send(player, "pet.summoned-replaced");
            case MODEL_MISSING -> messages.send(player, "pet.model-missing");
            case UNKNOWN_TYPE -> messages.send(player, "pet.unknown-type");
        }
    }

    /** 소환 중인 펫을 전부 돌려보낸다. 한 마리만 넣고 싶으면 보관함에서 고른다. */
    private void dismiss(final Player player) {
        final int count = pets.dismissAll(player);
        if (count == 0) {
            messages.send(player, "pet.none-active");
        } else {
            messages.send(player, "pet.dismissed-count", "count", String.valueOf(count));
        }
    }

    private void dismount(final Player player) {
        if (rides.isRiding(player)) {
            rides.stop(player);
            messages.send(player, "ride.stopped");
        } else {
            messages.send(player, "ride.not-riding");
        }
    }

    /**
     * {@code /pet rename [펫id] <이름>}.
     *
     * <p>펫 id 는 생략할 수 있다 — 한 마리만 소환 중이면 그 펫을 고른다. 여러 마리를
     * 데리고 다닐 수 있게 되면서 "소환 중인 펫"이 한 마리라는 보장이 사라졌으므로,
     * 그때는 id 를 받아야 어느 쪽을 바꿀지 정해진다. 보관함의 이름 변경 버튼이
     * id 가 박힌 명령을 그대로 안내한다.
     */
    private void rename(final Player player, final String[] args) {
        if (args.length < 2) {
            messages.send(player, "command.rename-usage");
            return;
        }
        // 첫 인자가 소유한 펫의 id 앞자리이고 이름이 뒤에 더 있으면 그쪽을 대상으로 본다.
        Optional<PetData> target = args.length >= 3 ? resolve(player, args[1]) : Optional.empty();
        final int nameFrom = target.isPresent() ? 2 : 1;

        if (target.isEmpty()) {
            final List<PetData> active = store.activePets(player.getUniqueId());
            if (active.isEmpty()) {
                messages.send(player, "pet.none-active");
                return;
            }
            if (active.size() > 1) {
                messages.send(player, "pet.rename-pick");
                return;
            }
            target = Optional.of(active.getFirst());
        }

        final String name = String.join(" ", java.util.Arrays.copyOfRange(args, nameFrom, args.length));
        if (name.length() > 32) {
            messages.send(player, "pet.name-too-long");
            return;
        }
        target.get().nickname(name);
        store.saveAsync(target.get());
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
        if (args.length == 2 && (args[0].equalsIgnoreCase("summon")
            || args[0].equalsIgnoreCase("rename"))) {
            return store.owned(player.getUniqueId()).stream()
                .map(pet -> pet.petId().toString().substring(0, 8))
                .toList();
        }
        return List.of();
    }
}
