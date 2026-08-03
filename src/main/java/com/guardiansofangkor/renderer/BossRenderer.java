package com.guardiansofangkor.renderer;

import com.guardiansofangkor.engine.BossFight;
import com.guardiansofangkor.i18n.FontManager;
import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.util.GameConfig;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws the final boss and the paragraph it demands.
 *
 * <p>Its own class rather than more methods on {@link GamePanel} because the
 * finale is a screen unto itself: a health bar, stage pips, a wrapped paragraph
 * panel and a monster drawn four times the size of anything else. Folding that
 * into the panel that already handles seven enemy types, projectiles, power-ups
 * and effects would make the largest file in the project considerably larger for
 * no benefit.
 *
 * <p>Reads {@link BossFight} and paints it. Decides nothing.
 */
public class BossRenderer {

    private static final int PANEL_SIDE_MARGIN = 90;
    private static final int PANEL_PADDING = 22;
    private static final int LINE_GAP = 8;

    /** Stage pip size and spacing. */
    private static final int PIP_RADIUS = 7;
    private static final int PIP_GAP = 22;

    private Font paragraphFont;
    private Font nameFont;
    private Font labelFont;
    private Font warningFont;

    public BossRenderer(Language language) {
        setLanguage(language);
    }

    /** Swaps fonts if the language changes mid-session. */
    public final void setLanguage(Language language) {
        this.paragraphFont = FontManager.wordFont(language, 26, Font.BOLD);
        this.nameFont = FontManager.uiFont(language, 40, Font.BOLD);
        this.labelFont = FontManager.uiFont(language, 13, Font.BOLD);
        this.warningFont = FontManager.uiFont(language, 44, Font.BOLD);
    }

    /**
     * The boss and its chrome, drawn UNDERNEATH the rest of the play field.
     *
     * <p>Split from {@link #drawOverlay} because of a real bug: the summoned
     * monsters of a MINIONS phase are ordinary enemies with ordinary word plates,
     * and the boss is four times the size of anything else standing dead centre.
     * With the boss painted after the enemies, a summon that walked in front of
     * it had both its sprite and — far worse — its word buried, so the player
     * was being asked to type something they could not read.
     *
     * <p>Everything here is background by definition: the monster, its bar, and
     * whichever panel is up. None of it is typed. So all of it goes down before
     * the things that are.
     *
     * @param sprites used for the boss's artwork; falls back to a drawn
     *                silhouette when the PNG is absent, as everywhere else
     */
    public void drawWorld(Graphics2D g2, BossFight boss, SpriteCache sprites) {
        if (boss == null) {
            return;
        }
        drawMonster(g2, boss, sprites);

        if (boss.isArriving() || boss.isBeaten()) {
            return;
        }

        // The paragraph is off screen for the whole of an attack phase. Leaving
        // it up would ask the player to read a sentence they are not allowed to
        // type, which reads as the input having broken; the banner in its place
        // says what is happening and how much longer it lasts.
        if (boss.isAttacking()) {
            // The health bar is deliberately NOT drawn during a phase. It cannot
            // move — the verse is what damages the boss and the verse is away —
            // so it is a large, bright, centred thing reporting a number that is
            // not changing, sitting exactly where the summons come in. The phase
            // chip takes its slot instead, which keeps the top of the field to
            // one element rather than two.
            drawPhaseChip(g2, boss);
        } else {
            drawHealthBar(g2, boss);
            drawParagraphPanel(g2, boss);
        }
    }

    /**
     * The parts that must sit above everything, drawn last.
     *
     * <p>Only the two held screens qualify: the arrival name card and the
     * briefing. Both stop the fight while they are up, so nothing they cover is
     * anything the player could be acting on.
     */
    public void drawOverlay(Graphics2D g2, BossFight boss) {
        if (boss == null) {
            return;
        }
        if (boss.isArriving()) {
            drawArrival(g2, boss);
        } else if (boss.isBriefing()) {
            drawBriefing(g2, boss);
        }
    }

