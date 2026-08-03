package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.EnemyType;

import java.util.Locale;

/**
 * Difficulty presets offered after New Game.
 *
 * <p>{@link #HARD} is the reference tuning — every curve in
 * {@link DifficultyCurve} is written at Hard's numbers, and its scales are all
 * 1.0. The other tiers are expressed as deviations from it, so there is exactly
 * one place the baseline lives and the tiers cannot silently disagree about what
 * "normal" means.
 *
 * <p>The reference used to be Medium, and moved when the tiers were rebalanced:
 * what shipped as Medium was reported as the real difficulty ceiling, so it was
 * promoted to Hard unchanged and a genuinely intermediate Medium was built
 * between it and Easy. Moving the reference with it means the rebalance did not
 * require rewriting every curve — the numbers that were already tuned and
 * playtested stayed exactly where they were, and only the label moved.
 *
 * <p>Tiers no longer share a length. Easy runs ten waves, Medium fifteen, Hard
 * twenty: a player climbing the ladder is meant to be signing up for more, not
 * only for faster. {@link #ENDLESS} is the exception by definition — its wave
 * count is structural only and it is not playable yet.
 *
 * <p>Tiers are also gated. Medium cannot be started until Easy has been
 * cleared and Hard until Medium has — see {@link #requiredPredecessor()} and
 * {@link DifficultyProgress}. Difficulty selection is meant to be a decision
 * about what the player has earned, not a slider they can drag to a wall on
 * their first run.
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
     * bands hold back the long vocabulary, because a beginner's problem is
     * usually finding the letters rather than the clock. Heavier types also
     * unlock later here — see {@link WaveWeights}.
     */
    EASY("Easy", "A steady tide. Shorter names, more time.", true,
            0.58, 1.50, -1, -2,
            EnemyType.NAGA, 10,
            0.22, 1.25, 0.65,
            2, 2, 2),

    /**
     * The middle rung, built to sit halfway between Easy and Hard.
     *
     * <p>Every scale here is the midpoint of the two tiers either side of it
     * rather than a number picked by feel. That is the whole point of the tier:
     * the old jump from Easy straight to what is now Hard was the reported
     * problem, and a middle rung that drifted toward either end would put the
     * cliff back, just somewhere else.
     */
    MEDIUM("Medium", "The tide turns. Longer names, less room.", true,
            0.79, 1.25, 0, -1,
            EnemyType.KRONG_REAP, 15,
            0.21, 1.12, 0.82,
            3, 3, 3),

    /**
     * The reference tuning, and the longest run. Everything DifficultyCurve is
     * written against.
     *
     * <p>These are the numbers that shipped as Medium, unchanged. What did
     * change is the shared escalation curve they feed into: see
     * {@link DifficultyCurve#LEVEL_RAMP_DAMPING}. Late waves were outrunning
     * even a fast typist's reaction time, which is a different failure from
     * being hard.
     */
    HARD("Hard", "No tide at all. The temple gets no rest.", true,
            1.0, 1.0, 0, 0,
            EnemyType.KRONG_REAP, 20,
            0.20, 1.0, 1.0,
            3, 3, 3),

    /**
     * No final boss; escalates until the temple falls.
     *
     * <p>Structure only. The tuning below is real so nothing has to special-case
     * a half-configured tier, but {@code implemented} is false and no run can
     * start on it — the mode itself is not built.
     */
    ENDLESS("Endless", "No last level. It ends when you do.", false,
            1.1, 0.9, 0, 0,
            null, Integer.MAX_VALUE,
            0.16, 0.9, 1.05,
            3, 3, 3);

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
    private final int bossParagraphsPerCycle;
    private final int bossSentencesPerParagraph;
    private final int bossCycles;

    Difficulty(String displayName, String tagline, boolean implemented,
               double speedScale, double spawnIntervalScale,
               int wordMinShift, int wordMaxShift,
               EnemyType finalBossType, int finalBossLevel,
               double powerUpDropChance, double powerUpDurationScale,
               double enemyCountScale,
               int bossParagraphsPerCycle, int bossSentencesPerParagraph,
               int bossCycles) {
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
        this.bossParagraphsPerCycle = bossParagraphsPerCycle;
        this.bossSentencesPerParagraph = bossSentencesPerParagraph;
        this.bossCycles = bossCycles;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** One-line description shown under the difficulty list. */
    public String getTagline() {
        return tagline;
    }

    /**
     * False for tiers that are visible in the menu but cannot be started yet.
     *
     * <p>Distinct from being locked. An unimplemented tier is one nobody can
     * play; a locked one is built and waiting to be earned. They read the same
     * on the button and mean entirely different things, which is why
     * {@link MenuState} explains them differently.
     */
    public boolean isImplemented() {
        return implemented;
    }

    /**
     * The tier that must be cleared before this one can be started, or null
     * when it is open from the beginning.
     *
     * <p>A method rather than a constructor argument because an enum constant
     * cannot refer to one declared after it, and the ladder runs forwards.
     */
    public Difficulty requiredPredecessor() {
        return switch (this) {
            case EASY -> null;
            case MEDIUM -> EASY;
            case HARD -> MEDIUM;
            case ENDLESS -> HARD;
        };
    }

    /** Multiplier on enemy speed. 1.0 at Hard. */
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

    /** How many ordinary waves a run on this tier sends. */
    public int getWaveCount() {
        return finalBossLevel;
    }

    // ---- the finale's length -----------------------------------------------

    /**
     * Paragraphs the boss demands before one cycle of its fight is done.
     *
     * <p>The finale's health is measured in text rather than in hits, so these
     * three numbers are the boss's HP bar. Easy asks for two paragraphs of two
     * sentences, twice over; Medium and Hard ask for three of three, three times
     * over. The old finale was a single paragraph and was reported as falling
     * far too quickly to read as a climax.
     */
    public int getBossParagraphsPerCycle() {
        return bossParagraphsPerCycle;
    }

    /**
     * Sentences in each of the boss's paragraphs.
     *
     * <p>Also what decides how often the boss changes attack: a phase ends when
     * a paragraph is finished, so this is the length of a phase measured in
     * typing rather than in seconds.
     */
    public int getBossSentencesPerParagraph() {
        return bossSentencesPerParagraph;
    }

    /** How many times the boss repeats its paragraph block before it falls. */
    public int getBossCycles() {
        return bossCycles;
    }

    /** Paragraphs the whole fight asks for, across every cycle. */
    public int getBossParagraphCount() {
        return bossParagraphsPerCycle * bossCycles;
    }

    /** Sentences the whole fight asks for. The boss's total health. */
    public int getBossSentenceCount() {
        return getBossParagraphCount() * bossSentencesPerParagraph;
    }

    // ---- generosity --------------------------------------------------------

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
        return HARD;
    }
}
