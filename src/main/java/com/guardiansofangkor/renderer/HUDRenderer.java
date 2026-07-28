package com.guardiansofangkor.renderer;

import com.guardiansofangkor.engine.GameState;
import com.guardiansofangkor.engine.WaveManager;
import com.guardiansofangkor.i18n.FontManager;
import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.util.GameConfig;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Heads-up display and the game-over screen.
 *
 * <p>Design intent: the temple art is the star, so the HUD stays out of the
 * centre of the screen entirely. Stats sit in a bar across the top with the
 * level number given visual priority — it is the number that changes the game's
 * feel, so it reads first. Lives are lotus rosettes on the right, matching the
 * Khmer motif rather than generic hearts.
 *
 * <p>All type is set from one scale so proportions stay consistent: micro labels
 * for field names, a large weight for values, and a display weight for the level
 * and banners.
 *
 * <p>Reads {@link GameState} and paints. Contains no gameplay logic — it never
 * decides anything, it only reports what the engine already decided.
 */
public class HUDRenderer {

    // Palette pulled from the background art: temple gold, dusk purple, ember.
    private static final Color COLOR_BAR_TOP = new Color(0x0A, 0x06, 0x11, 236);
    private static final Color COLOR_BAR_BOTTOM = new Color(0x18, 0x0F, 0x26, 200);
    private static final Color COLOR_RULE = new Color(0xE8, 0xB9, 0x3B, 105);
    private static final Color COLOR_RULE_SOFT = new Color(0xE8, 0xB9, 0x3B, 45);
    private static final Color COLOR_LABEL = new Color(0xA8, 0x95, 0xC6);
    private static final Color COLOR_VALUE = new Color(0xF7, 0xF3, 0xFD);
    private static final Color COLOR_GOLD = new Color(0xE8, 0xB9, 0x3B);
    private static final Color COLOR_GOLD_DIM = new Color(0x97, 0x79, 0x2C);
    private static final Color COLOR_LIFE = new Color(0xE8, 0x6F, 0x5E);
    private static final Color COLOR_LIFE_CORE = new Color(0xFF, 0xC9, 0x6B);
    private static final Color COLOR_LIFE_LOST = new Color(0x33, 0x28, 0x3E);
    private static final Color COLOR_SCRIM = new Color(0x07, 0x04, 0x0C, 222);
    private static final Color COLOR_PANEL = new Color(0x12, 0x0C, 0x1C, 210);
    private static final Color COLOR_DANGER = new Color(0xE2, 0x5C, 0x66);

    /**
     * Height of the top stat bar. Sourced from GameConfig because the engine
     * needs the same number to keep spawns from painting behind the bar.
     */
    public static final int BAR_HEIGHT = GameConfig.HUD_BAR_HEIGHT;

    private static final int EDGE_PADDING = 30;

    private Font microFont;
    private Font valueFont;
    private Font levelFont;
    private Font bannerFont;
    private Font bodyFont;
    private Font titleFont;

    public HUDRenderer(Language language) {
        setLanguage(language);
    }

    /** Swaps fonts if the language changes mid-session. */
    public final void setLanguage(Language language) {
        this.microFont = FontManager.uiFont(language, 13, Font.BOLD);
        this.valueFont = FontManager.uiFont(language, 28, Font.BOLD);
        this.levelFont = FontManager.uiFont(language, 54, Font.BOLD);
        this.bannerFont = FontManager.uiFont(language, 44, Font.BOLD);
        this.titleFont = FontManager.uiFont(language, 52, Font.BOLD);
        this.bodyFont = FontManager.uiFont(language, 19, Font.PLAIN);
    }

    /**
     * @param restartArmed true while Tab has been pressed and Enter would restart
     */
    public void draw(Graphics2D g2, GameState state, boolean restartArmed) {
        drawTopBar(g2, state);
        drawLevelBanner(g2, state);
        if (state.isGameOver()) {
            drawGameOver(g2, state, restartArmed);
        } else if (restartArmed) {
            drawRestartPrompt(g2);
        }
    }

    // ---- top bar -----------------------------------------------------------

