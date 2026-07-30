package com.guardiansofangkor.renderer;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

/**
 * Large headline text drawn as vector outlines rather than rasterised glyphs.
 *
 * <p>This exists because of a specific Java2D behaviour: {@code drawString}
 * rasterises glyphs at the font's declared point size and then pushes that
 * bitmap through the current transform. Scaling the {@link Graphics2D} and then
 * drawing text therefore <em>upscales a bitmap</em> — which is exactly what the
 * countdown looked like. Animating the size by scaling the context is the wrong
 * tool for type.
 *
 * <p>Working from {@link GlyphVector#getOutline()} instead gives real geometry,
 * so it stays crisp at any size and at fractional sizes between frames. It also
 * makes a proper halo possible: the outline is stroked outward at decreasing
 * alpha, which is a genuine glow rather than the same string stamped at four
 * offsets.
 */
public final class DisplayText {

    private DisplayText() {
        // Drawing helper — not instantiable.
    }

    /**
     * Draws {@code text} centred on a point, with a soft halo behind it.
     *
     * @param centreX    horizontal centre
     * @param centreY    vertical <em>optical</em> centre — the ink is centred
     *                   here, not the font's baseline or line box
     * @param fillTop    gradient colour at the top of the glyphs
     * @param fillBottom gradient colour at the bottom
     * @param glow       halo colour, or null for no halo
     * @param glowAlpha  halo strength, 0 to 1
     * @param alpha      overall opacity, 0 to 1
     */
    public static void drawCentred(Graphics2D g2, String text, Font font,
                                   double centreX, double centreY,
                                   Color fillTop, Color fillBottom,
                                   Color glow, float glowAlpha, float alpha) {
        if (text == null || text.isEmpty() || font == null) {
            return;
        }

        Graphics2D tg = (Graphics2D) g2.create();
        try {
            // Pure stroke geometry and fractional metrics matter here: without
            // them the outline is snapped to whole pixels and the animation
            // judders as it grows.
            tg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            tg.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);
            tg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            tg.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, clamp(alpha)));

            Shape outline = centredOutline(tg, text, font, centreX, centreY);
            Rectangle2D bounds = outline.getBounds2D();

            if (glow != null && glowAlpha > 0.001f) {
                drawHalo(tg, outline, glow, glowAlpha * clamp(alpha));
            }

            tg.setPaint(new GradientPaint(
                    0, (float) bounds.getMinY(), fillTop,
                    0, (float) bounds.getMaxY(), fillBottom));
            tg.fill(outline);
        } finally {
            tg.dispose();
        }
    }

    /** Single-colour convenience form. */
    public static void drawCentred(Graphics2D g2, String text, Font font,
                                   double centreX, double centreY,
                                   Color fill, Color glow,
                                   float glowAlpha, float alpha) {
        drawCentred(g2, text, font, centreX, centreY, fill, fill, glow, glowAlpha, alpha);
    }

    /**
     * The glyph outline, translated so its ink is centred on the given point.
     *
     * <p>Centred on the <em>visual</em> bounds rather than font metrics, because
     * a row of numerals has no descenders and centring on the line box leaves it
     * visibly high.
     */
    private static Shape centredOutline(Graphics2D g2, String text, Font font,
                                        double centreX, double centreY) {
        FontRenderContext frc = g2.getFontRenderContext();
        GlyphVector glyphs = font.createGlyphVector(frc, text);
        Shape raw = glyphs.getOutline();
        Rectangle2D bounds = raw.getBounds2D();

        double dx = centreX - bounds.getCenterX();
        double dy = centreY - bounds.getCenterY();
        return AffineTransform.getTranslateInstance(dx, dy).createTransformedShape(raw);
    }

    /**
     * Halo built by stroking the outline outward in decreasing bands.
     *
     * <p>Wide-and-faint through to narrow-and-stronger, which accumulates into a
     * smooth falloff. Stamping the text at offsets instead produces four visible
     * ghosts.
     */
    private static void drawHalo(Graphics2D g2, Shape outline, Color glow, float strength) {
        float[] widths = {26f, 19f, 13f, 8f, 4f};
        float[] alphas = {0.10f, 0.13f, 0.17f, 0.22f, 0.28f};

        g2.setColor(glow);
        for (int i = 0; i < widths.length; i++) {
            g2.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, clamp(alphas[i] * strength)));
            g2.setStroke(new BasicStroke(widths[i],
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(outline);
        }
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(strength)));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
