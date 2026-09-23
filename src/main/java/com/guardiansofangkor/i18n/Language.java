package com.guardiansofangkor.i18n;

/**
 * Playable typing languages. Selected at game start or from settings; the word
 * bank and font both key off this.
 */
public enum Language {

    ENGLISH("en", "English", "/words/words_en.json"),
    KHMER("km", "ភាសាខ្មែរ", "/words/words_km.json");

    private final String code;
    private final String displayName;
    private final String wordListPath;

    Language(String code, String displayName, String wordListPath) {
        this.code = code;
        this.displayName = displayName;
        this.wordListPath = wordListPath;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Classpath location of this language's word list. */
    public String getWordListPath() {
        return wordListPath;
    }

    /** True when this script needs the bundled Noto Sans Khmer font to render. */
    public boolean requiresKhmerFont() {
        return this == KHMER;
    }

    /** Parses a language code, defaulting to English on anything unrecognised. */
    public static Language fromCode(String code) {
        if (code != null) {
            for (Language language : values()) {
                if (language.code.equalsIgnoreCase(code)) {
                    return language;
                }
            }
        }
        return ENGLISH;
    }
}
