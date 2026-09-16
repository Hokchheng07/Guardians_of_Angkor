package com.guardiansofangkor;

import com.guardiansofangkor.audio.AudioManager;
import com.guardiansofangkor.audio.AudioManager.Sfx;
import com.guardiansofangkor.audio.AudioManager.Track;
import com.guardiansofangkor.engine.CharacterType;
import com.guardiansofangkor.engine.GameLoop;
import com.guardiansofangkor.engine.GameState;
import com.guardiansofangkor.engine.GameState.SoundEvent;
import com.guardiansofangkor.engine.MenuState;
import com.guardiansofangkor.i18n.FontManager;
import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.input.KeyboardHandler;
import com.guardiansofangkor.input.TypingInputField;
import com.guardiansofangkor.matching.ResolveResult;
import com.guardiansofangkor.renderer.CharacterSelectionPanel;
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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

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
    private static final String CARD_CHARACTER_SELECT = "character_select";
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
        AudioManager.getInstance().preloadAll();

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
        CharacterSelectionPanel charSelectPanel = new CharacterSelectionPanel();

        JPanel root = new JPanel(new CardLayout());
        root.add(menuPanel, CARD_MENU);
        root.add(charSelectPanel, CARD_CHARACTER_SELECT);
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
                    AudioManager.getInstance().playSFX(Sfx.HIT_WRONG);
                    input.flashError(GameConfig.TYPO_FLASH_TICKS);
                    input.revertTo(result.validBuffer());
                }
                case COMPLETED -> {
                    AudioManager.getInstance().playSFX(Sfx.HIT_CORRECT);
                    input.clearBuffer();
                }
                default -> {
                    // Ambiguous or locked — leave the buffer as the player typed it.
                }
            }
            panel.repaint();
        }));

        boolean[] wasGameOver = new boolean[]{false};

        GameLoop loop = new GameLoop(state, () -> {
            input.tick();
            keys.tick();
            panel.tick();

            // The target the player was typing at left the field mid-word — it
            // breached, or a Purge took it. The engine has already dropped the
            // buffer; the field is Swing's and has to be told separately, or the
            // next keystroke is measured against letters the engine forgot and
            // the player is charged a typo for something that was taken from
            // them.
            if (state.consumeBufferInvalidated()) {
                input.clearBuffer();
            }

            for (SoundEvent event : state.consumeSoundEvents()) {
                switch (event) {
                    case ENEMY_SPAWNED -> AudioManager.getInstance().playSFX(Sfx.ENEMY_SPAWN);
                    case BOSS_SPAWNED -> AudioManager.getInstance().playSFX(Sfx.BOSS_SPAWN);
                    case POWERUP_CLAIMED -> AudioManager.getInstance().playSFX(Sfx.POWERUP_COLLECT);
                }
            }

            if (state.isLevelJustCleared()) {
                AudioManager.getInstance().playSFX(Sfx.WAVE_COMPLETE);
                autosave.saveQuietly();
            }
            if (state.isGameOver()) {
                if (!wasGameOver[0]) {
                    wasGameOver[0] = true;
                    AudioManager.getInstance().stopMusic();
                    if (state.isVictory()) {
                        AudioManager.getInstance().playSFX(Sfx.VICTORY);
                    } else {
                        AudioManager.getInstance().playSFX(Sfx.GAME_OVER);
                    }
                }
                if (input.isEnabled()) {
                    // Stop accepting typing and offer the restart chord immediately,
                    // so the player does not have to discover Tab on their own.
                    input.setEnabled(false);
                    keys.forceArmRestart();
                    autosave.saveQuietly();
                }
            } else {
                wasGameOver[0] = false;
            }

            panel.setRestartArmed(keys.isRestartArmed());
            panel.repaint();
        });

        Runnable showGame = () -> {
            menuPanel.deactivateScreen();
            ((CardLayout) root.getLayout()).show(root, CARD_GAME);
            input.resetForNewRun();
            input.requestFocusInWindow();
            wasGameOver[0] = false;
            loop.clearFailures();
            loop.start();
            AudioManager.getInstance().switchMusic(Track.GAMEPLAY_THEME, true);
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
            AudioManager.getInstance().switchMusic(Track.MENU_THEME, true);
        };

        // ---- menu actions --------------------------------------------------

        menuPanel.setOnScreenChanged(screen -> {
            if (screen == MenuState.Screen.CHARACTER_SELECTION) {
                menuPanel.deactivateScreen();
                ((CardLayout) root.getLayout()).show(root, CARD_CHARACTER_SELECT);
            }
        });

        charSelectPanel.setOnSelect(() -> menuGuard.run(() -> {
            CharacterType selected = charSelectPanel.getSelectedCharacter();
            menuState.setSelectedCharacter(selected);
            state.getPlayer().setCharacterType(selected);
            state.restartWith(menuState.getSelectedDifficulty());
            autosave.saveQuietly();
            showGame.run();
        }));

        charSelectPanel.setOnBack(() -> menuGuard.run(() -> {
            menuState.openDifficulty();
            ((CardLayout) root.getLayout()).show(root, CARD_MENU);
            menuPanel.activateCurrentScreen();
        }));

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
            AudioManager.getInstance().shutdown();
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
            wasGameOver[0] = false;
            autosave.saveQuietly();
            AudioManager.getInstance().switchMusic(Track.GAMEPLAY_THEME, true);
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
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                AudioManager.getInstance().shutdown();
            }
        });
        frame.setVisible(true);

        ((CardLayout) root.getLayout()).show(root, CARD_MENU);
        menuPanel.activateScreen();
        AudioManager.getInstance().switchMusic(Track.MENU_THEME, true);
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
