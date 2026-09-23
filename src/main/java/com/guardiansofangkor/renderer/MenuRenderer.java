package com.guardiansofangkor.renderer;

import com.guardiansofangkor.engine.Difficulty;
import com.guardiansofangkor.engine.MenuItem;
import com.guardiansofangkor.engine.MenuState;
import com.guardiansofangkor.i18n.FontManager;
import com.guardiansofangkor.util.GameConfig;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
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
 * <p>Geometry is the design's responsive clamps resolved at the game's fixed
 * 1280x720: {@code clamp(28px, 3.5vw, 52px)} becomes 45, and so on. They are
 * resolved rather than reimplemented because the window does not resize — a
 * clamp that can only ever produce one value is just that value with extra
 * arithmetic in the way.
 *
 * <p><b>Selection, not hover.</b> The design distinguishes a permanent primary
 * action (gold pill) from secondary ones (stone rectangles), and animates a
 * hover state. This menu is keyboard-driven and has no cursor, so the two
 * treatments are mapped onto the state that actually exists: the <em>selected</em>
 * entry takes the gold pill, everything else is a stone rectangle. Showing which
 * entry the arrow keys are on matters more than marking one entry permanently
 * special, since without it the menu cannot be navigated at all.
 *
 * <p>Colours come from {@link Palette}, the same stone-and-gold the HUD uses, so
 * the menu and the game are recognisably one product.
 */
public class MenuRenderer {

    // ---- panel geometry, the design's clamps resolved at 1280x720 ----------

    private static final int PANEL_X = 45;
    private static final int PANEL_Y = 36;
    private static final int PANEL_W = 307;
    private static final int PANEL_H = GameConfig.SCREEN_HEIGHT - PANEL_Y * 2;
    private static final int PANEL_ARC = 10;

    private static final int PAD_X = 26;
    private static final int PAD_TOP = 25;
    private static final int PAD_BOTTOM = 22;

    private static final int CONTENT_W = PANEL_W - PAD_X * 2;
    private static final int CONTENT_X = PANEL_X + PAD_X;
    private static final int CENTRE_X = PANEL_X + PANEL_W / 2;

    /**
     * Every entry is the same height, primary or not.
     *
     * <p>The design gives the gold pill slightly more padding than the stone
     * rectangles. It cannot here: the menu is mouse-navigable, hovering an entry
     * selects it, and a selected entry that changed height would move its own
     * hit box out from under the cursor — so the row under the pointer would
     * flicker between two states at the boundary. Stable geometry is worth more
     * than four pixels of padding.
     */
    private static final int BUTTON_H = 44;
    private static final int BUTTON_GAP = 9;

    /**
     * Corner radius for every entry, selected or not.
     *
     * <p>One constant rather than one per state, so the two draw paths cannot
     * drift into different shapes — which is the bug this is guarding against.
     */
    private static final int BUTTON_ARC = 8;

    /** Distance from one entry's top edge to the next. */
    private static final int ENTRY_PITCH = BUTTON_H + BUTTON_GAP;

    /** How far a secondary entry slides right when it is the selected one. */
    private static final int SELECT_SHIFT = 3;

    // ---- title block, laid out as constants --------------------------------
    //
    // Fixed rather than accumulated at draw time so {@link #entryBounds} can be
    // a pure function of the index. The drawn position and the hit box are then
    // the same arithmetic by construction and cannot drift apart.

    private static final int TITLE_RULE_Y = PANEL_Y + PAD_TOP;
    private static final int DOT_ROW_Y = TITLE_RULE_Y + 18;
    private static final int EYEBROW_BASELINE = DOT_ROW_Y + 22;
    private static final int WORDMARK_CENTRE_Y = EYEBROW_BASELINE + 46;
    private static final int SUBTITLE_BASELINE = WORDMARK_CENTRE_Y + 36;
    private static final int DIVIDER_CENTRE_Y = SUBTITLE_BASELINE + 28;

    /** The hairline closing the title block. */
    private static final int TITLE_CLOSE_RULE_Y = DIVIDER_CENTRE_Y + 30;

    /** Top edge of the first entry on the main screen. */
    private static final int ENTRIES_Y = TITLE_CLOSE_RULE_Y + 18;

    /** The difficulty screen carries a heading, so its list starts lower. */
    private static final int DIFFICULTY_HEADING_Y = ENTRIES_Y;
    private static final int DIFFICULTY_ENTRIES_Y = ENTRIES_Y + 22;

    // ---- panel fill --------------------------------------------------------

