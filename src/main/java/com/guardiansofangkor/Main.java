package com.guardiansofangkor;

import com.guardiansofangkor.engine.GameLoop;
import com.guardiansofangkor.engine.GameState;
import com.guardiansofangkor.engine.MenuState;
import com.guardiansofangkor.engine.TempleMap;
import com.guardiansofangkor.entities.Hero;
import com.guardiansofangkor.i18n.FontManager;
import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.input.KeyboardHandler;
import com.guardiansofangkor.input.TypingInputField;
import com.guardiansofangkor.matching.ResolveResult;
import com.guardiansofangkor.renderer.GamePanel;
import com.guardiansofangkor.renderer.MenuPanel;
import com.guardiansofangkor.renderer.SandboxTray;
import com.guardiansofangkor.audio.SoundManager;
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
import java.util.concurrent.atomic.AtomicBoolean;

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
        SaveManager saveManager = new SaveManager();
        SaveData saved = saveManager.load();

        // The player's last choice from the Options screen, remembered across
        // launches.
        Language language = saved.language();

        GameState state = new GameState(language);
        // Unlocks are needed the instant the menu opens, which is before the
        // player has decided whether to resume anything — so they are seeded
        // separately from the run itself.
        state.restoreProgress(saved);
        // The hero too: the autosave writes whatever the state holds, so a
        // launch-and-quit must not quietly reset the player's pick to default.
        state.setHero(Hero.fromKey(saved.heroKey()));
        state.setTempleMap(TempleMap.fromKey(saved.templeMapKey()));

        // The volume sliders belong to the menu, not the run, so they are
        // layered onto the run's snapshot here. Without it every autosave would
        // write the defaults back over the player's volumes. Seeded from the
        // save before the menu exists, because the shutdown hook can fire first.
        //
        // Until a run starts this session, the state holds no run at all —
        // level 0 — and saving it would overwrite the run on disk that Continue
        // is offering. So before then, the loaded save is written back with
        // only the settings changed.
        MenuState menuState = new MenuState(saved.hasResumableRun());
        menuState.setLanguage(language);
        menuState.setAudio(saved.audio());
        AtomicBoolean runTouched = new AtomicBoolean(false);
        AutosaveHook autosave = new AutosaveHook(saveManager, () -> {
            SaveData base = runTouched.get() ? state.toSaveData() : saved;
            return base.withSettings(menuState.getLanguage(), menuState.getAudio());
        });
        autosave.register();

        // Music starts at the saved volume, before the menu is on screen.
        SoundManager.apply(saved.audio());
        SoundManager.startMenuMusic();

        SpriteCache sprites = new SpriteCache();

        // ---- game screen ---------------------------------------------------

        GamePanel panel = new GamePanel(state);
        TypingInputField input = new TypingInputField();
        KeyboardHandler keys = new KeyboardHandler();

        input.setTypingFont(FontManager.wordFont(language, 22, Font.BOLD));

        SandboxTray tray = new SandboxTray(state);
        tray.setVisible(false);

        panel.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                // If we are in the Sandbox and the mouse is within 30 pixels of the right edge
                if (state.isSandbox() && e.getX() >= panel.getWidth() - 30) {
                    tray.setVisible(true);
                }
            }
        });

        JPanel gameRoot = new JPanel(new BorderLayout());
        gameRoot.setBackground(Color.BLACK);
        gameRoot.add(panel, BorderLayout.CENTER);
        gameRoot.add(tray, BorderLayout.EAST);
        gameRoot.add(input, BorderLayout.SOUTH);
        gameRoot.setBorder(BorderFactory.createEmptyBorder());

        // ---- front end -----------------------------------------------------

        menuState.setProgress(state.getProgress());
        menuState.setPreferredHero(state.getHero());
        menuState.setPreferredTemple(state.getTempleMap());
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

        // The engine counts shots; the UI sounds them. Sampled once per tick, so
        // several shots landing in one tick make one bow sound, not a pile-up.
        int[] shotsHeard = {state.getShotsLoosed()};

        GameLoop loop = new GameLoop(state, () -> {
            input.tick();
            if (state.getShotsLoosed() != shotsHeard[0]) {
                shotsHeard[0] = state.getShotsLoosed();
                SoundManager.playSFX(state.getHero().getShotSound());
            }
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
            SoundManager.startGameplayMusic(state.getTempleMap());
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
            menuState.setPreferredHero(state.getHero());
            menuState.setPreferredTemple(state.getTempleMap());
            SoundManager.startMenuMusic();
            menuPanel.activateScreen();
        };

        // ---- menu actions --------------------------------------------------

        menuPanel.setOnStartRun(() -> menuGuard.run(() -> {
            runTouched.set(true);
            tray.setVisible(false); // <-- Hide for normal play
            state.setSandboxMode(false); // Make sure Sandbox is off
            state.setTempleMap(menuState.getSelectedTemple());
            state.restartWith(menuState.getSelectedDifficulty(), menuState.getSelectedHero());
            autosave.saveQuietly();
            showGame.run();
        }));

        menuPanel.setOnStartSandbox(() -> menuGuard.run(() -> {
            runTouched.set(true);
            tray.setVisible(true);      // <-- Show the tray
            state.setSandboxMode(true); // Turn on God Mode & Disable Waves
            state.setHero(menuState.getSelectedHero());
            state.setTempleMap(menuState.getSelectedTemple());
            state.restart();            // Clean the board
            state.skipIntro();          // Skip the 3-2-1 countdown for fast testing
            showGame.run();             // Swap the screen to the game panel
        }));

        menuPanel.setOnResumeRun(() -> menuGuard.run(() -> {
            runTouched.set(true);
            tray.setVisible(false); // <-- Hide for normal play
            state.setSandboxMode(false); // Make sure Sandbox is off
            state.restoreFrom(saveManager.load());
            state.beginIntro();
            showGame.run();
        }));
        menuPanel.setOnSettingsChanged(() -> menuGuard.run(() -> {
            // Language is the one setting the game itself reads. GameState
            // swaps its word bank; the typing field is Swing, so its font is
            // swapped here. The play field notices on its own next paint.
            Language chosen = menuState.getLanguage();
            if (chosen != state.getLanguage()) {
                state.setLanguage(chosen);
                input.setTypingFont(FontManager.wordFont(chosen, 22, Font.BOLD));
            }
            // Volumes take effect immediately, including on the music playing.
            SoundManager.apply(menuState.getAudio());
        }));

        // Settings are written once, on the way out of Options, rather than on
        // every slider step — a drag would otherwise rewrite the save file
        // dozens of times a second.
        menuPanel.setOnScreenChanged(screen -> menuGuard.run(() -> {
            if (screen == MenuState.Screen.MAIN) {
                autosave.saveQuietly();
            }
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
