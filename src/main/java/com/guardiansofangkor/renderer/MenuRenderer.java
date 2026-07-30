package com.guardiansofangkor.renderer;

import com.guardiansofangkor.engine.Difficulty;
import com.guardiansofangkor.engine.MenuItem;
import com.guardiansofangkor.engine.MenuState;
import com.guardiansofangkor.util.GameConfig;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Paints the front end: title, main entries and the difficulty picker.
 *
 * <p>Follows the design's composition — a floating panel inset from the left
 * edge, with the temple scene wrapping visibly around it on three sides so it
 * reads as embedded in the world rather than pasted on top. That inset is the
 * whole trick, so the panel is deliberately narrow and never bleeds to an edge.
 *
 * <p>Colours come from {@link Palette}, the same stone-and-gold the HUD uses, so
 * the menu and the game are recognisably one product.
 */
public class MenuRenderer {

    // ---- panel geometry, proportional to the window ------------------------

    private static final int PANEL_X = 52;
    private static final int PANEL_Y = 46;
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 628;
    private static final int PANEL_ARC = 14;

    private static final int PANEL_PADDING = 28;
    private static final int BUTTON_H = 46;
    private static final int BUTTON_GAP = 12;

    // ---- colours -----------------------------------------------------------

    private static final Color PANEL_TOP = new Color(0x24, 0x1C, 0x12, 238);
    private static final Color PANEL_BOTTOM = new Color(0x16, 0x11, 0x0B, 246);
    private static final Color BUTTON_IDLE = new Color(0x1C, 0x16, 0x0E, 210);
    private static final Color BUTTON_SELECTED_TOP = new Color(0xF2, 0xD8, 0x7C);
    private static final Color BUTTON_SELECTED_BOTTOM = new Color(0xC9, 0x9F, 0x2E);
    private static final Color SELECTED_TEXT = new Color(0x24, 0x1A, 0x08);
    private static final Color LOCKED_TEXT = new Color(0x6E, 0x62, 0x50);
    private static final Color LOCKED_BORDER = new Color(0x46, 0x3C, 0x2C);
    private static final Color TAGLINE = new Color(0xB9, 0xA8, 0x8B);
    private static final Color TITLE_FILL_TOP = new Color(0xFF, 0xEB, 0xB4);
    private static final Color TITLE_FILL_BOTTOM = new Color(0xDE, 0xAE, 0x42);

    private Font eyebrowFont;
    private Font titleFont;
    private Font subtitleFont;
    private Font buttonFont;
    private Font taglineFont;
    private Font footerFont;
    private Font hintFont;

    public MenuRenderer() {
        this.eyebrowFont = new Font(Font.SERIF, Font.PLAIN, 17);
        this.titleFont = new Font(Font.SERIF, Font.BOLD, 52);
        this.subtitleFont = new Font(Font.SERIF, Font.ITALIC, 15);
        this.buttonFont = new Font(Font.SERIF, Font.BOLD, 16);
        this.taglineFont = new Font(Font.SERIF, Font.ITALIC, 13);
        this.footerFont = new Font(Font.SERIF, Font.ITALIC, 11);
        this.hintFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }

    /**
     * @param glowPhase advancing radians, used to breathe the selected entry
     */
    public void draw(Graphics2D g2, MenuState state, BufferedImage background,
                     double glowPhase) {
        drawBackdrop(g2, background);
        drawPanel(g2);

        int contentX = PANEL_X + PANEL_PADDING;
        int contentW = PANEL_W - PANEL_PADDING * 2;
        int centreX = PANEL_X + PANEL_W / 2;

        int y = drawTitleBlock(g2, centreX, contentW);

        if (state.getScreen() == MenuState.Screen.MAIN) {
            drawMainEntries(g2, state, contentX, contentW, y, glowPhase);
        } else {
            drawDifficultyEntries(g2, state, contentX, contentW, centreX, y, glowPhase);
        }

        drawLockedMessage(g2, state, centreX);
        drawFooter(g2, centreX);
    }

    // ---- backdrop and panel ------------------------------------------------

    private void drawBackdrop(Graphics2D g2, BufferedImage background) {
        if (background != null) {
            g2.drawImage(background, 0, 0,
                    GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT, null);
            return;
        }
        g2.setPaint(new GradientPaint(
                0, 0, new Color(0x1B, 0x16, 0x2E),
                0, GameConfig.SCREEN_HEIGHT, new Color(0x7A, 0x3F, 0x22)));
        g2.fillRect(0, 0, GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT);
    }

