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

    /**
     * 설정에서 온 값들. {@code final} 이 아닌 이유는 {@code /betterpets reload} 다.
     *
     * <p>생성자에서 붙박아 두고 있었다. 그래서 채널을 바꾸고 리로드해도 예전 채널로
     * 계속 나갔고, {@code enabled: false} 로 꺼도 계속 나갔다 — "설정을 다시 읽었습니다"가
     * 거짓말이 되는 자리가 하나 더 있었던 셈이다.
     *
     * <p>{@code volatile} 인 이유는 {@link #send} 가 비동기 스레드에서 읽기 때문이다.
     * 리로드는 메인 스레드에서 일어나므로 둘이 겹칠 수 있다.
     */
    private volatile boolean enabled;
    private volatile String channel;

    /** DiscordSRV 인스턴스와 채널 조회 메서드. null 이면 연동이 꺼진 것이다. */
    private volatile Object discordSrv;
    private volatile Method channelLookup;

    /**
     * 전송 실패를 한 번은 눈에 보이게 알렸는가.
     *
     * <p>전에는 전부 {@code fine} 으로 남겼다. 기본 로그 수준에서는 보이지 않으니,
     * DiscordSRV 의 호출 사슬이 바뀌어 모든 알림이 실패해도 흔적이 없었다.
     * 첫 실패만 경고로 올리고 그 뒤는 조용히 넘긴다 — 반복 실패로 로그를 덮지 않으면서
     * "뭔가 잘못됐다"는 신호는 남는다.
     */
    private volatile boolean warnedOnSendFailure;

    public DiscordBridge(final Plugin plugin, final boolean enabled, final String channel) {
        this.plugin = plugin;
        this.enabled = enabled;
        this.channel = normalizeChannel(channel);
    }

    private static String normalizeChannel(final String raw) {
        return raw == null || raw.isBlank() ? "global" : raw.trim();
    }

    /**
     * {@code /betterpets reload} 가 부른다. 설정을 다시 받아 처음부터 연결한다.
     *
     * @return 연동이 살아 있으면 true
     */
    public boolean reload(final boolean enabledNow, final String channelNow) {
        this.enabled = enabledNow;
        this.channel = normalizeChannel(channelNow);
        this.warnedOnSendFailure = false;   // 새 설정이니 실패도 다시 알릴 값어치가 있다
        disable();                          // 예전 연결 상태를 버리고 다시 잡는다
        return connect();
    }

    /** 꺼져 있거나 연결에 실패했으면 false. 기동 로그에 그대로 쓴다. */
    public boolean connect() {
        if (!enabled) {
            return false;
        }
        final Plugin discordSrvPlugin = plugin.getServer().getPluginManager().getPlugin("DiscordSRV");
        if (discordSrvPlugin == null || !discordSrvPlugin.isEnabled()) {
            plugin.getLogger().info("DiscordSRV 가 없어 Discord 알림은 꺼둡니다.");
            return false;
        }
        try {
            // Class.forName(name) 은 호출자(BetterPets)의 클래스로더로 찾는다. Paper 는
            // 플러그인 클래스로더를 격리하고 paper-plugin.yml 에서 DiscordSRV 를
            // join-classpath: false 로 뒀기 때문에, DiscordSRV 가 이미 떠서 정상 작동
            // 중이어도 이 클래스로더로는 안 보인다 — 실기에서 DiscordSRV 가 멀쩡히 뜬
            // 뒤에도 ClassNotFoundException 이 나는 걸로 확인했다. DiscordSRV 자신의
            // 클래스로더로 찾아야 한다.
            final Class<?> type = discordSrvPlugin.getClass().getClassLoader()
                .loadClass("github.scarsz.discordsrv.DiscordSRV");
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
                // 다만 첫 실패는 눈에 보여야 한다 — 호출 사슬이 바뀌어 전부 실패하는
                // 상황과 일시적인 실패가 로그에서 똑같이 보이면 안 된다.
                if (warnedOnSendFailure) {
                    plugin.getLogger().fine("Discord 전송 실패: " + error);
                } else {
                    warnedOnSendFailure = true;
                    plugin.getLogger().warning("Discord 전송에 실패했습니다."
                        + " 계속되면 DiscordSRV 쪽을 확인하세요 (이 경고는 한 번만 남깁니다): "
                        + error);
                }
            }
        });
    }
}
