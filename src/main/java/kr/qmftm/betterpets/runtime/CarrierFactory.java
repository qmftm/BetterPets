package kr.qmftm.betterpets.runtime;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Rabbit;
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
 * <p><b>나는 펫과 걷는 펫은 서로 다른 엔티티를 쓴다.</b> {@code Allay} 는 비행형
 * 엔티티라 <b>AI 를 꺼도 중력과 무관하게 뜬 채로 남는다</b> — 부양은 AI(행동 목록)가
 * 아니라 엔티티 클래스 자체에 박힌 물리라서다. 걷는 펫까지 이걸 쓰면, 소유자가 비행
 * 중이거나 무언가로 공중에 잘못 순간이동됐을 때 <b>영영 허공에 떠 있는다</b> — 중력을
 * 강제로 꺼둔 것도 한몫한다(중력이 켜져 있어도 Allay 는 물리를 직접 재정의해서
 * 그대로 떠 있을 수 있다). 그래서 걷는 펫은 원래 지상 물리를 쓰는 {@code Rabbit} 으로
 * 바꾸고 중력도 켜서, 잘못된 자리에 놓여도 스스로 떨어져 바닥을 찾게 한다.
 * {@code Rabbit} 을 고른 이유는 히트박스가 작고(0.4×0.5) <b>중립(Animal)</b> 이라
 * 골렘 같은 적대 판정 몹이 반응할 일이 없어서다 — {@code Silverfish} 류는 크기는
 * 비슷해도 적대(Monster) 분류라 굳이 그 위험을 감수할 이유가 없다.
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
     *
     * @param flying 이 펫 종류가 나는 탑승({@code RideMode.FLY})인가. {@code Allay}(중력 없음)와
     *              {@code Rabbit}(중력 있음) 중 무엇을 쓸지, 중력을 켤지를 이 값으로 가른다
     */
    public Mob spawn(final Location location, final UUID petId, final boolean flying) {
        final World world = location.getWorld();
        // Allay·Rabbit 둘 다 Mob 이지만, world.spawn 의 제네릭은 Class<T> 와 Consumer<T> 가
        // 같은 T 여야 한다 — EntityType.getEntityClass() 로 하나로 합치면 T 가 Entity 로
        // 무너져서 Consumer 안의 setAI 같은 Mob 전용 메서드를 못 쓴다. 그래서 두 번 따로 건다.
        return flying
            ? world.spawn(location, Allay.class, CreatureSpawnEvent.SpawnReason.CUSTOM,
                entity -> configure(entity, petId, true))
            : world.spawn(location, Rabbit.class, CreatureSpawnEvent.SpawnReason.CUSTOM,
                entity -> configure(entity, petId, false));
    }

    private void configure(final Mob entity, final UUID petId, final boolean flying) {
        entity.setAI(false);              // 바닐라 경로탐색 차단 — 이동은 우리가 계산한다
        // 나는 펫은 우리가 y 를 전적으로 정한다 — 중력이 끼면 텔레포트와 싸운다.
        // 걷는 펫은 반대로 켜둔다 — 우리 계산이 놓친 자리(공중)에서도 스스로 떨어진다.
        entity.setGravity(!flying);
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
