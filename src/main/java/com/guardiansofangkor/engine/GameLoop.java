package com.guardiansofangkor.engine;

import com.guardiansofangkor.util.CrashGuard;
import com.guardiansofangkor.util.GameConfig;

import javax.swing.Timer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Fixed-timestep game loop driven by a {@link javax.swing.Timer}, so every tick
 * runs on the Event Dispatch Thread. That is what makes it safe for the tick to
 * touch state the renderer also reads without any locking.
 *
 * <p><b>Simulation speed is measured against the wall clock, not against how
 * often the timer fires.</b> This is load-bearing and was a real bug: the loop
 * used to run exactly one tick per timer callback and assume sixty of those
 * happened per second. Every speed, duration and cooldown in the game is
 * written in ticks, so on any machine that could not deliver sixty callbacks a
 * second the entire game ran proportionally slow — not stuttery, but smoothly,
 * uniformly in slow motion. It reproduced on Windows and not on macOS, because
 * Windows rounds Swing timer requests to its ~15.6ms scheduler granularity (so
 * a 16ms request can land on 31ms, i.e. half rate) and because Java2D there
 * falls back to software for a lot of what this game paints.
 *
 * <p>So the loop accumulates <em>real elapsed nanoseconds</em> and runs however
 * many whole ticks have come due, which may be none on a fast machine and
 * several on a slow one. A slow machine now renders fewer frames per second
 * rather than playing a slower game — the correct thing to degrade.
 *
 * <p>Catch-up is capped at {@link #MAX_CATCHUP_TICKS}. Without a cap this is
 * the classic spiral of death: a frame that overruns asks for extra ticks, the
 * extra ticks make the next frame overrun further, and the game locks solid.
 * The cap also covers the laptop-lid case — after a sleep, a window drag or a
 * debugger breakpoint the clock has jumped by seconds, and uncapped the game
 * would fast-forward through every one of them.
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

    /**
     * Most simulation ticks one wake-up may run.
     *
     * <p>Five means the loop still holds true speed down to twelve frames a
     * second, which is far below anything playable — so in practice the cap is
     * a safety net rather than a limit anyone reaches. Past that the game does
     * genuinely slow down, which is the honest failure: the alternative is
     * simulating faster than the machine can draw, and the player cannot react
     * to frames they never see.
     */
    private static final int MAX_CATCHUP_TICKS = 5;

    private final GameState state;
    private final Runnable repaintHook;
    private final Timer timer;
    private final CrashGuard guard = new CrashGuard("game loop", HOPELESS_AFTER_TICKS);

    /** Source of monotonic nanoseconds. Swappable so tests can drive time. */
    private final LongSupplier nanoClock;

    /** When the last wake-up was seen. Negative until the first one. */
    private long lastNanos = -1;

    /** Real time owed to the simulation but not yet spent, in nanoseconds. */
    private long accumulator;

    /** Notified with a player-readable reason when the loop stops for good. */
    private Consumer<String> onFatalError = reason -> { };

    /** Guards against reporting the same fatal state on every subsequent tick. */
    private boolean fatalReported;

    public GameLoop(GameState state, Runnable repaintHook) {
        this(state, repaintHook, System::nanoTime);
    }

    /**
     * Test seam: drives the loop from a supplied clock.
     *
     * <p>Necessary because the whole point of this class is now that it reads a
     * clock — a test that fires the timer in a tight loop would otherwise see
     * zero elapsed time and run zero ticks.
     */
    GameLoop(GameState state, Runnable repaintHook, LongSupplier nanoClock) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        this.state = state;
        this.repaintHook = repaintHook == null ? () -> { } : repaintHook;
        this.nanoClock = nanoClock == null ? System::nanoTime : nanoClock;
        this.timer = new Timer(GameConfig.TICK_INTERVAL_MS, e -> tick());

        // Coalescing is right precisely because the clock, not the callback
        // count, decides how much runs: a backlog of queued timer events would
        // be redundant work, since one wake-up already catches up all the time
        // that has passed.
        this.timer.setCoalesce(true);
    }

    private void tick() {
        int owed = ticksDue();
        if (owed > 0) {
            guard.run(() -> runTicks(owed));
        }

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
     * Banks the time since the last wake-up and reports how many whole ticks
     * that buys.
     *
     * <p>The accumulator is clamped <em>before</em> the ticks are counted, so a
     * clock jump is discarded rather than queued up to be worked through over
     * the following frames.
     */
    private int ticksDue() {
        long now = nanoClock.getAsLong();
        if (lastNanos < 0) {
            // First wake-up: there is no previous reading to measure against,
            // and treating "now minus zero" as elapsed time would hand the
            // simulation the entire uptime of the JVM.
            lastNanos = now;
            return 0;
        }

        long elapsed = Math.max(0, now - lastNanos);
        lastNanos = now;

        long ceiling = GameConfig.NANOS_PER_TICK * MAX_CATCHUP_TICKS;
        accumulator = Math.min(accumulator + elapsed, ceiling);

        int owed = (int) (accumulator / GameConfig.NANOS_PER_TICK);
        accumulator -= owed * GameConfig.NANOS_PER_TICK;
        return owed;
    }

    /**
     * The simulation ticks that have come due, then one repaint.
     *
     * <p>One repaint however many ticks ran: drawing intermediate states nobody
     * would see is exactly the wasted work that made the machine fall behind in
     * the first place.
     *
     * <p>The repaint is invoked even when an update throws, so the player keeps
     * seeing the last good frame rather than a blank window — and both sit
     * inside the same guard, so a failure in either is counted.
     */
    private void runTicks(int count) {
        try {
            for (int i = 0; i < count; i++) {
                state.update();
            }
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
