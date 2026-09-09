package kr.qmftm.betterpets.domain;

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
    private boolean canFly;
    private boolean active;
    private long updatedAt;

    private transient boolean dirty;

    public PetData(final UUID petId,
                   final UUID ownerId,
                   final String typeId,
                   final String nickname,
                   final LifeStage stage,
                   final int growth,
                   final boolean canFly,
                   final boolean active,
                   final long acquiredAt,
                   final long updatedAt) {
        this.petId = petId;
        this.ownerId = ownerId;
        this.typeId = typeId;
        this.nickname = nickname;
        this.stage = stage;
        this.growth = growth;
        this.canFly = canFly;
        this.active = active;
        this.acquiredAt = acquiredAt;
        this.updatedAt = updatedAt;
    }

    /** 새로 획득한 알. */
    public static PetData newEgg(final UUID ownerId, final String typeId, final long now) {
        return new PetData(UUID.randomUUID(), ownerId, typeId, null,
            LifeStage.EGG, 0, false, false, now, now);
    }

    public UUID petId() { return petId; }
    public UUID ownerId() { return ownerId; }
    public String typeId() { return typeId; }
    public String nickname() { return nickname; }
    public LifeStage stage() { return stage; }
    public int growth() { return growth; }
    public boolean canFly() { return canFly; }
    public boolean active() { return active; }
    public long acquiredAt() { return acquiredAt; }
    public long updatedAt() { return updatedAt; }

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }

    public void typeId(final String value) {
        if (!value.equals(typeId)) { typeId = value; dirty = true; }
    }

    public void nickname(final String value) {
        nickname = value;
        dirty = true;
    }

    public void stage(final LifeStage value) {
        if (value != stage) { stage = value; dirty = true; }
    }

    public void canFly(final boolean value) {
        if (value != canFly) { canFly = value; dirty = true; }
    }

    public void active(final boolean value) {
        if (value != active) { active = value; dirty = true; }
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

    /** 표시용 이름. 별명이 없으면 종류 이름으로 폴백해야 하므로 nullable 을 그대로 준다. */
    public String displayNameOr(final String fallback) {
        return nickname == null || nickname.isBlank() ? fallback : nickname;
    }
}
