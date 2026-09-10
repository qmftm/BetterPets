package kr.qmftm.betterpets.gui;

import kr.qmftm.betterpets.domain.PetData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * GUI 식별용 홀더.
 *
 * <p><b>제목 문자열로 GUI 를 구분하지 않는다.</b> 색코드나 번역이 끼면 비교가 깨지고,
 * 그 순간 클릭 취소가 풀려 아이템 복제로 이어진다. {@link InventoryHolder} 로 구분하면
 * 그런 일이 없다.
 */
public final class Menus {

    private Menus() {
        throw new AssertionError("유틸리티 클래스");
    }

    /** 모든 BetterPets GUI 의 공통 부모. 리스너는 이 타입만 확인하면 된다. */
    public abstract static class Holder implements InventoryHolder {

        private Inventory inventory;

        /** 슬롯 → 그 칸이 가리키는 펫. 클릭 처리에서 쓴다. */
        protected final Map<Integer, PetData> slots = new HashMap<>();

        void inventory(final Inventory value) {
            inventory = value;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        public PetData petAt(final int slot) {
            return slots.get(slot);
        }
    }

    /** 보관함. 소유한 펫 목록을 보여준다. */
    public static final class Box extends Holder {
        private int page;

        public int page() { return page; }
        public void page(final int value) { page = Math.max(0, value); }
    }

    /** 상세. 펫 한 마리를 다룬다. */
    public static final class Detail extends Holder {
        private final PetData target;

        /**
         * 어느 쪽에서 들어왔는가.
         *
         * <p>"돌아가기"가 언제나 1쪽을 열고 있었다. 3쪽에서 펫 하나를 보고 나오면
         * 처음으로 튕겨 나가서, 여러 마리를 훑어보는 동안 매번 다시 넘겨야 했다.
         */
        private final int page;

        public Detail(final PetData target, final int page) {
            this.target = target;
            this.page = Math.max(0, page);
        }

        public PetData target() { return target; }

        public int page() { return page; }
    }
}
