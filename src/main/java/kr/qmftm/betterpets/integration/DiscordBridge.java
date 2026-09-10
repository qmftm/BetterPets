package kr.qmftm.betterpets.integration;

import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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

    /** DiscordSRV 인스턴스와 채널 조회 메서드. null 이면 연동이 꺼진 것이다. */
    private Object discordSrv;
    private Method channelLookup;

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

            // 기동 시점에 채널이 실제로 잡히는지만 확인한다. sendMessage 메서드는
            // 캐시하지 않는다 — JDA 가 돌려주는 구현 클래스가 매번 같다는 보장이 없고,
            // 다르면 캐시해 둔 Method 가 IllegalArgumentException 을 낸다.
            // 알림은 드물게 나가므로 그때마다 찾아도 비용이 되지 않는다.
            if (channelLookup.invoke(discordSrv, this.channel) == null) {
                plugin.getLogger().warning("DiscordSRV 에 '" + this.channel
                    + "' 채널이 없습니다. Discord 알림은 꺼둡니다.");
                return disable();
            }
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
        return false;
    }

    /**
     * 밖에서 부를 수 있는 메서드를 찾는다.
     *
     * <p><b>구현 클래스에서 바로 찾으면 안 된다.</b> JDA 가 돌려주는 객체는 대개
     * package-private 클래스라, {@code getMethod} 가 공개 메서드를 찾아 주더라도
     * {@code invoke} 가 {@link IllegalAccessException} 을 낸다. 공개 타입(인터페이스나
     * 공개 상위 클래스)에서 선언된 것을 써야 한다.
     *
     * <p>구현 클래스부터 먼저 보는 이유는, 그 클래스가 공개라면 그게 가장 정확한
     * 대상이기 때문이다. 아니면 상속 계층과 인터페이스를 훑는다.
     */
    static Method publicMethod(final Class<?> type, final String name, final Class<?>... args)
        throws NoSuchMethodException {

        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (Modifier.isPublic(current.getModifiers())) {
                try {
                    return current.getMethod(name, args);
                } catch (final NoSuchMethodException ignored) {
                    // 이 계층엔 없다. 인터페이스 쪽에서 다시 찾는다.
                }
            }
            for (final Class<?> face : current.getInterfaces()) {
                try {
                    return face.getMethod(name, args);
                } catch (final NoSuchMethodException ignored) {
                    // 다음 인터페이스로.
                }
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + " 를 공개 타입에서 찾지 못했습니다");
    }

    /** 한 줄 보낸다. 연동이 없으면 아무 일도 하지 않는다. */
    public void send(final String text) {
        if (discordSrv == null || text == null || text.isBlank()) {
            return;
        }
        // 종료 중에 스케줄러를 건드리면 IllegalPluginAccessException 이 난다.
        // 마지막 알림 한 줄 때문에 종료 로그를 더럽힐 이유가 없다.
        if (!plugin.isEnabled()) {
            return;
        }
        // 네트워크 왕복이다. 메인 스레드에서 하지 않는다.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                final Object target = channelLookup.invoke(discordSrv, channel);
                if (target == null) {
                    return;     // 채널이 사라졌다. 다음 호출에서 다시 시도한다
                }
                // JDA 의 MessageChannel#sendMessage(CharSequence) — 결과는 RestAction 이라
                // queue() 를 불러야 실제로 나간다. 이걸 빼면 요청이 만들어지기만 한다.
                final Object action =
                    publicMethod(target.getClass(), "sendMessage", CharSequence.class)
                        .invoke(target, text);
                publicMethod(action.getClass(), "queue").invoke(action);
            } catch (final ReflectiveOperationException | RuntimeException error) {
                // 한 번 실패했다고 연동 전체를 내리지는 않는다. 순간적인 네트워크 문제일 수 있다.
                plugin.getLogger().fine("Discord 전송 실패: " + error);
            }
        });
    }
}