    private void drawTopBar(Graphics2D g2, GameState state) {
        g2.setPaint(new GradientPaint(0, 0, COLOR_BAR_TOP, 0, BAR_HEIGHT, COLOR_BAR_BOTTOM));
        g2.fillRect(0, 0, GameConfig.SCREEN_WIDTH, BAR_HEIGHT);

        g2.setColor(COLOR_RULE);
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(0, BAR_HEIGHT, GameConfig.SCREEN_WIDTH, BAR_HEIGHT);

        int x = drawLevelBlock(g2, state);

        x = drawStat(g2, "SCORE", Integer.toString(state.getScore()), x);
        x = drawStat(g2, "WPM", Integer.toString((int) Math.round(state.getWpm())), x);
        x = drawStat(g2, "ACCURACY",
                Math.round(state.getResolver().getAccuracy() * 100) + "%", x);
        x = drawStat(g2, "SLAIN", Integer.toString(state.getEnemiesDefeated()), x);
        drawStat(g2, "BEST", Integer.toString(state.getBestScore()), x);

        drawLives(g2, state);
    }

    /**
     * The level number gets its own oversized block with a divider — it is the
     * headline stat, so it should not compete with the others for attention.
     *
     * @return the x coordinate the remaining stats should start from
     */
    private int drawLevelBlock(Graphics2D g2, GameState state) {
        g2.setColor(COLOR_GOLD_DIM);
        g2.setFont(microFont);
        g2.drawString("LEVEL", EDGE_PADDING, 26);

        g2.setColor(COLOR_GOLD);
        g2.setFont(levelFont);
        String level = Integer.toString(Math.max(1, state.getLevel()));
        g2.drawString(level, EDGE_PADDING - 3, 70);

        int blockWidth = Math.max(
                g2.getFontMetrics(levelFont).stringWidth(level),
                g2.getFontMetrics(microFont).stringWidth("LEVEL"));

        int dividerX = EDGE_PADDING + blockWidth + 30;
        g2.setColor(COLOR_RULE_SOFT);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(dividerX, 18, dividerX, BAR_HEIGHT - 18);

        return dividerX + 32;
    }

    /** Draws one label/value pair and returns the x to continue from. */
    private int drawStat(Graphics2D g2, String label, String value, int x) {
        g2.setFont(microFont);
        g2.setColor(COLOR_LABEL);
        g2.drawString(label, x, 30);

        g2.setFont(valueFont);
        g2.setColor(COLOR_VALUE);
        g2.drawString(value, x, 64);

        int width = Math.max(
                g2.getFontMetrics(microFont).stringWidth(label),
                g2.getFontMetrics(valueFont).stringWidth(value));
        return x + width + 46;
    }

    private void drawLives(Graphics2D g2, GameState state) {
        int total = GameConfig.STARTING_LIVES;
        int size = 34;
        int gap = 12;
        int right = GameConfig.SCREEN_WIDTH - EDGE_PADDING;
        int firstX = right - (total * size) - ((total - 1) * gap);

        g2.setFont(microFont);
        g2.setColor(COLOR_LABEL);
        String label = "LIVES";
        int labelWidth = g2.getFontMetrics().stringWidth(label);
        g2.drawString(label, firstX - labelWidth - 18, BAR_HEIGHT / 2 + 5);

        int y = (BAR_HEIGHT - size) / 2;
        for (int i = 0; i < total; i++) {
            drawLotusPip(g2, firstX + i * (size + gap), y, size, i < state.getLives());
        }
    }

    /**
     * An eight-petal lotus rosette standing in for a heart.
     *
     * <p>Lost lives keep the full silhouette but drop to a flat dark fill, so the
     * player reads "three slots, one spent" rather than "two icons" — the count
     * stays legible at a glance without needing to count gaps.
     */
    private void drawLotusPip(Graphics2D g2, int x, int y, int size, boolean lit) {
        double half = size / 2.0;
        double cx = x + half;
        double cy = y + half;

        Color petal = lit ? COLOR_LIFE : COLOR_LIFE_LOST;

        // Four minor petals on the diagonals, drawn first so they sit behind.
        g2.setColor(lit ? petal.darker() : COLOR_LIFE_LOST);
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI / 4 + Math.PI / 2 * i;
            double px = cx + Math.cos(angle) * half * 0.44;
            double py = cy + Math.sin(angle) * half * 0.44;
            double r = half * 0.40;
            g2.fill(new Ellipse2D.Double(px - r, py - r, r * 2, r * 2));
        }

