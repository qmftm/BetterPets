package kr.qmftm.betterpets.domain;

import kr.qmftm.betterpets.config.Tags;

import java.util.UUID;

/**
 * 플레이어가 소유한 펫 "개체". 저장 대상이다.
 *
 * <p>가변 객체다 — 성장도와 상태가 계속 바뀐다. 변경 시 {@link #dirty} 가 서고,
 * 저장소가 그걸 보고 비동기로 기록한다.
 *
 * <p>성장도는 {@link #growth} 와 {@link #updatedAt} 을 함께 봐야 한다. 저장된 값은
 * 마지막 기록 시점의 것이고, 그 뒤로 흐른 시간은 {@link GrowthCurve} 가 환산한다.
 */
public final class PetData {

    private final UUID petId;
    private final UUID ownerId;
    private final long acquiredAt;

    private String typeId;
    private String nickname;        // nullable
    private LifeStage stage;
    private int growth;
    private int growthStage;        // 1부터 시작. 성장도가 찰 때마다 오를 수 있다
    private int fullness;           // 먹일 때마다 오른다. 상한을 넘으면 더 못 먹인다
    private long fullnessUpdatedAt; // 포만도 감소의 기준 시각. growth 의 updatedAt 과 같은 계약,
                                     // 다만 값이다 — 급여(addFullness)로는 안 움직이고
                                     // 감소 계산(applyFullness)으로만 전진한다
    private boolean active;
    private long updatedAt;

    private transient boolean dirty;

    public PetData(final UUID petId,
                   final UUID ownerId,
                   final String typeId,
                   final String nickname,
                   final LifeStage stage,
                   final int growth,
                   final int growthStage,
                   final int fullness,
                   final long fullnessUpdatedAt,
                   final boolean active,
                   final long acquiredAt,
                   final long updatedAt) {
        this.petId = petId;
        this.ownerId = ownerId;
        this.typeId = typeId;
        this.nickname = sanitizeNickname(nickname);
        this.stage = stage;
        this.growth = growth;
        this.growthStage = Math.max(1, growthStage);
        this.fullness = Math.max(0, fullness);
        this.fullnessUpdatedAt = fullnessUpdatedAt;
        this.active = active;
        this.acquiredAt = acquiredAt;
        this.updatedAt = updatedAt;
    }

    /** 새로 획득한 펫. 알에서 갓 나온 상태다. */
    public static PetData newBaby(final UUID ownerId, final String typeId, final long now) {
        return new PetData(UUID.randomUUID(), ownerId, typeId, null,
            LifeStage.NORMAL, 0, 1, 0, now, false, now, now);
    }

