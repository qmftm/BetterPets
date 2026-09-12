package kr.qmftm.betterpets.runtime;

import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 탑승과 비행.
 *
 * <p><b>BetterModel 의 {@code MountedHitBox}(좌석 본)를 쓰지 않는다.</b> 같은 일을 하는
 * 배포 중인 플러그인(betterpets-paper)이 그 경로 대신 보이지 않는 {@code ArmorStand} 를
 * 직접 구동하고 있어서, 검증된 쪽을 택했다.
 *
 * <p>조향은 Paper 의 {@code Input} API 로 실제 키 입력을 읽는다. 시선 방향에 속도를 주는
 * 옛 방식보다 정확하다.
 *
 * <p><b>{@code allowFlight} 는 쓰지 않는다.</b> 그걸 부여하면 하차·로그아웃·종료·사망 모든
 * 경로에서 회수해야 하고, 하나라도 빠지면 플레이어가 영구 비행을 얻는다. 아머스탠드를
 * 직접 움직이면 그 위험 자체가 없다.
 */
public final class RideController {

    /** 경로 검사 서브스텝 간격. 이보다 크게 움직이면 얇은 벽을 관통할 수 있다. */
    private static final double SUB_STEP = 0.45;

    /** 탑승자 몸통 반경. 중심만 검사하면 어깨가 벽에 낀다. */
    private static final double BODY_RADIUS = 0.35;

    /**
     * 안전 검사에서 볼 수평 오프셋. 중심 + 네 방향.
     *
     * <p><b>메서드 안의 배열 리터럴이었다.</b> 그 자리는 매 틱, 탑승자마다, 서브스텝마다
     * 도는 곳이라 검사 한 번에 배열 여섯 개가 새로 생기고 있었다. 값이 상수인데 그럴
     * 이유가 없다.
     */
    private static final double[][] BODY_OFFSETS = {
        {0, 0}, {BODY_RADIUS, 0}, {-BODY_RADIUS, 0}, {0, BODY_RADIUS}, {0, -BODY_RADIUS}
    };

    private static final String RIDE_TAG = "BetterPets.Ride";

    /**
     * 마운트에서 잠글 슬롯. 아머스탠드가 실제로 가진 여섯 개다.
     *
     * <p><b>{@code EquipmentSlot.values()} 를 쓰지 않는다.</b> 그 enum 에는 몹용
     * {@code BODY} 와 {@code SADDLE} 도 들어 있는데, 아머스탠드에 없는 슬롯을 넘겼을 때
     * 구현이 어떻게 반응하는지 서버 없이 확인할 수 없다. 스폰 콜백 안에서 예외가 나면
     * 탑승이 통째로 실패하므로, 확실한 것만 적는다.
     */
    private static final EquipmentSlot[] ARMOR_STAND_SLOTS = {
        EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final Plugin plugin;
    private final NamespacedKey ownerKey;
    private final Map<UUID, Ride> rides = new ConcurrentHashMap<>();

    /**
     * 비행 수치는 기동 시 한 번만 읽는다.
     *
     * <p>{@code drive()} 와 {@code isSafe()} 는 <b>매 틱, 탑승자마다, 서브스텝마다</b>
     * 돈다. 거기서 {@code getConfig().getDouble()} 을 부르면 문자열 키로 맵을 뒤지는
     * 일이 초당 수백 번 일어난다. 값은 리로드 때만 바뀌므로 캐시가 맞다.
     */
    private volatile double flightLift;
    private volatile double flightMaxHeight;

    /**
     * 지형 검사용 위치 버퍼.
     *
     * <p>{@code tick} 은 메인 스레드에서 순차적으로 돌기 때문에 컨트롤러 하나에 버퍼
     * 하나면 충분하다. 이 클래스 밖으로 새어 나가지 않는다 — 검사에만 쓰고 버린다.
     * {@code MovementController} 가 같은 이유로 같은 방식을 쓴다.
     */
    private final Location probe = new Location(null, 0, 0, 0);

    public RideController(final Plugin plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "ride_owner");
        reloadTuning();
    }

