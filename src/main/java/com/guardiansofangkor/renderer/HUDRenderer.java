package com.guardiansofangkor.renderer;

import com.guardiansofangkor.engine.GameState;
import com.guardiansofangkor.engine.WaveManager;
import com.guardiansofangkor.i18n.FontManager;
import com.guardiansofangkor.util.GameConfig;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Draws the heads-up display: score, WPM, accuracy, wave and lives, plus the
 * between-wave banner and the game-over panel.
 *
 * <p>Reads {@link GameState} and paints. Contains no gameplay logic — it never
 * decides anything, it only reports what the engine already decided.
 */
public class HUDRenderer {

    private static final Color COLOR_PANEL = new Color(0x0D, 0x09, 0x14, 170);
    private static final Color COLOR_LABEL = new Color(0xA9, 0x97, 0xC4);
    private static final Color COLOR_VALUE = new Color(0xF3, 0xEE, 0xFA);
    private static final Color COLOR_GOLD = new Color(0xE8, 0xB9, 0x3B);
    private static final Color COLOR_LIFE = new Color(0xD9, 0x4F, 0x5C);
    private static final Color COLOR_LIFE_LOST = new Color(0x4A, 0x3A, 0x52);
    private static final Color COLOR_BANNER = new Color(0x10, 0x0B, 0x18, 200);

    private static final int PANEL_HEIGHT = 46;
    private static final int PADDING = 18;

    private Font labelFont;
    private Font valueFont;
    private Font bannerFont;

    public HUDRenderer(com.guardiansofangkor.i18n.Language language) {
        this.labelFont = FontManager.uiFont(language, 12, Font.PLAIN);
        this.valueFont = FontManager.uiFont(language, 18, Font.BOLD);
        this.bannerFont = FontManager.uiFont(language, 34, Font.BOLD);
    }

    /** Swaps fonts if the language changes mid-session. */
    public void setLanguage(com.guardiansofangkor.i18n.Language language) {
        this.labelFont = FontManager.uiFont(language, 12, Font.PLAIN);
        this.valueFont = FontManager.uiFont(language, 18, Font.BOLD);
        this.bannerFont = FontManager.uiFont(language, 34, Font.BOLD);
    }

    public void draw(Graphics2D g2, GameState state) {
        drawTopPanel(g2, state);
        drawWaveBanner(g2, state);
        if (state.isGameOver()) {
            drawGameOver(g2, state);
        }
    }

    private void drawTopPanel(Graphics2D g2, GameState state) {
        g2.setColor(COLOR_PANEL);
        g2.fill(new RoundRectangle2D.Double(
                -12, -12, GameConfig.SCREEN_WIDTH + 24, PANEL_HEIGHT + 12, 16, 16));

        int x = PADDING;
        x = drawStat(g2, "SCORE", Integer.toString(state.getScore()), x);
        x = drawStat(g2, "WAVE", Integer.toString(state.getWave()), x);
        x = drawStat(g2, "WPM", Integer.toString((int) Math.round(state.getWpm())), x);
        x = drawStat(g2, "ACCURACY",
                Math.round(state.getResolver().getAccuracy() * 100) + "%", x);
        drawStat(g2, "BEST", Integer.toString(state.getBestScore()), x);

        drawLives(g2, state);
    }

    /** Draws one label/value pair and returns the x to continue from. */
    private int drawStat(Graphics2D g2, String label, String value, int x) {
        g2.setFont(labelFont);
        g2.setColor(COLOR_LABEL);
        g2.drawString(label, x, 16);

        g2.setFont(valueFont);
        g2.setColor(COLOR_VALUE);
        g2.drawString(value, x, 36);

        FontMetrics labelMetrics = g2.getFontMetrics(labelFont);
        FontMetrics valueMetrics = g2.getFontMetrics(valueFont);
        int width = Math.max(labelMetrics.stringWidth(label), valueMetrics.stringWidth(value));
        return x + width + 34;
    }

    private void drawLives(Graphics2D g2, GameState state) {
        int radius = 11;
        int gap = 8;
        int total = GameConfig.STARTING_LIVES;
        int right = GameConfig.SCREEN_WIDTH - PADDING;

        g2.setFont(labelFont);
        g2.setColor(COLOR_LABEL);
        String label = "LIVES";
        int labelWidth = g2.getFontMetrics().stringWidth(label);
        int firstX = right - (total * (radius + gap)) - labelWidth - 10;
        g2.drawString(label, firstX, 30);

        for (int i = 0; i < total; i++) {
            int cx = firstX + labelWidth + 14 + i * (radius + gap);
            g2.setColor(i < state.getLives() ? COLOR_LIFE : COLOR_LIFE_LOST);
            g2.fill(new Ellipse2D.Double(cx, 16, radius, radius));
        }
    }

    /** Between-wave announcement. */
    private void drawWaveBanner(Graphics2D g2, GameState state) {
        WaveManager waves = state.getWaveManager();
        if (!waves.isIntermission() || state.isGameOver()) {
            return;
        }
        String text = waves.getWave() == 0
                ? "Defend the temple"
                : "Wave " + waves.getWave() + " cleared";
        drawCenteredBanner(g2, text, COLOR_GOLD, GameConfig.SCREEN_HEIGHT / 2 - 40);
    }

    private void drawGameOver(Graphics2D g2, GameState state) {
        g2.setColor(COLOR_BANNER);
        g2.fillRect(0, 0, GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT);

        drawCenteredBanner(g2, "The temple has fallen", COLOR_LIFE,
                GameConfig.SCREEN_HEIGHT / 2 - 50);

        g2.setFont(valueFont);
        g2.setColor(COLOR_VALUE);
        String summary = "Reached wave " + state.getWave()
                + "   ·   " + state.getScore() + " points"
                + "   ·   " + (int) Math.round(state.getWpm()) + " WPM";
        int width = g2.getFontMetrics().stringWidth(summary);
        g2.drawString(summary,
                (GameConfig.SCREEN_WIDTH - width) / 2,
                GameConfig.SCREEN_HEIGHT / 2 + 10);
    }

    private void drawCenteredBanner(Graphics2D g2, String text, Color color, int y) {
        g2.setFont(bannerFont);
        FontMetrics fm = g2.getFontMetrics();
        int width = fm.stringWidth(text);
        int x = (GameConfig.SCREEN_WIDTH - width) / 2;

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f));
        g2.setColor(COLOR_BANNER);
        g2.fill(new RoundRectangle2D.Double(
                x - 28, y - fm.getAscent() - 14, width + 56, fm.getHeight() + 26, 14, 14));
        g2.setComposite(AlphaComposite.SrcOver);

        g2.setColor(color);
        g2.drawString(text, x, y);
    }
}
