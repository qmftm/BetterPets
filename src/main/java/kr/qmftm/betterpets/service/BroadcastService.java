package kr.qmftm.betterpets.service;

import kr.qmftm.betterpets.config.Messages;
import kr.qmftm.betterpets.config.Tags;
import kr.qmftm.betterpets.domain.PetData;
import kr.qmftm.betterpets.domain.PetType;
import kr.qmftm.betterpets.domain.Rarity;
import kr.qmftm.betterpets.integration.DiscordBridge;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * 서버 전체 알림.
 *
 * <p>희귀한 일이 일어났을 때만 운다. 아무 펫이나 알릴 거면 이 기능은 소음일 뿐이고,
 * 5명 서버에서 채팅이 밀리는 건 실제 피해다. 그래서 <b>기본 문턱을 A등급으로</b> 두고
 * 등급·성장 단계 두 조건을 모두 넘겨야 나간다.
 *
 * <p>같은 문구가 게임 채팅과 Discord 두 곳으로 간다. Discord 쪽은 연동이 없으면
 * 조용히 아무 일도 하지 않는다 — {@link DiscordBridge} 참고.
 */
public final class BroadcastService {

    private final Server server;
    private final Messages messages;
    private final DiscordBridge discord;

    /**
     * 방송 조건. {@code /betterpets reload} 로 통째로 갈아끼운다 —
     * 설정을 다시 읽었는데 방송 문턱만 예전 값이면 "리로드했다"가 거짓말이 된다.
     */
    public record Rules(boolean enabled,
                        Rarity minRarity,
                        int minGrowthStage,
                        boolean onObtain,
                        boolean onStageUp,
                        boolean sound) {
        public Rules {
            minGrowthStage = Math.max(1, minGrowthStage);
        }

        /**
         * 알릴 만한 일인가. 등급과 성장 단계를 <b>모두</b> 넘겨야 한다.
         *
         * <p>판정을 규칙 옆에 두는 이유는 이게 이 기능의 전부이기 때문이다 — 나머지는
         * 문자열을 조립해 보내는 배관이다. 여기 있어야 Bukkit 없이 검증할 수 있다.
         */
        public boolean qualifies(final Rarity rarity, final int growthStage) {
            return enabled && rarity != null
                && rarity.atLeast(minRarity)
                && growthStage >= minGrowthStage;
        }
    }

    private volatile Rules rules;

    public BroadcastService(final Server server,
                            final Messages messages,
                            final DiscordBridge discord,
                            final Rules rules) {
        this.server = server;
        this.messages = messages;
        this.discord = discord;
        this.rules = rules;
    }

    /** {@code /betterpets reload} 가 부른다. */
    public void rules(final Rules value) {
        rules = value;
    }

    /** 알에서 펫이 나왔을 때. 뽑기 결과를 자랑하는 자리다. */
    public void onObtained(final Player owner, final PetData data, final PetType type) {
        if (rules.onObtain()) {
            announce("broadcast.obtained", owner, data, type);
        }
    }

    /**
     * 성장 단계가 올랐을 때.
     *
     * <p>{@code min-growth-stage} 가 실제로 쓸모 있는 곳이 여기다. 획득 시점의 단계는
     * 언제나 1이라 문턱을 2 이상으로 두면 획득 알림은 아예 나가지 않는다 — 그건 문턱을
     * 켠 사람의 의도가 아닐 것이다. "일정 단계를 넘겼다"를 알리려면 넘긴 순간이 필요하다.
     */
    public void onStageUp(final Player owner, final PetData data, final PetType type) {
        if (rules.onStageUp()) {
            announce("broadcast.stage-up", owner, data, type);
        }
    }

    private void announce(final String key, final Player owner, final PetData data, final PetType type) {
        final Rules current = rules;
        if (!current.qualifies(type.rarity(), data.growthStage())) {
            return;
        }
        final String[] placeholders = {
            "player", owner.getName(),
            "stage", String.valueOf(data.growthStage()),
            "pet", Tags.strip(data.displayNameOr(type.displayName())),
            "rarity", type.rarity().name(),
            "rarity-name", Tags.strip(type.stats().displayName()),
            "type", Tags.strip(type.displayName())
        };

        final Component line = messages.bare(key, placeholders);
        server.sendMessage(line);   // 콘솔까지 한 번에 간다
        if (current.sound()) {
            for (final Player online : server.getOnlinePlayers()) {
                online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.0f);
            }
        }
        // Discord 에는 서식을 걷어낸 평문을 보낸다. MiniMessage 태그가 그대로 가면 흉하다.
        discord.send(Tags.strip(messages.raw(key, placeholders)));
    }

}
