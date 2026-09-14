package kr.qmftm.betterpets.integration.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import kr.qmftm.betterpets.domain.PetData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.Collection;

/**
 * {@code owned pets of %player%} / {@code %player%'s pets} — 보유 중인 펫 전체의
 * id 목록. {@link kr.qmftm.betterpets.storage.PetStore} 를 본다 — 소환 여부와
 * 무관하게 전부 잡힌다({@link ExprActivePets} 는 소환 중인 것만 본다).
 */
public final class ExprOwnedPets extends SimpleExpression<String> {

    static void register() {
        Skript.registerExpression(
            ExprOwnedPets.class, String.class, ExpressionType.COMBINED,
            "[the] owned pet[s] of %player%",
            "%player%'s pets"
        );
    }

    private Expression<Player> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(final Expression<?>[] exprs, final int matchedPattern,
                        final Kleenean isDelayed, final SkriptParser.ParseResult parseResult) {
        playerExpr = (Expression<Player>) exprs[0];
        return true;
    }

    @Override
    protected String[] get(final Event event) {
        final Player player = playerExpr.getSingle(event);
        if (player == null || SkriptBridge.store() == null) {
            return new String[0];
        }
        final Collection<PetData> owned = SkriptBridge.store().owned(player.getUniqueId());
        final String[] ids = new String[owned.size()];
        int i = 0;
        for (final PetData pet : owned) {
            ids[i++] = pet.petId().toString();
        }
        return ids;
    }

    @Override
    public boolean isSingle() {
        return false;
    }

    @Override
    public Class<? extends String> getReturnType() {
        return String.class;
    }

    @Override
    public String toString(final Event event, final boolean debug) {
        return "owned pets of " + playerExpr.toString(event, debug);
    }
}
