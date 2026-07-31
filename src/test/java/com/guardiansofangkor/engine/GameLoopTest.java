package com.guardiansofangkor.engine;

import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.util.GameConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GameLoop — wall-clock pacing and failure containment")
class GameLoopTest {

    /**
     * A clock the test moves by hand.
     *
     * <p>The loop reads real elapsed time to decide how much to simulate, so
     * firing the timer in a tight loop would correctly do nothing at all. Tests
     * therefore advance this rather than the callback count — which is the
     * whole property under test.
     */
    private static final class FakeClock {
        private long nanos = 1_000_000_000L;

        long read() {
            return nanos;
        }

        void advance(long by) {
            nanos += by;
        }

        void advanceTicks(double ticks) {
            advance((long) (GameConfig.NANOS_PER_TICK * ticks));
        }
    }

    /** A loop plus the clock driving it, since tests always need both. */
    private record Rig(GameLoop loop, FakeClock clock) { }

    private static Rig rig(GameState state, Runnable repaintHook) {
        FakeClock clock = new FakeClock();
        return new Rig(new GameLoop(state, repaintHook, clock::read), clock);
    }

    // ---- construction ------------------------------------------------------

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

    // ---- pacing ------------------------------------------------------------

    @Test
    @DisplayName("a wake-up with no time elapsed simulates nothing")
    void noTimeMeansNoTicks() {
        // A timer that fires faster than the tick rate must not make the game
        // run fast, which is the same bug as slow motion with the sign flipped.
        CountingState state = new CountingState();
        Rig rig = rig(state, () -> { });

        for (int i = 0; i < 20; i++) {
            tickOnce(rig.loop());
        }

        assertEquals(0, state.updates,
                "no wall-clock time passed, so no simulation should have");
    }

    @Test
    @DisplayName("one tick of real time is one tick of simulation")
    void realTimeMapsToTicks() {
        CountingState state = new CountingState();
        Rig rig = rig(state, () -> { });
        tickOnce(rig.loop());

        for (int i = 0; i < 30; i++) {
            rig.clock().advanceTicks(1);
            tickOnce(rig.loop());
        }

        assertEquals(30, state.updates);
    }

    @Test
    @DisplayName("a slow machine catches up rather than running in slow motion")
    void slowFramesStillRunFullSpeed() {
        // The reported bug: Windows delivered roughly half the callbacks and the
        // whole game played at half speed. Half the wake-ups now means two ticks
        // each, not half the simulation.
        CountingState state = new CountingState();
        Rig rig = rig(state, () -> { });
        tickOnce(rig.loop());

        for (int i = 0; i < 30; i++) {
            rig.clock().advanceTicks(2);
            tickOnce(rig.loop());
        }

        assertEquals(60, state.updates,
                "thirty wake-ups over sixty ticks of real time should simulate sixty");
    }

    @Test
    @DisplayName("fractional overshoot is banked, not discarded")
    void leftoverTimeIsKept() {
        // Dropping the remainder each frame would lose a few milliseconds every
        // wake-up, which is slow motion again — just gentler.
        CountingState state = new CountingState();
        Rig rig = rig(state, () -> { });
        tickOnce(rig.loop());

        for (int i = 0; i < 100; i++) {
            rig.clock().advanceTicks(1.5);
            tickOnce(rig.loop());
        }

        assertEquals(150, state.updates, "150 ticks of real time should simulate 150");
    }

    @Test
    @DisplayName("a huge clock jump does not fast-forward the game")
    void clockJumpsAreClamped() {
        // Laptop sleep, a window drag, a debugger breakpoint. Uncapped, the game
        // would work through every second the player was not playing.
        CountingState state = new CountingState();
        Rig rig = rig(state, () -> { });
        tickOnce(rig.loop());

        rig.clock().advance(60L * 1_000_000_000L);
        tickOnce(rig.loop());

        assertTrue(state.updates > 0, "some time should still pass");
        assertTrue(state.updates <= GameConfig.TARGET_FPS / 4,
                "a minute of absence simulated " + state.updates + " ticks");
    }

