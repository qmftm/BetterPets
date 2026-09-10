package kr.qmftm.betterpets.config;

import java.util.regex.Pattern;

/**
 * MiniMessage 태그를 걷어낸다.
 *
 * <p>서식을 모르는 곳 — 인벤토리 제목, 콘솔, Discord, 플레이스홀더 — 으로 문자열을
 * 넘길 때 쓴다. 태그가 그대로 가면 {@code <gold>드래곤} 처럼 보인다.
 *
 * <p><b>패턴을 미리 컴파일한다.</b> {@code String.replaceAll} 은 호출마다
 * {@link Pattern#compile} 을 다시 돈다. 네 군데에서 같은 정규식을 쓰고 있었고,
 * 그중 플레이스홀더는 스코어보드가 초당 여러 번 부르는 자리다.
 */
public final class Tags {

    private static final Pattern TAG = Pattern.compile("<[^>]*>");

    private Tags() {
        throw new AssertionError("유틸리티 클래스");
    }

    /** @return 태그를 걷어낸 문자열. {@code null} 이면 빈 문자열 */
    public static String strip(final String raw) {
        return raw == null ? "" : TAG.matcher(raw).replaceAll("");
    }
}
