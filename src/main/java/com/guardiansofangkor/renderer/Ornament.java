package com.guardiansofangkor.renderer;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/**
 * Khmer decorative motifs shared by the menu and the HUD.
 *
 * <p>Kept in one place so the lotus-bud prang silhouette is literally the same
 * shape everywhere it appears — as a life pip on the HUD bar and as the divider
 * under the title. Two hand-tuned copies would drift apart the first time either
 * was adjusted.
 */
public final class Ornament {

    private Ornament() {
        // Shape factory — not instantiable.
    }

    /**
     * One lotus-bud tower: pointed apex, swelling body, narrow waist. The Angkor
     * prang silhouette, which is why it reads as belonging to this game rather
     * than as generic ornament.
     *
     * @param cx    horizontal centre
     * @param baseY the bottom of the tower
     */
    public static Path2D budPath(double cx, double baseY, double width, double height) {
        double halfW = width / 2.0;
        double apexY = baseY - height;

        Path2D path = new Path2D.Double();
        path.moveTo(cx - halfW * 0.55, baseY);
        path.curveTo(
                cx - halfW, baseY - height * 0.34,
                cx - halfW * 0.86, baseY - height * 0.70,
                cx, apexY);
        path.curveTo(
                cx + halfW * 0.86, baseY - height * 0.70,
                cx + halfW, baseY - height * 0.34,
                cx + halfW * 0.55, baseY);
        path.closePath();
        return path;
    }

    /**
     * A three-tower divider flanked by rules and diamonds — the ornament sitting
     * between the title and the menu buttons.
     *
     * @param cx       horizontal centre of the group
     * @param baseY    the line the towers stand on
     * @param fullWidth total span including the flanking rules
     */
    public static void drawTempleDivider(Graphics2D g2, double cx, double baseY,
                                         double fullWidth) {
        double centreHeight = 22;
        double sideHeight = 13;
        double centreWidth = 13;
        double sideWidth = 10;
        double spacing = 13;

        g2.fill(budPath(cx, baseY, centreWidth, centreHeight));
        g2.fill(budPath(cx - spacing, baseY, sideWidth, sideHeight));
        g2.fill(budPath(cx + spacing, baseY, sideWidth, sideHeight));

        // Plinth the towers stand on.
        g2.fill(new java.awt.geom.Rectangle2D.Double(
                cx - spacing - sideWidth * 0.7, baseY, (spacing + sideWidth * 0.7) * 2, 2));

        double clusterHalf = spacing + sideWidth;
        double gap = 12;
        double ruleStart = clusterHalf + gap;
        double ruleEnd = fullWidth / 2.0;
        double midY = baseY - centreHeight * 0.35;

        if (ruleEnd > ruleStart + 14) {
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new java.awt.geom.Line2D.Double(
                    cx - ruleEnd, midY, cx - ruleStart - 8, midY));
            g2.draw(new java.awt.geom.Line2D.Double(
                    cx + ruleStart + 8, midY, cx + ruleEnd, midY));

            drawDiamond(g2, cx - ruleStart - 3, midY, 4);
            drawDiamond(g2, cx + ruleStart + 3, midY, 4);
        }
    }

    /**
     * A thin rule with a diamond at each end and one in the middle — the lighter
     * divider used above the title.
     */
    public static void drawDottedRule(Graphics2D g2, double cx, double y, double width) {
        double half = width / 2.0;
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new java.awt.geom.Line2D.Double(cx - half, y, cx - 10, y));
        g2.draw(new java.awt.geom.Line2D.Double(cx + 10, y, cx + half, y));

        drawDiamond(g2, cx, y, 3.5);
        drawDiamond(g2, cx - half - 6, y, 2.5);
        drawDiamond(g2, cx + half + 6, y, 2.5);
    }

    /** A small filled diamond, used as a terminator on rules. */
    public static void drawDiamond(Graphics2D g2, double cx, double cy, double radius) {
        Path2D diamond = new Path2D.Double();
        diamond.moveTo(cx, cy - radius);
        diamond.lineTo(cx + radius, cy);
        diamond.lineTo(cx, cy + radius);
        diamond.lineTo(cx - radius, cy);
        diamond.closePath();
        g2.fill(diamond);
    }

    /**
     * A votive candle with a flame, drawn flanking a button label.
     *
     * @param baseY bottom of the candle body
     */
    public static void drawCandle(Graphics2D g2, double cx, double baseY, double height,
                                  java.awt.Color body, java.awt.Color flame) {
        double bodyW = height * 0.30;
        double bodyH = height * 0.58;

        g2.setColor(body);
        g2.fill(new java.awt.geom.RoundRectangle2D.Double(
                cx - bodyW / 2, baseY - bodyH, bodyW, bodyH, bodyW * 0.5, bodyW * 0.5));

        // Base flare, so it reads as standing rather than floating.
        g2.fill(new java.awt.geom.RoundRectangle2D.Double(
                cx - bodyW * 0.8, baseY - 2, bodyW * 1.6, 3, 2, 2));

        double flameH = height * 0.34;
        double flameW = height * 0.20;
        double flameBase = baseY - bodyH - 1;

        Path2D fire = new Path2D.Double();
        fire.moveTo(cx, flameBase - flameH);
        fire.curveTo(cx + flameW * 0.9, flameBase - flameH * 0.45,
                cx + flameW * 0.55, flameBase,
                cx, flameBase);
        fire.curveTo(cx - flameW * 0.55, flameBase,
                cx - flameW * 0.9, flameBase - flameH * 0.45,
                cx, flameBase - flameH);
        fire.closePath();

        g2.setColor(flame);
        g2.fill(fire);
        g2.fill(new Ellipse2D.Double(
                cx - flameW * 0.22, flameBase - flameH * 0.30,
                flameW * 0.44, flameH * 0.30));
    }
}
