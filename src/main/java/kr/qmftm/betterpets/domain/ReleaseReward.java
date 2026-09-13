package kr.qmftm.betterpets.domain;

import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 펫을 놓아줬을 때 주는 보상 아이템. {@code items.yml} 의 {@code release-reward:} 에서 읽는다.
 *
 * <p>알·먹이와 달리 종류가 하나뿐이라 id 로 여러 개를 관리할 필요가 없다 — 서버마다
 * "놓아주면 무엇을 주는가"는 보통 하나로 정해져 있다.
 */
public record ReleaseReward(
    String material,
    String displayName,
    List<String> lore,
    int minAmount,
    int maxAmount,
    boolean glow
) {

    public ReleaseReward {
        lore = List.copyOf(lore);   // 표시용 텍스트라 순서가 결과에 영향을 주지 않는다
        // 최댓값이 최솟값보다 작으면 난수 구간을 계산할 수 없다 — 설정 실수로 보고 맞춘다.
        minAmount = Math.max(0, minAmount);
        maxAmount = Math.max(minAmount, maxAmount);
    }

    /** 이번에 줄 개수. min==max 면 그 값 그대로, 아니면 구간에서 고른다. */
    public int roll(final RandomGenerator random) {
        return minAmount == maxAmount ? minAmount : minAmount + random.nextInt(maxAmount - minAmount + 1);
    }
}
