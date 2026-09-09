package kr.qmftm.betterpets.domain;

/**
 * 먹이 아이템 정의. {@code items.yml} 의 {@code feeds:} 에서 로드한다.
 *
 * <p>먹이마다 성장도 증가량이 다를 수 있다 — 우유는 +10, 고급 사료는 +30 하는 식이다.
 * {@code growth} 를 적지 않으면 {@code config.yml} 의 {@code growth.feed-amount} 를 쓴다.
 */
public record FeedDefinition(
    String id,
    String displayName,
    String material,
    String itemModel,       // nullable — 리소스팩 모델 키
    Integer growth          // nullable — 설정하지 않으면 전역 기본값
) {

    /** 이 먹이가 올려줄 성장도. 지정하지 않았으면 전역 기본값을 쓴다. */
    public int growthOr(final int fallback) {
        return growth == null ? fallback : growth;
    }
}
