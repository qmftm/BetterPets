package kr.qmftm.betterpets;

import kr.qmftm.betterpets.ability.AbilityRegistry;
import kr.qmftm.betterpets.command.PetAdminCommand;
import kr.qmftm.betterpets.command.PetCommand;
import kr.qmftm.betterpets.config.Messages;
import kr.qmftm.betterpets.config.PetCatalog;
import kr.qmftm.betterpets.domain.PetLimits;
import kr.qmftm.betterpets.domain.Rarity;
import kr.qmftm.betterpets.gui.PetMenuFactory;
import kr.qmftm.betterpets.integration.BedrockSupport;
import kr.qmftm.betterpets.integration.DiscordBridge;
import kr.qmftm.betterpets.integration.PetPlaceholders;
import kr.qmftm.betterpets.item.PetItems;
import kr.qmftm.betterpets.listener.AbilityTriggerListener;
import kr.qmftm.betterpets.listener.InteractionListener;
import kr.qmftm.betterpets.listener.MenuListener;
import kr.qmftm.betterpets.listener.SessionListener;
import kr.qmftm.betterpets.render.BetterModelRenderer;
import kr.qmftm.betterpets.render.PetRenderer;
import kr.qmftm.betterpets.runtime.CarrierFactory;
import kr.qmftm.betterpets.runtime.PetRegistry;
import kr.qmftm.betterpets.runtime.PetTicker;
import kr.qmftm.betterpets.runtime.RideController;
import kr.qmftm.betterpets.service.AbilityService;
import kr.qmftm.betterpets.service.BroadcastService;
import kr.qmftm.betterpets.service.GrowthCatchUp;
import kr.qmftm.betterpets.service.GrowthService;
import kr.qmftm.betterpets.service.PetService;
import kr.qmftm.betterpets.storage.PetStore;
import kr.qmftm.betterpets.storage.YamlPetRepository;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * BetterPets 진입점.
 *
 * <p><b>이 클래스는 배선만 한다.</b> 로직은 각 계층에 있다. 참고 구현(betterpets-paper)은
 * 진입점 하나가 5,034줄이고 매니저가 3,792줄로 코드의 69%가 두 파일에 몰려 있다.
 * 여기서 한 파일이 800줄을 넘으면 분리 신호로 본다.
 */
public final class BetterPetsPlugin extends JavaPlugin {

    private PetRenderer renderer;
    private PetCatalog catalog;
    private Messages messages;
    private PetStore store;
    private PetRegistry registry;
    private RideController rides;
    private PetTicker ticker;
    private PetService pets;
    private CarrierFactory carriers;
    private BedrockSupport bedrock;
    private DiscordBridge discord;
    private BroadcastService broadcasts;

