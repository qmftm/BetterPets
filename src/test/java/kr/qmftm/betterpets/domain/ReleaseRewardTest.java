package kr.qmftm.betterpets.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseRewardTest {

    /** 항상 같은 값을 주는 난수. 구간 경계를 정확히 겨냥한다. */
    private static RandomGenerator fixed(final int value) {
        return new RandomGenerator() {
            @Override public long nextLong() { return value; }
            @Override public int nextInt(final int bound) { return Math.min(value, bound - 1); }
        };
    }

    @Test
    @DisplayName("min 과 max 가 같으면 그 값을 그대로 준다 — 난수를 안 건드린다")
    void fixedAmountSkipsRandom() {
        final ReleaseReward reward = new ReleaseReward("PAPER", "증표", List.of(), 2, 2, false);
        assertEquals(2, reward.roll(fixed(0)));
    }

    @Test
    @DisplayName("min~max 구간에서 고른다")
    void rollsWithinRange() {
        final ReleaseReward reward = new ReleaseReward("PAPER", "증표", List.of(), 1, 3, false);
        assertEquals(1, reward.roll(fixed(0)));
        assertEquals(3, reward.roll(fixed(2)));
    }

    @Test
    @DisplayName("max 가 min 보다 작게 적혔으면 min 으로 맞춘다 — 설정 실수로 난수 구간이 뒤집히면 안 된다")
    void maxBelowMinClampsToMin() {
        final ReleaseReward reward = new ReleaseReward("PAPER", "증표", List.of(), 5, 2, false);
        assertEquals(5, reward.minAmount());
        assertEquals(5, reward.maxAmount());
    }

    @Test
    @DisplayName("음수 min 은 0으로 접는다")
    void negativeMinFloorsAtZero() {
        final ReleaseReward reward = new ReleaseReward("PAPER", "증표", List.of(), -3, 1, false);
        assertEquals(0, reward.minAmount());
    }

    @Test
    @DisplayName("로어는 밖에서 고칠 수 없다")
    void loreIsImmutable() {
        final var mutable = new java.util.ArrayList<String>();
        mutable.add("a");
        final ReleaseReward reward = new ReleaseReward("PAPER", "증표", mutable, 1, 1, false);

        mutable.add("injected");
        assertTrue(reward.lore().size() == 1, "외부에서 정의를 바꿀 수 있으면 안 된다");
    }
}
