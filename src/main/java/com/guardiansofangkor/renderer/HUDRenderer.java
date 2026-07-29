package com.guardiansofangkor.renderer;

import com.guardiansofangkor.engine.GameState;
import com.guardiansofangkor.engine.LevelPreview;
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
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Heads-up display and the game-over screen.
 *
 * <p>Design intent: the temple art is the star, so the HUD stays out of the
 * centre of the screen entirely. Colour is stone-dark and temple gold from
 * {@link Palette}, shared with the typing bar so the two frames read as one
 * system rather than two unrelated widgets.
 *
 * <p>The stat row is deliberately not five equal peers. LEVEL and SCORE carry
 * the display weight in gold; WPM, ACCURACY, SLAIN and BEST drop a full size
 * tier and sit in off-white with dimmed labels. Everything on the bar is
 * glanceable, but only two things are meant to be <em>read</em>.
 *
 * <p>Reads {@link GameState} and paints. Contains no gameplay logic — it never
 * decides anything, it only reports what the engine already decided.
 */
public class HUDRenderer {

    /**
     * Height of the top stat bar. Sourced from GameConfig because the engine
     * needs the same number to keep spawns from painting behind the bar.
     */
    public static final int BAR_HEIGHT = GameConfig.HUD_BAR_HEIGHT;

    /** Thin level-progress bar sitting directly under the divider. */
    private static final int PROGRESS_HEIGHT = 4;

    private static final int EDGE_PADDING = 30;

    /** Secondary labels sit below full strength so the row recedes. */
    private static final double SECONDARY_LABEL_ALPHA = 0.7;

    private Font microFont;
    private Font displayFont;
    private Font secondaryFont;
    private Font bannerFont;
    private Font bodyFont;
    private Font hintFont;
    private Font titleFont;

    public HUDRenderer(Language language) {
        setLanguage(language);
    }

    /** Swaps fonts if the language changes mid-session. */
    public final void setLanguage(Language language) {
        this.microFont = FontManager.uiFont(language, 13, Font.BOLD);
        this.displayFont = FontManager.uiFont(language, 50, Font.BOLD);
        this.secondaryFont = FontManager.uiFont(language, 24, Font.BOLD);
        this.bannerFont = FontManager.uiFont(language, 44, Font.BOLD);
        this.titleFont = FontManager.uiFont(language, 52, Font.BOLD);
        this.bodyFont = FontManager.uiFont(language, 19, Font.PLAIN);
        this.hintFont = FontManager.uiFont(language, 16, Font.PLAIN);
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
        g2.setPaint(new GradientPaint(
                0, 0, Palette.HUD_BG, 0, BAR_HEIGHT, Palette.HUD_BG_SOFT));
        g2.fillRect(0, 0, GameConfig.SCREEN_WIDTH, BAR_HEIGHT);

        g2.setColor(Palette.HUD_DIVIDER);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(0, BAR_HEIGHT, GameConfig.SCREEN_WIDTH, BAR_HEIGHT);

        drawProgressBar(g2, state);

        int x = drawLevelBlock(g2, state);

        // Primary: same weight as LEVEL, in gold.
        x = drawStat(g2, "SCORE", Integer.toString(state.getScore()),
                x, displayFont, Palette.HUD_TEXT_GOLD, 1.0);

        // Secondary row: a full tier smaller, off-white, dimmed labels.
        x = drawStat(g2, "WPM", Integer.toString((int) Math.round(state.getWpm())),
                x, secondaryFont, Palette.HUD_TEXT_WHITE, SECONDARY_LABEL_ALPHA);
        x = drawStat(g2, "ACCURACY",
                Math.round(state.getResolver().getAccuracy() * 100) + "%",
                x, secondaryFont, Palette.HUD_TEXT_WHITE, SECONDARY_LABEL_ALPHA);
        x = drawStat(g2, "SLAIN", Integer.toString(state.getEnemiesDefeated()),
                x, secondaryFont, Palette.HUD_TEXT_WHITE, SECONDARY_LABEL_ALPHA);
        drawStat(g2, "BEST", Integer.toString(state.getBestScore()),
                x, secondaryFont, Palette.HUD_TEXT_WHITE, SECONDARY_LABEL_ALPHA);

        drawLives(g2, state);
    }

