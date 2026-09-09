package kr.qmftm.betterpets.ability;

import kr.qmftm.betterpets.ability.impl.AttributeAbility;
import kr.qmftm.betterpets.ability.impl.ExtraDropAbility;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * id → 능력 구현체 등록소.
 *
 * <p>설정 파일은 능력을 id 문자열로 참조한다. 여기 없는 id 를 쓰면 로드 시 검증에서 걸린다.
 */
public final class AbilityRegistry {

    private final Map<String, PetAbility> abilities = new LinkedHashMap<>();

    public AbilityRegistry(final Plugin plugin) {
        // 기본 제공 능력. 새 능력을 추가하려면 여기 한 줄을 더하면 된다.
        register(new AttributeAbility(plugin, "attribute_speed",
            Attribute.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_SCALAR));
        register(new AttributeAbility(plugin, "attribute_health",
            Attribute.MAX_HEALTH, AttributeModifier.Operation.ADD_NUMBER));
        register(new AttributeAbility(plugin, "attribute_damage",
            Attribute.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_NUMBER));
        register(new ExtraDropAbility());
    }

    public void register(final PetAbility ability) {
        abilities.put(ability.id(), ability);
    }

    public Optional<PetAbility> find(final String id) {
        return Optional.ofNullable(abilities.get(id));
    }

    /** 설정 검증에서 "이 중 하나를 쓰세요"를 보여줄 때 쓴다. */
    public Set<String> ids() {
        return Set.copyOf(abilities.keySet());
    }
}