    private static final Color PANEL_TOP = new Color(0x1E, 0x19, 0x14, 247);
    private static final Color PANEL_MID = new Color(0x23, 0x1C, 0x15, 245);
    private static final Color PANEL_BOTTOM = new Color(0x1A, 0x15, 0x10, 247);

    // ---- gold pill ---------------------------------------------------------

    private static final Color PILL_TOP = new Color(0xE8, 0xC0, 0x48);
    private static final Color PILL_MID = new Color(0xD4, 0xAF, 0x37);
    private static final Color PILL_BOTTOM = new Color(0xC4, 0x9A, 0x10);
    private static final Color PILL_TOP_BRIGHT = new Color(0xF7, 0xD1, 0x6E);
    private static final Color PILL_BOTTOM_BRIGHT = new Color(0xB8, 0x96, 0x0C);

    private static final Color LOCKED_TEXT = new Color(0x6E, 0x62, 0x50);
    private static final Color LOCKED_BORDER = new Color(0x46, 0x3C, 0x2C);

    private Font eyebrowFont;
    private Font titleFont;
    private Font subtitleFont;
    private Font primaryButtonFont;
    private Font secondaryButtonFont;
    private Font taglineFont;
    private Font footerFont;
    private Font hintFont;

    public MenuRenderer() {
        // Three faces, as the design specifies: a decorative display face for the
        // wordmark, a plain inscription serif for UI, and Garamond for prose.
        // All three degrade to a platform serif — see FontManager.
        this.eyebrowFont = FontManager.displayFont(16, Font.PLAIN);
        this.titleFont = FontManager.displayFont(49, Font.BOLD);
        this.subtitleFont = FontManager.bodyFont(15, Font.ITALIC);
        this.primaryButtonFont = FontManager.uiSerifFont(15, Font.BOLD);
        this.secondaryButtonFont = FontManager.uiSerifFont(14, Font.BOLD);
        this.taglineFont = FontManager.bodyFont(13, Font.ITALIC);
        this.footerFont = FontManager.bodyFont(11, Font.ITALIC);
        this.hintFont = FontManager.uiSerifFont(12, Font.PLAIN);
    }

    /**
     * @param glowPhase advancing radians, used to breathe the selected entry
     */
    public void draw(Graphics2D g2, MenuState state, BufferedImage background,
                     double glowPhase) {
        drawBackdrop(g2, background);
        drawPanel(g2);
        drawTitleBlock(g2);

        if (state.getScreen() == MenuState.Screen.MAIN) {
            drawMainEntries(g2, state, glowPhase);
        } else {
            drawDifficultyEntries(g2, state, glowPhase);
        }

        drawLockedMessage(g2, state);
        drawFooter(g2);
    }

    /**
     * Where entry {@code index} is drawn, for mouse hit-testing.
     *
     * <p>Public and static because {@code MenuPanel} owns the mouse and this
     * class owns the layout — the alternative is the panel hard-coding a second
     * copy of these numbers, which is exactly how a hit box ends up one pixel
     * off the thing it is supposed to be hitting.
     *
     * <p>Deliberately ignores the selected entry's horizontal slide: the shift
     * is a 3px cosmetic nudge, and letting it move the hit box would mean the
     * bounds changed the instant the cursor entered them.
     */
    public static Rectangle entryBounds(int index, MenuState.Screen screen) {
        int top = screen == MenuState.Screen.MAIN ? ENTRIES_Y : DIFFICULTY_ENTRIES_Y;
        return new Rectangle(CONTENT_X, top + index * ENTRY_PITCH, CONTENT_W, BUTTON_H);
    }

    // ---- backdrop and panel ------------------------------------------------

