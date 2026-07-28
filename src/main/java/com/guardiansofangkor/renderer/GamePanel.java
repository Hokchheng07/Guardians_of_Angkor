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

    private static final Color COLOR_SKY_TOP = new Color(0x1A, 0x12, 0x2E);
    private static final Color COLOR_SKY_BOTTOM = new Color(0x6B, 0x3A, 0x2A);
    private static final Color COLOR_GROUND = new Color(0x22, 0x22, 0x17);
    private static final Color COLOR_PLACEHOLDER = new Color(0x4C, 0x3A, 0x63);
    private static final Color COLOR_PLACEHOLDER_EDGE = new Color(0x7A, 0x5F, 0x9B);
    private static final Color COLOR_TEXT = new Color(0xF3, 0xEE, 0xFA);
    private static final Color COLOR_TYPED = new Color(0xE8, 0xB9, 0x3B);
    private static final Color COLOR_LOCKED_GLOW = new Color(0xFF, 0xD9, 0x6B);
    private static final Color COLOR_WORD_BG = new Color(0x0D, 0x09, 0x14, 195);
    private static final Color COLOR_SHADOW = new Color(0x00, 0x00, 0x00, 110);
    private static final Color COLOR_POOF = new Color(0xCE, 0xC2, 0xD8);
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

    private final GameState state;
    private final SpriteCache sprites = new SpriteCache();
    private final HUDRenderer hud;

    private final Font wordFont;
    private final Font boltFont;

    /** Set by Main each tick so the HUD can show the restart prompt. */
    private boolean restartArmed;

    public GamePanel(GameState state) {
        this.state = state;
        Language language = state.getLanguage();
        this.hud = new HUDRenderer(language);
        this.wordFont = FontManager.wordFont(language, 20, Font.BOLD);
        this.boltFont = FontManager.wordFont(language, 17, Font.BOLD);

        setPreferredSize(new Dimension(GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT));
        setBackground(COLOR_SKY_TOP);
        setDoubleBuffered(true);
        setFocusable(false);
    }

    public void setRestartArmed(boolean restartArmed) {
        this.restartArmed = restartArmed;
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
