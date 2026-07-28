package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.ApproachPath;
import com.guardiansofangkor.entities.Enemy;
import com.guardiansofangkor.entities.EnemyType;
import com.guardiansofangkor.i18n.WordBank;
import com.guardiansofangkor.util.GameConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Owns level composition, spawn pacing and escalation.
 *
 * <p>Enemies materialise back along a 45-degree line from the temple — up-left
 * or up-right — and walk down and inward toward it. Spawning happens on-screen
 * rather than beyond the edges, which is why every spawn is accompanied by a
 * puff of smoke: without it monsters would visibly pop into existence.
 *
 * <p>All difficulty scaling is delegated to {@link DifficultyCurve} so balance
 * lives in one readable place.
 */
public class WaveManager {

    /** Every 5th level is a Naga mini-boss level. */
    private static final int MINI_BOSS_INTERVAL = 5;

    /** Krong Reap appears at this level. */
    private static final int FINAL_BOSS_LEVEL = 15;

    /** Pause between a level being cleared and the next starting. */
    private static final int INTERMISSION_TICKS = GameConfig.TARGET_FPS * 2;

    private final WordBank wordBank;
    private final Random random;

    private int level;
    private int remainingToSpawn;
    private int spawnCooldown;
    private int intermissionCooldown;
    private boolean levelInProgress;
    private int lastDirection = -1;

    public WaveManager(WordBank wordBank) {
        this(wordBank, new Random());
    }

    /** Seeded constructor so level composition is reproducible in tests. */
    public WaveManager(WordBank wordBank, Random random) {
        this.wordBank = wordBank == null ? new WordBank(null) : wordBank;
        this.random = random == null ? new Random() : random;
    }

    /**
     * Advances spawn timing by one tick.
     *
     * @param activeEnemies enemies currently on the field, used to detect a
     *                      cleared level and to avoid duplicate words
     * @return enemies spawned this tick; usually empty
     */
    public List<Enemy> update(List<Enemy> activeEnemies) {
        List<Enemy> spawned = new ArrayList<>();

        if (!levelInProgress) {
            if (intermissionCooldown > 0) {
                intermissionCooldown--;
                return spawned;
            }
            beginLevel(level + 1);
        }

        if (remainingToSpawn > 0) {
            if (spawnCooldown > 0) {
                spawnCooldown--;
            } else {
                spawned.add(spawnOne(activeEnemies));
                remainingToSpawn--;
                spawnCooldown = DifficultyCurve.spawnIntervalTicks(level);
            }
        } else if (activeEnemies.isEmpty()) {
            levelInProgress = false;
            intermissionCooldown = INTERMISSION_TICKS;
        }

        return spawned;
    }

    /** True the tick a level finishes, so GameState knows to autosave. */
    public boolean isLevelCleared() {
        return !levelInProgress && intermissionCooldown == INTERMISSION_TICKS;
    }

    private void beginLevel(int newLevel) {
        this.level = newLevel;
        this.levelInProgress = true;
        this.remainingToSpawn = DifficultyCurve.enemyCount(newLevel);
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

        // Ground types walk in from a flank or descend the causeway; flyers do
        // the same two shapes but at hover altitude.
        ApproachPath[] routes = ApproachPath.forBehaviour(type.getGroundBehavior());
        ApproachPath path = routes[random.nextInt(routes.length)];

        // Varying the run means monsters do not all appear at the same few pixels.
        // The ceiling is per-type: a high-hovering flyer has less headroom before
        // its word plate would collide with the HUD bar.
        int maxRun = path.maxRunFor(type.anchorTargetY(), type.spawnHeadroom());
        int run = path.runMin() + random.nextInt(Math.max(1, maxRun - path.runMin() + 1));

        double speed = DifficultyCurve.speedFor(type, level);

        return new Enemy(type, path, word, run, direction, speed);
    }

    private EnemyType chooseType() {
        if (level == FINAL_BOSS_LEVEL && remainingToSpawn == 1) {
            return EnemyType.KRONG_REAP;
        }
        if (level % MINI_BOSS_INTERVAL == 0 && remainingToSpawn == 1) {
            return EnemyType.NAGA;
        }
        return WaveWeights.pick(level, random);
    }

    public int getLevel() {
        return level;
    }

    public boolean isLevelInProgress() {
        return levelInProgress;
    }

    public int getRemainingToSpawn() {
        return remainingToSpawn;
    }

    /** True while the game is between levels. Used by the HUD for the banner. */
    public boolean isIntermission() {
        return !levelInProgress && intermissionCooldown > 0;
    }

    /** Restores spawn state after loading a save. */
    public void resumeAtLevel(int savedLevel) {
        this.level = Math.max(0, savedLevel);
        this.levelInProgress = false;
        this.remainingToSpawn = 0;
        this.intermissionCooldown = INTERMISSION_TICKS;
    }

    /** Full reset for a new run. */
    public void reset() {
        this.level = 0;
        this.levelInProgress = false;
        this.remainingToSpawn = 0;
        this.spawnCooldown = 0;
        this.intermissionCooldown = 0;
        this.lastDirection = -1;
    }
}
