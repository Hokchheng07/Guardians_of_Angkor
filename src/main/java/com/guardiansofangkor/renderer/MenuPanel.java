package com.guardiansofangkor.renderer;

import com.guardiansofangkor.engine.Difficulty;
import com.guardiansofangkor.engine.MenuItem;
import com.guardiansofangkor.engine.MenuState;
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

    /** Drives the selected entry's breathing glow. */
    private final Timer animator;
    private double glowPhase;

    private Runnable onStartRun = () -> { };
    private Runnable onResumeRun = () -> { };
    private Runnable onExit = () -> { };
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
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (hoverAt(e.getX(), e.getY())) {
                    activate();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        this.animator = new Timer(GameConfig.TICK_INTERVAL_MS, e -> {
            glowPhase += 0.055;
            if (glowPhase > Math.PI * 2) {
                glowPhase -= Math.PI * 2;
            }
            this.state.tick();
            repaint();
        });
    }

    /** Starts the idle animation and takes keyboard focus. */
    public void activateScreen() {
        state.reset();
        animator.start();
        requestFocusInWindow();
        repaint();
    }

    /** Stops animating when the menu is not on screen. */
    public void deactivateScreen() {
        animator.stop();
    }

    // ---- input -------------------------------------------------------------

    private void handleKey(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP, KeyEvent.VK_W -> {
                state.moveUp();
                repaint();
            }
            case KeyEvent.VK_DOWN, KeyEvent.VK_S -> {
                state.moveDown();
                repaint();
            }
            case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> activate();
            case KeyEvent.VK_ESCAPE, KeyEvent.VK_BACK_SPACE -> {
                MenuState.Outcome outcome = state.back();
                if (outcome == MenuState.Outcome.EXIT) {
                    onExit.run();
                } else {
                    onScreenChanged.accept(state.getScreen());
                }
                repaint();
            }
            default -> {
                // Everything else is ignored on the menu.
            }
        }
    }

    private void activate() {
        MenuState.Outcome outcome = state.activate();
        switch (outcome) {
            case START_RUN -> onStartRun.run();
            case RESUME_RUN -> onResumeRun.run();
            case EXIT -> onExit.run();
            case OPEN_DIFFICULTY, BACK -> onScreenChanged.accept(state.getScreen());
            case NONE -> {
                // Locked entry — MenuState is already showing the reason.
            }
        }
        repaint();
    }

    /**
     * Moves the highlight to whatever entry is under the cursor.
     *
     * @return true when the cursor is over an entry
     */
    private boolean hoverAt(int mouseX, int mouseY) {
        int count = state.getScreen() == MenuState.Screen.MAIN
                ? MenuItem.values().length
                : Difficulty.values().length;

        for (int i = 0; i < count; i++) {
            Rectangle bounds = MenuRenderer.entryBounds(i, state.getScreen());
            if (bounds.contains(mouseX, mouseY)) {
                if (state.getScreen() == MenuState.Screen.MAIN) {
                    state.select(MenuItem.values()[i]);
                } else {
                    state.select(Difficulty.values()[i]);
                }
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

            renderer.draw(g2, state, sprites.menuBackground(), glowPhase);
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
