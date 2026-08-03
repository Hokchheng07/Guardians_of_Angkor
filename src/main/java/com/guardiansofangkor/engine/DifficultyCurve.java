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
 *
 * <p>Every number here is written at {@link Difficulty#HARD}, the reference
 * tuning. The single-argument overloads therefore describe Hard exactly; the
 * {@link Difficulty} overloads apply that tier's deviation on top.
 */
public final class DifficultyCurve {

    /**
     * How much of the original per-level escalation is kept.
     *
     * <p>The speed curves used to climb 30% faster than this. Playtesting on
     * what is now Hard reported level eleven as unreactable at 102 words per
     * minute — not hard, but past the point where reading a word and answering
     * it is physically possible, which is a different failure and not one more
     * practice fixes.
     *
     * <p>Applied to the <em>slope</em> rather than to the speed itself, so
     * level one is untouched and only the far end of a run is pulled back in.
     * Flattening the whole curve instead would have made the opening waves
     * sluggish to fix a problem that only exists at the end.
     *
     * <p>It also has to stretch further than it used to: Hard now runs twenty
     * waves rather than fifteen, so the same slope would have reached somewhere
     * even worse than the level that prompted this.
     */
    public static final double LEVEL_RAMP_DAMPING = 0.7;

    /** Speed added per level at the reference tuning, after damping. */
    private static final double SPEED_PER_LEVEL = 0.035 * LEVEL_RAMP_DAMPING;

    private DifficultyCurve() {
        // Utility class — not instantiable.
    }

    /** Enemies in a level: starts at 4, climbs, capped so the screen stays readable. */
    public static int enemyCount(int level) {
        return Math.min(4 + (Math.max(1, level) - 1) * 2, 20);
    }

    /**
     * Enemies in a level on a given tier.
     *
     * <p>The most direct lever there is on how hard a level feels, and the one
     * that was missing: slowing Easy down and shortening its words still left a
     * beginner facing twenty monsters on level fifteen, which no amount of extra
     * time per monster makes reasonable. Floored at two so no level is empty.
     */
    public static int enemyCount(int level, Difficulty difficulty) {
        double scaled = enemyCount(level) * scaleOf(difficulty).getEnemyCountScale();
        return Math.max(2, (int) Math.round(scaled));
    }

    /** Ticks between spawns at the reference tuning. */
    public static int spawnIntervalTicks(int level) {
        int base = 130 - (Math.max(1, level) * 7);
        return Math.max(base, 26);
    }

    /** Ticks between spawns, widened or tightened by the tier. */
    public static int spawnIntervalTicks(int level, Difficulty difficulty) {
        double scaled = spawnIntervalTicks(level) * scaleOf(difficulty).getSpawnIntervalScale();
        // Floor applies after scaling too, or a fast tier could reach zero.
        return Math.max(20, (int) Math.round(scaled));
    }

    /**
     * Base march speed in pixels per tick at the reference tuning, before the
     * per-type multiplier.
     */
    public static double baseSpeed(int level) {
        return Math.min(0.40 + (Math.max(1, level) - 1) * SPEED_PER_LEVEL, 1.15);
    }

    /** Base march speed for a tier. */
    public static double baseSpeed(int level, Difficulty difficulty) {
        return baseSpeed(level) * scaleOf(difficulty).getSpeedScale();
    }

    /**
     * Per-type speed multiplier at a given level.
     *
     * <p>This is where "weaker enemies get faster sooner" lives. Light types
     * carry a high {@link EnemyType#getLevelSpeedGain()} so the swarm becomes
     * genuinely frantic in later levels, while heavies stay ponderous — their
     * threat is word length, not pace. Without this split, scaling everything
     * equally makes late levels a wall of fast heavies that is simply unfair.
     *
     * <p>Independent of difficulty on purpose: the tier scales the base speed,
     * so the <em>relationship</em> between types stays intact at every tier.
     *
     * <p>The per-level gain is damped by {@link #LEVEL_RAMP_DAMPING} for the
     * same reason the base speed is. The two compound — a late-game Ahp is
     * both on a faster base and carrying a bigger multiplier — so damping only
     * one of them would leave the swarm types, which are exactly the ones that
     * outran the player, barely slower than before.
     */
    public static double speedMultiplier(EnemyType type, int level) {
        int levelsIn = Math.max(0, level - 1);
        double gain = type.getLevelSpeedGain() * LEVEL_RAMP_DAMPING;
        double gained = type.getSpeedMultiplier() + (levelsIn * gain);
        return Math.min(gained, type.getMaxSpeedMultiplier());
    }

    /** Actual pixels per tick for a type at a level, at the reference tuning. */
    public static double speedFor(EnemyType type, int level) {
        return baseSpeed(level) * speedMultiplier(type, level);
    }

    /** Actual pixels per tick for a type at a level on a given tier. */
    public static double speedFor(EnemyType type, int level, Difficulty difficulty) {
        return baseSpeed(level, difficulty) * speedMultiplier(type, level);
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

    /**
     * Ticks to clear a projectile on a given tier.
     *
     * <p>Scaled by the spawn interval rather than by speed, because both express
     * "how much time the player is given".
     */
    public static int projectileFlightTicks(int level, Difficulty difficulty) {
        double scaled = projectileFlightTicks(level)
                * scaleOf(difficulty).getSpawnIntervalScale();
        return Math.max(60, (int) Math.round(scaled));
    }

    /** Levels are worth more the deeper you get, so late play rewards fairly. */
    public static double scoreMultiplier(int level) {
        return 1.0 + (Math.max(1, level) - 1) * 0.12;
    }

    private static Difficulty scaleOf(Difficulty difficulty) {
        return difficulty == null ? Difficulty.reference() : difficulty;
    }
}
