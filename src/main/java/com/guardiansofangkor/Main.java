package com.guardiansofangkor;

import com.guardiansofangkor.engine.GameLoop;
import com.guardiansofangkor.engine.GameState;
import com.guardiansofangkor.i18n.FontManager;
import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.input.KeyboardHandler;
import com.guardiansofangkor.input.TypingInputField;
import com.guardiansofangkor.matching.ResolveResult;
import com.guardiansofangkor.renderer.GamePanel;
import com.guardiansofangkor.save.AutosaveHook;
import com.guardiansofangkor.save.SaveData;
import com.guardiansofangkor.save.SaveManager;
import com.guardiansofangkor.util.CrashGuard;
import com.guardiansofangkor.util.GameConfig;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;

/**
 * Entry point. Assembles the window, wires input to the game state, restores any
 * saved progress and starts the loop.
 *
 * <p>Controls: type to attack, Tab then Enter to restart, Escape to quit,
 * Cmd+P (macOS) or Ctrl+P to pause.
 *
 * <p>Failure policy: every callback that Swing invokes is wrapped, because an
 * exception escaping into Swing is logged and then <em>ignored</em> — leaving a
 * window that looks alive but is not. Anything unrecoverable ends with a dialog
 * that says so, and progress is saved first.
 */
public final class Main {

    public static void main(String[] args) {
        installGlobalHandler();
        SwingUtilities.invokeLater(() -> {
            try {
                launch();
            } catch (Throwable t) {
                System.err.println("[Main] Fatal error during startup: " + t);
                t.printStackTrace();
                showFatalDialog(null,
                        "Guardians of Angkor could not start.\n\n"
                                + describe(t)
                                + "\n\nSee the console for the full details.");
            }
        });
    }

    /**
     * Catches anything thrown on a thread that has no handler of its own —
     * chiefly the shutdown hook. Without this such failures vanish silently.
     */
    private static void installGlobalHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, t) -> {
            System.err.println("[Main] Uncaught error on thread " + thread.getName() + ":");
            t.printStackTrace();
        });
    }

    private static void launch() {
        Language language = Language.ENGLISH;

        SaveManager saveManager = new SaveManager();
        SaveData saved = saveManager.load();

        GameState state = new GameState(language);
        state.restoreFrom(saved);

        AutosaveHook autosave = new AutosaveHook(saveManager, state::toSaveData);
        autosave.register();

        GamePanel panel = new GamePanel(state);
        TypingInputField input = new TypingInputField();
        KeyboardHandler keys = new KeyboardHandler();

        input.setTypingFont(FontManager.wordFont(language, 22, Font.BOLD));

        // Typing is a Swing document callback, so a throw here would be
        // swallowed by the toolkit and the player would just see keystrokes
        // stop working, with no clue why.
        CrashGuard inputGuard = new CrashGuard("input handling", Integer.MAX_VALUE);
        input.setOnBufferChanged(text -> inputGuard.run(() -> {
            ResolveResult result = state.handleInput(text);
            switch (result.status()) {
                case TYPO -> {
                    input.flashError(GameConfig.TYPO_FLASH_TICKS);
                    input.revertTo(result.validBuffer());
                }
                case COMPLETED -> input.clearBuffer();
                default -> {
                    // Ambiguous or locked — leave the buffer as the player typed it.
                }
            }
            panel.repaint();
        }));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.BLACK);
        root.add(panel, BorderLayout.CENTER);
        root.add(input, BorderLayout.SOUTH);
        root.setBorder(BorderFactory.createEmptyBorder());

        JFrame frame = new JFrame("Guardians of Angkor — Word Defense");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(root);
        frame.setResizable(false);

        CrashGuard controlGuard = new CrashGuard("controls", Integer.MAX_VALUE);

        keys.setOnClearBuffer(() -> controlGuard.run(input::clearBuffer));

        keys.setOnQuit(() -> controlGuard.run(() -> {
            autosave.saveQuietly();
            System.exit(0);
        }));

        keys.setOnRestart(() -> controlGuard.run(() -> {
            state.restart();
            input.resetForNewRun();
            input.requestFocusInWindow();
            autosave.saveQuietly();
            panel.repaint();
        }));

        keys.install(root);

        GameLoop loop = new GameLoop(state, () -> {
            input.tick();
            keys.tick();
            panel.tick();

            if (state.isLevelJustCleared()) {
                autosave.saveQuietly();
            }
            if (state.isGameOver() && input.isEnabled()) {
                // Stop accepting typing and offer the restart chord immediately,
                // so the player does not have to discover Tab on their own.
                input.setEnabled(false);
                keys.forceArmRestart();
                autosave.saveQuietly();
            }

            panel.setRestartArmed(keys.isRestartArmed());
            panel.repaint();
        });

        // Pausing skips the simulation rather than stopping the loop, so the
        // renderer keeps running and can draw the overlay. Typing is disabled
        // while paused so keystrokes cannot leak through to the resolver.
        keys.setOnPauseToggle(() -> controlGuard.run(() -> {
            boolean nowPaused = state.togglePause();
            if (!state.isGameOver()) {
                input.setEnabled(!nowPaused);
                if (!nowPaused) {
                    input.requestFocusInWindow();
                }
            }
            panel.repaint();
        }));

        // The loop stops itself if ticks keep failing. Save what we have, then
        // tell the player rather than leaving a dead window on screen.
        loop.setOnFatalError(reason -> {
            autosave.saveQuietly();
            input.setEnabled(false);
            showFatalDialog(frame,
                    "The game had to stop.\n\n" + reason
                            + "\n\nYour progress has been saved.\n"
                            + "See the console for the full details.");
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        input.requestFocusInWindow();
        loop.start();
    }

    /** Shows an error the player can actually read, falling back to the console. */
    private static void showFatalDialog(JFrame owner, String message) {
        try {
            JOptionPane.showMessageDialog(owner, message,
                    "Guardians of Angkor", JOptionPane.ERROR_MESSAGE);
        } catch (Throwable t) {
            // Headless, or the toolkit itself is broken. The console message
            // above has already been printed, so there is nothing left to do.
            System.err.println(message);
        }
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        String type = t.getClass().getSimpleName();
        return (message == null || message.isBlank()) ? type : type + ": " + message;
    }

    private Main() {
        // Entry point only — not instantiable.
    }
}
