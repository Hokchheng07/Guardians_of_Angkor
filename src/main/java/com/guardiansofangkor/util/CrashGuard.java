package com.guardiansofangkor.util;

/**
 * Runs work that must never take the whole game down with it.
 *
 * <p>The game loop and the renderer both fire sixty times a second. An unguarded
 * exception in either is the worst failure mode available: Swing catches it,
 * prints a stack trace, and then <em>keeps firing</em>. The player gets a frozen
 * window that is still open, while the console fills with thousands of copies of
 * the same trace. Nothing recovers and nothing tells them what happened.
 *
 * <p>So failures are counted rather than propagated:
 *
 * <ul>
 *   <li>The first few are logged in full, because that is the diagnostic
 *       information worth having.</li>
 *   <li>After that logging goes quiet — a hundred identical traces say nothing
 *       the first one did not.</li>
 *   <li>If failures keep happening back to back, the guard reports the work as
 *       hopeless so the caller can stop and show the player something honest
 *       instead of pretending.</li>
 * </ul>
 *
 * <p>Catches {@link Throwable} deliberately. An {@code Error} here — a bad
 * texture blowing the heap, a native imaging fault — is exactly the case where
 * silently continuing is worse than a clean stop.
 */
public final class CrashGuard {

    /** How many failures get a full stack trace before logging goes quiet. */
    private static final int REPORT_LIMIT = 3;

    private final String label;
    private final int hopelessAfter;

    private int totalFailures;
    private int consecutiveFailures;
    private boolean silenced;
    private Throwable lastFailure;

    /**
     * @param label         name used in log lines, e.g. "game loop"
     * @param hopelessAfter consecutive failures before {@link #isHopeless()}
     *                      starts returning true
     */
    public CrashGuard(String label, int hopelessAfter) {
        this.label = label == null ? "task" : label;
        this.hopelessAfter = Math.max(1, hopelessAfter);
    }

    /**
     * Runs {@code action}, absorbing anything it throws.
     *
     * @return true if it completed cleanly
     */
    public boolean run(Runnable action) {
        if (action == null) {
            return true;
        }
        try {
            action.run();
            consecutiveFailures = 0;
            return true;
        } catch (Throwable t) {
            record(t);
            return false;
        }
    }

    private void record(Throwable t) {
        totalFailures++;
        consecutiveFailures++;
        lastFailure = t;

        if (totalFailures <= REPORT_LIMIT) {
            System.err.println("[CrashGuard] Failure #" + totalFailures
                    + " in " + label + ":");
            t.printStackTrace();

            if (totalFailures == REPORT_LIMIT) {
                System.err.println("[CrashGuard] Further " + label
                        + " failures will be counted but not printed.");
                silenced = true;
            }
        }
    }

    /**
     * True once the guarded work has failed on enough consecutive attempts that
     * retrying is pointless.
     */
    public boolean isHopeless() {
        return consecutiveFailures >= hopelessAfter;
    }

    public int getTotalFailures() {
        return totalFailures;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public boolean isSilenced() {
        return silenced;
    }

    /** The most recent throwable, or null if nothing has failed. */
    public Throwable getLastFailure() {
        return lastFailure;
    }

    /** A short, player-readable description of what went wrong. */
    public String describeLastFailure() {
        if (lastFailure == null) {
            return "";
        }
        String message = lastFailure.getMessage();
        String type = lastFailure.getClass().getSimpleName();
        return (message == null || message.isBlank()) ? type : type + ": " + message;
    }

    /** Clears the streak, e.g. after the player restarts a run. */
    public void reset() {
        consecutiveFailures = 0;
    }
}
