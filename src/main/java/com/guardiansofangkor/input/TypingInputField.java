package com.guardiansofangkor.input;

import com.guardiansofangkor.renderer.Palette;
import com.guardiansofangkor.util.CrashGuard;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

/**
 * The persistent input bar the player types into, painted as a frosted glass
 * plate rather than a default Swing text box.
 *
 * <p>Uses a {@link DocumentListener} rather than a raw KeyListener on purpose:
 * Khmer combines base consonants with diacritics across several codepoints per
 * visual character, and input-method composition delivers those as document
 * edits, not as clean {@code keyTyped} chars (dev brief Section 5.1). Listening
 * at the document level means the same code path handles English and Khmer.
 */
public class TypingInputField extends JTextField {

    // Stone-dark and gold, matching the top HUD bar. The two frames are one
    // system; if they drift apart the screen stops reading as a single
    // interface. Palette is the single source of truth for both.
    private static final Color COLOR_FG = Palette.HUD_TEXT_WHITE;
    private static final Color COLOR_CARET = Palette.HUD_TEXT_GOLD;
    private static final Color COLOR_HINT = Palette.alpha(Palette.HUD_TEXT_DIM, 0.65);

    private static final Color GLASS_TOP = new Color(0x3A, 0x31, 0x26, 150);
    private static final Color GLASS_BOTTOM = new Color(0x1E, 0x19, 0x14, 226);
    private static final Color GLASS_TOP_ERROR = new Color(0x6B, 0x2E, 0x28, 165);
    private static final Color GLASS_BOTTOM_ERROR = new Color(0x2B, 0x14, 0x11, 232);

    private static final Color BORDER_OUTER = Palette.alpha(Palette.HUD_DIVIDER, 0.62);
    private static final Color BORDER_OUTER_ERROR = Palette.alpha(Palette.DANGER, 0.85);
    private static final Color SHEEN = new Color(0xF7, 0xD1, 0x6E, 30);

    private static final int PLATE_HEIGHT = 62;
    private static final int PLATE_MAX_WIDTH = 780;
    private static final int ARC = 20;

    /** Notified with the full buffer contents every time it changes. */
    private Consumer<String> onBufferChanged = text -> { };

    /** Guards against reacting to our own programmatic edits. */
    private boolean suppressEvents;

    private int errorFlashTicks;

    /** Slow pulse so the plate feels alive rather than static. */
    private double glowPhase;

    /** Absorbs painting failures so a bad frame cannot become a repaint flood. */
    private final CrashGuard paintGuard =
            new CrashGuard("typing field", Integer.MAX_VALUE);

    private String hintText = "type to strike";

