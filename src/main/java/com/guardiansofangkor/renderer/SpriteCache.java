package com.guardiansofangkor.renderer;

import com.guardiansofangkor.engine.Difficulty;
import com.guardiansofangkor.entities.EnemyType;
import com.guardiansofangkor.entities.PowerUpType;
import com.guardiansofangkor.util.GameConfig;

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
 *
 * <p><b>Everything is converted to a display-compatible copy at working size.</b>
 * Two separate reasons, both of which cost real frame time on Windows and very
 * little on macOS, which is why the game ran slowly on one and not the other:
 *
 * <ol>
 *   <li>{@link BufferedImage#getSubimage} returns a <em>view</em> onto the
 *       parent's raster. Java2D will not treat a view as a managed image, so it
 *       can never be cached in video memory and every single blit of it falls
 *       back to a software loop. Trimming therefore has to produce a real copy,
 *       not a window onto the original.</li>
 *   <li>{@code ImageIO} decodes PNGs to whatever the file says, usually
 *       {@code TYPE_4BYTE_ABGR} or {@code TYPE_CUSTOM}. Neither matches the
 *       screen, so each draw pays a per-pixel format conversion.
 *       {@code TYPE_INT_ARGB_PRE} is the format the pipeline actually wants.</li>
 * </ol>
 *
 * <p>They are also scaled down once, on load, to the largest size they are ever
 * drawn at. The art is delivered at up to 1216x1200; a monster on screen is
 * around two hundred pixels tall. Rescaling from the full source sixty times a
 * second is most of a frame's work thrown away, and the pixels beyond the
 * display size cannot be seen by definition.
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

    private final Map<PowerUpType, BufferedImage> powerUpIcons =
            new EnumMap<>(PowerUpType.class);
    private final Map<PowerUpType, Boolean> powerUpAttempted =
            new EnumMap<>(PowerUpType.class);

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

        BufferedImage ready = toWorkingCopy(trimmed, workingHeightFor(type));
        sprites.put(type, ready);
        return ready;
    }

    /**
     * The tallest this type is ever drawn, and therefore the only resolution
     * worth keeping.
     *
     * <p>Derived from the tier table rather than hard-coded, so pointing a tier
     * at a different final boss cannot silently leave that monster cached too
     * small to draw at boss size.
     */
    private static int workingHeightFor(EnemyType type) {
        // Only the types a tier actually ends on are ever drawn at BOSS_HEIGHT.
        // Giving every type that headroom cost real sharpness: a Kmaoch drawn
        // at 130 was being cached at 380 and then bilinearly reduced by 2.9x on
        // every frame. Bilinear samples a 2x2 neighbourhood, so any reduction
        // past 2x undersamples — which reads as a soft, shimmering sprite
        // rather than as a small one.
        //
        // Cached at the size it is actually drawn, the per-frame blit is 1:1
        // and the only scaling left is the one-time, high-quality reduction
        // from the source art.
        return isEverAFinalBoss(type)
                ? Math.max(type.getTargetHeight(), GameConfig.BOSS_HEIGHT)
                : type.getTargetHeight();
    }

    /** True when some tier ends on this type, so it is also drawn boss-sized. */
    private static boolean isEverAFinalBoss(EnemyType type) {
        for (Difficulty tier : Difficulty.values()) {
            if (tier.getFinalBossType() == type) {
                return true;
            }
        }
        return false;
    }

    /**
     * The icon for a power-up, or null when its art has not been drawn yet.
     *
     * <p>None of it has been, at time of writing. That is fine and deliberate:
     * the renderer draws a glyph in the boon's palette colour instead, exactly
     * as it already does for the enemy types still awaiting art. Dropping a
     * {@code powerup_*.png} into {@code resources/images} is all it takes to
     * replace one — there is no registration step and no code to change.
     */
    public BufferedImage powerUpIcon(PowerUpType type) {
        if (type == null) {
            return null;
        }
        if (Boolean.TRUE.equals(powerUpAttempted.get(type))) {
            return powerUpIcons.get(type);
        }
        powerUpAttempted.put(type, Boolean.TRUE);

        BufferedImage raw = read(type.getSpritePath());
        if (raw == null) {
            System.out.println("[SpriteCache] No art for " + type.getDisplayName()
                    + " (" + type.getSpritePath() + ") — drawing a placeholder.");
            return null;
        }
        BufferedImage ready =
                toWorkingCopy(safeTrim(raw), GameConfig.POWERUP_ICON_SIZE);
        powerUpIcons.put(type, ready);
        return ready;
    }

    /** True when this boon has real art, as opposed to a placeholder glyph. */
    public boolean hasPowerUpIcon(PowerUpType type) {
        return powerUpIcon(type) != null;
    }

    /** The temple backdrop, or null when it is missing. */
    public BufferedImage background() {
        if (backgroundAttempted) {
            return background;
        }
        backgroundAttempted = true;
        background = toBackdrop(read(BACKGROUND_PATH));
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
        menuBackground = toBackdrop(read(MENU_BACKGROUND_PATH));
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
            // Preah Ream is the biggest source in the game at 896x1200 and is
            // redrawn every frame, twice over once the rim light is counted.
            playerIdle = toWorkingCopy(safeTrim(idle), GameConfig.PLAYER_HEIGHT);
            playerAction = toWorkingCopy(safeTrim(action), GameConfig.PLAYER_HEIGHT);

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
     * A display-compatible copy, scaled down to at most {@code maxHeight}.
     *
     * <p>See the class comment: this is what makes a sprite cacheable in video
     * memory instead of re-converted and re-scaled from the full-resolution
     * source on every frame. Never upscales — a sprite smaller than its display
     * size is left alone, since inventing pixels here would only bake in
     * blurring the renderer can do just as well on the fly.
     */
    private static BufferedImage toWorkingCopy(BufferedImage source, int maxHeight) {
        if (source == null) {
            return null;
        }
        int sourceHeight = Math.max(1, source.getHeight());
        double scale = Math.min(1.0, maxHeight / (double) sourceHeight);

        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(sourceHeight * scale));

        try {
            BufferedImage copy = new BufferedImage(
                    width, height, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g = copy.createGraphics();
            try {
                // Quality is affordable here in a way it is not per-frame: this
                // runs once per sprite for the life of the process.
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(source, 0, 0, width, height, null);
            } finally {
                g.dispose();
            }
            return copy;
        } catch (RuntimeException | OutOfMemoryError e) {
            // A failed conversion costs speed, never the sprite itself.
            System.err.println("[SpriteCache] Could not prepare an image ("
                    + e + ") — using it as decoded.");
            return source;
        }
    }

    /**
     * An opaque, screen-sized copy of a full-bleed backdrop.
     *
     * <p>The delivered art is 1672x941 and the window is 1280x720, so drawing it
     * directly means rescaling one and a half million pixels every frame to
     * produce the one image on screen guaranteed never to change. Opaque rather
     * than ARGB because it is the bottom layer: there is nothing behind it to
     * blend with, and skipping the alpha channel skips the blend.
     */
    private static BufferedImage toBackdrop(BufferedImage source) {
        if (source == null) {
            return null;
        }
        try {
            BufferedImage copy = new BufferedImage(
                    GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = copy.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(source, 0, 0,
                        GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT, null);
            } finally {
                g.dispose();
            }
            return copy;
        } catch (RuntimeException | OutOfMemoryError e) {
            System.err.println("[SpriteCache] Could not prepare a backdrop ("
                    + e + ") — drawing it scaled every frame instead.");
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
