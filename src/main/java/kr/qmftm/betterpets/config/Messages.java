package kr.qmftm.betterpets.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 사용자에게 보이는 문자열을 한곳에 모은다.
 *
 * <p>레거시 색코드(&amp;) 대신 MiniMessage 를 쓴다. Paper 26.2 는 Adventure 5.x 기반이라
 * 이쪽이 기본이다.
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Map<String, String> values = new LinkedHashMap<>();
    private String prefix = "<gray>[<gold>펫<gray>] </gray>";

    public void load(final File file) {
        values.clear();
        if (!file.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
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
        String raw = values.getOrDefault(key, key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            raw = raw.replace("%" + placeholders[i] + "%", placeholders[i + 1]);
        }
        return MINI.deserialize(prefix + raw);
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
