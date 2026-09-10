package kr.qmftm.betterpets.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 사용자에게 보이는 문자열을 한곳에 모은다.
 *
 * <p>레거시 색코드(&amp;) 대신 MiniMessage 를 쓴다. Paper 26.2 는 Adventure 5.x 기반이라
 * 이쪽이 기본이다.
 *
 * <p><b>두 겹으로 읽는다.</b> 먼저 jar 안의 {@value #FALLBACK_LANGUAGE} 를 통째로 깔고,
 * 그 위에 선택된 언어 파일을 덮는다. 이 순서 덕분에 <b>번역이 빠진 키가 화면에 키 이름
 * 그대로 뜨지 않는다</b> — 플러그인이 올라가 문구가 늘어도 번역 파일은 그대로 두면 된다.
 * 번역자는 바꾸고 싶은 줄만 남겨도 되고, 파일 전체를 유지할 필요가 없다.
 */
public final class Messages {

    /** 최후의 기본값. jar 안에 항상 들어 있고, 모든 키가 채워져 있다. */
    public static final String FALLBACK_LANGUAGE = "ko_kr";

    /** 플러그인이 채우는 자리. {@code %이름%} */
    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z0-9_-]+)%");

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Map<String, String> values = new LinkedHashMap<>();
    private String prefix = "<gray>[<gold>펫<gray>] </gray>";

    /**
     * 언어 파일을 읽는다.
     *
     * @param language {@code config.yml} 의 {@code language}. {@code plugins/BetterPets/lang/}
     *                 에서 같은 이름의 {@code .yml} 을 찾는다. 없으면 jar 안을 보고,
     *                 그것도 없으면 기본 언어만으로 돈다 (경고를 남긴다)
     */
    public void load(final Plugin plugin, final File dataFolder, final String language) {
        values.clear();

        // 1) 내장 기본값. 여기서 실패하면 남은 게 없으므로 조용히 넘어가지 않는다.
        if (!overlayResource(plugin, FALLBACK_LANGUAGE)) {
            plugin.getLogger().severe("내장 언어 파일 lang/" + FALLBACK_LANGUAGE
                + ".yml 을 읽지 못했습니다. 메시지가 키 이름으로 보일 수 있습니다.");
        }

        final String code = normalizeLanguage(language);

        // 2) 디스크의 언어 파일이 가장 우선이다 — 관리자가 고친 문구가 거기 있다.
        if (overlayFile(new File(dataFolder, "lang/" + code + ".yml"))) {
            return;
        }
        if (code.equals(FALLBACK_LANGUAGE)) {
            return;     // 파일이 없어도 1) 에서 내장 기본값을 이미 깔았다
        }

        // 3) 디스크에 없으면 jar 안에 동봉된 번역을 쓴다.
        if (!overlayResource(plugin, code)) {
            plugin.getLogger().warning("language: " + code + " 에 해당하는 lang/" + code
                + ".yml 이 없습니다. " + FALLBACK_LANGUAGE + " 로 표시합니다.");
        }
    }

    /**
     * 파일명으로 쓸 수 있는 형태로 다듬는다. {@code ko-KR}, {@code KO_KR} 모두 받는다.
     *
     * <p><b>이 값은 파일 경로가 된다.</b> 설정 파일이라고 해서 그대로 믿지 않는다 —
     * {@code ../../} 같은 값이 들어오면 데이터 폴더 밖을 읽게 된다. 허용 문자를
     * {@code [a-z0-9_]} 로 좁히고, 벗어나면 조용히 기본 언어로 접는다.
     */
    static String normalizeLanguage(final String language) {
        if (language == null || language.isBlank()) {
            return FALLBACK_LANGUAGE;
        }
        final String trimmed = language.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        // 경로 조작을 막는다. 설정 파일이라도 파일 경로를 그대로 믿지 않는다.
        return trimmed.matches("[a-z0-9_]{1,32}") ? trimmed : FALLBACK_LANGUAGE;
    }

    private boolean overlayFile(final File file) {
        if (!file.isFile()) {
            return false;
        }
        overlay(YamlConfiguration.loadConfiguration(file));
        return true;
    }

    private boolean overlayResource(final Plugin plugin, final String code) {
        try (InputStream stream = plugin.getResource("lang/" + code + ".yml")) {
            if (stream == null) {
                return false;
            }
            overlay(YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8)));
            return true;
        } catch (final java.io.IOException error) {
            return false;
        }
    }

    private void overlay(final YamlConfiguration yaml) {
        prefix = yaml.getString("prefix", prefix);
        for (final String key : yaml.getKeys(true)) {
            if (yaml.isString(key)) {
                values.put(key, yaml.getString(key));
            }
        }
    }

    /**
     * 메시지를 조립한다.
     *
     * @param placeholders 키·값 쌍. {@code send(p, "pet.summoned", "name", "드래곤")} 형태
     */
    public Component get(final String key, final String... placeholders) {
        return MINI.deserialize(prefix + raw(key, placeholders));
    }

    /**
     * 접두사 없이 조립한다. 방송처럼 자기 서식을 통째로 들고 있는 문구에 쓴다.
     */
    public Component bare(final String key, final String... placeholders) {
        return MINI.deserialize(raw(key, placeholders));
    }

    /**
     * 치환까지 끝낸 원문. Discord 처럼 MiniMessage 를 모르는 곳에 넘길 때 쓴다.
     *
     * <p><b>한 번에 훑으며 바꾼다.</b> {@code replace} 를 자리마다 반복하면 앞서 넣은
     * 값 안의 {@code %...%} 가 뒤 차례에 다시 치환된다. 값 중에는 <b>플레이어가 정한
     * 것</b>도 있다 — 펫 이름을 {@code %rarity%} 로 지어두면 그 자리에 등급이 박힌다.
     * 지금은 흉내 내기에 그치지만, 이런 건 나중에 더 나쁜 형태로 돌아온다.
     *
     * <p>모르는 자리는 손대지 않고 원문 그대로 남긴다. 그래야 번역에서 이름을 잘못
     * 적었을 때 빈칸이 아니라 {@code %틀린이름%} 이 보여서 눈에 띈다.
     */
    public String raw(final String key, final String... placeholders) {
        final String template = values.getOrDefault(key, key);
        if (placeholders.length < 2 || template.indexOf('%') < 0) {
            return template;
        }
        final Matcher matcher = PLACEHOLDER.matcher(template);
        final StringBuilder out = new StringBuilder(template.length() + 16);
        while (matcher.find()) {
            final String replacement = lookup(matcher.group(1), placeholders);
            matcher.appendReplacement(out,
                Matcher.quoteReplacement(replacement == null ? matcher.group() : replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** 자리 이름에 해당하는 값. 없으면 null — 호출부가 원문을 그대로 남긴다. */
    private static String lookup(final String name, final String[] placeholders) {
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            if (name.equals(placeholders[i])) {
                return placeholders[i + 1];
            }
        }
        return null;
    }

    public void send(final CommandSender target, final String key, final String... placeholders) {
        target.sendMessage(get(key, placeholders));
    }

    /** 접두사 없이. GUI 아이템 이름처럼 접두사가 방해되는 곳에 쓴다. */
    public static Component plain(final String raw) {
        return MINI.deserialize(raw).decorationIfAbsent(
            net.kyori.adventure.text.format.TextDecoration.ITALIC,
            net.kyori.adventure.text.format.TextDecoration.State.FALSE);
    }
}
