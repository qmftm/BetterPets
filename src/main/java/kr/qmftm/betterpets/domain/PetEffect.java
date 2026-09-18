package kr.qmftm.betterpets.domain;

/**
 * 펫 이벤트(먹이·탑승·하차·성장) 하나에서 재생할 사운드·파티클.
 * {@code config.yml} 의 {@code effects.<이벤트>} 에서 로드한다.
 *
 * <p>사운드와 파티클은 각각 독립적인 선택이다 — 이름을 안 적으면(또는 이벤트 섹션
 * 자체가 없으면) 그 효과만 꺼진다. 이름을 {@link String} 으로 받는 이유는
 * {@link FeedDefinition#material()} 과 같다: 실제 Bukkit {@code Sound}·{@code Particle}
 * enum 조회는 Bukkit 이 있는 자리({@code EffectService})에서 한다 — 이 레코드는
 * 서버 없이도 만들고 검증할 수 있어야 한다.
 */
public record PetEffect(
    String sound,          // nullable — 안 적으면 소리 없음
    double volume,
    double pitch,
    String particle,       // nullable — 안 적으면 파티클 없음
    int particleCount
) {
    public PetEffect {
        // 음수는 뜻이 없다. 실수로 적은 값이 조용히 무시되는 대신 0(=꺼짐)으로 접힌다.
        volume = Math.max(0.0, volume);
        pitch = Math.max(0.0, pitch);
        particleCount = Math.max(0, particleCount);
    }

    /** 이벤트 섹션 자체가 없을 때 쓰는 값. 아무 효과도 내지 않는다. */
    public static PetEffect none() {
        return new PetEffect(null, 1.0, 1.0, null, 0);
    }

    public boolean hasSound() {
        return sound != null && !sound.isBlank();
    }

    public boolean hasParticle() {
        return particle != null && !particle.isBlank() && particleCount > 0;
    }
}
