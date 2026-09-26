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

    /**
     * The wordmark's inscription face — the design reference calls for Cinzel
     * Decorative, with Cinzel (its non-decorative sibling) as the fallback
     * Google itself suggests when Decorative is unavailable.
     *
     * <p>Not committed for the same reason the Khmer faces are not: it is a
     * Google Fonts file, and this repository does not vendor font binaries.
     * Missing it is not an error — the menu falls back to the plain serif it
     * always used, exactly as Khmer falls back to sans-serif tofu-free English.
     */
    private static final String[] DISPLAY_CANDIDATES = {
        "/fonts/CinzelDecorative-Black.ttf",
        "/fonts/CinzelDecorative-Bold.ttf",
        "/fonts/CinzelDecorative-Regular.ttf",
        "/fonts/Cinzel-Black.ttf",
        "/fonts/Cinzel-Bold.ttf",
        "/fonts/Cinzel-Regular.ttf",
    };

    /** System faces to look for if nothing is bundled. */
    private static final String[] DISPLAY_SYSTEM_FALLBACKS = {
        "Cinzel Decorative", "Cinzel",
    };

    /**
     * The body face — EB Garamond, used for the italic captions, subtitles and
     * footnotes the design leans on to separate prose from the tracked caps of
     * the UI face.
     *
     * <p>Optional exactly like the others. Without it those lines fall back to
     * an italic serif, which is the same shape of thing and reads correctly,
     * just less finely.
     */
    private static final String[] BODY_CANDIDATES = {
        "/fonts/EBGaramond-Regular.ttf",
        "/fonts/EBGaramond-Italic.ttf",
        "/fonts/EBGaramond-VariableFont_wght.ttf",
    };

    private static final String[] BODY_SYSTEM_FALLBACKS = {
        "EB Garamond", "Garamond", "Adobe Garamond Pro",
    };

    private static Font khmerBase;
    private static String loadedFrom;
    private static boolean loadAttempted;

    private static Font displayBase;
    private static String displayLoadedFrom;
    private static boolean displayLoadAttempted;

    /**
     * The UI face — plain Cinzel, for tracked caps on buttons and stat labels.
     *
     * <p>Separate from the display chain even though both start at Cinzel,
     * because the design uses them for different jobs: Cinzel Decorative has
     * swash capitals that read beautifully at 49px on the wordmark and turn a
     * 14px button label into a smear. Preferring the plain cut here is the
     * whole point of having two chains.
     */
    private static final String[] UI_CANDIDATES = {
        "/fonts/Cinzel-SemiBold.ttf",
        "/fonts/Cinzel-Bold.ttf",
        "/fonts/Cinzel-Regular.ttf",
        "/fonts/Cinzel-VariableFont_wght.ttf",
    };

    private static final String[] UI_SYSTEM_FALLBACKS = {
        "Cinzel", "Cinzel Decorative",
    };

    private static Font bodyBase;
    private static boolean bodyLoadAttempted;

    private static Font uiSerifBase;
    private static boolean uiSerifLoadAttempted;

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
        } catch (RuntimeException e) {
            // registerFont and the headless environment can both throw beyond
            // the checked types. A missing font must never be fatal.
            System.err.println("[FontManager] " + path + " could not be registered ("
                    + e + ") — trying the next candidate.");
            return null;
        }
    }

    /** Looks for a Khmer face the operating system already has registered. */
    private static Font findSystemKhmerFont() {
        return findSystemFont(SYSTEM_FALLBACKS);
    }

    /** Looks for any of {@code wanted} among the faces the OS already has. */
    private static Font findSystemFont(String[] wanted) {
        try {
            List<String> installed = new ArrayList<>(List.of(
                    GraphicsEnvironment.getLocalGraphicsEnvironment()
                            .getAvailableFontFamilyNames()));

            for (String name : wanted) {
                for (String available : installed) {
                    if (available.equalsIgnoreCase(name)) {
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
     * The inscription-style display face, or null when nothing usable was
     * found. Resolved once and cached, the same as {@link #khmerBase()}.
     */
    public static synchronized Font displayBase() {
        if (displayLoadAttempted) {
            return displayBase;
        }
        displayLoadAttempted = true;

        Font bundled = loadFirstAvailableDisplay(DISPLAY_CANDIDATES);
        if (bundled != null) {
            displayBase = bundled;
            return displayBase;
        }

        Font system = findSystemFont(DISPLAY_SYSTEM_FALLBACKS);
        if (system != null) {
            System.out.println("[FontManager] No bundled display face — "
                    + "using system face " + system.getFontName() + ".");
            displayBase = system;
            return displayBase;
        }

        System.out.println("[FontManager] No Cinzel Decorative / Cinzel found. Add "
                + "e.g. " + DISPLAY_CANDIDATES[1] + " under src/main/resources to give "
                + "the wordmark its inscription face. The menu falls back to a plain "
                + "serif otherwise.");
        return null;
    }

    private static Font loadFirstAvailableDisplay(String[] paths) {
        for (String path : paths) {
            Font font = loadResourceFont(path);
            if (font != null) {
                displayLoadedFrom = path;
                System.out.println("[FontManager] Loaded " + font.getFontName()
                        + " from " + path);
                return font;
            }
        }
        return null;
    }

    /**
     * A font suitable for the main-menu wordmark. Falls back to a plain bold
     * serif — the same face the title always used — when nothing decorative is
     * bundled or installed, so a fresh clone still has a title, just a plainer
     * one.
     */
    public static Font displayFont(int size, int style) {
        Font base = displayBase();
        if (base != null) {
            return base.deriveFont(style, (float) size);
        }
        return new Font(Font.SERIF, style, size);
    }

    /** Which resource the display face came from, or null if none was bundled. */
    public static synchronized String displayLoadedFrom() {
        displayBase();
        return displayLoadedFrom;
    }

    /** The body serif, or null when nothing usable was found. Resolved once. */
    public static synchronized Font bodyBase() {
        if (bodyLoadAttempted) {
            return bodyBase;
        }
        bodyLoadAttempted = true;

        Font bundled = loadFirstAvailableQuietly(BODY_CANDIDATES);
        if (bundled != null) {
            bodyBase = bundled;
            return bodyBase;
        }
        bodyBase = findSystemFont(BODY_SYSTEM_FALLBACKS);
        if (bodyBase == null) {
            System.out.println("[FontManager] No EB Garamond found — captions fall "
                    + "back to an italic serif. See resources/fonts/README.md.");
        }
        return bodyBase;
    }

    /**
     * A font for prose: subtitles, captions and footnotes.
     *
     * <p>Falls back to the platform serif, which is the right shape of failure —
     * these lines are italic serif by intent, so an unstyled italic serif is a
     * plainer version of the design rather than a broken one.
     */
    public static Font bodyFont(int size, int style) {
        Font base = bodyBase();
        if (base != null) {
            return base.deriveFont(style, (float) size);
        }
        return new Font(Font.SERIF, style, size);
    }

    /** The plain inscription serif, or null when nothing usable was found. */
    public static synchronized Font uiSerifBase() {
        if (uiSerifLoadAttempted) {
            return uiSerifBase;
        }
        uiSerifLoadAttempted = true;

        Font bundled = loadFirstAvailableQuietly(UI_CANDIDATES);
        if (bundled != null) {
            uiSerifBase = bundled;
            return uiSerifBase;
        }
        uiSerifBase = findSystemFont(UI_SYSTEM_FALLBACKS);
        return uiSerifBase;
    }

    /**
     * A font for tracked capitals: button labels, stat labels, small chrome text.
     *
     * <p>Falls back to the platform serif rather than to sans, because these are
     * inscription-style caps and a sans fallback changes what the interface is
     * saying about itself more than a plainer serif does.
     */
    public static Font uiSerifFont(int size, int style) {
        Font base = uiSerifBase();
        if (base != null) {
            return base.deriveFont(style, (float) size);
        }
        return new Font(Font.SERIF, style, size);
    }

    /** {@link #loadFirstAvailable} without the per-file success logging. */
    private static Font loadFirstAvailableQuietly(String[] paths) {
        for (String path : paths) {
            Font font = loadResourceFont(path);
            if (font != null) {
                return font;
            }
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