    /** {@code /petadmin reload} 가 부른다. 설정을 다시 읽어 캐시를 갈아끼운다. */
    public void reloadTuning() {
        flightLift = plugin.getConfig().getDouble("ride.flight-lift", 0.5);
        flightMaxHeight = plugin.getConfig().getDouble("ride.flight-max-height", 1024.0);
    }

    /**
     * 진행 중인 탑승 하나.
     *
     * <p>플레이어가 동시에 탈 수 있는 건 어차피 한 마리라 탑승은 소유자별로 하나다.
     * 대신 <b>어느 펫에 탔는지</b>({@link #petId})를 들고 있어야 한다 — 여러 마리를
     * 소환해 둔 상태에서 틱 루프가 엉뚱한 펫을 마운트에 붙여버리지 않으려면 필요하다.
     */
    public static final class Ride {
        private final UUID petId;
        private final ArmorStand mount;
        private final boolean flying;
        private final double speed;
        private volatile Input input;

        private Ride(final UUID petId, final ArmorStand mount, final boolean flying, final double speed) {
            this.petId = petId;
            this.mount = mount;
            this.flying = flying;
            this.speed = speed;
        }

        public UUID petId() { return petId; }
        public ArmorStand mount() { return mount; }
        public boolean flying() { return flying; }
        void input(final Input value) { input = value; }
    }

    /** 이 플레이어가 그 펫에 타고 있는가. */
    public boolean isRiding(final Player player, final UUID petId) {
        final Ride ride = rides.get(player.getUniqueId());
        return ride != null && ride.petId.equals(petId);
    }

    public boolean isRiding(final Player player) {
        return rides.containsKey(player.getUniqueId());
    }

    public Ride rideOf(final Player player) {
        return rides.get(player.getUniqueId());
    }

    /**
     * 지금 타고 있는 사람들.
     *
     * <p>틱 루프가 이걸로 돈다. 소환된 펫 전부를 훑으며 "타고 있나?"를 묻는 것보다,
     * 보통 비어 있는 이 집합을 도는 편이 훨씬 싸다.
     */
    public java.util.Set<UUID> riderIds() {
        return rides.keySet();
    }

