package kr.qmftm.betterpets.integration.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

/**
 * {@code add pet %string% to %player%} — 그 종류의 펫 한 마리를 지급한다.
 *
 * <p>{@code /betterpets give} 와 같은 경로
 * ({@link kr.qmftm.betterpets.service.PetService#grantPet})를 쓴다. 종류 id 가
 * {@code pets/*.yml} 에 없거나 보유 한도가 찼으면 조용히 아무 일도 하지 않는다 —
 * 관리자 명령과 달리 스크립트에는 채팅으로 알릴 대상이 마땅치 않다.
 */
public final class EffAddPet extends Effect {

    static void register() {
        Skript.registerEffect(
            EffAddPet.class,
            "add pet %string% to %player%"
        );
    }

    private Expression<String> typeExpr;
    private Expression<Player> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(final Expression<?>[] exprs, final int matchedPattern,
                        final Kleenean isDelayed, final SkriptParser.ParseResult parseResult) {
        typeExpr = (Expression<String>) exprs[0];
        playerExpr = (Expression<Player>) exprs[1];
        return true;
    }

    @Override
    protected void execute(final Event event) {
        final String typeId = typeExpr.getSingle(event);
        final Player player = playerExpr.getSingle(event);
        if (typeId == null || player == null
            || SkriptBridge.catalog() == null || SkriptBridge.pets() == null) {
            return;
        }
        if (SkriptBridge.catalog().type(typeId).isEmpty()) {
            return;     // 없는 종류 id. 관리자 명령(/betterpets give)과 같은 검증이다
        }
        SkriptBridge.pets().grantPet(player, typeId);
    }

    @Override
    public String toString(final Event event, final boolean debug) {
        return "add pet " + typeExpr.toString(event, debug) + " to " + playerExpr.toString(event, debug);
    }
}
