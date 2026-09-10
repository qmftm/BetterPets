package kr.qmftm.betterpets.ability;

import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetType;
import org.bukkit.event.Event;

/**
 * 펫 능력.
 *
 * <p><b>계약이 하나 있다: {@link #onUnequip} 은 {@link #onEquip} 의 정확한 역연산이어야 한다.</b>
 * 이걸 어기면 상태가 샌다 — 대표적으로 {@code AttributeModifier} 가 재접속마다 쌓여
 * 플레이어가 로켓처럼 날아가는 버그가 된다.
 *
 * <p>새 능력 추가는 이 인터페이스 구현체 하나 + {@link AbilityRegistry} 등록 한 줄이면 된다.
 */
public interface PetAbility {

    enum Type {
        /** 소환 중 상시 적용. */
        PASSIVE,
        /** 특정 이벤트에 반응. */
        TRIGGER
    }

    /** 설정에서 이 능력을 가리키는 id. */
    String id();

    Type type();

    /** 펫 소환 시. */
    default void onEquip(AbilityContext ctx) {}

    /** 펫 해제 시. onEquip 이 한 일을 정확히 되돌린다. */
    default void onUnequip(AbilityContext ctx) {}

    /** TRIGGER 타입만 쓴다. 관심 없는 이벤트면 아무것도 하지 않는다. */
    default void onEvent(AbilityContext ctx, Event event) {}

    /**
     * 보관함에 보여줄 수치.
     *
     * <p><b>능력마다 읽는 설정 키가 다르다.</b> GUI 가 {@code base}/{@code per-growth} 를
     * 직접 계산하고 있었는데, {@code chance-base} 를 쓰는 능력은 그 키가 없어서
     * <b>언제나 {@code +0.00} 으로 보였다.</b> 수치를 어디서 읽는지는 그 수치를 쓰는
     * 구현체만 안다 — 그러니 구현체가 답한다.
     */
    default double displayValue(PetData pet, PetType type, AbilityDefinition definition) {
        return AbilityContext.scaledValue(pet, type, definition);
    }

    /**
     * 이 수치가 확률인가.
     *
     * <p>확률을 {@code +0.15} 로 적으면 무슨 뜻인지 알 수 없다. true 면 GUI 가
     * 백분율로 바꿔 적는다.
     */
    default boolean displayAsChance() {
        return false;
    }
}
