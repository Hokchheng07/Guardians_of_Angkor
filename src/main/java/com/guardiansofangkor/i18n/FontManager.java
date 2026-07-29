package com.guardiansofangkor.i18n;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the bundled Khmer typefaces.
 *
 * <p>Dev brief Section 5.1: default Swing fonts do not render Khmer glyphs, so
 * faces have to be bundled as resources and registered explicitly with
 * {@link Font#createFont}. Swing will not silently substitute a system font for
 * missing glyphs the way a browser does — unbundled Khmer renders as tofu boxes.
 *
 * <p>There is a deliberate chain rather than a single file:
 *
 * <ol>
 *   <li><b>Suwannaphum</b> — the primary face. Traditional Khmer proportions,
 *       good at the sizes words are drawn at.</li>
 *   <li><b>Kamtumruy Pro</b> — the backup. A cleaner sans that stays legible at
 *       small sizes, used if Suwannaphum is missing or fails to parse.</li>
 *   <li><b>A registered system Khmer face</b> — whatever the machine already
 *       has, found by name.</li>
 *   <li><b>Sans-serif</b> — English play is then unaffected even though Khmer
 *       will not render.</li>
 * </ol>
 *
 * <p>Every step is optional. Missing font files are logged once, never thrown,
 * so a fresh clone runs before anyone has added the assets.
 */
public final class FontManager {

    /** Primary Khmer face. */
    private static final String SUWANNAPHUM_PATH = "/fonts/Suwannaphum-Regular.ttf";

    /** Backup Khmer face, used when the primary is unavailable. */
    private static final String KAMTUMRUY_PATH = "/fonts/KantumruyPro-Regular.ttf";

    /**
     * Alternative filenames accepted for each slot.
     *
     * <p>Google Fonts has shipped these under several names over the years —
     * "Kantumruy" versus "KamtumruyPro", with and without a weight suffix. The
     * team should not have to rename a file to make the game find it.
     */
    private static final String[] PRIMARY_CANDIDATES = {
        SUWANNAPHUM_PATH,
        "/fonts/Suwannaphum.ttf",
        "/fonts/Suwannaphum-Regular.otf",
    };

    private static final String[] BACKUP_CANDIDATES = {
        KAMTUMRUY_PATH,
        "/fonts/KantumruyPro-Regular.otf",
        "/fonts/KamtumruyPro-Regular.ttf",
        "/fonts/Kantumruy-Regular.ttf",
        "/fonts/KhmerOS.ttf",
        "/fonts/NotoSansKhmer-Regular.ttf",
    };

    /** System faces to look for if nothing is bundled. */
    private static final String[] SYSTEM_FALLBACKS = {
        "Suwannaphum", "Kantumruy Pro", "Khmer OS", "Khmer OS System",
        "Noto Sans Khmer", "Khmer MN", "Khmer Sangam MN",
    };

    private static Font khmerBase;
    private static String loadedFrom;
    private static boolean loadAttempted;

    private FontManager() {
        // Utility class — not instantiable.
    }

    /**
     * The Khmer-capable base font, or null when nothing usable was found.
     * Resolved once and cached; a failed resolution is not retried.
     */
    public static synchronized Font khmerBase() {
        if (loadAttempted) {
            return khmerBase;
        }
        loadAttempted = true;

        Font primary = loadFirstAvailable(PRIMARY_CANDIDATES);
        if (primary != null) {
            khmerBase = primary;
            return khmerBase;
        }

        Font backup = loadFirstAvailable(BACKUP_CANDIDATES);
        if (backup != null) {
            System.out.println("[FontManager] Suwannaphum not found — "
                    + "using backup face " + backup.getFontName() + ".");
            khmerBase = backup;
            return khmerBase;
        }

        Font system = findSystemKhmerFont();
        if (system != null) {
            System.out.println("[FontManager] No bundled Khmer font — "
                    + "using system face " + system.getFontName() + ".");
            khmerBase = system;
            return khmerBase;
        }

        System.out.println("[FontManager] No Khmer font available. Add "
                + SUWANNAPHUM_PATH + " (and optionally " + KAMTUMRUY_PATH
                + ") under src/main/resources to enable Khmer. "
                + "English play is unaffected.");
        return null;
    }

    /** Tries each classpath location in order, returning the first that loads. */
    private static Font loadFirstAvailable(String[] paths) {
        for (String path : paths) {
            Font font = loadResourceFont(path);
            if (font != null) {
                loadedFrom = path;
                System.out.println("[FontManager] Loaded " + font.getFontName()
                        + " from " + path);
                return font;
            }
        }
        return null;
    }

    private static Font loadResourceFont(String path) {
        try (InputStream in = FontManager.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        } catch (IOException | FontFormatException e) {
            // A corrupt or wrong-format file must not stop the chain — fall
            // through so the backup still gets its turn.
            System.err.println("[FontManager] " + path + " could not be read ("
                    + e.getMessage() + ") — trying the next candidate.");
            return null;
        }
    }

    /** Looks for a Khmer face the operating system already has registered. */
    private static Font findSystemKhmerFont() {
        try {
            List<String> installed = new ArrayList<>(List.of(
                    GraphicsEnvironment.getLocalGraphicsEnvironment()
                            .getAvailableFontFamilyNames()));

            for (String wanted : SYSTEM_FALLBACKS) {
                for (String available : installed) {
                    if (available.equalsIgnoreCase(wanted)) {
                        return new Font(available, Font.PLAIN, 12);
                    }
                }
            }
        } catch (RuntimeException e) {
            // Headless environments can refuse to enumerate fonts.
            return null;
        }
        return null;
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

    /**
     * Which resource the Khmer face came from, or null if none was bundled.
     * Useful in a startup log line when diagnosing a team member's setup.
     */
    public static synchronized String loadedFrom() {
        khmerBase();
        return loadedFrom;
    }

    /** Test seam: forces the next call to re-resolve the chain. */
    static synchronized void resetForTesting() {
        khmerBase = null;
        loadedFrom = null;
        loadAttempted = false;
    }
}
