package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.EnemyType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class WaveWeights {

    private static final Map<EnemyType, Integer> UNLOCK_WAVE = new EnumMap<>(EnemyType.class);
    private static final Map<EnemyType, Integer> WEIGHT = new EnumMap<>(EnemyType.class);
    private static final Map<Difficulty, Integer> UNLOCK_DELAY = new EnumMap<>(Difficulty.class);

    static {
        UNLOCK_DELAY.put(Difficulty.EASY, 2);
        UNLOCK_DELAY.put(Difficulty.MEDIUM, 1);
        UNLOCK_DELAY.put(Difficulty.HARD, 0);
        UNLOCK_DELAY.put(Difficulty.ENDLESS, 0);
    }

    static {
        // STANDARD MINIONS
        UNLOCK_WAVE.put(EnemyType.BEISACH, 1);
        UNLOCK_WAVE.put(EnemyType.AHP, 2);
        UNLOCK_WAVE.put(EnemyType.YEAK, 3);
        UNLOCK_WAVE.put(EnemyType.KMAOCH, 5);
        UNLOCK_WAVE.put(EnemyType.PRET, 6);

        // ELITE MINIONS - Newly added to normal spawn pool for Gauntlet modes!
        UNLOCK_WAVE.put(EnemyType.CHARGER, 7);
        UNLOCK_WAVE.put(EnemyType.GARUDA, 9);
        UNLOCK_WAVE.put(EnemyType.SPLITTER, 12);
        UNLOCK_WAVE.put(EnemyType.ARAK, 15);

        // Bosses are placed explicitly by WaveManager/GameState, never randomly!
        UNLOCK_WAVE.put(EnemyType.NAGA, Integer.MAX_VALUE);
        UNLOCK_WAVE.put(EnemyType.KRONG_REAP, Integer.MAX_VALUE);
        UNLOCK_WAVE.put(EnemyType.YAKSHA_COMMANDER, Integer.MAX_VALUE);
        UNLOCK_WAVE.put(EnemyType.CORRUPTED_APSARA, Integer.MAX_VALUE);
        UNLOCK_WAVE.put(EnemyType.REAM_EYSO, Integer.MAX_VALUE);

        WEIGHT.put(EnemyType.BEISACH, 5);
        WEIGHT.put(EnemyType.AHP, 4);
        WEIGHT.put(EnemyType.YEAK, 3);
        WEIGHT.put(EnemyType.KMAOCH, 2);
        WEIGHT.put(EnemyType.PRET, 2);

        // Elite weights
        WEIGHT.put(EnemyType.CHARGER, 2);
        WEIGHT.put(EnemyType.GARUDA, 2);
        WEIGHT.put(EnemyType.SPLITTER, 1);
        WEIGHT.put(EnemyType.ARAK, 1);
    }

    private WaveWeights() {
        // Utility class
    }

    static List<EnemyType> available(int wave) {
        return available(wave, Difficulty.reference());
    }

    static List<EnemyType> available(int wave, Difficulty difficulty) {
        int delay = UNLOCK_DELAY.getOrDefault(
                difficulty == null ? Difficulty.reference() : difficulty, 0);

        List<EnemyType> types = new ArrayList<>();
        for (EnemyType type : EnemyType.values()) {
            Integer unlock = UNLOCK_WAVE.get(type);
            if (unlock == null || unlock == Integer.MAX_VALUE) {
                continue;
            }
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

    static EnemyType pick(int wave, Random random) {
        return pick(wave, Difficulty.reference(), random);
    }

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