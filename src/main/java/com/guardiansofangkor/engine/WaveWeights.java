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

    /** Wave at which each type first appears. */
    private static final Map<EnemyType, Integer> UNLOCK_WAVE = new EnumMap<>(EnemyType.class);

    /** Relative spawn weight once unlocked. */
    private static final Map<EnemyType, Integer> WEIGHT = new EnumMap<>(EnemyType.class);

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

    /** Every non-boss type available at {@code wave}. Never empty. */
    static List<EnemyType> available(int wave) {
        List<EnemyType> types = new ArrayList<>();
        for (EnemyType type : EnemyType.values()) {
            Integer unlock = UNLOCK_WAVE.get(type);
            if (unlock != null && wave >= unlock) {
                types.add(type);
            }
        }
        if (types.isEmpty()) {
            types.add(EnemyType.BEISACH);
        }
        return types;
    }

    /** Picks a type for {@code wave}, respecting the weight table. */
    static EnemyType pick(int wave, Random random) {
        List<EnemyType> pool = available(wave);

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