    /**
     * 탑승을 시작한다.
     *
     * @return 성공 여부. 실패 시 마운트를 남기지 않는다
     */
    public boolean start(final Player player, final UUID petId, final Location at,
                         final boolean flying, final double speed) {
        if (rides.containsKey(player.getUniqueId())) {
            return false;
        }
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        final ArmorStand mount = player.getWorld().spawn(at, ArmorStand.class, entity -> {
            entity.setVisible(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setCollidable(false);
            entity.setSmall(true);
            entity.setBasePlate(false);
            entity.setPersistent(false);
            entity.setSilent(true);
            // 보이지 않아도 아머스탠드는 아머스탠드다 — 우클릭하면 손에 든 것을 입는다.
            // 남이 지나가다 클릭해 갑옷을 잃거나, 반대로 남의 마운트에서 갑옷을 벗겨
            // 가져갈 수 있다. 슬롯을 잠가 바닐라 쪽에서 막는다.
            entity.setDisabledSlots(ARMOR_STAND_SLOTS);
            entity.addScoreboardTag(RIDE_TAG);
            entity.getPersistentDataContainer()
                .set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        });

        if (!mount.addPassenger(player)) {
            // 태우지 못했으면 유령 아머스탠드를 남기지 않는다.
            mount.remove();
            return false;
        }
        rides.put(player.getUniqueId(), new Ride(petId, mount, flying, speed));
        return true;
    }

    /** 탑승자의 키 입력을 받아둔다. 실제 이동은 틱 루프가 한다. */
    public void input(final Player player, final Input input) {
        final Ride ride = rides.get(player.getUniqueId());
        if (ride != null) {
            ride.input(input);
        }
    }

    /**
     * 하차. 어느 경로로 불려도 안전해야 한다 — 스니크, 로그아웃, 펫 디스폰, 서버 종료.
     *
     * <p><b>완강 낙하는 걸지 않는다.</b> 공중에서 내리면 그대로 떨어진다 — 요청으로 뺐다.
     * {@code setFallDistance(0)} 만 남긴다. 마운트를 타고 있던 동안 쌓인 낙하 거리를
     * 하차 시점부터 다시 세게 해서, 탑승 전에 이미 떨어지던 중이었다면 그 몫까지
     * 하차 직후 피해로 잡히는 것만 막는다.
     */
    public void stop(final Player player) {
        final Ride ride = rides.remove(player.getUniqueId());
        if (ride == null) {
            return;
        }
        if (player.isInsideVehicle() && ride.mount.equals(player.getVehicle())) {
            player.leaveVehicle();
        }
        ride.mount.remove();
        player.setFallDistance(0.0f);
    }

    /**
     * 플레이어 객체 없이 정리한다.
     *
     * <p>탑승자가 이미 나갔거나 서버가 내려가는 중이면 {@link #stop} 이 필요한 것들
     * (하차·낙하 보호)을 해 줄 대상이 없다. <b>그래도 마운트는 반드시 지워야 한다</b> —
     * 안 지우면 보이지 않는 아머스탠드가 다음 재시작까지 월드에 떠 있는다.
     */
    public void stopQuietly(final UUID riderId) {
        final Ride ride = rides.remove(riderId);
        if (ride != null) {
            ride.mount.remove();
        }
    }

    public void stopAll() {
        for (final UUID id : Map.copyOf(rides).keySet()) {
            final Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                stop(player);
            } else {
                stopQuietly(id);
            }
        }
    }

    /**
     * 매 틱 호출. 마운트를 움직이고, 상태가 어긋나면 안전하게 하차시킨다.
     *
     * @return 여전히 탑승 중이면 true
     */
    public boolean tick(final Player player) {
        final Ride ride = rides.get(player.getUniqueId());
        if (ride == null) {
            return false;
        }
        // 마운트가 죽었거나, 플레이어가 내렸거나, 월드가 갈렸으면 정리한다.
        if (ride.mount.isDead()
            || !ride.mount.getPassengers().contains(player)
            || !ride.mount.getWorld().equals(player.getWorld())) {
            stop(player);
            return false;
        }
        drive(player, ride);
        // drive 안에서 스니크 하차가 일어날 수 있다. 무조건 true 를 돌려주면
        // 호출부는 아직 타고 있는 줄 알고, 추종으로 되돌리는 쪽이 실행되지 않는다.
        return rides.containsKey(player.getUniqueId());
    }