        // Four major petals on the axes.
        g2.setColor(petal);
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI / 2 * i;
            double px = cx + Math.cos(angle) * half * 0.46;
            double py = cy + Math.sin(angle) * half * 0.46;
            double r = half * 0.50;
            g2.fill(new Ellipse2D.Double(px - r, py - r, r * 2, r * 2));
        }

        if (lit) {
            // Gold seed pod at the centre, with a thin ring to lift it off the bar.
            g2.setColor(COLOR_LIFE_CORE);
            double r = half * 0.34;
            g2.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));

            g2.setColor(new Color(0xFF, 0xE3, 0xA8, 150));
            g2.setStroke(new BasicStroke(1.3f));
            g2.draw(new Ellipse2D.Double(cx - half * 0.9, cy - half * 0.9,
                    half * 1.8, half * 1.8));
        } else {
            g2.setColor(new Color(0x59, 0x48, 0x66, 140));
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(new Ellipse2D.Double(cx - half * 0.9, cy - half * 0.9,
                    half * 1.8, half * 1.8));
        }
    }

    // ---- banners -----------------------------------------------------------

    private void drawLevelBanner(Graphics2D g2, GameState state) {
        WaveManager waves = state.getWaveManager();
        if (!waves.isIntermission() || state.isGameOver()) {
            return;
        }
        String text = waves.getLevel() == 0
                ? "Defend the temple"
                : "Level " + waves.getLevel() + " cleared";
        String sub = waves.getLevel() == 0
                ? "Type the words above the spirits"
                : "Level " + (waves.getLevel() + 1) + " approaching";

        drawCenteredPlaque(g2, text, sub, COLOR_GOLD, GameConfig.SCREEN_HEIGHT / 2 - 70);
    }

    private void drawRestartPrompt(Graphics2D g2) {
        String text = "Press Enter to restart";
        g2.setFont(bodyFont);
        FontMetrics fm = g2.getFontMetrics();
        int width = fm.stringWidth(text);
        int x = (GameConfig.SCREEN_WIDTH - width) / 2;
        int y = BAR_HEIGHT + 52;

        g2.setColor(COLOR_PANEL);
        g2.fill(new RoundRectangle2D.Double(
                x - 22, y - fm.getAscent() - 10, width + 44, fm.getHeight() + 18, 12, 12));
        g2.setColor(COLOR_RULE);
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(new RoundRectangle2D.Double(
                x - 22, y - fm.getAscent() - 10, width + 44, fm.getHeight() + 18, 12, 12));
        g2.setColor(COLOR_GOLD);
        g2.drawString(text, x, y);
    }

    // ---- game over ---------------------------------------------------------

    private void drawGameOver(Graphics2D g2, GameState state, boolean restartArmed) {
        g2.setColor(COLOR_SCRIM);
        g2.fillRect(0, 0, GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT);

        final int centerX = GameConfig.SCREEN_WIDTH / 2;

        // The whole panel is laid out from one anchor so nothing drifts.
        final int panelWidth = 620;
        final int panelX = centerX - panelWidth / 2;
        final int panelY = 96;
        final int panelHeight = 500;

        g2.setColor(COLOR_PANEL);
        g2.fill(new RoundRectangle2D.Double(panelX, panelY, panelWidth, panelHeight, 20, 20));
        g2.setColor(COLOR_RULE_SOFT);
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(new RoundRectangle2D.Double(panelX, panelY, panelWidth, panelHeight, 20, 20));

        // --- title, centred on the panel ---
        int titleBaseline = panelY + 74;
        g2.setFont(titleFont);
        FontMetrics titleMetrics = g2.getFontMetrics();
        String title = "The temple has fallen";
        g2.setColor(COLOR_DANGER);
        g2.drawString(title, centerX - titleMetrics.stringWidth(title) / 2, titleBaseline);

        g2.setColor(COLOR_RULE);
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(centerX - 150, titleBaseline + 24, centerX + 150, titleBaseline + 24);

        // --- stat grid: two equal columns, symmetric about the centre ---
        final int columnWidth = 210;
        final int columnGap = 70;
        final int leftCol = centerX - columnGap / 2 - columnWidth;
        final int rightCol = centerX + columnGap / 2;
        final int gridTop = titleBaseline + 82;
        final int rowHeight = 76;

        drawGameOverStat(g2, "LEVEL REACHED",
                Integer.toString(Math.max(1, state.getLevel())), leftCol, gridTop);
        drawGameOverStat(g2, "FINAL SCORE",
                Integer.toString(state.getScore()), rightCol, gridTop);
        drawGameOverStat(g2, "SPIRITS SLAIN",
                Integer.toString(state.getEnemiesDefeated()), leftCol, gridTop + rowHeight);
        drawGameOverStat(g2, "BOLTS INTERCEPTED",
                Integer.toString(state.getProjectilesIntercepted()),
                rightCol, gridTop + rowHeight);
        drawGameOverStat(g2, "WORDS PER MINUTE",
                Integer.toString((int) Math.round(state.getWpm())),
                leftCol, gridTop + rowHeight * 2);
        drawGameOverStat(g2, "ACCURACY",
                Math.round(state.getResolver().getAccuracy() * 100) + "%",
                rightCol, gridTop + rowHeight * 2);

        // --- personal best, centred ---
        int bestY = gridTop + rowHeight * 2 + 62;
        boolean newBest = state.getScore() >= state.getBestScore() && state.getScore() > 0;
        g2.setFont(bodyFont);
        String bestText = newBest
                ? "New personal best"
                : "Personal best  " + state.getBestScore()
                        + "   ·   Level " + Math.max(1, state.getBestLevel());
        FontMetrics bestMetrics = g2.getFontMetrics();
        g2.setColor(newBest ? COLOR_GOLD : COLOR_LABEL);
        g2.drawString(bestText, centerX - bestMetrics.stringWidth(bestText) / 2, bestY);

        // --- controls, below the panel so they never collide with the stats ---
        int controlsY = panelY + panelHeight + 46;
        if (restartArmed) {
            drawKeyHint(g2, centerX, controlsY, "ENTER", "restart now", COLOR_GOLD);
        } else {
            drawKeyHint(g2, centerX, controlsY, "TAB  then  ENTER", "play again", COLOR_GOLD);
        }
        drawKeyHint(g2, centerX, controlsY + 44, "ESC", "quit", COLOR_LABEL);
    }

    /** Label above value, both left-aligned to the column. */
    private void drawGameOverStat(Graphics2D g2, String label, String value, int x, int y) {
        g2.setFont(microFont);
        g2.setColor(COLOR_LABEL);
        g2.drawString(label, x, y);

        g2.setFont(valueFont);
        g2.setColor(COLOR_VALUE);
        g2.drawString(value, x, y + 34);
    }

    /** A key cap with a caption, the pair centred together on {@code centerX}. */
    private void drawKeyHint(Graphics2D g2, int centerX, int y,
                             String key, String caption, Color accent) {
        FontMetrics keyMetrics = g2.getFontMetrics(microFont);
        FontMetrics capMetrics = g2.getFontMetrics(bodyFont);

        int capHeight = 30;
        int keyWidth = keyMetrics.stringWidth(key) + 30;
        int capWidth = capMetrics.stringWidth(caption);
        int total = keyWidth + 16 + capWidth;
        int x = centerX - total / 2;

        // Vertically centre the cap on the text baseline.
        int capTop = y - capHeight / 2 - 5;

        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 38));
        g2.fill(new RoundRectangle2D.Double(x, capTop, keyWidth, capHeight, 9, 9));
        g2.setColor(accent);
        g2.setStroke(new BasicStroke(1.3f));
        g2.draw(new RoundRectangle2D.Double(x, capTop, keyWidth, capHeight, 9, 9));

        g2.setFont(microFont);
        g2.drawString(key, x + 15, capTop + capHeight / 2 + keyMetrics.getAscent() / 2 - 1);

        g2.setFont(bodyFont);
        g2.setColor(COLOR_LABEL);
        g2.drawString(caption, x + keyWidth + 16,
                capTop + capHeight / 2 + capMetrics.getAscent() / 2 - 1);
    }

    // ---- shared ------------------------------------------------------------

    private void drawCenteredPlaque(Graphics2D g2, String text, String sub,
                                    Color color, int y) {
        g2.setFont(bannerFont);
        FontMetrics fm = g2.getFontMetrics();
        int width = fm.stringWidth(text);

        g2.setFont(bodyFont);
        int subWidth = g2.getFontMetrics().stringWidth(sub);
        int plaqueWidth = Math.max(width, subWidth) + 90;
        int x = (GameConfig.SCREEN_WIDTH - plaqueWidth) / 2;

        RoundRectangle2D plaque = new RoundRectangle2D.Double(
                x, y - fm.getAscent() - 26, plaqueWidth, fm.getHeight() + 76, 18, 18);

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
        g2.setColor(COLOR_PANEL);
        g2.fill(plaque);
        g2.setComposite(AlphaComposite.SrcOver);

        g2.setColor(COLOR_RULE);
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(plaque);

        g2.setFont(bannerFont);
        g2.setColor(color);
        g2.drawString(text, (GameConfig.SCREEN_WIDTH - width) / 2, y);

        g2.setFont(bodyFont);
        g2.setColor(COLOR_LABEL);
        g2.drawString(sub, (GameConfig.SCREEN_WIDTH - subWidth) / 2, y + 34);
    }
}
