package kr.qmftm.betterpets;

import kr.qmftm.betterpets.render.BetterModelRenderer;
import kr.qmftm.betterpets.render.PetRenderer;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * BetterPets 진입점.
 *
 * <p>M0 단계에서는 부트스트랩과 BetterModel 확인만 한다. 서비스·틱 루프·명령어는
 * 이후 마일스톤에서 붙는다.
 *
 * <p><b>이 클래스를 비대하게 만들지 않는다.</b> 참고 구현(betterpets-paper)은 진입점 하나가
 * 5,034줄이고 매니저가 3,792줄로, 둘이 코드의 69%를 차지한다. 여기는 배선만 하고 로직은
 * 각 계층에 둔다. 한 파일이 800줄을 넘으면 분리 신호로 본다.
 */
public final class BetterPetsPlugin extends JavaPlugin {

    private PetRenderer renderer;

    @Override
    public void onEnable() {
        // paper-plugin.yml 에서 required: true 로 걸어뒀으므로 여기 도달했다면 BetterModel 은
        // 이미 로드돼 있다. 그래도 확인하는 이유는, 로드는 됐지만 초기화에 실패한 상태로
        // 진행하면 첫 소환에서야 터지기 때문이다. 그건 진단하기 훨씬 어렵다.
        if (!getServer().getPluginManager().isPluginEnabled("BetterModel")) {
            getLogger().severe("BetterModel 이 활성화되지 않았습니다. BetterPets 를 비활성화합니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            renderer = new BetterModelRenderer();
        } catch (final LinkageError error) {
            // BetterModel 의 메이저 버전이 바뀌어 시그니처가 사라진 경우 여기로 온다.
            // 서버를 통째로 죽이는 대신 이 플러그인만 내린다.
            getLogger().severe("BetterModel API 가 예상과 다릅니다. 3.4.1 이상이 필요합니다: " + error.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("BetterPets 활성화됨. 로드된 모델 " + renderer.availableModels().size() + "개.");
    }

    @Override
    public void onDisable() {
        // 트래커 정리는 PetRegistry 가 생기는 M1 에서 여기에 붙는다.
        // 지금 닫을 것이 없다는 사실 자체를 남겨둔다 — 나중에 빠뜨리지 않기 위해서다.
        renderer = null;
    }

    /** 렌더러. 플러그인이 비활성화된 뒤에는 null 이다. */
    public PetRenderer renderer() {
        return renderer;
    }
}
