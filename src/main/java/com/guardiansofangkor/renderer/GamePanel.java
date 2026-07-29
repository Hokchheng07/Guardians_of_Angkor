package com.guardiansofangkor.renderer;

import com.guardiansofangkor.engine.GameState;
import com.guardiansofangkor.entities.AttackPhase;
import com.guardiansofangkor.entities.Enemy;
import com.guardiansofangkor.entities.EnemyType;
import com.guardiansofangkor.entities.Player;
import com.guardiansofangkor.entities.Projectile;
import com.guardiansofangkor.entities.VisualEffect;
import com.guardiansofangkor.i18n.FontManager;
import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.matching.WordTarget;
import com.guardiansofangkor.util.GameConfig;

import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The drawing surface. Reads {@link GameState} and paints it — and does nothing
 * else. No gameplay decisions are made in this class: it never moves an enemy,
 * never scores a word, never mutates anything it reads.
 *
 * <p>Sprites are anchored by ground behaviour: grounded monsters have the bottom
 * of their artwork placed exactly on their current ground Y and never bob, so
 * their feet stay planted. Floaters are centred on a hover height and bob on a
 * sine wave. Everything is scaled by its approach progress, which is what makes
 * the diagonal read as depth rather than sliding.
 */
public class GamePanel extends JPanel {

    private static final Color COLOR_SKY_TOP = new Color(0x1E, 0x19, 0x14);
    private static final Color COLOR_SKY_BOTTOM = new Color(0x6B, 0x3A, 0x2A);
    private static final Color COLOR_GROUND = new Color(0x22, 0x22, 0x17);
    private static final Color COLOR_PLACEHOLDER = new Color(0x4A, 0x3E, 0x2E);
    private static final Color COLOR_PLACEHOLDER_EDGE = new Color(0x7A, 0x66, 0x48);
    private static final Color COLOR_TEXT = Palette.HUD_TEXT_WHITE;
    private static final Color COLOR_TYPED = Palette.HUD_DIVIDER;
    private static final Color COLOR_LOCKED_GLOW = Palette.HUD_TEXT_GOLD;
    private static final Color COLOR_WORD_BG = new Color(0x1E, 0x19, 0x14, 205);
    private static final Color COLOR_SHADOW = new Color(0x00, 0x00, 0x00, 110);
    private static final Color COLOR_POOF = new Color(0xD2, 0xC6, 0xB0);
    private static final Color COLOR_BOLT_CORE = new Color(0xFF, 0xE7, 0xA8);
    private static final Color COLOR_BOLT_EDGE = new Color(0xD9, 0x5B, 0x3C);
    private static final Color COLOR_BOLT_WORD = new Color(0xFF, 0xC9, 0x5C);
    private static final Color COLOR_ARROW = new Color(0xF4, 0xE3, 0xB0);
    private static final Color COLOR_TELEGRAPH = new Color(0xE8, 0x6A, 0x4A);

    /** Vertical bob amplitude in pixels. Floaters only. */
    private static final double BOB_AMPLITUDE = 7.0;

    /** Bob cycles per tick — multiplied by each type's speed for variety. */
    private static final double BOB_FREQUENCY = 0.07;

    /** How far a throwing enemy leans back at full windup, in radians. */
    private static final double WINDUP_LEAN = Math.toRadians(11);

    /** How far it snaps forward on release. */
    private static final double RELEASE_LEAN = Math.toRadians(-15);

    /** Strength of the halo behind Preah Ream. Subtle by design. */
    private static final float RIM_LIGHT_ALPHA = 0.18f;

    private static final int LOCK_CHIP_HEIGHT = 48;
    private static final int LOCK_CHIP_MIN_WIDTH = 180;

    /** Gap between the lock chip and the bottom of the play area. */
    private static final int LOCK_CHIP_MARGIN = 10;

    /** How fast the lock chip fades in and out, per tick. */
    private static final float LOCK_FADE_STEP = 0.14f;

