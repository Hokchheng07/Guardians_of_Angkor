package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.Enemy;
import com.guardiansofangkor.entities.EnemyType;
import com.guardiansofangkor.i18n.WordBank;
import com.guardiansofangkor.util.GameConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Owns wave composition, spawn pacing and escalation.
 *
 * <p>Enemies march in from the left and right screen edges toward the temple
 * entrance at the centre. Which edge is chosen alternates with a random nudge so
 * pressure arrives from both sides rather than settling into a pattern.
 *
 * <p>Escalation per dev brief Section 1: enemy count, word length and variety
 * all climb with the wave number. Word length comes free — later waves unlock
 * heavier enemy types, and each type requests its own tier from the
 * {@link WordBank}.
 */
public class WaveManager {

    /** Every 5th wave is a Naga mini-boss wave. */
    private static final int MINI_BOSS_INTERVAL = 5;

    /** Krong Reap appears at this wave. */
    private static final int FINAL_BOSS_WAVE = 15;

    /** Pause between a wave being cleared and the next spawning. */
    private static final int INTERMISSION_TICKS = GameConfig.TARGET_FPS * 2;

    private final WordBank wordBank;
    private final Random random;

    private int wave;
    private int remainingToSpawn;
    private int spawnCooldown;
    private int intermissionCooldown;
    private boolean waveInProgress;
    private int lastDirection = -1;

    public WaveManager(WordBank wordBank) {
        this(wordBank, new Random());
    }

    /** Seeded constructor so wave composition is reproducible in tests. */
    public WaveManager(WordBank wordBank, Random random) {
        this.wordBank = wordBank == null ? new WordBank(null) : wordBank;
        this.random = random == null ? new Random() : random;
    }

    /**
     * Advances spawn timing by one tick.
     *
     * @param activeEnemies enemies currently on the field, used to detect a
     *                      cleared wave and to avoid duplicate words
     * @return enemies spawned this tick; usually empty
     */
    public List<Enemy> update(List<Enemy> activeEnemies) {
        List<Enemy> spawned = new ArrayList<>();

        if (!waveInProgress) {
            if (intermissionCooldown > 0) {
                intermissionCooldown--;
                return spawned;
            }
            beginWave(wave + 1);
        }

        if (remainingToSpawn > 0) {
            if (spawnCooldown > 0) {
                spawnCooldown--;
            } else {
                spawned.add(spawnOne(activeEnemies));
                remainingToSpawn--;
                spawnCooldown = spawnIntervalTicks();
            }
        } else if (activeEnemies.isEmpty()) {
            // Everything spawned and everything killed — wave cleared.
            waveInProgress = false;
            intermissionCooldown = INTERMISSION_TICKS;
        }

        return spawned;
    }

    /** True the tick a wave finishes, so GameState knows to autosave. */
    public boolean isWaveCleared() {
        return !waveInProgress && intermissionCooldown == INTERMISSION_TICKS;
    }

    private void beginWave(int newWave) {
        this.wave = newWave;
        this.waveInProgress = true;
        this.remainingToSpawn = enemyCountFor(newWave);
        this.spawnCooldown = 0;
    }

    private Enemy spawnOne(List<Enemy> activeEnemies) {
        EnemyType type = chooseType();

        List<String> inPlay = new ArrayList<>();
        for (Enemy enemy : activeEnemies) {
            inPlay.add(enemy.getWord());
        }
        String word = wordBank.wordFor(type, inPlay);

        // Alternate sides, with a random chance to repeat so it is not metronomic.
        int direction = random.nextInt(4) == 0 ? lastDirection : -lastDirection;
        lastDirection = direction;

        double startX = direction > 0
                ? -GameConfig.SPAWN_MARGIN
                : GameConfig.SCREEN_WIDTH + GameConfig.SPAWN_MARGIN;

        return new Enemy(type, word, startX, direction, baseSpeedFor(wave));
    }

    private EnemyType chooseType() {
        if (wave == FINAL_BOSS_WAVE && remainingToSpawn == 1) {
            return EnemyType.KRONG_REAP;
        }
        if (wave % MINI_BOSS_INTERVAL == 0 && remainingToSpawn == 1) {
            return EnemyType.NAGA;
        }
        return WaveWeights.pick(wave, random);
    }

    // ---- escalation curves ------------------------------------------------

    /** Enemies in a wave: starts at 4 and climbs, capped so it stays readable. */
    int enemyCountFor(int wave) {
        return Math.min(4 + (wave - 1) * 2, 18);
    }

    /** Ticks between spawns: tightens as waves progress, floored for fairness. */
    int spawnIntervalTicks() {
        return Math.max(GameConfig.TARGET_FPS * 2 - (wave * 6), GameConfig.TARGET_FPS / 2);
    }

    /** Pixels per tick before the type multiplier. Creeps up with the wave. */
    double baseSpeedFor(int wave) {
        return Math.min(0.35 + (wave - 1) * 0.04, 1.1);
    }

    public int getWave() {
        return wave;
    }

    public boolean isWaveInProgress() {
        return waveInProgress;
    }

    public int getRemainingToSpawn() {
        return remainingToSpawn;
    }

    /** True while the game is between waves. Used by the HUD for the banner. */
    public boolean isIntermission() {
        return !waveInProgress && intermissionCooldown > 0;
    }

    /** Restores spawn state after loading a save. */
    public void resumeAtWave(int savedWave) {
        this.wave = Math.max(0, savedWave);
        this.waveInProgress = false;
        this.remainingToSpawn = 0;
        this.intermissionCooldown = INTERMISSION_TICKS;
    }
}
