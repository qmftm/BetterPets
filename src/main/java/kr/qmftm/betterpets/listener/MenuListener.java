package kr.qmftm.betterpets.listener;

import kr.qmftm.betterpets.config.Messages;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.gui.Menus;
import kr.qmftm.betterpets.gui.PetMenuFactory;
import kr.qmftm.betterpets.service.PetService;
import kr.qmftm.betterpets.storage.PetStore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * GUI 클릭 처리.
 *
 * <p><b>모든 경로에서 {@code setCancelled(true)} 를 가장 먼저 부른다.</b> 로직을 먼저 돌리고
 * 취소를 나중에 하면, 로직 중간에 예외가 나거나 조기 return 이 걸리는 순간 취소가 안 돼
 * 아이템 복제로 이어진다. GUI 취약점의 대부분이 이 순서 문제다.
 *
 * <p>GUI 식별은 {@link Menus.Holder} 타입으로 한다 — 제목 문자열 비교는 색코드나
 * 번역이 끼면 깨진다.
 */
public final class MenuListener implements Listener {

    private final PetService pets;
    private final PetStore store;
    private final PetMenuFactory menus;
    private final Messages messages;

    public MenuListener(final PetService pets,
                        final PetStore store,
                        final PetMenuFactory menus,
                        final Messages messages) {
        this.pets = pets;
        this.store = store;
        this.menus = menus;
        this.messages = messages;
    }

    @EventHandler
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menus.Holder holder)) {
            return;
        }
        event.setCancelled(true);   // ★ 무조건 먼저

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
            || !event.getClickedInventory().equals(event.getInventory())) {
            return;     // 플레이어 인벤토리 쪽 클릭. 취소만 하고 끝
        }

        if (holder instanceof Menus.Box box) {
            handleBox(player, box, event.getSlot());
        } else if (holder instanceof Menus.Detail detail) {
            handleDetail(player, detail, event.getSlot());
        }
    }

    /** 드래그도 막는다. 클릭만 막으면 드래그로 아이템을 넣을 수 있다. */
    @EventHandler
    public void onDrag(final InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menus.Holder) {
            event.setCancelled(true);
        }
    }

    private void handleBox(final Player player, final Menus.Box box, final int slot) {
        if (slot == PetMenuFactory.SLOT_PREV) {
            player.openInventory(menus.box(sorted(player.getUniqueId()), box.page() - 1));
            return;
        }
        if (slot == PetMenuFactory.SLOT_NEXT) {
            player.openInventory(menus.box(sorted(player.getUniqueId()), box.page() + 1));
            return;
        }
        final PetData pet = box.petAt(slot);
        if (pet != null) {
            player.openInventory(menus.detail(pet));
        }
    }

    private void handleDetail(final Player player, final Menus.Detail detail, final int slot) {
        final PetData pet = detail.target();
        switch (slot) {
            case PetMenuFactory.SLOT_SUMMON -> {
                if (pet.active()) {
                    pets.dismiss(player);
                    messages.send(player, "pet.dismissed");
                } else {
                    final PetService.SummonResult result = pets.summon(player, pet);
                    switch (result) {
                        case OK -> messages.send(player, "pet.summoned");
                        case MODEL_MISSING -> messages.send(player, "pet.model-missing");
                        case UNKNOWN_TYPE -> messages.send(player, "pet.unknown-type");
                    }
                }
                player.closeInventory();
            }
            case PetMenuFactory.SLOT_RENAME -> {
                player.closeInventory();
                messages.send(player, "pet.rename-hint");
            }
            case PetMenuFactory.SLOT_RELEASE -> {
                pets.release(player, pet);
                messages.send(player, "pet.released");
                player.openInventory(menus.box(sorted(player.getUniqueId()), 0));
            }
            case PetMenuFactory.SLOT_BACK ->
                player.openInventory(menus.box(sorted(player.getUniqueId()), 0));
            default -> { /* 빈 칸 */ }
        }
    }

    private List<PetData> sorted(final UUID ownerId) {
        final List<PetData> list = new ArrayList<>(store.owned(ownerId));
        list.sort(Comparator.comparingLong(PetData::acquiredAt));
        return list;
    }
}