    // ---- the monster -------------------------------------------------------

    private void drawMonster(Graphics2D g2, BossFight boss, SpriteCache sprites) {
        int height = GameConfig.BOSS_HEIGHT;
        int cx = GameConfig.TEMPLE_CENTER_X;

        // Rises out of the ground on arrival and sinks back into it when beaten,
        // so both ends of the fight have a beat that is visible rather than a
        // sprite that simply appears and simply stops.
        double lift = 0;
        float alpha = 1f;
        if (boss.isArriving()) {
            double t = ease(boss.getPhaseProgress());
            lift = (1.0 - t) * height * 0.85;
            alpha = (float) Math.min(1.0, t * 1.6);
        } else if (boss.isBeaten()) {
            double t = boss.getPhaseProgress();
            lift = -t * height * 0.25;
            alpha = (float) Math.max(0, 1.0 - t);
        }

        // A slow sway while fighting, so it never looks like a still image.
        double sway = boss.isFighting() ? Math.sin(boss.getTicks() * 0.035) * 9 : 0;

        BufferedImage sprite = sprites.sprite(boss.getType());
        int width = sprite != null && sprite.getHeight() > 0
                ? (int) Math.round(height * (sprite.getWidth() / (double) sprite.getHeight()))
                : (int) Math.round(height * 0.8);

        int topY = (int) Math.round(GameConfig.BOSS_BASE_Y - height + lift);
        int leftX = (int) Math.round(cx - width / 2.0 + sway);

        Graphics2D bg = (Graphics2D) g2.create();
        try {
            bg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

            drawAura(bg, cx, topY + height / 2, width, height, boss);

            if (sprite != null) {
                bg.drawImage(sprite, leftX, topY, width, height, null);
            } else {
                drawSilhouette(bg, leftX, topY, width, height);
            }

            if (boss.getHitFlashTicks() > 0) {
                BufferedImage flash = sprites.silhouette(boss.getType());
                float strength = Math.min(0.75f,
                        boss.getHitFlashTicks() / (float) (GameConfig.TARGET_FPS / 2));
                bg.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, alpha * strength));
                if (flash != null) {
                    bg.drawImage(flash, leftX, topY, width, height, null);
                } else {
                    bg.setColor(Color.WHITE);
                    bg.fillRect(leftX, topY, width, height);
                }
            }
        } finally {
            bg.dispose();
        }
    }

    /** A dark corona so the boss separates from the temple behind it. */
    private void drawAura(Graphics2D g2, int cx, int cy, int width, int height,
                          BossFight boss) {
        double pulse = 1.0 + Math.sin(boss.getTicks() * 0.05) * 0.04;
        double rx = width * 0.72 * pulse;
        double ry = height * 0.58 * pulse;

        Graphics2D ag = (Graphics2D) g2.create();
        try {
            // Three widening bands rather than one flat disc — the falloff is
            // what makes it read as light rather than as a shape.
            for (int i = 3; i >= 1; i--) {
                ag.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, 0.05f * i));
                ag.setColor(Palette.VENOM_EDGE);
                double sx = rx * (1.0 + i * 0.16);
                double sy = ry * (1.0 + i * 0.16);
                ag.fill(new Ellipse2D.Double(cx - sx, cy - sy, sx * 2, sy * 2));
            }
        } finally {
            ag.dispose();
        }
    }

    /** Stand-in shape when the boss has no artwork yet. */
    private void drawSilhouette(Graphics2D g2, int x, int y, int width, int height) {
        g2.setColor(new Color(0x2A, 0x1E, 0x2E));
        g2.fill(new RoundRectangle2D.Double(x, y, width, height, 40, 40));
        g2.setColor(Palette.VENOM_EDGE);
        g2.setStroke(new BasicStroke(3f));
        g2.draw(new RoundRectangle2D.Double(x, y, width, height, 40, 40));

        // Two eyes, so it is unmistakably facing the player.
        double eyeR = width * 0.055;
        double eyeY = y + height * 0.22;
        g2.setColor(Palette.VENOM_CORE);
        g2.fill(new Ellipse2D.Double(x + width * 0.34 - eyeR, eyeY - eyeR, eyeR * 2, eyeR * 2));
        g2.fill(new Ellipse2D.Double(x + width * 0.66 - eyeR, eyeY - eyeR, eyeR * 2, eyeR * 2));
    }

    // ---- arrival -----------------------------------------------------------

    /** The name card, held for the whole rise. */
    private void drawArrival(Graphics2D g2, BossFight boss) {
        double t = boss.getPhaseProgress();
        // Fades in over the first third and back out over the last quarter, so
        // it never covers the first sentence.
        float alpha = (float) Math.min(1.0, Math.min(t * 3.0, (1.0 - t) * 4.0));
        if (alpha <= 0.01f) {
            return;
        }

        Graphics2D ag = (Graphics2D) g2.create();
        try {
            ag.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

            int centreY = GameConfig.SCREEN_HEIGHT / 2 - 40;
            String name = boss.getType().getDisplayName().toUpperCase(java.util.Locale.ROOT);

            DisplayText.drawCentred(ag, name, nameFont,
                    GameConfig.TEMPLE_CENTER_X, centreY,
                    Palette.HUD_TEXT_GOLD, Palette.DANGER,
                    Palette.DANGER, 0.5f, alpha);

            ag.setFont(labelFont);
            FontMetrics fm = ag.getFontMetrics();
            String sub = boss.getStageCount() + " VERSES  ·  NO QUARTER";
            ag.setColor(Palette.HUD_TEXT_DIM);
            ag.drawString(sub,
                    GameConfig.TEMPLE_CENTER_X - fm.stringWidth(sub) / 2, centreY + 44);
        } finally {
            ag.dispose();
        }
    }

    // ---- the briefing --------------------------------------------------------

    private static final int BRIEF_W = 700;
    private static final int BRIEF_H = 210;

    /** Size of the angled corner cut, which is what makes the frame read as HUD. */
    private static final int BRIEF_CUT = 26;

    /** How far the solid corner brackets run along each edge. */
    private static final int BRACKET_RUN = 54;

    private static final Color BRIEF_RED = new Color(0xE0, 0x2B, 0x20);
    private static final Color BRIEF_RED_DEEP = new Color(0x8E, 0x12, 0x0C);
    private static final Color BRIEF_FILL = new Color(0x2A, 0x0E, 0x0C, 238);

    /**
     * The rules of the finale, held on screen for {@link BossFight#BRIEFING_TICKS}.
     *
     * <p>Exists because the finale quietly changes three rules at once — words
     * confirm on space, orbs are answered by typing them, a slip costs the
     * verse — and none of them are guessable. The arrival card announces the
     * boss's name, which is the one thing the player can already see. Without
     * this, the first mistake is the tutorial.
     */
    private void drawBriefing(Graphics2D g2, BossFight boss) {
        double t = boss.getBriefingProgress();
        // Snap in, hold, ease out: roughly 0.4s in, 3.7s readable, 0.9s out.
        // Weighted heavily toward the hold because the panel has three lines to
        // read and a fade is dead time in a five-second budget.
        float alpha = (float) Math.min(1.0, Math.min(t * 12.0, (1.0 - t) * 6.0));
        if (alpha <= 0.01f) {
            return;
        }

        Graphics2D bg = (Graphics2D) g2.create();
        try {
            bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            // Scrim first. The play field behind this is busy and red-on-busy
            // is the one thing that would cost legibility in five seconds.
            bg.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, alpha * 0.72f));
            bg.setColor(new Color(0x08, 0x05, 0x04));
            bg.fillRect(0, 0, GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT);

            bg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

            int x = GameConfig.TEMPLE_CENTER_X - BRIEF_W / 2;
            int y = GameConfig.SCREEN_HEIGHT / 2 - BRIEF_H / 2 - 40;

            Shape frame = cutCornerFrame(x, y, BRIEF_W, BRIEF_H, BRIEF_CUT);
            bg.setColor(BRIEF_FILL);
            bg.fill(frame);
            bg.setColor(BRIEF_RED);
            bg.setStroke(new BasicStroke(2.2f));
            bg.draw(frame);

            drawCornerBrackets(bg, x, y, BRIEF_W, BRIEF_H);
            drawHazardBand(bg, x + 200, y + 34, BRIEF_W - 232, 12, alpha);
            drawHazardBand(bg, x + 200, y + BRIEF_H - 46, BRIEF_W - 232, 12, alpha);
            drawWarningTriangle(bg, x + 96, y + BRIEF_H / 2.0, 62);

            bg.setFont(warningFont);
            FontMetrics hfm = bg.getFontMetrics();
            bg.setColor(BRIEF_RED);
            bg.drawString("WARNING", x + 200, y + 34 + hfm.getAscent() + 18);

            bg.setFont(labelFont);
            FontMetrics fm = bg.getFontMetrics();
            int lineY = y + 34 + hfm.getAscent() + 46;
            for (String line : List.of(
                    "TYPE A VERSE WORD, THEN SPACE TO CONFIRM IT",
                    "TYPE AN ORB'S WORD TO DESTROY IT BEFORE IT LANDS",
                    "A WRONG LETTER OR SPACE RESETS THE CURRENT VERSE")) {
                bg.setColor(Palette.HUD_TEXT_WHITE);
                bg.drawString(line, x + 200, lineY);
                lineY += fm.getHeight() + 5;
            }
        } finally {
            bg.dispose();
        }
    }

    /** The panel outline: a rectangle with two opposite corners sliced off. */
    private static Shape cutCornerFrame(int x, int y, int w, int h, int cut) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(x + cut, y);
        path.lineTo(x + w, y);
        path.lineTo(x + w, y + h - cut);
        path.lineTo(x + w - cut, y + h);
        path.lineTo(x, y + h);
        path.lineTo(x, y + cut);
        path.closePath();
        return path;
    }

    /**
     * Heavy solid corners, drawn over the thin outline.
     *
     * <p>This is the whole trick of the style: a uniform border reads as a
     * dialog box, while a thin edge anchored by four heavy corners reads as an
     * instrument panel.
     */
    private static void drawCornerBrackets(Graphics2D g2, int x, int y, int w, int h) {
        g2.setColor(BRIEF_RED);
        g2.setStroke(new BasicStroke(6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));

        // Top-left and bottom-right are the square corners; the other two are
        // cut, so their brackets start clear of the slice.
        g2.drawLine(x + 3, y + BRIEF_CUT, x + 3, y + BRIEF_CUT + BRACKET_RUN);
        g2.drawLine(x + BRIEF_CUT, y + 3, x + BRIEF_CUT + BRACKET_RUN, y + 3);

        g2.drawLine(x + w - 3, y + 3, x + w - 3, y + BRACKET_RUN);
        g2.drawLine(x + w - BRACKET_RUN, y + 3, x + w - 3, y + 3);

        g2.drawLine(x + 3, y + h - 3, x + 3 + BRACKET_RUN, y + h - 3);
        g2.drawLine(x + 3, y + h - BRACKET_RUN, x + 3, y + h - 3);

        g2.drawLine(x + w - 3, y + h - BRIEF_CUT - BRACKET_RUN,
                x + w - 3, y + h - BRIEF_CUT);
        g2.drawLine(x + w - BRIEF_CUT - BRACKET_RUN, y + h - 3,
                x + w - BRIEF_CUT, y + h - 3);
    }

    /** Diagonal hazard stripes, clipped to a thin band. */
    private static void drawHazardBand(Graphics2D g2, int x, int y, int w, int h,
                                       float alpha) {
        Graphics2D hg = (Graphics2D) g2.create();
        try {
            hg.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, alpha * 0.85f));
            hg.setClip(x, y, w, h);
            hg.setColor(BRIEF_RED_DEEP);
            hg.setStroke(new BasicStroke(5f));
            // Start a full band-height to the left so the first stripe enters
            // the clip already at its proper angle rather than as a stub.
            for (int sx = x - h; sx < x + w + h; sx += 11) {
                hg.drawLine(sx, y + h, sx + h, y);
            }
        } finally {
            hg.dispose();
        }
    }

    /** The bordered triangle and its exclamation mark. */
    private static void drawWarningTriangle(Graphics2D g2, double cx, double cy,
                                            double size) {
        double half = size / 2.0;
        Path2D.Double tri = new Path2D.Double();
        tri.moveTo(cx, cy - half);
        tri.lineTo(cx + half * 1.08, cy + half * 0.82);
        tri.lineTo(cx - half * 1.08, cy + half * 0.82);
        tri.closePath();

        g2.setColor(BRIEF_RED);
        g2.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(tri);

        // Tapered bar and dot rather than a drawn glyph, so the mark keeps its
        // weight independently of whatever font happens to be resolved.
        double barTop = cy - half * 0.28;
        double barBottom = cy + half * 0.22;
        g2.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Line2D.Double(cx, barTop, cx, barBottom));
        g2.fill(new Ellipse2D.Double(cx - 2.8, cy + half * 0.42, 5.6, 5.6));
    }

    // ---- health ------------------------------------------------------------

    private void drawHealthBar(Graphics2D g2, BossFight boss) {
        int barWidth = 620;
        int x = GameConfig.TEMPLE_CENTER_X - barWidth / 2;
        int y = HUDRenderer.BAR_HEIGHT + 34;
        int height = GameConfig.BOSS_BAR_HEIGHT;

        g2.setFont(labelFont);
        FontMetrics fm = g2.getFontMetrics();
        String name = boss.getType().getDisplayName().toUpperCase(java.util.Locale.ROOT);
        g2.setColor(Palette.HUD_TEXT_GOLD);
        g2.drawString(name, GameConfig.TEMPLE_CENTER_X - fm.stringWidth(name) / 2, y - 8);

        g2.setColor(Palette.PROGRESS_TRACK);
        g2.fillRect(x, y, barWidth, height);

        int fill = (int) Math.round(barWidth * boss.getHealthFraction());
        if (fill > 0) {
            g2.setColor(Palette.BOSS_HEALTH);
            g2.fillRect(x, y, fill, height);
        }

        // Notches at the stage boundaries, so the bar says how many verses are
        // left as well as how much of one.
        g2.setColor(Palette.HUD_BG);
        for (int i = 1; i < boss.getStageCount(); i++) {
            int notchX = x + barWidth * i / boss.getStageCount();
            g2.fillRect(notchX - 1, y, 2, height);
        }

        g2.setColor(Palette.HUD_DIVIDER_SOFT);
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRect(x, y, barWidth, height);

        drawStagePips(g2, boss, GameConfig.TEMPLE_CENTER_X, y + height + 18);
    }

    /** One pip per verse, filled as each is cleared. */
    private void drawStagePips(Graphics2D g2, BossFight boss, int centreX, int y) {
        int count = boss.getStageCount();
        int totalWidth = (count - 1) * PIP_GAP;
        int startX = centreX - totalWidth / 2;

        for (int i = 0; i < count; i++) {
            int cx = startX + i * PIP_GAP;
            Ellipse2D pip = new Ellipse2D.Double(
                    cx - PIP_RADIUS, y - PIP_RADIUS, PIP_RADIUS * 2, PIP_RADIUS * 2);
            if (i < boss.getStage()) {
                g2.setColor(Palette.HUD_TEXT_GOLD);
                g2.fill(pip);
            } else {
                g2.setColor(Palette.LIFE_LOST);
                g2.setStroke(new BasicStroke(1.6f));
                g2.draw(pip);
            }
        }
    }

    // ---- the attack phase --------------------------------------------------

    /**
     * What the boss is doing, and how much of it is left.
     *
     * <p>Occupies the verse panel's exact footprint. Anything else would make
     * the whole lower half of the screen jump every few seconds, and the two
     * states are meant to read as the same slot showing different things rather
     * than as the layout rearranging itself.
     *
     * <p>Deliberately thin: a name and a draining bar. The player's attention
     * during a phase belongs on the field, and a panel that competed for it
     * would defeat the point of having taken the paragraph away.
     */
    /** Width of the phase chip. Narrow on purpose — see {@link #drawPhaseChip}. */
    private static final int PHASE_CHIP_W = 300;

    /**
     * What the boss is doing, as a compact chip in the health bar's slot.
     *
     * <p>This used to be a full-width panel pinned above Preah Ream's head, and
     * that was the worst possible place for it during a MINIONS phase: it is
     * exactly where grounded summons converge, so the one panel that says "type
     * them down" sat on top of the words the player had to type. Even drawn
     * underneath it cost contrast behind every plate.
     *
     * <p>So it moves up into the slot the health bar has vacated, and shrinks to
     * something nearer a label than a panel. Between the HUD bar and the hero
     * there is now nothing of the boss's but the boss, which is the space the
     * summons and their words need.
     */
    private void drawPhaseChip(Graphics2D g2, BossFight boss) {
        if (boss.getAttackPhase() == null) {
            return;
        }
        int x = GameConfig.TEMPLE_CENTER_X - PHASE_CHIP_W / 2;
        int y = HUDRenderer.BAR_HEIGHT + 26;
        int height = 34;

        RoundRectangle2D chip =
                new RoundRectangle2D.Double(x, y, PHASE_CHIP_W, height, 10, 10);
        g2.setColor(Palette.BOSS_PANEL);
        g2.fill(chip);
        g2.setColor(Palette.alpha(Palette.DANGER, 0.75));
        g2.setStroke(new BasicStroke(1.4f));
        g2.draw(chip);

        g2.setFont(labelFont);
        FontMetrics fm = g2.getFontMetrics();
        String name = boss.getAttackPhase().getDisplayName()
                .toUpperCase(java.util.Locale.ROOT);
        g2.setColor(Palette.HUD_TEXT_GOLD);
        g2.drawString(name,
                GameConfig.TEMPLE_CENTER_X - fm.stringWidth(name) / 2,
                y + 15 + fm.getAscent() / 2 - 2);

        // Drains left to right, so "nearly over" is legible at a glance without
        // reading anything.
        int trackWidth = PHASE_CHIP_W - 24;
        int trackX = x + 12;
        int trackY = y + height - 9;
        double remaining = Math.max(0.0, 1.0 - boss.getAttackPhaseProgress());

        g2.setColor(Palette.LIFE_LOST);
        g2.fillRect(trackX, trackY, trackWidth, 4);
        g2.setColor(Palette.DANGER);
        g2.fillRect(trackX, trackY, (int) Math.round(trackWidth * remaining), 4);
    }

    // ---- the paragraph -----------------------------------------------------

    /**
     * The current verse, wrapped, with what has been typed already in gold.
     *
     * <p>Sits low and wide rather than over the boss, because the player's eyes
     * are on the typing bar and a sentence they have to read should be as close
     * to it as the layout allows.
     */
    private void drawParagraphPanel(Graphics2D g2, BossFight boss) {
        String sentence = boss.currentSentence();

        g2.setFont(paragraphFont);
        FontMetrics fm = g2.getFontMetrics();

        int maxWidth = GameConfig.SCREEN_WIDTH - PANEL_SIDE_MARGIN * 2 - PANEL_PADDING * 2;
        List<String> lines = wrap(sentence, fm, maxWidth);

        int lineHeight = fm.getHeight() + LINE_GAP;
        int panelHeight = PANEL_PADDING * 2 + lines.size() * lineHeight;
        int panelWidth = GameConfig.SCREEN_WIDTH - PANEL_SIDE_MARGIN * 2;
        int panelX = PANEL_SIDE_MARGIN;
        // Anchored to its BOTTOM edge, which is pinned just above the top of
        // Preah Ream's head. He is drawn in the foreground, so a panel that
        // reached any lower would have him standing in the middle of the
        // sentence — which is exactly what it used to do.
        int panelY = GameConfig.VERSE_PANEL_BOTTOM_Y - panelHeight;

        RoundRectangle2D panel = new RoundRectangle2D.Double(
                panelX, panelY, panelWidth, panelHeight, 16, 16);
        g2.setColor(Palette.BOSS_PANEL);
        g2.fill(panel);

        // The frame goes red the instant a verse is reset, which is the only
        // signal the player gets that their progress went rather than that the
        // game stopped responding.
        boolean scolding = boss.getTypoFlashTicks() > 0;
        g2.setColor(scolding ? Palette.DANGER : Palette.HUD_DIVIDER_SOFT);
        g2.setStroke(new BasicStroke(scolding ? 2.4f : 1.4f));
        g2.draw(panel);

        // Walk the wrapped lines against how much of the verse is behind the
        // player, so the gold/white split lands mid-line correctly rather than
        // per-line. Progress is counted in characters of the whole verse, which
        // is why it can be compared against absolute line offsets.
        int cleared = boss.getClearedCharacters();
        int consumed = 0;
        int baseline = panelY + PANEL_PADDING + fm.getAscent();

        for (String line : lines) {
            int lineStart = sentence.indexOf(line, consumed);
            if (lineStart < 0) {
                lineStart = consumed;
            }
            int lineEnd = lineStart + line.length();
            int matchedInLine = Math.max(0,
                    Math.min(line.length(), cleared - lineStart));

            int x = GameConfig.TEMPLE_CENTER_X - fm.stringWidth(line) / 2;

            if (matchedInLine > 0) {
                String head = line.substring(0, matchedInLine);
                g2.setColor(Palette.HUD_TEXT_GOLD);
                g2.drawString(head, x, baseline);
                x += fm.stringWidth(head);
            }
            g2.setColor(Palette.HUD_TEXT_WHITE);
            g2.drawString(line.substring(matchedInLine), x, baseline);

            consumed = lineEnd;
            baseline += lineHeight;
        }

        drawVerseLabel(g2, boss, panelX + panelWidth - PANEL_PADDING, panelY - 10);
    }

    private void drawVerseLabel(Graphics2D g2, BossFight boss, int rightX, int y) {
        g2.setFont(labelFont);
        FontMetrics fm = g2.getFontMetrics();
        String label = "VERSE " + (boss.getStage() + 1) + " OF " + boss.getStageCount();
        g2.setColor(Palette.HUD_TEXT_DIM);
        g2.drawString(label, rightX - fm.stringWidth(label), y);
    }

    /**
     * Greedy word wrap.
     *
     * <p>Never splits a word: a sentence broken mid-word is materially harder to
     * type than the same sentence broken between words, and the finale is
     * difficult enough on purpose without being difficult by accident.
     */
    static List<String> wrap(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }

        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();

        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(candidate) <= maxWidth || line.length() == 0) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static double ease(double t) {
        // Ease-out cubic: fast out of the ground, settling at the top.
        double inverted = 1.0 - Math.max(0, Math.min(1, t));
        return 1.0 - inverted * inverted * inverted;
    }
}
