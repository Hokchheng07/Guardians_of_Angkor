package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.EnemyType;

import java.util.Locale;

/**
 * Difficulty presets offered after New Game.
 */
public enum Difficulty {

    EASY("Easy", "A steady tide. Shorter names, more time.", true,
            0.60, 1.50, -2, -3, // SPEED LOCKED: 0.60. WORDS: Massively shorter.
            new EnemyType[]{ EnemyType.NAGA }, 10,
            0.22, 1.25, 0.65, 4,
            2, 2, 2),

    MEDIUM("Medium", "The tide turns. Longer names, less room.", true,
            0.60, 1.25, 0, 0, // SPEED LOCKED: 0.60. WORDS: Standard baseline length.
            new EnemyType[]{ EnemyType.YAKSHA_COMMANDER, EnemyType.REAM_EYSO }, 15,
            0.21, 1.12, 0.82, 6,
            3, 3, 3),

    HARD("Hard", "No tide at all. The temple gets no rest.", true,
            0.60, 1.0, 1, 2, // SPEED LOCKED: 0.60. WORDS: +1 to +2 characters longer!
            new EnemyType[]{ EnemyType.YAKSHA_COMMANDER, EnemyType.KRONG_REAP }, 20,
            0.20, 1.0, 1.0, 8,
            3, 3, 3),

    ENDLESS("Endless", "No last level. It ends when you do.", true,
            1.1, 0.9, 0, 0, // Endless remains dynamic via DifficultyCurve
            new EnemyType[]{}, Integer.MAX_VALUE,
            0.16, 0.9, 1.05, 8,
            3, 3, 3);

    private final String displayName;
    private final String tagline;
    private final boolean implemented;
    private final double speedScale;
    private final double spawnIntervalScale;
    private final int wordMinShift;
    private final int wordMaxShift;
    private final EnemyType[] gauntletBosses;
    private final int finalBossLevel;
    private final double powerUpDropChance;
    private final double powerUpDurationScale;
    private final double enemyCountScale;
    private final int maxConcurrentEnemies;
    private final int bossParagraphsPerCycle;
    private final int bossSentencesPerParagraph;
    private final int bossCycles;

