package com.guardiansofangkor.renderer;

import com.guardiansofangkor.entities.EnemyType;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * Loads and caches artwork.
 *
 * <p>Two things matter here beyond plain caching:
 *
 * <p><b>Missing art is not an error.</b> Only some of the roster has been drawn.
 * A type with no PNG yet returns null and the renderer draws a placeholder shape
 * at the same dimensions, so the game stays playable and dropping the real file
 * into {@code src/main/resources/images/} later needs no code change.
 *
 * <p><b>Sprites are trimmed to their content.</b> The delivered PNGs sit on large
 * transparent canvases with very different amounts of padding — Yeak has ~25%
 * empty space on each side, Krong Reap under 4%. Scaling the raw canvas would
 * make the same nominal height produce wildly different apparent sizes, and
 * would float grounded monsters above the plaza by however much transparent
 * padding sat below their feet. Trimming to the opaque bounding box first fixes
 * both problems at once.
 */
public class SpriteCache {

    /** Alpha at or below this counts as empty when trimming. */
    private static final int ALPHA_THRESHOLD = 32;

    /** How far the hero's halo bleeds past his silhouette. */
    public static final int GLOW_RADIUS = 14;

    private static final String BACKGROUND_PATH = "/images/Background.png";
    private static final String MENU_BACKGROUND_PATH = "/images/Main-Menu-Background.png";
    private static final String PLAYER_IDLE_PATH = "/images/Prea_Ream(idle).png";
    private static final String PLAYER_ACTION_PATH = "/images/Preas_Ream(Action).png";

    private final Map<EnemyType, BufferedImage> sprites = new EnumMap<>(EnemyType.class);
    private final Map<EnemyType, BufferedImage> silhouettes = new EnumMap<>(EnemyType.class);
    private final Map<EnemyType, Boolean> loadAttempted = new EnumMap<>(EnemyType.class);

    private BufferedImage background;
    private boolean backgroundAttempted;

    private BufferedImage menuBackground;
    private boolean menuBackgroundAttempted;

    private BufferedImage playerIdle;
    private BufferedImage playerAction;
    private boolean playerAttempted;

    private BufferedImage playerGlowIdle;
    private BufferedImage playerGlowAction;
    private boolean glowIdleAttempted;
    private boolean glowActionAttempted;
    private int glowBuiltForHeight = -1;

    /**
     * The trimmed sprite for {@code type}, or null when its art has not been
     * added yet. Loaded once per type; a failed load is not retried every frame.
     */
    public BufferedImage sprite(EnemyType type) {
        if (type == null) {
            return null;
        }
        if (Boolean.TRUE.equals(loadAttempted.get(type))) {
            return sprites.get(type);
        }
        loadAttempted.put(type, Boolean.TRUE);

        BufferedImage raw = read(type.getSpritePath());
        if (raw == null) {
            System.out.println("[SpriteCache] No art for " + type.getDisplayName()
                    + " (" + type.getSpritePath() + ") — drawing a placeholder.");
            return null;
        }

        BufferedImage trimmed;
        try {
            trimmed = trim(raw);
        } catch (RuntimeException e) {
            // An unusual raster or colour model can throw during the scan. Use
            // the untrimmed image rather than losing the monster entirely.
            System.err.println("[SpriteCache] Could not trim "
                    + type.getDisplayName() + " (" + e + ") — using it untrimmed.");
            trimmed = raw;
        }
        sprites.put(type, trimmed);
        return trimmed;
    }

    /** The temple backdrop, or null when it is missing. */
    public BufferedImage background() {
        if (backgroundAttempted) {
            return background;
        }
        backgroundAttempted = true;
        background = read(BACKGROUND_PATH);
        if (background == null) {
            System.out.println("[SpriteCache] No background at " + BACKGROUND_PATH
                    + " — falling back to a painted gradient.");
        }
        return background;
    }

    /**
     * The title-screen painting, or null when it is missing.
     *
     * <p>A separate image from the in-game backdrop: it includes Preah Ream
     * drawn into the scene, which would double up with the live player sprite
     * during play.
     */
    public BufferedImage menuBackground() {
        if (menuBackgroundAttempted) {
            return menuBackground;
        }
        menuBackgroundAttempted = true;
        menuBackground = read(MENU_BACKGROUND_PATH);
        if (menuBackground == null) {
            System.out.println("[SpriteCache] No menu art at " + MENU_BACKGROUND_PATH
                    + " — falling back to a painted gradient.");
        }
        return menuBackground;
    }

