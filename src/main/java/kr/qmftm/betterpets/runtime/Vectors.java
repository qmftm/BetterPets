package kr.qmftm.betterpets.runtime;

import org.bukkit.util.Vector;

/**
 * 수평 방향 벡터를 다루는 잔손질.
 *
 * <p>펫을 소유자 주위에 배치하는 계산이 두 군데에 있다 — 소환 지점을 고를 때
 * ({@code PetService})와 여러 마리를 부채꼴로 펼칠 때({@link MovementController}).
 * 같은 회전을 각자 들고 있으면 한쪽만 고쳤을 때 배치가 어긋난다.
 */
public final class Vectors {

    private Vectors() {
        throw new AssertionError("유틸리티 클래스");
    }

    /**
     * y축을 중심으로 돌린다. <b>주어진 벡터를 그 자리에서 바꾼다.</b>
     *
     * <p>y 성분은 건드리지 않는다 — 여기서 다루는 건 전부 지면과 나란한 방향이라,
     * 높이까지 돌리면 펫이 공중이나 땅속을 가리키게 된다.
     *
     * @param radians 시계 방향(위에서 볼 때)이 양수
     */
    public static Vector rotateAroundY(final Vector vector, final double radians) {
        if (radians == 0.0) {
            return vector;
        }
        final double cos = Math.cos(radians);
        final double sin = Math.sin(radians);
        final double x = vector.getX();
        final double z = vector.getZ();
        vector.setX(x * cos - z * sin);
        vector.setZ(x * sin + z * cos);
        return vector;
    }
}
