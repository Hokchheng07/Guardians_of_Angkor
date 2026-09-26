package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.EnemyType;

/**
 * Every way the game gets harder as the level climbs, in one place.
 */
public final class DifficultyCurve {

    public static final double LEVEL_RAMP_DAMPING = 0.7;
    private static final double SPEED_PER_LEVEL = 0.035 * LEVEL_RAMP_DAMPING;

    private DifficultyCurve() {
        // Utility class — not instantiable.
    }

    public static int enemyCount(int level) {
        return Math.min(4 + (Math.max(1, level) - 1) * 2, 40);
    }

    public static int enemyCount(int level, Difficulty difficulty) {
        double scaled = enemyCount(level) * getDynamicEnemyCountScale(level, difficulty);
        return Math.max(2, (int) Math.round(scaled));
    }

    public static int spawnIntervalTicks(int level) {
        int base = 130 - (Math.max(1, level) * 7);
        return Math.max(base, 26);
    }

    public static int spawnIntervalTicks(int level, Difficulty difficulty) {
        double scaled = spawnIntervalTicks(level) * getDynamicSpawnScale(level, difficulty);
        return Math.max(20, (int) Math.round(scaled));
    }

    public static double baseSpeed(int level) {
        // GLOBAL NERF: Dropped the baseline from 0.40 to a crawled 0.20
        return Math.min(0.20 + (Math.max(1, level) - 1) * SPEED_PER_LEVEL, 0.75);
    }

    public static double baseSpeed(int level, Difficulty difficulty) {
        // LOCK THE SPEED CURVE: Treat every wave as Wave 1 for speed calculation, unless Endless!
        boolean isEndless = difficulty != null && difficulty.name().equals("ENDLESS");
        int speedLevel = isEndless ? level : 1;

        return baseSpeed(speedLevel) * getDynamicSpeedScale(level, difficulty);
    }

    public static double speedMultiplier(EnemyType type, int level) {
        int levelsIn = Math.max(0, level - 1);
        double gain = type.getLevelSpeedGain() * 0.3; // Heavily nerfed the escalation gain
        double gained = type.getSpeedMultiplier() + (levelsIn * gain);

        // TARGETED NERF: Cut Ahp and Beisach's base speed completely in half!
        if (type.name().equals("AHP") || type.name().equals("BEISACH")) {
            gained *= 0.5;
        }

        return Math.min(gained, type.getMaxSpeedMultiplier());
    }

    public static double speedFor(EnemyType type, int level, Difficulty difficulty) {
        // LOCK THE SPEED MULTIPLIER: Treat every wave as Wave 1, unless Endless!
        boolean isEndless = difficulty != null && difficulty.name().equals("ENDLESS");
        int speedLevel = isEndless ? level : 1;

        return baseSpeed(level, difficulty) * speedMultiplier(type, speedLevel);
    }

    public static int throwIntervalTicks(EnemyType type, int level) {
        int base = type.getThrowIntervalTicks();
        if (base <= 0) {
            return 0;
        }
        int scaled = base - (Math.max(0, level - 1) * 12);
        return Math.max(scaled, Math.max(90, base / 3));
    }

    public static int projectileFlightTicks(int level) {
        return Math.max(150 - (Math.max(1, level) - 1) * 8, 70);
    }

    public static int projectileFlightTicks(int level, Difficulty difficulty) {
        double scaled = projectileFlightTicks(level) * getDynamicSpawnScale(level, difficulty);
        return Math.max(60, (int) Math.round(scaled));
    }

    public static double scoreMultiplier(int level) {
        return 1.0 + (Math.max(1, level) - 1) * 0.12;
    }

    // --- DYNAMIC ENDLESS SCALING HELPERS ---

    private static double getDynamicSpeedScale(int level, Difficulty difficulty) {
        if (difficulty != null && difficulty.name().equals("ENDLESS")) {
            // Starts at 0.65 (Easy) and gets 1% faster every level, forever.
            return Math.min(1.5, 0.65 + (level * 0.01));
        }
        return difficulty == null ? 1.0 : difficulty.getSpeedScale();
    }

    private static double getDynamicSpawnScale(int level, Difficulty difficulty) {
        if (difficulty != null && difficulty.name().equals("ENDLESS")) {
            // Starts at 1.50 (Easy) and drops spacing by 1.5% every level.
            return Math.max(0.6, 1.50 - (level * 0.015));
        }
        return difficulty == null ? 1.0 : difficulty.getSpawnIntervalScale();
    }

    private static double getDynamicEnemyCountScale(int level, Difficulty difficulty) {
        if (difficulty != null && difficulty.name().equals("ENDLESS")) {
            // Starts at 0.65 (Easy size) and grows by 1% every level.
            return Math.min(2.0, 0.65 + (level * 0.01));
        }
        return difficulty == null ? 1.0 : difficulty.getEnemyCountScale();
    }
}