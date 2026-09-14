package kr.qmftm.betterpets.integration.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import kr.qmftm.betterpets.domain.PetData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.Locale;

/**
 * {@code remove pet %string% from %player%} — 그 id(앞자리만 적어도 된다)를 가진
 * 펫을 놓아준다.
 *
 * <p>{@code /pet release} 와 같은 경로
 * ({@link kr.qmftm.betterpets.service.PetService#release})를 쓴다 —
 * <b>되돌릴 수 없다.</b> 일치하는 펫이 없으면 조용히 아무 일도 하지 않는다.
 */
public final class EffRemovePet extends Effect {

    static void register() {
        Skript.registerEffect(
            EffRemovePet.class,
            "remove pet %string% from %player%"
        );
    }

    private Expression<String> idExpr;
    private Expression<Player> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(final Expression<?>[] exprs, final int matchedPattern,
                        final Kleenean isDelayed, final SkriptParser.ParseResult parseResult) {
        idExpr = (Expression<String>) exprs[0];
        playerExpr = (Expression<Player>) exprs[1];
        return true;
    }

    @Override
    protected void execute(final Event event) {
        final String needle = idExpr.getSingle(event);
        final Player player = playerExpr.getSingle(event);
        if (needle == null || player == null
            || SkriptBridge.store() == null || SkriptBridge.pets() == null) {
            return;
        }
        final String lower = needle.toLowerCase(Locale.ROOT);
        final PetData target = SkriptBridge.store().owned(player.getUniqueId()).stream()
            .filter(pet -> pet.petId().toString().toLowerCase(Locale.ROOT).startsWith(lower))
            .findFirst()
            .orElse(null);
        if (target == null) {
            return;
        }
        SkriptBridge.pets().release(player, target);
    }

    @Override
    public String toString(final Event event, final boolean debug) {
        return "remove pet " + idExpr.toString(event, debug) + " from " + playerExpr.toString(event, debug);
    }
}