    /**
     * Level progress, as a hairline under the divider.
     *
     * <p>Sized for peripheral vision — there is no number, because reading it
     * would cost the player the attention they need for the words.
     */
    private void drawProgressBar(Graphics2D g2, GameState state) {
        int y = BAR_HEIGHT + 1;
        int width = GameConfig.SCREEN_WIDTH;

        g2.setColor(Palette.PROGRESS_TRACK);
        g2.fillRect(0, y, width, PROGRESS_HEIGHT);

        int fill = (int) Math.round(width * state.getLevelProgress());
        if (fill > 0) {
            g2.setColor(Palette.PROGRESS_FILL);
            g2.fillRect(0, y, fill, PROGRESS_HEIGHT);
        }

        // Quarter milestones, notched out of the track.
        g2.setColor(Palette.alpha(Palette.HUD_BG, 0.85));
        for (int i = 1; i <= 3; i++) {
            int tickX = width * i / 4;
            g2.fillRect(tickX - 1, y, 2, PROGRESS_HEIGHT);
        }
    }

    /**
     * The level number gets its own block with a divider — it is the headline
     * stat, so it should not compete with the others for attention.
     *
     * @return the x coordinate the remaining stats should start from
     */
    private int drawLevelBlock(Graphics2D g2, GameState state) {
        g2.setColor(Palette.HUD_TEXT_DIM);
        g2.setFont(microFont);
        g2.drawString("LEVEL", EDGE_PADDING, 26);

        g2.setColor(Palette.HUD_TEXT_GOLD);
        g2.setFont(displayFont);
        String level = Integer.toString(Math.max(1, state.getLevel()));
        g2.drawString(level, EDGE_PADDING - 2, 68);

        int blockWidth = Math.max(
                g2.getFontMetrics(displayFont).stringWidth(level),
                g2.getFontMetrics(microFont).stringWidth("LEVEL"));

        int dividerX = EDGE_PADDING + blockWidth + 28;
        g2.setColor(Palette.HUD_DIVIDER_SOFT);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(dividerX, 18, dividerX, BAR_HEIGHT - 18);

        return dividerX + 30;
    }

    /**
     * Draws one label/value pair and returns the x to continue from.
     *
     * <p>Both the value font and the label opacity are parameters, because that
     * pair is exactly what separates a primary stat from a secondary one.
     */
    private int drawStat(Graphics2D g2, String label, String value, int x,
                         Font valueFont, Color valueColor, double labelAlpha) {
        g2.setFont(microFont);
        g2.setColor(Palette.alpha(Palette.HUD_TEXT_DIM, labelAlpha));
        g2.drawString(label, x, 26);

        g2.setFont(valueFont);
        g2.setColor(valueColor);
        // Shared baseline so the differently-sized values still sit on one line.
        g2.drawString(value, x, 68);

        int width = Math.max(
                g2.getFontMetrics(microFont).stringWidth(label),
                g2.getFontMetrics(valueFont).stringWidth(value));
        return x + width + 42;
    }

    private void drawLives(Graphics2D g2, GameState state) {
        int total = GameConfig.STARTING_LIVES;
        int size = 24;
        int gap = 12;
        int right = GameConfig.SCREEN_WIDTH - EDGE_PADDING;
        int firstX = right - (total * size) - ((total - 1) * gap);

        g2.setFont(microFont);
        g2.setColor(Palette.HUD_TEXT_DIM);
        String label = "LIVES";
        int labelWidth = g2.getFontMetrics().stringWidth(label);
        g2.drawString(label, firstX - labelWidth - 18, BAR_HEIGHT / 2 + 5);

        int y = (BAR_HEIGHT - size) / 2;
        for (int i = 0; i < total; i++) {
            drawLotusBud(g2, firstX + i * (size + gap), y, size, i < state.getLives());
        }
    }

    /**
     * A lotus-bud tower glyph — the same silhouette as the Angkor prangs in the
     * background art, which is why it reads as belonging here rather than as a
     * generic icon.
     *
     * <p>Held lives are solid gold; spent ones keep the full outline in dim
     * stone. Preserving the silhouette rather than removing the icon means the
     * player reads "three slots, one spent" without counting gaps.
     */
    private void drawLotusBud(Graphics2D g2, int x, int y, int size, boolean lit) {
        Path2D bud = budPath(x + size / 2.0, y + size * 0.94, size * 0.52, size * 0.80);
        Path2D left = budPath(x + size * 0.20, y + size * 0.96, size * 0.30, size * 0.48);
        Path2D right = budPath(x + size * 0.80, y + size * 0.96, size * 0.30, size * 0.48);

        if (lit) {
            g2.setColor(Palette.LIFE_FILLED);
            g2.fill(left);
            g2.fill(right);
            g2.fill(bud);

            // Plinth, so the towers sit on something.
            g2.fillRect((int) (x + size * 0.10), (int) (y + size * 0.90),
                    (int) (size * 0.80), Math.max(2, (int) (size * 0.09)));
        } else {
            g2.setColor(Palette.LIFE_LOST);
            g2.setStroke(new BasicStroke(1.4f));
            g2.draw(left);
            g2.draw(right);
            g2.draw(bud);
            g2.drawLine((int) (x + size * 0.10), (int) (y + size * 0.94),
                    (int) (x + size * 0.90), (int) (y + size * 0.94));
        }
    }

