package kr.qmftm.betterpets.gui;

import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetFilter;
import kr.qmftm.betterpets.domain.PetSort;
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

    /**
     * 지금 보고 있는 화면 상태.
     *
     * <p>쪽·정렬·필터를 하나로 묶는다. 셋을 따로 들고 다니면 상세 화면을 거쳐 돌아올 때
     * 하나씩 빠뜨리게 되고, 실제로 그렇게 해서 <b>"돌아가기"가 언제나 1쪽을 열고
     * 있었다</b> — 3쪽에서 펫 하나를 보고 나오면 처음으로 튕겼다.
     */
    public record View(int page, PetSort sort, PetFilter filter) {

        public View {
            page = Math.max(0, page);
        }

        /** 처음 열 때. */
        public static View first() {
            return new View(0, PetSort.DEFAULT, PetFilter.ALL);
        }

        public View page(final int value) {
            return new View(value, sort, filter);
        }

        /** 정렬·필터를 바꾸면 1쪽부터 다시 본다 — 3쪽에 있던 펫이 어디로 갔는지 모른다. */
        public View nextSort() {
            return new View(0, sort.next(), filter);
        }

        public View nextFilter() {
            return new View(0, sort, filter.next());
        }
    }

    /** 보관함. 소유한 펫 목록을 보여준다. */
    public static final class Box extends Holder {
        private View view = View.first();

        public View view() { return view; }
        public void view(final View value) { view = value; }
    }

    /** 상세. 펫 한 마리를 다룬다. */
    public static final class Detail extends Holder {
        private final PetData target;

        /** 어느 화면에서 들어왔는가. "돌아가기"가 그대로 되돌아간다. */
        private final View view;

        public Detail(final PetData target, final View view) {
            this.target = target;
            this.view = view;
        }

        public PetData target() { return target; }

        public View view() { return view; }
    }
}
