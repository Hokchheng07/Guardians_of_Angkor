package com.guardiansofangkor.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CrashGuard — absorbing repeated failures")
class CrashGuardTest {

    @Test
    @DisplayName("clean work reports success and records nothing")
    void cleanWorkIsUntouched() {
        CrashGuard guard = new CrashGuard("test", 5);
        AtomicInteger runs = new AtomicInteger();

        assertTrue(guard.run(runs::incrementAndGet));

        assertEquals(1, runs.get());
        assertEquals(0, guard.getTotalFailures());
        assertFalse(guard.isHopeless());
    }

    @Test
    @DisplayName("a throwing task is absorbed rather than propagated")
    void throwsAreAbsorbed() {
        CrashGuard guard = new CrashGuard("test", 5);

        // The whole point: this must not escape and reach Swing.
        assertFalse(guard.run(() -> {
            throw new IllegalStateException("boom");
        }));
        assertEquals(1, guard.getTotalFailures());
    }

    @Test
    @DisplayName("errors are caught too, not just exceptions")
    void errorsAreCaught() {
        CrashGuard guard = new CrashGuard("test", 5);

        assertFalse(guard.run(() -> {
            throw new StackOverflowError("deep");
        }));
        assertEquals(1, guard.getTotalFailures());
    }

    @Test
    @DisplayName("a success resets the consecutive streak")
    void successResetsStreak() {
        CrashGuard guard = new CrashGuard("test", 3);

        guard.run(CrashGuardTest::boom);
        guard.run(CrashGuardTest::boom);
        assertEquals(2, guard.getConsecutiveFailures());

        guard.run(() -> { });

        assertEquals(0, guard.getConsecutiveFailures(),
                "an occasional glitch must not accumulate toward hopeless");
        assertEquals(2, guard.getTotalFailures(), "the total still counts them");
        assertFalse(guard.isHopeless());
    }

    @Test
    @DisplayName("only sustained failure is declared hopeless")
    void sustainedFailureIsHopeless() {
        CrashGuard guard = new CrashGuard("test", 3);

        guard.run(CrashGuardTest::boom);
        guard.run(CrashGuardTest::boom);
        assertFalse(guard.isHopeless(), "two in a row could still be transient");

        guard.run(CrashGuardTest::boom);
        assertTrue(guard.isHopeless());
    }

    @Test
    @DisplayName("logging goes quiet after the first few failures")
    void loggingIsSilencedAfterAFewFailures() {
        CrashGuard guard = new CrashGuard("test", Integer.MAX_VALUE);

        for (int i = 0; i < 50; i++) {
            guard.run(CrashGuardTest::boom);
        }

        assertTrue(guard.isSilenced(),
                "fifty identical traces say nothing the first one did not");
        assertEquals(50, guard.getTotalFailures(), "but they are all still counted");
    }

    @Test
    @DisplayName("the last failure is described for the player")
    void describesLastFailure() {
        CrashGuard guard = new CrashGuard("test", 5);
        guard.run(() -> {
            throw new IllegalArgumentException("bad sprite");
        });

        assertNotNull(guard.getLastFailure());
        assertTrue(guard.describeLastFailure().contains("bad sprite"));
        assertTrue(guard.describeLastFailure().contains("IllegalArgumentException"));
    }

    @Test
    @DisplayName("a message-less throwable still describes readably")
    void describesThrowableWithoutMessage() {
        CrashGuard guard = new CrashGuard("test", 5);
        guard.run(() -> {
            throw new NullPointerException();
        });

        assertEquals("NullPointerException", guard.describeLastFailure());
    }

    @Test
    @DisplayName("reset clears the streak but keeps the history")
    void resetClearsStreakOnly() {
        CrashGuard guard = new CrashGuard("test", 2);
        guard.run(CrashGuardTest::boom);
        guard.run(CrashGuardTest::boom);
        assertTrue(guard.isHopeless());

        guard.reset();

        assertFalse(guard.isHopeless());
        assertEquals(2, guard.getTotalFailures());
    }

    @Test
    @DisplayName("a null task is a no-op, not a crash")
    void nullTaskIsSafe() {
        CrashGuard guard = new CrashGuard("test", 5);

        assertTrue(guard.run(null));
        assertEquals(0, guard.getTotalFailures());
    }

    private static void boom() {
        throw new IllegalStateException("boom");
    }
}