    @Override
    public void onEnable() {
        // paper-plugin.yml 에서 required: true 로 걸어뒀지만, 로드는 됐어도 초기화에
        // 실패한 상태일 수 있다. 그 경우 첫 소환에서야 터지는데 진단이 훨씬 어렵다.
        if (!getServer().getPluginManager().isPluginEnabled("BetterModel")) {
            getLogger().severe("BetterModel 이 활성화되지 않았습니다. BetterPets 를 비활성화합니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            renderer = new BetterModelRenderer();
        } catch (final LinkageError error) {
            // BetterModel 의 메이저 버전이 바뀌어 시그니처가 사라졌을 때 여기로 온다.
            // 서버를 통째로 죽이는 대신 이 플러그인만 내린다.
            getLogger().severe("BetterModel API 가 예상과 다릅니다. 3.4.1 이상이 필요합니다: " + error.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        saveResourceIfMissing("lang/ko_kr.yml");
        saveResourceIfMissing("lang/en_us.yml");
        saveResourceIfMissing("items.yml");
        saveResourceIfMissing("rarity.yml");
        saveResourceIfMissing("pets/wolf.yml");
        saveResourceIfMissing("pets/dragon.yml");
        saveResourceIfMissing("pets/pig.yml");
        saveResourceIfMissing("pets/hatchling.yml");

        final AbilityRegistry abilityRegistry = new AbilityRegistry(this);

        messages = new Messages();
        catalog = new PetCatalog();
        reloadDefinitions(abilityRegistry);

        store = new PetStore(
            new YamlPetRepository(new File(getDataFolder(), "playerdata"), getLogger()),
            getLogger());

        registry = new PetRegistry();
        carriers = new CarrierFactory(this);
        rides = new RideController(this);

        final GrowthService growth = new GrowthService(catalog, readGrowthTuning());

        bedrock = new BedrockSupport(this);
        bedrock.detect();

        discord = new DiscordBridge(this,
            getConfig().getBoolean("integrations.discord.enabled", true),
            getConfig().getString("integrations.discord.channel", "global"));
        discord.connect();

        broadcasts = new BroadcastService(getServer(), messages, discord, readBroadcastRules());

        final AbilityService abilities = new AbilityService(this, abilityRegistry);
        pets = new PetService(
            catalog, store, renderer, carriers, registry, rides, abilities, growth, readLimits());

        final PetItems items = new PetItems(this, catalog, messages, growth);
        final PetMenuFactory menus = new PetMenuFactory(
            catalog, growth, registry, pets, messages);

        // 시간이 흘러 일어난 성장을 확인하는 자리. 접속·보관함·목록·틱이 모두 여기를 지난다.
        final GrowthCatchUp catchUp = new GrowthCatchUp(store, pets, growth, broadcasts, messages);

        // 기동 시 청소. 정상 종료였다면 지울 게 없고, 크래시였다면 여기서 정리된다.
        final int orphanCarriers = carriers.purgeOrphans(this);
        final int orphanMounts = rides.purgeOrphans();
        if (orphanCarriers + orphanMounts > 0) {
            getLogger().info("이전 세션에서 남은 엔티티를 정리했습니다: 캐리어 "
                + orphanCarriers + "개, 마운트 " + orphanMounts + "개");
        }

        registerListeners(items, menus, abilities, growth, broadcasts, catchUp);
        registerCommands(items, menus, abilityRegistry, catchUp, growth);

        ticker = new PetTicker(this, registry, rides, pets, catchUp);
        ticker.start();

        // 스코어보드·홀로그램에서 쓸 %betterpets_...%. 없으면 건너뛴다.
        // 한도는 서비스에 물어보게 넘긴다 — 리로드로 바뀐 값이 바로 보여야 한다.
        //
        // 이 if 를 지우면 안 된다. PetPlaceholders 는 PlaceholderExpansion 을 상속해서,
        // static 메서드 하나만 호출해도 그 순간 클래스 로딩·링킹이 걸리며 상위 클래스까지
        // 물고 들어간다 — PlaceholderAPI 가 없는 서버에서 NoClassDefFoundError 로 onEnable
        // 이 통째로 죽는 걸 실기에서 확인했다. tryRegister 안의 isPluginEnabled 검사는
        // 이미 클래스가 로드된 뒤라 늦다; 호출 여부를 가르는 검사는 호출자 쪽에 있어야 한다.
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            PetPlaceholders.tryRegister(this, store, registry, catalog, growth, pets);
        } else {
            getLogger().info("PlaceholderAPI 가 없어 %betterpets_...% 를 건너뜁니다.");
        }

        // 리로드로 들어온 경우 이미 접속해 있는 플레이어의 데이터를 읽어야 한다.
        for (final var online : getServer().getOnlinePlayers()) {
            abilities.purge(online);
            // 접속 이벤트와 같은 마무리를 해 줘야 한다. 리로드로 들어온 플레이어만
            // 성장 확인을 건너뛰면, 그 자리에서만 펫이 아기로 굳는다.
            store.loadAsync(online.getUniqueId(), () -> {
                if (isEnabled()) {
                    getServer().getScheduler().runTask(this, () -> {
                        if (online.isOnline()) {
                            catchUp.all(online);
                        }
                    });
                }
            });
        }

        getLogger().info("BetterPets 활성화됨. 펫 " + catalog.types().size()
            + "종, 알 " + catalog.eggs().size() + "종, 먹이 " + catalog.feeds().size()
            + "종, 모델 " + renderer.availableModels().size() + "개.");
    }

    @Override
    public void onDisable() {
        // 정리 순서가 중요하다. 탑승 → 펫 → 저장 순으로 내려간다.
        if (ticker != null) {
            ticker.stop();
        }
        if (rides != null) {
            rides.stopAll();
        }
        if (registry != null) {
            registry.closeAll();    // 트래커와 캐리어를 닫는다
        }
        if (store != null) {
            store.shutdown();       // ★ 동기 대기. 비동기면 마지막 쓰기를 잃는다
        }
    }

    private void registerListeners(final PetItems items,
                                   final PetMenuFactory menus,
                                   final AbilityService abilities,
                                   final GrowthService growth,
                                   final BroadcastService broadcasts,
                                   final GrowthCatchUp catchUp) {
        final var manager = getServer().getPluginManager();
        manager.registerEvents(
            new SessionListener(this, store, pets, registry, abilities, catchUp), this);
        manager.registerEvents(new InteractionListener(pets, store, items, registry, rides, growth,
            messages, broadcasts, bedrock,
            () -> getConfig().getBoolean("integrations.bedrock.skip-mount-confirm", true)), this);
        manager.registerEvents(new MenuListener(pets, store, menus, messages, catchUp), this);
        manager.registerEvents(new AbilityTriggerListener(registry, abilities), this);
    }

    private void registerCommands(final PetItems items,
                                  final PetMenuFactory menus,
                                  final AbilityRegistry abilityRegistry,
                                  final GrowthCatchUp catchUp,
                                  final GrowthService growth) {
        bind("pet", "betterpets.use", new PetCommand(pets, store, menus, rides, messages, catchUp));
        bind("betterpets", List.of("bp"), "betterpets.admin", new PetAdminCommand(pets, store, catalog, items, registry, renderer,
            messages, catchUp, () -> {
                reloadConfig();
                reloadDefinitions(abilityRegistry);
                // 아래 다섯은 값을 들고 있는 쪽이라 다시 밀어 넣어야 반영된다.
                // 빠뜨리면 "설정을 다시 읽었습니다" 가 거짓말이 된다.
                // 새로 무언가를 들고 있게 만들었다면 여기도 같이 늘려야 한다 —
                // 더 나은 답은 아예 들고 있지 않는 것이다(PetItems 가 그렇게 한다).
                rides.reloadTuning();
                pets.limits(readLimits());
                broadcasts.rules(readBroadcastRules());
                // Discord 는 채널 이름을 붙박아 두고 있었다. 채널을 바꾸고 리로드해도
                // 예전 채널로 계속 나갔고, enabled: false 로 꺼도 계속 나갔다.
                discord.reload(
                    getConfig().getBoolean("integrations.discord.enabled", true),
                    getConfig().getString("integrations.discord.channel", "global"));
                // 성장·기믹도 마찬가지였다. growth.max-stage 를 고치고 리로드해도
                // 예전 값으로 돌았다 — 기동 코드는 리로드 때마다 overfeed.count 를
                // 다시 읽어 경고까지 내면서, 정작 쓰는 쪽에는 안 밀어 넣고 있었다.
                growth.tuning(readGrowthTuning());
            }, getDataFolder()));
    }

    /**
     * paper-plugin.yml 로 선언된 플러그인은 {@link JavaPlugin#getCommand} 를 못 쓴다 —
     * 기동 중 호출하면 {@code UnsupportedOperationException} 이 나서 onEnable 이 통째로
     * 죽는다(첫 실기 테스트에서 이걸로 플러그인이 아예 안 켜졌다). 기존 {@link CommandExecutor}
     * 를 새로 고치는 대신 {@link BasicCommand} 로 감싸 {@link #registerCommand} 로 붙인다.
     */
    private void bind(final String name, final String permission, final Object handler) {
        bind(name, List.of(), permission, handler);
    }

    /** 별칭이 있는 명령을 붙인다. {@code /betterpets} 를 {@code /bp} 로 줄여 쓰는 경우가 여기 해당한다. */
    private void bind(final String name, final Collection<String> aliases,
                      final String permission, final Object handler) {
        final CommandExecutor executor = (CommandExecutor) handler;
        final TabCompleter completer = handler instanceof TabCompleter tc ? tc : null;
        registerCommand(name, aliases, new LegacyCommandAdapter(name, permission, executor, completer));
    }

    /** {@link CommandSourceStack} ↔ {@link CommandSender} 를 잇는 어댑터. Command 객체는 두 커맨드 모두 안 쓴다. */
    private static final class LegacyCommandAdapter implements BasicCommand {

        private final Command legacy;
        private final String permission;
        private final CommandExecutor executor;
        private final TabCompleter completer;

        LegacyCommandAdapter(final String name, final String permission,
                             final CommandExecutor executor, final TabCompleter completer) {
            this.legacy = new Command(name) {
                @Override
                public boolean execute(final CommandSender sender, final String label, final String[] args) {
                    return false;
                }
            };
            this.permission = permission;
            this.executor = executor;
            this.completer = completer;
        }

        @Override
        public void execute(final CommandSourceStack source, final String[] args) {
            executor.onCommand(source.getSender(), legacy, legacy.getName(), args);
        }

        @Override
        public Collection<String> suggest(final CommandSourceStack source, final String[] args) {
            if (completer == null) {
                return List.of();
            }
            // 레거시 Bukkit onTabComplete 는 "/pet " 처럼 아무것도 안 친 자리를 빈 문자열
            // 하나짜리 배열([""])로 받는다 — PetCommand/PetAdminCommand 의 args.length == 1
            // 분기가 전부 이 관례를 전제한다. BasicCommand 경로는 그 자리를 빈 배열([])로
            // 주는데, 그대로 넘기면 그 분기가 안 걸려서 "/pet " 뒤 첫 하위 명령 제안이
            // 통째로 안 뜬다 — 실기에서 이걸로 자동완성이 죽어 있었다.
            final String[] effective = args.length == 0 ? new String[] {""} : args;
            final List<String> result =
                completer.onTabComplete(source.getSender(), legacy, legacy.getName(), effective);
            return result == null ? List.of() : result;
        }

        @Override
        public String permission() {
            return permission;
        }
    }

    /** 설정을 다시 읽는다. 문제가 있으면 전부 모아 한 번에 보고한다. */
    private void reloadDefinitions(final AbilityRegistry abilityRegistry) {
        messages.load(this, getDataFolder(), getConfig().getString("language", Messages.FALLBACK_LANGUAGE));
        catalog.load(getDataFolder(), abilityRegistry, renderer);

        final var problems = catalog.problems();
        if (!problems.isEmpty()) {
            getLogger().warning("설정에서 " + problems.size() + "개의 문제를 찾았습니다:");
            problems.forEach(problem -> getLogger().warning("  - " + problem));
        }

        // 0 이하는 "첫 급여에 바로 돼지"가 된다. 코드가 2로 막지만, 관리자가 의도한
        // 건 대개 '끄기'이므로 무엇이 일어나는지 알려준다.
        final int overfeedCount = getConfig().getInt("gimmick.overfeed.count", 10);
        if (getConfig().getBoolean("gimmick.overfeed.enabled", true) && overfeedCount < 2) {
            getLogger().warning("gimmick.overfeed.count 가 " + overfeedCount
                + " 입니다. 2 미만은 먹이 몇 번에 바로 돼지가 된다는 뜻이라 2로 올려 씁니다."
                + " 기믹을 끄려면 gimmick.overfeed.enabled: false 로 하세요.");
        }

        // 과급식 기믹이 가리키는 펫이 없으면 상태만 '돼지'가 되고 모습은 그대로 남는다.
        // 조용히 넘어가면 "돼지가 됐다는데 왜 드래곤이지"로 헤매게 된다.
        final String pigType = getConfig().getString("gimmick.overfeed.becomes", "pig");
        if (getConfig().getBoolean("gimmick.overfeed.enabled", true)
            && pigType != null && !pigType.isBlank()
            && catalog.type(pigType).isEmpty()) {
            getLogger().warning("gimmick.overfeed.becomes 가 가리키는 펫 '" + pigType
                + "' 가 없습니다. 과급식해도 모습은 그대로 남습니다.");
        }
    }

    private PetLimits readLimits() {
        return new PetLimits(
            getConfig().getInt("pets.max-owned", 20),
            getConfig().getInt("pets.max-active", 1));
    }

    /** 성장·기믹 설정. 기동과 리로드가 같은 함수를 쓴다. */
    private GrowthService.Tuning readGrowthTuning() {
        return new GrowthService.Tuning(
            getConfig().getInt("growth.feed-amount", 10),
            getConfig().getBoolean("gimmick.overfeed.enabled", true),
            getConfig().getInt("gimmick.overfeed.count", 10),
            getConfig().getLong("gimmick.overfeed.window-seconds", 60) * 1000L,
            getConfig().getString("gimmick.overfeed.becomes", "pig"),
            getConfig().getInt("growth.max-stage", 1),
            getConfig().getInt("growth.fullness.min-gain", 5),
            getConfig().getInt("growth.fullness.max-gain", 15),
            getConfig().getInt("growth.fullness.max", 100),
            getConfig().getLong("growth.fullness.decay-seconds", 30) * 1000L);
    }

    private BroadcastService.Rules readBroadcastRules() {
        final boolean enabled = getConfig().getBoolean("broadcast.enabled", true);
        final int minStage = getConfig().getInt("broadcast.min-growth-stage", 1);

        // 성장 단계는 growth.max-stage 를 넘지 못한다. 문턱이 그보다 높으면 어떤 펫도
        // 조건을 넘을 수 없어, 알림이 통째로 꺼진 것과 같아진다 — 껐다는 자각 없이.
        // enabled: false 와 증상이 똑같아서, 로그가 없으면 원인을 찾을 길이 없다.
        final int maxStage = Math.max(1, getConfig().getInt("growth.max-stage", 1));
        if (enabled && minStage > maxStage) {
            getLogger().warning("broadcast.min-growth-stage(" + minStage
                + ") 가 growth.max-stage(" + maxStage + ") 보다 큽니다."
                + " 성장 단계가 그 값에 닿을 수 없어 전체 알림이 하나도 나가지 않습니다.");
        }

        return new BroadcastService.Rules(
            enabled,
            broadcastFloor(),
            minStage,
            getConfig().getBoolean("broadcast.on-obtain", true),
            getConfig().getBoolean("broadcast.on-grown", true),
            getConfig().getBoolean("broadcast.on-stage-up", false),
            getConfig().getBoolean("broadcast.sound", true));
    }

    /**
     * 방송 문턱 등급을 읽는다.
     *
     * <p>잘못된 값을 조용히 A 로 바꾸면 "S로 적었는데 왜 다 뜨지"로 헤매게 된다.
     * 오타는 드러나야 고쳐진다.
     */
    private Rarity broadcastFloor() {
        final String raw = getConfig().getString("broadcast.min-rarity", "A");
        return Rarity.parse(raw).orElseGet(() -> {
            getLogger().warning("broadcast.min-rarity 값 '" + raw
                + "' 은 등급이 아닙니다. D C B A S 중 하나여야 합니다. A 로 진행합니다.");
            return Rarity.A;
        });
    }

    private void saveResourceIfMissing(final String path) {
        if (!new File(getDataFolder(), path).exists()) {
            saveResource(path, false);
        }
    }

    public PetRenderer renderer() {
        return renderer;
    }
}
