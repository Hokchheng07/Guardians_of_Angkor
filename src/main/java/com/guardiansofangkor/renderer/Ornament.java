package com.guardiansofangkor.renderer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

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

    // ---- ported from the design's SVG components ---------------------------
    //
    // Drawn as real geometry rather than shipped as images. These are small, flat
    // vector marks, so at the sizes they appear a Path2D is both sharper and
    // cheaper than a PNG — and it recolours with the palette instead of baking
    // one gold in.

    /** Natural width of {@link #drawNagaDivider}, before scaling. */
    public static final double NAGA_DIVIDER_WIDTH = 160;

    /** Natural height of {@link #drawNagaDivider}, before scaling. */
    public static final double NAGA_DIVIDER_HEIGHT = 36;

    /**
     * The lotus-tower divider: a central prasat flanked by two smaller towers,
     * with rules running out to either side and a diamond at each junction.
     *
     * <p>Drawn in the design's 160x36 coordinate space and transformed into
     * place, so the proportions cannot drift when it is used at two sizes — it
     * appears under the menu title and again in the modal header.
     *
     * @param cx    horizontal centre of the whole ornament
     * @param cy    vertical centre — the line the side rules sit on
     * @param scale 1.0 draws it at its natural 160x36
     */
    public static void drawNagaDivider(Graphics2D g2, double cx, double cy,
                                       double scale, Color gold) {
        Graphics2D ng = (Graphics2D) g2.create();
        try {
            ng.translate(cx - NAGA_DIVIDER_WIDTH / 2 * scale,
                    cy - NAGA_DIVIDER_HEIGHT / 2 * scale);
            ng.scale(scale, scale);

            // Side rules, stopping short of the tower cluster.
            ng.setColor(Palette.alpha(gold, 0.40));
            ng.setStroke(new BasicStroke(0.75f));
            ng.draw(new Line2D.Double(0, 18, 52, 18));
            ng.draw(new Line2D.Double(108, 18, 160, 18));

            // Central tower: plinth, body, tapering cap, bud finial.
            ng.setColor(Palette.alpha(gold, 0.60));
            ng.fill(new RoundRectangle2D.Double(74, 24, 12, 8, 2, 2));
            ng.setColor(Palette.alpha(gold, 0.70));
            ng.fill(new RoundRectangle2D.Double(76, 17, 8, 9, 2, 2));

            Path2D cap = new Path2D.Double();
            cap.moveTo(80, 6);
            cap.curveTo(78, 10, 76, 13, 76, 17);
            cap.lineTo(84, 17);
            cap.curveTo(84, 13, 82, 10, 80, 6);
            cap.closePath();
            ng.setColor(Palette.alpha(gold, 0.80));
            ng.fill(cap);

            ng.setColor(Palette.alpha(gold, 0.90));
            ng.fill(new Ellipse2D.Double(77.5, 2.5, 5, 7));

            drawMiniTower(ng, 65, 68.5, gold);
            drawMiniTower(ng, 88, 91.5, gold);

            // Diamonds where the rules meet the cluster.
            ng.setColor(Palette.alpha(gold, 0.50));
            drawDiamond(ng, 56, 18, 2.8);
            drawDiamond(ng, 104, 18, 2.8);
        } finally {
            ng.dispose();
        }
    }

    /** One of the two smaller towers flanking the centre of the naga divider. */
    private static void drawMiniTower(Graphics2D g2, double left, double cx, Color gold) {
        g2.setColor(Palette.alpha(gold, 0.40));
        g2.fill(new RoundRectangle2D.Double(left, 26, 7, 6, 2, 2));

        Path2D cap = new Path2D.Double();
        cap.moveTo(cx, 20);
        cap.curveTo(cx - 1, 22, left, 24, left, 26);
        cap.lineTo(left + 7, 26);
        cap.curveTo(left + 7, 24, cx + 1, 22, cx, 20);
        cap.closePath();
        g2.setColor(Palette.alpha(gold, 0.45));
        g2.fill(cap);

        g2.setColor(Palette.alpha(gold, 0.50));
        g2.fill(new Ellipse2D.Double(cx - 1.8, 17, 3.6, 5));
    }

    /**
     * The lotus-and-flame flourish that sits either side of a button label.
     *
     * @param cx       horizontal centre
     * @param cy       vertical centre
     * @param scale    1.0 draws it at its natural 18x22
     * @param mirrored flips it for the right-hand copy
     */
    public static void drawLotusFlame(Graphics2D g2, double cx, double cy, double scale,
                                      boolean mirrored, Color body, Color highlight) {
        Graphics2D lg = (Graphics2D) g2.create();
        try {
            lg.translate(cx, cy);
            lg.scale(mirrored ? -scale : scale, scale);
            lg.translate(-9, -11);

            // Lotus base — two stacked ellipses for depth.
            lg.setColor(Palette.alpha(body, 0.35));
            lg.fill(new Ellipse2D.Double(4.5, 14, 9, 6));
            lg.setColor(Palette.alpha(body, 0.55));
            lg.fill(new Ellipse2D.Double(6, 14, 6, 4));

            // Flame, outer teardrop.
            Path2D flame = new Path2D.Double();
            flame.moveTo(9, 14);
            flame.curveTo(9, 14, 6, 10, 7.5, 6);
            flame.curveTo(8, 4, 9, 2, 9, 2);
            flame.curveTo(9, 2, 10, 4, 10.5, 6);
            flame.curveTo(12, 10, 9, 14, 9, 14);
            flame.closePath();
            lg.setColor(Palette.alpha(body, 0.90));
            lg.fill(flame);

            // Flame, inner highlight.
            Path2D inner = new Path2D.Double();
            inner.moveTo(9, 13);
            inner.curveTo(9, 13, 7.5, 10.5, 8.2, 8);
            inner.curveTo(8.5, 6.5, 9, 5, 9, 5);
            inner.curveTo(9, 5, 9.5, 6.5, 9.8, 8);
            inner.curveTo(10.5, 10.5, 9, 13, 9, 13);
            inner.closePath();
            lg.setColor(Palette.alpha(highlight, 0.70));
            lg.fill(inner);

            // Side petals.
            lg.setColor(Palette.alpha(body, 0.50));
            Path2D leftPetal = new Path2D.Double();
            leftPetal.moveTo(9, 17);
            leftPetal.curveTo(7, 15, 4, 15, 4.5, 17.5);
            leftPetal.curveTo(5, 19, 7, 18.5, 9, 17);
            leftPetal.closePath();
            lg.fill(leftPetal);

            Path2D rightPetal = new Path2D.Double();
            rightPetal.moveTo(9, 17);
            rightPetal.curveTo(11, 15, 14, 15, 13.5, 17.5);
            rightPetal.curveTo(13, 19, 11, 18.5, 9, 17);
            rightPetal.closePath();
            lg.fill(rightPetal);
        } finally {
            lg.dispose();
        }
    }

    /**
     * Worn-stone block texture, tiled across a shape.
     *
     * <p>Clipped to {@code area} rather than drawn full-bleed so a panel's
     * rounded corners stay rounded. Kept very faint on purpose — at the design's
     * 0.12 it reads as surface, and any stronger it reads as noise.
     */
    public static void drawStoneTexture(Graphics2D g2, Shape area, double opacity) {
        Graphics2D sg = (Graphics2D) g2.create();
        try {
            sg.clip(area);
            Rectangle2D bounds = area.getBounds2D();

            double x0 = bounds.getMinX();
            double y0 = bounds.getMinY();
            double x1 = bounds.getMaxX();
            double y1 = bounds.getMaxY();

            for (double y = y0; y < y1 + 40; y += 40) {
                for (double x = x0; x < x1 + 60; x += 60) {
                    drawStoneTile(sg, x, y, opacity);
                }
            }
        } finally {
            sg.dispose();
        }
    }

    /** One 60x40 block of the stone pattern: mortar courses and hairline cracks. */
    private static void drawStoneTile(Graphics2D g2, double x, double y, double opacity) {
        // Horizontal mortar courses.
        g2.setStroke(new BasicStroke(0.6f));
        g2.setColor(Palette.alpha(Palette.STONE_MORTAR, 0.6 * opacity));
        g2.draw(new Line2D.Double(x, y, x + 60, y));
        g2.draw(new Line2D.Double(x, y + 40, x + 60, y + 40));

        g2.setStroke(new BasicStroke(0.5f));
        g2.setColor(Palette.alpha(Palette.STONE_MORTAR, 0.4 * opacity));
        g2.draw(new Line2D.Double(x, y + 20, x + 60, y + 20));

        // Vertical mortar, offset between courses so the blocks stagger.
        g2.setStroke(new BasicStroke(0.4f));
        g2.setColor(Palette.alpha(Palette.STONE_MORTAR, 0.3 * opacity));
        g2.draw(new Line2D.Double(x + 30, y, x + 30, y + 20));
        g2.draw(new Line2D.Double(x, y + 20, x, y + 40));
        g2.draw(new Line2D.Double(x + 60, y + 20, x + 60, y + 40));

        // Surface cracks.
        g2.setStroke(new BasicStroke(0.3f));
        g2.setColor(Palette.alpha(Palette.STONE_CRACK, 0.4 * opacity));
        Path2D crack = new Path2D.Double();
        crack.moveTo(x + 8, y + 5);
        crack.curveTo(x + 10, y + 7, x + 9, y + 12, x + 11, y + 15);
        g2.draw(crack);

        g2.setColor(Palette.alpha(Palette.STONE_CRACK, 0.3 * opacity));
        Path2D crack2 = new Path2D.Double();
        crack2.moveTo(x + 42, y + 22);
        crack2.curveTo(x + 44, y + 26, x + 43, y + 30, x + 45, y + 33);
        g2.draw(crack2);

        g2.setColor(Palette.alpha(Palette.STONE_CRACK, 0.35 * opacity));
        Path2D crack3 = new Path2D.Double();
        crack3.moveTo(x + 18, y + 25);
        crack3.curveTo(x + 16, y + 27, x + 17, y + 33, x + 15, y + 36);
        g2.draw(crack3);
    }

    /**
     * A hairline that fades out at both ends.
     *
     * <p>Used everywhere the design wants a rule. The fade is the whole point:
     * a rule with hard ends reads as a border, one that dissolves reads as
     * ornament.
     */
    public static void drawGoldRule(Graphics2D g2, double cx, double y, double width,
                                    Color gold, double peakOpacity) {
        double half = width / 2.0;
        if (half <= 1) {
            return;
        }
        g2.setPaint(new LinearGradientPaint(
                (float) (cx - half), (float) y,
                (float) (cx + half), (float) y,
                new float[] {0f, 0.3f, 0.5f, 0.7f, 1f},
                new Color[] {
                    Palette.alpha(gold, 0),
                    Palette.alpha(gold, peakOpacity * 0.66),
                    Palette.alpha(gold, peakOpacity),
                    Palette.alpha(gold, peakOpacity * 0.66),
                    Palette.alpha(gold, 0),
                }));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new Line2D.Double(cx - half, y, cx + half, y));
    }

    /**
     * A vertical version of {@link #drawGoldRule}, for the panel's outer seam.
     */
    public static void drawGoldSeam(Graphics2D g2, double x, double y0, double y1,
                                    double thickness, Color gold) {
        g2.setPaint(new LinearGradientPaint(
                (float) x, (float) y0,
                (float) x, (float) y1,
                new float[] {0f, 0.06f, 0.25f, 0.5f, 0.75f, 0.94f, 1f},
                new Color[] {
                    Palette.alpha(gold, 0),
                    Palette.alpha(gold, 0.20),
                    Palette.alpha(gold, 0.60),
                    Palette.alpha(gold, 0.75),
                    Palette.alpha(gold, 0.60),
                    Palette.alpha(gold, 0.20),
                    Palette.alpha(gold, 0),
                }));
        g2.fill(new Rectangle2D.Double(x - thickness / 2, y0, thickness, y1 - y0));
    }

    /**
     * An L-shaped corner bracket with a dot at its elbow.
     *
     * @param corner 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right
     */
    public static void drawCornerBracket(Graphics2D g2, double x, double y, double size,
                                         int corner, Color gold, double opacity) {
        Graphics2D cg = (Graphics2D) g2.create();
        try {
            cg.translate(x, y);
            // Each corner is the top-left bracket reflected into place, so the
            // four can never be drawn at subtly different proportions.
            double flipX = (corner == 1 || corner == 3) ? -1 : 1;
            double flipY = (corner >= 2) ? -1 : 1;
            cg.transform(new AffineTransform(flipX, 0, 0, flipY,
                    flipX < 0 ? size : 0, flipY < 0 ? size : 0));

            double inset = size * 0.07;
            double run = size * 0.86;

            cg.setColor(Palette.alpha(gold, opacity));
            cg.setStroke(new BasicStroke((float) (size * 0.043),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            Path2D bracket = new Path2D.Double();
            bracket.moveTo(inset, run);
            bracket.lineTo(inset, inset);
            bracket.lineTo(run, inset);
            cg.draw(bracket);

            double dot = size * 0.089;
            cg.fill(new Ellipse2D.Double(inset - dot, inset - dot, dot * 2, dot * 2));
        } finally {
            cg.dispose();
        }
    }

    /**
     * A short rule studded with dots, one pair of which flanks the menu's
     * eyebrow text.
     */
    public static void drawDotRow(Graphics2D g2, double x, double y, double width,
                                  Color gold) {
        g2.setColor(Palette.alpha(gold, 0.50));
        g2.setStroke(new BasicStroke(0.75f));
        g2.draw(new Line2D.Double(x, y, x + width, y));

        double[] stops = {6, 14, 22, 30, 38};
        for (int i = 0; i < stops.length; i++) {
            boolean centre = i == 2;
            double r = centre ? 2 : 1;
            double cx = x + stops[i] / 48.0 * width;
            g2.setColor(Palette.alpha(gold, centre ? 0.70 : 0.40));
            g2.fill(new Ellipse2D.Double(cx - r, y - r, r * 2, r * 2));
        }
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
