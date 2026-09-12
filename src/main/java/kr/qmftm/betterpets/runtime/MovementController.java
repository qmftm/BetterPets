package kr.qmftm.betterpets.runtime;

import kr.qmftm.betterpets.domain.PetType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * 펫의 추종 이동. 거리에 따라 상태를 오간다.
 *
 * <p>바닐라 경로탐색을 쓰지 않는다({@code setAI(false)}). 대신 목표 지점을 계산해
 * 보간 이동하고, 막히면 간이 지형 처리로 넘어가거나 텔레포트로 폴백한다.
 *
 * <p><b>여기는 초당 5번, 소환된 펫마다 도는 자리다.</b> 두 가지를 아낀다:
 * <ul>
 *   <li><b>패킷</b> — {@code teleport}/{@code setRotation} 은 시청자 수만큼 패킷이 된다.
 *       원작이 렉으로 무너진 지점이 여기라, <b>바뀐 게 없으면 부르지 않는다</b>
 *   <li><b>블록 검사</b> — 지형 확인은 한 걸음에 최대 다섯 번인데, 예전엔 검사마다
 *       {@code clone()} 이 하나씩 났다. 버퍼({@link #probe})를 y 만 바꿔가며 돌려 쓴다
 * </ul>
 *
 * <p><b>할당을 전부 없애지는 않았다.</b> {@link #followTarget} 과 {@link #step} 은
 * 여전히 틱마다 {@code Location}·{@code Vector} 를 몇 개 만든다. 없애려면 목표 지점
 * 계산을 yaw 삼각함수와 버퍼로 직접 다시 쓰고 부채꼴 각도까지 손으로 돌려야 하는데,
 * <b>서버 없이는 추종이 여전히 자연스러운지 확인할 방법이 없다.</b> 5명 규모에서
 * 아끼는 양(초당 수백 개의 짧은 수명 객체)보다 "펫이 이상하게 걷는다"가 훨씬 비싸다.
 * 실측할 수 있게 되면 그때 판단한다 — ROADMAP 의 확인 목록에 있다.
 */
public final class MovementController {

    /**
     * 이동 모드. 탑승 중에는 추종을 멈춰야 하므로 모드로 분리한다.
     *
     * <p>탑승을 둘로 나눈 이유는 <b>애니메이션</b> 하나다. MODELING 은 비행 펫에게
     * {@code fly} 애니메이션을 만들라고 요구하는데, 정작 코드에는 그걸 거는 자리가
     * 없어서 하늘을 나는 드래곤이 {@code ride} 를 재생하고 있었다 — 모델 제작자에게
     * 만들라고 해놓고 쓰지 않는 셈이었다.
     */
    public enum Mode {
        /** 주인을 따라다닌다. */
        GROUND,
        /** 지상 탑승 중. */
        RIDDEN,
        /** 비행 탑승 중. */
        RIDDEN_FLYING;

        /** 누가 타고 있는 상태인가. 추종을 멈춰야 하는지 판단한다. */
        public boolean ridden() {
            return this != GROUND;
        }
    }

    public enum State {
        IDLE, WALK, RUN, TELEPORT
    }

    /** 목표 지점 근처에서 미세하게 떠는 것을 막는 데드존. */
    private static final double DEAD_ZONE = 0.8;

    /**
     * 경로 검사 서브스텝 간격. {@code RideController.SUB_STEP} 과 같은 기법이다 —
     * 목적지만 보면 한 틱에 여러 블록을 가는 설정(빠른 run-speed)에서 중간의 얇은 벽을
     * 못 보고 통과해버릴 수 있다.
     */
    private static final double SUB_STEP = 0.4;

    /** 이 시간 동안 목표에 가까워지지 못하면 텔레포트한다. */
    private static final long STUCK_MILLIS = 3_000L;

    /** 이보다 작은 회전은 패킷을 보낼 값어치가 없다. 눈으로 구분되지 않는다. */
    private static final float YAW_EPSILON = 2.0f;

    private final Mob carrier;
    private final PetType.MovementProfile profile;
    private final double walkStep;
    private final double runStep;

    /**
     * 틱마다 다시 쓰는 위치 버퍼.
     *
     * <p>한 펫의 tick 은 항상 메인 스레드에서 순차적으로 돈다. 그래서 인스턴스마다
     * 하나씩 들고 돌려 써도 안전하고, 이 클래스 밖으로는 절대 새어 나가지 않는다 —
     * 값을 넘길 때는 반드시 복사한다.
     */
    private final Location here = new Location(null, 0, 0, 0);
    private final Location probe = new Location(null, 0, 0, 0);

    private Mode mode = Mode.GROUND;
    private State state = State.IDLE;

    /**
     * 이 펫이 주인 뒤 어느 방향에 설지. 0이 정중앙 뒤다.
     *
     * <p>{@code max-active} 가 2 이상이면 여러 마리가 <b>같은 한 점</b>을 목표로 삼는다.
     * 그러면 서로 겹쳐 떨거나 밀어내는 것처럼 보인다. 슬롯마다 각도를 달리 줘서
     * 부채꼴로 펼친다.
     */
    private int followSlot;
    private double lastDistance = Double.MAX_VALUE;
    private long lastProgressAt = System.currentTimeMillis();
    private float lastYaw = Float.NaN;

    public MovementController(final Mob carrier, final PetType type) {
        this.carrier = carrier;
        this.profile = type.movement();
        final double multiplier = type.stats().moveSpeedMultiplier();
        this.walkStep = profile.walkSpeed() * multiplier;
        this.runStep = profile.runSpeed() * multiplier;
    }

    public Mode mode() {
        return mode;
    }

    public void mode(final Mode value) {
        mode = value;
    }

    public State state() {
        return state;
    }

    /** 소환 순서상 몇 번째인가. {@code PetService} 가 소환·해제 때마다 다시 매긴다. */
    public void followSlot(final int value) {
        followSlot = Math.max(0, value);
    }

    public void tick(final Player owner) {
        if (mode.ridden()) {
            return;     // 타고 있는 동안은 RideController 가 위치를 정한다
        }
        final Location target = followTarget(owner);
        final Location current = carrier.getLocation(here);

        if (!current.getWorld().equals(target.getWorld())) {
            teleportTo(target);
            return;
        }

        // "움직여야 하는가"는 주인과의 실제 거리로 정한다. 걸어갈 지점(target)은
        // 주인이 바라보는 방향에 따라 등 뒤로 도는데, 그걸 기준으로 삼으면 주인이
        // follow-distance 안에 가만히 서 있어도 고개만 돌리면 목표가 휙 튀어서
        // 펫이 자리를 다시 잡으러 걸어간다 — "가까운데도 움직인다"는 그 증상이었다.
        // 실제로 걸어갈 지점은 여전히 target(부채꼴 자리)이다. 안 그러면 여러 마리가
        // 전부 주인 몸 위로 겹친다.
        final double ownerDistance = current.distance(owner.getLocation());
        final double targetDistance = current.distance(target);
        state = resolveState(ownerDistance);

        if (state == State.TELEPORT || isStuck(targetDistance)) {
            teleportTo(target);
            return;
        }
        if (state == State.IDLE) {
            faceOwner(owner);
            return;
        }
        step(current, target, targetDistance);
    }

    private State resolveState(final double distance) {
        if (distance >= profile.teleportDistance()) {
            return State.TELEPORT;
        }
        if (distance >= 8.0) {
            return State.RUN;
        }
        if (distance >= profile.followDistance() + DEAD_ZONE) {
            return State.WALK;
        }
        return State.IDLE;
    }

    /**
     * 소유자 뒤쪽 — 실제로 걸어갈 지점. "움직여야 하는가" 판정에는 안 쓰인다({@link #tick}
     * 참고) — 이 지점은 소유자가 바라보는 방향에 따라 계속 돌기 때문에, 상태 판정까지
     * 여기 기준으로 하면 제자리 회전만으로도 펫이 걸어 다니는 것처럼 보인다.
     *
     * <p>여러 마리를 데리고 다니면 슬롯마다 각도를 벌려 부채꼴로 세운다.
     */
    private Location followTarget(final Player owner) {
        final Location base = owner.getLocation();
        final Vector behind = base.getDirection().setY(0);
        if (behind.lengthSquared() < 1.0e-4) {
            behind.setX(0).setZ(1);
        }
        behind.normalize().multiply(-profile.followDistance());
        Vectors.rotateAroundY(behind, slotAngleRadians());
        return base.clone().add(behind);
    }

    /**
     * 슬롯 → 각도. 0은 정중앙, 그다음부터 좌우로 번갈아 벌린다.
     *
     * <p>{@code 0° · +35° · -35° · +70° · -70° …} 순이다. 전체 마릿수를 몰라도 되도록
     * 고정 간격을 쓴다 — 소환·해제로 마릿수가 계속 바뀌는데 그때마다 전부 다시
     * 배치하면 펫들이 우르르 움직여서 더 어수선해진다.
     */
    private double slotAngleRadians() {
        if (followSlot == 0) {
            return 0.0;
        }
        final int step = (followSlot + 1) / 2;               // 1,1,2,2,3,3...
        final int sign = followSlot % 2 == 1 ? 1 : -1;       // 오른쪽부터 번갈아
        return Math.toRadians(Math.min(150.0, step * 35.0)) * sign;
    }


    private void step(final Location current, final Location target, final double distance) {
        final double speed = state == State.RUN ? runStep : walkStep;

        final Vector direction = target.toVector().subtract(current.toVector());
        direction.setY(0);
        if (direction.lengthSquared() < 1.0e-6) {
            return;
        }
        direction.normalize().multiply(Math.min(speed, distance));

        final Location next = advance(current, direction);
        if (next == null) {
            return;     // 한 발짝도 못 뗀다 — 벽이거나 디딜 곳이 없다. isStuck 이 결국 텔레포트로 꺼낸다
        }
        next.setDirection(target.toVector().subtract(next.toVector()).setY(0));
        carrier.teleport(next);
        lastYaw = next.getYaw();    // 회전 캐시를 실제로 보낸 값에 맞춘다

        if (distance < lastDistance - 0.05) {
            lastProgressAt = System.currentTimeMillis();
        }
        lastDistance = distance;
    }

    /**
     * 목적지까지 {@link #SUB_STEP} 간격으로 잘게 나눠 검사하며 전진한다.
     * {@code RideController.tryMove} 와 같은 기법이다.
     *
     * <p><b>벽을 뚫고 오던 문제.</b> 예전에는 목적지 한 칸만 보고 그리로 바로
     * 텔레포트했다 — {@link #groundLevel} 이 "여기는 못 지나간다"는 뜻으로 y 를
     * 그대로 돌려줘도, 호출부가 그걸 실패로 안 보고 x·z 는 그대로 이동시켜서 벽을
     * 뚫고 전진했다. 여기서는 막힌 지점 <b>직전까지만</b> 인정한다.
     *
     * <p><b>공중에 뜨던 문제.</b> 같은 이유다 — 절벽 너머로 디딜 곳을 못 찾아도 y 가
     * 안 바뀐 채 x·z 만 옮겨가서 허공에 뜬 채로 미끄러지는 것처럼 보였다. 이제는
     * 디딜 곳을 못 찾은 지점도 똑같이 "막힘"으로 처리해 그 앞에서 멈춘다.
     *
     * @return 실제로 도달한 위치. 첫 서브스텝부터 막혀 있으면 {@code null}(제자리)
     */
    private Location advance(final Location current, final Vector direction) {
        final World world = current.getWorld();
        final int steps = Math.max(1, (int) Math.ceil(direction.length() / SUB_STEP));

        double x = current.getX();
        double y = current.getY();
        double z = current.getZ();
        boolean moved = false;

        for (int i = 1; i <= steps; i++) {
            final double fraction = (double) i / steps;
            final double nx = current.getX() + direction.getX() * fraction;
            final double nz = current.getZ() + direction.getZ() * fraction;
            final double ny = groundLevel(world, nx, nz, y);

            if (Double.isNaN(ny) || !fits(world, nx, ny, nz)) {
                break;      // 여기부터 막혔다. 직전까지만 인정한다
            }
            x = nx;
            y = ny;
            z = nz;
            moved = true;
        }
        return moved ? new Location(world, x, y, z) : null;
    }

    /**
     * 간이 지형 처리. 정식 경로탐색이 아니라 "한 칸 오르내리기"만 한다.
     *
     * <p>앞이 막혔고 그 위가 비었으면 올라가고, 발밑이 비었으면 최대 3칸 내려간다.
     * 그보다 복잡한 지형(2칸 벽, 3칸보다 깊은 낙차)은 {@link Double#NaN} 을 돌려준다 —
     * <b>디딜 곳이 없다는 뜻이라 호출부가 이 지점을 이동 후보에서 뺀다.</b> 예전에는
     * 여기서 {@code currentY} 를 그대로 돌려줬는데, 그게 "실패"라는 신호를 아무도
     * 못 알아듣고 그대로 이동을 진행해 벽을 뚫거나 허공에 뜨는 원인이었다.
     */
    private double groundLevel(final World world, final double x, final double z, final double currentY) {
        // 버퍼 하나를 y 만 바꿔가며 재사용한다. 예전엔 검사마다 clone() 이 하나씩 났다.
        probe.setWorld(world);
        probe.setX(x);
        probe.setZ(z);

        if (isSolidAt(currentY)) {
            return isSolidAt(currentY + 1.0)
                ? Double.NaN            // 두 칸 벽. 못 지나간다
                : currentY + 1.0;
        }
        for (int drop = 1; drop <= 3; drop++) {
            if (isSolidAt(currentY - drop)) {
                return currentY - drop + 1.0;
            }
        }
        return Double.NaN;              // 3칸 안에 디딜 곳이 없다. 절벽이나 구멍이다
    }

    /** {@link #probe} 의 x·z 를 그대로 두고 높이만 바꿔 확인한다. */
    private boolean isSolidAt(final double y) {
        probe.setY(y);
        final Material material = probe.getBlock().getType();
        return material.isSolid();
    }

    /**
     * 이 지점에 몸이 들어갈 수 있는가 — 발치와 머리 높이 둘 다 본다.
     *
     * <p>{@code RideController.isSafe} 와 같은 목적이지만, 캐리어가 {@code Allay}
     * (0.35×0.6)라 몸통이 작아 중심 한 점만 봐도 충분하다 — 플레이어를 태우는
     * {@code ArmorStand} 처럼 어깨가 넓어 네 방향을 더 볼 필요는 없다.
     */
    private boolean fits(final World world, final double x, final double y, final double z) {
        probe.setWorld(world);
        probe.setX(x);
        probe.setZ(z);
        probe.setY(y);
        if (!probe.getBlock().isPassable()) {
            return false;
        }
        probe.setY(y + 1.0);
        return probe.getBlock().isPassable();
    }

    /**
     * 목표에 가까워지지 못한 채 시간이 흐르면 갇힌 것으로 본다.
     * 지형 처리가 감당 못 하는 상황에서 펫이 영영 뒤처지는 것을 막는다.
     */
    private boolean isStuck(final double distance) {
        if (distance <= profile.followDistance() + DEAD_ZONE) {
            lastProgressAt = System.currentTimeMillis();
            return false;
        }
        return System.currentTimeMillis() - lastProgressAt > STUCK_MILLIS;
    }

    /**
     * 소유자를 바라본다.
     *
     * <p>가만히 서 있는 펫이 여기로 온다 — 가장 자주 도는 경로다. 그래서 <b>회전이
     * 눈에 띄게 달라졌을 때만</b> 패킷을 보낸다. 매 틱 같은 각도를 다시 보내면
     * 소환된 펫 수 × 시청자 수만큼 의미 없는 트래픽이 된다.
     */
    private void faceOwner(final Player owner) {
        final Location ownerAt = owner.getLocation();
        final Location current = carrier.getLocation(here);
        final double dx = ownerAt.getX() - current.getX();
        final double dz = ownerAt.getZ() - current.getZ();
        if (dx * dx + dz * dz < 1.0e-4) {
            return;
        }
        final float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        if (!Float.isNaN(lastYaw) && Math.abs(angleDelta(yaw, lastYaw)) < YAW_EPSILON) {
            return;     // 사실상 같은 방향이다. 패킷을 아낀다
        }
        carrier.setRotation(yaw, 0.0f);
        lastYaw = yaw;
    }

    /** -180..180 으로 접은 각도 차. 359도와 1도가 358도 차이로 잡히지 않게 한다. */
    private static float angleDelta(final float a, final float b) {
        float delta = (a - b) % 360.0f;
        if (delta > 180.0f) {
            delta -= 360.0f;
        } else if (delta < -180.0f) {
            delta += 360.0f;
        }
        return delta;
    }

    private void teleportTo(final Location target) {
        carrier.teleport(target);
        state = State.IDLE;
        lastDistance = Double.MAX_VALUE;
        lastProgressAt = System.currentTimeMillis();
        lastYaw = Float.NaN;    // 위치가 튀었다. 다음 회전은 무조건 보낸다
    }
}
