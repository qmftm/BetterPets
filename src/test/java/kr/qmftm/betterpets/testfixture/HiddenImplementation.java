package kr.qmftm.betterpets.testfixture;

/**
 * JDA 같은 외부 라이브러리의 구조를 흉내 내는 테스트 대역.
 *
 * <p><b>패키지가 따로 있어야 의미가 있다.</b> 테스트와 같은 패키지에 두면 자바가
 * 접근을 허용해서, 막으려는 실패 자체가 재현되지 않는다.
 */
public final class HiddenImplementation {

    private HiddenImplementation() {
        throw new AssertionError("유틸리티 클래스");
    }

    /** 공개 인터페이스. JDA 의 {@code MessageChannel} 자리다. */
    public interface Channel {
        String sendMessage(CharSequence text);
    }

    /** 구현 클래스. <b>일부러 public 이 아니다</b> — JDA 의 내부 구현이 그렇다. */
    static final class PackagePrivateChannel implements Channel {
        @Override
        public String sendMessage(final CharSequence text) {
            return "보냄: " + text;
        }
    }

    /** 밖에서는 인터페이스로만 받는다. 구현 클래스 이름조차 알 수 없다. */
    public static Channel create() {
        return new PackagePrivateChannel();
    }
}
