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
 * <p>확률은 {@code chance-base + chance-per-growth * 성장도} 다.
 *
 * <p><b>등급 배율은 곱하지 않는다.</b> 능력치 계열({@link AttributeAbility})은
 * {@code AbilityContext.scaledValue()} 를 거치면서 등급 배율을 받는데 이쪽은 아니다.
 * 확률에 1.0~1.7 배를 곱하면 상한(1.0)에 금방 붙어 등급 차이가 오히려 뭉개지고,
 * 등급별 차이는 {@code pets/*.yml} 의 {@code chance-base} 로 이미 낼 수 있다.
 * 밸런싱 판단이므로 바꾸고 싶으면 여기 한 줄이다.
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
