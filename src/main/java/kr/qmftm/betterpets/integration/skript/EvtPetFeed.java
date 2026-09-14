package kr.qmftm.betterpets.integration.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptParser;
import kr.qmftm.betterpets.event.PetFedEvent;
import org.bukkit.event.Event;

/**
 * {@code on pet feed:} — 펫에게 먹이를 줘서 실제로 반영됐을 때(포만도가 꽉 차 거절된
 * 경우는 안 잡힌다).
 *
 * <p>{@code player} 는 Skript 가 알아서 주고, {@code event-pet} 은
 * {@link ExprEventPet} 이 준다.
 */
public final class EvtPetFeed extends SkriptEvent {

    static void register() {
        Skript.registerEvent(
            "Pet Feed",
            EvtPetFeed.class,
            PetFedEvent.class,
            "pet feed[ing]",
            "pet fed"
        );
    }

    @Override
    public boolean init(final Literal<?>[] args, final int matchedPattern, final SkriptParser.ParseResult parseResult) {
        return true;
    }

    @Override
    public boolean check(final Event event) {
        return event instanceof PetFedEvent;
    }

    @Override
    public String toString(final Event event, final boolean debug) {
        return "pet feed";
    }
}
