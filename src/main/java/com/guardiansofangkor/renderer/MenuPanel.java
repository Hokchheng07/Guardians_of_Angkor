package com.guardiansofangkor.renderer;

import com.guardiansofangkor.audio.SoundManager;
import com.guardiansofangkor.engine.Difficulty;
import com.guardiansofangkor.engine.MenuItem;
import com.guardiansofangkor.engine.MenuState;
import com.guardiansofangkor.entities.Hero;
import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.util.CrashGuard;
import com.guardiansofangkor.util.GameConfig;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * The front-end screen: draws the menu and turns key and mouse input into
 * {@link MenuState} navigation.
 *
 * <p>Focusable with its own key listener rather than window-level key bindings.
 * The game screen already installs bindings on the window, and window-scoped
 * bindings fire whether or not their component is showing — so sharing that
 * mechanism would have the menu and the game both reacting to the same
 * keystroke.
 */
public class MenuPanel extends JPanel {

    private final MenuState state;
    private final MenuRenderer renderer = new MenuRenderer();
    private final SpriteCache sprites;
    private final CrashGuard paintGuard = new CrashGuard("menu", Integer.MAX_VALUE);
    private final CrashGuard tickGuard = new CrashGuard("menu tick", Integer.MAX_VALUE);

    /** Drives the selected entry's breathing glow. */
    private final Timer animator;
    private double glowPhase;

    private Runnable onStartSandbox = () -> { };
    private Runnable onStartRun = () -> { };
    private Runnable onResumeRun = () -> { };
    private Runnable onExit = () -> { };
    private Runnable onSettingsChanged = () -> { };

    /**
     * What was highlighted last time input was handled, so moving the
     * highlight — by key or by mouse — can sound the hover tick exactly once.
     */
    private String lastHighlight = "";

    /** The volume row being dragged, or null when no slider is held. */
    private MenuState.OptionRow draggingSlider;
    private Consumer<MenuState.Screen> onScreenChanged = screen -> { };

