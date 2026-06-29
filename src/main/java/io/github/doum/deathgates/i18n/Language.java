package io.github.doum.deathgates.i18n;

import java.util.Locale;

/**
 * Languages DouM can render player-facing messages in. The client locale selects the
 * language; any unsupported locale falls back to {@link #ENGLISH}.
 */
public enum Language {
    ENGLISH("en"),
    CHINESE("zh");

    /** Language used when the client locale is unknown or unsupported. */
    public static final Language DEFAULT = ENGLISH;

    private final String code;

    Language(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /**
     * Resolves a language from a locale or language string such as {@code "en"},
     * {@code "en_us"}, {@code "zh"}, or {@code "zh_cn"}. Unknown or null values resolve
     * to {@link #DEFAULT}.
     */
    public static Language fromLanguageCode(String localeOrLanguage) {
        if (localeOrLanguage == null) {
            return DEFAULT;
        }
        String normalized = localeOrLanguage.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (Language language : values()) {
            if (normalized.equals(language.code) || normalized.startsWith(language.code + "_")) {
                return language;
            }
        }
        return DEFAULT;
    }

    /** Resolves a language from a {@link Locale}; null resolves to {@link #DEFAULT}. */
    public static Language fromLocale(Locale locale) {
        return locale == null ? DEFAULT : fromLanguageCode(locale.getLanguage());
    }
}