    private final GameState state;
    private final SpriteCache sprites = new SpriteCache();
    private final HUDRenderer hud;

    private final Font wordFont;
    private final Font boltFont;
    private final Font lockFont;

    /** Set by Main each tick so the HUD can show the restart prompt. */
    private boolean restartArmed;

    /** Eased 0-1 visibility of the lock chip. */
    private float lockChipAlpha;

    /**
     * The last target seen locked. Held after the lock clears so the chip has
     * something to draw while it fades out.
     */
    private WordTarget lastLockedTarget;

    public GamePanel(GameState state) {
        this.state = state;
        Language language = state.getLanguage();
        this.hud = new HUDRenderer(language);
        this.wordFont = FontManager.wordFont(language, 20, Font.BOLD);
        this.boltFont = FontManager.wordFont(language, 17, Font.BOLD);
        this.lockFont = FontManager.wordFont(language, 26, Font.BOLD);

        setPreferredSize(new Dimension(GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT));
        setBackground(COLOR_SKY_TOP);
        setDoubleBuffered(true);
        setFocusable(false);
    }

    public void setRestartArmed(boolean restartArmed) {
        this.restartArmed = restartArmed;
    }

    /**
     * Advances render-only animation. Called once per game tick rather than per
     * paint, so the fade runs at a fixed rate and pauses when the game does.
     */
    public void tick() {
        WordTarget locked = state.getResolver().getLockedTarget();
        if (locked != null) {
            lastLockedTarget = locked;
            lockChipAlpha = Math.min(1f, lockChipAlpha + LOCK_FADE_STEP);
        } else {
            lockChipAlpha = Math.max(0f, lockChipAlpha - LOCK_FADE_STEP);
            if (lockChipAlpha <= 0f) {
                lastLockedTarget = null;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);

            drawBackdrop(g2);

            String typed = state.getResolver().getValidBuffer();
            List<WordTarget> highlighted = state.getResolver().getHighlighted();
            WordTarget locked = state.getResolver().getLockedTarget();

            drawEffects(g2, VisualEffect.Kind.SPAWN_POOF);

            // Painter's algorithm: things further from the temple are higher on
            // screen, so sorting by Y makes near monsters overlap far ones.
            List<Enemy> ordered = new ArrayList<>(state.getEnemies());
            ordered.sort(Comparator.comparingDouble(Enemy::getAnchorY));
            for (Enemy enemy : ordered) {
                drawEnemy(g2, enemy, typed, highlighted.contains(enemy), locked == enemy);
            }

            drawPlayer(g2, state.getPlayer());

            for (Projectile projectile : state.getProjectiles()) {
                drawProjectile(g2, projectile, typed,
                        highlighted.contains(projectile), locked == projectile);
            }

            drawEffects(g2, VisualEffect.Kind.ARROW);
            drawEffects(g2, VisualEffect.Kind.IMPACT);

            if (!state.isGameOver()) {
                drawLockChip(g2);
            }

            hud.draw(g2, state, restartArmed);
        } finally {
            g2.dispose();
        }
    }

    private void drawBackdrop(Graphics2D g2) {
        BufferedImage bg = sprites.background();
        if (bg != null) {
            g2.drawImage(bg, 0, 0, GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT, null);
            return;
        }
        g2.setPaint(new GradientPaint(
                0, 0, COLOR_SKY_TOP, 0, GameConfig.GROUND_LINE_Y, COLOR_SKY_BOTTOM));
        g2.fillRect(0, 0, GameConfig.SCREEN_WIDTH, GameConfig.GROUND_LINE_Y);
        g2.setColor(COLOR_GROUND);
        g2.fillRect(0, GameConfig.GROUND_LINE_Y - 40,
                GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT);
    }

    // ---- enemies -----------------------------------------------------------