    @Test
    @DisplayName("several ticks in one wake-up still repaint only once")
    void oneRepaintPerWakeUp() {
        // Painting intermediate states nobody sees is the wasted work that made
        // the machine fall behind to begin with.
        AtomicInteger repaints = new AtomicInteger();
        Rig rig = rig(new CountingState(), repaints::incrementAndGet);
        tickOnce(rig.loop());

        rig.clock().advanceTicks(3);
        tickOnce(rig.loop());

        assertEquals(1, repaints.get());
    }

    // ---- failure containment -----------------------------------------------

    @Test
    @DisplayName("a throwing repaint hook does not escape into Swing")
    void throwingHookIsContained() {
        // Swing logs an escaped exception and then fires the timer again, so an
        // unguarded throw here becomes sixty traces a second forever.
        AtomicInteger calls = new AtomicInteger();

        Rig rig = rig(new GameState(Language.ENGLISH), () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("bad frame");
        });

        // Drive the tick directly rather than waiting on the Swing timer.
        tickSeveralTimes(rig);
        assertTrue(calls.get() > 0, "the hook should still have been attempted");
        assertTrue(rig.loop().getFailureCount() > 0, "and the failure should be counted");
    }

    @Test
    @DisplayName("the repaint hook still runs when the update throws")
    void repaintRunsEvenIfUpdateFails() {
        // Otherwise a broken update leaves the window blank rather than frozen
        // on the last good frame.
        AtomicInteger repaints = new AtomicInteger();
        Rig rig = rig(new ExplodingState(), repaints::incrementAndGet);

        tickSeveralTimes(rig);

        assertTrue(repaints.get() > 0,
                "the player should keep seeing the last good frame");
    }

    @Test
    @DisplayName("sustained failure stops the loop and reports once")
    void sustainedFailureStopsAndReports() {
        AtomicInteger fatalReports = new AtomicInteger();
        Rig rig = rig(new ExplodingState(), () -> { });
        rig.loop().setOnFatalError(reason -> fatalReports.incrementAndGet());

        rig.loop().start();
        for (int i = 0; i < 200; i++) {
            rig.clock().advanceTicks(1);
            tickOnce(rig.loop());
        }

        assertFalse(rig.loop().isRunning(),
                "a loop that cannot tick must stop rather than flood the console");
        assertEquals(1, fatalReports.get(),
                "the player should be told once, not on every tick");
    }

    @Test
    @DisplayName("clearFailures lets the loop be resumed after a restart")
    void clearFailuresAllowsResume() {
        Rig rig = rig(new ExplodingState(), () -> { });
        for (int i = 0; i < 200; i++) {
            rig.clock().advanceTicks(1);
            tickOnce(rig.loop());
        }
        assertTrue(rig.loop().getFailureCount() > 0);

        rig.loop().clearFailures();
        rig.loop().start();

        assertTrue(rig.loop().isRunning(),
                "should be restartable after clearing the streak");
        rig.loop().stop();
    }

    // ---- helpers -----------------------------------------------------------

    /** Invokes the private tick via the timer's action listener. */
    private static void tickOnce(GameLoop loop) {
        for (java.awt.event.ActionListener listener : timerOf(loop).getActionListeners()) {
            listener.actionPerformed(null);
        }
    }

    /** Reaching the end of this without an exception escaping is the assertion. */
    private static void tickSeveralTimes(Rig rig) {
        for (int i = 0; i < 5; i++) {
            rig.clock().advanceTicks(1);
            tickOnce(rig.loop());
        }
    }

    /** Counts updates without simulating anything, so tick maths is isolated. */
    private static final class CountingState extends GameState {
        private int updates;

        CountingState() {
            super(Language.ENGLISH);
        }

        @Override
        public void update() {
            updates++;
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
