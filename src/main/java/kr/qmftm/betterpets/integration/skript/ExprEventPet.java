package kr.qmftm.betterpets.integration.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import kr.qmftm.betterpets.event.PetObtainedEvent;
import org.bukkit.event.Event;

/**
 * {@code event-pet} — {@link EvtPetObtain}({@code on pet obtain}) 안에서, 새로 얻은
 * 펫의 id(문자열)를 준다.
 *
 * <p><b>{@code EventValues.registerEventValue} 대신 직접 패턴을 등록한다.</b> 그 API 는
 * 등록한 자바 타입의 {@code ClassInfo} 코드네임으로 {@code event-<코드네임>} 을 자동으로
 * 만든다 — {@code String} 은 이미 코드네임이 "string" 이라 그 경로로는 {@code event-string}
 * 만 나오고 {@code event-pet} 이 안 나온다. 새 타입을 등록하는 대신, "event-pet" 이라는
 * 고정 문구 자체를 하나의 Expression 으로 등록해 원하는 이름을 그대로 얻는다.
 */
public final class ExprEventPet extends SimpleExpression<String> {

    static void register() {
        Skript.registerExpression(
            ExprEventPet.class, String.class, ExpressionType.SIMPLE,
            "event-pet"
        );
    }

    @Override
    public boolean init(final Expression<?>[] exprs, final int matchedPattern,
                        final Kleenean isDelayed, final SkriptParser.ParseResult parseResult) {
        return true;
    }

    @Override
    protected String[] get(final Event event) {
        if (!(event instanceof PetObtainedEvent obtained)) {
            return new String[0];
        }
        return new String[] {obtained.pet().petId().toString()};
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Class<? extends String> getReturnType() {
        return String.class;
    }

    @Override
    public String toString(final Event event, final boolean debug) {
        return "event-pet";
    }
}
