package kr.qmftm.betterpets.runtime;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * 펫 모델이 올라탈 캐리어 엔티티를 만들고 청소한다.
 *
 * <p><b>왜 보이지 않는 {@code Mob} 인가</b> — BetterModel 은 엔티티를 스폰하지 않고 기존
 * 엔티티에 모델을 얹는다. 그 엔티티가 위치·회전을 담당한다. {@code ItemDisplay} 도 되지만
 * 히트박스가 없어 클릭을 받으려면 {@code Interaction} 엔티티를 하나 더 붙여야 한다.
 * {@code Mob} 은 자체 히트박스가 있어서 모델에 히트박스 본(`b_`)이 없어도 우클릭이 먹는다.
 *
 * <p>{@code Allay} 를 고른 이유는 히트박스가 작아(0.35×0.6) 플레이어의 시야나 채굴을
 * 방해할 여지가 적기 때문이다. AI 를 끄므로 부양 특성 같은 것은 작동하지 않는다.
 */
public final class CarrierFactory {

    private final NamespacedKey petKey;

    public CarrierFactory(final Plugin plugin) {
        this.petKey = new NamespacedKey(plugin, "pet_id");
    }

    public NamespacedKey petKey() {
        return petKey;
    }

    /**
     * 캐리어를 스폰한다.
     *
     * <p>설정 하나하나가 의도적이다. 하나라도 빠지면 펫이 제멋대로 움직이거나,
     * 소리를 내거나, 청크에 저장돼 서버 재시작 후 유령으로 남는다.
     */
    public Mob spawn(final Location location, final UUID petId) {
        final World world = location.getWorld();
        return world.spawn(location, Allay.class, CreatureSpawnEvent.SpawnReason.CUSTOM, entity -> {
            entity.setAI(false);              // 바닐라 경로탐색 차단 — 이동은 우리가 계산한다
            entity.setGravity(false);         // 우리가 y 를 정한다. 중력이 끼면 텔레포트와 싸운다
            entity.setInvisible(true);        // 실제로 보이는 것은 BetterModel 이 그린 모델이다
            entity.setSilent(true);
            entity.setInvulnerable(true);
            entity.setCollidable(false);      // 플레이어를 밀지 않는다
            entity.setPersistent(false);      // 청크 저장 대상에서 제외
            entity.setRemoveWhenFarAway(false);
            entity.setCanPickupItems(false);

            // ★ PDC 태깅. 서버가 비정상 종료되면 close 가 불리지 않아 캐리어가 남는다.
            //   다음 기동 때 이 태그로 찾아 지운다.
            entity.getPersistentDataContainer()
                .set(petKey, PersistentDataType.STRING, petId.toString());
        });
    }

    /** 이 엔티티가 우리가 만든 캐리어인가. */
    public boolean isCarrier(final Entity entity) {
        return entity != null
            && entity.getPersistentDataContainer().has(petKey, PersistentDataType.STRING);
    }

    /**
     * 로드된 월드에 남아 있는 캐리어를 전부 지운다.
     *
     * <p>기동 시 한 번 호출한다. 정상 종료였다면 지울 게 없고, 크래시였다면 여기서 정리된다.
     *
     * @return 지운 개수
     */
    public int purgeOrphans(final Plugin plugin) {
        int removed = 0;
        for (final World world : plugin.getServer().getWorlds()) {
            for (final Entity entity : world.getEntities()) {
                if (isCarrier(entity)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }
}