    public TypingInputField() {
        setOpaque(false);
        setForeground(COLOR_FG);
        setCaretColor(COLOR_CARET);
        setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        setHorizontalAlignment(CENTER);
        setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        setPreferredSize(new Dimension(0, PLATE_HEIGHT + 34));

        // Tab and Enter are the restart chord, so this field must not eat them.
        // Without this, Tab moves focus out of the field and never reaches the
        // window-level key binding.
        setFocusTraversalKeysEnabled(false);
        getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "none");
        getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "none");

        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                fireChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                fireChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                fireChanged();
            }
        });
    }

    // ---- painting ----------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        // Guarded for the same reason as the game panel: a throwing paint gets
        // retried forever. Falling back to a plain bar keeps the field usable.
        if (!paintGuard.run(() -> paintPlate(g))) {
            g.setColor(Palette.HUD_BG);
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        // Text and caret last, on top of the glass.
        super.paintComponent(g);
    }

    private void paintPlate(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            boolean error = errorFlashTicks > 0;

            // Outer frame: the same stone bar and gold hairline as the HUD, so
            // the top and bottom of the screen read as one chrome system rather
            // than a game window with a text box stuck underneath.
            g2.setColor(Palette.HUD_BG);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(Palette.HUD_DIVIDER);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(0, 0, getWidth(), 0);

            int plateWidth = Math.min(PLATE_MAX_WIDTH, getWidth() - 60);
            int x = (getWidth() - plateWidth) / 2;
            int y = (getHeight() - PLATE_HEIGHT) / 2;
            RoundRectangle2D plate =
                    new RoundRectangle2D.Double(x, y, plateWidth, PLATE_HEIGHT, ARC, ARC);

            drawOuterGlow(g2, plate, error);
            drawGlassBody(g2, plate, x, y, plateWidth, error);
            drawSheen(g2, plate, x, y, plateWidth);
            drawBorder(g2, plate, error);

            if (getDocument().getLength() == 0 && isEnabled()) {
                drawHint(g2, y);
            }
        } finally {
            g2.dispose();
        }
    }

    /** Soft halo behind the plate — the thing that reads as "lit glass". */
    private void drawOuterGlow(Graphics2D g2, RoundRectangle2D plate, boolean error) {
        double pulse = 0.5 + 0.5 * Math.sin(glowPhase);
        float strength = error ? 0.34f : (float) (0.11 + 0.07 * pulse);

        Graphics2D glow = (Graphics2D) g2.create();
        try {
            glow.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, strength));
            glow.setColor(error ? BORDER_OUTER_ERROR : BORDER_OUTER);
            for (int i = 4; i >= 1; i--) {
                double pad = i * 3.0;
                glow.setStroke(new BasicStroke((float) (pad * 0.9)));
                glow.draw(new RoundRectangle2D.Double(
                        plate.getX() - pad / 2, plate.getY() - pad / 2,
                        plate.getWidth() + pad, plate.getHeight() + pad,
                        ARC + pad, ARC + pad));
            }
        } finally {
            glow.dispose();
        }
    }

    private void drawGlassBody(Graphics2D g2, RoundRectangle2D plate,
                               int x, int y, int width, boolean error) {
        g2.setPaint(new GradientPaint(
                x, y, error ? GLASS_TOP_ERROR : GLASS_TOP,
                x, y + PLATE_HEIGHT, error ? GLASS_BOTTOM_ERROR : GLASS_BOTTOM));
        g2.fill(plate);
    }

    /**
     * Highlight across the top third only. Clipping it to the plate is what
     * stops it looking like a stripe pasted over the top.
     */
    private void drawSheen(Graphics2D g2, RoundRectangle2D plate,
                           int x, int y, int width) {
        Graphics2D sheen = (Graphics2D) g2.create();
        try {
            Area clip = new Area(plate);
            clip.intersect(new Area(new java.awt.Rectangle(
                    x, y, width, (int) (PLATE_HEIGHT * 0.45))));
            sheen.setClip(clip);
            sheen.setPaint(new GradientPaint(
                    x, y, SHEEN,
                    x, y + PLATE_HEIGHT * 0.45f, Palette.alpha(SHEEN, 0)));
            sheen.fillRect(x, y, width, PLATE_HEIGHT);
        } finally {
            sheen.dispose();
        }
    }

    private void drawBorder(Graphics2D g2, RoundRectangle2D plate, boolean error) {
        g2.setColor(error ? BORDER_OUTER_ERROR : BORDER_OUTER);
        g2.setStroke(new BasicStroke(1.6f));
        g2.draw(plate);

        // Inner hairline, inset by a pixel, for glass thickness.
        g2.setColor(Palette.alpha(Palette.HUD_TEXT_GOLD, error ? 0.20 : 0.14));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Double(
                plate.getX() + 2, plate.getY() + 2,
                plate.getWidth() - 4, plate.getHeight() - 4,
                ARC - 3, ARC - 3));
    }

    private void drawHint(Graphics2D g2, int plateY) {
        g2.setFont(getFont().deriveFont(Font.PLAIN, 17f));
        g2.setColor(COLOR_HINT);
        FontMetrics fm = g2.getFontMetrics();
        int width = fm.stringWidth(hintText);
        g2.drawString(hintText,
                (getWidth() - width) / 2,
                plateY + PLATE_HEIGHT / 2 + fm.getAscent() / 2 - 2);
    }

    // ---- input plumbing ----------------------------------------------------

    /** Registers the callback that receives the buffer on every edit. */
    public void setOnBufferChanged(Consumer<String> listener) {
        this.onBufferChanged = listener == null ? text -> { } : listener;
    }

    /** Sets the bundled Khmer-capable font (Phase 9). */
    public void setTypingFont(Font font) {
        if (font != null) {
            setFont(font);
        }
    }

    public void setHintText(String hintText) {
        this.hintText = hintText == null ? "" : hintText;
    }

    private void fireChanged() {
        if (suppressEvents) {
            return;
        }
        String text = safeText();
        // Never mutate a Document from inside its own listener — defer instead.
        SwingUtilities.invokeLater(() -> onBufferChanged.accept(text));
    }

    private String safeText() {
        try {
            return getDocument().getText(0, getDocument().getLength());
        } catch (BadLocationException e) {
            // Cannot happen with these bounds, but the brief requires that no
            // I/O or text boundary is left unguarded — degrade to empty.
            return "";
        }
    }

    /**
     * Rewrites the buffer without re-triggering the change callback. Used to
     * revert a rejected keystroke back to the last valid prefix on a typo.
     */
    public void revertTo(String validBuffer) {
        String target = validBuffer == null ? "" : validBuffer;
        if (target.equals(safeText())) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            suppressEvents = true;
            try {
                setText(target);
                setCaretPosition(getDocument().getLength());
            } finally {
                suppressEvents = false;
            }
        });
    }

    /** Clears the buffer, e.g. after a word is completed. */
    public void clearBuffer() {
        revertTo("");
    }

    /** Full reset for a new run: clears text, re-enables input, drops the flash. */
    public void resetForNewRun() {
        errorFlashTicks = 0;
        setEnabled(true);
        clearBuffer();
        repaint();
    }

    /** Starts the red typo flash. Ticked down by {@link #tick()}. */
    public void flashError(int ticks) {
        this.errorFlashTicks = Math.max(this.errorFlashTicks, ticks);
        repaint();
    }

    /** Called once per game tick so the error flash and glow pulse advance. */
    public void tick() {
        glowPhase += 0.045;
        if (glowPhase > Math.PI * 2) {
            glowPhase -= Math.PI * 2;
        }
        if (errorFlashTicks > 0) {
            errorFlashTicks--;
        }
        repaint();
    }
}
