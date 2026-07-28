package com.guardiansofangkor.renderer;

import com.guardiansofangkor.engine.GameState;
import com.guardiansofangkor.entities.Enemy;
import com.guardiansofangkor.entities.EnemyType;
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
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * The drawing surface. Reads {@link GameState} and paints it — and does nothing
 * else. No gameplay decisions are made in this class: it never moves an enemy,
 * never scores a word, never mutates anything it reads.
 *
 * <p>Sprites are anchored by ground behaviour: grounded monsters have the bottom
 * of their artwork placed exactly on the ground line and never bob, so their feet
 * stay planted on the plaza. Floaters are centred on a hover height and bob on a
 * sine wave.
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
    private static final Color COLOR_WORD_BG = new Color(0x0D, 0x09, 0x14, 190);
    private static final Color COLOR_SHADOW = new Color(0x00, 0x00, 0x00, 110);
    private static final Color COLOR_BREACH = new Color(0xD9, 0x4F, 0x5C, 60);

    /** Vertical bob amplitude in pixels. Floaters only. */
    private static final double BOB_AMPLITUDE = 7.0;

    /** Bob cycles per tick — multiplied by each type's speed for variety. */
    private static final double BOB_FREQUENCY = 0.07;

    private final GameState state;
    private final SpriteCache sprites = new SpriteCache();
    private final HUDRenderer hud;

    private Font wordFont;

    public GamePanel(GameState state) {
        this.state = state;
        Language language = state.getLanguage();
        this.hud = new HUDRenderer(language);
        this.wordFont = FontManager.wordFont(language, 20, Font.BOLD);

        setPreferredSize(new Dimension(GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT));
        setBackground(COLOR_SKY_TOP);
        setDoubleBuffered(true);
        setFocusable(false);
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
            drawBreachZone(g2);

            String typed = state.getResolver().getValidBuffer();
            List<WordTarget> highlighted = state.getResolver().getHighlighted();
            WordTarget locked = state.getResolver().getLockedTarget();

            // Draw far-to-near so bigger monsters overlap smaller ones sensibly.
            List<Enemy> enemies = state.getEnemies();
            for (Enemy enemy : enemies) {
                drawEnemy(g2, enemy, typed, highlighted.contains(enemy), locked == enemy);
            }

            hud.draw(g2, state);
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
        // Painted fallback so the game still reads correctly without the art.
        g2.setPaint(new GradientPaint(
                0, 0, COLOR_SKY_TOP, 0, GameConfig.GROUND_LINE_Y, COLOR_SKY_BOTTOM));
        g2.fillRect(0, 0, GameConfig.SCREEN_WIDTH, GameConfig.GROUND_LINE_Y);
        g2.setColor(COLOR_GROUND);
        g2.fillRect(0, GameConfig.GROUND_LINE_Y - 40,
                GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT);
    }

    /** Subtle marker showing where enemies breach, so the stakes are legible. */
    private void drawBreachZone(Graphics2D g2) {
        int width = GameConfig.BREACH_RADIUS * 2;
        g2.setColor(COLOR_BREACH);
        g2.fillRect(GameConfig.TEMPLE_CENTER_X - GameConfig.BREACH_RADIUS,
                GameConfig.GROUND_LINE_Y - 150, width, 165);
    }

    private void drawEnemy(Graphics2D g2, Enemy enemy, String typed,
                           boolean isCandidate, boolean isLocked) {

        EnemyType type = enemy.getType();
        BufferedImage sprite = sprites.sprite(type);

        int baseHeight = type.getTargetHeight();
        int baseWidth = sprites.widthFor(type);

        // --- defeat scale + fade -------------------------------------------
        double scale = 1.0;
        float alpha = 1.0f;
        if (!enemy.isActive()) {
            double t = Math.min(1.0,
                    enemy.getDefeatTicks() / (double) GameConfig.DEFEAT_ANIMATION_TICKS);
            scale = 1.0 - (0.35 * t);
            alpha = (float) Math.max(0.0, 1.0 - t);
        }

        int drawW = Math.max(1, (int) Math.round(baseWidth * scale));
        int drawH = Math.max(1, (int) Math.round(baseHeight * scale));

        // --- anchoring ------------------------------------------------------
        // Grounded: bottom of the artwork sits on the ground line, no bobbing.
        // Floating: centred on the hover height, bobbing on a sine wave.
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
            feetY = GameConfig.GROUND_LINE_Y;
        }

        int cx = (int) Math.round(enemy.getX());

        Graphics2D eg = (Graphics2D) g2.create();
        try {
            eg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

            drawContactShadow(eg, type, cx, feetY, drawW, bob);

            if (isLocked) {
                drawLockGlow(eg, cx, topY, drawW, drawH);
            }

            if (sprite != null) {
                drawSprite(eg, sprite, enemy, cx, topY, drawW, drawH);
            } else {
                drawPlaceholder(eg, cx, topY, drawW, drawH, isCandidate, isLocked);
            }

            if (enemy.getHitFlashTicks() > 0) {
                drawHitFlash(eg, enemy, cx, topY, drawW, drawH, alpha);
            }

            drawWord(eg, enemy.getWord(), typed, isCandidate, cx, topY - 14);
        } finally {
            eg.dispose();
        }
    }

    private void drawSprite(Graphics2D g2, BufferedImage sprite, Enemy enemy,
                            int cx, int topY, int drawW, int drawH) {
        // Sprites are drawn facing their direction of travel. The source art
        // faces the viewer, so mirroring on one side reads as "turning around".
        if (enemy.getDirection() < 0) {
            g2.drawImage(sprite,
                    cx + drawW / 2, topY, cx - drawW / 2, topY + drawH,
                    0, 0, sprite.getWidth(), sprite.getHeight(), null);
        } else {
            g2.drawImage(sprite, cx - drawW / 2, topY, drawW, drawH, null);
        }
    }

    /** Placeholder body for roster entries whose art has not arrived yet. */
    private void drawPlaceholder(Graphics2D g2, int cx, int topY, int drawW, int drawH,
                                 boolean isCandidate, boolean isLocked) {
        g2.setColor(COLOR_PLACEHOLDER);
        g2.fill(new RoundRectangle2D.Double(cx - drawW / 2.0, topY, drawW, drawH, 18, 18));
        g2.setColor(isLocked ? COLOR_LOCKED_GLOW : COLOR_PLACEHOLDER_EDGE);
        g2.setStroke(new BasicStroke(isCandidate ? 3f : 1.5f));
        g2.draw(new RoundRectangle2D.Double(cx - drawW / 2.0, topY, drawW, drawH, 18, 18));
    }

    /**
     * Ellipse under the sprite. For floaters it shrinks as they rise, which is
     * what sells that they are airborne rather than badly positioned.
     */
    private void drawContactShadow(Graphics2D g2, EnemyType type,
                                   int cx, int feetY, int drawW, double bob) {
        double shrink = type.isGrounded() ? 1.0 : 1.0 - (bob / (BOB_AMPLITUDE * 3));
        int shadowW = Math.max(4, (int) Math.round(drawW * 0.6 * shrink));
        int shadowH = Math.max(3, (int) Math.round(shadowW * 0.24));

        g2.setColor(COLOR_SHADOW);
        g2.fill(new Ellipse2D.Double(
                cx - shadowW / 2.0, feetY - shadowH / 2.0, shadowW, shadowH));
    }

    /** Soft halo behind the locked target so it is unmistakable. */
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

    /**
     * White wash on a correct keystroke. Drawn through the sprite's own alpha so
     * the flash follows the monster's silhouette instead of a rectangle.
     */
    private void drawHitFlash(Graphics2D g2, Enemy enemy,
                              int cx, int topY, int drawW, int drawH, float alpha) {
        Graphics2D flash = (Graphics2D) g2.create();
        try {
            flash.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, 0.4f * alpha));

            BufferedImage white = sprites.silhouette(enemy.getType());
            if (white != null) {
                drawSprite(flash, white, enemy, cx, topY, drawW, drawH);
            } else {
                flash.setColor(Color.WHITE);
                flash.fill(new RoundRectangle2D.Double(
                        cx - drawW / 2.0, topY, drawW, drawH, 18, 18));
            }
        } finally {
            flash.dispose();
        }
    }

    /**
     * Draws the word above an enemy, colouring the already-typed prefix
     * differently from the remainder so ambiguity is visible at a glance.
     */
    private void drawWord(Graphics2D g2, String word, String typed,
                          boolean isCandidate, int centerX, int baselineY) {

        g2.setFont(wordFont);
        FontMetrics fm = g2.getFontMetrics();

        int matched = (isCandidate && typed != null && word.startsWith(typed))
                ? typed.length()
                : 0;

        String head = word.substring(0, matched);
        String tail = word.substring(matched);

        int totalWidth = fm.stringWidth(word);
        int x = centerX - totalWidth / 2;

        // Plate behind the text so words stay readable over busy artwork.
        g2.setColor(COLOR_WORD_BG);
        g2.fill(new RoundRectangle2D.Double(
                x - 10, baselineY - fm.getAscent() - 5,
                totalWidth + 20, fm.getHeight() + 8, 10, 10));

        if (!head.isEmpty()) {
            g2.setColor(COLOR_TYPED);
            g2.drawString(head, x, baselineY);
            x += fm.stringWidth(head);
        }
        g2.setColor(COLOR_TEXT);
        g2.drawString(tail, x, baselineY);
    }
}