    private void drawEnemy(Graphics2D g2, Enemy enemy, String typed,
                           boolean isCandidate, boolean isLocked) {

        EnemyType type = enemy.getType();
        BufferedImage sprite = sprites.sprite(type);

        double depth = enemy.depthScale();

        double scale = depth;
        float alpha = 1.0f;
        if (!enemy.isActive()) {
            double t = Math.min(1.0,
                    enemy.getDefeatTicks() / (double) GameConfig.DEFEAT_ANIMATION_TICKS);
            scale = depth * (1.0 - 0.35 * t);
            alpha = (float) Math.max(0.0, 1.0 - t);
        }

        int drawH = Math.max(1, (int) Math.round(type.getTargetHeight() * scale));
        int drawW = Math.max(1, (int) Math.round(sprites.widthFor(type) * scale));

        double bob = 0;
        int topY;
        int feetY;
        if (type.isGrounded()) {
            feetY = (int) Math.round(enemy.getAnchorY());
            topY = feetY - drawH;
        } else {
            double phase = enemy.getTicksAlive() * BOB_FREQUENCY * type.getSpeedMultiplier();
            bob = Math.sin(phase) * BOB_AMPLITUDE;
            int centerY = (int) Math.round(enemy.getAnchorY() + bob);
            topY = centerY - drawH / 2;
            feetY = centerY + drawH / 2;
        }

        int cx = (int) Math.round(enemy.getX());

        Graphics2D eg = (Graphics2D) g2.create();
        try {
            eg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

            drawContactShadow(eg, type, cx, feetY, drawW, bob);

            if (isLocked) {
                drawLockGlow(eg, cx, topY, drawW, drawH);
            }

            // Attack lean is applied around the feet so a grounded monster
            // pivots on the ground rather than floating as it winds up.
            AffineTransform saved = eg.getTransform();
            applyAttackLean(eg, enemy, cx, feetY);

            if (sprite != null) {
                drawSprite(eg, sprite, enemy.getDirection(), cx, topY, drawW, drawH);
            } else {
                drawPlaceholder(eg, cx, topY, drawW, drawH, isCandidate, isLocked);
            }

            if (enemy.getHitFlashTicks() > 0) {
                drawHitFlash(eg, enemy, cx, topY, drawW, drawH, alpha);
            }

            eg.setTransform(saved);

            if (enemy.getAttackPhase() == AttackPhase.WINDUP) {
                drawThrowTelegraph(eg, enemy, drawH);
            }

            drawWord(eg, enemy.getWord(), typed, isCandidate, cx, topY - 14, wordFont);
        } finally {
            eg.dispose();
        }
    }

    /**
     * Rotates and shifts a throwing enemy through its windup and release.
     *
     * <p>This is the whole "throw" illusion on a single static image:
     * anticipation (lean back, drift away from the target) followed by a fast
     * snap forward. Timing carries the read, not drawn poses — so when real
     * throw art arrives it slots into the same phases unchanged.
     */
    private void applyAttackLean(Graphics2D g2, Enemy enemy, int pivotX, int pivotY) {
        AttackPhase phase = enemy.getAttackPhase();
        if (phase == AttackPhase.NONE) {
            return;
        }
        double t = enemy.getAttackPhaseProgress();
        int facing = enemy.getDirection();

        double lean;
        double shift;
        switch (phase) {
            case WINDUP -> {
                double eased = t * t;
                lean = -facing * WINDUP_LEAN * eased;
                shift = -facing * 9 * eased;
            }
            case RELEASE -> {
                lean = -facing * (WINDUP_LEAN + (RELEASE_LEAN - WINDUP_LEAN) * t);
                shift = facing * 12 * t;
            }
            case RECOVER -> {
                double remaining = 1.0 - t;
                lean = -facing * RELEASE_LEAN * remaining;
                shift = facing * 12 * remaining;
            }
            default -> {
                return;
            }
        }

        g2.translate(pivotX + shift, pivotY);
        g2.rotate(lean);
        g2.translate(-pivotX, -pivotY);
    }

