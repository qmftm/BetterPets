package kr.qmftm.betterpets.domain;

/**
 * 보유·동시 소환 한도. {@code config.yml} 의 {@code pets:} 에서 온다.
 *
 * <p><b>0 은 무제한이다.</b> 음수를 "무제한"으로 쓰지 않는 이유는 오타 때문이다 —
 * {@code max-owned: -1} 을 의도한 사람과 {@code max-owned: -} 를 잘못 적은 사람을
 * 구분할 수 없다. 0 으로 통일하고 음수는 0 으로 접는다.
 */
public record PetLimits(int maxOwned, int maxActive) {

    public PetLimits {
        maxOwned = Math.max(0, maxOwned);
        maxActive = Math.max(0, maxActive);
    }

    /** 설정이 없을 때. 20마리 보유, 1마리 소환 — 원작의 감각에 가깝다. */
    public static PetLimits defaults() {
        return new PetLimits(20, 1);
    }

    public boolean ownedUnlimited() {
        return maxOwned == 0;
    }

    public boolean activeUnlimited() {
        return maxActive == 0;
    }

    /** 한 마리 더 받을 수 있는가. */
    public boolean canOwnMore(final int owned) {
        return ownedUnlimited() || owned < maxOwned;
    }

    /** 한 마리 더 소환할 수 있는가. */
    public boolean canSummonMore(final int active) {
        return activeUnlimited() || active < maxActive;
    }

    /** GUI 표시용 "3/20" 또는 "3/∞". */
    public String ownedLabel(final int owned) {
        return owned + "/" + (ownedUnlimited() ? "∞" : String.valueOf(maxOwned));
    }

    public String activeLabel(final int active) {
        return active + "/" + (activeUnlimited() ? "∞" : String.valueOf(maxActive));
    }
}
