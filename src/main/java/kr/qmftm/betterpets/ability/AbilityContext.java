package kr.qmftm.betterpets.ability;

import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** 능력이 실행될 때 필요한 것들을 모아 넘긴다. */
public record AbilityContext(
    Plugin plugin,
    Player owner,
    PetData pet,
    PetType type,
    AbilityDefinition definition
) {

    /** 등급을 반영한 최종 수치. 대부분의 능력이 이걸 쓴다. */
    public double scaledValue() {
        return scaledValue(pet, type, definition);
    }

    /**
     * 컨텍스트 없이 같은 수치를 구한다.
     *
     * <p>보관함 GUI 가 능력 수치를 미리 보여주는데, 거기엔 {@link Player} 도
     * {@link Plugin} 도 없어서 컨텍스트를 만들 수 없다. 공식을 GUI 쪽에 한 번 더 적었더니
     * <b>두 곳이 조용히 갈릴 수 있는 상태</b>가 됐다 — 보이는 수치와 실제로 붙는 수치가
     * 다르면 버그 리포트를 아무도 못 믿는다. 공식은 여기 한 줄뿐이어야 한다.
     *
     * <p>{@code pet} 은 더 이상 계산에 쓰이지 않는다 — 성장도가 능력치를 더는 키우지
     * 않기 때문이다. 그래도 매개변수로 남겨둔다: 이 메서드는 "능력 수치를 보여줘라"는
     * 일반적인 계약이고, 호출부(GUI·{@link kr.qmftm.betterpets.ability.PetAbility})가
     * 전부 개체를 들고 있는 채로 부르므로 시그니처를 유지하는 쪽이 더 단순하다.
     */
    public static double scaledValue(final PetData pet,
                                     final PetType type,
                                     final AbilityDefinition definition) {
        return definition.scaled(type.stats().moveSpeedMultiplier());
    }
}
