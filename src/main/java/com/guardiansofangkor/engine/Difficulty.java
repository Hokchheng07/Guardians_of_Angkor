package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.EnemyType;

import java.util.Locale;

/**
 * Difficulty presets offered after New Game.
 *
 * <p>{@link #MEDIUM} is the reference tuning — every curve in
 * {@link DifficultyCurve} is written at Medium's numbers, and its scales are all
 * 1.0. The other tiers are expressed as deviations from it, so there is exactly
 * one place the baseline lives and the tiers cannot silently disagree about what
 * "normal" means.
 *
 * <p>Easy, Medium and Hard all finish on level 15. Sharing one finale length is
 * deliberate: a tier is meant to change how hard the same run is, not how long
 * it is, and a player moving up from Easy should recognise the shape of what
 * they are attempting. {@link #ENDLESS} is the exception by definition.
 *
 * <p>Word <em>vocabulary</em> is not tuned here. Which words a tier may use at a
 * given level lives in the word bank's own JSON, keyed by {@link
 * #getWordBankKey()} — see {@code words_en.json}. This enum only carries the
 * length nudge applied on top of whatever that band offers, so retuning
 * vocabulary never means recompiling.
 */
public enum Difficulty {

    /**
     * Gentler pace and shorter words, with the Naga as the final boss.
     *
     * <p>Deliberately does not just slow everything down: the word bank's Easy
     * bands hold back the long vocabulary for the first ten levels, because a
     * beginner's problem is usually finding the letters rather than the clock.
     * Heavier types also unlock later here — see {@link WaveWeights}.
     */
    EASY("Easy", "A steady tide. Shorter names, more time.", true,
            0.58, 1.50, -1, -2,
            EnemyType.NAGA, 15,
            0.22, 1.25, 0.65),

    /** The reference tuning. Everything DifficultyCurve is written against. */
    MEDIUM("Medium", "The temple as it was meant to be defended.", true,
            1.0, 1.0, 0, 0,
            EnemyType.KRONG_REAP, 15,
            0.20, 1.0, 1.0),

    /** Longer words and heavier pressure. Not yet playable. */
    HARD("Hard", "Longer names. The temple gets no rest.", false,
            1.28, 0.82, 1, 1,
            EnemyType.KRONG_REAP, 15,
            0.12, 0.8, 1.15),

    /** No final boss; escalates until the temple falls. Not yet playable. */
    ENDLESS("Endless", "No last level. It ends when you do.", false,
            1.1, 0.9, 0, 0,
            null, Integer.MAX_VALUE,
            0.16, 0.9, 1.05);

    private final String displayName;
    private final String tagline;
    private final boolean implemented;
    private final double speedScale;
    private final double spawnIntervalScale;
    private final int wordMinShift;
    private final int wordMaxShift;
    private final EnemyType finalBossType;
    private final int finalBossLevel;
    private final double powerUpDropChance;
    private final double powerUpDurationScale;
    private final double enemyCountScale;

    Difficulty(String displayName, String tagline, boolean implemented,
               double speedScale, double spawnIntervalScale,
               int wordMinShift, int wordMaxShift,
               EnemyType finalBossType, int finalBossLevel,
               double powerUpDropChance, double powerUpDurationScale,
               double enemyCountScale) {
        this.displayName = displayName;
        this.tagline = tagline;
        this.implemented = implemented;
        this.speedScale = speedScale;
        this.spawnIntervalScale = spawnIntervalScale;
        this.wordMinShift = wordMinShift;
        this.wordMaxShift = wordMaxShift;
        this.finalBossType = finalBossType;
        this.finalBossLevel = finalBossLevel;
        this.powerUpDropChance = powerUpDropChance;
        this.powerUpDurationScale = powerUpDurationScale;
        this.enemyCountScale = enemyCountScale;
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

    /**
     * The key this tier is filed under in the word bank JSON.
     *
     * <p>A plain lower-case string rather than the enum itself, so the word bank
     * does not have to know the engine exists. {@code i18n} is already imported
     * by {@code engine}; making that dependency mutual to pass one identifier
     * would be a package cycle for no benefit.
     */
    public String getWordBankKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Which monster ends the run, or null when the tier never ends. */
    public EnemyType getFinalBossType() {
        return finalBossType;
    }

    /** Level the final boss appears on. {@code Integer.MAX_VALUE} means never. */
    public int getFinalBossLevel() {
        return finalBossLevel;
    }

    /**
     * The last level of a run on this tier — clearing it wins the game.
     *
     * <p>Identical to {@link #getFinalBossLevel()} today, and expressed
     * separately anyway because they answer different questions: one is "when
     * does the boss arrive", the other is "when does the game stop". A tier that
     * ever wanted a victory lap after the boss would change one and not the
     * other.
     */
    public int getFinalLevel() {
        return finalBossLevel;
    }

    /**
     * How many sentences the finale asks for.
     *
     * <p>Not a number kept here: it is however many sentences the tier's
     * paragraph has in {@code words_en.json}. Keeping a second copy in Java
     * would let the two disagree, and the paragraph is the one that is right.
     */
    /**
     * Chance that defeating an ordinary enemy leaves a power-up behind.
     *
     * <p>Steepest on Easy on purpose. Power-ups are the difficulty valve that
     * does not require retuning any curve: a struggling player sees more of
     * them, and the tier's identity survives intact.
     *
     * <p>This is the chance per <em>eligible</em> kill, and only Yeak, Pret and
     * Naga are eligible — roughly a third of what the player fights. So Easy's
     * 0.22 is nearer one boon in thirteen kills overall, which is where a drop
     * still reads as a find rather than as loot. It also leaves the mercy curve
     * in {@link PowerUpDrops} real room to climb toward the 0.30 ceiling instead
     * of already sitting on it — a base equal to the cap would mean a
     * struggling player gets no more generous at all.
     */
    public double getPowerUpDropChance() {
        return powerUpDropChance;
    }

    /** Multiplier on how long a timed power-up lasts. Longer on gentler tiers. */
    public double getPowerUpDurationScale() {
        return powerUpDurationScale;
    }

    /**
     * Multiplier on how many enemies a level sends.
     *
     * <p>The bluntest lever on difficulty, and worth having separately from
     * speed: a gentler tier that only slows things down still ends up asking a
     * beginner to hold twenty words in their head at once on the last level,
     * which no amount of extra time per word makes reasonable.
     */
    public double getEnemyCountScale() {
        return enemyCountScale;
    }

    /** True when this tier has a final boss at all. */
    public boolean hasFinalBoss() {
        return finalBossType != null && finalBossLevel != Integer.MAX_VALUE;
    }

    /** True when a run on this tier can be finished rather than only survived. */
    public boolean isWinnable() {
        return hasFinalBoss();
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
