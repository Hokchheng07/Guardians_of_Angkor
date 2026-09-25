package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.EnemyType;

import java.util.Locale;

/**
 * Difficulty presets offered after New Game.
 */
public enum Difficulty {

    EASY("Easy", "A steady tide. Shorter names, more time.", true,
            0.60, 1.50, -2, -3, // SPEED LOCKED: 0.60. WORDS: Massively shorter.
            new EnemyType[]{ EnemyType.YAKSHA_COMMANDER, EnemyType.NAGA }, 20,
            0.22, 1.25, 0.65, 4,
            2, 2, 2),

    MEDIUM("Medium", "The tide turns. Longer names, less room.", true,
            0.60, 1.25, 0, 0, // SPEED LOCKED: 0.60. WORDS: Standard baseline length.
            new EnemyType[]{ EnemyType.YAKSHA_COMMANDER, EnemyType.NAGA, EnemyType.CORRUPTED_APSARA, EnemyType.REAM_EYSO }, 40,
            0.21, 1.12, 0.82, 6,
            3, 3, 3),

    HARD("Hard", "No tide at all. The temple gets no rest.", true,
            0.60, 1.0, 1, 2, // SPEED LOCKED: 0.60. WORDS: +1 to +2 characters longer!
            new EnemyType[]{ EnemyType.YAKSHA_COMMANDER, EnemyType.NAGA, EnemyType.CORRUPTED_APSARA, EnemyType.REAM_EYSO, EnemyType.KRONG_REAP }, 50,
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
        int index = (level / 10) - 1;
        if (index >= 0 && index < gauntletBosses.length) {
            return gauntletBosses[index];
        }
        return null;
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