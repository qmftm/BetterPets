package kr.qmftm.betterpets.listener;

import kr.qmftm.betterpets.config.Messages;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.gui.Menus;
import kr.qmftm.betterpets.gui.PetMenuFactory;
import kr.qmftm.betterpets.service.GrowthCatchUp;
import kr.qmftm.betterpets.service.PetService;
import kr.qmftm.betterpets.storage.PetStore;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;


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
    private final GrowthCatchUp catchUp;

    public MenuListener(final PetService pets,
                        final PetStore store,
                        final PetMenuFactory menus,
                        final Messages messages,
                        final GrowthCatchUp catchUp) {
        this.pets = pets;
        this.store = store;
        this.menus = menus;
        this.messages = messages;
        this.catchUp = catchUp;
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
            handleDetail(player, detail, event.getSlot(), event.isShiftClick());
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
        final Menus.View view = box.view();
        switch (slot) {
            case PetMenuFactory.SLOT_PREV -> {
                openBox(player, view.page(view.page() - 1));
                return;
            }
            case PetMenuFactory.SLOT_NEXT -> {
                openBox(player, view.page(view.page() + 1));
                return;
            }
            case PetMenuFactory.SLOT_SORT -> {
                openBox(player, view.nextSort());
                return;
            }
            case PetMenuFactory.SLOT_FILTER -> {
                openBox(player, view.nextFilter());
                return;
            }
            default -> { /* 펫 아이콘일 수 있다 */ }
        }
        final PetData pet = box.petAt(slot);
        if (pet != null) {
            // 보던 화면을 그대로 넘겨준다. 상세에서 "돌아가기"가 같은 쪽·정렬·필터로
            // 되돌아가야 여러 마리를 훑어보는 동안 매번 다시 맞추지 않는다.
            player.openInventory(menus.detail(pet, view));
        }
    }

    private void handleDetail(final Player player,
                              final Menus.Detail detail,
                              final int slot,
                              final boolean shift) {
        final PetData pet = detail.target();
        switch (slot) {
            case PetMenuFactory.SLOT_SUMMON -> {
                if (pet.active()) {
                    // 소환 해제는 화면을 닫지 않는다 — 보관함을 관리하는 동작이지 밖을
                    // 보러 나가는 동작이 아니다. 같은 화면에서 소환 버튼이 바로
                    // "소환하기"로 바뀌어야 다른 펫으로 갈아탈 때 다시 열 필요가 없다.
                    pets.dismiss(player, pet.petId());
                    messages.send(player, "pet.dismissed");
                    player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_ITEM_TAKEN, 1.0f, 1.0f);
                    player.openInventory(menus.detail(pet, detail.view()));
                } else {
                    final PetService.SummonResult result = pets.summon(player, pet);
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
                    // 소환은 화면을 닫는다 — 소환된 펫이 눈앞에 나타나는 걸 봐야 한다.
                    player.closeInventory();
                }
            }
            case PetMenuFactory.SLOT_RENAME -> {
                player.closeInventory();
                // 어느 펫인지 id 를 박아서 알려준다. 여러 마리를 소환해 둔 상태에서
                // "/pet rename <이름>" 만 안내하면 어느 쪽이 바뀔지 알 수 없다.
                messages.send(player, "pet.rename-hint",
                    "id", pet.petId().toString().substring(0, 8));
            }
            case PetMenuFactory.SLOT_RELEASE -> {
                // 되돌릴 수 없는 유일한 조작이다. 소환 버튼 네 칸 옆에 있어서
                // 한 번 잘못 누르면 키우던 펫이 그대로 사라졌다.
                if (!shift) {
                    messages.send(player, "pet.release-confirm");
                    return;
                }
                pets.release(player, pet);
                messages.send(player, "pet.released");
                player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_ITEM_TAKEN, 0.7f, 0.7f);
                openBox(player, detail.view());
            }
            case PetMenuFactory.SLOT_BACK -> openBox(player, detail.view());
            default -> { /* 빈 칸 */ }
        }
    }

    private void openBox(final Player player, final Menus.View view) {
        // 보관함의 펫도 시간이 흐르면 자란다. 열기 전에 맞춰야 화면이 사실을 말한다.
        catchUp.all(player);
        // 거르고 정렬하는 건 PetMenuFactory 가 한다 — 여기서 미리 걸러 넘기면
        // 현황에 적을 전체 마릿수를 잃는다.
        player.openInventory(menus.box(player.getUniqueId(), store.owned(player.getUniqueId()), view));
    }
}
