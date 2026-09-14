package kr.qmftm.betterpets.integration.skript;

import ch.njol.skript.Skript;
import kr.qmftm.betterpets.runtime.PetRegistry;
import kr.qmftm.betterpets.storage.PetStore;
import org.bukkit.plugin.Plugin;

/**
 * Skript 구문({@code on pet obtain}, {@code event-pet}, {@code summoned/owned pets of ...})을
 * 등록한다.
 *
 * <p><b>호출자가 먼저 {@code isPluginEnabled("Skript")} 를 확인해야 한다.</b> 이 클래스는
 * {@code SkriptEvent}·{@code SimpleExpression} 을 상속한 클래스들을 참조하므로, 이 메서드를
 * 부르는 {@code invokestatic} 이 실행되는 순간 JVM 이 그 클래스들을 로딩·링킹하며 Skript
 * 의 클래스까지 물고 들어간다 — {@link kr.qmftm.betterpets.integration.PetPlaceholders} 와
 * 같은 함정이라 같은 자리(호출자인 {@code BetterPetsPlugin})에서 막는다. 아래 검사는
 * 그 방어선이 뚫렸을 때를 위한 이중 확인일 뿐이다.
 */
public final class BetterPetsSkript {

    private BetterPetsSkript() {
        throw new AssertionError("유틸리티 클래스");
    }

    /*
     * ⚠️ Skript.registerEvent/registerExpression 은 2.16.2 기준 "제거 예정"으로
     * 표시돼 있다. 대체 경로(SkriptAddon.syntaxRegistry() 의 새 빌더 API)가 있지만,
     * 참고한 다른 애드온(qmftm/CasterAbility, 같은 Skript 버전)도 여전히 이 API 를
     * 쓰고 있고 지금도 정상 동작한다 — 생태계 전체가 아직 옮겨가지 않은 상태다.
     * 새 API 로 옮기는 건 이 구문들이 실제로 깨졌을 때(또는 다음 대규모 점검 때)
     * 판단한다.
     */

    /** @return 등록했으면 true */
    public static boolean tryRegister(final Plugin plugin, final PetRegistry registry, final PetStore store) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("Skript")) {
            return false;
        }
        try {
            if (!Skript.isAcceptRegistrations()) {
                plugin.getLogger().warning("Skript 가 이미 구문 등록을 마감했습니다 — 로드 순서를 확인하세요"
                    + "(paper-plugin.yml 에서 Skript 를 load: BEFORE 로 두면 이 문제가 없습니다).");
                return false;
            }
            SkriptBridge.init(registry, store);
            EvtPetObtain.register();
            ExprEventPet.register();
            ExprActivePets.register();
            ExprOwnedPets.register();
            plugin.getLogger().info("Skript 연동됨. on pet obtain · event-pet ·"
                + " summoned/owned pets of ... 를 쓸 수 있습니다.");
            return true;
        } catch (final LinkageError | RuntimeException error) {
            plugin.getLogger().warning("Skript 연동에 실패했습니다: " + error);
            return false;
        }
    }
}
