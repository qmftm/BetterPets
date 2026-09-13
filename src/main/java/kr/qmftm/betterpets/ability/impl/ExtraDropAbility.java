package kr.qmftm.betterpets.ability.impl;

import kr.qmftm.betterpets.ability.AbilityContext;
import kr.qmftm.betterpets.ability.AbilityDefinition;
import kr.qmftm.betterpets.ability.PetAbility;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetType;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 소유자가 몹을 처치할 때 일정 확률로 드랍을 한 벌 더 준다.
 *
 * <p>확률은 {@code chance-base} 고정값이다. 성장도는 더 이상 능력 수치에 관여하지
 * 않는다 — 진화 하나가 성장도의 유일한 역할이다.
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
        final double chance = chance(ctx.pet(), ctx.definition());
        if (chance <= 0.0 || ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        // 드랍 목록을 복사한 뒤 추가한다. 순회 중 수정하면 ConcurrentModificationException 이다.
        final List<ItemStack> extra = List.copyOf(death.getDrops());
        for (final ItemStack stack : extra) {
            death.getDrops().add(stack.clone());
        }
    }

    /**
     * 실제로 굴리는 확률. <b>보관함에 보여주는 값과 같은 함수여야 한다</b> —
     * 두 곳에 따로 적으면 "표시는 30%인데 체감은 아니다"가 된다.
     */
    private static double chance(final PetData pet, final AbilityDefinition definition) {
        return Math.min(1.0, definition.value("chance-base", 0.0));
    }

    @Override
    public double displayValue(final PetData pet,
                               final PetType type,
                               final AbilityDefinition definition) {
        return chance(pet, definition);
    }

    @Override
    public boolean displayAsChance() {
        return true;
    }
}
