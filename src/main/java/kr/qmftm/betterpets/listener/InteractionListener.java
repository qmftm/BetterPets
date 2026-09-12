package kr.qmftm.betterpets.listener;

import kr.qmftm.betterpets.config.Messages;
import kr.qmftm.betterpets.config.Tags;
import kr.qmftm.betterpets.domain.EggDefinition;
import kr.qmftm.betterpets.domain.FeedDefinition;
import kr.qmftm.betterpets.domain.LifeStage;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetType;
import kr.qmftm.betterpets.domain.RideMode;
import kr.qmftm.betterpets.integration.BedrockSupport;
import kr.qmftm.betterpets.item.PetItems;
import kr.qmftm.betterpets.runtime.ActivePet;
import kr.qmftm.betterpets.runtime.MovementController;
import kr.qmftm.betterpets.runtime.PetRegistry;
import kr.qmftm.betterpets.runtime.RideController;
import kr.qmftm.betterpets.service.BroadcastService;
import kr.qmftm.betterpets.service.GrowthService;
import kr.qmftm.betterpets.service.PetService;
import kr.qmftm.betterpets.storage.PetStore;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** 알 우클릭, 펫 우클릭(먹이·탑승), 탑승 조작 입력. */
public final class InteractionListener implements Listener {

    /** 비행 이륙 확인 창. 이 안에 한 번 더 우클릭해야 뜬다. */
    private static final long MOUNT_CONFIRM_MILLIS = 3_000L;

    private final PetService pets;
    private final PetStore store;
    private final PetItems items;
    private final PetRegistry registry;
    private final RideController rides;
    private final GrowthService growth;
    private final Messages messages;
    private final BroadcastService broadcasts;
    private final BedrockSupport bedrock;

    /**
     * Bedrock 플레이어에게 비행 이륙 확인을 건너뛸지. 터치로는 두 번째 우클릭이 어렵다.
     *
     * <p>값을 복사해 들고 있으면 {@code /petadmin reload} 때 다시 밀어 넣어야 한다.
     * 매번 물어보면 그럴 일이 없다 — 여기는 틱 루프가 아니라 우클릭 한 번이라
     * 설정을 읽는 비용이 문제가 되지 않는다.
     */
    private final java.util.function.BooleanSupplier skipMountConfirmOnBedrock;

    private final Map<UUID, Long> mountConfirms = new ConcurrentHashMap<>();

    public InteractionListener(final PetService pets,
                               final PetStore store,
                               final PetItems items,
                               final PetRegistry registry,
                               final RideController rides,
                               final GrowthService growth,
                               final Messages messages,
                               final BroadcastService broadcasts,
                               final BedrockSupport bedrock,
                               final java.util.function.BooleanSupplier skipMountConfirmOnBedrock) {
        this.pets = pets;
        this.store = store;
        this.items = items;
        this.registry = registry;
        this.rides = rides;
        this.growth = growth;
        this.messages = messages;
        this.broadcasts = broadcasts;
        this.bedrock = bedrock;
        this.skipMountConfirmOnBedrock = skipMountConfirmOnBedrock;
    }

