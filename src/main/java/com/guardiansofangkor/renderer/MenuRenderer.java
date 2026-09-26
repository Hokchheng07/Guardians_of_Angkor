package com.guardiansofangkor.renderer;

import com.guardiansofangkor.engine.Difficulty;
import com.guardiansofangkor.engine.MenuItem;
import com.guardiansofangkor.audio.AudioSettings;
import com.guardiansofangkor.engine.MenuState;
import com.guardiansofangkor.engine.TempleMap;
import com.guardiansofangkor.entities.Hero;
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
import java.awt.LinearGradientPaint;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Paints the front end: title, main entries, the hero picker and the
 * difficulty picker.
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

    // ---- hero cards --------------------------------------------------------
    //
    // The hero picker keeps the panel — its list is the keyboard's target, the
    // same as every other screen — and puts the heroes themselves in the scene
    // to its right, full height, because a portrait is the whole point of
    // choosing one. Both cards are always visible; the selected one is lit and
    // posed, the other stands idle and dimmed.

    private static final int CARD_W = 330;
    private static final int CARD_H = 610;
    private static final int CARD_GAP = 44;
    private static final int CARD_Y = (GameConfig.SCREEN_HEIGHT - CARD_H) / 2;

    /** Cards are centred in the scene area to the right of the panel. */
    private static final int SCENE_X = PANEL_X + PANEL_W;
    private static final int CARDS_X = SCENE_X
            + (GameConfig.SCREEN_WIDTH - SCENE_X - (CARD_W * 2 + CARD_GAP)) / 2;

    /** Where the hero's feet rest inside the card. */
    private static final int CARD_FEET_OFFSET = 492;
    private static final int CARD_NAME_OFFSET = 536;
    private static final int CARD_EPITHET_OFFSET = 566;
    private static final int CARD_ARC = 12;

    // ---- temple cards -----------------------------------------------------

    private static final int TEMPLE_CARD_W = 390;
    private static final int TEMPLE_CARD_H = 312;
    private static final int TEMPLE_CARD_GAP = 32;
    private static final int TEMPLE_CARD_Y = 190;
    private static final int TEMPLE_CARDS_X = SCENE_X
            + (GameConfig.SCREEN_WIDTH - SCENE_X
            - (TEMPLE_CARD_W * 2 + TEMPLE_CARD_GAP)) / 2;
    private static final int TEMPLE_IMAGE_H = 222;

    // ---- options card ------------------------------------------------------
    //
    // Same arrangement as the hero picker: the panel keeps the title and the
    // key hints, and the settings themselves sit on one wide stone card in the
    // scene, because a slider needs more width than the panel has.

    private static final int OPT_CARD_X = SCENE_X + 60;
    private static final int OPT_CARD_W = GameConfig.SCREEN_WIDTH - 60 - OPT_CARD_X;
    private static final int OPT_CARD_Y = 84;
    private static final int OPT_CARD_H = 552;

    private static final int OPT_ROWS_Y = OPT_CARD_Y + 112;
    private static final int OPT_ROW_PITCH = 76;
    private static final int OPT_ROW_H = 60;
    private static final int OPT_ROW_X = OPT_CARD_X + 40;
    private static final int OPT_ROW_W = OPT_CARD_W - 80;

    /** Where a row's control starts, after its label. */
    private static final int OPT_CONTROL_X = OPT_ROW_X + 250;
    private static final int OPT_CONTROL_RIGHT = OPT_ROW_X + OPT_ROW_W - 24;

    /** The slider track stops short of the right edge to leave room for its value. */
    private static final int OPT_TRACK_RIGHT = OPT_CONTROL_RIGHT - 64;
    private static final int OPT_SEGMENT_H = 38;
    private static final int OPT_BACK_W = 220;

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
    private Font heroNameFont;
    private Font heroEpithetFont;
    private Font optionLabelFont;
    private Font optionValueFont;

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
        this.heroNameFont = FontManager.displayFont(28, Font.BOLD);
        this.heroEpithetFont = FontManager.bodyFont(16, Font.ITALIC);
        this.optionLabelFont = FontManager.uiSerifFont(15, Font.BOLD);
        this.optionValueFont = FontManager.uiSerifFont(16, Font.BOLD);
    }

    /**
     * @param glowPhase advancing radians, used to breathe the selected entry
     */
    public void draw(Graphics2D g2, MenuState state, SpriteCache sprites,
                     double glowPhase) {
        drawBackdrop(g2, sprites.menuBackground());
        if (state.getScreen() == MenuState.Screen.HERO) {
            drawHeroCards(g2, state, sprites, glowPhase);
        } else if (state.getScreen() == MenuState.Screen.TEMPLE) {
            drawTempleCards(g2, state, sprites, glowPhase);
        } else if (state.getScreen() == MenuState.Screen.OPTIONS) {
            drawOptionsCard(g2, state, glowPhase);
        }
        drawPanel(g2);
        drawTitleBlock(g2);

        switch (state.getScreen()) {
            case MAIN -> drawMainEntries(g2, state, glowPhase);
            case HERO -> drawHeroEntries(g2, state, glowPhase);
            case TEMPLE -> drawTempleEntries(g2, state, glowPhase);
            case DIFFICULTY -> drawDifficultyEntries(g2, state, glowPhase);
            case OPTIONS -> drawOptionsHints(g2, state);
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

    /** Where Options row {@code index} is drawn, for mouse hit-testing. */
    public static Rectangle optionRowBounds(int index) {
        return new Rectangle(OPT_ROW_X, OPT_ROWS_Y + index * OPT_ROW_PITCH, OPT_ROW_W, OPT_ROW_H);
    }

    /**
     * The draggable span of a volume row: the track itself, full row height so
     * the knob is easy to catch.
     */
    public static Rectangle sliderTrackBounds(MenuState.OptionRow row) {
        Rectangle r = optionRowBounds(row.ordinal());
        return new Rectangle(OPT_CONTROL_X, r.y, OPT_TRACK_RIGHT - OPT_CONTROL_X, r.height);
    }

    /** The slider value under a mouse x, 0 to 100. */
    public static int sliderValueAt(int mouseX) {
        double t = (mouseX - OPT_CONTROL_X) / (double) (OPT_TRACK_RIGHT - OPT_CONTROL_X);
        t = Math.max(0, Math.min(1, t));
        return (int) Math.round(AudioSettings.MIN + t * (AudioSettings.MAX - AudioSettings.MIN));
    }

    /** One language's segment in the Language row. */
    public static Rectangle languageSegmentBounds(int index) {
        Rectangle r = optionRowBounds(MenuState.OptionRow.LANGUAGE.ordinal());
        int count = Language.values().length;
        int gap = 10;
        int w = (OPT_CONTROL_RIGHT - OPT_CONTROL_X - gap * (count - 1)) / count;
        int y = r.y + (r.height - OPT_SEGMENT_H) / 2;
        return new Rectangle(OPT_CONTROL_X + index * (w + gap), y, w, OPT_SEGMENT_H);
    }

    /** Where hero card {@code index} is drawn, for mouse hit-testing. */
    public static Rectangle heroCardBounds(int index) {
        return new Rectangle(CARDS_X + index * (CARD_W + CARD_GAP), CARD_Y, CARD_W, CARD_H);
    }

    /** Where temple preview card {@code index} is drawn, for mouse hit-testing. */
    public static Rectangle templeCardBounds(int index) {
        return new Rectangle(TEMPLE_CARDS_X + index * (TEMPLE_CARD_W + TEMPLE_CARD_GAP),
                TEMPLE_CARD_Y, TEMPLE_CARD_W, TEMPLE_CARD_H);
    }

    // ---- backdrop and panel ------------------------------------------------

    private void drawBackdrop(Graphics2D g2, BufferedImage background) {
        if (background != null) {
            // Pre-scaled to the screen's device size by SpriteCache, so drawing
            // it into the window's logical size is a 1:1 blit on the device.
            // The explicit size matters: on a 2x screen the image is 2560 wide.
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
            // All four tiers are playable. The progress record is retained for
            // completion history, but it does not gate the difficulty picker.
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

    // ---- options -----------------------------------------------------------

    private void drawOptionsHints(Graphics2D g2, MenuState state) {
        g2.setColor(Palette.alpha(Palette.GOLD_FAINT, 0.95));
        g2.setFont(hintFont);
        drawTracked(g2, "SETTINGS", CENTRE_X, DIFFICULTY_HEADING_Y, 2.6);

        int y = DIFFICULTY_ENTRIES_Y + 18;
        String[] hints = {
                "UP / DOWN  ·  choose",
                "LEFT / RIGHT  ·  adjust",
                "ENTER  ·  switch language",
                "ESC  ·  back"};
        g2.setColor(Palette.alpha(Palette.GOLD_FAINT, 0.9));
        for (String hint : hints) {
            g2.drawString(hint, CENTRE_X - g2.getFontMetrics().stringWidth(hint) / 2, y);
            y += 22;
        }

        // Say what the highlighted row does — the language one is the only
        // setting that changes gameplay, so it is worth being precise about.
        g2.setFont(taglineFont);
        g2.setColor(Palette.GOLD_WARM);
        String note = switch (state.getSelectedOption()) {
            case LANGUAGE -> "The words you type, from the next one on.";
            case MASTER -> "Scales every sound in the game.";
            case SFX -> "Bows, bolts, hits and chimes.";
            case MUSIC -> "The temple's soundtrack.";
            case BACK -> "Settings are saved as you change them.";
        };
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(note, CENTRE_X - fm.stringWidth(note) / 2, y + 14);

        if (state.getLanguage() == Language.KHMER && !FontManager.isKhmerAvailable()) {
            g2.setFont(hintFont);
            g2.setColor(Palette.alpha(Palette.DANGER, 0.9));
            String warn = "Khmer font missing - see fonts/README";
            g2.drawString(warn, CENTRE_X - g2.getFontMetrics().stringWidth(warn) / 2, y + 40);
        }
    }

    private void drawOptionsCard(Graphics2D g2, MenuState state, double glowPhase) {
        RoundRectangle2D card = new RoundRectangle2D.Double(
                OPT_CARD_X, OPT_CARD_Y, OPT_CARD_W, OPT_CARD_H, CARD_ARC, CARD_ARC);
        for (int i = 5; i >= 1; i--) {
            g2.setColor(new Color(0, 0, 0, 16));
            g2.fill(new RoundRectangle2D.Double(OPT_CARD_X - i, OPT_CARD_Y - i + 3,
                    OPT_CARD_W + i * 2, OPT_CARD_H + i * 2, CARD_ARC + i, CARD_ARC + i));
        }
        g2.setPaint(new LinearGradientPaint(
                OPT_CARD_X, OPT_CARD_Y, OPT_CARD_X, OPT_CARD_Y + OPT_CARD_H,
                new float[] {0f, 0.55f, 1f},
                new Color[] {
                        Palette.alpha(PANEL_TOP, 0.9),
                        Palette.alpha(PANEL_MID, 0.88),
                        Palette.alpha(PANEL_BOTTOM, 0.95)}));
        g2.fill(card);
        Ornament.drawStoneTexture(g2, card, 0.08);
        g2.setColor(Palette.alpha(Palette.GOLD, 0.55));
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(card);

        int size = 26;
        Ornament.drawCornerBracket(g2, OPT_CARD_X + 8, OPT_CARD_Y + 8, size, 0, Palette.GOLD, 0.8);
        Ornament.drawCornerBracket(g2, OPT_CARD_X + OPT_CARD_W - 8 - size, OPT_CARD_Y + 8,
                size, 1, Palette.GOLD, 0.8);
        Ornament.drawCornerBracket(g2, OPT_CARD_X + 8, OPT_CARD_Y + OPT_CARD_H - 8 - size,
                size, 2, Palette.GOLD, 0.8);
        Ornament.drawCornerBracket(g2, OPT_CARD_X + OPT_CARD_W - 8 - size,
                OPT_CARD_Y + OPT_CARD_H - 8 - size, size, 3, Palette.GOLD, 0.8);

        int cx = OPT_CARD_X + OPT_CARD_W / 2;
        DisplayText.drawCentred(g2, "OPTIONS", heroNameFont, cx, OPT_CARD_Y + 50,
                Palette.GOLD_LIGHT, Palette.GOLD_LIGHT, Palette.GOLD, 0.45f, 1f);
        Ornament.drawNagaDivider(g2, cx, OPT_CARD_Y + 82, 1.1, Palette.GOLD);

        for (MenuState.OptionRow row : MenuState.OptionRow.values()) {
            boolean selected = state.getSelectedOption() == row;
            if (row == MenuState.OptionRow.BACK) {
                drawOptionsBack(g2, state, selected, glowPhase);
            } else {
                drawOptionRow(g2, state, row, selected, glowPhase);
            }
        }
    }

    private void drawOptionRow(Graphics2D g2, MenuState state, MenuState.OptionRow row,
                               boolean selected, double glowPhase) {
        Rectangle r = optionRowBounds(row.ordinal());
        RoundRectangle2D plate = new RoundRectangle2D.Double(
                r.x, r.y, r.width, r.height, BUTTON_ARC, BUTTON_ARC);

        // The highlighted row is the one the arrow keys act on, so it has to be
        // unmistakable: a lit plate and a breathing gold edge.
        double pulse = 0.5 + 0.5 * Math.sin(glowPhase);
        g2.setColor(selected ? new Color(0x30, 0x20, 0x18, 200) : new Color(0x1E, 0x19, 0x14, 120));
        g2.fill(plate);
        g2.setColor(Palette.alpha(Palette.GOLD, selected ? 0.55 + 0.3 * pulse : 0.22));
        g2.setStroke(new BasicStroke(selected ? 1.8f : 1.1f));
        g2.draw(plate);

        int midY = r.y + r.height / 2;
        g2.setFont(optionLabelFont);
        g2.setColor(selected ? Palette.GOLD_LIGHT : Palette.GOLD_MID);
        String label = row.getLabel().toUpperCase(java.util.Locale.ROOT);
        FontMetrics fm = g2.getFontMetrics();
        int labelWidth = (int) Math.ceil(trackedWidth(fm, label, 2.0));
        drawTracked(g2, label, r.x + 28 + labelWidth / 2, midY + fm.getAscent() / 2 - 2, 2.0);
        if (selected) {
            Ornament.drawLotusFlame(g2, r.x + 14, midY, 0.7, false,
                    Palette.GOLD_LIGHT, Palette.GOLD_LIGHT);
        }

        if (row == MenuState.OptionRow.LANGUAGE) {
            drawLanguageSegments(g2, state, selected);
        } else {
            drawSlider(g2, state.sliderValue(row), midY, selected);
        }
    }

    private void drawLanguageSegments(Graphics2D g2, MenuState state, boolean rowSelected) {
        Language[] all = Language.values();
        for (int i = 0; i < all.length; i++) {
            Language language = all[i];
            boolean chosen = state.getLanguage() == language;
            Rectangle s = languageSegmentBounds(i);
            RoundRectangle2D seg = new RoundRectangle2D.Double(
                    s.x, s.y, s.width, s.height, BUTTON_ARC, BUTTON_ARC);
            if (chosen) {
                g2.setPaint(new LinearGradientPaint(s.x, s.y, s.x, s.y + s.height,
                        new float[] {0f, 0.5f, 1f},
                        new Color[] {PILL_TOP, PILL_MID, PILL_BOTTOM}));
                g2.fill(seg);
                g2.setColor(Palette.GOLD_LIGHT);
            } else {
                g2.setColor(new Color(0x18, 0x14, 0x0E, 200));
                g2.fill(seg);
                g2.setColor(Palette.alpha(Palette.GOLD, rowSelected ? 0.5 : 0.3));
            }
            g2.setStroke(new BasicStroke(1.3f));
            g2.draw(seg);

            // Each language names itself in its own script, so the Khmer entry
            // needs a Khmer-capable face or it draws as empty boxes.
            String name = language == Language.ENGLISH
                    ? language.getDisplayName().toUpperCase(java.util.Locale.ROOT)
                    : language.getDisplayName();
            Font font = language.requiresKhmerFont()
                    ? FontManager.uiFont(language, 16, Font.BOLD)
                    : optionValueFont;
            g2.setFont(font);
            g2.setColor(chosen ? Palette.STONE_DARK : Palette.GOLD_MID);
            FontMetrics fm = g2.getFontMetrics();
            int baseline = s.y + (s.height - fm.getHeight()) / 2 + fm.getAscent();
            if (language == Language.ENGLISH) {
                drawTracked(g2, name, s.x + s.width / 2, baseline, 2.0);
            } else {
                g2.drawString(name, s.x + (s.width - fm.stringWidth(name)) / 2, baseline);
            }
        }
    }

    private void drawSlider(Graphics2D g2, int value, int midY, boolean selected) {
        double t = (value - AudioSettings.MIN) / (double) (AudioSettings.MAX - AudioSettings.MIN);
        int trackW = OPT_TRACK_RIGHT - OPT_CONTROL_X;
        int trackH = 6;
        int fillW = (int) Math.round(trackW * t);

        g2.setColor(Palette.PROGRESS_TRACK);
        g2.fill(new RoundRectangle2D.Double(OPT_CONTROL_X, midY - trackH / 2.0,
                trackW, trackH, trackH, trackH));
        if (fillW > 0) {
            g2.setPaint(new GradientPaint(OPT_CONTROL_X, 0, Palette.GOLD_DIM,
                    OPT_TRACK_RIGHT, 0, Palette.GOLD_LIGHT));
            g2.fill(new RoundRectangle2D.Double(OPT_CONTROL_X, midY - trackH / 2.0,
                    fillW, trackH, trackH, trackH));
        }

        // Tick marks every quarter, so a value can be judged at a glance.
        g2.setColor(Palette.alpha(Palette.GOLD, 0.35));
        for (int i = 0; i <= 4; i++) {
            int x = OPT_CONTROL_X + trackW * i / 4;
            g2.fillRect(x, midY + 9, 1, 5);
        }

        double knobR = selected ? 11 : 9;
        double kx = OPT_CONTROL_X + fillW;
        if (selected) {
            g2.setColor(Palette.alpha(Palette.GOLD, 0.3));
            g2.fill(new java.awt.geom.Ellipse2D.Double(
                    kx - knobR - 5, midY - knobR - 5, (knobR + 5) * 2, (knobR + 5) * 2));
        }
        g2.setColor(selected ? Palette.GOLD_LIGHT : Palette.GOLD);
        g2.fill(new java.awt.geom.Ellipse2D.Double(kx - knobR, midY - knobR, knobR * 2, knobR * 2));
        g2.setColor(Palette.STONE_DARK);
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(new java.awt.geom.Ellipse2D.Double(kx - knobR, midY - knobR, knobR * 2, knobR * 2));

        g2.setFont(optionValueFont);
        g2.setColor(selected ? Palette.GOLD_LIGHT : Palette.GOLD_MID);
        String text = Integer.toString(value);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, OPT_CONTROL_RIGHT - fm.stringWidth(text), midY + fm.getAscent() / 2 - 2);
    }

    private void drawOptionsBack(Graphics2D g2, MenuState state, boolean selected,
                                 double glowPhase) {
        Rectangle r = optionBackBounds();
        drawButtonAt(g2, "BACK", r.x, r.y + (r.height - BUTTON_H) / 2, r.width,
                selected, glowPhase, pressFor(state, selected));
    }

    /** The Back button on the Options card. */
    public static Rectangle optionBackBounds() {
        Rectangle row = optionRowBounds(MenuState.OptionRow.BACK.ordinal());
        return new Rectangle(row.x + (row.width - OPT_BACK_W) / 2, row.y, OPT_BACK_W, row.height);
    }

    private void drawHeroEntries(Graphics2D g2, MenuState state, double glowPhase) {
        g2.setColor(Palette.alpha(Palette.GOLD_FAINT, 0.95));
        g2.setFont(hintFont);
        drawTracked(g2, "CHOOSE YOUR GUARDIAN", CENTRE_X, DIFFICULTY_HEADING_Y, 2.6);

        Hero[] heroes = Hero.values();
        for (int i = 0; i < heroes.length; i++) {
            boolean selected = state.getSelectedHero() == heroes[i];
            drawButton(g2,
                    heroes[i].getDisplayName().toUpperCase(java.util.Locale.ROOT),
                    entryBounds(i, MenuState.Screen.HERO).y,
                    selected, true, false, glowPhase, pressFor(state, selected));
        }

        int y = DIFFICULTY_ENTRIES_Y + heroes.length * ENTRY_PITCH;

        g2.setFont(taglineFont);
        g2.setColor(Palette.GOLD_WARM);
        String tagline = state.getSelectedHero().getTagline();
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(tagline, CENTRE_X - fm.stringWidth(tagline) / 2, y + 14);

        g2.setFont(hintFont);
        g2.setColor(Palette.alpha(Palette.GOLD_FAINT, 0.9));
        String next = "ENTER  ·  choose temple";
        g2.drawString(next, CENTRE_X - g2.getFontMetrics().stringWidth(next) / 2, y + 40);
        String back = "ESC  ·  back";
        g2.drawString(back, CENTRE_X - g2.getFontMetrics().stringWidth(back) / 2, y + 60);
    }

    private void drawHeroCards(Graphics2D g2, MenuState state, SpriteCache sprites,
                               double glowPhase) {
        Hero[] heroes = Hero.values();
        for (int i = 0; i < heroes.length; i++) {
            drawHeroCard(g2, sprites, heroes[i], heroCardBounds(i),
                    state.getSelectedHero() == heroes[i], glowPhase);
        }
    }

    private void drawTempleEntries(Graphics2D g2, MenuState state, double glowPhase) {
        g2.setColor(Palette.alpha(Palette.GOLD_FAINT, 0.95));
        g2.setFont(hintFont);
        drawTracked(g2, "CHOOSE YOUR TEMPLE", CENTRE_X, DIFFICULTY_HEADING_Y, 2.6);

        TempleMap[] temples = TempleMap.values();
        for (int i = 0; i < temples.length; i++) {
            boolean selected = state.getSelectedTemple() == temples[i];
            drawButton(g2,
                    temples[i].getDisplayName().toUpperCase(java.util.Locale.ROOT),
                    entryBounds(i, MenuState.Screen.TEMPLE).y,
                    selected, true, false, glowPhase, pressFor(state, selected));
        }

        int y = DIFFICULTY_ENTRIES_Y + temples.length * ENTRY_PITCH;
        g2.setFont(taglineFont);
        g2.setColor(Palette.GOLD_WARM);
        String tagline = state.getSelectedTemple().getTagline();
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(tagline, CENTRE_X - fm.stringWidth(tagline) / 2, y + 14);

        g2.setFont(hintFont);
        g2.setColor(Palette.alpha(Palette.GOLD_FAINT, 0.9));
        String next = state.isHeroForSandbox() ? "ENTER  ·  defend temple" : "ENTER  ·  choose trial";
        g2.drawString(next, CENTRE_X - g2.getFontMetrics().stringWidth(next) / 2, y + 40);
        String back = "ESC  ·  back";
        g2.drawString(back, CENTRE_X - g2.getFontMetrics().stringWidth(back) / 2, y + 60);
    }

    private void drawTempleCards(Graphics2D g2, MenuState state, SpriteCache sprites,
                                 double glowPhase) {
        TempleMap[] temples = TempleMap.values();
        for (int i = 0; i < temples.length; i++) {
            drawTempleCard(g2, sprites, temples[i], templeCardBounds(i),
                    state.getSelectedTemple() == temples[i], glowPhase);
        }
    }

    /** A wide, cinematic preview that also acts as a large mouse target. */
    private void drawTempleCard(Graphics2D g2, SpriteCache sprites, TempleMap temple,
                                Rectangle card, boolean selected, double glowPhase) {
        double pulse = 0.5 + 0.5 * Math.sin(glowPhase);
        RoundRectangle2D plate = new RoundRectangle2D.Double(
                card.x, card.y, card.width, card.height, CARD_ARC, CARD_ARC);

        if (selected) {
            g2.setColor(Palette.alpha(Palette.GOLD, 0.18 + pulse * 0.12));
            g2.setStroke(new BasicStroke(9f));
            g2.draw(plate);
        }

        g2.setColor(Palette.STONE_DARK);
        g2.fill(plate);

        BufferedImage image = sprites.background(temple);
        if (image != null) {
            Graphics2D preview = (Graphics2D) g2.create();
            try {
                preview.clip(new RoundRectangle2D.Double(card.x, card.y,
                        card.width, TEMPLE_IMAGE_H, CARD_ARC, CARD_ARC));
                preview.drawImage(image, card.x, card.y, card.width, TEMPLE_IMAGE_H, null);
            } finally {
                preview.dispose();
            }
        }

        // A dark foot gives both bright and dark previews the same readable label.
        g2.setPaint(new GradientPaint(card.x, card.y + TEMPLE_IMAGE_H - 25,
                Palette.alpha(Palette.STONE_DARK, 0), card.x, card.y + card.height,
                Palette.STONE_DARK));
        g2.fill(new RoundRectangle2D.Double(card.x, card.y + TEMPLE_IMAGE_H - 25,
                card.width, card.height - TEMPLE_IMAGE_H + 25, CARD_ARC, CARD_ARC));

        if (!selected) {
            g2.setColor(Palette.alpha(Palette.STONE_DARK, 0.42));
            g2.fill(plate);
        }

        g2.setColor(Palette.alpha(Palette.GOLD, selected ? 0.9 : 0.36));
        g2.setStroke(new BasicStroke(selected ? 2f : 1.2f));
        g2.draw(plate);

        int cx = card.x + card.width / 2;
        DisplayText.drawCentred(g2,
                temple.getDisplayName().toUpperCase(java.util.Locale.ROOT),
                heroNameFont, cx, card.y + 263,
                selected ? Palette.GOLD_LIGHT : Palette.GOLD_DIM,
                selected ? Palette.GOLD_LIGHT : Palette.GOLD_DIM,
                selected ? Palette.GOLD : null, 0.4f, 1f);

        g2.setFont(heroEpithetFont);
        g2.setColor(selected ? Palette.GOLD_WARM : Palette.GOLD_GHOST);
        FontMetrics fm = g2.getFontMetrics();
        String tagline = temple.getTagline();
        g2.drawString(tagline, cx - fm.stringWidth(tagline) / 2, card.y + 291);
    }

    /**
     * One hero, full length, on a stone card.
     *
     * <p>The selected hero is shown mid-attack. One with left and right poses
     * sways between them, crossfading so the turn reads as a dance rather than
     * a cut; the other hero stands idle, dimmed, so which one Enter will pick
     * is never in doubt.
     */
    private void drawHeroCard(Graphics2D g2, SpriteCache sprites, Hero hero,
                              Rectangle card, boolean selected, double glowPhase) {
        double pulse = 0.5 + 0.5 * Math.sin(glowPhase);
        RoundRectangle2D plate = new RoundRectangle2D.Double(
                card.x, card.y, card.width, card.height, CARD_ARC, CARD_ARC);

        if (selected) {
            Graphics2D glow = (Graphics2D) g2.create();
            try {
                glow.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, (float) (0.18 + 0.14 * pulse)));
                glow.setColor(Palette.GOLD);
                for (int i = 5; i >= 1; i--) {
                    glow.setStroke(new BasicStroke(i * 3f));
                    glow.draw(new RoundRectangle2D.Double(card.x - i, card.y - i,
                            card.width + i * 2, card.height + i * 2,
                            CARD_ARC + i, CARD_ARC + i));
                }
            } finally {
                glow.dispose();
            }
        }

        g2.setPaint(new LinearGradientPaint(
                card.x, card.y, card.x, card.y + card.height,
                new float[] {0f, 0.55f, 1f},
                new Color[] {
                        Palette.alpha(PANEL_TOP, selected ? 0.82 : 0.7),
                        Palette.alpha(PANEL_MID, selected ? 0.78 : 0.66),
                        Palette.alpha(PANEL_BOTTOM, 0.94)}));
        g2.fill(plate);
        Ornament.drawStoneTexture(g2, plate, 0.08);

        // A warm light behind the figure, so a dark-robed hero still separates
        // from a dark card.
        int cx = card.x + card.width / 2;
        int feetY = card.y + CARD_FEET_OFFSET;
        int figureH = SpriteCache.HERO_PORTRAIT_HEIGHT;
        g2.setPaint(new java.awt.RadialGradientPaint(
                new java.awt.geom.Point2D.Double(cx, feetY - figureH * 0.45),
                figureH * 0.55f,
                new float[] {0f, 1f},
                new Color[] {
                        Palette.alpha(Palette.GOLD, selected ? 0.22 + 0.06 * pulse : 0.08),
                        Palette.alpha(Palette.GOLD, 0)}));
        g2.fill(plate);

        drawHeroFigure(g2, sprites, hero, selected, cx, feetY, figureH, glowPhase);

        // The hero not chosen is dimmed with stone laid over the whole card,
        // not by drawing the figure translucent — a see-through figure lets the
        // moon and temple show through their body.
        if (!selected) {
            g2.setColor(Palette.alpha(Palette.STONE_DARK, 0.5));
            g2.fill(plate);
        }

        // Frame and corners over the figure, so scarves reaching the edge are
        // tucked behind the stone rather than spilling past it.
        g2.setColor(Palette.alpha(Palette.GOLD, selected ? 0.85 : 0.35));
        g2.setStroke(new BasicStroke(selected ? 2f : 1.3f));
        g2.draw(plate);
        double opacity = selected ? 0.9 : 0.4;
        int size = 26;
        Ornament.drawCornerBracket(g2, card.x + 8, card.y + 8, size, 0, Palette.GOLD, opacity);
        Ornament.drawCornerBracket(g2, card.x + card.width - 8 - size, card.y + 8, size, 1,
                Palette.GOLD, opacity);
        Ornament.drawCornerBracket(g2, card.x + 8, card.y + card.height - 8 - size, size, 2,
                Palette.GOLD, opacity);
        Ornament.drawCornerBracket(g2, card.x + card.width - 8 - size,
                card.y + card.height - 8 - size, size, 3, Palette.GOLD, opacity);

        // Name plate.
        Ornament.drawGoldRule(g2, cx, card.y + CARD_NAME_OFFSET - 26, card.width * 0.7,
                Palette.GOLD, selected ? 0.7 : 0.35);
        DisplayText.drawCentred(g2, hero.getDisplayName().toUpperCase(java.util.Locale.ROOT),
                heroNameFont, cx, card.y + CARD_NAME_OFFSET,
                selected ? Palette.GOLD_LIGHT : Palette.GOLD_DIM,
                selected ? Palette.GOLD_LIGHT : Palette.GOLD_DIM,
                selected ? Palette.GOLD : null, 0.45f, 1f);

        g2.setFont(heroEpithetFont);
        g2.setColor(selected ? Palette.GOLD_WARM : Palette.GOLD_GHOST);
        FontMetrics fm = g2.getFontMetrics();
        String epithet = hero.getEpithet();
        g2.drawString(epithet, cx - fm.stringWidth(epithet) / 2,
                card.y + CARD_EPITHET_OFFSET);

        drawHeroCrest(g2, sprites, hero, cx, card.y + 34, selected);
    }

    private void drawHeroFigure(Graphics2D g2, SpriteCache sprites, Hero hero,
                                boolean selected, int cx, int feetY, int height,
                                double glowPhase) {
        if (!selected) {
            drawPortrait(g2, sprites, hero, SpriteCache.Pose.IDLE, cx, feetY, height, 1f);
            return;
        }
        if (!hero.hasDirectionalAttack()) {
            drawPortrait(g2, sprites, hero, SpriteCache.Pose.ATTACK_RIGHT,
                    cx, feetY, height, 1f);
            return;
        }
        // Holds each side for most of the cycle and crossfades through the
        // middle — a steep sine, clamped.
        double toRight = Math.max(0, Math.min(1, 0.5 + Math.sin(glowPhase) * 2.2));
        if (toRight < 1) {
            drawPortrait(g2, sprites, hero, SpriteCache.Pose.ATTACK_LEFT,
                    cx, feetY, height, (float) (1 - toRight));
        }
        if (toRight > 0) {
            drawPortrait(g2, sprites, hero, SpriteCache.Pose.ATTACK_RIGHT,
                    cx, feetY, height, (float) toRight);
        }
    }

    private void drawPortrait(Graphics2D g2, SpriteCache sprites, Hero hero,
                              SpriteCache.Pose pose, int cx, int feetY, int height,
                              float alpha) {
        BufferedImage image = sprites.heroPortrait(hero, pose);
        int width = sprites.heroPortraitWidth(hero, pose, height);
        // Wide poses are narrowed to fit the card rather than cropped, so the
        // whole figure and her scarves always show.
        int maxWidth = CARD_W - 24;
        if (width > maxWidth) {
            height = (int) Math.round(height * maxWidth / (double) width);
            width = maxWidth;
        }
        Graphics2D pg = (Graphics2D) g2.create();
        try {
            pg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            if (image != null) {
                pg.drawImage(image, cx - width / 2, feetY - height, width, height, null);
            } else {
                pg.setColor(Palette.STONE_MID);
                pg.fill(new RoundRectangle2D.Double(
                        cx - width / 2.0, feetY - height, width, height, 20, 20));
            }
        } finally {
            pg.dispose();
        }
    }

    /**
     * A small emblem at the top of the card: the hero's crest from their own
     * effect sheet when it has one (Apsara's ruby lotus), otherwise the
     * lotus-bud prang the rest of the interface already uses.
     */
    private void drawHeroCrest(Graphics2D g2, SpriteCache sprites, Hero hero,
                               int cx, int cy, boolean selected) {
        Graphics2D cg = (Graphics2D) g2.create();
        try {
            cg.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, selected ? 1f : 0.45f));
            BufferedImage crest = sprites.shotFx(hero, SpriteCache.ShotFx.CREST);
            if (crest != null) {
                int h = 40;
                int w = (int) Math.round(h * crest.getWidth() / (double) crest.getHeight());
                cg.drawImage(crest, cx - w / 2, cy - h / 2, w, h, null);
            } else {
                cg.setColor(Palette.GOLD);
                cg.fill(Ornament.budPath(cx, cy + 16, 20, 34));
            }
        } finally {
            cg.dispose();
        }
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
     * @param unbuilt whether to badge the plate SOON. Kept generic for future
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

    /** A button at an arbitrary position — selected is gold, otherwise stone. */
    private void drawButtonAt(Graphics2D g2, String label, int x, int y, int width,
                              boolean selected, double glowPhase, double pressProgress) {
        int top = y + (int) Math.round(2 * pressProgress);
        if (selected) {
            drawPrimaryButton(g2, label, x, top, width, glowPhase, pressProgress);
        } else {
            drawSecondaryButton(g2, label, x, top, width, false, true, false);
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