    /**
     * Preah Ream's sprite for the requested pose.
     *
     * <p>Both poses are loaded together so the swap on the first shot does not
     * cause a one-frame stall while the action image decodes.
     *
     * @param firing true for the drawn-bow pose, false for idle
     */
    public BufferedImage player(boolean firing) {
        if (!playerAttempted) {
            playerAttempted = true;
            BufferedImage idle = read(PLAYER_IDLE_PATH);
            BufferedImage action = read(PLAYER_ACTION_PATH);
            playerIdle = safeTrim(idle);
            playerAction = safeTrim(action);

            if (playerIdle == null && playerAction == null) {
                System.out.println("[SpriteCache] No Preah Ream art found — "
                        + "drawing a placeholder guardian.");
            }
        }
        // Fall back to whichever pose exists, so a single missing file does not
        // make the hero vanish mid-shot.
        BufferedImage wanted = firing ? playerAction : playerIdle;
        if (wanted != null) {
            return wanted;
        }
        return firing ? playerIdle : playerAction;
    }

    /** Width for Preah Ream at a given height, preserving his aspect ratio. */
    public int playerWidth(boolean firing, int height) {
        BufferedImage image = player(firing);
        if (image == null || image.getHeight() == 0) {
            return (int) Math.round(height * 0.6);
        }
        return Math.max(1,
                (int) Math.round(height * (image.getWidth() / (double) image.getHeight())));
    }

    /**
     * A soft gold halo matching Preah Ream's silhouette, drawn behind him so he
     * separates from the temple behind.
     *
     * <p>Built by scaling his silhouette to display size, padding it, and
     * running a separable Gaussian blur. Done at <em>display</em> size rather
     * than source size and cached per pose — blurring the full 896x1200 source
     * every frame would cost hundreds of millions of operations and stall the
     * loop.
     *
     * @param firing which pose to build the halo for
     * @param height the on-screen height he is drawn at
     * @return the halo, or null when there is no art to derive one from
     */
    public BufferedImage playerGlow(boolean firing, int height) {
        if (glowBuiltForHeight != height) {
            // Display size changed, so the cached halos are the wrong scale.
            playerGlowIdle = null;
            playerGlowAction = null;
            glowIdleAttempted = false;
            glowActionAttempted = false;
            glowBuiltForHeight = height;
        }

        // Tracked with a flag rather than a null check, so a failed build is not
        // retried on every single frame.
        if (firing ? glowActionAttempted : glowIdleAttempted) {
            return firing ? playerGlowAction : playerGlowIdle;
        }
        if (firing) {
            glowActionAttempted = true;
        } else {
            glowIdleAttempted = true;
        }

        BufferedImage source = player(firing);
        if (source == null) {
            return null;
        }

        BufferedImage built;
        try {
            built = buildGlow(source, playerWidth(firing, height), height);
        } catch (RuntimeException | OutOfMemoryError e) {
            // Building the halo allocates a padded canvas and runs two convolve
            // passes. If either fails, the hero simply draws without a rim
            // light — a cosmetic loss, not a reason to lose the frame.
            System.err.println("[SpriteCache] Could not build the hero glow ("
                    + e + ") — drawing without a rim light.");
            built = null;
        }

        if (firing) {
            playerGlowAction = built;
        } else {
            playerGlowIdle = built;
        }
        return built;
    }

    /** Scales, tints gold, pads and blurs a sprite into a halo. */
    private BufferedImage buildGlow(BufferedImage source, int width, int height) {
        int pad = GLOW_RADIUS * 3;
        BufferedImage canvas = new BufferedImage(
                width + pad * 2, height + pad * 2, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, pad, pad, width, height, null);
        } finally {
            g.dispose();
        }

