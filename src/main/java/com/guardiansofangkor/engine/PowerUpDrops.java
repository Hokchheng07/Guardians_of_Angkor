package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.EnemyType;
import com.guardiansofangkor.entities.PowerUpType;
import com.guardiansofangkor.util.GameConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Decides whether a defeated spirit leaves a boon, and which one.
 *
 * <p>Kept beside {@link WaveWeights} and for the same reason: this is a tuning
 * table, and a table is easier to argue about than the same numbers scattered
 * through spawn code.
 *
 * <p>The rate is not flat. It rises as the player loses lives, which is the
 * whole point of having drops at all — a run that is going badly gets quietly
 * more generous, so a player who is out of their depth is pulled back toward the
 * fight rather than watching the last life drain with nothing to reach for. A
 * run going well barely notices the system exists.
 */
final class PowerUpDrops {

    /** Most a low-life bonus can multiply the base chance by. */
    private static final double MAX_MERCY_MULTIPLIER = 2.0;

    /** No drops at all until this level, so the opening minute stays legible. */
    private static final int FIRST_DROP_LEVEL = 2;

    /**
     * Hard ceiling, so a bad streak cannot turn into a shower of boons.
     *
     * <p>Lowered alongside the base rates: even at the mercy curve's most
     * generous, fewer than one kill in three should leave anything behind.
     */
    private static final double MAX_CHANCE = 0.30;

    private PowerUpDrops() {
        // Utility class — not instantiable.
    }

    /**
     * Chance that a kill drops something, 0 to 1.
     *
     * @param lives lives the player has left right now
     */
    static double chanceFor(Difficulty difficulty, int level, int lives) {
        if (level < FIRST_DROP_LEVEL) {
            return 0;
        }
        Difficulty tier = difficulty == null ? Difficulty.reference() : difficulty;
        double base = tier.getPowerUpDropChance();

        // Mercy scales with how much of the life bar is gone: untouched is 1.0,
        // down to the last life is the full multiplier.
        double lost = Math.max(0, GameConfig.STARTING_LIVES - Math.max(0, lives));
        double hurt = lost / (double) Math.max(1, GameConfig.STARTING_LIVES - 1);
        double mercy = 1.0 + (MAX_MERCY_MULTIPLIER - 1.0) * Math.min(1.0, hurt);

        return Math.min(MAX_CHANCE, base * mercy);
    }

    /**
     * Rolls whether this kill drops a boon.
     *
     * <p>Only the types that {@link EnemyType#dropsBoons()} allows can drop at
     * all — the grounded heavies and the mini-boss. Checking eligibility here
     * rather than at the call site keeps every question about drops in this one
     * table.
     */
    static boolean shouldDrop(EnemyType type, Difficulty difficulty, int level,
                              int lives, Random random) {
        if (type == null || !type.dropsBoons()) {
            return false;
        }
        double chance = chanceFor(difficulty, level, lives);
        return chance > 0 && random.nextDouble() < chance;
    }

    /**
     * Picks which boon drops, respecting the weight table.
     *
     * <p>Mend is withheld at full health rather than being rolled and wasted. A
     * drop the player runs across the screen for and gains nothing from teaches
     * them to ignore drops, which costs far more than the occasional heal.
     */
    static PowerUpType roll(int lives, boolean shieldsFull, Random random) {
        List<PowerUpType> pool = new ArrayList<>();
        for (PowerUpType type : PowerUpType.values()) {
            if (type == PowerUpType.MEND && lives >= GameConfig.STARTING_LIVES) {
                continue;
            }
            if (type == PowerUpType.NAGA_SHIELD && shieldsFull) {
                continue;
            }
            pool.add(type);
        }
        if (pool.isEmpty()) {
            return PowerUpType.SLOW_TIDE;
        }

        int total = 0;
        for (PowerUpType type : pool) {
            total += Math.max(1, type.getDropWeight());
        }
        int point = random.nextInt(total);
        for (PowerUpType type : pool) {
            point -= Math.max(1, type.getDropWeight());
            if (point < 0) {
                return type;
            }
        }
        return pool.get(pool.size() - 1);
    }
}
