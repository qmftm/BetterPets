package kr.qmftm.betterpets.integration.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import kr.qmftm.betterpets.runtime.ActivePet;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;

/**
 * {@code summoned pets of %player%} / {@code %player%'s summoned pets} — 지금
 * 소환 중인 펫들의 id 목록. {@link kr.qmftm.betterpets.runtime.PetRegistry} 를 본다 —
 * 보관함에 넣어둔 펫은 안 잡힌다({@link ExprOwnedPets} 가 그쪽이다).
 */
public final class ExprActivePets extends SimpleExpression<String> {

    static void register() {
        Skript.registerExpression(
            ExprActivePets.class, String.class, ExpressionType.COMBINED,
            "[the] summoned pet[s] of %player%",
            "%player%'s summoned pet[s]"
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
        if (player == null || SkriptBridge.registry() == null) {
            return new String[0];
        }
        final List<ActivePet> active = SkriptBridge.registry().allOf(player.getUniqueId());
        final String[] ids = new String[active.size()];
        for (int i = 0; i < active.size(); i++) {
            ids[i] = active.get(i).petId().toString();
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
        return "summoned pets of " + playerExpr.toString(event, debug);
    }
}