    /**
     * 알 아이템 우클릭.
     *
     * <p><b>{@code EquipmentSlot.HAND} 만 처리한다.</b> 안 그러면 오프핸드로도 이벤트가 와서
     * 한 번의 우클릭에 알이 두 개 소비된다.
     *
     * <p><b>블록을 클릭했을 때는 블록에 양보한다.</b> 그러지 않으면 알을 들고 상자를
     * 열려다 알이 까진다 — 아이템을 잃은 것이고 되돌릴 수도 없다. 그래서 알은 허공에
     * 대고 쓰거나, 블록 쪽이면 스니크를 요구한다(바닐라도 스니크면 블록 상호작용을
     * 건너뛰므로 따로 배울 게 없다).
     *
     * <p><b>"열리는 블록만" 가려내려 하지 않는다.</b> {@code isInteractable()} 이
     * 그 질문에 답해 주는 것처럼 보이지만, {@code Material} 과 {@code BlockType} 양쪽
     * 모두 deprecated 다 — 답이 들고 있는 아이템과 블록 상태에 따라 달라져서
     * 플랫폼 스스로 믿을 수 없다고 표시해 둔 것이다. 틀린 답의 대가가 <b>알 하나가
     * 사라지는 것</b>이라, 못 미더운 판정에 기대느니 규칙을 단순하게 둔다.
     * 실패하면 "아무 일도 안 일어남"이고, 그건 되돌릴 수 있다.
     *
     * <p><b>블록에 양보할 때도 아이템의 바닐라 동작은 막는다.</b> 알 재질을 스폰 알로
     * 두면서 드러난 문제다 — 그냥 {@code return} 만 하면 이벤트가 취소되지 않은 채
     * 그대로 진행돼서, 스니크 없이 돌바닥에 우클릭하는 순간 <b>진짜 셜커가 소환된다.</b>
     * {@code setCancelled(true)} 는 블록 쪽 상호작용(상자 열기)까지 같이 막아버려서
     * 쓸 수 없다 — {@code setUseItemInHand(DENY)} 로 아이템 쪽만 죽이고 블록 쪽은
     * 그대로 둔다.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEggUse(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!event.getAction().isRightClick()) {
            return;
        }
        final ItemStack held = event.getItem();
        final Optional<String> eggId = items.eggIdOf(held);
        if (eggId.isEmpty()) {
            return;
        }
        // 블록을 클릭했으면 블록이 먼저다. 스니크 중이면 바닐라도 블록 상호작용을
        // 건너뛰므로 그때는 알을 쓰려는 게 맞다.
        if (event.getClickedBlock() != null && !event.getPlayer().isSneaking()) {
            event.setUseItemInHand(Event.Result.DENY);
            return;
        }
        event.setCancelled(true);

        final Player player = event.getPlayer();
        final Optional<EggDefinition> definition = pets.catalog().egg(eggId.get());
        if (definition.isEmpty()) {
            messages.send(player, "egg.unknown");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            return;
        }
        final String typeId = definition.get().roll(ThreadLocalRandom.current());
        if (typeId == null || pets.catalog().type(typeId).isEmpty()) {
            messages.send(player, "egg.broken-definition");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            return;
        }

        // 보유 한도가 찼으면 알을 먹어치우지 않는다. 아이템을 잃는 게 제일 나쁜 결과다.
        final Optional<PetData> granted = pets.grantPet(player, typeId);
        if (granted.isEmpty()) {
            messages.send(player, "pet.box-full",
                "max", String.valueOf(pets.limits().maxOwned()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            return;
        }
        held.setAmount(held.getAmount() - 1);   // 성공한 뒤에만 소비한다

        final PetType type = pets.catalog().type(typeId).orElseThrow();
        messages.send(player, "egg.opened", "name", Tags.strip(type.displayName()));
        player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1.0f, 1.2f);
        broadcasts.onObtained(player, granted.get(), type);
    }

    /**
     * 펫 우클릭 — 먹이를 들고 있으면 급여, 아니면 탑승 시도.
     *
     * <p><b>캐리어를 건드리는 상호작용은 무조건 먼저 취소한다.</b> 캐리어는 보이지 않는
     * {@code Allay} 인데, 알레이의 바닐라 동작은 <b>우클릭한 사람의 손에 든 아이템을
     * 받아 가는 것</b>이다. AI 를 꺼도 이 상호작용은 그대로 돈다. 취소를 조건 뒤로
     * 미루고 있어서 구멍이 둘 있었다:
     *
     * <ul>
     *   <li><b>오프핸드</b> — {@code HAND} 가 아니면 그냥 return 했다. 오프핸드에 든
     *       아이템이 알레이에게 넘어간다
     *   <li><b>남의 펫</b> — 소유자가 아니면 그냥 return 했다. 아무나 남의 펫을 클릭해
     *       자기 아이템을 잃는다. 반대로 말하면, 남의 펫을 아이템 먹이는 함정으로 쓸 수 있다
     * </ul>
     *
     * <p>GUI 클릭 핸들러와 같은 규칙이다 — 취소를 로직 뒤로 미루면 그 사이의 모든
     * 조기 return 이 구멍이 된다.
     */
    @EventHandler
    public void onPetClick(final PlayerInteractEntityEvent event) {
        final Optional<ActivePet> clicked = registry.byCarrier(event.getRightClicked());
        if (clicked.isEmpty()) {
            return;     // 우리 캐리어가 아니다. 남의 엔티티를 막을 이유가 없다
        }
        event.setCancelled(true);   // ★ 무조건 먼저

        if (event.getHand() != EquipmentSlot.HAND) {
            return;     // 한 번의 우클릭을 두 번 처리하지 않는다. 취소는 이미 했다
        }
        final Player player = event.getPlayer();
        if (!clicked.get().ownerId().equals(player.getUniqueId())) {
            return;     // 남의 펫은 만질 수 없다. 조용히 아무 일도 하지 않는다
        }

        final ActivePet pet = clicked.get();
        final ItemStack held = player.getInventory().getItemInMainHand();

        final Optional<String> feedId = items.feedIdOf(held);
        if (feedId.isPresent()) {
            feed(player, pet, held, feedId.get());
            return;
        }
        tryRide(player, pet);
    }