        // Flatten to a solid gold silhouette, keeping alpha, before blurring.
        int gold = Palette.GLOW.getRGB() & 0x00FFFFFF;
        for (int y = 0; y < canvas.getHeight(); y++) {
            for (int x = 0; x < canvas.getWidth(); x++) {
                int argb = canvas.getRGB(x, y);
                canvas.setRGB(x, y, (argb & 0xFF000000) | gold);
            }
        }

        // Separable blur: two 1-D passes instead of one 2-D kernel. For radius
        // 14 that is 58 taps per pixel rather than 841.
        BufferedImage blurred = convolve(canvas, gaussianKernel(GLOW_RADIUS, true));
        return convolve(blurred, gaussianKernel(GLOW_RADIUS, false));
    }

    private static Kernel gaussianKernel(int radius, boolean horizontal) {
        int size = radius * 2 + 1;
        float[] data = new float[size];
        double sigma = radius / 2.4;
        double twoSigmaSq = 2 * sigma * sigma;
        double total = 0;

        for (int i = -radius; i <= radius; i++) {
            double value = Math.exp(-(i * i) / twoSigmaSq);
            data[i + radius] = (float) value;
            total += value;
        }
        for (int i = 0; i < data.length; i++) {
            data[i] /= (float) total;
        }
        return horizontal ? new Kernel(size, 1, data) : new Kernel(1, size, data);
    }

    private static BufferedImage convolve(BufferedImage source, Kernel kernel) {
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_ZERO_FILL, null);
        BufferedImage out = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        op.filter(source, out);
        return out;
    }

    /** True when this type has real art, as opposed to a placeholder. */
    public boolean hasSprite(EnemyType type) {
        return sprite(type) != null;
    }

    /**
     * An all-white copy of the sprite that keeps its alpha, used for the
     * hit flash so the wash follows the monster's silhouette rather than a
     * bounding box.
     *
     * <p>Cached, because building it is a per-pixel loop over a ~900x900 image —
     * doing that every frame of every flash would visibly stutter the game.
     */
    public BufferedImage silhouette(EnemyType type) {
        BufferedImage source = sprite(type);
        if (source == null) {
            return null;
        }
        BufferedImage cached = silhouettes.get(type);
        if (cached != null) {
            return cached;
        }

        BufferedImage out = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                // Keep alpha, force RGB to white.
                out.setRGB(x, y, (source.getRGB(x, y) & 0xFF000000) | 0x00FFFFFF);
            }
        }
        silhouettes.put(type, out);
        return out;
    }

    /**
     * On-screen width for {@code type} at its configured height, preserving the
     * sprite's own aspect ratio. Falls back to a square when there is no art.
     */
    public int widthFor(EnemyType type) {
        BufferedImage image = sprite(type);
        int height = type.getTargetHeight();
        if (image == null || image.getHeight() == 0) {
            return height;
        }
        return Math.max(1,
                (int) Math.round(height * (image.getWidth() / (double) image.getHeight())));
    }

    // ---- loading helpers ---------------------------------------------------

    private BufferedImage read(String path) {
        try (InputStream in = SpriteCache.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return ImageIO.read(in);
        } catch (IOException | RuntimeException e) {
            // Per Section 5.4 every I/O boundary degrades gracefully — a corrupt
            // PNG costs one sprite, not the whole game.
            System.err.println("[SpriteCache] Could not read " + path
                    + " (" + e.getMessage() + ").");
            return null;
        }
    }

    /** {@link #trim} that degrades to the untrimmed image instead of throwing. */
    private static BufferedImage safeTrim(BufferedImage source) {
        if (source == null) {
            return null;
        }
        try {
            return trim(source);
        } catch (RuntimeException e) {
            System.err.println("[SpriteCache] Could not trim an image ("
                    + e + ") — using it untrimmed.");
            return source;
        }
    }

    /**
     * Crops away fully transparent margins so the returned image is exactly the
     * monster. Returns the original if it has no alpha channel or is entirely
     * transparent.
     */
    static BufferedImage trim(BufferedImage source) {
        if (source == null) {
            return null;
        }
        if (!source.getColorModel().hasAlpha()) {
            return source;
        }

        int width = source.getWidth();
        int height = source.getHeight();
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (source.getRGB(x, y) >>> 24);
                if (alpha > ALPHA_THRESHOLD) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            return source;
        }
        return source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }
}
