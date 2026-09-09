package kr.qmftm.betterpets.runtime;

import kr.qmftm.betterpets.domain.PetType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * 펫의 추종 이동. 거리에 따라 상태를 오간다.
 *
 * <p>바닐라 경로탐색을 쓰지 않는다({@code setAI(false)}). 대신 목표 지점을 계산해
 * 보간 이동하고, 막히면 간이 지형 처리로 넘어가거나 텔레포트로 폴백한다.
 */
public final class MovementController {

    /** 이동 모드. 탑승 중에는 추종을 멈춰야 하므로 모드로 분리한다. */
    public enum Mode { GROUND, FLY, RIDDEN }

    public enum State {
        IDLE, WALK, RUN, TELEPORT
    }

    /** 목표 지점 근처에서 미세하게 떠는 것을 막는 데드존. */
    private static final double DEAD_ZONE = 0.8;

    /** 이 시간 동안 목표에 가까워지지 못하면 텔레포트한다. */
    private static final long STUCK_MILLIS = 3_000L;

    private final Mob carrier;
    private final PetType type;
    private final double speedMultiplier;

    private Mode mode = Mode.GROUND;
    private State state = State.IDLE;
    private double lastDistance = Double.MAX_VALUE;
    private long lastProgressAt = System.currentTimeMillis();

    public MovementController(final Mob carrier, final PetType type) {
        this.carrier = carrier;
        this.type = type;
        this.speedMultiplier = type.rarity().moveSpeedMultiplier();
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

    /** 탑승 중에는 추종을 하지 않는다. */
    public boolean followsOwner() {
        return mode != Mode.RIDDEN;
    }

    public void tick(final Player owner) {
        if (mode == Mode.RIDDEN) {
            return;
        }
        final Location target = followTarget(owner);
        final Location current = carrier.getLocation();

        if (!current.getWorld().equals(target.getWorld())) {
            teleportTo(target);
            return;
        }

        final double distance = current.distance(target);
        state = resolveState(distance);

        if (state == State.TELEPORT || isStuck(distance)) {
            teleportTo(target);
            return;
        }
        if (state == State.IDLE) {
            faceOwner(owner);
            return;
        }
        step(current, target, distance);
    }

    private State resolveState(final double distance) {
        if (distance >= type.movement().teleportDistance()) {
            return State.TELEPORT;
        }
        if (distance >= 8.0) {
            return State.RUN;
        }
        if (distance >= type.movement().followDistance() + DEAD_ZONE) {
            return State.WALK;
        }
        return State.IDLE;
    }

    /** 소유자 뒤쪽. 소유자가 제자리 회전할 때 펫이 따라 도는 것을 데드존이 막는다. */
    private Location followTarget(final Player owner) {
        final Location base = owner.getLocation();
        final Vector behind = base.getDirection().setY(0);
        if (behind.lengthSquared() < 1.0e-4) {
            behind.setX(0).setZ(1);
        }
        behind.normalize().multiply(-type.movement().followDistance());
        return base.clone().add(behind);
    }

    private void step(final Location current, final Location target, final double distance) {
        final double speed = (state == State.RUN
            ? type.movement().runSpeed()
            : type.movement().walkSpeed()) * speedMultiplier;

        final Vector direction = target.toVector().subtract(current.toVector());
        direction.setY(0);
        if (direction.lengthSquared() < 1.0e-6) {
            return;
        }
        direction.normalize().multiply(Math.min(speed, distance));

        final Location next = current.clone().add(direction);
        next.setY(groundLevel(next, current.getY()));
        next.setDirection(target.toVector().subtract(next.toVector()).setY(0));
        carrier.teleport(next);

        if (distance < lastDistance - 0.05) {
            lastProgressAt = System.currentTimeMillis();
        }
        lastDistance = distance;
    }

    /**
     * 간이 지형 처리. 정식 경로탐색이 아니라 "한 칸 오르내리기"만 한다.
     *
     * <p>앞이 막혔고 그 위가 비었으면 올라가고, 발밑이 비었으면 최대 3칸 내려간다.
     * 그보다 복잡한 지형은 텔레포트 폴백이 처리한다.
     */
    private double groundLevel(final Location at, final double currentY) {
        final Location probe = at.clone();
        probe.setY(currentY);

        if (isSolid(probe)) {
            final Location above = probe.clone().add(0, 1, 0);
            if (!isSolid(above)) {
                return currentY + 1.0;
            }
            return currentY;    // 두 칸 벽. 텔레포트 폴백에 맡긴다
        }
        for (int drop = 1; drop <= 3; drop++) {
            final Location below = probe.clone().subtract(0, drop, 0);
            if (isSolid(below)) {
                return currentY - drop + 1.0;
            }
        }
        return currentY;
    }

    private boolean isSolid(final Location location) {
        final Material material = location.getBlock().getType();
        return material.isSolid();
    }

    /**
     * 목표에 가까워지지 못한 채 시간이 흐르면 갇힌 것으로 본다.
     * 지형 처리가 감당 못 하는 상황에서 펫이 영영 뒤처지는 것을 막는다.
     */
    private boolean isStuck(final double distance) {
        if (distance <= type.movement().followDistance() + DEAD_ZONE) {
            lastProgressAt = System.currentTimeMillis();
            return false;
        }
        return System.currentTimeMillis() - lastProgressAt > STUCK_MILLIS;
    }

    private void faceOwner(final Player owner) {
        final Vector toOwner = owner.getLocation().toVector()
            .subtract(carrier.getLocation().toVector()).setY(0);
        if (toOwner.lengthSquared() < 1.0e-4) {
            return;
        }
        final Location facing = carrier.getLocation();
        facing.setDirection(toOwner);
        carrier.setRotation(facing.getYaw(), 0.0f);
    }

    private void teleportTo(final Location target) {
        carrier.teleport(target);
        state = State.IDLE;
        lastDistance = Double.MAX_VALUE;
        lastProgressAt = System.currentTimeMillis();
    }
}
