package kr.qmftm.betterpets.integration;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Geyser(Bedrock) 플레이어를 알아본다.
 *
 * <p><b>왜 필요한가.</b> 조작 경로 몇 개가 자바 클라이언트를 전제로 한다:
 * <ul>
 *   <li>탑승 조향은 Paper 의 {@code Input} API 로 실제 키 입력을 읽는다. Geyser 는
 *       이 패킷을 자바와 똑같이 채워 보내주지 못하는 경우가 있다
 *   <li>비행 이륙 확인은 "3초 안에 한 번 더 우클릭"이다. 터치 조작에서는 두 번째
 *       우클릭을 맞히기가 훨씬 어렵다
 * </ul>
 * 그래서 Bedrock 플레이어에게는 확인 절차를 건너뛸 수 있게 해 두고, 조향은
 * 입력이 비었을 때 시선 방향으로 되돌아가는 기존 폴백에 맡긴다.
 *
 * <p>모델 자체(GeyserModelEngine)는 이 플러그인이 관여하지 않는다 — BetterModel 과
 * GME 사이에서 처리되고, 우리는 그 조합이 실제로 뜨는지 기동 로그로만 알린다.
 *
 * <p>Floodgate 도 리플렉션으로 붙는다. 이유는 {@link DiscordBridge} 와 같다 —
 * 없어도 돌아가야 하는 선택적 연동에 컴파일 의존을 걸 값이 없다.
 */
public final class BedrockSupport {

    private final Plugin plugin;
    private Method isFloodgatePlayer;
    private Object floodgateApi;

    private boolean geyserPresent;

    public BedrockSupport(final Plugin plugin) {
        this.plugin = plugin;
    }

    /** 기동 시 한 번. 무엇이 붙었는지 로그로 남긴다. */
    public void detect() {
        final var manager = plugin.getServer().getPluginManager();
        geyserPresent = manager.isPluginEnabled("Geyser-Spigot") || manager.isPluginEnabled("floodgate");

        if (!geyserPresent) {
            return;
        }
        try {
            final Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateApi = api.getMethod("getInstance").invoke(null);
            isFloodgatePlayer = api.getMethod("isFloodgatePlayer", UUID.class);
        } catch (final ReflectiveOperationException | RuntimeException error) {
            // Geyser 는 있는데 Floodgate 가 없는 구성이다. 그러면 개별 구분은 못 한다.
            floodgateApi = null;
            isFloodgatePlayer = null;
        }

        final boolean modelEngine = manager.isPluginEnabled("GeyserModelEngine");
        plugin.getLogger().info("Geyser 감지됨. Bedrock 플레이어 구분 "
            + (floodgateApi != null ? "가능(Floodgate)" : "불가(Floodgate 없음)")
            + ", GeyserModelEngine " + (modelEngine ? "있음" : "없음"));
        if (!modelEngine) {
            plugin.getLogger().warning("GeyserModelEngine 이 없습니다. "
                + "Bedrock 플레이어에게는 펫 모델이 보이지 않습니다 (히트박스만 존재).");
        }
    }

    /** Geyser 가 이 서버에 붙어 있는가. */
    public boolean geyserPresent() {
        return geyserPresent;
    }

    /**
     * 이 플레이어가 Bedrock 클라이언트인가.
     *
     * <p>Floodgate 가 없으면 판별할 수 없어 항상 {@code false} 다 — <b>모르면 자바로
     * 취급한다.</b> 반대로 하면 자바 플레이어의 조작까지 바뀌어서, 틀렸을 때의 피해가
     * 훨씬 크다.
     */
    public boolean isBedrock(final Player player) {
        if (isFloodgatePlayer == null || player == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(isFloodgatePlayer.invoke(floodgateApi, player.getUniqueId()));
        } catch (final ReflectiveOperationException | RuntimeException error) {
            return false;
        }
    }
}
