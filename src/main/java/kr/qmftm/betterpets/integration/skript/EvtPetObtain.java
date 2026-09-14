package kr.qmftm.betterpets.integration.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptParser;
import kr.qmftm.betterpets.event.PetObtainedEvent;
import org.bukkit.event.Event;

/**
 * {@code on pet obtain:} — 알을 까거나 관리자가 지급해 새 펫을 얻었을 때.
 *
 * <p>안에서 쓸 수 있는 값:
 * <ul>
 *   <li>{@code player} — 얻은 사람. {@link PetObtainedEvent} 가 {@code PlayerEvent} 라
 *       Skript 가 따로 등록하지 않아도 알아서 준다
 *   <li>{@code event-pet} — 새로 얻은 펫의 id (문자열). {@link ExprEventPet} 이 제공한다
 * </ul>
 */
public final class EvtPetObtain extends SkriptEvent {

    static void register() {
        Skript.registerEvent(
            "Pet Obtain",
            EvtPetObtain.class,
            PetObtainedEvent.class,
            "pet obtain[ed]",
            "pet obtain[ing]"
        );
    }

    @Override
    public boolean init(final Literal<?>[] args, final int matchedPattern, final SkriptParser.ParseResult parseResult) {
        return true;
    }

    @Override
    public boolean check(final Event event) {
        return event instanceof PetObtainedEvent;
    }

    @Override
    public String toString(final Event event, final boolean debug) {
        return "pet obtain";
    }
}