    /** One lotus-bud tower: pointed apex, swelling body, narrow waist. */
    private static Path2D budPath(double cx, double baseY, double width, double height) {
        double halfW = width / 2.0;
        double apexY = baseY - height;

        Path2D path = new Path2D.Double();
        path.moveTo(cx - halfW * 0.55, baseY);
        // Left flank: waist in, belly out, taper to the point.
        path.curveTo(
                cx - halfW * 1.0, baseY - height * 0.34,
                cx - halfW * 0.86, baseY - height * 0.70,
                cx, apexY);
        // Right flank, mirrored.
        path.curveTo(
                cx + halfW * 0.86, baseY - height * 0.70,
                cx + halfW * 1.0, baseY - height * 0.34,
                cx + halfW * 0.55, baseY);
        path.closePath();
        return path;
    }

    // ---- banners -----------------------------------------------------------

    private void drawLevelBanner(Graphics2D g2, GameState state) {
        WaveManager waves = state.getWaveManager();
        if (!waves.isIntermission() || state.isGameOver()) {
            return;
        }
        int next = waves.getLevel() + 1;

        String text = waves.getLevel() == 0
                ? "Defend the temple"
                : "Level " + waves.getLevel() + " cleared";
        String sub = waves.getLevel() == 0
                ? "Type the words above the spirits"
                : "Level " + next + " approaching";

        LevelPreview preview = LevelPreview.forLevel(next);
        String hint = preview == null ? null : preview.hint();

        drawCenteredPlaque(g2, text, sub, hint,
                Palette.HUD_TEXT_GOLD, GameConfig.SCREEN_HEIGHT / 2 - 70);
    }

    private void drawRestartPrompt(Graphics2D g2) {
        String text = "Press Enter to restart";
        g2.setFont(bodyFont);
        FontMetrics fm = g2.getFontMetrics();
        int width = fm.stringWidth(text);
        int x = (GameConfig.SCREEN_WIDTH - width) / 2;
        int y = BAR_HEIGHT + 62;

        RoundRectangle2D chip = new RoundRectangle2D.Double(
                x - 22, y - fm.getAscent() - 10, width + 44, fm.getHeight() + 18, 12, 12);

        g2.setColor(Palette.HUD_BG);
        g2.fill(chip);
        g2.setColor(Palette.HUD_DIVIDER);
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(chip);
        g2.setColor(Palette.HUD_TEXT_GOLD);
        g2.drawString(text, x, y);
    }

    // ---- game over ---------------------------------------------------------

