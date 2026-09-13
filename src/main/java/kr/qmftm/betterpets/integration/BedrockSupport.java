package kr.qmftm.betterpets.integration;

import org.bukkit.plugin.Plugin;

/**
 * Geyser(Bedrock) 연동 진단.
 *
 * <p>모델 자체(GeyserModelEngine)는 이 플러그인이 관여하지 않는다 — BetterModel 과
 * GME 사이에서 처리되고, 우리는 그 조합이 실제로 뜨는지 기동 로그로만 알린다.
 *
 * <p>예전에는 Floodgate 로 개별 플레이어가 Bedrock 인지 구분해, 비행 이륙 확인(두 번째
 * 우클릭)을 터치 조작에서 건너뛰게 했다. 그 확인 절차 자체가 없어지면서(자바·Bedrock
 * 모두 우클릭 한 번이면 탑승·이륙한다) 개별 구분이 더는 필요 없어 걷어냈다.
 */
public final class BedrockSupport {

    private final Plugin plugin;

    public BedrockSupport(final Plugin plugin) {
        this.plugin = plugin;
    }

    /** 기동 시 한 번. 무엇이 붙었는지 로그로 남긴다. */
    public void detect() {
        final var manager = plugin.getServer().getPluginManager();
        final boolean geyser =
            manager.isPluginEnabled("Geyser-Spigot") || manager.isPluginEnabled("floodgate");
        if (!geyser) {
            return;
        }
        final boolean modelEngine = manager.isPluginEnabled("GeyserModelEngine");
        plugin.getLogger().info("Geyser 감지됨. GeyserModelEngine " + (modelEngine ? "있음" : "없음"));
        if (!modelEngine) {
            plugin.getLogger().warning("GeyserModelEngine 이 없습니다. "
                + "Bedrock 플레이어에게는 펫 모델이 보이지 않습니다 (히트박스만 존재).");
        }
    }
}