    private void drive(final Player player, final Ride ride) {
        final Input input = ride.input != null ? ride.input : player.getCurrentInput();
        if (input == null) {
            return;
        }
        // 비행 중에는 스니크를 하차가 아니라 하강에 쓴다. 점프(상승)의 반대짝이
        // 없으면 위로만 갈 수 있고 내려올 방법이 없다 — 실기에서 이걸로 막혔다.
        // 지상 탑승은 상하 이동이 필요 없으니 스니크가 그대로 하차 단축키로 남는다.
        // 비행 중 하차는 /pet dismount 로 한다.
        if (input.isSneak() && !ride.flying) {
            stop(player);
            return;
        }

        final Location eye = player.getLocation();
        final Vector look = eye.getDirection();
        final Vector move = new Vector();

        if (input.isForward()) {
            move.add(look);
        }
        if (input.isBackward()) {
            move.subtract(look);
        }
        Vector flat = new Vector(look.getX(), 0, look.getZ());
        if (flat.lengthSquared() < 1.0e-4) {
            flat = new Vector(0, 0, 1);
        }
        flat.normalize();
        final Vector right = new Vector(-flat.getZ(), 0, flat.getX());
        if (input.isRight()) {
            move.add(right);
        }
        if (input.isLeft()) {
            move.subtract(right);
        }
        if (move.lengthSquared() > 1.0e-4) {
            move.normalize().multiply(ride.speed);
        }

        if (ride.flying) {
            // 점프=상승, 스니크=하강. 정확히 반대짝이라 둘 다 눌리면 상쇄된다.
            if (input.isJump()) {
                move.setY(move.getY() + flightLift);
            }
            if (input.isSneak()) {
                move.setY(move.getY() - flightLift);
            }
        } else {
            // 지상 탑승은 수평 이동만. 지면 높이는 아래에서 맞춘다.
            move.setY(0);
        }

        final float yaw = eye.getYaw();
        final Location base = ride.mount.getLocation();

        if (move.lengthSquared() < 1.0e-6) {
            ride.mount.setRotation(yaw, 0.0f);      // 정지 중에도 바라보는 방향은 따라간다
            return;
        }

        // 전체 이동 → 수평만 → 수직만. 한 축이 막혔다고 전부 멈추지 않게 하는 벽 슬라이딩이다.
        if (!tryMove(ride, base, move.getX(), move.getY(), move.getZ(), yaw)
            && !tryMove(ride, base, move.getX(), 0.0, move.getZ(), yaw)
            && !tryMove(ride, base, 0.0, move.getY(), 0.0, yaw)) {
            ride.mount.setRotation(yaw, 0.0f);
        }
    }

    /**
     * 목적지만이 아니라 <b>경로 전체</b>를 잘게 검사한다.
     *
     * <p>빠른 마운트가 한 틱에 여러 블록을 가면 목적지만 봐서는 중간의 벽을 못 본다.
     * all-or-nothing 이라, 하나라도 막히면 호출부가 축을 바꿔 다시 시도한다.
     */
    private boolean tryMove(final Ride ride, final Location base,
                            final double dx, final double dy, final double dz, final float yaw) {
        if (dx == 0.0 && dy == 0.0 && dz == 0.0) {
            return false;
        }
        final double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        final int steps = Math.max(1, (int) Math.ceil(distance / SUB_STEP));
        final World world = base.getWorld();
        for (int i = 1; i <= steps; i++) {
            final double fraction = (double) i / steps;
            // 좌표만 넘긴다. 서브스텝마다 Location 을 뜨면 그게 곧 틱당 쓰레기다.
            if (!isSafe(world,
                base.getX() + dx * fraction,
                base.getY() + dy * fraction,
                base.getZ() + dz * fraction, ride.flying)) {
                return false;
            }
        }
        final Location target = base.clone().add(dx, dy, dz);
        target.setYaw(yaw);
        target.setPitch(0.0f);
        ride.mount.teleport(target);
        return true;
    }

    private boolean isSafe(final World world, final double x, final double y, final double z,
                           final boolean flying) {
        if (world == null || y <= world.getMinHeight() + 1) {
            return false;
        }
        if (flying && y >= flightMaxHeight) {
            return false;
        }
        // 빌드 높이 위는 블록이 없으므로 검사를 건너뛴다.
        if (y >= world.getMaxHeight()) {
            return true;
        }
        // 중심 + 네 방향. 발치와 머리 높이 양쪽을 본다 — 중심만 보면 어깨가 낀다.
        probe.setWorld(world);
        for (final double[] offset : BODY_OFFSETS) {
            probe.setX(x + offset[0]);
            probe.setZ(z + offset[1]);
            probe.setY(y);
            if (!probe.getBlock().isPassable()) {
                return false;
            }
            probe.setY(y + 1.0);
            if (!probe.getBlock().isPassable()) {
                return false;
            }
        }
        return true;
    }

    /** 서버 재시작 후 남은 마운트를 지운다. */
    public int purgeOrphans() {
        int removed = 0;
        for (final World world : plugin.getServer().getWorlds()) {
            for (final Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(RIDE_TAG)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }
}
