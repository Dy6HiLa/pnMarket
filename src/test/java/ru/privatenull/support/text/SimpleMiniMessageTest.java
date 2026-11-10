package ru.privatenull.support.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimpleMiniMessageTest {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private static String legacy(Component component) {
        return LEGACY.serialize(component);
    }

    @Test
    void matchesMiniMessageOnTheTagsColorUtilProduces() {
        String[] inputs = {
                "plain text",
                "<red>red <bold>bold</bold> after",
                "<reset><#ff8800>hex <italic>it",
                "<reset><green>a<reset><blue>b",
                "<underlined>u<strikethrough>s<obfuscated>o",
                "<gold>line<br>next",
                "unknown <tag> stays",
                "1 < 2 and 3 > 2",
        };
        for (String input : inputs) {
            assertEquals(legacy(MiniMessage.miniMessage().deserialize(input)), legacy(SimpleMiniMessage.parse(input)), input);
        }
    }

    @Test
    void colorUtilStillConvertsAmpersandCodes() {
        assertEquals(legacy(SimpleMiniMessage.parse("<reset><red>x")), ColorUtil.colorize("&cx"));
    }
}
