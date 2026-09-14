package kr.qmftm.betterpets.integration.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptParser;
import kr.qmftm.betterpets.event.PetReleasedEvent;
import org.bukkit.event.Event;

public final class EvtPetRelease extends SkriptEvent {

    static void register() {
        Skript.registerEvent(
            "Pet Release",
            EvtPetRelease.class,
            PetReleasedEvent.class,
            "pet release[d]",
            "pet releas(e|ing)"
        );
    }

    @Override
    public boolean init(final Literal<?>[] args, final int matchedPattern, final SkriptParser.ParseResult parseResult) {
        return true;
    }

    @Override
    public boolean check(final Event event) {
        return event instanceof PetReleasedEvent;
    }

    @Override
    public String toString(final Event event, final boolean debug) {
        return "pet release";
    }
}