    public MenuPanel(MenuState state, SpriteCache sprites) {
        this.state = state == null ? new MenuState() : state;
        this.sprites = sprites == null ? new SpriteCache() : sprites;

        setPreferredSize(new Dimension(GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT));
        setBackground(Color.BLACK);
        setDoubleBuffered(true);
        setFocusable(true);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKey(e);
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                hoverAt(e.getX(), e.getY());
                soundHighlightChange();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                // Options rows act on press (below), not on click — a slider
                // has to respond the moment it is grabbed.
                if (MenuPanel.this.state.getScreen() == MenuState.Screen.OPTIONS) {
                    return;
                }
                if (hoverAt(e.getX(), e.getY())) {
                    activate();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (MenuPanel.this.state.getScreen() == MenuState.Screen.OPTIONS) {
                    pressOptions(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggingSlider != null
                        && MenuPanel.this.state.setSlider(draggingSlider,
                                MenuRenderer.sliderValueAt(e.getX()))) {
                    onSettingsChanged.run();
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                draggingSlider = null;
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        this.animator = new Timer(GameConfig.TICK_INTERVAL_MS, e -> tickGuard.run(() -> {
            glowPhase += 0.055;
            if (glowPhase > Math.PI * 2) {
                glowPhase -= Math.PI * 2;
            }
            this.state.tick();

            // A press that has finished depressing fires here rather than at the
            // moment of the click, which is what gives the menu its weight.
            dispatch(this.state.pollReady());
            repaint();
        }));
    }

    /** Starts the idle animation and takes keyboard focus. */
    public void activateScreen() {
        state.reset();
        syncHighlight();
        animator.start();
        requestFocusInWindow();
        repaint();
    }

    /** Stops animating when the menu is not on screen. */
    public void deactivateScreen() {
        animator.stop();
    }

    // ---- input -------------------------------------------------------------

    /** Plays the hover tick when the highlighted entry has changed. */
    private void soundHighlightChange() {
        String now = state.getScreen() + ":" + state.getSelectedIndex();
        if (!now.equals(lastHighlight)) {
            if (!lastHighlight.isEmpty()) {
                SoundManager.playSFX("hover.wav");
            }
            lastHighlight = now;
        }
    }

    /** Forgets the highlight without a sound, e.g. when a new screen opens. */
    private void syncHighlight() {
        lastHighlight = state.getScreen() + ":" + state.getSelectedIndex();
    }

    private void handleKey(KeyEvent e) {
        handleKeyInner(e);
        soundHighlightChange();
    }

    private void handleKeyInner(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP, KeyEvent.VK_W -> {
                state.moveUp();
                repaint();
            }
            case KeyEvent.VK_DOWN, KeyEvent.VK_S -> {
                state.moveDown();
                repaint();
            }
            // The heroes stand side by side, so left and right move between
            // them too. Elsewhere the lists are vertical and these do nothing.
            case KeyEvent.VK_LEFT, KeyEvent.VK_A -> sideways(-1);
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> sideways(1);
            case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> activate();
            case KeyEvent.VK_ESCAPE, KeyEvent.VK_BACK_SPACE -> {
                // Exit is immediate; backing out gets the same press delay as
                // any other button.
                MenuState.Outcome backed = state.back();
                if (backed == MenuState.Outcome.PENDING) {
                    SoundManager.playSFX("click.wav");
                }
                dispatch(backed);
                repaint();
            }
            default -> {
                // Everything else is ignored on the menu.
            }
        }
    }

    /**
     * Left and Right: move between the side-by-side heroes, or change the
     * highlighted setting. Elsewhere the lists are vertical and these do
     * nothing.
     */
    private void sideways(int direction) {
        switch (state.getScreen()) {
            case HERO -> {
                if (direction < 0) {
                    state.moveUp();
                } else {
                    state.moveDown();
                }
            }
            case OPTIONS -> {
                if (state.adjust(direction)) {
                    onSettingsChanged.run();
                }
            }
            default -> {
                // Vertical lists only.
            }
        }
        repaint();
    }

    /** A mouse press on the Options card: grab a slider, pick a language, or go back. */
    private void pressOptions(int x, int y) {
        MenuState.OptionRow[] rows = MenuState.OptionRow.values();
        for (MenuState.OptionRow row : rows) {
            if (!MenuRenderer.optionRowBounds(row.ordinal()).contains(x, y)) {
                continue;
            }
            state.select(row);
            if (row.isSlider() && MenuRenderer.sliderTrackBounds(row).contains(x, y)) {
                draggingSlider = row;
                if (state.setSlider(row, MenuRenderer.sliderValueAt(x))) {
                    onSettingsChanged.run();
                }
            } else if (row == MenuState.OptionRow.LANGUAGE) {
                Language[] languages = Language.values();
                for (int i = 0; i < languages.length; i++) {
                    if (MenuRenderer.languageSegmentBounds(i).contains(x, y)
                            && state.chooseLanguage(languages[i])) {
                        onSettingsChanged.run();
                    }
                }
            } else if (row == MenuState.OptionRow.BACK
                    && MenuRenderer.optionBackBounds().contains(x, y)) {
                dispatch(state.back());
            }
            repaint();
            return;
        }
    }

    /**
     * Registers a press. The action itself fires later, from
     * {@link MenuState#pollReady()} on the animation timer.
     */
    private void activate() {
        // Most presses resolve later through pollReady; a settings toggle
        // resolves at once, so its outcome has to be acted on here.
        MenuState.Outcome outcome = state.activate();
        if (outcome == MenuState.Outcome.PENDING
                || outcome == MenuState.Outcome.SETTINGS_CHANGED) {
            SoundManager.playSFX("click.wav");
        }
        if (outcome == MenuState.Outcome.SETTINGS_CHANGED) {
            dispatch(outcome);
        }
        repaint();
    }

    /** Acts on a resolved outcome. PENDING and NONE are both no-ops here. */
    private void dispatch(MenuState.Outcome outcome) {
        switch (outcome) {
            case START_RUN -> onStartRun.run();
            case RESUME_RUN -> onResumeRun.run();
            case START_SANDBOX -> onStartSandbox.run();
            case EXIT -> onExit.run();
            case SETTINGS_CHANGED -> onSettingsChanged.run();
            case OPEN_HERO, OPEN_DIFFICULTY, OPEN_OPTIONS, BACK -> {
                // A new screen starts on its default entry; that is not the
                // player moving the highlight, so it makes no hover sound.
                syncHighlight();
                onScreenChanged.accept(state.getScreen());
            }
            case PENDING, NONE -> {
                // Still depressing, or a locked entry that has already explained
                // itself. Nothing to do either way.
            }
        }
    }

    /**
     * Moves the highlight to whatever entry is under the cursor.
     *
     * @return true when the cursor is over an entry
     */
    private boolean hoverAt(int mouseX, int mouseY) {
        MenuState.Screen screen = state.getScreen();
        if (screen == MenuState.Screen.OPTIONS) {
            return hoverOptions(mouseX, mouseY);
        }
        int count = switch (screen) {
            case MAIN -> MenuItem.values().length;
            case HERO -> Hero.values().length;
            case DIFFICULTY -> Difficulty.values().length;
            case OPTIONS -> 0;
        };

        for (int i = 0; i < count; i++) {
            // On the hero screen the portrait cards are targets as well as the
            // list entries — they are the bigger, more obvious thing to click.
            boolean over = MenuRenderer.entryBounds(i, screen).contains(mouseX, mouseY)
                    || (screen == MenuState.Screen.HERO
                        && MenuRenderer.heroCardBounds(i).contains(mouseX, mouseY));
            if (over) {
                switch (screen) {
                    case MAIN -> state.select(MenuItem.values()[i]);
                    case HERO -> state.select(Hero.values()[i]);
                    case DIFFICULTY -> state.select(Difficulty.values()[i]);
                    case OPTIONS -> { }
                }
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                repaint();
                return true;
            }
        }
        setCursor(Cursor.getDefaultCursor());
        return false;
    }

    /** Highlights the Options row under the cursor, unless a slider is held. */
    private boolean hoverOptions(int mouseX, int mouseY) {
        if (draggingSlider != null) {
            return true;
        }
        for (MenuState.OptionRow row : MenuState.OptionRow.values()) {
            if (MenuRenderer.optionRowBounds(row.ordinal()).contains(mouseX, mouseY)) {
                state.select(row);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                repaint();
                return true;
            }
        }
        setCursor(Cursor.getDefaultCursor());
        return false;
    }

    // ---- painting ----------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (!paintGuard.run(() -> paintMenu(g))) {
            g.setColor(Palette.HUD_BG);
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    private void paintMenu(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);

            renderer.draw(g2, state, sprites, glowPhase);
        } finally {
            g2.dispose();
        }
    }

    // ---- wiring ------------------------------------------------------------

    public void setOnStartRun(Runnable onStartRun) {
        this.onStartRun = onStartRun == null ? () -> { } : onStartRun;
    }

    public void setOnResumeRun(Runnable onResumeRun) {
        this.onResumeRun = onResumeRun == null ? () -> { } : onResumeRun;
    }

    public void setOnStartSandbox(Runnable onStartSandbox) {
        this.onStartSandbox = onStartSandbox == null ? () -> { } : onStartSandbox;
    }

    /** Called whenever a setting changes; read the new values off the state. */
    public void setOnSettingsChanged(Runnable onSettingsChanged) {
        this.onSettingsChanged = onSettingsChanged == null ? () -> { } : onSettingsChanged;
    }

    public void setOnExit(Runnable onExit) {
        this.onExit = onExit == null ? () -> { } : onExit;
    }

    public void setOnScreenChanged(Consumer<MenuState.Screen> onScreenChanged) {
        this.onScreenChanged = onScreenChanged == null ? screen -> { } : onScreenChanged;
    }

    public MenuState getState() {
        return state;
    }
}
