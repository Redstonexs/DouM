package io.github.doum.deathgates.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Loads the UTF-8 {@code .properties} message bundles shipped under {@code /messages/} inside
 * the plugin jar. Reading through an explicit UTF-8 reader keeps non-ASCII (e.g. Chinese) text
 * intact without {@code native2ascii} escaping.
 */
public final class TranslationsLoader {
    private static final String RESOURCE_PREFIX = "/messages/";
    private static final String RESOURCE_SUFFIX = ".properties";

    private TranslationsLoader() {}

    public static Translations load() {
        Map<Language, Map<String, String>> byLanguage = new EnumMap<>(Language.class);
        for (Language language : Language.values()) {
            byLanguage.put(language, loadLanguage(language));
        }
        return new Translations(byLanguage);
    }

    private static Map<String, String> loadLanguage(Language language) {
        String resource = RESOURCE_PREFIX + language.code() + RESOURCE_SUFFIX;
        try (InputStream input = TranslationsLoader.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled translations: " + resource);
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            Map<String, String> messages = new HashMap<>();
            for (String key : properties.stringPropertyNames()) {
                messages.put(key, properties.getProperty(key));
            }
            return messages;
        } catch (IOException error) {
            throw new UncheckedIOException("Could not read translations: " + resource, error);
        }
    }
}
