package ru.privatenull.support.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Minimal replacement for MiniMessage on servers whose own Adventure is too old for it (Paper 1.16.5).
 * Understands the tags {@link ColorUtil} produces plus the common ones: colors by name, {@code #rrggbb},
 * decorations, {@code reset}, {@code br}. Anything else stays visible as plain text. Uses only Adventure calls
 * that exist in old versions.
 */
final class SimpleMiniMessage {

    private static final Map<String, TextDecoration> DECORATIONS = Map.ofEntries(
            Map.entry("bold", TextDecoration.BOLD), Map.entry("b", TextDecoration.BOLD),
            Map.entry("italic", TextDecoration.ITALIC), Map.entry("i", TextDecoration.ITALIC),
            Map.entry("em", TextDecoration.ITALIC),
            Map.entry("underlined", TextDecoration.UNDERLINED), Map.entry("u", TextDecoration.UNDERLINED),
            Map.entry("strikethrough", TextDecoration.STRIKETHROUGH), Map.entry("st", TextDecoration.STRIKETHROUGH),
            Map.entry("obfuscated", TextDecoration.OBFUSCATED), Map.entry("obf", TextDecoration.OBFUSCATED)
    );

    private SimpleMiniMessage() {
    }

    static Component parse(String input) {
        Component result = Component.empty();
        StringBuilder text = new StringBuilder();
        TextColor color = null;
        Set<TextDecoration> decorations = EnumSet.noneOf(TextDecoration.class);

        for (int index = 0; index < input.length();) {
            char current = input.charAt(index);
            int end = current == '<' ? input.indexOf('>', index) : -1;
            if (end < 0) {
                text.append(current);
                index++;
                continue;
            }
            String tag = input.substring(index + 1, end).toLowerCase(Locale.ROOT);
            boolean closing = tag.startsWith("/");
            String name = closing ? tag.substring(1) : tag;
            TextColor tagColor = colorOf(name);
            TextDecoration decoration = DECORATIONS.get(name);
            boolean reset = name.equals("reset") && !closing;
            boolean newline = (name.equals("br") || name.equals("newline")) && !closing;
            if (tagColor == null && decoration == null && !reset && !newline) {
                text.append(current);
                index++;
                continue;
            }
            if (newline) {
                text.append('\n');
                index = end + 1;
                continue;
            }
            result = flush(result, text, color, decorations);
            if (reset) {
                color = null;
                decorations.clear();
            } else if (tagColor != null) color = closing ? null : tagColor;
            else if (closing) decorations.remove(decoration);
            else decorations.add(decoration);
            index = end + 1;
        }
        return flush(result, text, color, decorations);
    }

    private static Component flush(Component result, StringBuilder text, TextColor color, Set<TextDecoration> decorations) {
        if (text.length() == 0) return result;
        Component part = Component.text(text.toString());
        if (color != null) part = part.color(color);
        for (TextDecoration decoration : decorations) part = part.decoration(decoration, true);
        text.setLength(0);
        return result.append(part);
    }

    private static TextColor colorOf(String name) {
        String value = name.startsWith("color:") ? name.substring(6) : name.startsWith("c:") ? name.substring(2) : name;
        if (value.startsWith("#") && value.length() == 7) {
            try {
                return TextColor.color(Integer.parseInt(value.substring(1), 16));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return NamedTextColor.NAMES.value(value);
    }
}
