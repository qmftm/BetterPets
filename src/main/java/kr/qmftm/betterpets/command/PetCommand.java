package kr.qmftm.betterpets.command;

import kr.qmftm.betterpets.config.Messages;
import kr.qmftm.betterpets.config.Tags;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetType;
import kr.qmftm.betterpets.gui.Menus;
import kr.qmftm.betterpets.gui.PetMenuFactory;
import kr.qmftm.betterpets.runtime.RideController;
import kr.qmftm.betterpets.service.GrowthCatchUp;
import kr.qmftm.betterpets.service.PetService;
import kr.qmftm.betterpets.storage.PetStore;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** {@code /pet} — 플레이어용 명령. */
public final class PetCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
        List.of("summon", "dismiss", "dismount", "rename", "list", "help");

    /** 두 번째 인자로 펫 id 를 받는 하위 명령들. */
    private static final Set<String> PET_ID_ARG = Set.of("summon", "dismiss", "rename");

    private final PetService pets;
    private final PetStore store;
    private final PetMenuFactory menus;
    private final RideController rides;
    private final Messages messages;

    /** 목록을 보여주기 전에 시간 경과분을 반영한다 — 보관함의 펫도 자란다. */
    private final GrowthCatchUp catchUp;

    public PetCommand(final PetService pets,
                      final PetStore store,
                      final PetMenuFactory menus,
                      final RideController rides,
                      final Messages messages,
                      final GrowthCatchUp catchUp) {
        this.pets = pets;
        this.store = store;
        this.menus = menus;
        this.rides = rides;
        this.messages = messages;
        this.catchUp = catchUp;
    }

    @Override
    public boolean onCommand(final @NotNull CommandSender sender,
                             final @NotNull Command command,
                             final @NotNull String label,
                             final String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        if (args.length == 0) {
            openBox(player);
            return true;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "summon" -> summon(player, args);
            case "dismiss" -> dismiss(player, args);
            case "dismount" -> dismount(player);
            case "rename" -> rename(player, args);
            case "list" -> list(player);
            case "help" -> help(player);
            default -> messages.send(player, "command.unknown");
        }
        return true;
    }

    private void openBox(final Player player) {
        // 열기 전에 맞춘다. 보관함에 넣어둔 펫은 틱을 안 받아서, 여기서 확인하지 않으면
        // 성장도가 상한에 붙은 채 아기로 보인다.
        catchUp.all(player);
        player.openInventory(menus.box(
            player.getUniqueId(), store.owned(player.getUniqueId()), Menus.View.first()));
    }

    private void summon(final Player player, final String[] args) {
        if (args.length < 2) {
            messages.send(player, "command.summon-usage");
            return;
        }
        final Optional<PetData> target = resolve(player, args[1]);
        if (target.isEmpty()) {
            messages.send(player, "pet.not-found");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            return;
        }
        final PetService.SummonResult result = pets.summon(player, target.get());
        switch (result) {
            case OK, OK_REPLACED -> {
                messages.send(player, result == PetService.SummonResult.OK
                    ? "pet.summoned" : "pet.summoned-replaced");
                player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_ITEM_GIVEN, 1.0f, 1.0f);
            }
            case MODEL_MISSING -> {
                messages.send(player, "pet.model-missing");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            }
            case UNKNOWN_TYPE -> {
                messages.send(player, "pet.unknown-type");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            }
        }
    }

    /**
     * {@code /pet dismiss [펫id]} — id 를 주면 그 한 마리만, 없으면 전부 돌려보낸다.
     *
     * <p>여러 마리를 데리고 다닐 수 있게 된 뒤로 "한 마리만 넣기"를 채팅에서 할 수 없었다.
     * 보관함을 열어 고르는 수밖에 없었는데, 타고 있는 중이면 그것도 번거롭다.
     */
    private void dismiss(final Player player, final String[] args) {
        if (args.length >= 2) {
            final Optional<PetData> target = resolve(player, args[1]);
            if (target.isEmpty()) {
                messages.send(player, "pet.not-found");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
                return;
            }
            final boolean dismissed = pets.dismiss(player, target.get().petId());
            messages.send(player, dismissed ? "pet.dismissed" : "pet.none-active");
            if (dismissed) {
                player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_ITEM_TAKEN, 1.0f, 1.0f);
            }
            return;
        }
        final int count = pets.dismissAll(player);
        if (count == 0) {
            messages.send(player, "pet.none-active");
        } else {
            messages.send(player, "pet.dismissed-count", "count", String.valueOf(count));
            player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_ITEM_TAKEN, 1.0f, 1.0f);
        }
    }

    /**
     * {@code /pet list} — 채팅으로 보는 목록.
     *
     * <p>보관함 GUI 가 있는데도 두는 이유는 <b>클릭해서 바로 소환</b>할 수 있어서다.
     * 이름을 클릭하면 {@code /pet summon <id>} 가 실행된다 — GUI 를 열고 쪽을 넘겨
     * 아이콘을 찾는 것보다 빠를 때가 있고, 여덟 자리 id 를 손으로 옮겨 적을 일도 없앤다.
     */
    private void list(final Player player) {
        catchUp.all(player);
        final List<PetData> owned = menus.ordered(store.owned(player.getUniqueId()));
        if (owned.isEmpty()) {
            messages.send(player, "pet.list-empty");
            return;
        }
        messages.send(player, "pet.list-header", "max", pets.limits().ownedLabel(owned.size()));

        for (final PetData pet : owned) {
            final String id = pet.petId().toString().substring(0, 8);
            final PetType type = pets.catalog().type(pet.typeId()).orElse(null);
            final String name = Tags.strip(type == null
                ? pet.typeId() : pet.displayNameOr(type.displayName()));
            final String rarity = type == null ? "?" : type.rarity().name();

            // 줄은 짧게, 자세한 건 호버로. 20마리를 훑을 때 한 줄이 길면 눈이 못 따라간다.
            player.sendMessage(messages.bare("pet.list-entry",
                    "mark", pet.active() ? "●" : "○",
                    "id", id,
                    "name", name,
                    "rarity", rarity)
                .clickEvent(ClickEvent.runCommand("/pet summon " + id))
                .hoverEvent(HoverEvent.showText(messages.bare("pet.list-hover",
                    "name", name,
                    "growth", pet.growth() + "/" + pets.growth().maxOf(pet)))));
        }
    }

    /** {@code /pet help} — 무엇을 할 수 있는지. 하위 명령이 늘어난 만큼 필요해졌다. */
    private void help(final Player player) {
        for (final String key : List.of("summon", "dismiss", "dismount", "rename", "list")) {
            messages.send(player, "help." + key);
        }
    }

    private void dismount(final Player player) {
        if (rides.isRiding(player)) {
            rides.stop(player);
            // 채팅 문구는 없다 — 내렸다는 건 화면으로 이미 보인다. 소리만 알린다.
            player.playSound(player.getLocation(), Sound.ENTITY_HORSE_LAND, 0.8f, 1.0f);
        } else {
            messages.send(player, "ride.not-riding");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
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
        Optional<PetData> target = args.length >= 3 && looksLikeId(args[1])
            ? resolve(player, args[1])
            : Optional.empty();
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

        // 길이는 태그를 걷어낸 뒤에 잰다. 그래야 "화면에 보이는 글자 수"로 제한이 걸린다 —
        // 태그를 포함해 재면 <gradient:...> 하나로 한도를 다 쓴 것처럼 거절당한다.
        final String name = PetData.sanitizeNickname(
            String.join(" ", java.util.Arrays.copyOfRange(args, nameFrom, args.length)));
        if (name == null) {
            // 태그만 적었다. 조용히 별명을 지우는 것보다 다시 치게 하는 편이 낫다.
            messages.send(player, "command.rename-usage");
            return;
        }
        if (name.length() > 32) {
            messages.send(player, "pet.name-too-long");
            return;
        }
        target.get().nickname(name);
        store.saveAsync(target.get());
        messages.send(player, "pet.renamed", "name", name);
    }

    /**
     * 이 토큰을 펫 id 로 볼 만한가.
     *
     * <p><b>{@code /pet rename} 에만 쓴다.</b> 거기서는 첫 인자가 id 일 수도 있고 이름의
     * 첫 단어일 수도 있어서 추측해야 한다. 그런데 그냥 앞자리 일치로 두면
     * {@code /pet rename a b} 가 "이름을 'a b' 로" 가 아니라 "id 가 a 로 시작하는 펫을
     * 'b' 로" 가 된다 — 한 글자로 남의 펫 이름을 엉뚱하게 바꾸는 셈이다.
     *
     * <p>화면에 보여주는 id 가 여덟 자리라 최소 네 자리를 요구한다. 16진수 네 자리가
     * 우연히 이름의 첫 단어와 겹칠 확률은 무시할 만하고, 겹치더라도 그 값이 실제
     * 소유한 펫의 id 앞자리와 같아야 한다.
     */
    static boolean looksLikeId(final String token) {
        return token.length() >= 4 && token.matches("[0-9a-fA-F-]+");
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

    @Override
    public List<String> onTabComplete(final @NotNull CommandSender sender,
                                      final @NotNull Command command,
                                      final @NotNull String label,
                                      final String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                .filter(option -> option.startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
                .toList();
        }
        if (args.length == 2 && PET_ID_ARG.contains(args[0].toLowerCase(java.util.Locale.ROOT))) {
            // 입력한 앞자리로 걸러서 준다. 전부 돌려주면 20마리 서버에서 제안 목록이
            // 화면을 덮고, 무엇을 더 쳐야 좁혀지는지 알 수 없다.
            final String typed = args[1].toLowerCase(java.util.Locale.ROOT);
            return store.owned(player.getUniqueId()).stream()
                .map(pet -> pet.petId().toString().substring(0, 8))
                .filter(id -> id.startsWith(typed))
                .sorted()
                .toList();
        }
        return List.of();
    }
}