    private void feed(final Player player, final ActivePet pet, final ItemStack held, final String feedId) {
        final PetData data = pet.data();
        if (data.stage() == LifeStage.ADULT || data.stage() == LifeStage.PIG) {
            messages.send(player, "feed.already-grown");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            return;
        }
        // 설정에서 지워진 먹이를 들고 있을 수 있다. 아이템을 먹어치우지 않고 알려준다.
        final Optional<FeedDefinition> definition = pets.catalog().feed(feedId);
        if (definition.isEmpty()) {
            messages.send(player, "feed.unknown");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            return;
        }
        final GrowthService.FeedResult result = growth.feed(data, definition.get());
        held.setAmount(held.getAmount() - 1);
        store.saveAsync(data);

        // 먹는 동작은 종류가 바뀌기 전에 얹어야 한다. 아래 refreshAfterGrowth 가
        // 모델을 다시 붙이면 이 오버레이는 어차피 사라진다.
        pet.animation().overlay(kr.qmftm.betterpets.domain.PetType.AnimationSet.EAT);

        // 방송에는 pet.type() 을 쓰면 안 된다. ActivePet 은 소환 시점의 종류를 붙들고
        // 있어서, 성장 단계 진화로 종류가 바뀌면 "늑대가 2단계가 됐습니다" 처럼
        // 바뀌기 전 이름을 부르게 된다. 지금 데이터가 가리키는 종류를 다시 찾는다.
        final PetType current = pets.catalog().type(data.typeId()).orElse(pet.type());

        switch (result) {
            case STAGE_UP -> {
                messages.send(player, "feed.stage-up",
                    "stage", String.valueOf(data.growthStage()),
                    "max", String.valueOf(growth.maxStage()));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                broadcasts.onStageUp(player, data, current);
            }
            case GREW_UP -> {
                messages.send(player, "feed.grew-up");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                broadcasts.onGrown(player, data, current);
            }
            case BECAME_PIG -> {
                messages.send(player, "feed.became-pig");
                player.playSound(player.getLocation(), Sound.ENTITY_PIG_AMBIENT, 1.0f, 1.0f);
            }
            case FED -> {
                messages.send(player, "feed.fed",
                    "growth", String.valueOf(data.growth()),
                    "max", String.valueOf(growth.maxOf(data)));
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8f, 1.0f);
            }
        }

