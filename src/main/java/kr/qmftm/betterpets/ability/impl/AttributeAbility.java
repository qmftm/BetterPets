package kr.qmftm.betterpets.ability.impl;

import kr.qmftm.betterpets.ability.AbilityContext;
import kr.qmftm.betterpets.ability.PetAbility;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * 소유자의 능력치를 올리는 패시브. 이동속도·최대체력·공격력을 같은 코드로 처리한다.
 *
 * <p><b>모디파이어 누적이 이 클래스의 핵심 위험이다.</b> 재접속·펫 교체·리로드가 겹치면
 * 같은 키의 모디파이어가 여러 개 붙어 수치가 폭주한다. 그래서 부착 전에 항상 먼저 제거한다.
 * {@link #onEquip} 이 {@link #remove} 로 시작하는 게 그 이유다.
 */
public final class AttributeAbility implements PetAbility {

    private final String id;
    private final Attribute attribute;
    private final AttributeModifier.Operation operation;
    private final NamespacedKey key;

    public AttributeAbility(final Plugin plugin,
                            final String id,
                            final Attribute attribute,
                            final AttributeModifier.Operation operation) {
        this.id = id;
        this.attribute = attribute;
        this.operation = operation;
        // 능력마다 고유 키. 이 키로만 제거하므로 다른 플러그인의 모디파이어는 건드리지 않는다.
        this.key = new NamespacedKey(plugin, "ability_" + id);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Type type() {
        return Type.PASSIVE;
    }

    @Override
    public void onEquip(final AbilityContext ctx) {
        final double amount = ctx.scaledValue();
        if (amount == 0.0) {
            return;
        }
        final AttributeInstance instance = ctx.owner().getAttribute(attribute);
        if (instance == null) {
            return;
        }
        remove(instance);   // ★ 항상 먼저 제거. 이게 없으면 누적된다
        instance.addModifier(new AttributeModifier(key, amount, operation));
    }

    @Override
    public void onUnequip(final AbilityContext ctx) {
        final AttributeInstance instance = ctx.owner().getAttribute(attribute);
        if (instance != null) {
            remove(instance);
        }
    }

    /**
     * 접속 시 청소용. 서버가 비정상 종료돼 해제가 불린 적 없는 모디파이어를 걷어낸다.
     * 능력을 실제로 쓰는지와 무관하게 호출해야 한다.
     */
    public void purge(final Player player) {
        final AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            remove(instance);
        }
    }

    private void remove(final AttributeInstance instance) {
        // 같은 키가 여러 개 붙어 있을 수 있다. 복사본을 순회해야
        // 제거 중 ConcurrentModificationException 이 나지 않는다.
        for (final AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
            if (key.equals(modifier.getKey())) {
                instance.removeModifier(modifier);
            }
        }
    }
}
