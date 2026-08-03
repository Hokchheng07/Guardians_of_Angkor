package com.guardiansofangkor.engine;

/**
 * The player's run of perfectly typed words, and what it is worth.
 *
 * <p>One step per word finished without a single wrong keystroke. Any mistype
 * ends the run outright — not a decay, not a partial loss. That is the whole
 * proposition: the combo is a claim about accuracy, and a combo that survived
 * one mistake would be a claim about mostly-accuracy, which is not a thing worth
 * displaying a number for.
 *
 * <p>Deliberately knows nothing about what was typed. Enemies, bolts, boons and
 * the finale's verse words all count the same, because from the player's side
 * they are the same act. {@link GameState} decides what a word is; this only
 * counts them.
 *
 * <p>Pure logic and separately testable, like everything else in this package —
 * the scoring rule in particular is much easier to reason about here than
 * embedded in the middle of input handling.
 */
public class ComboTracker {

    /**
     * How much each clean word adds to the score multiplier.
     *
     * <p>Small on purpose. The combo is meant to reward a good run over its
     * whole length rather than to make the first few words decisive, and a
     * steep early curve would mean a single unlucky typo cost more than the
     * words that earned it.
     */
    public static final double STEP = 0.05;

    /**
     * Where the multiplier stops climbing.
     *
     * <p>Twenty clean words doubles the score and that is the end of it. Without
     * a cap a long enough run makes every earlier level irrelevant to the final
     * total, which turns the scoreboard into a measure of how long somebody
     * managed to keep going rather than of how well they played.
     */
    public static final int CAP = 20;

    /**
     * Words needed before the combo is worth showing.
     *
     * <p>Below this the counter would spend most of a level flickering between
     * one and zero, which reads as a broken HUD element rather than as an
     * achievement.
     */
    public static final int DISPLAY_THRESHOLD = 3;

    private int count;
    private int best;

    /** Records a word finished with no wrong keystroke in it. */
    public void noteCleanWord() {
        count++;
        if (count > best) {
            best = count;
        }
    }

    /**
     * Records a mistype, ending the run.
     *
     * <p>A single wrong letter is enough, and the game's own typo handling is
     * what defines that: the input field reverts to the last valid prefix, so
     * the correction the player then makes is exactly the "had to backspace"
     * case. There is no separate check for it because there is no way to make
     * the mistake without one.
     */
    public void breakStreak() {
        count = 0;
    }

    /** Words in the current unbroken run. */
    public int getCount() {
        return count;
    }

    /** The longest run this game, for the end-of-run summary. */
    public int getBest() {
        return best;
    }

    /** True once the run is long enough to be worth putting on screen. */
    public boolean isWorthShowing() {
        return count >= DISPLAY_THRESHOLD;
    }

    /** Score multiplier, 1.0 with no combo and capped at 2.0. */
    public double getMultiplier() {
        return 1.0 + Math.min(count, CAP) * STEP;
    }

    /** How close the combo is to its ceiling, 0 to 1. For the HUD. */
    public double getFillFraction() {
        return Math.min(1.0, count / (double) CAP);
    }

    /** Clears everything, including the best. For a fresh run. */
    public void reset() {
        count = 0;
        best = 0;
    }

    @Override
    public String toString() {
        return "ComboTracker[" + count + " x" + getMultiplier() + "]";
    }
}
