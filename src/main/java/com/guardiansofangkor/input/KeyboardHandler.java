package com.guardiansofangkor.input;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Non-text keys: pause, quit, clear-buffer. Word typing is <em>not</em> handled
 * here — that goes through {@link TypingInputField}'s document listener so Khmer
 * input-method composition works.
 *
 * <p>Uses key bindings rather than a KeyListener so shortcuts still fire while
 * the text field holds focus.
 */
public class KeyboardHandler {

    private Runnable onPauseToggle = () -> { };
    private Runnable onClearBuffer = () -> { };
    private Runnable onQuit = () -> { };

    /** Installs the bindings on the root component of the game window. */
    public void install(JComponent root) {
        if (root == null) {
            return;
        }
        InputMap inputMap = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = root.getActionMap();

        bind(inputMap, actionMap, KeyEvent.VK_ESCAPE, 0, "goa.pause", () -> onPauseToggle.run());
        bind(inputMap, actionMap, KeyEvent.VK_BACK_SPACE,
                KeyEvent.CTRL_DOWN_MASK, "goa.clear", () -> onClearBuffer.run());
        bind(inputMap, actionMap, KeyEvent.VK_Q,
                KeyEvent.CTRL_DOWN_MASK, "goa.quit", () -> onQuit.run());
    }

    private static void bind(InputMap inputMap, ActionMap actionMap,
                             int keyCode, int modifiers, String name, Runnable action) {
        inputMap.put(KeyStroke.getKeyStroke(keyCode, modifiers), name);
        actionMap.put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    public void setOnPauseToggle(Runnable onPauseToggle) {
        this.onPauseToggle = onPauseToggle == null ? () -> { } : onPauseToggle;
    }

    public void setOnClearBuffer(Runnable onClearBuffer) {
        this.onClearBuffer = onClearBuffer == null ? () -> { } : onClearBuffer;
    }

    public void setOnQuit(Runnable onQuit) {
        this.onQuit = onQuit == null ? () -> { } : onQuit;
    }
}