        // 종류가 바뀌었으면 모델을, 성체가 됐으면 능력을 지금 상태에 맞춘다.
        if (pets.refreshAfterGrowth(player, data) == PetService.RefreshResult.DETACHED) {
            // 진화한 종류의 모델이 없다. 먹인 사람 눈앞에서 펫이 사라진 상태라
            // 그냥 넘어가면 "먹였더니 펫이 없어졌다"는 신고가 된다.
            messages.send(player, "pet.model-missing");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
        }
    }

    /**
     * 탑승 시도.
     *
     * <p>지상 탑승은 즉시, <b>비행은 두 번째 우클릭을 요구한다.</b> 실수로 이륙하면
     * 그대로 하늘로 날아가버려서 성가시다.
     */
    private void tryRide(final Player player, final ActivePet pet) {
        final PetData data = pet.data();
        final PetType type = pet.type();

        if (!type.ride().canRide()) {
            messages.send(player, "ride.not-rideable");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            return;
        }
        // 생애주기는 탑승을 막지 않는다 — pets/*.yml 의 ride 값이 유일한 기준이다.
        // 진화(성장)는 이 판정과 별개로 그대로 일어난다.
        if (rides.isRiding(player)) {
            return;     // 비행 중 우클릭은 무시한다. 하차는 스니크 전용
        }

        // FLY 종류라도 비행 추첨에 실패한 개체는 걷는 탑승까지만 된다.
        final boolean flying = type.ride().effective(data.canFly()) == RideMode.FLY;
        // Bedrock 은 터치 조작이라 "3초 안에 한 번 더 우클릭"을 맞히기가 어렵다.
        // 자바 플레이어의 실수 방지는 그대로 두고, 이쪽만 건너뛴다.
        if (flying && !(skipMountConfirmOnBedrock.getAsBoolean() && bedrock.isBedrock(player))) {
            final long now = System.currentTimeMillis();
            final Long armed = mountConfirms.get(player.getUniqueId());
            if (armed == null || now > armed) {
                mountConfirms.put(player.getUniqueId(), now + MOUNT_CONFIRM_MILLIS);
                messages.send(player, "ride.confirm");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);
                return;
            }
            mountConfirms.remove(player.getUniqueId());
        }

        final double speed = type.rideSpeed();
        if (rides.start(player, pet.petId(), pet.carrier().getLocation(), flying, speed)) {
            // 비행이면 fly 애니메이션이 걸리게 모드를 나눠 준다.
            pet.movement().mode(flying
                ? MovementController.Mode.RIDDEN_FLYING
                : MovementController.Mode.RIDDEN);
            messages.send(player, flying ? "ride.started-flying" : "ride.started-ground");
            player.playSound(player.getLocation(),
                flying ? Sound.ENTITY_PHANTOM_FLAP : Sound.ENTITY_HORSE_SADDLE, 1.0f, 1.0f);
        } else {
            messages.send(player, "ride.failed");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
        }
    }

    /** 탑승 조작 입력을 컨트롤러에 넘긴다. 실제 이동은 틱 루프가 한다. */
    @EventHandler
    public void onInput(final PlayerInputEvent event) {
        if (rides.isRiding(event.getPlayer())) {
            rides.input(event.getPlayer(), event.getInput());
        }
    }

    /**
     * 퇴장 시 이륙 확인 창을 버린다.
     *
     * <p>지우는 곳이 "실제로 이륙했을 때" 하나뿐이었다. 확인만 띄우고 안 탄 사람의
     * 항목은 서버가 살아 있는 내내 남는다는 뜻이다 — 3초면 의미가 없어지는 값인데
     * 지도에는 상한이 없다. 과급식 카운터에서 똑같은 걸 한 번 겪었다.
     */
    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        mountConfirms.remove(event.getPlayer().getUniqueId());
    }

    /** 스니크 하차. 입력 이벤트가 오지 않는 상황을 위한 보조 경로다. */
    @EventHandler
    public void onSneak(final PlayerToggleSneakEvent event) {
        if (event.isSneaking() && rides.isRiding(event.getPlayer())) {
            rides.stop(event.getPlayer());
            messages.send(event.getPlayer(), "ride.stopped");
            event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.ENTITY_HORSE_LAND, 0.8f, 1.0f);
        }
    }

}
