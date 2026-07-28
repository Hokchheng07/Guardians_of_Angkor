package com.guardiansofangkor.engine;

import com.guardiansofangkor.util.GameConfig;

import javax.swing.Timer;

/**
 * Fixed-interval game loop driven by a {@link javax.swing.Timer}, so every tick
 * runs on the Event Dispatch Thread. That is what makes it safe for the tick to
 * touch state the renderer also reads without any locking.
 *
 * <p>The loop takes a repaint hook as a {@link Runnable} rather than a reference
 * to GamePanel — that keeps the engine package free of any Swing UI dependency
 * beyond the timer itself, and makes the loop testable with a no-op hook.
 */
public class GameLoop {

    private final GameState state;
    private final Runnable repaintHook;
    private final Timer timer;

    public GameLoop(GameState state, Runnable repaintHook) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        this.state = state;
        this.repaintHook = repaintHook == null ? () -> { } : repaintHook;
        this.timer = new Timer(GameConfig.TICK_INTERVAL_MS, e -> tick());
        this.timer.setCoalesce(true);
    }

    private void tick() {
        state.update();
        repaintHook.run();
    }

    public void start() {
        timer.start();
    }

    public void stop() {
        timer.stop();
    }

    public boolean isRunning() {
        return timer.isRunning();
    }

    public GameState getState() {
        return state;
    }
}