    private void drawBackdrop(Graphics2D g2, BufferedImage background) {
        if (background != null) {
            // Pre-scaled to the window by SpriteCache, so this is a 1:1 blit.
            g2.drawImage(background, 0, 0, null);
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

        g2.setPaint(new LinearGradientPaint(
                PANEL_X, PANEL_Y, PANEL_X + PANEL_W * 0.18f, PANEL_Y + PANEL_H,
                new float[] {0f, 0.4f, 1f},
                new Color[] {PANEL_TOP, PANEL_MID, PANEL_BOTTOM}));
        g2.fill(panel);

        Ornament.drawStoneTexture(g2, panel, 0.12);

        g2.setColor(Palette.alpha(Palette.GOLD, 0.22));
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(panel);

        // The seam down the right edge, separating the panel from the scene.
        Ornament.drawGoldSeam(g2, PANEL_X + PANEL_W, PANEL_Y + 10,
                PANEL_Y + PANEL_H - 10, 2, Palette.GOLD);
    }

    // ---- title -------------------------------------------------------------

    private void drawTitleBlock(Graphics2D g2) {
        Ornament.drawGoldRule(g2, CENTRE_X, TITLE_RULE_Y, CONTENT_W, Palette.GOLD, 0.8);

        // Paired dot rules — a header ornament rather than a divider.
        Graphics2D dg = (Graphics2D) g2.create();
        try {
            dg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
            Ornament.drawDotRow(dg, CENTRE_X - 52, DOT_ROW_Y, 48, Palette.GOLD);
            Ornament.drawDotRow(dg, CENTRE_X + 4, DOT_ROW_Y, 48, Palette.GOLD);
        } finally {
            dg.dispose();
        }

        // "GUARDIANS OF" — 0.35em tracking at 16px is 5.6px between letters.
        g2.setFont(eyebrowFont);
        g2.setColor(Palette.alpha(Palette.GOLD_MID, 0.9));
        drawTracked(g2, "GUARDIANS OF", CENTRE_X, EYEBROW_BASELINE, 5.6);

        // The wordmark, drawn as outlines so the halo is a real glow rather than
        // the string stamped at four offsets. Centred on its ink, not a baseline.
        DisplayText.drawCentred(g2, "ANGKOR", titleFont, CENTRE_X, WORDMARK_CENTRE_Y,
                Palette.GOLD_LIGHT, Palette.GOLD_LIGHT,
                Palette.GOLD, 0.55f, 1f);

        // "WORD DEFENSE" — 0.25em tracking at 15px is 3.75px.
        g2.setFont(subtitleFont);
        g2.setColor(Palette.GOLD_WARM);
        drawTracked(g2, "WORD DEFENSE", CENTRE_X, SUBTITLE_BASELINE, 3.75);

        // Scaled to about 1.27, so its ink runs roughly 20px either side of the
        // centre — TITLE_CLOSE_RULE_Y has to clear that, or the hairline lands
        // on the tower plinths rather than under them.
        Ornament.drawNagaDivider(g2, CENTRE_X, DIVIDER_CENTRE_Y,
                CONTENT_W / 200.0, Palette.GOLD);

        Ornament.drawGoldRule(g2, CENTRE_X, TITLE_CLOSE_RULE_Y,
                CONTENT_W * 0.8, Palette.GOLD, 0.6);
    }

    // ---- entry lists -------------------------------------------------------

    private void drawMainEntries(Graphics2D g2, MenuState state, double glowPhase) {
        MenuItem[] items = MenuItem.values();
        for (int i = 0; i < items.length; i++) {
            MenuItem item = items[i];
            boolean selected = state.getSelectedItem() == item
                    && state.getScreen() == MenuState.Screen.MAIN;

            drawButton(g2, item.getLabel().toUpperCase(java.util.Locale.ROOT),
                    entryBounds(i, MenuState.Screen.MAIN).y,
                    selected, state.isEnabled(item), !item.isImplemented(),
                    glowPhase, pressFor(state, selected));
        }
    }

    /** Press progress for a button — only the pressed, selected one moves. */
    private static double pressFor(MenuState state, boolean selected) {
        return selected && state.isPressed() ? state.getPressProgress() : 0;
    }

    private void drawDifficultyEntries(Graphics2D g2, MenuState state,
                                       double glowPhase) {
        g2.setColor(Palette.alpha(Palette.GOLD_FAINT, 0.95));
        g2.setFont(hintFont);
        drawTracked(g2, "CHOOSE YOUR TRIAL", CENTRE_X, DIFFICULTY_HEADING_Y, 2.6);

        Difficulty[] tiers = Difficulty.values();
        for (int i = 0; i < tiers.length; i++) {
            Difficulty difficulty = tiers[i];
            boolean selected = state.getSelectedDifficulty() == difficulty;
            // Only Endless is unbuilt. Medium and Hard are finished and simply
            // locked until they are earned, so they render dark but unbadged —
            // pressing one explains what would open it.
            drawButton(g2,
                    difficulty.getDisplayName().toUpperCase(java.util.Locale.ROOT),
                    entryBounds(i, MenuState.Screen.DIFFICULTY).y,
                    selected, state.isEnabled(difficulty),
                    !difficulty.isImplemented(), glowPhase,
                    pressFor(state, selected));
        }

        int y = DIFFICULTY_ENTRIES_Y + tiers.length * ENTRY_PITCH;

        // Tagline for whatever is highlighted, so each tier explains itself.
        g2.setFont(taglineFont);
        g2.setColor(Palette.GOLD_WARM);
        String tagline = state.getSelectedDifficulty().getTagline();
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(tagline, CENTRE_X - fm.stringWidth(tagline) / 2, y + 14);

        g2.setFont(hintFont);
        g2.setColor(Palette.alpha(Palette.GOLD_FAINT, 0.9));
        String back = "ESC  ·  back";
        g2.drawString(back, CENTRE_X - g2.getFontMetrics().stringWidth(back) / 2, y + 40);
    }

    /**
     * One menu entry.
     *
     * <p>The selected, available entry takes the design's primary treatment — a
     * gold pill with dark text. Everything else takes the secondary stone
     * rectangle. A selected entry that is <em>locked</em> deliberately does not
     * get the pill: the pill means "press this", and offering it for something
     * that will refuse would be a lie the player only discovers by pressing.
     * It brightens its border and slides across instead, which says "you are
     * here" without saying "this works".
     *
     * @param unbuilt whether to badge the plate SOON. Deliberately separate from
     *                {@code enabled}, because there are two quite different
     *                reasons a button can be dark. SOON means the feature does
     *                not exist yet and no amount of playing will produce it. A
     *                difficulty the player has not earned, or a Continue with
     *                nothing to continue, is finished work waiting on them —
     *                badging those SOON tells the player a lie, and one that
     *                would stop them trying to unlock it.
     */
    private void drawButton(Graphics2D g2, String label, int y,
                            boolean selected, boolean enabled, boolean unbuilt,
                            double glowPhase, double pressProgress) {
        boolean primary = selected && enabled;

        // A pressed button sinks a couple of pixels, so the press has a physical
        // read rather than only a colour change. Purely cosmetic — entryBounds
        // does not follow it, so a press cannot move its own hit box.
        int sink = (int) Math.round(2 * pressProgress);
        int shift = selected && !primary ? SELECT_SHIFT : 0;

        int x = CONTENT_X + shift;
        int top = y + sink;
        int width = CONTENT_W - shift;

        if (primary) {
            drawPrimaryButton(g2, label, x, top, width, glowPhase, pressProgress);
        } else {
            drawSecondaryButton(g2, label, x, top, width, selected, enabled, unbuilt);
        }
    }

    /**
     * The selected entry: gold gradient fill, glowing, dark text.
     *
     * <p>Deliberately the same 8px rectangle as every other entry. The design
     * draws its primary action as a fully rounded pill, but selection here
     * follows the cursor — and a row that changed shape as the pointer crossed
     * it would be a silhouette flicking between two outlines on every hover.
     * Colour and glow carry the state; the shape stays still.
     */
    private void drawPrimaryButton(Graphics2D g2, String label, int x, int y,
                                   int width, double glowPhase,
                                   double pressProgress) {
        double pulse = 0.5 + 0.5 * Math.sin(glowPhase);
        int height = BUTTON_H;
        int arc = BUTTON_ARC;

        Graphics2D glow = (Graphics2D) g2.create();
        try {
            glow.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, (float) (0.20 + 0.16 * pulse)));
            glow.setColor(Palette.GOLD);
            for (int i = 4; i >= 1; i--) {
                glow.setStroke(new BasicStroke(i * 2.6f));
                glow.draw(new RoundRectangle2D.Double(
                        x - i, y - i, width + i * 2, height + i * 2, arc + i, arc + i));
            }
        } finally {
            glow.dispose();
        }