    Difficulty(String displayName, String tagline, boolean implemented,
               double speedScale, double spawnIntervalScale,
               int wordMinShift, int wordMaxShift,
               EnemyType[] gauntletBosses, int finalBossLevel,
               double powerUpDropChance, double powerUpDurationScale,
               double enemyCountScale, int maxConcurrentEnemies,
               int bossParagraphsPerCycle, int bossSentencesPerParagraph,
               int bossCycles) {
        this.displayName = displayName;
        this.tagline = tagline;
        this.implemented = implemented;
        this.speedScale = speedScale;
        this.spawnIntervalScale = spawnIntervalScale;
        this.wordMinShift = wordMinShift;
        this.wordMaxShift = wordMaxShift;
        this.gauntletBosses = gauntletBosses;
        this.finalBossLevel = finalBossLevel;
        this.powerUpDropChance = powerUpDropChance;
        this.powerUpDurationScale = powerUpDurationScale;
        this.enemyCountScale = enemyCountScale;
        this.maxConcurrentEnemies = maxConcurrentEnemies;
        this.bossParagraphsPerCycle = bossParagraphsPerCycle;
        this.bossSentencesPerParagraph = bossSentencesPerParagraph;
        this.bossCycles = bossCycles;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTagline() {
        return tagline;
    }

    public boolean isImplemented() {
        return implemented;
    }

    public Difficulty requiredPredecessor() {
        return switch (this) {
            case EASY -> null;
            case MEDIUM -> EASY;
            case HARD -> MEDIUM;
            case ENDLESS -> HARD;
        };
    }

    public int getMaxConcurrentEnemies() {
        return maxConcurrentEnemies;
    }

    public double getSpeedScale() {
        return speedScale;
    }

    public double getSpawnIntervalScale() {
        return spawnIntervalScale;
    }

    public int getWordMinShift() {
        return wordMinShift;
    }

    public int getWordMaxShift() {
        return wordMaxShift;
    }

    public String getWordBankKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public EnemyType getFinalBossType() {
        if (gauntletBosses == null || gauntletBosses.length == 0) return null;
        return gauntletBosses[gauntletBosses.length - 1];
    }

    /** Grabs the specific boss for wave 10, 20, 30, etc. */
    public EnemyType getMilestoneBoss(int level) {
        // ENDLESS MODE: Cycle through all 5 bosses infinitely!
        if (this.name().equals("ENDLESS")) {
            EnemyType[] allBosses = {
                    EnemyType.YAKSHA_COMMANDER, EnemyType.NAGA,
                    EnemyType.CORRUPTED_APSARA, EnemyType.REAM_EYSO, EnemyType.KRONG_REAP
            };
            int index = ((level / 10) - 1) % allBosses.length;
            if (index >= 0) return allBosses[index];
        }

        // Standard Campaign logic
        if (gauntletBosses == null || gauntletBosses.length == 0) return null;
        // The last level always closes on the tier's final boss, even when it
        // is not a multiple of ten (Medium ends on 15).
        if (level == finalBossLevel) return getFinalBossType();
        int index = (level / 10) - 1;
        if (index >= 0 && index < gauntletBosses.length) {
            return gauntletBosses[index];
        }
        return null;
    }

    /**
     * True when {@code level} ends in a boss fight: every tenth level, plus the
     * tier's final level wherever it falls.
     */
    public boolean isBossLevel(int level) {
        if (level <= 0) return false;
        return level % 10 == 0 || (hasFinalBoss() && level == finalBossLevel);
    }

    public int getFinalBossLevel() {
        return finalBossLevel;
    }

    public int getFinalLevel() {
        return finalBossLevel;
    }

    public int getWaveCount() {
        return finalBossLevel;
    }

    public int getBossParagraphsPerCycle() {
        return bossParagraphsPerCycle;
    }

    public int getBossSentencesPerParagraph() {
        return bossSentencesPerParagraph;
    }

    public int getBossCycles() {
        return bossCycles;
    }

    public int getBossParagraphCount() {
        return bossParagraphsPerCycle * bossCycles;
    }

    /**
     * The level from which a boss fight is the tier's full length.
     *
     * <p>Medium, Hard and Endless ask for nine paragraphs of three sentences —
     * about 230 words — which was the right size for a finale and far too much
     * for the level-10 Yaksha that opens every gauntlet. Bosses before this
     * level fight at {@link #EASY}'s size instead (about 60 words), so the long
     * fights only arrive once the player has come far enough to expect one.
     */
    public static final int FULL_LENGTH_BOSS_LEVEL = 30;

    /** How many paragraphs the boss on {@code level} makes the player type. */
    public int bossParagraphCountAt(int level) {
        int full = getBossParagraphCount();
        return isFullLengthBoss(level) ? full : Math.min(full, EASY.getBossParagraphCount());
    }

    /** How many sentences per paragraph the boss on {@code level} uses. */
    public int bossSentencesPerParagraphAt(int level) {
        int full = bossSentencesPerParagraph;
        return isFullLengthBoss(level)
                ? full : Math.min(full, EASY.getBossSentencesPerParagraph());
    }

    /** Late gauntlet bosses and every tier's finale fight at full length. */
    private boolean isFullLengthBoss(int level) {
        return level >= FULL_LENGTH_BOSS_LEVEL || (hasFinalBoss() && level == finalBossLevel);
    }

    public int getBossSentenceCount() {
        return getBossParagraphCount() * bossSentencesPerParagraph;
    }

    public double getPowerUpDropChance() {
        return powerUpDropChance;
    }

    public double getPowerUpDurationScale() {
        return powerUpDurationScale;
    }

    public double getEnemyCountScale() {
        return enemyCountScale;
    }

    public boolean hasFinalBoss() {
        return getFinalBossType() != null && finalBossLevel != Integer.MAX_VALUE;
    }

    public boolean isWinnable() {
        return hasFinalBoss();
    }

    public static Difficulty defaultChoice() {
        return EASY;
    }

    public static Difficulty reference() {
        return HARD;
    }
}