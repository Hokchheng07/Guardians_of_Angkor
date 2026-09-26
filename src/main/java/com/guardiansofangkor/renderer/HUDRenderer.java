package com.guardiansofangkor.renderer;

import com.guardiansofangkor.engine.ComboTracker;
import com.guardiansofangkor.engine.GameState;
import com.guardiansofangkor.engine.IntroSequence;
import com.guardiansofangkor.engine.LevelPreview;
import com.guardiansofangkor.engine.PowerUpState;
import com.guardiansofangkor.engine.WaveManager;
import com.guardiansofangkor.entities.PowerUpType;
import com.guardiansofangkor.i18n.FontManager;
import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.util.GameConfig;
import com.guardiansofangkor.util.Platform;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Locale;

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

    /** Point size of a countdown numeral at rest. */
    private static final float COUNT_SIZE = 150f;

    /**
     * Point size of the DEFEND flash. Smaller than a numeral because it is six
     * characters wide, so matching the numeral's size would run off the screen.
     */
    private static final float DEFEND_SIZE = 92f;

    private static final Color COUNT_FILL_TOP = new Color(0xFF, 0xF1, 0xC4);
    private static final Color COUNT_FILL_BOTTOM = new Color(0xE0, 0xAE, 0x3C);

    /** Height of one row in the active-boon strip. */
    private static final int BOON_ROW_HEIGHT = 26;

    /** Width of the strip's drain bars. */
    private static final int BOON_BAR_WIDTH = 96;

    /** Where the boon strip sits below the progress bar. */
    private static final int BOON_STRIP_TOP = BAR_HEIGHT + 16;

    private Font microFont;
    private Font displayFont;
    private Font secondaryFont;
    private Font bannerFont;
    private Font bodyFont;
    private Font hintFont;
    private Font titleFont;
    private Font countFont;
    private Font defendFont;

    // ---- end-of-run modal ---------------------------------------------------
    //
    // Its own faces rather than the HUD's, because the modal is the one screen
    // in the game that is read rather than glanced at, and the design specifies
    // three faces for it: a decorative display cut for the headline and the
    // numbers, plain Cinzel for the tracked labels, Garamond for the prose.

    private Font modalTitleFont;
    private Font modalPreTitleFont;
    private Font modalBadgeFont;
    private Font modalStatLabelFont;
    private Font modalStatValueFont;
    private Font modalStatBigFont;
    private Font modalNoteFont;
    private Font modalFootnoteFont;

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
        // Base sizes only — drawCountNumeral derives these per frame, so the
        // animation re-rasterises at its true size instead of upscaling.
        this.countFont = FontManager.uiFont(language, (int) COUNT_SIZE, Font.BOLD);
        this.defendFont = FontManager.uiFont(language, (int) DEFEND_SIZE, Font.BOLD);

        this.modalTitleFont = FontManager.displayFont(34, Font.BOLD);
        this.modalPreTitleFont = FontManager.bodyFont(13, Font.ITALIC);
        this.modalBadgeFont = FontManager.uiSerifFont(10, Font.BOLD);
        this.modalStatLabelFont = FontManager.uiSerifFont(9, Font.BOLD);
        this.modalStatValueFont = FontManager.displayFont(22, Font.BOLD);
        this.modalStatBigFont = FontManager.displayFont(26, Font.BOLD);
        this.modalNoteFont = FontManager.bodyFont(11, Font.ITALIC);
        this.modalFootnoteFont = FontManager.bodyFont(12, Font.ITALIC);
    }

    /**
     * @param restartArmed true while Tab has been pressed and Enter would restart
     */
    public void draw(Graphics2D g2, GameState state, boolean restartArmed) {
        drawTopBar(g2, state);

        // The opening beat owns the screen — no level banner underneath it.
        if (state.isIntroActive()) {
            drawIntro(g2, state.getIntro());
            return;
        }

        drawBoonStrip(g2, state.getPowerUpState());

        drawLevelBanner(g2, state);
        if (state.isGameOver()) {
            drawGameOver(g2, state, restartArmed);
        } else if (state.isPaused()) {
            drawPauseOverlay(g2);
        } else if (restartArmed) {
            drawRestartPrompt(g2);
        }
    }

    /**
     * The opening loading bar and countdown.
     *
     * <p>Drawn over a scrim rather than replacing the scene, so the player can
     * already see the temple they are about to defend while the count runs.
     */
    private void drawIntro(Graphics2D g2, IntroSequence intro) {
        if (intro == null) {
            return;
        }
        int centerX = GameConfig.SCREEN_WIDTH / 2;
        int centerY = GameConfig.SCREEN_HEIGHT / 2;

        // Scrim lifts during the countdown so the scene reads through more
        // strongly as play approaches.
        float scrim = intro.getPhase() == IntroSequence.Phase.LOADING ? 0.82f : 0.52f;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, scrim));
        g2.setColor(Palette.SCRIM);
        g2.fillRect(0, 0, GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT);
        g2.setComposite(AlphaComposite.SrcOver);

        switch (intro.getPhase()) {
            case LOADING -> drawLoading(g2, intro, centerX, centerY);
            case COUNTDOWN -> drawCountNumeral(g2, intro,
                    Integer.toString(intro.getCount()), centerX, centerY);
            case GO -> drawCountNumeral(g2, intro, "DEFEND", centerX, centerY);
            case DONE -> {
                // Nothing left to draw.
            }
        }
    }

    private void drawLoading(Graphics2D g2, IntroSequence intro,
                             int centerX, int centerY) {
        int barWidth = 340;
        int barHeight = 6;
        int barX = centerX - barWidth / 2;
        int barY = centerY + 18;

        g2.setFont(bodyFont);
        FontMetrics fm = g2.getFontMetrics();
        String label = intro.getLoadingLabel();
        g2.setColor(Palette.HUD_TEXT_DIM);
        g2.drawString(label, centerX - fm.stringWidth(label) / 2, centerY - 16);

        // Three prangs above the bar, tying the wait to the game's own motif.
        g2.setColor(Palette.alpha(Palette.HUD_DIVIDER, 0.75));
        Ornament.drawTempleDivider(g2, centerX, centerY - 46, 220);

        g2.setColor(Palette.PROGRESS_TRACK);
        g2.fill(new RoundRectangle2D.Double(barX, barY, barWidth, barHeight, 3, 3));

        int fill = (int) Math.round(barWidth * intro.getLoadingProgress());
        if (fill > 0) {
            g2.setPaint(new GradientPaint(
                    barX, barY, Palette.HUD_DIVIDER,
                    barX + barWidth, barY, Palette.HUD_TEXT_GOLD));
            g2.fill(new RoundRectangle2D.Double(barX, barY, fill, barHeight, 3, 3));
        }

        g2.setColor(Palette.alpha(Palette.HUD_DIVIDER, 0.4));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Double(barX, barY, barWidth, barHeight, 3, 3));
    }

    /**
     * One counted beat, punched large on arrival and settling as it plays out.
     *
     * <p>The size is animated by deriving the font, not by scaling the graphics
     * context. Scaling the context upscales a rasterised glyph bitmap, which is
     * what made this look pixelated; deriving the font re-rasterises the outline
     * at its true size every frame.
     */
    private void drawCountNumeral(Graphics2D g2, IntroSequence intro,
                                  String text, int centerX, int centerY) {
        double t = intro.getBeatProgress();

        // Overshoot on arrival, then settle. Eased sharply so the punch lands in
        // the first few frames rather than drifting.
        double scale = 1.30 - 0.30 * Math.min(1.0, t * 2.6);

        // Hold, then fade over the last quarter of the beat.
        float alpha = (float) Math.max(0.0, 1.0 - Math.max(0.0, t - 0.74) / 0.26);

        boolean isWord = text.length() > 2;
        float baseSize = isWord ? DEFEND_SIZE : COUNT_SIZE;
        Font font = (isWord ? defendFont : countFont)
                .deriveFont((float) (baseSize * scale));

        DisplayText.drawCentred(g2, text, font, centerX, centerY,
                COUNT_FILL_TOP, COUNT_FILL_BOTTOM,
                Palette.HUD_TEXT_GOLD, 1.0f, alpha);
    }

    /**
     * Full-screen pause state.
     *
     * <p>The game loop keeps ticking while paused so this can be drawn — without
     * it the screen simply freezes, which is indistinguishable from a hang.
     */
    private void drawPauseOverlay(Graphics2D g2) {
        g2.setColor(Palette.SCRIM);
        g2.fillRect(0, 0, GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT);

        int centerX = GameConfig.SCREEN_WIDTH / 2;
        int centerY = GameConfig.SCREEN_HEIGHT / 2;

        g2.setFont(titleFont);
        FontMetrics fm = g2.getFontMetrics();
        String title = "Paused";
        int titleWidth = fm.stringWidth(title);

        int panelWidth = 420;
        int panelHeight = 210;
        RoundRectangle2D panel = new RoundRectangle2D.Double(
                centerX - panelWidth / 2.0, centerY - panelHeight / 2.0,
                panelWidth, panelHeight, 20, 20);

        g2.setColor(Palette.HUD_BG);
        g2.fill(panel);
        g2.setColor(Palette.HUD_DIVIDER);
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(panel);

        g2.setColor(Palette.HUD_TEXT_GOLD);
        g2.drawString(title, centerX - titleWidth / 2, centerY - 34);

        g2.setColor(Palette.HUD_DIVIDER_SOFT);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(centerX - 110, centerY - 16, centerX + 110, centerY - 16);

        // The label follows the platform, so macOS players are told Cmd and
        // everyone else is told Ctrl.
        drawKeyHint(g2, centerX, centerY + 22, Platform.pauseShortcutLabel(),
                "resume", Palette.HUD_TEXT_GOLD);
        drawKeyHint(g2, centerX, centerY + 66, "ESC", "quit", Palette.HUD_TEXT_DIM);
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
        x = drawStat(g2, "BEST", Integer.toString(state.getBestScore()),
                x, secondaryFont, Palette.HUD_TEXT_WHITE, SECONDARY_LABEL_ALPHA);

        drawCombo(g2, state);

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
     * The unbroken run of perfectly typed words, and the multiplier it is worth.
     *
     * <p>Not part of the stat row, and appears only once the run is long enough
     * to mean something. The other stats are steady facts about the game that
     * are always true and always in the same place; this one is a live streak
     * that vanishes the instant it is lost, and putting it in the row would make
     * four fixed columns jump sideways every time somebody mistyped.
     *
     * <p>Sits below the bar on the right, opposite the lives, where the eye
     * already goes for "how am I doing" — and where it can flash without
     * disturbing anything that has to stay readable.
     */
    private void drawCombo(Graphics2D g2, GameState state) {
        ComboTracker combo = state.getCombo();
        if (!combo.isWorthShowing()) {
            return;
        }

        String value = combo.getCount() + "x";
        String multiplier = String.format(java.util.Locale.ROOT,
                "%.2f SCORE", combo.getMultiplier());

        int right = GameConfig.SCREEN_WIDTH - 28;
        int top = BAR_HEIGHT + 26;

        // Warms from gold toward white as the multiplier approaches its ceiling,
        // so a maxed combo is visibly different from a merely good one without
        // needing a second number read.
        double fill = combo.getFillFraction();
        Color hot = Palette.blend(Palette.HUD_TEXT_GOLD, Palette.HUD_TEXT_WHITE, fill);

        g2.setFont(secondaryFont);
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(hot);
        g2.drawString(value, right - fm.stringWidth(value), top);

        g2.setFont(microFont);
        FontMetrics micro = g2.getFontMetrics();
        g2.setColor(Palette.alpha(Palette.HUD_TEXT_DIM, SECONDARY_LABEL_ALPHA));
        g2.drawString(multiplier, right - micro.stringWidth(multiplier), top + 16);
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
        int halves = state.getHalfLives();
        for (int i = 0; i < total; i++) {
            // Two halves per bud: full if both are held, half if one is, empty
            // if neither. Comparing against the half count directly means the
            // pip and the life total can never disagree.
            int heldHere = Math.max(0, Math.min(GameConfig.HALVES_PER_LIFE,
                    halves - i * GameConfig.HALVES_PER_LIFE));
            drawLotusBud(g2, firstX + i * (size + gap), y, size, heldHere);
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
     *
     * <p>Half a life fills the LEFT half of the same silhouette, by clipping the
     * gold to the left of the tower's centre before filling it. Splitting an
     * existing pip is what keeps the bar readable — six small pips would be
     * exact and require actual counting, where three towers with one of them
     * half dark is a shape the eye reads at a glance.
     *
     * @param heldHalves 0, 1 or 2
     */
    private void drawLotusBud(Graphics2D g2, int x, int y, int size, int heldHalves) {
        // Shared with the menu's title divider, so the two never drift apart.
        Path2D bud = Ornament.budPath(
                x + size / 2.0, y + size * 0.94, size * 0.52, size * 0.80);
        Path2D left = Ornament.budPath(
                x + size * 0.20, y + size * 0.96, size * 0.30, size * 0.48);
        Path2D right = Ornament.budPath(
                x + size * 0.80, y + size * 0.96, size * 0.30, size * 0.48);

        // The empty silhouette is always drawn, so a half pip has a dark right
        // side to be half of rather than simply being a smaller pip.
        g2.setColor(Palette.LIFE_LOST);
        g2.setStroke(new BasicStroke(1.4f));
        g2.draw(left);
        g2.draw(right);
        g2.draw(bud);
        g2.drawLine((int) (x + size * 0.10), (int) (y + size * 0.94),
                (int) (x + size * 0.90), (int) (y + size * 0.94));

        if (heldHalves <= 0) {
            return;
        }

        Graphics2D lit = (Graphics2D) g2.create();
        try {
            if (heldHalves < GameConfig.HALVES_PER_LIFE) {
                lit.clipRect(x, y, (int) Math.round(size / 2.0), size + 2);
            }
            lit.setColor(Palette.LIFE_FILLED);
            lit.fill(left);
            lit.fill(right);
            lit.fill(bud);

            // Plinth, so the towers sit on something.
            lit.fillRect((int) (x + size * 0.10), (int) (y + size * 0.90),
                    (int) (size * 0.80), Math.max(2, (int) (size * 0.09)));
        } finally {
            lit.dispose();
        }
    }

    // ---- power-ups ---------------------------------------------------------

    /**
     * Running boons and banked shield charges, stacked down the left edge.
     *
     * <p>Left rather than on the top bar because the bar is already at capacity
     * and because this list changes size. Something that appears and disappears
     * has to live somewhere it can grow without pushing a permanent stat around
     * — a HUD whose numbers move as boons come and go is unreadable at speed.
     *
     * <p>Nothing is drawn when nothing is active, so a player who never picks up
     * a boon never sees this at all.
     */
    private void drawBoonStrip(Graphics2D g2, PowerUpState powerUps) {
        if (powerUps == null || !powerUps.hasAnything()) {
            return;
        }

        int y = BOON_STRIP_TOP;

        if (powerUps.hasShield()) {
            drawShieldRow(g2, powerUps.getShieldCharges(), EDGE_PADDING, y);
            y += BOON_ROW_HEIGHT;
        }

        List<PowerUpState.Active> active = powerUps.getActive();
        for (PowerUpState.Active boon : active) {
            drawBoonRow(g2, boon, EDGE_PADDING, y);
            y += BOON_ROW_HEIGHT;
        }
    }

    /** One timed boon: pip, name, drain bar, seconds left. */
    private void drawBoonRow(Graphics2D g2, PowerUpState.Active boon, int x, int y) {
        Color accent = Palette.powerUp(boon.type());
        int centreY = y + BOON_ROW_HEIGHT / 2;

        g2.setColor(accent);
        g2.fill(new Ellipse2D.Double(x, centreY - 5, 10, 10));

        g2.setFont(microFont);
        FontMetrics fm = g2.getFontMetrics();
        String name = boon.type().getDisplayName().toUpperCase(java.util.Locale.ROOT);
        int textX = x + 18;
        g2.setColor(Palette.HUD_TEXT_WHITE);
        g2.drawString(name, textX, centreY + fm.getAscent() / 2 - 1);

        int barX = textX + Math.max(96, fm.stringWidth(name) + 12);
        int barY = centreY - 3;

        g2.setColor(Palette.PROGRESS_TRACK);
        g2.fillRect(barX, barY, BOON_BAR_WIDTH, 6);

        int fill = (int) Math.round(BOON_BAR_WIDTH * boon.fraction());
        if (fill > 0) {
            g2.setColor(accent);
            g2.fillRect(barX, barY, fill, 6);
        }

        g2.setColor(Palette.HUD_TEXT_DIM);
        g2.drawString(boon.secondsLeft() + "s",
                barX + BOON_BAR_WIDTH + 10, centreY + fm.getAscent() / 2 - 1);
    }

    /**
     * Banked Naga Shield charges, drawn as rings rather than a bar.
     *
     * <p>Deliberately a different shape from the timed rows: a charge does not
     * drain, it is spent, and giving it a countdown bar would say the opposite.
     */
    private void drawShieldRow(Graphics2D g2, int charges, int x, int y) {
        Color accent = Palette.powerUp(PowerUpType.NAGA_SHIELD);
        int centreY = y + BOON_ROW_HEIGHT / 2;

        g2.setFont(microFont);
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Palette.HUD_TEXT_WHITE);
        g2.drawString("WARD", x + 18, centreY + fm.getAscent() / 2 - 1);

        g2.setColor(accent);
        g2.fill(new Ellipse2D.Double(x, centreY - 5, 10, 10));

        int ringX = x + 18 + fm.stringWidth("WARD") + 14;
        g2.setStroke(new BasicStroke(2f));
        for (int i = 0; i < GameConfig.MAX_SHIELD_CHARGES; i++) {
            double cx = ringX + i * 18;
            Ellipse2D ring = new Ellipse2D.Double(cx, centreY - 6, 12, 12);
            if (i < charges) {
                g2.setColor(accent);
                g2.fill(ring);
            } else {
                g2.setColor(Palette.LIFE_LOST);
                g2.draw(ring);
            }
        }
    }

    // ---- banners -----------------------------------------------------------

    private void drawLevelBanner(Graphics2D g2, GameState state) {
        WaveManager waves = state.getWaveManager();
        if (!waves.isIntermission() || state.isGameOver()) {
            return;
        }
        // The last wave leaves the manager permanently in intermission — there
        // is no level 16 to count down to. Without this the banner would sit
        // over the finale for its whole length promising a level that is never
        // coming.
        if (state.isBossActive()) {
            return;
        }
        int next = waves.getLevel() + 1;

        String text = waves.getLevel() == 0
                ? "Defend the temple"
                : "Level " + waves.getLevel() + " cleared";
        String sub = waves.getLevel() == 0
                ? "Type the words above the spirits"
                : "Level " + next + " approaching";

        LevelPreview preview = LevelPreview.forLevel(next, state.getDifficulty());
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

    /** Modal card geometry — the design's constants, matching the engine's. */
    private static final int MODAL_W = 620;
    private static final int MODAL_H = 552;
    private static final int MODAL_TOP = 66;
    private static final int MODAL_ARC = 20;
    private static final int MODAL_PAD_X = 36;

    /** Stat grid: two columns, four rows, one cell per statistic. */
    private static final int STAT_COLUMNS = 2;
    private static final int STAT_ROWS = 4;

    /**
     * The end-of-run card, shared by a won run and a lost one.
     *
     * <p>Fires once, when the run ends — not per level. The two outcomes share
     * every pixel of the layout and differ only in the headline, the accent and
     * the badge, because they are the same information about the same run. What
     * they must never share is the wording or the colour: congratulating a
     * player in the defeat palette, or commiserating in gold, is the one mistake
     * on this screen nobody would forgive.
     */
    private void drawGameOver(Graphics2D g2, GameState state, boolean restartArmed) {
        // The design blurs the backdrop. That is a full-screen convolve every
        // frame, which is precisely the per-frame cost the render pass was just
        // rebuilt to avoid, so this darkens instead — the purpose is to push the
        // play field back, and a scrim does that without the frame budget.
        g2.setColor(Palette.MODAL_SCRIM);
        g2.fillRect(0, 0, GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT);

        final int centerX = GameConfig.SCREEN_WIDTH / 2;
        final int panelX = centerX - MODAL_W / 2;
        final int panelY = MODAL_TOP;

        boolean won = state.isVictory();
        Color accent = won ? Palette.GOLD : Palette.DANGER;
        Color accentLight = won ? Palette.GOLD_LIGHT : Palette.DANGER_LIGHT;

        drawModalCard(g2, panelX, panelY, accent);

        int y = panelY + 28;
        y = drawModalHeader(g2, state, centerX, y, won, accent, accentLight);

        // Centred 18 below the badge rather than 12: the divider is 36 tall, so
        // a smaller step overlaps the pill's bottom edge by a couple of pixels.
        Ornament.drawNagaDivider(g2, centerX, y + 18, 1.0, accent);
        y += 38;

        y = drawStatGrid(g2, state, panelX + MODAL_PAD_X, y,
                MODAL_W - MODAL_PAD_X * 2, accent, accentLight);

        y += 14;
        Ornament.drawGoldRule(g2, centerX, y, MODAL_W - MODAL_PAD_X * 2, accent, 0.6);
        y += 20;

        drawModalActions(g2, centerX, y, restartArmed, accent);
        y += 52;

        drawModalFootnote(g2, state, centerX, y, won);
    }

    /** The card itself: gradient fill, top accent bar, corner brackets. */
    private void drawModalCard(Graphics2D g2, int x, int y, Color accent) {
        RoundRectangle2D card = new RoundRectangle2D.Double(
                x, y, MODAL_W, MODAL_H, MODAL_ARC, MODAL_ARC);

        // Outer drop shadow, so the card lifts off the play field behind it.
        for (int i = 6; i >= 1; i--) {
            g2.setColor(new Color(0, 0, 0, 18));
            g2.fill(new RoundRectangle2D.Double(
                    x - i, y - i + 4, MODAL_W + i * 2, MODAL_H + i * 2,
                    MODAL_ARC + i, MODAL_ARC + i));
        }

        g2.setPaint(new LinearGradientPaint(
                x, y, x + MODAL_W * 0.35f, y + MODAL_H,
                new float[] {0f, 0.45f, 1f},
                new Color[] {
                    Palette.STONE_LIGHT, Palette.STONE_MODAL_MID, Palette.STONE_MODAL_LOW,
                }));
        g2.fill(card);

        Ornament.drawStoneTexture(g2, card, 0.12);

        g2.setColor(Palette.alpha(accent, 0.35));
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(card);

        // The 3px accent bar across the top edge, clipped to the card so it
        // follows the rounded corners instead of squaring them off.
        Graphics2D bar = (Graphics2D) g2.create();
        try {
            bar.clip(card);
            bar.setPaint(new LinearGradientPaint(
                    x, y, x + MODAL_W, y,
                    new float[] {0f, 0.3f, 0.5f, 0.7f, 1f},
                    new Color[] {
                        Palette.alpha(accent, 0),
                        accent,
                        Palette.blend(accent, Color.WHITE, 0.35),
                        accent,
                        Palette.alpha(accent, 0),
                    }));
            bar.fillRect(x, y, MODAL_W, 3);
        } finally {
            bar.dispose();
        }

        // L-brackets, brighter at the top than the bottom exactly as the design
        // has them — the eye enters at the top and the weight follows it.
        Ornament.drawCornerBracket(g2, x + 8, y + 8, 28, 0, accent, 0.45);
        Ornament.drawCornerBracket(g2, x + MODAL_W - 36, y + 8, 28, 1, accent, 0.45);
        Ornament.drawCornerBracket(g2, x + 8, y + MODAL_H - 36, 28, 2, accent, 0.35);
        Ornament.drawCornerBracket(g2, x + MODAL_W - 36, y + MODAL_H - 36, 28, 3,
                accent, 0.35);
    }

    /** Pre-title, headline and badge pill. Returns the y below the badge. */
    private int drawModalHeader(Graphics2D g2, GameState state, int centerX, int y,
                                boolean won, Color accent, Color accentLight) {
        // Pre-title — quiet, italic, and the line that says what run this was.
        g2.setFont(modalPreTitleFont);
        FontMetrics preMetrics = g2.getFontMetrics();
        String pre = won
                ? state.getDifficulty().getDisplayName() + " · Run Complete"
                : "Level " + Math.max(1, state.getLevel()) + " · The Siege Broke Through";
        g2.setColor(Palette.GOLD_DIM);
        drawTrackedString(g2, pre, centerX, y + preMetrics.getAscent(), 2.3);
        y += 30;

        // Headline, drawn as outlines so the glow is a real halo.
        String title = won ? "The Temple Stands" : "The Temple Has Fallen";
        y += 24;
        DisplayText.drawCentred(g2, title, modalTitleFont, centerX, y,
                accentLight, accentLight, accent, 0.45f, 1f);
        y += 28;

        drawBadgePill(g2, state, centerX, y, won, accent);
        return y + 26;
    }

    /** The rounded outline pill under the headline, flanked by two asterisks. */
    private void drawBadgePill(Graphics2D g2, GameState state, int centerX, int y,
                               boolean won, Color accent) {
        String text = won
                ? (state.getDifficulty().getDisplayName() + " cleared · all "
                        + state.getFinalLevel() + " levels").toUpperCase(Locale.ROOT)
                : (state.getDifficulty().getDisplayName() + " · reached level "
                        + Math.max(1, state.getLevel()) + " of "
                        + state.getFinalLevel()).toUpperCase(Locale.ROOT);

        g2.setFont(modalBadgeFont);
        FontMetrics fm = g2.getFontMetrics();
        double tracking = 2.2;
        double textWidth = trackedWidth(fm, text, tracking);

        int starGap = 10;
        double starWidth = 7;
        double inner = textWidth + (starGap + starWidth) * 2;
        int padX = 14;
        int pillW = (int) Math.round(inner + padX * 2);
        int pillH = 22;
        int pillX = centerX - pillW / 2;

        RoundRectangle2D pill = new RoundRectangle2D.Double(
                pillX, y, pillW, pillH, pillH, pillH);
        g2.setColor(Palette.alpha(accent, 0.10));
        g2.fill(pill);
        g2.setColor(Palette.alpha(accent, 0.28));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(pill);

        int baseline = y + pillH / 2 + fm.getAscent() / 2 - 1;
        g2.setColor(Palette.GOLD_MID);
        drawTrackedString(g2, text, centerX, baseline, tracking);

        // Four-point stars either side, drawn as diamonds so they match the
        // diamond terminators used on every rule in the design.
        g2.setColor(accent);
        double starX = textWidth / 2 + starGap + starWidth / 2;
        Ornament.drawDiamond(g2, centerX - starX, y + pillH / 2.0, 3.2);
        Ornament.drawDiamond(g2, centerX + starX, y + pillH / 2.0, 3.2);
    }

    /**
     * The bordered 2x4 grid of run statistics.
     *
     * <p>Cells are laid out left-to-right, top-to-bottom in the design's order.
     * Final Score is the highlighted one — it is the number the player came for,
     * and the design gives it a larger cut, a brighter gold and a tinted cell so
     * it wins the grid without needing a second colour introduced.
     *
     * @return the y below the grid
     */
    private int drawStatGrid(Graphics2D g2, GameState state, int x, int y, int width,
                             Color accent, Color accentLight) {
        String[] labels = {
            "LEVEL REACHED", "FINAL SCORE",
            "SPIRITS SLAIN", "BOLTS INTERCEPTED",
            "WORDS PER MINUTE", "ACCURACY",
            "BEST COMBO", "DIFFICULTY",
        };
        String[] values = {
            Integer.toString(Math.max(1, state.getLevel())),
            Integer.toString(state.getScore()),
            Integer.toString(state.getEnemiesDefeated()),
            Integer.toString(state.getProjectilesIntercepted()),
            Integer.toString((int) Math.round(state.getWpm())),
            Math.round(state.getResolver().getAccuracy() * 100) + "%",
            state.getCombo().getBest() + "×",
            state.getDifficulty().getDisplayName(),
        };

        boolean newBest = state.getScore() >= state.getBestScore() && state.getScore() > 0;
        String[] notes = new String[labels.length];
        notes[6] = newBest ? "New personal best" : null;
        notes[7] = state.getPowerUpsCollected() + " boons claimed";

        int cellW = width / STAT_COLUMNS;
        int cellH = 62;
        int gridH = cellH * STAT_ROWS;

        RoundRectangle2D grid =
                new RoundRectangle2D.Double(x, y, width, gridH, 8, 8);

        Graphics2D gg = (Graphics2D) g2.create();
        try {
            gg.clip(grid);
            gg.setColor(new Color(0, 0, 0, 46));
            gg.fill(grid);

            for (int i = 0; i < labels.length; i++) {
                int col = i % STAT_COLUMNS;
                int row = i / STAT_COLUMNS;
                int cx = x + col * cellW;
                int cy = y + row * cellH;
                boolean highlight = i == 1;

                // Cell tint: the highlighted cell takes the accent, and alternate
                // row-pairs take a barely-there lift so the grid has a rhythm
                // without needing visible zebra striping.
                if (highlight) {
                    gg.setColor(Palette.alpha(accent, 0.05));
                    gg.fillRect(cx, cy, cellW, cellH);
                } else if (row % 2 == 0) {
                    gg.setColor(new Color(0xFF, 0xFF, 0xFF, 4));
                    gg.fillRect(cx, cy, cellW, cellH);
                }

                gg.setStroke(new BasicStroke(1f));
                gg.setColor(Palette.alpha(accent, 0.08));
                if (row < STAT_ROWS - 1) {
                    gg.drawLine(cx, cy + cellH, cx + cellW, cy + cellH);
                }
                if (col < STAT_COLUMNS - 1) {
                    gg.drawLine(cx + cellW, cy, cx + cellW, cy + cellH);
                }

                drawStatCell(gg, labels[i], values[i], notes[i],
                        cx + 18, cy + 11, highlight, accentLight);
            }
        } finally {
            gg.dispose();
        }

        g2.setColor(Palette.alpha(accent, 0.12));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(grid);

        return y + gridH;
    }

    /** One grid cell: tracked label, large value, optional italic note. */
    private void drawStatCell(Graphics2D g2, String label, String value, String note,
                              int x, int y, boolean highlight, Color accentLight) {
        g2.setFont(modalStatLabelFont);
        FontMetrics labelMetrics = g2.getFontMetrics();
        g2.setColor(Palette.GOLD_FAINT);
        drawTrackedLeft(g2, label, x, y + labelMetrics.getAscent(), 2.0);

        g2.setFont(highlight ? modalStatBigFont : modalStatValueFont);
        FontMetrics valueMetrics = g2.getFontMetrics();
        int valueBaseline = y + labelMetrics.getHeight() + valueMetrics.getAscent();

        if (highlight) {
            // The one number with a halo. Drawn through DisplayText for the same
            // reason the headline is: a stamped shadow at this size reads as
            // ghosting rather than as glow.
            double w = valueMetrics.stringWidth(value);
            DisplayText.drawCentred(g2, value, modalStatBigFont,
                    x + w / 2, valueBaseline - valueMetrics.getAscent() / 2.0 + 1,
                    accentLight, accentLight, accentLight, 0.3f, 1f);
        } else {
            g2.setColor(Palette.GOLD_VALUE);
            g2.drawString(value, x, valueBaseline);
        }

        if (note != null) {
            g2.setFont(modalNoteFont);
            g2.setColor(Palette.GOLD_WARM);
            g2.drawString(note, x, valueBaseline + 15);
        }
    }

    /**
     * The action row.
     *
     * <p>The design draws three clickable buttons. This game's end screen is
     * keyboard-driven — Tab arms a restart, Enter confirms it, Escape quits —
     * so the buttons keep the design's shape but carry their key as the label.
     * Drawing a mouse affordance for something the mouse cannot do would be
     * worse than not drawing a button at all.
     */
    private void drawModalActions(Graphics2D g2, int centerX, int y,
                                  boolean restartArmed, Color accent) {
        int buttonW = 168;
        int buttonH = 38;
        int gap = 12;
        int totalW = buttonW * 2 + gap;
        int x = centerX - totalW / 2;

        if (restartArmed) {
            drawModalButton(g2, x, y, buttonW, buttonH, "ENTER", "play again",
                    true, accent);
        } else {
            drawModalButton(g2, x, y, buttonW, buttonH, "TAB → ENTER", "play again",
                    true, accent);
        }
        drawModalButton(g2, x + buttonW + gap, y, buttonW, buttonH, "ESC", "quit",
                false, accent);
    }

    /** One action button: key cap treatment, primary or secondary. */
    private void drawModalButton(Graphics2D g2, int x, int y, int width, int height,
                                 String key, String caption, boolean primary,
                                 Color accent) {
        RoundRectangle2D plate =
                new RoundRectangle2D.Double(x, y, width, height, 8, 8);

        if (primary) {
            g2.setColor(Palette.alpha(accent, 0.16));
            g2.fill(plate);
            g2.setColor(Palette.alpha(accent, 0.75));
            g2.setStroke(new BasicStroke(1.5f));
        } else {
            g2.setColor(new Color(0x1E, 0x19, 0x14, 140));
            g2.fill(plate);
            g2.setColor(Palette.alpha(accent, 0.32));
            g2.setStroke(new BasicStroke(1.2f));
        }
        g2.draw(plate);

        g2.setFont(modalBadgeFont);
        FontMetrics keyMetrics = g2.getFontMetrics();
        g2.setColor(primary ? Palette.GOLD_LIGHT : Palette.GOLD_MID);
        drawTrackedString(g2, key, x + width / 2,
                y + height / 2 - 1, 2.0);

        g2.setFont(modalNoteFont);
        FontMetrics capMetrics = g2.getFontMetrics();
        g2.setColor(Palette.GOLD_FAINT);
        g2.drawString(caption,
                x + width / 2 - capMetrics.stringWidth(caption) / 2,
                y + height / 2 + capMetrics.getAscent() + 1);
    }

    /** The single italic line under the actions. */
    private void drawModalFootnote(Graphics2D g2, GameState state, int centerX, int y,
                                   boolean won) {
        g2.setFont(modalFootnoteFont);
        FontMetrics fm = g2.getFontMetrics();

        String text;
        if (won) {
            text = "Personal best " + state.getBestScore()
                    + " · reached level " + Math.max(1, state.getBestLevel());
        } else {
            text = "Best this session " + state.getBestScore()
                    + " · level " + Math.max(1, state.getBestLevel());
        }
        g2.setColor(Palette.GOLD_FAINT);
        g2.drawString(text, centerX - fm.stringWidth(text) / 2, y);
    }

    // ---- tracked text helpers ----------------------------------------------

    private static double trackedWidth(FontMetrics fm, String text, double tracking) {
        if (text.isEmpty()) {
            return 0;
        }
        return fm.stringWidth(text) + tracking * (text.length() - 1);
    }

    /** Tracked text centred on {@code centerX}. */
    private void drawTrackedString(Graphics2D g2, String text, double centerX,
                                   double baseline, double tracking) {
        FontMetrics fm = g2.getFontMetrics();
        drawTrackedLeft(g2, text,
                centerX - trackedWidth(fm, text, tracking) / 2, baseline, tracking);
    }

    /** Tracked text starting at {@code x}. */
    private void drawTrackedLeft(Graphics2D g2, String text, double x, double baseline,
                                 double tracking) {
        FontMetrics fm = g2.getFontMetrics();
        double cursor = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            g2.drawString(ch, (float) cursor, (float) baseline);
            cursor += fm.stringWidth(ch) + tracking;
        }
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
