package com.guardiansofangkor.input;

import com.guardiansofangkor.util.GameConfig;
import com.guardiansofangkor.util.Platform;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Non-text keys: restart, quit, pause, clear-buffer. Word typing is <em>not</em>
 * handled here — that goes through {@link TypingInputField}'s document listener
 * so Khmer input-method composition works.
 *
 * <p>Uses key bindings rather than a KeyListener so shortcuts still fire while
 * the text field holds focus.
 *
 * <p>Restart is a two-key chord: Tab arms it, Enter within a few seconds
 * confirms. A single accidental Tab therefore cannot wipe a run, and the HUD
 * shows a prompt while the chord is armed so the state is never invisible.
 *
 * <p>Bindings: Tab then Enter restarts, Escape quits, Ctrl+P pauses,
 * Ctrl+Backspace clears the buffer.
 */
public class KeyboardHandler {

    private Runnable onPauseToggle = () -> { };
    private Runnable onClearBuffer = () -> { };
    private Runnable onQuit = () -> { };
    private Runnable onRestart = () -> { };

    /** Ticks remaining in the armed window, or zero when disarmed. */
    private int restartArmedTicks;

    /** Installs the bindings on the root component of the game window. */
    public void install(JComponent root) {
        if (root == null) {
            return;
        }
        InputMap inputMap = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = root.getActionMap();

        bind(inputMap, actionMap, KeyEvent.VK_TAB, 0, "goa.armRestart", this::armRestart);
        bind(inputMap, actionMap, KeyEvent.VK_ENTER, 0, "goa.confirmRestart",
                this::confirmRestart);
        bind(inputMap, actionMap, KeyEvent.VK_ESCAPE, 0, "goa.quit", () -> onQuit.run());

        // Pause lives on the platform's own command modifier plus P: Cmd+P on
        // macOS, Ctrl+P everywhere else. A bare P is impossible here, because
        // the typing field legitimately consumes every letter key.
        bind(inputMap, actionMap, KeyEvent.VK_P,
                Platform.commandModifier(), "goa.pause", () -> onPauseToggle.run());

        // Some macOS keyboards and remote sessions still deliver Ctrl. Binding
        // both costs nothing and avoids a dead shortcut.
        if (Platform.commandModifier() != KeyEvent.CTRL_DOWN_MASK) {
            bind(inputMap, actionMap, KeyEvent.VK_P,
                    KeyEvent.CTRL_DOWN_MASK, "goa.pauseAlt", () -> onPauseToggle.run());
        }

        bind(inputMap, actionMap, KeyEvent.VK_BACK_SPACE,
                KeyEvent.CTRL_DOWN_MASK, "goa.clear", () -> onClearBuffer.run());
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

    private void armRestart() {
        restartArmedTicks = GameConfig.RESTART_ARMED_TICKS;
    }

    private void confirmRestart() {
        if (restartArmedTicks > 0) {
            restartArmedTicks = 0;
            onRestart.run();
        }
    }

    /**
     * Called once per game tick so the armed window expires on its own.
     * Driven by the game loop rather than a separate timer so it pauses when the
     * game does.
     */
    public void tick() {
        if (restartArmedTicks > 0) {
            restartArmedTicks--;
        }
    }

    /** True while Tab has been pressed and Enter would restart. For the HUD prompt. */
    public boolean isRestartArmed() {
        return restartArmedTicks > 0;
    }

    /** Arms the chord programmatically, e.g. automatically on game over. */
    public void forceArmRestart() {
        armRestart();
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

    public void setOnRestart(Runnable onRestart) {
        this.onRestart = onRestart == null ? () -> { } : onRestart;
    }
}
