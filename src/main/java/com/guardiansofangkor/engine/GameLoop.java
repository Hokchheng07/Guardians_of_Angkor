package com.guardiansofangkor.engine;

import com.guardiansofangkor.util.CrashGuard;
import com.guardiansofangkor.util.GameConfig;

import javax.swing.Timer;
import java.util.function.Consumer;

/**
 * Fixed-interval game loop driven by a {@link javax.swing.Timer}, so every tick
 * runs on the Event Dispatch Thread. That is what makes it safe for the tick to
 * touch state the renderer also reads without any locking.
 *
 * <p>The loop takes a repaint hook as a {@link Runnable} rather than a reference
 * to GamePanel — that keeps the engine package free of any Swing UI dependency
 * beyond the timer itself, and makes the loop testable with a no-op hook.
 *
 * <p>Ticks are wrapped in a {@link CrashGuard}. Left unguarded, a single bad
 * tick becomes sixty stack traces a second forever, because Swing logs the
 * exception and then fires the timer again — the player is left with a window
 * that is open, frozen, and silent about why. Instead a stray failure is
 * absorbed and play continues, while a persistent one stops the loop and hands
 * the caller a message worth showing.
 */
public class GameLoop {

    /**
     * Consecutive failed ticks before the loop gives up. Half a second of solid
     * failure — long enough to ride out a transient glitch, short enough that
     * the player is not staring at a frozen screen.
     */
    private static final int HOPELESS_AFTER_TICKS = GameConfig.TARGET_FPS / 2;

    private final GameState state;
    private final Runnable repaintHook;
    private final Timer timer;
    private final CrashGuard guard = new CrashGuard("game loop", HOPELESS_AFTER_TICKS);

    /** Notified with a player-readable reason when the loop stops for good. */
    private Consumer<String> onFatalError = reason -> { };

    /** Guards against reporting the same fatal state on every subsequent tick. */
    private boolean fatalReported;

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
        guard.run(this::runTick);

        // Reported once, not once per tick — the player gets a single dialog,
        // however many times this is reached afterwards.
        if (guard.isHopeless() && !fatalReported) {
            fatalReported = true;

            // Stop before the console fills and the window sits there lying
            // about being alive.
            timer.stop();
            String reason = guard.describeLastFailure();
            System.err.println("[GameLoop] Stopped after "
                    + guard.getConsecutiveFailures()
                    + " consecutive failed ticks. Last error: " + reason);
            onFatalError.accept(reason);
        }
    }

    /**
     * One tick of simulation plus one repaint.
     *
     * <p>The repaint is invoked even when the update throws, so the player keeps
     * seeing the last good frame rather than a blank window — but both sit
     * inside the same guard, so a failure in either is counted.
     */
    private void runTick() {
        try {
            state.update();
        } finally {
            repaintHook.run();
        }
    }

    /**
     * Registers the callback fired when the loop stops because of repeated
     * failures. Receives a short description of the last error.
     */
    public void setOnFatalError(Consumer<String> onFatalError) {
        this.onFatalError = onFatalError == null ? reason -> { } : onFatalError;
    }

    /** Clears the failure streak, e.g. when the player restarts after an error. */
    public void clearFailures() {
        guard.reset();
        fatalReported = false;
    }

    /** How many ticks have thrown over the life of this loop. */
    public int getFailureCount() {
        return guard.getTotalFailures();
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
