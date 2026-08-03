package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.EnemyType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Decides which enemy types can appear in a given wave and how likely each is.
 *
 * <p>Kept separate from {@link WaveManager} so difficulty tuning is one small
 * readable table rather than branches buried in spawn code.
 */
final class WaveWeights {

    /** Level at which each type first appears. */
    private static final Map<EnemyType, Integer> UNLOCK_WAVE = new EnumMap<>(EnemyType.class);

    /** Relative spawn weight once unlocked. */
    private static final Map<EnemyType, Integer> WEIGHT = new EnumMap<>(EnemyType.class);

    /**
     * Levels each tier holds a type back by, on top of its base unlock.
     *
     * <p>Easy delays the roster rather than only slowing it. Meeting five
     * different monsters in the first six levels is a lot to learn while also
     * learning to type under pressure, and the tier's job is to let the player
     * get good at one thing at a time. Medium holds the roster back by one
     * level rather than two, so the ladder introduces monsters a little sooner
     * at every rung. Hard is the reference and delays nothing.
     *
     * <p>Nothing can unlock before level 1 regardless of what is written here.
     */
    private static final Map<Difficulty, Integer> UNLOCK_DELAY = new EnumMap<>(Difficulty.class);

    static {
        UNLOCK_DELAY.put(Difficulty.EASY, 2);
        UNLOCK_DELAY.put(Difficulty.MEDIUM, 1);
        UNLOCK_DELAY.put(Difficulty.HARD, 0);
        UNLOCK_DELAY.put(Difficulty.ENDLESS, 0);
    }

    static {
        UNLOCK_WAVE.put(EnemyType.BEISACH, 1);
        UNLOCK_WAVE.put(EnemyType.AHP, 2);
        UNLOCK_WAVE.put(EnemyType.YEAK, 3);
        UNLOCK_WAVE.put(EnemyType.STEC_KANTOAB, 5);
        UNLOCK_WAVE.put(EnemyType.PRET, 6);
        // Bosses are placed explicitly by WaveManager, never randomly.
        UNLOCK_WAVE.put(EnemyType.NAGA, Integer.MAX_VALUE);
        UNLOCK_WAVE.put(EnemyType.KRONG_REAP, Integer.MAX_VALUE);

        WEIGHT.put(EnemyType.BEISACH, 5);
        WEIGHT.put(EnemyType.AHP, 4);
        WEIGHT.put(EnemyType.YEAK, 3);
        WEIGHT.put(EnemyType.STEC_KANTOAB, 2);
        WEIGHT.put(EnemyType.PRET, 2);
        WEIGHT.put(EnemyType.NAGA, 0);
        WEIGHT.put(EnemyType.KRONG_REAP, 0);
    }

    private WaveWeights() {
        // Utility class — not instantiable.
    }

    /** Every non-boss type available at {@code wave} on the reference tier. */
    static List<EnemyType> available(int wave) {
        return available(wave, Difficulty.reference());
    }

    /** Every non-boss type available at {@code wave} on a tier. Never empty. */
    static List<EnemyType> available(int wave, Difficulty difficulty) {
        int delay = UNLOCK_DELAY.getOrDefault(
                difficulty == null ? Difficulty.reference() : difficulty, 0);

        List<EnemyType> types = new ArrayList<>();
        for (EnemyType type : EnemyType.values()) {
            Integer unlock = UNLOCK_WAVE.get(type);
            if (unlock == null || unlock == Integer.MAX_VALUE) {
                continue;
            }
            // Beisach is never delayed — a tier with no enemies at all on level
            // one would be a stalled level, not a gentle one.
            int effective = type == EnemyType.BEISACH ? unlock : Math.max(1, unlock + delay);
            if (wave >= effective) {
                types.add(type);
            }
        }
        if (types.isEmpty()) {
            types.add(EnemyType.BEISACH);
        }
        return types;
    }

    /**
     * Types appearing for the very first time at {@code wave} on this tier.
     *
     * <p>Exists so {@link LevelPreview} can announce a new monster from the same
     * table that decides when it actually shows up. A hardcoded list of "level 3
     * is Yeak" was correct at Medium and a lie on every other tier, because the
     * tier shifts the unlocks — and a banner that promises the wrong monster is
     * worse than one that promises nothing.
     */
    static List<EnemyType> newlyUnlockedAt(int wave, Difficulty difficulty) {
        if (wave <= 1) {
            return List.of();
        }
        List<EnemyType> before = available(wave - 1, difficulty);
        List<EnemyType> now = available(wave, difficulty);

        List<EnemyType> fresh = new ArrayList<>();
        for (EnemyType type : now) {
            if (!before.contains(type)) {
                fresh.add(type);
            }
        }
        return fresh;
    }

    /** Picks a type for {@code wave} on the reference tier. */
    static EnemyType pick(int wave, Random random) {
        return pick(wave, Difficulty.reference(), random);
    }

    /** Picks a type for {@code wave}, respecting the tier and the weight table. */
    static EnemyType pick(int wave, Difficulty difficulty, Random random) {
        List<EnemyType> pool = available(wave, difficulty);

        int total = 0;
        for (EnemyType type : pool) {
            total += Math.max(1, WEIGHT.getOrDefault(type, 1));
        }

        int roll = random.nextInt(total);
        for (EnemyType type : pool) {
            roll -= Math.max(1, WEIGHT.getOrDefault(type, 1));
            if (roll < 0) {
                return type;
            }
        }
        return pool.get(pool.size() - 1);
    }
}
