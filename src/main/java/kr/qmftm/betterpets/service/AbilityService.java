package kr.qmftm.betterpets.service;

import kr.qmftm.betterpets.ability.AbilityContext;
import kr.qmftm.betterpets.ability.AbilityDefinition;
import kr.qmftm.betterpets.ability.AbilityRegistry;
import kr.qmftm.betterpets.ability.PetAbility;
import kr.qmftm.betterpets.ability.impl.AttributeAbility;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

/**
 * 능력의 부착과 해제.
 *
 * <p><b>{@link #unequip} 은 {@link #equip} 의 정확한 역연산이어야 한다.</b> 이 계약이
 * 깨지면 재접속·펫 교체마다 상태가 쌓인다 — 대표적으로 이동속도 모디파이어가 누적돼
 * 플레이어가 로켓처럼 날아간다.
 */
public final class AbilityService {

    private final Plugin plugin;
    private final AbilityRegistry registry;

    public AbilityService(final Plugin plugin, final AbilityRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void equip(final Player owner, final PetData data, final PetType type) {
        if (!data.stage().abilitiesActive()) {
            return;     // 아기와 알은 능력이 없다
        }
        forEach(owner, data, type, (ability, ctx) -> ability.onEquip(ctx));
    }

    public void unequip(final Player owner, final PetData data, final PetType type) {
        // 해제는 생애주기와 무관하게 무조건 돈다. 상태가 바뀐 뒤에 해제될 수도 있어서,
        // equip 과 같은 조건을 걸면 붙은 것을 못 떼는 경우가 생긴다.
        forEach(owner, data, type, (ability, ctx) -> ability.onUnequip(ctx));
    }

    public void dispatch(final Player owner, final PetData data, final PetType type, final Event event) {
        if (!data.stage().abilitiesActive()) {
            return;
        }
        forEach(owner, data, type, (ability, ctx) -> {
            if (ability.type() == PetAbility.Type.TRIGGER) {
                ability.onEvent(ctx, event);
            }
        });
    }

    /**
     * 접속 시 청소.
     *
     * <p>서버가 비정상 종료되면 해제가 불린 적 없는 모디파이어가 플레이어에게 남는다.
     * 펫을 소환하기 전에 무조건 한 번 걷어낸다. 이게 누적 버그의 마지막 방어선이다.
     */
    public void purge(final Player player) {
        for (final String id : registry.ids()) {
            registry.find(id).ifPresent(ability -> {
                if (ability instanceof AttributeAbility attribute) {
                    attribute.purge(player);
                }
            });
        }
    }

    private void forEach(final Player owner,
                         final PetData data,
                         final PetType type,
                         final java.util.function.BiConsumer<PetAbility, AbilityContext> action) {
        for (final AbilityDefinition definition : type.abilities()) {
            registry.find(definition.id()).ifPresent(ability ->
                action.accept(ability, new AbilityContext(plugin, owner, data, type, definition)));
        }
    }
}
