package com.guardiansofangkor.engine;

import com.guardiansofangkor.util.GameConfig;

/**
 * The beat between choosing a difficulty and the first word appearing: a short
 * loading phase, then a three-count.
 *
 * <p>This exists for two reasons beyond decoration. Dropping straight into play
 * means the first enemies are already walking before the player's hands are on
 * the keys, which reads as unfair rather than fast. And the countdown gives the
 * eye a moment to find the temple, the plaza and the input bar before any of
 * them matter.
 *
 * <p>Pure logic with no Swing — the renderer reads {@link #getPhase()} and the
 * progress accessors and decides what that looks like.
 */
public class IntroSequence {

    /** Stages of the opening, in order. */
    public enum Phase {
        /** Assets settling, progress bar filling. */
        LOADING,

        /** Three, two, one. */
        COUNTDOWN,

        /** The brief "defend" flash before play begins. */
        GO,

        /** Finished; the simulation may run. */
        DONE
    }

    /** Long enough to read the tier name, short enough not to annoy. */
    private static final int LOADING_TICKS = GameConfig.TARGET_FPS * 5 / 4;

    /** Ticks per counted number. */
    private static final int COUNT_TICKS = GameConfig.TARGET_FPS * 3 / 4;

    /** How many numbers are counted. */
    private static final int COUNT_FROM = 3;

    /** Hold on the final flash. */
    private static final int GO_TICKS = GameConfig.TARGET_FPS / 2;

    private final Difficulty difficulty;

    private Phase phase = Phase.LOADING;
    private int phaseTicks;

    /** Which number is showing, counting down. Zero outside the countdown. */
    private int count = COUNT_FROM;

    public IntroSequence() {
        this(Difficulty.defaultChoice());
    }

    public IntroSequence(Difficulty difficulty) {
        this.difficulty = difficulty == null ? Difficulty.defaultChoice() : difficulty;
    }

    /** Advances one tick. Called from the game loop while the sim is frozen. */
    public void update() {
        if (phase == Phase.DONE) {
            return;
        }
        phaseTicks++;

        switch (phase) {
            case LOADING -> {
                if (phaseTicks >= LOADING_TICKS) {
                    phase = Phase.COUNTDOWN;
                    phaseTicks = 0;
                    count = COUNT_FROM;
                }
            }
            case COUNTDOWN -> {
                if (phaseTicks >= COUNT_TICKS) {
                    phaseTicks = 0;
                    count--;
                    if (count <= 0) {
                        phase = Phase.GO;
                    }
                }
            }
            case GO -> {
                if (phaseTicks >= GO_TICKS) {
                    phase = Phase.DONE;
                    phaseTicks = 0;
                }
            }
            case DONE -> {
                // Terminal.
            }
        }
    }

    /** True while the simulation should stay frozen. */
    public boolean isActive() {
        return phase != Phase.DONE;
    }

    public boolean isDone() {
        return phase == Phase.DONE;
    }

    public Phase getPhase() {
        return phase;
    }

    /** The number currently showing, 3 down to 1. */
    public int getCount() {
        return Math.max(0, count);
    }

    /** Loading progress, 0 to 1. Zero once loading has finished. */
    public double getLoadingProgress() {
        if (phase != Phase.LOADING) {
            return phase == Phase.COUNTDOWN || phase == Phase.GO || phase == Phase.DONE
                    ? 1.0 : 0.0;
        }
        return Math.min(1.0, phaseTicks / (double) LOADING_TICKS);
    }

    /**
     * Progress through the current beat, 0 at its start and 1 at its end.
     *
     * <p>The renderer uses this to punch each numeral: large and sharp on
     * arrival, settling as the beat plays out.
     */
    public double getBeatProgress() {
        int duration = switch (phase) {
            case LOADING -> LOADING_TICKS;
            case COUNTDOWN -> COUNT_TICKS;
            case GO -> GO_TICKS;
            case DONE -> 1;
        };
        return Math.min(1.0, phaseTicks / (double) duration);
    }

    /** Line shown under the loading bar. */
    public String getLoadingLabel() {
        return "Preparing the temple  ·  " + difficulty.getDisplayName();
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    /** Skips straight to play, for a player who does not want the ceremony. */
    public void skip() {
        phase = Phase.DONE;
        phaseTicks = 0;
        count = 0;
    }
}
