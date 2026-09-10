package kr.qmftm.betterpets.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 동봉하는 언어 파일들이 서로 어긋나지 않는지 본다.
 *
 * <p>런타임 폴백이 있어서 키가 빠져도 <b>터지지는 않는다</b> — 그 줄만 한국어로 나온다.
 * 문제는 그게 조용하다는 것이다. 영어 서버에서 한 줄만 한국어로 뜨는 걸 아무도
 * 신고하지 않으면 영영 그대로 남는다. 여기서 잡는다.
 */
class LanguageFilesTest {

    /** MiniMessage 태그가 아니라 플러그인이 채우는 자리. {@code %이름%} */
    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-z0-9_-]+)%");

    private static YamlConfiguration load(final String code) {
        final InputStream stream = LanguageFilesTest.class.getClassLoader()
            .getResourceAsStream("lang/" + code + ".yml");
        assertNotNull(stream, "lang/" + code + ".yml 이 리소스에 없습니다");
        return YamlConfiguration.loadConfiguration(
            new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private static Set<String> stringKeys(final YamlConfiguration yaml) {
        final Set<String> keys = new TreeSet<>();
        for (final String key : yaml.getKeys(true)) {
            if (yaml.isString(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    @Test
    @DisplayName("기본 언어에는 키가 하나라도 있어야 한다")
    void fallbackIsNotEmpty() {
        assertFalse(stringKeys(load(Messages.FALLBACK_LANGUAGE)).isEmpty(),
            "내장 기본값이 비면 모든 메시지가 키 이름으로 뜬다");
    }

    @Test
    @DisplayName("en_us 와 ko_kr 의 키 집합이 같다")
    void languagesHaveTheSameKeys() {
        final Set<String> korean = stringKeys(load("ko_kr"));
        final Set<String> english = stringKeys(load("en_us"));

        final Set<String> missing = new TreeSet<>(korean);
        missing.removeAll(english);
        final Set<String> extra = new TreeSet<>(english);
        extra.removeAll(korean);

        assertTrue(missing.isEmpty(), "en_us 에 없는 키 (한국어로 폴백된다): " + missing);
        assertTrue(extra.isEmpty(), "ko_kr 에 없는 키 (기본 언어에 없으면 폴백이 없다): " + extra);
    }

    @Test
    @DisplayName("같은 키의 %자리% 가 언어마다 어긋나지 않는다")
    void placeholdersMatchAcrossLanguages() {
        final YamlConfiguration korean = load("ko_kr");
        final YamlConfiguration english = load("en_us");

        for (final String key : stringKeys(korean)) {
            if (!english.isString(key)) {
                continue;   // 위 테스트가 따로 잡는다
            }
            assertEquals(placeholders(korean.getString(key)), placeholders(english.getString(key)),
                key + " 의 치환 자리가 다릅니다. 번역에서 빠지면 그 값이 화면에 안 나옵니다");
        }
    }

    private static Set<String> placeholders(final String raw) {
        final Set<String> found = new LinkedHashSet<>();
        final Matcher matcher = PLACEHOLDER.matcher(raw == null ? "" : raw);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }
}
