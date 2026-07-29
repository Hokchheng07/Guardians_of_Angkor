package com.guardiansofangkor.engine;

import com.guardiansofangkor.i18n.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GameLoop — construction and failure containment")
class GameLoopTest {

    @Test
    @DisplayName("requires a state")
    void requiresState() {
        assertThrows(IllegalArgumentException.class, () -> new GameLoop(null, () -> { }));
    }

    @Test
    @DisplayName("a null repaint hook is tolerated")
    void nullHookIsTolerated() {
        GameLoop loop = new GameLoop(new GameState(Language.ENGLISH), null);
        assertFalse(loop.isRunning(), "should not start on its own");
    }

    @Test
    @DisplayName("starts and stops cleanly")
    void startsAndStops() {
        GameLoop loop = new GameLoop(new GameState(Language.ENGLISH), () -> { });

        loop.start();
        assertTrue(loop.isRunning());

        loop.stop();
        assertFalse(loop.isRunning());
    }

    @Test
    @DisplayName("a throwing repaint hook does not escape into Swing")
    void throwingHookIsContained() {
        // Swing logs an escaped exception and then fires the timer again, so an
        // unguarded throw here becomes sixty traces a second forever.
        GameState state = new GameState(Language.ENGLISH);
        AtomicInteger calls = new AtomicInteger();

        GameLoop loop = new GameLoop(state, () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("bad frame");
        });

        // Drive the tick directly rather than waiting on the Swing timer.
        tickSeveralTimes(loop);
        assertTrue(calls.get() > 0, "the hook should still have been attempted");
        assertTrue(loop.getFailureCount() > 0, "and the failure should be counted");
    }

    @Test
    @DisplayName("the repaint hook still runs when the update throws")
    void repaintRunsEvenIfUpdateFails() {
        // Otherwise a broken update leaves the window blank rather than frozen
        // on the last good frame.
        AtomicInteger repaints = new AtomicInteger();
        GameLoop loop = new GameLoop(new ExplodingState(), repaints::incrementAndGet);

        tickSeveralTimes(loop);

        assertTrue(repaints.get() > 0,
                "the player should keep seeing the last good frame");
    }

    @Test
    @DisplayName("sustained failure stops the loop and reports once")
    void sustainedFailureStopsAndReports() {
        AtomicInteger fatalReports = new AtomicInteger();
        GameLoop loop = new GameLoop(new ExplodingState(), () -> { });
        loop.setOnFatalError(reason -> fatalReports.incrementAndGet());

        loop.start();
        for (int i = 0; i < 200; i++) {
            tickOnce(loop);
        }

        assertFalse(loop.isRunning(),
                "a loop that cannot tick must stop rather than flood the console");
        assertEquals(1, fatalReports.get(),
                "the player should be told once, not on every tick");
    }

    @Test
    @DisplayName("clearFailures lets the loop be resumed after a restart")
    void clearFailuresAllowsResume() {
        GameLoop loop = new GameLoop(new ExplodingState(), () -> { });
        for (int i = 0; i < 200; i++) {
            tickOnce(loop);
        }
        assertTrue(loop.getFailureCount() > 0);

        loop.clearFailures();
        loop.start();

        assertTrue(loop.isRunning(), "should be restartable after clearing the streak");
        loop.stop();
    }

    // ---- helpers -----------------------------------------------------------

    /** Invokes the private tick via the timer's action listener. */
    private static void tickOnce(GameLoop loop) {
        for (java.awt.event.ActionListener listener : timerOf(loop).getActionListeners()) {
            listener.actionPerformed(null);
        }
    }

    /** Reaching the end of this without an exception escaping is the assertion. */
    private static void tickSeveralTimes(GameLoop loop) {
        for (int i = 0; i < 5; i++) {
            tickOnce(loop);
        }
    }

    private static javax.swing.Timer timerOf(GameLoop loop) {
        try {
            java.lang.reflect.Field field = GameLoop.class.getDeclaredField("timer");
            field.setAccessible(true);
            return (javax.swing.Timer) field.get(loop);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("GameLoop.timer is no longer reachable", e);
        }
    }

    /** A state whose update always fails, standing in for a corrupt run. */
    private static final class ExplodingState extends GameState {
        ExplodingState() {
            super(Language.ENGLISH);
        }

        @Override
        public void update() {
            throw new IllegalStateException("simulated engine failure");
        }
    }
}
