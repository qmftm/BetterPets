package kr.qmftm.betterpets.integration.skript;

import kr.qmftm.betterpets.runtime.PetRegistry;
import kr.qmftm.betterpets.storage.PetStore;

/**
 * Skript 구문(Expression·SkriptEvent)이 참조할 서비스를 들고 있는다.
 *
 * <p>Skript 는 {@code Expression}·{@code SkriptEvent} 구현체를 <b>기본 생성자로 직접
 * 만든다</b> — 우리가 생성자에 의존성을 주입할 방법이 없다. 그래서 이 계층에서만
 * 정적 보관소를 쓴다. 플러그인의 나머지 부분은 전부 생성자 주입을 지킨다 — Skript
 * 라는 외부 프레임워크의 제약이지, 우리 설계 원칙이 바뀐 게 아니다.
 */
final class SkriptBridge {

    private static volatile PetRegistry registry;
    private static volatile PetStore store;

    private SkriptBridge() {
        throw new AssertionError("유틸리티 클래스");
    }

    static void init(final PetRegistry registryValue, final PetStore storeValue) {
        registry = registryValue;
        store = storeValue;
    }

    static PetRegistry registry() {
        return registry;
    }

    static PetStore store() {
        return store;
    }
}
