package io.github.doum.deathgates.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class ChatRendererTest {
    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static String miniMessage(Component component) {
        return MiniMessage.miniMessage().serialize(component);
    }

    @Test
    void renderDenyFillsTextAndTargetWithPrefix() {
        ChatRenderer renderer = new ChatRenderer(() -> "[DouM] ");

        Component message = renderer.renderDeny(
                "You can't break {target} yet - need {required} (you have {actual}).",
                Map.of("required", "3", "actual", "1"),
                Component.text("Stone"));

        assertEquals("[DouM] You can't break Stone yet - need 3 (you have 1).", plain(message));
    }

    @Test
    void renderDenyKeepsMiniMessageColorTags() {
        ChatRenderer renderer = new ChatRenderer(() -> "");

        Component message = renderer.renderDeny(
                "<red>need {required}</red>", Map.of("required", "5"), Component.empty());

        assertTrue(miniMessage(message).contains("<red>"));
        assertTrue(plain(message).contains("need 5"));
    }

    @Test
    void renderDenyInsertsTranslatableTargetName() {
        ChatRenderer renderer = new ChatRenderer(() -> "");

        Component message = renderer.renderDeny(
                "break {target}", Map.of(), Component.translatable("block.minecraft.stone"));

        // The localized item name is inserted as a translatable child, resolved per-client at render.
        assertTrue(miniMessage(message).contains("block.minecraft.stone"));
    }

    @Test
    void unparsedValuesCannotInjectMarkupAndUnknownTokensRemain() {
        ChatRenderer renderer = new ChatRenderer(() -> "");

        Component message = renderer.renderDeny(
                "hi {player} {unknown}", Map.of("player", "<red>x</red>"), Component.empty());

        // A value is inserted literally, never parsed as a colour tag.
        assertTrue(plain(message).contains("<red>x</red>"));
        // Tokens DouM does not manage are left untouched.
        assertTrue(plain(message).contains("{unknown}"));
    }

    @Test
    void renderInfoEscapesMarkupAndAppliesPrefix() {
        ChatRenderer renderer = new ChatRenderer(() -> "[DouM] ");

        Component message = renderer.renderInfo("Reload failed: <bad> & oops");

        assertEquals("[DouM] Reload failed: <bad> & oops", plain(message));
    }

    @Test
    void blankPrefixOmitsPrefix() {
        ChatRenderer renderer = new ChatRenderer(() -> "");

        assertEquals("hello", plain(renderer.renderInfo("hello")));
    }
}