    /** Warning glow while an enemy winds up, so the throw is never a surprise. */
    private void drawThrowTelegraph(Graphics2D g2, Enemy enemy, int drawH) {
        double t = enemy.getAttackPhaseProgress();
        Graphics2D tg = (Graphics2D) g2.create();
        try {
            tg.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, (float) (0.25 + 0.45 * t)));
            tg.setColor(COLOR_TELEGRAPH);
            double radius = 10 + 8 * t;
            tg.fill(new Ellipse2D.Double(
                    enemy.getThrowOriginX() - radius,
                    enemy.getThrowOriginY() - radius,
                    radius * 2, radius * 2));
        } finally {
            tg.dispose();
        }
    }

    private void drawSprite(Graphics2D g2, BufferedImage sprite, int facing,
                            int cx, int topY, int drawW, int drawH) {
        if (facing < 0) {
            g2.drawImage(sprite,
                    cx + drawW / 2, topY, cx - drawW / 2, topY + drawH,
                    0, 0, sprite.getWidth(), sprite.getHeight(), null);
        } else {
            g2.drawImage(sprite, cx - drawW / 2, topY, drawW, drawH, null);
        }
    }

    private void drawPlaceholder(Graphics2D g2, int cx, int topY, int drawW, int drawH,
                                 boolean isCandidate, boolean isLocked) {
        g2.setColor(COLOR_PLACEHOLDER);
        g2.fill(new RoundRectangle2D.Double(cx - drawW / 2.0, topY, drawW, drawH, 18, 18));
        g2.setColor(isLocked ? COLOR_LOCKED_GLOW : COLOR_PLACEHOLDER_EDGE);
        g2.setStroke(new BasicStroke(isCandidate ? 3f : 1.5f));
        g2.draw(new RoundRectangle2D.Double(cx - drawW / 2.0, topY, drawW, drawH, 18, 18));
    }

    private void drawContactShadow(Graphics2D g2, EnemyType type,
                                   int cx, int feetY, int drawW, double bob) {
        double shrink = type.isGrounded() ? 1.0 : 1.0 - (bob / (BOB_AMPLITUDE * 3));
        int shadowW = Math.max(4, (int) Math.round(drawW * 0.6 * shrink));
        int shadowH = Math.max(3, (int) Math.round(shadowW * 0.24));

        g2.setColor(COLOR_SHADOW);
        g2.fill(new Ellipse2D.Double(
                cx - shadowW / 2.0, feetY - shadowH / 2.0, shadowW, shadowH));
    }

    private void drawLockGlow(Graphics2D g2, int cx, int topY, int drawW, int drawH) {
        Graphics2D glow = (Graphics2D) g2.create();
        try {
            glow.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
            glow.setColor(COLOR_LOCKED_GLOW);
            int pad = 16;
            glow.fill(new Ellipse2D.Double(
                    cx - drawW / 2.0 - pad, topY - pad, drawW + pad * 2, drawH + pad * 2));
        } finally {
            glow.dispose();
        }
    }

    private void drawHitFlash(Graphics2D g2, Enemy enemy,
                              int cx, int topY, int drawW, int drawH, float alpha) {
        Graphics2D flash = (Graphics2D) g2.create();
        try {
            flash.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, 0.4f * alpha));

            BufferedImage white = sprites.silhouette(enemy.getType());
            if (white != null) {
                drawSprite(flash, white, enemy.getDirection(), cx, topY, drawW, drawH);
            } else {
                flash.setColor(Color.WHITE);
                flash.fill(new RoundRectangle2D.Double(
                        cx - drawW / 2.0, topY, drawW, drawH, 18, 18));
            }
        } finally {
            flash.dispose();
        }
    }

    // ---- player ------------------------------------------------------------

    private void drawPlayer(Graphics2D g2, Player player) {
        boolean firing = player.isFiring();
        BufferedImage sprite = sprites.player(firing);

        int height = GameConfig.PLAYER_HEIGHT;
        int width = sprites.playerWidth(firing, height);
        int cx = (int) Math.round(player.getX());
        int feetY = (int) Math.round(player.getFeetY() + player.getRecoil() * 0.4);
        int topY = feetY - height;

        g2.setColor(COLOR_SHADOW);
        g2.fill(new Ellipse2D.Double(
                cx - width * 0.3, feetY - 9, width * 0.6, width * 0.16));

        drawPlayerRimLight(g2, firing, cx, topY, width, height);

        if (sprite != null) {
            g2.drawImage(sprite, cx - width / 2, topY, width, height, null);
        } else {
            g2.setColor(COLOR_PLACEHOLDER);
            g2.fill(new RoundRectangle2D.Double(
                    cx - width / 2.0, topY, width, height, 20, 20));
            g2.setColor(COLOR_TYPED);
            g2.setStroke(new BasicStroke(2.5f));
            g2.draw(new RoundRectangle2D.Double(
                    cx - width / 2.0, topY, width, height, 20, 20));
        }
    }

    /**
     * Soft warm halo behind the hero.
     *
     * <p>Purely for separation — Preah Ream's dark red robe sits against a dark
     * temple silhouette and without this he merges into it. Kept low-alpha on
     * purpose: this is a rim light, not a spotlight, and pushing it further
     * makes him look like he is on fire rather than lit from behind.
     */
    private void drawPlayerRimLight(Graphics2D g2, boolean firing,
                                    int cx, int topY, int width, int height) {
        BufferedImage glow = sprites.playerGlow(firing, height);
        if (glow == null) {
            return;
        }
        int pad = SpriteCache.GLOW_RADIUS * 3;

        Graphics2D rim = (Graphics2D) g2.create();
        try {
            rim.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, RIM_LIGHT_ALPHA));
            rim.drawImage(glow, cx - width / 2 - pad, topY - pad, null);
        } finally {
            rim.dispose();
        }
    }

    // ---- target lock -------------------------------------------------------

    /**
     * Confirms which enemy the prefix matcher has locked onto, and what is left
     * to type.
     *
     * <p>This exists because prefix matching is ambiguous by design: in a
     * crowded level several enemies light up at once and the moment the target
     * narrows to one is otherwise invisible. Without this chip the player has to
     * infer the lock from the highlight, which is unreliable when monsters
     * overlap.
     */
    private void drawLockChip(Graphics2D g2) {
        if (lockChipAlpha <= 0.01f) {
            return;
        }
        WordTarget locked = lastLockedTarget;
        if (locked == null) {
            return;
        }

        String word = locked.getWord();
        String typed = state.getResolver().getValidBuffer();
        String remaining = (typed != null && word.startsWith(typed))
                ? word.substring(typed.length())
                : word;

        Graphics2D cg = (Graphics2D) g2.create();
        try {
            cg.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, lockChipAlpha));

            cg.setFont(lockFont);
            FontMetrics fm = cg.getFontMetrics();
            int textWidth = fm.stringWidth(remaining);

            int iconSize = LOCK_CHIP_HEIGHT - 16;
            int chipWidth = Math.max(LOCK_CHIP_MIN_WIDTH,
                    iconSize + 14 + textWidth + 36);
            int chipX = (GameConfig.SCREEN_WIDTH - chipWidth) / 2;
            int chipY = GameConfig.SCREEN_HEIGHT - LOCK_CHIP_HEIGHT - LOCK_CHIP_MARGIN;

            RoundRectangle2D chip = new RoundRectangle2D.Double(
                    chipX, chipY, chipWidth, LOCK_CHIP_HEIGHT, 12, 12);
            cg.setColor(Palette.HUD_BG);
            cg.fill(chip);
            cg.setColor(Palette.HUD_DIVIDER);
            cg.setStroke(new BasicStroke(1.4f));
            cg.draw(chip);

            drawLockIcon(cg, locked, chipX + 12, chipY + 8, iconSize);

            cg.setFont(lockFont);
            cg.setColor(Palette.HUD_TEXT_GOLD);
            cg.drawString(remaining,
                    chipX + 12 + iconSize + 14,
                    chipY + LOCK_CHIP_HEIGHT / 2 + fm.getAscent() / 2 - 2);
        } finally {
            cg.dispose();
        }
    }

    /** Mini portrait of the locked target, so the chip identifies <em>which</em>. */
    private void drawLockIcon(Graphics2D g2, WordTarget locked, int x, int y, int size) {
        if (locked instanceof Enemy enemy) {
            BufferedImage sprite = sprites.sprite(enemy.getType());
            if (sprite != null) {
                double aspect = sprite.getWidth() / (double) sprite.getHeight();
                int w = (int) Math.round(size * aspect);
                g2.drawImage(sprite, x + (size - w) / 2, y, w, size, null);
                return;
            }
            g2.setColor(COLOR_PLACEHOLDER_EDGE);
            g2.fill(new RoundRectangle2D.Double(x, y, size, size, 6, 6));
            return;
        }
        // Projectiles get the bolt glyph rather than a monster portrait.
        g2.setColor(COLOR_BOLT_EDGE);
        g2.fill(new Ellipse2D.Double(x + size * 0.1, y + size * 0.1,
                size * 0.8, size * 0.8));
        g2.setColor(COLOR_BOLT_CORE);
        g2.fill(new Ellipse2D.Double(x + size * 0.3, y + size * 0.3,
                size * 0.4, size * 0.4));
    }

    // ---- projectiles -------------------------------------------------------

    private void drawProjectile(Graphics2D g2, Projectile projectile, String typed,
                                boolean isCandidate, boolean isLocked) {
        double x = projectile.getX();
        double y = projectile.getY();

        float alpha = 1.0f;
        double scale = 1.0;
        if (!projectile.isActive()) {
            double t = Math.min(1.0,
                    projectile.getDefeatTicks() / (double) GameConfig.DEFEAT_ANIMATION_TICKS);
            alpha = (float) Math.max(0, 1.0 - t);
            scale = 1.0 + t * 1.4;
        }

        Graphics2D pg = (Graphics2D) g2.create();
        try {
            pg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

            double radius = 15 * scale;

            // Trailing wisp so the bolt reads as moving, not hanging.
            pg.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, alpha * 0.28f));
            pg.setColor(COLOR_BOLT_EDGE);
            double heading = projectile.getHeading();
            for (int i = 1; i <= 3; i++) {
                double trail = radius * (1.0 - i * 0.22);
                double tx = x - Math.cos(heading) * i * 13;
                double ty = y - Math.sin(heading) * i * 13;
                pg.fill(new Ellipse2D.Double(tx - trail, ty - trail, trail * 2, trail * 2));
            }

            pg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            if (isLocked) {
                pg.setColor(COLOR_LOCKED_GLOW);
                pg.fill(new Ellipse2D.Double(
                        x - radius - 8, y - radius - 8,
                        (radius + 8) * 2, (radius + 8) * 2));
            }
            pg.setColor(COLOR_BOLT_EDGE);
            pg.fill(new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2));
            pg.setColor(COLOR_BOLT_CORE);
            pg.fill(new Ellipse2D.Double(
                    x - radius * 0.5, y - radius * 0.5, radius, radius));

            if (projectile.getHitFlashTicks() > 0) {
                pg.setColor(Color.WHITE);
                pg.fill(new Ellipse2D.Double(
                        x - radius * 0.7, y - radius * 0.7, radius * 1.4, radius * 1.4));
            }

            if (projectile.isActive()) {
                drawWord(pg, projectile.getWord(), typed, isCandidate,
                        (int) Math.round(x), (int) Math.round(y - radius - 12), boltFont);
            }
        } finally {
            pg.dispose();
        }
    }

    // ---- effects -----------------------------------------------------------

    private void drawEffects(Graphics2D g2, VisualEffect.Kind kind) {
        for (VisualEffect effect : state.getEffects()) {
            if (effect.getKind() != kind) {
                continue;
            }
            switch (kind) {
                case SPAWN_POOF -> drawPoof(g2, effect);
                case ARROW -> drawArrow(g2, effect);
                case IMPACT -> drawImpact(g2, effect);
            }
        }
    }

    /** Expanding, fading smoke cloud built from offset puffs. */
    private void drawPoof(Graphics2D g2, VisualEffect effect) {
        double t = effect.getProgress();
        float alpha = (float) Math.max(0, 0.75 * (1.0 - t));
        double spread = 30 * effect.getScale() * (0.4 + t * 1.5);

        Graphics2D pg = (Graphics2D) g2.create();
        try {
            pg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            pg.setColor(COLOR_POOF);

            double[][] puffs = {
                    {0, 0, 1.0}, {-0.8, 0.15, 0.72}, {0.8, 0.1, 0.72},
                    {-0.4, -0.55, 0.6}, {0.45, -0.5, 0.62}, {0, 0.45, 0.55}
            };
            for (double[] puff : puffs) {
                double r = spread * puff[2] * (0.55 + t * 0.5);
                double px = effect.getX() + puff[0] * spread;
                double py = effect.getY() + puff[1] * spread - t * 14;
                pg.fill(new Ellipse2D.Double(px - r, py - r, r * 2, r * 2));
            }
        } finally {
            pg.dispose();
        }
    }

    private void drawArrow(Graphics2D g2, VisualEffect effect) {
        Graphics2D ag = (Graphics2D) g2.create();
        try {
            double x = effect.getX();
            double y = effect.getY();
            ag.translate(x, y);
            ag.rotate(effect.getHeading());

            ag.setColor(COLOR_ARROW);
            ag.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            ag.drawLine(-16, 0, 12, 0);
            // Arrowhead
            ag.drawLine(12, 0, 4, -5);
            ag.drawLine(12, 0, 4, 5);
            // Fletching
            ag.setStroke(new BasicStroke(2f));
            ag.drawLine(-16, 0, -21, -4);
            ag.drawLine(-16, 0, -21, 4);

            ag.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
            ag.setStroke(new BasicStroke(1.2f));
            ag.drawLine(-40, 0, -18, 0);
        } finally {
            ag.dispose();
        }
    }

    private void drawImpact(Graphics2D g2, VisualEffect effect) {
        double t = effect.getProgress();
        // Only bloom for the back half of the lifetime — the front half is the
        // arrow still travelling toward this point.
        if (t < 0.5) {
            return;
        }
        double local = (t - 0.5) / 0.5;
        float alpha = (float) Math.max(0, 0.8 * (1.0 - local));
        double radius = 8 + local * 26;

        Graphics2D ig = (Graphics2D) g2.create();
        try {
            ig.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            ig.setColor(COLOR_BOLT_CORE);
            ig.setStroke(new BasicStroke(2.4f));
            ig.draw(new Ellipse2D.Double(
                    effect.getX() - radius, effect.getY() - radius, radius * 2, radius * 2));
        } finally {
            ig.dispose();
        }
    }

    // ---- words -------------------------------------------------------------

    private void drawWord(Graphics2D g2, String word, String typed,
                          boolean isCandidate, int centerX, int baselineY, Font font) {

        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();

        int matched = (isCandidate && typed != null && word.startsWith(typed))
                ? typed.length()
                : 0;

        String head = word.substring(0, matched);
        String tail = word.substring(matched);

        int totalWidth = fm.stringWidth(word);
        int x = centerX - totalWidth / 2;

        g2.setColor(COLOR_WORD_BG);
        g2.fill(new RoundRectangle2D.Double(
                x - 10, baselineY - fm.getAscent() - 5,
                totalWidth + 20, fm.getHeight() + 8, 10, 10));

        if (!head.isEmpty()) {
            g2.setColor(font == boltFont ? COLOR_BOLT_WORD : COLOR_TYPED);
            g2.drawString(head, x, baselineY);
            x += fm.stringWidth(head);
        }
        g2.setColor(COLOR_TEXT);
        g2.drawString(tail, x, baselineY);
    }
}
