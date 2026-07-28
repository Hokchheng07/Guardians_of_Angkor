package com.guardiansofangkor.i18n;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads the bundled Noto Sans Khmer font.
 *
 * <p>Dev brief Section 5.1: default Swing fonts do not render Khmer glyphs, so
 * the font has to be bundled as a resource and registered explicitly with
 * {@link Font#createFont}. The font file has not been added yet — until it is,
 * every accessor falls back to a sane sans-serif so English play is unaffected.
 * Drop {@code NotoSansKhmer-Regular.ttf} into {@code src/main/resources/fonts/}
 * and it activates with no code change.
 */
public final class FontManager {

    private static final String KHMER_FONT_PATH = "/fonts/NotoSansKhmer-Regular.ttf";

    private static Font khmerBase;
    private static boolean loadAttempted;

    private FontManager() {
        // Utility class — not instantiable.
    }

    /**
     * The Khmer-capable base font, or null when the resource is absent.
     * Loaded once and cached; a failed load is not retried.
     */
    public static synchronized Font khmerBase() {
        if (loadAttempted) {
            return khmerBase;
        }
        loadAttempted = true;

        try (InputStream in = FontManager.class.getResourceAsStream(KHMER_FONT_PATH)) {
            if (in == null) {
                System.out.println("[FontManager] " + KHMER_FONT_PATH
                        + " not found — Khmer text will not render until it is added.");
                return null;
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            khmerBase = font;
            System.out.println("[FontManager] Loaded " + font.getFontName());
        } catch (IOException | FontFormatException e) {
            System.err.println("[FontManager] Could not load Khmer font ("
                    + e.getMessage() + ") — falling back to the default sans-serif.");
        }
        return khmerBase;
    }

    /**
     * A font suitable for displaying words in {@code language} at {@code size}.
     * Always returns something usable.
     */
    public static Font wordFont(Language language, int size, int style) {
        if (language != null && language.requiresKhmerFont()) {
            Font base = khmerBase();
            if (base != null) {
                return base.deriveFont(style, (float) size);
            }
        }
        return new Font(Font.SANS_SERIF, style, size);
    }

    /** A font for HUD chrome. Uses the Khmer face too so labels stay consistent. */
    public static Font uiFont(Language language, int size, int style) {
        return wordFont(language, size, style);
    }

    /** True when Khmer glyphs can actually be drawn right now. */
    public static boolean isKhmerAvailable() {
        return khmerBase() != null;
    }
}
