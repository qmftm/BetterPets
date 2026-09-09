package kr.qmftm.betterpets;

import kr.qmftm.betterpets.ability.AbilityRegistry;
import kr.qmftm.betterpets.command.PetAdminCommand;
import kr.qmftm.betterpets.command.PetCommand;
import kr.qmftm.betterpets.config.Messages;
import kr.qmftm.betterpets.config.PetCatalog;
import kr.qmftm.betterpets.gui.PetMenuFactory;
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
import kr.qmftm.betterpets.service.GrowthService;
import kr.qmftm.betterpets.service.PetService;
import kr.qmftm.betterpets.storage.PetStore;
import kr.qmftm.betterpets.storage.YamlPetRepository;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

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
        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("items.yml");
        saveResourceIfMissing("pets/wolf.yml");
        saveResourceIfMissing("pets/dragon.yml");
        saveResourceIfMissing("pets/pig.yml");

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

        final GrowthService growth = new GrowthService(
            catalog,
            getConfig().getInt("growth.feed-amount", 10),
            getConfig().getBoolean("gimmick.overfeed.enabled", true),
            getConfig().getInt("gimmick.overfeed.count", 10),
            getConfig().getLong("gimmick.overfeed.window-seconds", 60) * 1000L,
            getConfig().getString("gimmick.overfeed.becomes", "pig"),
            getConfig().getInt("growth.max-stage", 1));

        final AbilityService abilities = new AbilityService(this, abilityRegistry);
        pets = new PetService(catalog, store, renderer, carriers, registry, rides, abilities, growth);

        final PetItems items = new PetItems(this);
        final PetMenuFactory menus = new PetMenuFactory(catalog, growth);

        // 기동 시 청소. 정상 종료였다면 지울 게 없고, 크래시였다면 여기서 정리된다.
        final int orphanCarriers = carriers.purgeOrphans(this);
        final int orphanMounts = rides.purgeOrphans();
        if (orphanCarriers + orphanMounts > 0) {
            getLogger().info("이전 세션에서 남은 엔티티를 정리했습니다: 캐리어 "
                + orphanCarriers + "개, 마운트 " + orphanMounts + "개");
        }

        registerListeners(items, menus, abilities, growth);
        registerCommands(items, menus, abilityRegistry);

        ticker = new PetTicker(this, registry, rides, growth, store, pets);
        ticker.start();

        // 리로드로 들어온 경우 이미 접속해 있는 플레이어의 데이터를 읽어야 한다.
        for (final var online : getServer().getOnlinePlayers()) {
            abilities.purge(online);
            store.loadAsync(online.getUniqueId(), null);
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
                                   final GrowthService growth) {
        final var manager = getServer().getPluginManager();
        manager.registerEvents(new SessionListener(store, pets, registry, abilities), this);
        manager.registerEvents(
            new InteractionListener(pets, store, items, registry, rides, growth, messages), this);
        manager.registerEvents(new MenuListener(pets, store, menus, messages), this);
        manager.registerEvents(new AbilityTriggerListener(registry, abilities), this);
    }

    private void registerCommands(final PetItems items,
                                  final PetMenuFactory menus,
                                  final AbilityRegistry abilityRegistry) {
        bind("pet", new PetCommand(pets, store, menus, rides, messages));
        bind("petadmin", new PetAdminCommand(pets, store, catalog, items, registry, renderer,
            messages, () -> {
                reloadConfig();
                reloadDefinitions(abilityRegistry);
            }));
    }

    private void bind(final String name, final Object handler) {
        final PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("명령어 '" + name + "' 가 paper-plugin.yml 에 없습니다.");
            return;
        }
        command.setExecutor((org.bukkit.command.CommandExecutor) handler);
        if (handler instanceof org.bukkit.command.TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }

    /** 설정을 다시 읽는다. 문제가 있으면 전부 모아 한 번에 보고한다. */
    private void reloadDefinitions(final AbilityRegistry abilityRegistry) {
        messages.load(new File(getDataFolder(), "messages.yml"));
        catalog.load(getDataFolder(), abilityRegistry, renderer);

        final var problems = catalog.problems();
        if (!problems.isEmpty()) {
            getLogger().warning("설정에서 " + problems.size() + "개의 문제를 찾았습니다:");
            problems.forEach(problem -> getLogger().warning("  - " + problem));
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

    private void saveResourceIfMissing(final String path) {
        if (!new File(getDataFolder(), path).exists()) {
            saveResource(path, false);
        }
    }

    public PetRenderer renderer() {
        return renderer;
    }
}
