package kr.qmftm.betterpets.runtime;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@code org.bukkit.util.Vector} 는 서버 없이도 만들 수 있는 순수 계산 클래스라
 * 여기 로직은 검증할 수 있다.
 */
class VectorsTest {

    private static final double EPS = 1.0e-9;

    private static void assertVector(final Vector actual,
                                     final double x, final double y, final double z) {
        assertEquals(x, actual.getX(), EPS, "x");
        assertEquals(y, actual.getY(), EPS, "y");
        assertEquals(z, actual.getZ(), EPS, "z");
    }

    @Test
    @DisplayName("90도 돌리면 축이 바뀐다")
    void quarterTurn() {
        assertVector(Vectors.rotateAroundY(new Vector(0, 0, 1), Math.toRadians(90)),
            -1, 0, 0);
    }

    @Test
    @DisplayName("180도 돌리면 반대 방향")
    void halfTurn() {
        assertVector(Vectors.rotateAroundY(new Vector(0, 0, 1), Math.toRadians(180)),
            0, 0, -1);
    }

    @Test
    @DisplayName("좌우 대칭 — +45도와 -45도가 z축을 기준으로 거울이다")
    void oppositeAnglesMirror() {
        // 부채꼴 배치가 이 성질에 기댄다. 한쪽으로만 쏠리면 펫들이 몰려 선다.
        final Vector left = Vectors.rotateAroundY(new Vector(0, 0, 1), Math.toRadians(45));
        final Vector right = Vectors.rotateAroundY(new Vector(0, 0, 1), Math.toRadians(-45));

        assertEquals(left.getX(), -right.getX(), EPS);
        assertEquals(left.getZ(), right.getZ(), EPS);
    }

    @Test
    @DisplayName("높이는 건드리지 않는다 — 돌린다고 펫이 뜨거나 가라앉으면 안 된다")
    void heightIsUntouched() {
        assertVector(Vectors.rotateAroundY(new Vector(1, 7.5, 0), Math.toRadians(123)),
            Math.cos(Math.toRadians(123)), 7.5, Math.sin(Math.toRadians(123)));
    }

    @Test
    @DisplayName("길이가 변하지 않는다 — 회전이지 확대가 아니다")
    void lengthIsPreserved() {
        final Vector rotated = Vectors.rotateAroundY(new Vector(3, 0, 4), Math.toRadians(37));
        assertEquals(5.0, rotated.length(), EPS);
    }

    @Test
    @DisplayName("0도는 아무것도 하지 않고 같은 객체를 준다")
    void zeroIsANoOp() {
        final Vector original = new Vector(1, 2, 3);
        assertSame(original, Vectors.rotateAroundY(original, 0.0));
        assertVector(original, 1, 2, 3);
    }

    @Test
    @DisplayName("한 바퀴를 나눠 돌아도 제자리로 온다")
    void fullTurnComesBack() {
        final Vector vector = new Vector(0, 0, 1);
        for (int i = 0; i < 8; i++) {
            Vectors.rotateAroundY(vector, Math.toRadians(45));
        }
        assertVector(vector, 0, 0, 1);
    }
}
