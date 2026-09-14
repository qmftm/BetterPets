package kr.qmftm.betterpets.integration.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.Locale;

/**
 * {@code %player% has pet %string%} / {@code %player% doesn't have pet %string%}.
 *
 * <p>준 문자열이 <b>종류 id</b>(예: {@code "phantom_normal"} — 이 종류를 하나라도
 * 가졌는가)와 같거나, <b>펫 id 의 앞자리</b>(특정 개체 하나를 가졌는가)와 일치하면
 * 참이다 — "이 종류를 가졌는가"와 "이 펫을 가졌는가"를 하나의 구문으로 같이 받는다.
 */
public final class CondHasPet extends Condition {

    static void register() {
        Skript.registerCondition(
            CondHasPet.class,
            "%player% has pet %string%",
            "%player% (doesn't|does not) have pet %string%"
        );
    }

    private Expression<Player> playerExpr;
    private Expression<String> needleExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(final Expression<?>[] exprs, final int matchedPattern,
                        final Kleenean isDelayed, final SkriptParser.ParseResult parseResult) {
        playerExpr = (Expression<Player>) exprs[0];
        needleExpr = (Expression<String>) exprs[1];
        setNegated(matchedPattern == 1);
        return true;
    }

    @Override
    public boolean check(final Event event) {
        final Player player = playerExpr.getSingle(event);
        final String needle = needleExpr.getSingle(event);
        if (player == null || needle == null || SkriptBridge.store() == null) {
            return isNegated();
        }
        final String lower = needle.toLowerCase(Locale.ROOT);
        final boolean has = SkriptBridge.store().owned(player.getUniqueId()).stream()
            .anyMatch(pet -> pet.typeId().equalsIgnoreCase(needle)
                || pet.petId().toString().toLowerCase(Locale.ROOT).startsWith(lower));
        return has != isNegated();
    }

    @Override
    public String toString(final Event event, final boolean debug) {
        return playerExpr.toString(event, debug) + (isNegated() ? " doesn't have pet " : " has pet ")
            + needleExpr.toString(event, debug);
    }
}
