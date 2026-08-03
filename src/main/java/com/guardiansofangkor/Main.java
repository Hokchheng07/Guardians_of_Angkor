package com.guardiansofangkor;

import com.guardiansofangkor.engine.GameLoop;
import com.guardiansofangkor.engine.GameState;
import com.guardiansofangkor.engine.MenuState;
import com.guardiansofangkor.i18n.FontManager;
import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.input.KeyboardHandler;
import com.guardiansofangkor.input.TypingInputField;
import com.guardiansofangkor.matching.ResolveResult;
import com.guardiansofangkor.renderer.GamePanel;
import com.guardiansofangkor.renderer.MenuPanel;
import com.guardiansofangkor.renderer.SpriteCache;
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
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;

/**
 * Entry point. Assembles the window, wires the front end to the game, restores
 * any saved progress and starts the loop.
 *
 * <p>Two screens live in one window, swapped with a {@link CardLayout}: the menu
 * and the game. Only one is animating at a time — the menu's timer stops when
 * play begins, and the game loop stops when the menu returns.
 *
 * <p>Controls in game: type to attack, Tab then Enter to restart, Escape to
 * quit, Cmd+P (macOS) or Ctrl+P to pause.
 *
 * <p>Failure policy: every callback Swing invokes is wrapped, because an
 * exception escaping into Swing is logged and then <em>ignored</em> — leaving a
 * window that looks alive but is not.
 */
public final class Main {

    private static final String CARD_MENU = "menu";
    private static final String CARD_GAME = "game";

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
        // Unlocks are needed the instant the menu opens, which is before the
        // player has decided whether to resume anything — so they are seeded
        // separately from the run itself.
        state.restoreProgress(saved);

        AutosaveHook autosave = new AutosaveHook(saveManager, state::toSaveData);
        autosave.register();

        SpriteCache sprites = new SpriteCache();

        // ---- game screen ---------------------------------------------------

        GamePanel panel = new GamePanel(state);
        TypingInputField input = new TypingInputField();
        KeyboardHandler keys = new KeyboardHandler();

        input.setTypingFont(FontManager.wordFont(language, 22, Font.BOLD));

        JPanel gameRoot = new JPanel(new BorderLayout());
        gameRoot.setBackground(Color.BLACK);
        gameRoot.add(panel, BorderLayout.CENTER);
        gameRoot.add(input, BorderLayout.SOUTH);
        gameRoot.setBorder(BorderFactory.createEmptyBorder());

        // ---- front end -----------------------------------------------------

        MenuState menuState = new MenuState(saved.hasResumableRun());
        menuState.setProgress(state.getProgress());
        MenuPanel menuPanel = new MenuPanel(menuState, sprites);

        JPanel root = new JPanel(new CardLayout());
        root.add(menuPanel, CARD_MENU);
        root.add(gameRoot, CARD_GAME);

        // The game's shortcuts are window-scoped, so they must be muted while
        // the menu is showing or Escape would quit from inside the menu.
        keys.setActiveWhen(gameRoot::isShowing);

        JFrame frame = new JFrame("Guardians of Angkor — Word Defense");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(root);
        frame.setResizable(false);

        CrashGuard inputGuard = new CrashGuard("input handling", Integer.MAX_VALUE);
        CrashGuard controlGuard = new CrashGuard("controls", Integer.MAX_VALUE);
        CrashGuard menuGuard = new CrashGuard("menu actions", Integer.MAX_VALUE);

        // Typing is a Swing document callback, so a throw here would be
        // swallowed by the toolkit and the player would just see keystrokes
        // stop working, with no clue why.
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

        Runnable showGame = () -> {
            menuPanel.deactivateScreen();
            ((CardLayout) root.getLayout()).show(root, CARD_GAME);
            input.resetForNewRun();
            input.requestFocusInWindow();
            loop.clearFailures();
            loop.start();
            panel.repaint();
        };

        Runnable showMenu = () -> {
            loop.stop();
            ((CardLayout) root.getLayout()).show(root, CARD_MENU);
            menuState.setContinueAvailable(state.getLevel() > 0 && !state.isGameOver());
            // A run that was just won may have opened the next tier. Refreshing
            // here rather than only at startup means the player sees it unlock
            // on the way back to the menu, not on their next launch.
            menuState.setProgress(state.getProgress());
            menuPanel.activateScreen();
        };

        // ---- menu actions --------------------------------------------------

        menuPanel.setOnStartRun(() -> menuGuard.run(() -> {
            state.restartWith(menuState.getSelectedDifficulty());
            autosave.saveQuietly();
            showGame.run();
        }));

        menuPanel.setOnResumeRun(() -> menuGuard.run(() -> {
            state.restoreFrom(saveManager.load());
            state.beginIntro();
            showGame.run();
        }));

        menuPanel.setOnExit(() -> menuGuard.run(() -> {
            autosave.saveQuietly();
            System.exit(0);
        }));

        // ---- game controls -------------------------------------------------

        keys.setOnClearBuffer(() -> controlGuard.run(input::clearBuffer));

        keys.setOnQuit(() -> controlGuard.run(() -> {
            autosave.saveQuietly();
            showMenu.run();
        }));

        keys.setOnRestart(() -> controlGuard.run(() -> {
            state.restart();
            input.resetForNewRun();
            input.requestFocusInWindow();
            autosave.saveQuietly();
            panel.repaint();
        }));

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

        keys.install(gameRoot);

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

        ((CardLayout) root.getLayout()).show(root, CARD_MENU);
        menuPanel.activateScreen();
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
