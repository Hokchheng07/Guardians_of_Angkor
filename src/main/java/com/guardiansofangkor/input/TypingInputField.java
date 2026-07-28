package com.guardiansofangkor.input;

import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.Color;
import java.awt.Font;
import java.util.function.Consumer;

/**
 * The persistent input bar the player types into.
 *
 * <p>Uses a {@link DocumentListener} rather than a raw KeyListener on purpose:
 * Khmer combines base consonants with diacritics across several codepoints per
 * visual character, and input-method composition delivers those as document
 * edits, not as clean {@code keyTyped} chars (dev brief Section 5.1). Listening
 * at the document level means the same code path handles English and Khmer.
 */
public class TypingInputField extends JTextField {

    private static final Color COLOR_BG = new Color(0x1B, 0x14, 0x28);
    private static final Color COLOR_FG = new Color(0xEC, 0xE6, 0xF5);
    private static final Color COLOR_CARET = new Color(0xE8, 0xB9, 0x3B);
    private static final Color COLOR_ERROR = new Color(0x8C, 0x2F, 0x39);

    /** Notified with the full buffer contents every time it changes. */
    private Consumer<String> onBufferChanged = text -> { };

    /** Guards against reacting to our own programmatic edits. */
    private boolean suppressEvents;

    private int errorFlashTicks;

    public TypingInputField() {
        setBackground(COLOR_BG);
        setForeground(COLOR_FG);
        setCaretColor(COLOR_CARET);
        setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        setHorizontalAlignment(CENTER);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 12, 10, 12));

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

    /** Starts the red typo flash. Ticked down by {@link #tick()}. */
    public void flashError(int ticks) {
        this.errorFlashTicks = Math.max(this.errorFlashTicks, ticks);
        setBackground(COLOR_ERROR);
    }

    /** Called once per game tick so the error flash can decay. */
    public void tick() {
        if (errorFlashTicks > 0) {
            errorFlashTicks--;
            if (errorFlashTicks == 0) {
                setBackground(COLOR_BG);
            }
        }
    }
}
