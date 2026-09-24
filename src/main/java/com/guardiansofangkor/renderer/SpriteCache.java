package com.guardiansofangkor.renderer;

import com.guardiansofangkor.engine.Difficulty;
import com.guardiansofangkor.entities.EnemyType;
import com.guardiansofangkor.entities.Hero;
import com.guardiansofangkor.entities.PowerUpType;
import com.guardiansofangkor.util.GameConfig;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
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

    /**
     * Device pixels per logical pixel on the main screen: 2 on a Retina Mac,
     * 1.25-2 on a scaled Windows display, 1 on a plain monitor or headless.
     *
     * <p>Swing lays the window out in logical pixels and the OS multiplies them
     * up. A sprite prepared at its logical draw size — 250px for the hero —
     * therefore reaches a Retina screen stretched to 500 device pixels, and
     * that stretch is exactly what made the art look soft. Working copies are
     * built at {@code drawSize * DISPLAY_SCALE} instead, so they land 1:1 on
     * the device. Every renderer draws them with an explicit destination size,
     * so the extra resolution changes sharpness and nothing else.
     *
     * <p>Capped at 3 so an exotic display cannot balloon memory.
     */
    private static final double DISPLAY_SCALE = detectDisplayScale();

    private static double detectDisplayScale() {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                return 1.0;
            }
            double scale = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration()
                    .getDefaultTransform().getScaleX();
            return Math.max(1.0, Math.min(3.0, scale));
        } catch (RuntimeException | Error e) {
            return 1.0;
        }
    }

    /** Pixel height for a working copy that is drawn {@code displayHeight} tall. */
    private static int sharp(int displayHeight) {
        return (int) Math.ceil(displayHeight * DISPLAY_SCALE);
    }

    /** Alpha at or below this counts as empty when trimming. */
    private static final int ALPHA_THRESHOLD = 32;

    /** How far the hero's halo bleeds past his silhouette. */
    public static final int GLOW_RADIUS = 14;

    private static final String BACKGROUND_PATH = "/images/Background.png";
    private static final String MENU_BACKGROUND_PATH = "/images/Main-Menu-Background.png";

    /**
     * How tall a hero is drawn on the selection screen.
     *
     * <p>Heroes get a second working copy at this size rather than one big copy
     * shared with play: the in-game sprite is blitted every frame, and drawing
     * a 440px image down to 250 sixty times a second is exactly the per-frame
     * rescale the working copies exist to avoid.
     */
    public static final int HERO_PORTRAIT_HEIGHT = 440;

    /** The pieces of a hero's effect sheet the game draws. */
    public enum ShotFx {
        /** Every ordinary shot. */
        BOLT,

        /** The blow a finished verse lands on the boss. */
        GREAT_BOLT,

        /** Where a finished word lands. */
        BLOOM,

        /** Where a mid-word shot lands. */
        BURST,

        /** The emblem on the hero's picker card. Optional. */
        CREST
    }

    /**
     * One piece of an effect sheet.
     *
     * @param region    where it sits on the sheet, padded for its glow but
     *                  clear of its neighbours — anything opaque inside the
     *                  rect survives trimming and is drawn with it
     * @param drawHeight the tallest it is ever drawn, in logical pixels
     * @param headAt    for bolts: how far along its width the tip sits, 0 to 1,
     *                  so the tip rather than the tail arrives on the target
     */
    public record Cut(Rectangle region, int drawHeight, double headAt) {
    }

    /**
     * Where each hero's pieces sit on their sheet.
     *
     * <p>Both sheets hold more than the game uses. Left out on purpose: the
     * long ribbon-trail bolts and loose petals on Apsara's, the crescent slashes
     * and leaf wisps on Preah Ream's. A shot lives for nine ticks, so anything
     * much longer than it is tall streaks across the plaza rather than flying.
     * Preah Ream's lotus-tipped arrow is the one long piece kept, because it is
     * an arrow and an archer's shot should look like one.
     *
     * <p>THESE RECTS DO NOT SURVIVE A SHEET CHANGE. Re-measure if either sheet
     * is replaced.
     */
    private static final Map<Hero, Map<ShotFx, Cut>> SHOT_CUTS = new EnumMap<>(Hero.class);

    static {
        Map<ShotFx, Cut> apsara = new EnumMap<>(ShotFx.class);
        apsara.put(ShotFx.BOLT, new Cut(new Rectangle(495, 85, 295, 185), 34, 0.85));
        apsara.put(ShotFx.GREAT_BOLT, new Cut(new Rectangle(1175, 20, 585, 290), 64, 0.85));
        apsara.put(ShotFx.BLOOM, new Cut(new Rectangle(20, 470, 555, 400), 96, 0.5));
        apsara.put(ShotFx.BURST, new Cut(new Rectangle(590, 480, 440, 390), 56, 0.5));
        apsara.put(ShotFx.CREST, new Cut(new Rectangle(1450, 560, 175, 155), 40, 0.5));
        SHOT_CUTS.put(Hero.APSARA, apsara);

        Map<ShotFx, Cut> ream = new EnumMap<>(ShotFx.class);
        // The lotus-tipped arrow: long and thin, so drawn short and led by its tip.
        ream.put(ShotFx.BOLT, new Cut(new Rectangle(15, 262, 1135, 191), 26, 0.97));
        ream.put(ShotFx.GREAT_BOLT, new Cut(new Rectangle(1160, 5, 605, 277), 64, 0.93));
        ream.put(ShotFx.BLOOM, new Cut(new Rectangle(20, 578, 485, 294), 96, 0.5));
        ream.put(ShotFx.BURST, new Cut(new Rectangle(540, 590, 322, 282), 56, 0.5));
        SHOT_CUTS.put(Hero.PREAH_REAM, ream);
    }

    /** Which picture of a hero to draw. */
    public enum Pose {
        IDLE,
        ATTACK_LEFT,
        ATTACK_RIGHT;

        /** The pose for a hero that is (or is not) mid-shot, facing its target. */
        public static Pose of(boolean firing, boolean aimingLeft) {
            if (!firing) {
                return IDLE;
            }
            return aimingLeft ? ATTACK_LEFT : ATTACK_RIGHT;
        }
    }

    private final Map<EnemyType, BufferedImage> sprites = new EnumMap<>(EnemyType.class);
    private final Map<EnemyType, BufferedImage> silhouettes = new EnumMap<>(EnemyType.class);
    private final Map<EnemyType, Boolean> loadAttempted = new EnumMap<>(EnemyType.class);

    private BufferedImage getchargerSprintSprite;
    private BufferedImage chargerSprintSilhouette;
    private BufferedImage customProjectileSprite;


    private final Map<PowerUpType, BufferedImage> powerUpIcons =
            new EnumMap<>(PowerUpType.class);
    private final Map<PowerUpType, Boolean> powerUpAttempted =
            new EnumMap<>(PowerUpType.class);

    private BufferedImage chargerSprintSprite;
    private BufferedImage background;
    private boolean backgroundAttempted;

    private BufferedImage menuBackground;
    private boolean menuBackgroundAttempted;

    /** In-game sprites per hero, indexed by {@link Pose#ordinal()}. */
    private final Map<Hero, BufferedImage[]> heroSprites = new EnumMap<>(Hero.class);

    /** Selection-screen copies per hero, indexed the same way. */
    private final Map<Hero, BufferedImage[]> heroPortraits = new EnumMap<>(Hero.class);

    /** Rim-light halos, keyed by hero and pose. A null value is a failed build. */
    private final Map<String, BufferedImage> heroGlows = new HashMap<>();
    private int glowBuiltForHeight = -1;

    private final Map<Hero, Map<ShotFx, BufferedImage>> shotFx = new EnumMap<>(Hero.class);

    /**
     * The trimmed sprite for {@code type}, or null when its art has not been
     * added yet. Loaded once per type; a failed load is not retried every frame.
     */
    public BufferedImage sprite(EnemyType type) {
        if (type == null) {
            return null;
        }

        if (type == EnemyType.CHARGER && chargerSprintSprite == null) {
            BufferedImage rawSprint = read("/images/DemonbullCharge.png");
            if (rawSprint != null) {
                chargerSprintSprite = toWorkingCopy(safeTrim(rawSprint), sharp(workingHeightFor(type)));
            }
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

        BufferedImage ready = toWorkingCopy(trimmed, sharp(workingHeightFor(type)));
        sprites.put(type, ready);
        return ready;
    }

    public BufferedImage chargerSprintSprite() {
        return chargerSprintSprite;
    }
    /** Generates a white hit-flash silhouette specifically for the charging sprite. */
    public BufferedImage chargerSprintSilhouette() {
        BufferedImage source = chargerSprintSprite();
        if (source == null) {
            return null;
        }
        if (chargerSprintSilhouette != null) {
            return chargerSprintSilhouette;
        }

        BufferedImage out = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                // Keep alpha, force RGB to white
                out.setRGB(x, y, (source.getRGB(x, y) & 0xFF000000) | 0x00FFFFFF);
            }
        }
        chargerSprintSilhouette = out;
        return out;
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
                toWorkingCopy(safeTrim(raw), sharp(GameConfig.POWERUP_ICON_SIZE));
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
     * A hero's in-game sprite for the requested pose.
     *
     * <p>Every pose is loaded together so the swap on the first shot does not
     * cause a one-frame stall while the attack image decodes.
     *
     * @return the sprite, or null when the hero has no art at all
     */
    public BufferedImage hero(Hero hero, Pose pose) {
        return pick(loadHero(hero)[0], pose);
    }

    /** The hero drawn large, for the selection screen. */
    public BufferedImage heroPortrait(Hero hero, Pose pose) {
        return pick(loadHero(hero)[1], pose);
    }

    /**
     * The requested pose, or the nearest one that exists.
     *
     * <p>A missing attack pose falls back to the other side and then to idle,
     * so a single missing file never makes the hero vanish mid-shot.
     */
    private static BufferedImage pick(BufferedImage[] poses, Pose pose) {
        BufferedImage wanted = poses[pose.ordinal()];
        if (wanted != null) {
            return wanted;
        }
        Pose[] order = switch (pose) {
            case IDLE -> new Pose[] {Pose.ATTACK_RIGHT, Pose.ATTACK_LEFT};
            case ATTACK_LEFT -> new Pose[] {Pose.ATTACK_RIGHT, Pose.IDLE};
            case ATTACK_RIGHT -> new Pose[] {Pose.ATTACK_LEFT, Pose.IDLE};
        };
        for (Pose fallback : order) {
            if (poses[fallback.ordinal()] != null) {
                return poses[fallback.ordinal()];
            }
        }
        return null;
    }

    /**
     * Loads every pose of a hero at both working sizes, once.
     *
     * @return {in-game poses, portrait poses}
     */
    private BufferedImage[][] loadHero(Hero hero) {
        Hero key = hero == null ? Hero.defaultChoice() : hero;
        BufferedImage[] inGame = heroSprites.get(key);
        if (inGame != null) {
            return new BufferedImage[][] {inGame, heroPortraits.get(key)};
        }

        inGame = new BufferedImage[Pose.values().length];
        BufferedImage[] portraits = new BufferedImage[Pose.values().length];
        String[] paths = {key.getIdlePath(), key.getAttackLeftPath(), key.getAttackRightPath()};

        // Preah Ream uses one file for both attack sides; decode it once.
        Map<String, BufferedImage[]> byPath = new HashMap<>();
        for (int i = 0; i < paths.length; i++) {
            BufferedImage[] pair = byPath.get(paths[i]);
            if (pair == null) {
                // The hero sources run up to 1024x1536 and are redrawn every
                // frame, twice over once the rim light is counted.
                BufferedImage trimmed = safeTrim(read(paths[i]));
                pair = new BufferedImage[] {
                        toWorkingCopy(trimmed, sharp(GameConfig.PLAYER_HEIGHT)),
                        toWorkingCopy(trimmed, sharp(HERO_PORTRAIT_HEIGHT))};
                byPath.put(paths[i], pair);
            }
            inGame[i] = pair[0];
            portraits[i] = pair[1];
        }

        if (inGame[0] == null && inGame[1] == null && inGame[2] == null) {
            System.out.println("[SpriteCache] No art found for " + key.getDisplayName()
                    + " — drawing a placeholder guardian.");
        }
        heroSprites.put(key, inGame);
        heroPortraits.put(key, portraits);
        return new BufferedImage[][] {inGame, portraits};
    }

    /**
     * One piece of a hero's effect sheet, or null when the sheet is missing or
     * the hero has no such piece — the renderer then falls back to vector art.
     *
     * <p>Each sheet is decoded once and every piece is cut, trimmed and scaled
     * to its own working copy in the same pass. A piece is a real copy rather
     * than a {@code getSubimage} view onto the sheet, for the same managed-image
     * reason given in the class comment.
     */
    public BufferedImage shotFx(Hero hero, ShotFx piece) {
        Hero key = hero == null ? Hero.defaultChoice() : hero;
        Map<ShotFx, BufferedImage> pieces = shotFx.get(key);
        if (pieces == null) {
            pieces = new EnumMap<>(ShotFx.class);
            shotFx.put(key, pieces);
            Map<ShotFx, Cut> cuts = SHOT_CUTS.getOrDefault(key, Map.of());
            BufferedImage sheet = cuts.isEmpty() ? null : read(key.getEffectSheetPath());
            if (sheet == null) {
                System.out.println("[SpriteCache] No effect sheet for " + key.getDisplayName()
                        + " — drawing plain arrows.");
            } else {
                Rectangle bounds = new Rectangle(0, 0, sheet.getWidth(), sheet.getHeight());
                for (Map.Entry<ShotFx, Cut> cut : cuts.entrySet()) {
                    Rectangle r = cut.getValue().region().intersection(bounds);
                    if (r.isEmpty()) {
                        continue;
                    }
                    BufferedImage region = sheet.getSubimage(r.x, r.y, r.width, r.height);
                    pieces.put(cut.getKey(), toWorkingCopy(safeTrim(region),
                            sharp(cut.getValue().drawHeight())));
                }
            }
        }
        return pieces.get(piece);
    }

    public BufferedImage customProjectile() {
        if (customProjectileSprite != null) {
            return customProjectileSprite;
        }
        BufferedImage raw = read("/images/SplitterBall.png");
        if (raw != null) {
            // Scaled to a max height of 45 pixels so it fits the hitbox. Not
            // sharp(): the renderer draws this one at its natural pixel size,
            // so a larger copy would draw larger rather than crisper.
            customProjectileSprite = toWorkingCopy(safeTrim(raw), 45);
        }
        return customProjectileSprite;
    }

    /** How a piece is laid out, or null when the hero has no such piece. */
    public Cut shotCut(Hero hero, ShotFx piece) {
        return SHOT_CUTS.getOrDefault(hero, Map.of()).get(piece);
    }

    /** Width for a hero at a given height, preserving the pose's aspect ratio. */
    public int heroWidth(Hero hero, Pose pose, int height) {
        return widthAt(hero(hero, pose), height);
    }

    /** Width for a hero's portrait at a given height. */
    public int heroPortraitWidth(Hero hero, Pose pose, int height) {
        return widthAt(heroPortrait(hero, pose), height);
    }

    private static int widthAt(BufferedImage image, int height) {
        if (image == null || image.getHeight() == 0) {
            return (int) Math.round(height * 0.6);
        }
        return Math.max(1,
                (int) Math.round(height * (image.getWidth() / (double) image.getHeight())));
    }

    /**
     * A soft gold halo matching the hero's silhouette, drawn behind them so they
     * separate from the temple behind.
     *
     * <p>Built by scaling the silhouette to display size, padding it, and
     * running a separable Gaussian blur. Done at <em>display</em> size rather
     * than source size and cached per hero and pose — blurring the full source
     * every frame would cost hundreds of millions of operations and stall the
     * loop.
     *
     * @param height the on-screen height the hero is drawn at
     * @return the halo, or null when there is no art to derive one from
     */
    public BufferedImage heroGlow(Hero hero, Pose pose, int height) {
        if (glowBuiltForHeight != height) {
            // Display size changed, so the cached halos are the wrong scale.
            heroGlows.clear();
            glowBuiltForHeight = height;
        }

        // Tracked by key presence rather than a null check, so a failed build
        // is not retried on every single frame.
        Hero key = hero == null ? Hero.defaultChoice() : hero;
        String cacheKey = key.name() + "/" + pose.name();
        if (heroGlows.containsKey(cacheKey)) {
            return heroGlows.get(cacheKey);
        }

        BufferedImage source = hero(key, pose);
        BufferedImage built = null;
        if (source != null) {
            try {
                built = buildGlow(source, heroWidth(key, pose, height), height);
            } catch (RuntimeException | OutOfMemoryError e) {
                // Building the halo allocates a padded canvas and runs two
                // convolve passes. If either fails, the hero simply draws
                // without a rim light — a cosmetic loss, not a lost frame.
                System.err.println("[SpriteCache] Could not build the hero glow ("
                        + e + ") — drawing without a rim light.");
            }
        }
        heroGlows.put(cacheKey, built);
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
            // Halve in steps, then make one final resize. A single bilinear
            // pass from 1536px to 250 reads only four source pixels per output
            // pixel and skips everything between them, which turns fine line
            // work — jewellery, hair, the gold trim — into grain. Halving never
            // skips a pixel, so the detail is averaged down instead of dropped.
            BufferedImage current = source;
            int currentW = source.getWidth();
            int currentH = sourceHeight;
            while (currentH / 2 >= height && currentW / 2 >= width) {
                currentW /= 2;
                currentH /= 2;
                current = resized(current, currentW, currentH,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            }
            if (currentW != width || currentH != height || current == source) {
                current = resized(current, width, height,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            }
            return current;
        } catch (RuntimeException | OutOfMemoryError e) {
            // A failed conversion costs speed, never the sprite itself.
            System.err.println("[SpriteCache] Could not prepare an image ("
                    + e + ") — using it as decoded.");
            return source;
        }
    }

    /** One resize into a fresh premultiplied image, at quality settings. */
    private static BufferedImage resized(BufferedImage source, int width, int height,
                                         Object interpolation) {
        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = copy.createGraphics();
        try {
            // Quality is affordable here in a way it is not per-frame: this
            // runs once per sprite for the life of the process.
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                    RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return copy;
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
