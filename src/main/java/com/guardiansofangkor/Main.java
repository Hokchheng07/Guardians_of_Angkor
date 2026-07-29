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
import com.guardiansofangkor.util.GameConfig;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
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
 * Ctrl+P to pause.
 */
public final class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                launch();
            } catch (RuntimeException e) {
                System.err.println("[Main] Fatal error during startup: " + e);
                e.printStackTrace();
            }
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

        input.setOnBufferChanged(text -> {
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
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.BLACK);
        root.add(panel, BorderLayout.CENTER);
        root.add(input, BorderLayout.SOUTH);
        root.setBorder(BorderFactory.createEmptyBorder());

        keys.setOnClearBuffer(input::clearBuffer);
        keys.setOnQuit(() -> {
            autosave.saveQuietly();
            System.exit(0);
        });
        keys.setOnRestart(() -> {
            state.restart();
            input.resetForNewRun();
            input.requestFocusInWindow();
            autosave.saveQuietly();
            panel.repaint();
        });
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
        keys.setOnPauseToggle(() -> {
            boolean nowPaused = state.togglePause();
            if (!state.isGameOver()) {
                input.setEnabled(!nowPaused);
                if (!nowPaused) {
                    input.requestFocusInWindow();
                }
            }
            panel.repaint();
        });

        JFrame frame = new JFrame("Guardians of Angkor — Word Defense");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(root);
        frame.setResizable(false);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        input.requestFocusInWindow();
        loop.start();
    }

    private Main() {
        // Entry point only — not instantiable.
    }
}
