package kr.qmftm.betterpets.integration;

import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * DiscordSRV 로 알림을 흘려보낸다.
 *
 * <p><b>리플렉션으로 붙는다 — 컴파일 의존이 없다.</b> DiscordSRV 는 메이븐 센트럴에 없고
 * 자체 저장소에 있어서, 의존을 걸면 이 플러그인을 빌드하려는 사람이 저장소 설정까지
 * 따라 해야 한다. 어차피 쓰는 건 "채널 하나에 문자열 하나 보내기"뿐이라 값이 맞지 않는다.
 *
 * <p>대신 연결에 실패하면 <b>조용히 꺼진다.</b> Discord 알림이 안 가는 것보다 그 때문에
 * 펫 시스템이 멈추는 게 훨씬 나쁘다. 실패 이유는 기동 시 한 번만 로그에 남긴다.
 *
 * <p>전송은 비동기로 한다. Discord 왕복은 네트워크라 메인 스레드에서 하면 서버가 멈춘다.
 */
public final class DiscordBridge {

    private final Plugin plugin;
    private final boolean enabled;
    private final String channel;

    /** {@code DiscordSRVApi#getTextChannelFromChannelName} 에 해당하는 메서드. 없으면 꺼진 것이다. */
    private Object discordSrv;
    private Method channelLookup;
    private Method sendMessage;

    public DiscordBridge(final Plugin plugin, final boolean enabled, final String channel) {
        this.plugin = plugin;
        this.enabled = enabled;
        this.channel = channel == null || channel.isBlank() ? "global" : channel.trim();
    }

    /** 꺼져 있거나 연결에 실패했으면 false. 기동 로그에 그대로 쓴다. */
    public boolean connect() {
        if (!enabled) {
            return false;
        }
        if (!plugin.getServer().getPluginManager().isPluginEnabled("DiscordSRV")) {
            plugin.getLogger().info("DiscordSRV 가 없어 Discord 알림은 꺼둡니다.");
            return false;
        }
        try {
            final Class<?> type = Class.forName("github.scarsz.discordsrv.DiscordSRV");
            discordSrv = type.getMethod("getPlugin").invoke(null);

            // 채널 이름 → JDA TextChannel. DiscordSRV 가 채널 별칭을 여기서 풀어준다.
            channelLookup = type.getMethod("getDestinationTextChannelForGameChannelName", String.class);

            final Object probe = channelLookup.invoke(discordSrv, this.channel);
            if (probe == null) {
                plugin.getLogger().warning("DiscordSRV 에 '" + this.channel
                    + "' 채널이 없습니다. Discord 알림은 꺼둡니다.");
                return disable();
            }
            // JDA 의 MessageChannel#sendMessage(CharSequence) — 결과는 RestAction 이라 queue() 해야 나간다.
            sendMessage = probe.getClass().getMethod("sendMessage", CharSequence.class);
            plugin.getLogger().info("DiscordSRV 연동됨. 알림 채널: " + this.channel);
            return true;
        } catch (final ReflectiveOperationException | RuntimeException error) {
            plugin.getLogger().log(Level.WARNING,
                "DiscordSRV 연동에 실패했습니다. Discord 알림 없이 계속합니다: " + error, error);
            return disable();
        }
    }

    private boolean disable() {
        discordSrv = null;
        channelLookup = null;
        sendMessage = null;
        return false;
    }

    /** 한 줄 보낸다. 연동이 없으면 아무 일도 하지 않는다. */
    public void send(final String text) {
        if (discordSrv == null || sendMessage == null || text == null || text.isBlank()) {
            return;
        }
        // 네트워크 왕복이다. 메인 스레드에서 하지 않는다.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                final Object target = channelLookup.invoke(discordSrv, channel);
                if (target == null) {
                    return;     // 채널이 사라졌다. 다음 호출에서 다시 시도한다
                }
                final Object action = sendMessage.invoke(target, text);
                // RestAction#queue() — 이걸 부르지 않으면 요청이 만들어지기만 하고 안 나간다.
                action.getClass().getMethod("queue").invoke(action);
            } catch (final ReflectiveOperationException | RuntimeException error) {
                // 한 번 실패했다고 연동 전체를 내리지는 않는다. 순간적인 네트워크 문제일 수 있다.
                plugin.getLogger().fine("Discord 전송 실패: " + error);
            }
        });
    }
}
