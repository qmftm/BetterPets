package kr.qmftm.betterpets.runtime;

import org.bukkit.World;

/**
 * 주어진 지점에서 아래로 내려가며 처음 만나는 단단한 블록의 바로 위 높이를 찾는다.
 *
 * <p>나는 능력이 없는 펫을 소환하거나 순간이동으로 복귀시킬 때 쓴다 — 소유자가 비행
 * 중이거나 다리 위에 있어도, 그 펫은 발밑 땅으로 내려서야 자연스럽다. {@code PetService}
 * (소환 지점)와 {@link MovementController}(순간이동 복귀) 둘 다 같은 규칙이 필요해서
 * 뺐다 — 한쪽만 고치면 소환 직후와 뒤이은 순간이동이 서로 다른 높이 규칙을 쓰게 된다.
 *
 * <p>틱마다 도는 자리가 아니라(소환 때 한 번, 순간이동 때 드물게) 성능보다 재사용을
 * 우선했다 — {@code MovementController.probe} 같은 버퍼를 따로 안 둔다.
 */
public final class Ground {

    private Ground() {
        throw new AssertionError("유틸리티 클래스");
    }

    /**
     * {@code startY} 부터 아래로 내려가며 처음 만나는 단단한 블록 위 높이를 돌려준다.
     * 월드 바닥까지 단단한 블록을 못 찾으면(허공에 뚫린 월드 등) {@code startY} 를
     * 그대로 돌려준다 — 못 찾았다고 극단적인 값을 주면 오히려 더 어색해진다.
     */
    public static double findY(final World world, final double x, final double z, final double startY) {
        final int bx = (int) Math.floor(x);
        final int bz = (int) Math.floor(z);
        final int top = Math.min((int) Math.floor(startY), world.getMaxHeight() - 1);
        final int bottom = world.getMinHeight();
        for (int y = top; y > bottom; y--) {
            if (world.getBlockAt(bx, y, bz).getType().isSolid()) {
                return y + 1.0;
            }
        }
        return startY;
    }
}
