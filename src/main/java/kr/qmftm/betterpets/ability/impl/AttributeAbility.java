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
import java.util.UUID;

/**
 * 소유자의 능력치를 올리는 패시브. 이동속도·최대체력·공격력을 같은 코드로 처리한다.
 *
 * <p><b>모디파이어 누적이 이 클래스의 핵심 위험이다.</b> 재접속·펫 교체·리로드가 겹치면
 * 같은 키의 모디파이어가 여러 개 붙어 수치가 폭주한다. 그래서 부착 전에 항상 먼저 제거한다.
 * {@link #onEquip} 이 {@link #remove} 로 시작하는 게 그 이유다.
 *
 * <p><b>키는 능력마다가 아니라 펫 개체마다 다르다</b>({@code ability_<능력>_<펫id>}).
 * {@code pets.max-active} 가 2 이상이면 같은 능력을 가진 펫을 여러 마리 소환할 수 있는데,
 * 키가 능력 하나뿐이면 둘째 펫이 첫째 펫의 모디파이어를 덮어쓰고, 둘째를 해제하는 순간
 * 첫째 것까지 같이 사라진다. 개체별 키라면 마리마다 자기 것만 붙였다 뗀다 —
 * 그래서 여러 마리의 보너스는 <b>겹쳐서</b> 적용된다.
 */
public final class AttributeAbility implements PetAbility {

    private final String id;
    private final Attribute attribute;
    private final AttributeModifier.Operation operation;
    private final Plugin plugin;

    /** 이 능력이 붙인 모디파이어의 키 앞부분. 청소는 이 접두사로 훑는다. */
    private final String prefix;

    /** 우리 키의 네임스페이스. 다른 플러그인의 모디파이어를 건드리지 않기 위한 확인용이다. */
    private final String namespace;

    public AttributeAbility(final Plugin plugin,
                            final String id,
                            final Attribute attribute,
                            final AttributeModifier.Operation operation) {
        this.plugin = plugin;
        this.id = id;
        this.attribute = attribute;
        this.operation = operation;
        // 능력마다 고유 접두사. 이 접두사로만 제거하므로 다른 플러그인의 모디파이어는 건드리지 않는다.
        this.prefix = "ability_" + id;
        this.namespace = new NamespacedKey(plugin, prefix).getNamespace();
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
        final NamespacedKey key = keyFor(ctx.pet().petId());
        remove(instance, key::equals);   // ★ 항상 먼저 제거. 이게 없으면 누적된다
        instance.addModifier(new AttributeModifier(key, amount, operation));
    }

    @Override
    public void onUnequip(final AbilityContext ctx) {
        final AttributeInstance instance = ctx.owner().getAttribute(attribute);
        if (instance != null) {
            // 이 펫의 것만 뗀다. 같이 소환 중인 다른 펫의 보너스는 남아야 한다.
            final NamespacedKey key = keyFor(ctx.pet().petId());
            remove(instance, key::equals);
        }
    }

    /**
     * 접속 시 청소용. 서버가 비정상 종료돼 해제가 불린 적 없는 모디파이어를 걷어낸다.
     * 능력을 실제로 쓰는지와 무관하게 호출해야 한다.
     *
     * <p>어느 펫의 것인지 알 수 없으므로 접두사로 전부 훑는다. 개체별 키로 바뀌기
     * 전에 붙은 {@code ability_<능력>} 도 같은 접두사에 걸린다.
     */
    public void purge(final Player player) {
        final AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            remove(instance, this::isOurs);
        }
    }

    private NamespacedKey keyFor(final UUID petId) {
        return new NamespacedKey(plugin, prefix + "_" + petId);
    }

    /** 이 능력이 붙인 키인가. 개체별 키와 예전 형식을 모두 인정한다. */
    private boolean isOurs(final NamespacedKey key) {
        if (key == null || !namespace.equals(key.getNamespace())) {
            return false;
        }
        final String name = key.getKey();
        // startsWith(prefix) 만 보면 ability_speed 가 ability_speed_boost 까지 잡는다.
        return name.equals(prefix) || name.startsWith(prefix + "_");
    }

    private void remove(final AttributeInstance instance,
                        final java.util.function.Predicate<NamespacedKey> matches) {
        // 같은 키가 여러 개 붙어 있을 수 있다. 복사본을 순회해야
        // 제거 중 ConcurrentModificationException 이 나지 않는다.
        for (final AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
            if (matches.test(modifier.getKey())) {
                instance.removeModifier(modifier);
            }
        }
    }
}