    public UUID petId() { return petId; }
    public UUID ownerId() { return ownerId; }
    public String typeId() { return typeId; }
    public String nickname() { return nickname; }
    public LifeStage stage() { return stage; }
    public int growth() { return growth; }
    public int growthStage() { return growthStage; }
    public int fullness() { return fullness; }
    public long fullnessUpdatedAt() { return fullnessUpdatedAt; }
    public boolean active() { return active; }
    public long acquiredAt() { return acquiredAt; }
    public long updatedAt() { return updatedAt; }

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }

    public void typeId(final String value) {
        if (!value.equals(typeId)) { typeId = value; dirty = true; }
    }

    /**
     * 별명을 정한다.
     *
     * <p><b>MiniMessage 태그를 걷어내고 저장한다.</b> 별명은 플레이어가 정하는 유일한
     * 문자열인데, 보관함 아이콘이 그걸 그대로 파싱해 그리고 있었다 —
     * {@code /pet rename <rainbow>왕} 이면 색을 공짜로 얻고, 닫히지 않은 태그를
     * 넣으면 그 줄 아래 서식이 통째로 새어 나간다.
     *
     * <p>보여주는 자리마다 걷어내는 대신 <b>들어오는 문 하나에서</b> 막는다. 표시하는
     * 곳은 앞으로도 늘어나지만(채팅·GUI·스코어보드·Discord), 저장되는 값이 이미 깨끗하면
     * 그중 한 곳을 빠뜨려도 사고가 나지 않는다.
     */
    public void nickname(final String value) {
        final String cleaned = sanitizeNickname(value);
        if (!java.util.Objects.equals(cleaned, nickname)) {
            nickname = cleaned;
            dirty = true;
        }
    }

    /**
     * 별명으로 저장해도 되는 형태로 다듬는다.
     *
     * <p>생성자에서도 부른다 — 이 방어가 생기기 전에 저장된 파일에 태그가 남아 있을 수
     * 있고, 파일은 관리자가 손으로 고칠 수도 있다.
     *
     * @return 태그를 걷어낸 별명. 남는 게 없으면 {@code null}(= 별명 없음)
     */
    public static String sanitizeNickname(final String value) {
        if (value == null) {
            return null;
        }
        final String stripped = Tags.strip(value).trim();
        return stripped.isEmpty() ? null : stripped;
    }

    public void stage(final LifeStage value) {
        if (value != stage) { stage = value; dirty = true; }
    }

    /** 성장 단계를 직접 지정한다. 1 미만으로는 떨어지지 않는다. */
    public void growthStage(final int value) {
        final int normalized = Math.max(1, value);
        if (normalized != growthStage) { growthStage = normalized; dirty = true; }
    }

    /**
     * 소환 중 표시.
     *
     * <p><b>저장하지 않는다 — 그래서 dirty 를 세우지 않는다.</b> 소환 상태는 런타임
     * 사실이라 파일에 남길 게 못 된다. 남겨두면 서버가 비정상 종료됐을 때
     * {@code active: true} 인 채로 굳어, 다음 접속에서 소환하지도 않은 펫이
     * 보관함에 "소환 중"으로 보인다.
     */
    public void active(final boolean value) {
        if (value != active) { active = value; }
    }

    /**
     * 성장도와 기준 시각을 함께 갱신한다.
     *
     * <p><b>따로 세팅하지 못하게 막아둔 것은 의도적이다.</b> 둘은 항상 짝으로 움직여야 한다.
     * 성장도만 올리고 기준 시각을 두면 같은 시간이 다시 환산돼 이중 계산이 된다.
     */
    public void applyGrowth(final GrowthCurve.Projection projection) {
        if (projection.growth() != growth || projection.updatedAt() != updatedAt) {
            growth = projection.growth();
            updatedAt = projection.updatedAt();
            dirty = true;
        }
    }

    /** 먹이 등으로 성장도를 직접 올린다. 기준 시각은 건드리지 않는다. */
    public void addGrowth(final int amount, final int max) {
        final int next = GrowthCurve.feed(growth, amount, max);
        if (next != growth) { growth = next; dirty = true; }
    }

    /**
     * 포만도를 올린다. 상한은 여기서 두지 않는다 — 급여를 막을지는
     * {@code GrowthService} 가 상한과 비교해 먼저 판단하고, 여긴 그 판단이 끝난
     * 뒤에만 불린다. 음수로는 안 내려간다.
     *
     * <p>{@link #fullnessUpdatedAt} 은 건드리지 않는다 — 감소 시계는 급여와 무관하게
     * 독립적으로 흐른다. 먹였다고 시계를 리셋하면, 감소분을 한 번 계산해 반영해 둔
     * 직후 또 먹였을 때 그 사이 시간이 다시 감소 몫으로 잡히는 이중 계산이 된다.
     */
    public void addFullness(final int amount) {
        final int next = Math.max(0, fullness + amount);
        if (next != fullness) { fullness = next; dirty = true; }
    }

    /**
     * 포만도와 그 기준 시각을 함께 갱신한다. 시간 경과로 깎일 때 쓴다.
     *
     * <p>{@link #applyGrowth} 와 같은 계약이다 — 값과 기준 시각을 따로 세팅하지
     * 못하게 막는다. 따로 두면 같은 경과 시간이 다음 계산에서 다시 잡힌다.
     */
    public void applyFullness(final FullnessCurve.Projection projection) {
        if (projection.fullness() != fullness || projection.updatedAt() != fullnessUpdatedAt) {
            fullness = projection.fullness();
            fullnessUpdatedAt = projection.updatedAt();
            dirty = true;
        }
    }

    /** 표시용 이름. 별명이 없으면 종류 이름으로 폴백해야 하므로 nullable 을 그대로 준다. */
    public String displayNameOr(final String fallback) {
        return nickname == null || nickname.isBlank() ? fallback : nickname;
    }
}
