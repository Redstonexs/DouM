package io.github.doum.deathgates.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.doum.deathgates.model.OperationType;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TranslationsTest {
    @Test
    void usesRequestedLanguageWhenPresent() {
        Translations translations = of(Map.of("greeting", "Hello"), Map.of("greeting", "你好"));

        assertEquals("Hello", translations.get(Language.ENGLISH, "greeting"));
        assertEquals("你好", translations.get(Language.CHINESE, "greeting"));
    }

    @Test
    void fallsBackToEnglishWhenKeyMissingForLanguage() {
        Translations translations = of(Map.of("only.en", "English only"), Map.of());

        assertEquals("English only", translations.get(Language.CHINESE, "only.en"));
    }

    @Test
    void returnsKeyWhenMissingEverywhere() {
        Translations translations = of(Map.of(), Map.of());

        assertEquals("absent.key", translations.get(Language.ENGLISH, "absent.key"));
    }

    @Test
    void formatRendersPlaceholders() {
        Translations translations = of(Map.of("set", "Set {player} to {count}."), Map.of());

        assertEquals(
                "Set Alex to 5.",
                translations.format(Language.ENGLISH, "set", Map.of("player", "Alex", "count", "5")));
    }

    @Test
    void bundledResourcesProvideDistinctEnglishAndChinese() {
        Translations translations = TranslationsLoader.load();

        String englishUsage = translations.get(Language.ENGLISH, MessageKeys.COMMAND_USAGE);
        String chineseUsage = translations.get(Language.CHINESE, MessageKeys.COMMAND_USAGE);

        assertTrue(englishUsage.contains("/doum"));
        assertTrue(chineseUsage.contains("/doum"));
        assertNotEquals(englishUsage, chineseUsage);
        assertTrue(translations.get(Language.CHINESE, MessageKeys.deny(OperationType.BLOCK_BREAK)).contains("死亡"));
    }

    private static Translations of(Map<String, String> english, Map<String, String> chinese) {
        Map<Language, Map<String, String>> byLanguage = new EnumMap<>(Language.class);
        byLanguage.put(Language.ENGLISH, english);
        byLanguage.put(Language.CHINESE, chinese);
        return new Translations(byLanguage);
    }
}
