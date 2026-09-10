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

    private final boolean enabled;
    private final Rarity minRarity;
    private final int minGrowthStage;
    private final boolean onObtain;
    private final boolean onGrown;
    private final boolean onStageUp;
    private final boolean sound;

    public BroadcastService(final Server server,
                            final Messages messages,
                            final DiscordBridge discord,
                            final boolean enabled,
                            final Rarity minRarity,
                            final int minGrowthStage,
                            final boolean onObtain,
                            final boolean onGrown,
                            final boolean onStageUp,
                            final boolean sound) {
        this.server = server;
        this.messages = messages;
        this.discord = discord;
        this.enabled = enabled;
        this.minRarity = minRarity;
        this.minGrowthStage = Math.max(1, minGrowthStage);
        this.onObtain = onObtain;
        this.onGrown = onGrown;
        this.onStageUp = onStageUp;
        this.sound = sound;
    }

    /** 알에서 펫이 나왔을 때. 뽑기 결과를 자랑하는 자리다. */
    public void onObtained(final Player owner, final PetData data, final PetType type) {
        if (onObtain) {
            announce("broadcast.obtained", owner, data, type);
        }
    }

    /** 성체가 됐을 때. 키운 결과라 획득보다 이쪽이 알릴 값어치가 크다. */
    public void onGrown(final Player owner, final PetData data, final PetType type) {
        if (onGrown) {
            announce("broadcast.grown", owner, data, type);
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
        if (onStageUp) {
            announce("broadcast.stage-up", owner, data, type);
        }
    }

    private void announce(final String key, final Player owner, final PetData data, final PetType type) {
        if (!enabled || !qualifies(data, type)) {
            return;
        }
        final String[] placeholders = {
            "player", owner.getName(),
            "stage", String.valueOf(data.growthStage()),
            "pet", Tags.strip(data.displayNameOr(type.displayName())),
            "rarity", type.rarity().name(),
            "rarity-name", Tags.strip(type.rarity().displayName()),
            "type", Tags.strip(type.displayName())
        };

        final Component line = messages.bare(key, placeholders);
        server.sendMessage(line);   // 콘솔까지 한 번에 간다
        if (sound) {
            for (final Player online : server.getOnlinePlayers()) {
                online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.0f);
            }
        }
        // Discord 에는 서식을 걷어낸 평문을 보낸다. MiniMessage 태그가 그대로 가면 흉하다.
        discord.send(Tags.strip(messages.raw(key, placeholders)));
    }

    /** 알릴 만한 일인가. 등급과 성장 단계를 모두 넘겨야 한다. */
    private boolean qualifies(final PetData data, final PetType type) {
        return type.rarity().atLeast(minRarity) && data.growthStage() >= minGrowthStage;
    }

}
