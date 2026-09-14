package kr.qmftm.betterpets.integration.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptParser;
import kr.qmftm.betterpets.event.PetSummonedEvent;
import org.bukkit.event.Event;

/**
 * {@code on pet summon:} — 펫을 소환했을 때(보관함 소환 버튼, {@code /pet summon}).
 *
 * <p>{@code player} 는 Skript 가 알아서 주고, {@code event-pet} 은
 * {@link ExprEventPet} 이 준다.
 */
public final class EvtPetSummon extends SkriptEvent {

    static void register() {
        Skript.registerEvent(
            "Pet Summon",
            EvtPetSummon.class,
            PetSummonedEvent.class,
            "pet summon[ed]",
            "pet summon[ing]"
        );
    }

    @Override
    public boolean init(final Literal<?>[] args, final int matchedPattern, final SkriptParser.ParseResult parseResult) {
        return true;
    }

    @Override
    public boolean check(final Event event) {
        return event instanceof PetSummonedEvent;
    }

    @Override
    public String toString(final Event event, final boolean debug) {
        return "pet summon";
    }
}
