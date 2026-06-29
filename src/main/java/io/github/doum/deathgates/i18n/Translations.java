package io.github.doum.deathgates.i18n;

import io.github.doum.deathgates.message.MessageFormatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable message catalogue keyed by {@link Language}. A key missing for the requested
 * language falls back to {@link Language#DEFAULT}; a key missing everywhere renders as the
 * key itself so a packaging gap never produces a null message.
 */
public final class Translations {
    private final Map<Language, Map<String, String>> messagesByLanguage;

    public Translations(Map<Language, Map<String, String>> messagesByLanguage) {
        Objects.requireNonNull(messagesByLanguage, "messagesByLanguage");
        Map<Language, Map<String, String>> copy = new EnumMap<>(Language.class);
        for (Map.Entry<Language, Map<String, String>> entry : messagesByLanguage.entrySet()) {
            copy.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        this.messagesByLanguage = copy;
    }

    /** Returns the raw template for the key, falling back to English then to the key. */
    public String get(Language language, String key) {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(key, "key");

        String value = lookup(language, key);
        if (value != null) {
            return value;
        }
        String fallback = lookup(Language.DEFAULT, key);
        return fallback != null ? fallback : key;
    }

    /** Returns the template for the key with placeholders rendered from {@code values}. */
    public String format(Language language, String key, Map<String, String> values) {
        return MessageFormatter.format(get(language, key), values);
    }

    private String lookup(Language language, String key) {
        Map<String, String> messages = messagesByLanguage.get(language);
        return messages == null ? null : messages.get(key);
    }
}
