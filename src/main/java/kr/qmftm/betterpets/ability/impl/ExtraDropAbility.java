package kr.qmftm.betterpets.ability.impl;

import kr.qmftm.betterpets.ability.AbilityContext;
import kr.qmftm.betterpets.ability.PetAbility;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 소유자가 몹을 처치할 때 일정 확률로 드랍을 한 벌 더 준다.
 *
 * <p>확률은 {@code chance-base + chance-per-growth * 성장도} 에 등급 배율을 곱한 값이다.
 */
public final class ExtraDropAbility implements PetAbility {

    @Override
    public String id() {
        return "on_kill_extra_drop";
    }

    @Override
    public Type type() {
        return Type.TRIGGER;
    }

    @Override
    public void onEvent(final AbilityContext ctx, final Event event) {
        if (!(event instanceof EntityDeathEvent death)) {
            return;
        }
        if (!ctx.owner().equals(death.getEntity().getKiller())) {
            return;
        }
        final double chance = Math.min(1.0, ctx.definition().value("chance-base", 0.0)
            + ctx.definition().value("chance-per-growth", 0.0) * ctx.pet().growth());
        if (chance <= 0.0 || ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        // 드랍 목록을 복사한 뒤 추가한다. 순회 중 수정하면 ConcurrentModificationException 이다.
        final List<ItemStack> extra = List.copyOf(death.getDrops());
        for (final ItemStack stack : extra) {
            death.getDrops().add(stack.clone());
        }
    }
}
