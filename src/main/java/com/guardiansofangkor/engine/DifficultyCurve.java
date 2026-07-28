package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.EnemyType;

/**
 * Every way the game gets harder as the level climbs, in one place.
 *
 * <p>Keeping the curves here rather than scattered through WaveManager and
 * Enemy means balance can be tuned by reading one file, and each curve can be
 * unit-tested in isolation.
 *
 * <p>All curves are monotonic and capped. An uncapped curve eventually produces
 * an unplayable level, which is worse than a plateau.
 */
public final class DifficultyCurve {

    private DifficultyCurve() {
        // Utility class — not instantiable.
    }

    /** Enemies in a level: starts at 4, climbs, capped so the screen stays readable. */
    public static int enemyCount(int level) {
        return Math.min(4 + (Math.max(1, level) - 1) * 2, 20);
    }

    /** Ticks between spawns: tightens with level, floored so it stays fair. */
    public static int spawnIntervalTicks(int level) {
        int base = 130 - (Math.max(1, level) * 7);
        return Math.max(base, 26);
    }

    /**
     * Base march speed in pixels per tick, before the per-type multiplier.
     * This is the floor that every enemy inherits.
     */
    public static double baseSpeed(int level) {
        return Math.min(0.40 + (Math.max(1, level) - 1) * 0.035, 1.15);
    }

    /**
     * Per-type speed multiplier at a given level.
     *
     * <p>This is where "weaker enemies get faster sooner" lives. Light types
     * carry a high {@link EnemyType#getLevelSpeedGain()} so the swarm becomes
     * genuinely frantic in later levels, while heavies stay ponderous — their
     * threat is word length, not pace. Without this split, scaling everything
     * equally makes late levels a wall of fast heavies that is simply unfair.
     */
    public static double speedMultiplier(EnemyType type, int level) {
        int levelsIn = Math.max(0, level - 1);
        double gained = type.getSpeedMultiplier() + (levelsIn * type.getLevelSpeedGain());
        return Math.min(gained, type.getMaxSpeedMultiplier());
    }

    /** Actual pixels per tick for a type at a level. */
    public static double speedFor(EnemyType type, int level) {
        return baseSpeed(level) * speedMultiplier(type, level);
    }

    /**
     * How often a ranged enemy throws, in ticks. Falls with level so attacks
     * come thicker, floored so the player can still clear them.
     */
    public static int throwIntervalTicks(EnemyType type, int level) {
        int base = type.getThrowIntervalTicks();
        if (base <= 0) {
            return 0;
        }
        int scaled = base - (Math.max(0, level - 1) * 12);
        return Math.max(scaled, Math.max(90, base / 3));
    }

    /**
     * Ticks the player has to clear a projectile before it lands. Shrinks with
     * level, which is the main source of late-game pressure.
     */
    public static int projectileFlightTicks(int level) {
        return Math.max(150 - (Math.max(1, level) - 1) * 8, 70);
    }

    /** Levels are worth more the deeper you get, so late play rewards fairly. */
    public static double scoreMultiplier(int level) {
        return 1.0 + (Math.max(1, level) - 1) * 0.12;
    }
}
