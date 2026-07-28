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
 */
public final class Main {

    public static void main(String[] args) {
        // A crash during construction should still surface clearly rather than
        // dying silently on the EDT.
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
        keys.install(root);

        GameLoop loop = new GameLoop(state, () -> {
            input.tick();
            // Autosave on every wave clear, not only on a clean exit (Section 5.4).
            if (state.isWaveJustCleared()) {
                autosave.saveQuietly();
            }
            if (state.isGameOver() && input.isEnabled()) {
                input.setEnabled(false);
                autosave.saveQuietly();
            }
            panel.repaint();
        });

        keys.setOnPauseToggle(() -> {
            if (loop.isRunning()) {
                loop.stop();
            } else {
                loop.start();
            }
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
