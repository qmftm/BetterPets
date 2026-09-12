package kr.qmftm.betterpets.render;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.EntityTrackerRegistry;
import kr.toxicity.model.api.tracker.TrackerModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.Set;

/**
 * BetterModel 3.4.1 구현.
 *
 * <p><b>이 프로젝트에서 {@code kr.toxicity.model.api} 를 import 하는 유일한 파일이다.</b>
 * 다른 곳에 BetterModel import 가 생기면 격리가 깨진 것이니 되돌려야 한다.
 */
public final class BetterModelRenderer implements PetRenderer {

    private static final AnimationModifier LOOP = AnimationModifier.builder()
        .type(AnimationIterator.Type.LOOP)
        .build();

    private static final AnimationModifier ONCE = AnimationModifier.DEFAULT_WITH_PLAY_ONCE;

    @Override
    public boolean modelExists(final String modelId) {
        return modelId != null && BetterModel.model(modelId).isPresent();
    }

    @Override
    public Set<String> availableModels() {
        return BetterModel.modelKeys();
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>여기서 예외가 새면 캐리어가 누수된다.</b> 호출부({@code PetService.summon})는
     * 이미 엔티티를 스폰해 둔 상태이고, 빈 결과를 받았을 때만 그걸 되돌린다. BetterModel
     * 이 던지는 예외를 그대로 통과시키면 되돌리기 코드가 실행되지 않아 보이지 않는
     * 엔티티가 월드에 남는다.
     *
     * <p>이 격리 계층 전체가 "BetterModel 이 우리 기대와 다르게 굴어도 플러그인은 버틴다"는
     * 목적이라, 실패는 여기서 흡수해 <b>모델이 없는 것과 같은 결과</b>로 돌려준다.
     */
    @Override
    public Optional<PetRenderHandle> attach(final Entity carrier, final String modelId) {
        if (carrier == null || modelId == null) {
            return Optional.empty();
        }
        try {
            return BetterModel.model(modelId)
                .map(renderer -> renderer.getOrCreate(BukkitAdapter.adapt(carrier), TrackerModifier.DEFAULT))
                .map(TrackerHandle::new);
        } catch (final RuntimeException | LinkageError error) {
            carrier.getServer().getLogger().warning(
                "[BetterPets] 모델 '" + modelId + "' 부착에 실패했습니다: " + error);
            return Optional.empty();
        }
    }

    @Override
    public int activeTrackerCount() {
        int count = 0;
        for (final EntityTrackerRegistry registry : EntityTrackerRegistry.registries()) {
            count += registry.trackers().size();
        }
        return count;
    }

    /**
     * {@link EntityTracker} 를 감싼 핸들.
     *
     * <p>모든 메서드가 {@code isClosed()} 를 먼저 확인한다. 닫힌 트래커를 건드리면
     * 예외가 나거나 조용히 유령 상태가 되므로, 방어를 한 군데로 모은다.
     */
    private static final class TrackerHandle implements PetRenderHandle {

        private final EntityTracker tracker;

        private TrackerHandle(final EntityTracker tracker) {
            this.tracker = tracker;
        }

        @Override
        public void close() {
            // 여러 번 불릴 수 있다 — 퇴장과 월드 언로드가 겹치는 식으로.
            if (!tracker.isClosed()) {
                tracker.close();
            }
        }

        @Override
        public boolean isClosed() {
            return tracker.isClosed();
        }

        @Override
        public boolean play(final String animation, final boolean loop) {
            if (tracker.isClosed() || animation == null) {
                return false;
            }
            return tracker.animate(animation, loop ? LOOP : ONCE);
        }

        @Override
        public boolean playOverlay(final String animation, final int priority, final Runnable onFinish) {
            if (tracker.isClosed() || animation == null) {
                return false;
            }
            final AnimationModifier overlay = AnimationModifier.builder()
                .type(AnimationIterator.Type.PLAY_ONCE)
                .priority(priority)
                .build();
            // 콜백은 애니메이션이 제거될 때 불린다. 여기서 이전 루프 상태를 복원한다.
            return onFinish == null
                ? tracker.animate(animation, overlay)
                : tracker.animate(animation, overlay, onFinish);
        }

        @Override
        public boolean stop(final String animation) {
            if (tracker.isClosed() || animation == null) {
                return false;
            }
            return tracker.stopAnimation(animation);
        }

        @Override
        public void hide(final Player viewer) {
            if (!tracker.isClosed() && viewer != null) {
                tracker.hide(BukkitAdapter.adapt(viewer));
            }
        }

        @Override
        public void show(final Player viewer) {
            if (!tracker.isClosed() && viewer != null) {
                tracker.show(BukkitAdapter.adapt(viewer));
            }
        }
    }
}
