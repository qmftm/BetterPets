package kr.qmftm.betterpets.service;

import kr.qmftm.betterpets.domain.PetEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/**
 * 먹이·탑승·하차·성장 때 나는 사운드·파티클을 재생한다.
 *
 * <p><b>이벤트마다 재생 코드를 따로 두지 않는다.</b> {@code config.yml} 의
 * {@code effects.<이벤트>} 하나하나를 이 클래스 하나가 읽고 재생한다 — 새 이벤트를
 * 더하고 싶으면 호출부에서 {@link #play} 를 새 키로 한 번 더 부르면 된다.
 *
 * <p><b>값을 들고 있지 않는다.</b> {@code /betterpets reload} 는 {@code reloadConfig()}
 * 를 먼저 부르므로, 매번 {@code plugin.getConfig()} 를 그대로 물어보면 이 클래스는
 * CLAUDE.md 가 추적하는 "리로드 때 다시 밀어 넣어야 하는" 목록에 오를 일이 없다 —
 * {@code PetItems}·{@code InteractionListener} 와 같은 이유다.
 *
 * <p>사운드·파티클 이름 오타는 여기서 조용히 넘어간다. 재생할 때마다 콘솔에 같은
 * 경고를 반복하는 대신, {@link kr.qmftm.betterpets.BetterPetsPlugin#reloadDefinitions}
 * 가 리로드 시점에 한 번만 검사해 알려준다.
 */
public final class EffectService {

    private final Plugin plugin;

    public EffectService(final Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * {@code effects.<key>} 에 적힌 효과를 소환자에게 재생한다.
     *
     * <p>소리와 파티클 둘 다 소환자의 현재 위치를 기준으로 한다 — 이 프로젝트의 다른
     * 모든 소리도 {@code player.getLocation()} 을 쓰고 있어서 그 관례를 그대로 따른다.
     * 섹션이 없으면(이벤트 하나를 통째로 꺼둔 것이다) 조용히 아무 일도 하지 않는다.
     */
    public void play(final String key, final Player owner) {
        final PetEffect effect = read(plugin.getConfig().getConfigurationSection("effects." + key));
        if (!effect.hasSound() && !effect.hasParticle()) {
            return;
        }
        final Location at = owner.getLocation();
        if (effect.hasSound()) {
            playSound(effect, owner, at);
        }
        if (effect.hasParticle()) {
            playParticle(effect, at);
        }
    }

    private PetEffect read(final ConfigurationSection section) {
        if (section == null) {
            return PetEffect.none();
        }
        return new PetEffect(
            section.getString("sound"),
            section.getDouble("volume", 1.0),
            section.getDouble("pitch", 1.0),
            section.getString("particle"),
            section.getInt("particle-count", 8)
        );
    }

    private void playSound(final PetEffect effect, final Player owner, final Location at) {
        try {
            final Sound sound = Sound.valueOf(effect.sound().toUpperCase(Locale.ROOT));
            owner.playSound(at, sound, (float) effect.volume(), (float) effect.pitch());
        } catch (final IllegalArgumentException invalidName) {
            // 잘못된 이름은 reloadDefinitions 의 유효성 검사가 이미 경고했다 — 여기서
            // 또 경고하면 재생할 때마다(펫마다, 먹일 때마다) 콘솔에 같은 줄이 반복된다.
        }
    }

    private void playParticle(final PetEffect effect, final Location at) {
        final World world = at.getWorld();
        if (world == null) {
            return;
        }
        try {
            final Particle particle = Particle.valueOf(effect.particle().toUpperCase(Locale.ROOT));
            world.spawnParticle(particle, at, effect.particleCount(), 0.3, 0.3, 0.3, 0.0);
        } catch (final IllegalArgumentException invalidName) {
            // 위와 같다.
        }
    }
}