        // Pressing inverts the gradient, so the plate reads as pushed in.
        boolean pressed = pressProgress > 0;
        RoundRectangle2D pill =
                new RoundRectangle2D.Double(x, y, width, height, arc, arc);

        g2.setPaint(new LinearGradientPaint(
                x, y, x + width * 0.7f, y + height,
                new float[] {0f, 0.5f, 1f},
                pressed
                        ? new Color[] {PILL_BOTTOM_BRIGHT, PILL_MID, PILL_TOP_BRIGHT}
                        : new Color[] {PILL_TOP, PILL_MID, PILL_BOTTOM}));
        g2.fill(pill);

        // Inner top highlight — the "inset 0 1px 0 rgba(255,255,255,0.2)".
        g2.setColor(new Color(0xFF, 0xFF, 0xFF, 51));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Double(
                x + 1.5, y + 1.5, width - 3, height - 3, arc - 2, arc - 2));

        g2.setColor(Palette.GOLD_LIGHT);
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(pill);

        g2.setFont(primaryButtonFont);
        FontMetrics fm = g2.getFontMetrics();
        int centreX = x + width / 2;
        int baseline = y + height / 2 + fm.getAscent() / 2 - 2;
        double tracking = 2.7;

        g2.setColor(Palette.STONE_DARK);
        drawTracked(g2, label, centreX, baseline, tracking);

        double half = trackedWidth(fm, label, tracking) / 2;
        double flameY = y + height / 2.0;
        Ornament.drawLotusFlame(g2, centreX - half - 15, flameY, 0.85, false,
                Palette.STONE_DARK, Palette.alpha(Palette.STONE_DARK, 0.6));
        Ornament.drawLotusFlame(g2, centreX + half + 15, flameY, 0.85, true,
                Palette.STONE_DARK, Palette.alpha(Palette.STONE_DARK, 0.6));
    }

    /** The stone rectangle: 8px corners, gold outline, gold text. */
    private void drawSecondaryButton(Graphics2D g2, String label, int x, int y,
                                     int width, boolean selected,
                                     boolean enabled, boolean unbuilt) {
        int height = BUTTON_H;
        RoundRectangle2D plate = new RoundRectangle2D.Double(
                x, y, width, height, BUTTON_ARC, BUTTON_ARC);

        Color text;
        if (!enabled) {
            g2.setColor(new Color(0x18, 0x14, 0x0E, 190));
            g2.fill(plate);
            g2.setColor(selected ? Palette.alpha(Palette.GOLD, 0.5) : LOCKED_BORDER);
            g2.setStroke(new BasicStroke(selected ? 1.5f : 1.1f));
            g2.draw(plate);
            text = LOCKED_TEXT;
        } else {
            g2.setColor(selected
                    ? new Color(0x30, 0x20, 0x18, 217)
                    : new Color(0x1E, 0x19, 0x14, 153));
            g2.fill(plate);
            g2.setColor(Palette.alpha(Palette.GOLD, selected ? 0.7 : 0.38));
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(plate);
            text = selected ? Palette.GOLD_LIGHT : Palette.GOLD_MID;
        }

        g2.setFont(secondaryButtonFont);
        FontMetrics fm = g2.getFontMetrics();
        int centreX = x + width / 2;
        int baseline = y + height / 2 + fm.getAscent() / 2 - 2;
        double tracking = 2.24;

        g2.setColor(text);
        drawTracked(g2, label, centreX, baseline, tracking);

        double half = trackedWidth(fm, label, tracking) / 2;
        double flameY = y + height / 2.0;
        Color flameBody = enabled
                ? (selected ? Palette.GOLD_LIGHT : Palette.GOLD_DIM)
                : LOCKED_TEXT;
        Ornament.drawLotusFlame(g2, centreX - half - 14, flameY, 0.8, false,
                flameBody, Palette.GOLD_LIGHT);
        Ornament.drawLotusFlame(g2, centreX + half + 14, flameY, 0.8, true,
                flameBody, Palette.GOLD_LIGHT);

        if (unbuilt) {
            g2.setFont(hintFont);
            g2.setColor(Palette.alpha(LOCKED_TEXT, 0.9));
            String tag = "SOON";
            g2.drawString(tag, x + width - g2.getFontMetrics().stringWidth(tag) - 10,
                    y + height - 7);
        }
    }

    // ---- footer and messages -----------------------------------------------

    private void drawLockedMessage(Graphics2D g2, MenuState state) {
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
                    CENTRE_X - width / 2.0 - 12, y - fm.getAscent() - 6,
                    width + 24, fm.getHeight() + 10, 8, 8));
            mg.setColor(Palette.alpha(Palette.DANGER, 0.9));
            mg.drawString(text, CENTRE_X - width / 2, y);
        } finally {
            mg.dispose();
        }
    }

    private void drawFooter(Graphics2D g2) {
        int y = PANEL_Y + PANEL_H - PAD_BOTTOM;

        Ornament.drawGoldRule(g2, CENTRE_X, y - 20, CONTENT_W, Palette.GOLD, 0.45);

        g2.setFont(footerFont);
        g2.setColor(Palette.GOLD_GHOST);
        String footer = "v1.0 · © 2025 Stone Gate Studios";
        drawTracked(g2, footer, CENTRE_X, y, 1.1);
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
        // One gap per letter except the last — a trailing gap would shift the
        // whole string half a space left of centre.
        return fm.stringWidth(text) + tracking * (text.length() - 1);
    }
}
