package io.github.doum.deathgates.message;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Turns DouM's localized message templates into Adventure {@link Component}s: it parses MiniMessage
 * color tags in the template, fills the {@code {placeholder}} tokens (plain text via injection-safe
 * unparsed resolvers, the item name via a component resolver), applies a base colour where the
 * template left one out, and prepends the configurable plugin prefix.
 *
 * <p>It lives in the adapter layer but depends only on Adventure (bundled with {@code paper-api}),
 * not Bukkit, so it is unit-tested without a live server. The prefix is read through a supplier so
 * that {@code /doum reload} can change it without rebuilding the renderer.
 */
public final class ChatRenderer {
    /** Text tokens DouM resolves; any other {@code {token}} is left untouched in the output. */
    private static final String[] TEXT_PLACEHOLDERS = {"player", "operation", "required", "actual", "count"};
    private static final String TARGET_PLACEHOLDER = "target";
    private static final String TAG_PREFIX = "doum_";
    private static final TextColor BASE_COLOR = NamedTextColor.GRAY;

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Supplier<String> prefixSupplier;

    public ChatRenderer(Supplier<String> prefixSupplier) {
        this.prefixSupplier = Objects.requireNonNull(prefixSupplier, "prefixSupplier");
    }

    /**
     * Renders a denial template, inserting {@code targetName} wherever {@code {target}} appears and
     * the remaining values for the other tokens. The template may carry MiniMessage colour tags.
     */
    public Component renderDeny(String template, Map<String, String> values, Component targetName) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(values, "values");

        Component body = miniMessage
                .deserialize(toTags(template), resolvers(values, targetName))
                .colorIfAbsent(BASE_COLOR);
        return prefixed(body);
    }

    /**
     * Renders already-substituted plain text (such as {@code /doum} output) with the base colour and
     * prefix. Any markup in the text is escaped, so untrusted content (e.g. an error message) can
     * never be interpreted as tags.
     */
    public Component renderInfo(String text) {
        Objects.requireNonNull(text, "text");
        Component body = miniMessage.deserialize(miniMessage.escapeTags(text)).colorIfAbsent(BASE_COLOR);
        return prefixed(body);
    }

    private Component prefixed(Component body) {
        String prefix = prefixSupplier.get();
        if (prefix == null || prefix.isEmpty()) {
            return body;
        }
        return Component.empty().append(miniMessage.deserialize(prefix)).append(body);
    }

    private static String toTags(String template) {
        String converted = template.replace("{" + TARGET_PLACEHOLDER + "}", "<" + TAG_PREFIX + TARGET_PLACEHOLDER + ">");
        for (String name : TEXT_PLACEHOLDERS) {
            converted = converted.replace("{" + name + "}", "<" + TAG_PREFIX + name + ">");
        }
        return converted;
    }

    private static TagResolver[] resolvers(Map<String, String> values, Component targetName) {
        TagResolver[] resolvers = new TagResolver[TEXT_PLACEHOLDERS.length + 1];
        resolvers[0] = Placeholder.component(
                TAG_PREFIX + TARGET_PLACEHOLDER, targetName == null ? Component.empty() : targetName);
        for (int i = 0; i < TEXT_PLACEHOLDERS.length; i++) {
            String name = TEXT_PLACEHOLDERS[i];
            resolvers[i + 1] = Placeholder.unparsed(TAG_PREFIX + name, values.getOrDefault(name, ""));
        }
        return resolvers;
    }
}