    private void drawGameOver(Graphics2D g2, GameState state, boolean restartArmed) {
        g2.setColor(Palette.SCRIM);
        g2.fillRect(0, 0, GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT);

        final int centerX = GameConfig.SCREEN_WIDTH / 2;
        final int panelWidth = 620;
        final int panelX = centerX - panelWidth / 2;
        final int panelY = 96;
        final int panelHeight = 500;

        RoundRectangle2D panel = new RoundRectangle2D.Double(
                panelX, panelY, panelWidth, panelHeight, 20, 20);
        g2.setColor(Palette.HUD_BG);
        g2.fill(panel);
        g2.setColor(Palette.HUD_DIVIDER_SOFT);
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(panel);

        int titleBaseline = panelY + 74;
        g2.setFont(titleFont);
        FontMetrics titleMetrics = g2.getFontMetrics();
        String title = "The temple has fallen";
        g2.setColor(Palette.DANGER);
        g2.drawString(title, centerX - titleMetrics.stringWidth(title) / 2, titleBaseline);

        g2.setColor(Palette.HUD_DIVIDER);
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(centerX - 150, titleBaseline + 24, centerX + 150, titleBaseline + 24);

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

        int bestY = gridTop + rowHeight * 2 + 62;
        boolean newBest = state.getScore() >= state.getBestScore() && state.getScore() > 0;
        g2.setFont(bodyFont);
        String bestText = newBest
                ? "New personal best"
                : "Personal best  " + state.getBestScore()
                        + "   ·   Level " + Math.max(1, state.getBestLevel());
        FontMetrics bestMetrics = g2.getFontMetrics();
        g2.setColor(newBest ? Palette.HUD_TEXT_GOLD : Palette.HUD_TEXT_DIM);
        g2.drawString(bestText, centerX - bestMetrics.stringWidth(bestText) / 2, bestY);

        int controlsY = panelY + panelHeight + 46;
        if (restartArmed) {
            drawKeyHint(g2, centerX, controlsY, "ENTER", "restart now",
                    Palette.HUD_TEXT_GOLD);
        } else {
            drawKeyHint(g2, centerX, controlsY, "TAB  then  ENTER", "play again",
                    Palette.HUD_TEXT_GOLD);
        }
        drawKeyHint(g2, centerX, controlsY + 44, "ESC", "quit", Palette.HUD_TEXT_DIM);
    }

    /** Label above value, both left-aligned to the column. */
    private void drawGameOverStat(Graphics2D g2, String label, String value, int x, int y) {
        g2.setFont(microFont);
        g2.setColor(Palette.HUD_TEXT_DIM);
        g2.drawString(label, x, y);

        g2.setFont(secondaryFont);
        g2.setColor(Palette.HUD_TEXT_WHITE);
        g2.drawString(value, x, y + 32);
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
        int capTop = y - capHeight / 2 - 5;

        RoundRectangle2D cap = new RoundRectangle2D.Double(x, capTop, keyWidth, capHeight, 9, 9);
        g2.setColor(Palette.alpha(accent, 0.15));
        g2.fill(cap);
        g2.setColor(accent);
        g2.setStroke(new BasicStroke(1.3f));
        g2.draw(cap);

        g2.setFont(microFont);
        g2.drawString(key, x + 15, capTop + capHeight / 2 + keyMetrics.getAscent() / 2 - 1);

        g2.setFont(bodyFont);
        g2.setColor(Palette.HUD_TEXT_DIM);
        g2.drawString(caption, x + keyWidth + 16,
                capTop + capHeight / 2 + capMetrics.getAscent() / 2 - 1);
    }

    // ---- shared ------------------------------------------------------------

    /**
     * @param hint optional third line telegraphing the next level; null when
     *             that level introduces nothing worth announcing
     */
    private void drawCenteredPlaque(Graphics2D g2, String text, String sub, String hint,
                                    Color color, int y) {
        g2.setFont(bannerFont);
        FontMetrics fm = g2.getFontMetrics();
        int width = fm.stringWidth(text);

        g2.setFont(bodyFont);
        int subWidth = g2.getFontMetrics().stringWidth(sub);

        int hintWidth = 0;
        if (hint != null) {
            g2.setFont(hintFont);
            hintWidth = g2.getFontMetrics().stringWidth(hint);
        }

        int plaqueWidth = Math.max(Math.max(width, subWidth), hintWidth) + 90;
        int extraHeight = hint == null ? 0 : 30;
        int x = (GameConfig.SCREEN_WIDTH - plaqueWidth) / 2;

        RoundRectangle2D plaque = new RoundRectangle2D.Double(
                x, y - fm.getAscent() - 26,
                plaqueWidth, fm.getHeight() + 76 + extraHeight, 18, 18);

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.94f));
        g2.setColor(Palette.HUD_BG);
        g2.fill(plaque);
        g2.setComposite(AlphaComposite.SrcOver);

        g2.setColor(Palette.HUD_DIVIDER);
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(plaque);

        g2.setFont(bannerFont);
        g2.setColor(color);
        g2.drawString(text, (GameConfig.SCREEN_WIDTH - width) / 2, y);

        g2.setFont(bodyFont);
        g2.setColor(Palette.HUD_TEXT_WHITE);
        g2.drawString(sub, (GameConfig.SCREEN_WIDTH - subWidth) / 2, y + 34);

        if (hint != null) {
            g2.setFont(hintFont);
            g2.setColor(Palette.HUD_TEXT_DIM);
            g2.drawString(hint, (GameConfig.SCREEN_WIDTH - hintWidth) / 2, y + 62);
        }
    }
}
