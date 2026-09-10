package kr.qmftm.betterpets.integration;

import kr.qmftm.betterpets.testfixture.HiddenImplementation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code DiscordBridge.publicMethod} 만 본다 — DiscordSRV 없이 검증할 수 있는 유일한 부분이고,
 * 가장 틀리기 쉬운 부분이기도 하다.
 *
 * <p>여기서 재현하는 건 JDA 를 상대할 때 실제로 나는 실패다: 돌려받은 객체가
 * <b>다른 패키지의 package-private 클래스</b>라, 그 클래스에서 찾은 공개 메서드조차
 * 호출할 수 없다. 대역을 별도 패키지에 둔 것이 이 테스트의 핵심이다 — 같은 패키지에
 * 두면 자바가 접근을 허용해서 막으려는 실패가 재현되지 않는다.
 */
class DiscordBridgeReflectionTest {

    private static final Object HIDDEN = HiddenImplementation.create();

    @Test
    @DisplayName("구현 클래스에서 바로 찾으면 호출에 실패한다 — 이게 막으려는 실패다")
    void reflectingOnTheHiddenClassFails() throws Exception {
        // getMethod 는 성공한다. 여기서 안심하고 넘어가는 게 함정이다.
        final Method naive = HIDDEN.getClass().getMethod("sendMessage", CharSequence.class);

        // 그런데 호출이 안 된다. 클래스 자체가 이 패키지에서 보이지 않기 때문이다.
        assertThrows(IllegalAccessException.class, () -> naive.invoke(HIDDEN, "안녕"));
    }

    @Test
    @DisplayName("공개 인터페이스에서 찾으면 실제로 호출된다")
    void findsMethodThroughPublicInterface() throws Exception {
        final Method found = DiscordBridge.publicMethod(
            HIDDEN.getClass(), "sendMessage", CharSequence.class);

        assertEquals(HiddenImplementation.Channel.class, found.getDeclaringClass(),
            "구현 클래스가 아니라 공개 인터페이스에서 찾아야 한다");
        assertNotEquals(HIDDEN.getClass(), found.getDeclaringClass());
        assertEquals("보냄: 안녕", found.invoke(HIDDEN, "안녕"));
    }

    @Test
    @DisplayName("공개 클래스면 그 클래스에서 바로 찾는다 — 가장 정확한 대상이다")
    void prefersThePublicClassItself() throws Exception {
        assertEquals(StringBuilder.class,
            DiscordBridge.publicMethod(StringBuilder.class, "toString").getDeclaringClass());
    }

    @Test
    @DisplayName("없는 메서드는 조용히 넘어가지 않고 던진다")
    void missingMethodThrows() {
        assertThrows(NoSuchMethodException.class,
            () -> DiscordBridge.publicMethod(HIDDEN.getClass(), "그런건없다"));
    }

    @Test
    @DisplayName("인자 타입이 다르면 찾지 못한다 — 시그니처가 바뀐 걸 조용히 넘기지 않는다")
    void wrongArgumentTypeIsNotFound() {
        // JDA 가 sendMessage(String) 으로 바뀌는 식의 변화를 여기서 잡는다.
        assertThrows(NoSuchMethodException.class,
            () -> DiscordBridge.publicMethod(HIDDEN.getClass(), "sendMessage", Integer.class));
    }

    @Test
    @DisplayName("대상이 던진 예외는 InvocationTargetException 으로 감싸여 온다")
    void invocationWrapsTargetFailure() throws Exception {
        // DiscordBridge.send 가 ReflectiveOperationException 을 통째로 잡는 이유를 고정한다.
        final Method parse = DiscordBridge.publicMethod(Integer.class, "parseInt", String.class);
        assertThrows(InvocationTargetException.class, () -> parse.invoke(null, "숫자아님"));
    }
}
