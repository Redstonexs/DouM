package io.github.doum.deathgates.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class LanguageTest {
    @Test
    void chineseLocalesResolveToChinese() {
        assertEquals(Language.CHINESE, Language.fromLanguageCode("zh"));
        assertEquals(Language.CHINESE, Language.fromLanguageCode("zh_cn"));
        assertEquals(Language.CHINESE, Language.fromLanguageCode("ZH-TW"));
        assertEquals(Language.CHINESE, Language.fromLocale(Locale.CHINA));
        assertEquals(Language.CHINESE, Language.fromLocale(Locale.TRADITIONAL_CHINESE));
    }

    @Test
    void englishLocalesResolveToEnglish() {
        assertEquals(Language.ENGLISH, Language.fromLanguageCode("en"));
        assertEquals(Language.ENGLISH, Language.fromLanguageCode("en_us"));
        assertEquals(Language.ENGLISH, Language.fromLocale(Locale.US));
    }

    @Test
    void unsupportedOrMissingLocalesFallBackToEnglish() {
        assertEquals(Language.ENGLISH, Language.DEFAULT);
        assertEquals(Language.ENGLISH, Language.fromLanguageCode("fr"));
        assertEquals(Language.ENGLISH, Language.fromLanguageCode("ja_jp"));
        assertEquals(Language.ENGLISH, Language.fromLanguageCode(""));
        assertEquals(Language.ENGLISH, Language.fromLanguageCode(null));
        assertEquals(Language.ENGLISH, Language.fromLocale(null));
    }
}
