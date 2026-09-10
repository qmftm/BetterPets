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

    /** 성장도와 등급을 반영한 최종 수치. 대부분의 능력이 이걸 쓴다. */
    public double scaledValue() {
        return definition.scaled(pet.growth(), type.stats().moveSpeedMultiplier());
    }
}
