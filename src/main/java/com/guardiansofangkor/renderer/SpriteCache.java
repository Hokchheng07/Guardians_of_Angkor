package com.guardiansofangkor.renderer;

import com.guardiansofangkor.entities.EnemyType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
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

    private static final String BACKGROUND_PATH = "/images/Background.png";

    private final Map<EnemyType, BufferedImage> sprites = new EnumMap<>(EnemyType.class);
    private final Map<EnemyType, BufferedImage> silhouettes = new EnumMap<>(EnemyType.class);
    private final Map<EnemyType, Boolean> loadAttempted = new EnumMap<>(EnemyType.class);

    private BufferedImage background;
    private boolean backgroundAttempted;

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
        BufferedImage trimmed = trim(raw);
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
