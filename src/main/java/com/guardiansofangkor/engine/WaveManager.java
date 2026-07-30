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

    /** Pause between a level being cleared and the next starting. */
    private static final int INTERMISSION_TICKS = GameConfig.TARGET_FPS * 2;

    private final WordBank wordBank;
    private final Random random;

    /**
     * Not final: the tier is chosen in the menu, after this manager exists, and
     * a new run can pick a different one. It is only ever changed between runs.
     */
    private Difficulty difficulty;

    private int level;
    private int remainingToSpawn;
    private int spawnCooldown;
    private int intermissionCooldown;
    private boolean levelInProgress;
    private int lastDirection = -1;

    public WaveManager(WordBank wordBank) {
        this(wordBank, Difficulty.defaultChoice(), new Random());
    }

    public WaveManager(WordBank wordBank, Difficulty difficulty) {
        this(wordBank, difficulty, new Random());
    }

    /** Seeded constructor so level composition is reproducible in tests. */
    public WaveManager(WordBank wordBank, Random random) {
        this(wordBank, Difficulty.defaultChoice(), random);
    }

    /** Seeded constructor so level composition is reproducible in tests. */
    public WaveManager(WordBank wordBank, Difficulty difficulty, Random random) {
        this.wordBank = wordBank == null ? new WordBank(null) : wordBank;
        this.difficulty = difficulty == null ? Difficulty.defaultChoice() : difficulty;
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
                spawnCooldown = DifficultyCurve.spawnIntervalTicks(level, difficulty);
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
        boolean isFinalBoss = isFinalBossSpawn();

        // Collect every word already promised to the field, including words
        // later in a mini-boss chain that have not been revealed yet — otherwise
        // a Naga's second word could duplicate a live enemy's.
        List<String> inPlay = new ArrayList<>();
        for (Enemy enemy : activeEnemies) {
            inPlay.addAll(enemy.getAllWords());
        }

        // The tier shifts every word's length window; the final boss gets a
        // further push, so even a gentle tier's boss demands its hardest typing.
        int minShift = difficulty.getWordMinShift();
        int maxShift = difficulty.getWordMaxShift();
        if (isFinalBoss) {
            minShift += difficulty.getBossWordLengthBonus();
            maxShift += difficulty.getBossWordLengthBonus();
        }

        List<String> words = new ArrayList<>();
        int chainLength = chainLengthFor(type, isFinalBoss);
        for (int i = 0; i < chainLength; i++) {
            String word = wordBank.wordFor(type, inPlay, minShift, maxShift);
            words.add(word);
            inPlay.add(word);
        }

        // Alternate sides, with a random chance to repeat so it is not metronomic.
        int direction = random.nextInt(4) == 0 ? lastDirection : -lastDirection;
        lastDirection = direction;

        // Ground types walk in from a flank or drift down the plaza; flyers do
        // the same two shapes but at hover altitude and a true 45 degrees.
        ApproachPath[] routes = ApproachPath.forBehaviour(type.getGroundBehavior());
        ApproachPath path = routes[random.nextInt(routes.length)];

        // Varying the run means monsters do not all appear at the same few pixels.
        // The ceiling is per-type: a high-hovering flyer has less headroom before
        // its word plate would collide with the HUD bar.
        int maxRun = path.maxRunFor(type.anchorTargetY(), type.spawnHeadroom());
        int run = path.runMin() + random.nextInt(Math.max(1, maxRun - path.runMin() + 1));

        double speed = DifficultyCurve.speedFor(type, level, difficulty);

        return new Enemy(type, path, words, run, direction, speed);
    }

    /**
     * How many words this spawn must take to kill.
     *
     * <p>The final boss uses the tier's fixed chain length so the climactic
     * fight is predictable. Ordinary mini-bosses get a randomised chain within
     * their configured range, so two Naga encounters do not feel identical.
     */
    private int chainLengthFor(EnemyType type, boolean isFinalBoss) {
        if (isFinalBoss) {
            return Math.max(1, difficulty.getFinalBossChainLength());
        }
        int max = type.getMaxChainLength();
        if (max <= 1) {
            return 1;
        }
        int min = Math.min(2, max);
        return min + random.nextInt(max - min + 1);
    }

    /** True when the spawn about to happen is this tier's final boss. */
    private boolean isFinalBossSpawn() {
        return difficulty.hasFinalBoss()
                && level == difficulty.getFinalBossLevel()
                && remainingToSpawn == 1;
    }

    private EnemyType chooseType() {
        if (isFinalBossSpawn()) {
            return difficulty.getFinalBossType();
        }
        if (level % MINI_BOSS_INTERVAL == 0 && remainingToSpawn == 1) {
            return EnemyType.NAGA;
        }
        return WaveWeights.pick(level, random);
    }

    /** The tier this manager is running, for the HUD and the boss banner. */
    public Difficulty getDifficulty() {
        return difficulty;
    }

    /**
     * Switches tier. Only valid between runs — call {@link #reset()} after, or
     * the current level would finish under different rules than it started.
     */
    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty == null ? Difficulty.defaultChoice() : difficulty;
    }

    /** True when the level just begun is this tier's final boss level. */
    public boolean isFinalBossLevel() {
        return difficulty.hasFinalBoss() && level == difficulty.getFinalBossLevel();
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