    private void drawPanel(Graphics2D g2) {
        RoundRectangle2D panel = new RoundRectangle2D.Double(
                PANEL_X, PANEL_Y, PANEL_W, PANEL_H, PANEL_ARC, PANEL_ARC);

        // Layered drop shadow, so the panel sits above the painting with weight.
        for (int i = 5; i >= 1; i--) {
            g2.setColor(new Color(0, 0, 0, 16));
            g2.fill(new RoundRectangle2D.Double(
                    PANEL_X - i, PANEL_Y - i + 3,
                    PANEL_W + i * 2, PANEL_H + i * 2, PANEL_ARC + i, PANEL_ARC + i));
        }

        g2.setPaint(new GradientPaint(
                PANEL_X, PANEL_Y, PANEL_TOP,
                PANEL_X, PANEL_Y + PANEL_H, PANEL_BOTTOM));
        g2.fill(panel);

        g2.setColor(Palette.alpha(Palette.HUD_DIVIDER, 0.55));
        g2.setStroke(new BasicStroke(1.4f));
        g2.draw(panel);

        g2.setColor(Palette.alpha(Palette.HUD_DIVIDER, 0.14));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Double(
                PANEL_X + 4, PANEL_Y + 4, PANEL_W - 8, PANEL_H - 8,
                PANEL_ARC - 3, PANEL_ARC - 3));
    }

    // ---- title -------------------------------------------------------------

    /** @return the y to begin the entry list at */
    private int drawTitleBlock(Graphics2D g2, int centreX, int contentW) {
        g2.setColor(Palette.alpha(Palette.HUD_DIVIDER, 0.5));
        Ornament.drawDottedRule(g2, centreX, PANEL_Y + 42, contentW * 0.52);

        g2.setColor(Palette.HUD_TEXT_GOLD);
        g2.setFont(eyebrowFont);
        drawTracked(g2, "GUARDIANS OF", centreX, PANEL_Y + 76, 3.2);

        // Optical centre now, not a baseline — DisplayText centres on the ink.
        drawGlowingTitle(g2, "ANGKOR", centreX, PANEL_Y + 114);

        g2.setColor(Palette.alpha(Palette.HUD_TEXT_GOLD, 0.82));
        g2.setFont(subtitleFont);
        drawTracked(g2, "WORD DEFENSE", centreX, PANEL_Y + 158, 3.6);

        g2.setColor(Palette.alpha(Palette.HUD_DIVIDER, 0.66));
        Ornament.drawTempleDivider(g2, centreX, PANEL_Y + 208, contentW * 0.94);

        return PANEL_Y + 236;
    }

    /**
     * The wordmark, drawn as outlines through {@link DisplayText}.
     *
     * <p>Shares the countdown's renderer so both get the same real halo — the
     * previous version stamped the string at four offsets, which reads as ghosting
     * rather than glow at this size.
     *
     * @param centreY the optical centre of the wordmark, not a baseline
     */
    private void drawGlowingTitle(Graphics2D g2, String text, int centreX, int centreY) {
        DisplayText.drawCentred(g2, text, titleFont, centreX, centreY,
                TITLE_FILL_TOP, TITLE_FILL_BOTTOM,
                Palette.HUD_TEXT_GOLD, 0.75f, 1f);
    }

    // ---- entry lists -------------------------------------------------------

    private void drawMainEntries(Graphics2D g2, MenuState state,
                                 int x, int width, int y, double glowPhase) {
        for (MenuItem item : MenuItem.values()) {
            boolean selected = state.getSelectedItem() == item
                    && state.getScreen() == MenuState.Screen.MAIN;
            drawButton(g2, item.getLabel().toUpperCase(java.util.Locale.ROOT),
                    x, y, width, selected, state.isEnabled(item), glowPhase,
                    pressFor(state, selected));
            y += BUTTON_H + BUTTON_GAP;
        }
    }

    /** Press progress for a button — only the pressed, selected one moves. */
    private static double pressFor(MenuState state, boolean selected) {
        return selected && state.isPressed() ? state.getPressProgress() : 0;
    }

    private void drawDifficultyEntries(Graphics2D g2, MenuState state,
                                       int x, int width, int centreX, int y,
                                       double glowPhase) {
        g2.setColor(Palette.alpha(Palette.HUD_TEXT_DIM, 0.85));
        g2.setFont(hintFont);
        drawTracked(g2, "CHOOSE YOUR TRIAL", centreX, y - 8, 2.2);
        y += 16;

        for (Difficulty difficulty : Difficulty.values()) {
            boolean selected = state.getSelectedDifficulty() == difficulty;
            drawButton(g2, difficulty.getDisplayName().toUpperCase(java.util.Locale.ROOT),
                    x, y, width, selected, state.isEnabled(difficulty), glowPhase,
                    pressFor(state, selected));
            y += BUTTON_H + BUTTON_GAP;
        }

        // Tagline for whatever is highlighted, so each tier explains itself.
        g2.setFont(taglineFont);
        g2.setColor(TAGLINE);
        String tagline = state.getSelectedDifficulty().getTagline();
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(tagline, centreX - fm.stringWidth(tagline) / 2, y + 14);

        g2.setFont(hintFont);
        g2.setColor(Palette.alpha(Palette.HUD_TEXT_DIM, 0.7));
        String back = "ESC  ·  back";
        g2.drawString(back, centreX - g2.getFontMetrics().stringWidth(back) / 2, y + 40);
    }

    /**
     * One menu button.
     *
     * <p>Three states, matching the design: selected is a filled gold plate with
     * dark text, available is an outlined plate with gold text, and locked keeps
     * the same silhouette but drops to flat stone — present, clearly not ready,
     * and not mistakable for either of the other two.
     */
    private void drawButton(Graphics2D g2, String label, int x, int y, int width,
                            boolean selected, boolean enabled, double glowPhase,
                            double pressProgress) {
        // A pressed button sinks a couple of pixels and loses a little width, so
        // the click has a physical read rather than just changing colour.
        int sink = (int) Math.round(2 * pressProgress);
        int inset = (int) Math.round(1.5 * pressProgress);
        x += inset;
        y += sink;
        width -= inset * 2;

        RoundRectangle2D plate = new RoundRectangle2D.Double(x, y, width, BUTTON_H, 10, 10);

        Color textColor;
        if (selected && enabled) {
            double pulse = 0.5 + 0.5 * Math.sin(glowPhase);
            Graphics2D glow = (Graphics2D) g2.create();
            try {
                glow.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, (float) (0.18 + 0.14 * pulse)));
                glow.setColor(Palette.HUD_TEXT_GOLD);
                for (int i = 4; i >= 1; i--) {
                    glow.setStroke(new BasicStroke(i * 2.4f));
                    glow.draw(new RoundRectangle2D.Double(
                            x - i, y - i, width + i * 2, BUTTON_H + i * 2, 10 + i, 10 + i));
                }
            } finally {
                glow.dispose();
            }

            // Pressing inverts the gradient, so the plate reads as pushed in.
            Color top = pressProgress > 0 ? BUTTON_SELECTED_BOTTOM : BUTTON_SELECTED_TOP;
            Color bottom = pressProgress > 0 ? BUTTON_SELECTED_TOP : BUTTON_SELECTED_BOTTOM;
            g2.setPaint(new GradientPaint(x, y, top, x, y + BUTTON_H, bottom));
            g2.fill(plate);
            g2.setColor(Palette.alpha(new Color(0xFF, 0xF3, 0xC9), 0.9));
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(plate);
            textColor = SELECTED_TEXT;

        } else if (enabled) {
            g2.setColor(BUTTON_IDLE);
            g2.fill(plate);
            g2.setColor(Palette.alpha(Palette.HUD_DIVIDER, selected ? 0.9 : 0.55));
            g2.setStroke(new BasicStroke(selected ? 1.6f : 1.2f));
            g2.draw(plate);
            textColor = Palette.HUD_TEXT_GOLD;

        } else {
            g2.setColor(new Color(0x18, 0x14, 0x0E, 190));
            g2.fill(plate);
            g2.setColor(selected ? Palette.alpha(Palette.HUD_DIVIDER, 0.45) : LOCKED_BORDER);
            g2.setStroke(new BasicStroke(selected ? 1.5f : 1.1f));
            g2.draw(plate);
            textColor = LOCKED_TEXT;
        }

        g2.setFont(buttonFont);
        FontMetrics fm = g2.getFontMetrics();
        double tracking = 2.6;
        double labelWidth = trackedWidth(fm, label, tracking);
        int centreX = x + width / 2;
        int baselineY = y + BUTTON_H / 2 + fm.getAscent() / 2 - 2;

        g2.setColor(textColor);
        drawTracked(g2, label, centreX, baselineY, tracking);

        // Votive candles flanking the label, as in the design.
        Color candleBody = selected && enabled
                ? Palette.alpha(SELECTED_TEXT, 0.55)
                : Palette.alpha(textColor, 0.5);
        Color candleFlame = enabled
                ? (selected ? new Color(0xFF, 0xF6, 0xDC) : Palette.HUD_TEXT_GOLD)
                : LOCKED_TEXT;

        double candleGap = labelWidth / 2 + 18;
        double candleBase = y + BUTTON_H / 2.0 + 9;
        Ornament.drawCandle(g2, centreX - candleGap, candleBase, 20,
                candleBody, candleFlame);
        Ornament.drawCandle(g2, centreX + candleGap, candleBase, 20,
                candleBody, candleFlame);

        if (!enabled) {
            g2.setFont(hintFont);
            g2.setColor(Palette.alpha(LOCKED_TEXT, 0.9));
            String tag = "SOON";
            g2.drawString(tag, x + width - g2.getFontMetrics().stringWidth(tag) - 10,
                    y + BUTTON_H - 8);
        }
    }

    // ---- footer and messages -----------------------------------------------

    private void drawLockedMessage(Graphics2D g2, MenuState state, int centreX) {
        double alpha = state.getLockedMessageAlpha();
        if (alpha <= 0.01 || state.getLockedMessage().isEmpty()) {
            return;
        }
        Graphics2D mg = (Graphics2D) g2.create();
        try {
            mg.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, (float) alpha));
            mg.setFont(hintFont);
            FontMetrics fm = mg.getFontMetrics();
            String text = state.getLockedMessage();
            int width = fm.stringWidth(text);
            int y = PANEL_Y + PANEL_H - 58;

            mg.setColor(Palette.alpha(Palette.HUD_BG, 0.92));
            mg.fill(new RoundRectangle2D.Double(
                    centreX - width / 2.0 - 12, y - fm.getAscent() - 6,
                    width + 24, fm.getHeight() + 10, 8, 8));
            mg.setColor(Palette.alpha(Palette.DANGER, 0.9));
            mg.drawString(text, centreX - width / 2, y);
        } finally {
            mg.dispose();
        }
    }

    private void drawFooter(Graphics2D g2, int centreX) {
        g2.setFont(footerFont);
        g2.setColor(Palette.alpha(Palette.HUD_TEXT_DIM, 0.6));
        String footer = "v1.0  ·  © 2025 Stone Gate Studios";
        g2.drawString(footer,
                centreX - g2.getFontMetrics().stringWidth(footer) / 2,
                PANEL_Y + PANEL_H - 22);
    }

    // ---- letter tracking ---------------------------------------------------

    /**
     * Draws {@code text} centred on {@code centreX} with extra space between
     * letters.
     *
     * <p>Java2D has no letter-spacing, and the design leans on wide tracking for
     * its inscription feel, so the string is laid out a character at a time.
     */
    private void drawTracked(Graphics2D g2, String text, int centreX, int baselineY,
                             double tracking) {
        FontMetrics fm = g2.getFontMetrics();
        double total = trackedWidth(fm, text, tracking);
        double x = centreX - total / 2;

        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            g2.drawString(ch, (float) x, baselineY);
            x += fm.stringWidth(ch) + tracking;
        }
    }

    private static double trackedWidth(FontMetrics fm, String text, double tracking) {
        if (text.isEmpty()) {
            return 0;
        }
        return fm.stringWidth(text) + tracking * (text.length() - 1);
    }

    // ---- hit testing -------------------------------------------------------

    /** Bounds of the entry at {@code index}, for mouse hit testing. */
    public static Rectangle entryBounds(int index, MenuState.Screen screen) {
        int x = PANEL_X + PANEL_PADDING;
        int width = PANEL_W - PANEL_PADDING * 2;
        int y = PANEL_Y + 236 + (screen == MenuState.Screen.DIFFICULTY ? 16 : 0)
                + index * (BUTTON_H + BUTTON_GAP);
        return new Rectangle(x, y, width, BUTTON_H);
    }
}
