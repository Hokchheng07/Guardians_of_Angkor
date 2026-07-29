package com.guardiansofangkor.engine;

/**
 * Difficulty presets offered after New Game.
 *
 * <p>Only {@link #EASY} is playable right now. The rest are deliberately listed
 * and visibly locked rather than hidden: the player should be able to see the
 * shape of what is coming, and the team should not have to add menu entries
 * later — implementing one is a matter of flipping {@link #implemented} and
 * supplying its multipliers.
 *
 * <p>The multipliers below are placeholders for the locked tiers and are not
 * consulted while {@code implemented} is false. They are recorded now so the
 * intended balance is written down rather than lost.
 */
public enum Difficulty {

    /** The default. Everything currently tuned in DifficultyCurve. */
    EASY("Easy", "A steady tide. Room to learn the words.", true, 1.0, 1.0),

    /** Faster spirits and tighter spawns. */
    MEDIUM("Medium", "Quicker spirits, less room to think.", false, 1.25, 0.85),

    /** Long words and heavy pressure. */
    HARD("Hard", "Longer names. The temple gets no rest.", false, 1.5, 0.7),

    /** No final boss; escalates until the temple falls. */
    ENDLESS("Endless", "No last level. It ends when you do.", false, 1.15, 0.8);

    private final String displayName;
    private final String tagline;
    private final boolean implemented;
    private final double speedScale;
    private final double spawnIntervalScale;

    Difficulty(String displayName, String tagline, boolean implemented,
               double speedScale, double spawnIntervalScale) {
        this.displayName = displayName;
        this.tagline = tagline;
        this.implemented = implemented;
        this.speedScale = speedScale;
        this.spawnIntervalScale = spawnIntervalScale;
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

    /** Multiplier on enemy speed. Only meaningful once implemented. */
    public double getSpeedScale() {
        return speedScale;
    }

    /** Multiplier on the gap between spawns. Below 1 means they come faster. */
    public double getSpawnIntervalScale() {
        return spawnIntervalScale;
    }

    /** The tier a new run starts on unless the player picks another. */
    public static Difficulty defaultChoice() {
        return EASY;
    }
}
