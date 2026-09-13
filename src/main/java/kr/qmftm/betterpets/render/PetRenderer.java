package kr.qmftm.betterpets.render;

import org.bukkit.entity.Entity;

import java.util.Optional;
import java.util.Set;

/**
 * 펫 모델 렌더링의 추상 경계.
 *
 * <p><b>이 인터페이스가 존재하는 이유</b> — BetterModel 은 2.x → 3.x 에서 아티팩트 이름 자체가
 * 바뀌었고({@code bettermodel} → {@code bettermodel-bukkit-api}), 멀티플랫폼화로
 * {@code BukkitAdapter.adapt()} 경유가 추가됐다. 앞으로도 깨질 수 있다.
 *
 * <p>그래서 BetterModel API 호출은 {@link BetterModelRenderer} 한 파일 안에만 둔다.
 * 나머지 코드는 BetterModel 타입을 import 하지 않는다. 버전이 올라가면 그 파일 하나만 고치면
 * 되고, 테스트에서는 가짜 구현으로 갈아끼울 수 있다.
 */
public interface PetRenderer {

    /** 해당 이름의 모델이 로드돼 있는가. 설정 검증에 쓴다. */
    boolean modelExists(String modelId);

    /** 로드된 모델 이름 전체. 설정의 오타를 잡을 때 후보를 보여주는 용도. */
    Set<String> availableModels();

    /**
     * 캐리어 엔티티에 모델을 붙인다.
     *
     * @return 모델이 없으면 비어 있는 Optional. 호출부는 이 경우를 반드시 처리해야 한다
     */
    Optional<PetRenderHandle> attach(Entity carrier, String modelId);

    /**
     * 엔진이 실제로 들고 있는 트래커 수.
     *
     * <p>우리가 세는 활성 펫 수와 이 값이 어긋나면 그게 곧 누수다.
     * {@code /betterpets debug} 가 두 숫자를 나란히 보여준다.
     */
    int activeTrackerCount();
}
