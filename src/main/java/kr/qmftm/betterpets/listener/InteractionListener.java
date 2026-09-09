package kr.qmftm.betterpets.listener;

import kr.qmftm.betterpets.config.Messages;
import kr.qmftm.betterpets.domain.EggDefinition;
import kr.qmftm.betterpets.domain.LifeStage;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetType;
import kr.qmftm.betterpets.domain.RideMode;
import kr.qmftm.betterpets.item.PetItems;
import kr.qmftm.betterpets.runtime.ActivePet;
import kr.qmftm.betterpets.runtime.MovementController;
import kr.qmftm.betterpets.runtime.PetRegistry;
import kr.qmftm.betterpets.runtime.RideController;
import kr.qmftm.betterpets.service.GrowthService;
import kr.qmftm.betterpets.service.PetService;
import kr.qmftm.betterpets.storage.PetStore;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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

    private final Map<UUID, Long> mountConfirms = new ConcurrentHashMap<>();

    public InteractionListener(final PetService pets,
                               final PetStore store,
                               final PetItems items,
                               final PetRegistry registry,
                               final RideController rides,
                               final GrowthService growth,
                               final Messages messages) {
        this.pets = pets;
        this.store = store;
        this.items = items;
        this.registry = registry;
        this.rides = rides;
        this.growth = growth;
        this.messages = messages;
    }

    /**
     * 알 아이템 우클릭.
     *
     * <p><b>{@code EquipmentSlot.HAND} 만 처리한다.</b> 안 그러면 오프핸드로도 이벤트가 와서
     * 한 번의 우클릭에 알이 두 개 소비된다.
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
        event.setCancelled(true);

        final Player player = event.getPlayer();
        final Optional<EggDefinition> definition = pets.catalog().egg(eggId.get());
        if (definition.isEmpty()) {
            messages.send(player, "egg.unknown");
            return;
        }
        final String typeId = definition.get().roll(ThreadLocalRandom.current());
        if (typeId == null || pets.catalog().type(typeId).isEmpty()) {
            messages.send(player, "egg.broken-definition");
            return;
        }

        pets.grantEgg(player, typeId);
        held.setAmount(held.getAmount() - 1);   // 성공한 뒤에만 소비한다

        final PetType type = pets.catalog().type(typeId).orElseThrow();
        messages.send(player, "egg.hatched-into", "name", stripTags(type.displayName()));
        player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1.0f, 1.2f);
    }

    /** 펫 우클릭 — 먹이를 들고 있으면 급여, 아니면 탑승 시도. */
    @EventHandler
    public void onPetClick(final PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        final Player player = event.getPlayer();
        final Optional<ActivePet> clicked = registry.byCarrier(event.getRightClicked());
        if (clicked.isEmpty() || !clicked.get().ownerId().equals(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);

        final ActivePet pet = clicked.get();
        final ItemStack held = player.getInventory().getItemInMainHand();

        if (items.isFeed(held)) {
            feed(player, pet, held);
            return;
        }
        tryRide(player, pet);
    }

    private void feed(final Player player, final ActivePet pet, final ItemStack held) {
        final PetData data = pet.data();
        if (data.stage() == LifeStage.ADULT || data.stage() == LifeStage.PIG) {
            messages.send(player, "feed.already-grown");
            return;
        }
        final GrowthService.FeedResult result = growth.feed(data);
        held.setAmount(held.getAmount() - 1);
        store.saveAsync(data);
        pet.animation().overlay(kr.qmftm.betterpets.domain.PetType.AnimationSet.EAT);

        switch (result) {
            case HATCHED -> messages.send(player, "feed.hatched");
            case STAGE_UP -> messages.send(player, "feed.stage-up",
                "stage", String.valueOf(data.growthStage()),
                "max", String.valueOf(growth.maxStage()));
            case GREW_UP -> messages.send(player, "feed.grew-up");
            case BECAME_PIG -> messages.send(player, "feed.became-pig");
            case FED -> messages.send(player, "feed.fed",
                "growth", String.valueOf(data.growth()),
                "max", String.valueOf(growth.maxOf(data)));
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
            return;
        }
        if (!data.stage().rideable()) {
            messages.send(player, "ride.too-young");
            return;
        }
        if (rides.isRiding(player)) {
            return;     // 비행 중 우클릭은 무시한다. 하차는 스니크 전용
        }

        // FLY 종류라도 비행 추첨에 실패한 개체는 걷는 탑승까지만 된다.
        final boolean flying = type.ride().effective(data.canFly()) == RideMode.FLY;
        if (flying) {
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

        final double speed = type.rarity().rideSpeed();
        if (rides.start(player, pet.carrier().getLocation(), flying, speed)) {
            pet.movement().mode(MovementController.Mode.RIDDEN);
            messages.send(player, flying ? "ride.started-flying" : "ride.started-ground");
        } else {
            messages.send(player, "ride.failed");
        }
    }

    /** 탑승 조작 입력을 컨트롤러에 넘긴다. 실제 이동은 틱 루프가 한다. */
    @EventHandler
    public void onInput(final PlayerInputEvent event) {
        if (rides.isRiding(event.getPlayer())) {
            rides.input(event.getPlayer(), event.getInput());
        }
    }

    /** 스니크 하차. 입력 이벤트가 오지 않는 상황을 위한 보조 경로다. */
    @EventHandler
    public void onSneak(final PlayerToggleSneakEvent event) {
        if (event.isSneaking() && rides.isRiding(event.getPlayer())) {
            rides.stop(event.getPlayer());
            messages.send(event.getPlayer(), "ride.stopped");
        }
    }

    private static String stripTags(final String raw) {
        return raw.replaceAll("<[^>]*>", "");
    }
}
