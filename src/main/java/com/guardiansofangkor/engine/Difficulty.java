package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.EnemyType;

/**
 * Difficulty presets offered after New Game.
 *
 * <p>{@link #MEDIUM} is the reference tuning — every curve in
 * {@link DifficultyCurve} is written at Medium's numbers, and its scales are all
 * 1.0. The other tiers are expressed as deviations from it, so there is exactly
 * one place the baseline lives and the tiers cannot silently disagree about what
 * "normal" means.
 *
 * <p>{@link #HARD} and {@link #ENDLESS} are listed but not playable yet. Their
 * multipliers are recorded so the intended balance is written down rather than
 * lost, and enabling one is a matter of flipping {@code implemented}.
 */
public enum Difficulty {

    /**
     * Gentler pace and shorter words, with the Naga as the final boss.
     *
     * <p>Deliberately does not just slow everything down: the words are shorter
     * too, because a beginner's problem is usually finding the letters rather
     * than the clock. The Naga arrives at level 10 instead of Krong Reap at 15,
     * so an Easy run has a reachable ending.
     */
    EASY("Easy", "A steady tide. Shorter names, more time.", true,
            0.72, 1.15, -1, -1,
            EnemyType.NAGA, 10, 3, 2),

    /** The reference tuning. Everything DifficultyCurve is written against. */
    MEDIUM("Medium", "The temple as it was meant to be defended.", true,
            1.0, 1.0, 0, 0,
            EnemyType.KRONG_REAP, 15, 1, 0),

    /** Longer words and heavier pressure. Not yet playable. */
    HARD("Hard", "Longer names. The temple gets no rest.", false,
            1.28, 0.82, 1, 1,
            EnemyType.KRONG_REAP, 20, 2, 2),

    /** No final boss; escalates until the temple falls. Not yet playable. */
    ENDLESS("Endless", "No last level. It ends when you do.", false,
            1.1, 0.9, 0, 0,
            null, Integer.MAX_VALUE, 3, 2);

    private final String displayName;
    private final String tagline;
    private final boolean implemented;
    private final double speedScale;
    private final double spawnIntervalScale;
    private final int wordMinShift;
    private final int wordMaxShift;
    private final EnemyType finalBossType;
    private final int finalBossLevel;
    private final int finalBossChainLength;
    private final int bossWordLengthBonus;

    Difficulty(String displayName, String tagline, boolean implemented,
               double speedScale, double spawnIntervalScale,
               int wordMinShift, int wordMaxShift,
               EnemyType finalBossType, int finalBossLevel,
               int finalBossChainLength, int bossWordLengthBonus) {
        this.displayName = displayName;
        this.tagline = tagline;
        this.implemented = implemented;
        this.speedScale = speedScale;
        this.spawnIntervalScale = spawnIntervalScale;
        this.wordMinShift = wordMinShift;
        this.wordMaxShift = wordMaxShift;
        this.finalBossType = finalBossType;
        this.finalBossLevel = finalBossLevel;
        this.finalBossChainLength = finalBossChainLength;
        this.bossWordLengthBonus = bossWordLengthBonus;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** One-line description shown under the difficulty list. */
    public String getTagline() {
        return tagline;
    }

    /** False for tiers that are visible in the menu but cannot be started yet. */
    public boolean isImplemented() {
        return implemented;
    }

    /** Multiplier on enemy speed. 1.0 at Medium. */
    public double getSpeedScale() {
        return speedScale;
    }

    /**
     * Multiplier on the gap between spawns. Above 1 means more breathing room,
     * below 1 means they come faster.
     */
    public double getSpawnIntervalScale() {
        return spawnIntervalScale;
    }

    /** Adjustment to each enemy type's minimum word length. */
    public int getWordMinShift() {
        return wordMinShift;
    }

    /** Adjustment to each enemy type's maximum word length. */
    public int getWordMaxShift() {
        return wordMaxShift;
    }

    /** Which monster ends the run, or null when the tier never ends. */
    public EnemyType getFinalBossType() {
        return finalBossType;
    }

    /** Level the final boss appears on. {@code Integer.MAX_VALUE} means never. */
    public int getFinalBossLevel() {
        return finalBossLevel;
    }

    /** Words the final boss takes to kill. */
    public int getFinalBossChainLength() {
        return finalBossChainLength;
    }

    /**
     * Extra word length granted to the final boss, on top of this tier's own
     * shift.
     *
     * <p>Lets Easy keep short words generally while still making its boss feel
     * like one — the fight should be the hardest typing in the run even on the
     * gentlest tier.
     */
    public int getBossWordLengthBonus() {
        return bossWordLengthBonus;
    }

    /** True when this tier has a final boss at all. */
    public boolean hasFinalBoss() {
        return finalBossType != null && finalBossLevel != Integer.MAX_VALUE;
    }

    /** The tier a new run starts on unless the player picks another. */
    public static Difficulty defaultChoice() {
        return EASY;
    }

    /** The tier every curve is written against. */
    public static Difficulty reference() {
        return MEDIUM;
    }
}
