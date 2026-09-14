package kr.qmftm.betterpets.integration.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptParser;
import kr.qmftm.betterpets.event.PetDismissedEvent;
import org.bukkit.event.Event;

/**
 * {@code on pet dismiss:} — 펫을 소환 해제했을 때(보관함 소환 해제 버튼,
 * {@code /pet dismiss}, 퇴장, 놓아주기 전 자동 해제 포함).
 *
 * <p>{@code player} 는 Skript 가 알아서 주고, {@code event-pet} 은
 * {@link ExprEventPet} 이 준다.
 */
public final class EvtPetDismiss extends SkriptEvent {

    static void register() {
        Skript.registerEvent(
            "Pet Dismiss",
            EvtPetDismiss.class,
            PetDismissedEvent.class,
            "pet dismiss[ed]",
            "pet dismiss[ing]"
        );
    }

    @Override
    public boolean init(final Literal<?>[] args, final int matchedPattern, final SkriptParser.ParseResult parseResult) {
        return true;
    }

    @Override
    public boolean check(final Event event) {
        return event instanceof PetDismissedEvent;
    }

    @Override
    public String toString(final Event event, final boolean debug) {
        return "pet dismiss";
    }
}
