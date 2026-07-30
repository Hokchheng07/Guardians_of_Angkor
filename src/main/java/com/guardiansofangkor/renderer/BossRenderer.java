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
import java.awt.geom.Ellipse2D;
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

    /** Where the paragraph panel sits above the typing bar. */
    private static final int PANEL_BOTTOM_MARGIN = 74;

    private static final int PANEL_SIDE_MARGIN = 90;
    private static final int PANEL_PADDING = 22;
    private static final int LINE_GAP = 8;

    /** Stage pip size and spacing. */
    private static final int PIP_RADIUS = 7;
    private static final int PIP_GAP = 22;

    private Font paragraphFont;
    private Font nameFont;
    private Font labelFont;

    public BossRenderer(Language language) {
        setLanguage(language);
    }

    /** Swaps fonts if the language changes mid-session. */
    public final void setLanguage(Language language) {
        this.paragraphFont = FontManager.wordFont(language, 26, Font.BOLD);
        this.nameFont = FontManager.uiFont(language, 40, Font.BOLD);
        this.labelFont = FontManager.uiFont(language, 13, Font.BOLD);
    }

    /**
     * Paints the whole fight.
     *
     * @param sprites used for the boss's artwork; falls back to a drawn
     *                silhouette when the PNG is absent, as everywhere else
     */
    public void draw(Graphics2D g2, BossFight boss, SpriteCache sprites) {
        if (boss == null) {
            return;
        }
        drawMonster(g2, boss, sprites);

        if (boss.isArriving()) {
            drawArrival(g2, boss);
            return;
        }
        if (boss.isBeaten()) {
            return;
        }
        drawHealthBar(g2, boss);
        drawParagraphPanel(g2, boss);
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

        int topY = (int) Math.round(GameConfig.GROUND_LINE_Y - height + lift);
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
        String typed = boss.getTyped();

        g2.setFont(paragraphFont);
        FontMetrics fm = g2.getFontMetrics();

        int maxWidth = GameConfig.SCREEN_WIDTH - PANEL_SIDE_MARGIN * 2 - PANEL_PADDING * 2;
        List<String> lines = wrap(sentence, fm, maxWidth);

        int lineHeight = fm.getHeight() + LINE_GAP;
        int panelHeight = PANEL_PADDING * 2 + lines.size() * lineHeight;
        int panelWidth = GameConfig.SCREEN_WIDTH - PANEL_SIDE_MARGIN * 2;
        int panelX = PANEL_SIDE_MARGIN;
        int panelY = GameConfig.SCREEN_HEIGHT - PANEL_BOTTOM_MARGIN - panelHeight;

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

        // Walk the wrapped lines against the typed prefix so the gold/white
        // split lands mid-line correctly rather than per-line.
        int consumed = 0;
        int baseline = panelY + PANEL_PADDING + fm.getAscent();

        for (String line : lines) {
            int lineStart = sentence.indexOf(line, consumed);
            if (lineStart < 0) {
                lineStart = consumed;
            }
            int lineEnd = lineStart + line.length();
            int matchedInLine = Math.max(0,
                    Math.min(line.length(), typed.length() - lineStart));

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
