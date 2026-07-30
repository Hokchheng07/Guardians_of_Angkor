package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.ApproachPath;
import com.guardiansofangkor.entities.Enemy;
import com.guardiansofangkor.entities.EnemyType;
import com.guardiansofangkor.i18n.WordBank;
import com.guardiansofangkor.i18n.WordPolicy;
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
            // The tier's last wave has been cleared. Stop here rather than
            // rolling into level 16 — the finale takes over from this point, and
            // a finite tier that kept spawning would have no ending at all.
            if (isRunComplete()) {
                return spawned;
            }
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
        this.remainingToSpawn = DifficultyCurve.enemyCount(newLevel, difficulty);
        this.spawnCooldown = 0;
    }

    private Enemy spawnOne(List<Enemy> activeEnemies) {
        EnemyType type = chooseType();

        // Collect every word already promised to the field, including words
        // later in a mini-boss chain that have not been revealed yet — otherwise
        // a Naga's second word could duplicate a live enemy's.
        List<String> inPlay = new ArrayList<>();
        for (Enemy enemy : activeEnemies) {
            inPlay.addAll(enemy.getAllWords());
        }

        // What this tier is allowed to say at this point in the run. Resolved
        // per spawn rather than cached, because a level can tick over mid-wave.
        WordPolicy policy = currentPolicy();

        List<String> words = new ArrayList<>();
        int chainLength = chainLengthFor(type);
        for (int i = 0; i < chainLength; i++) {
            String word = wordFor(type, inPlay, policy);
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
     * The vocabulary this tier may draw on at the current level.
     *
     * <p>Resolved from the word bank's own JSON, so which levels get which words
     * is a data question. Nothing here decides it.
     */
    public WordPolicy currentPolicy() {
        return wordBank.policyFor(difficulty.getWordBankKey(), Math.max(1, level));
    }

    /**
     * Picks one word for a spawn.
     *
     * <p>Mini-bosses come from their own ranked pools rather than from the
     * regular vocabulary with a length bonus bolted on. Two reasons: a boss word
     * can then never have already turned up on an ordinary enemy earlier in the
     * run, and the rank climbs with both the tier and the level band, so an Easy
     * Naga and a Hard Naga are genuinely different fights rather than the same
     * fight with two more letters.
     */
    private String wordFor(EnemyType type, List<String> inPlay, WordPolicy policy) {
        if (type.isChainedType()) {
            return wordBank.bossWord(inPlay, policy);
        }
        return wordBank.wordFor(type, inPlay, policy,
                difficulty.getWordMinShift(), difficulty.getWordMaxShift());
    }

    /**
     * How many words this spawn must take to kill.
     *
     * <p>Mini-bosses get a randomised chain within their configured range, so
     * two Naga encounters do not feel identical. The <em>final</em> boss is not
     * here at all — see {@link BossFight}; it is a paragraph, not a chain, and
     * it arrives after the last wave rather than inside it.
     */
    private int chainLengthFor(EnemyType type) {
        int max = type.getMaxChainLength();
        if (max <= 1) {
            return 1;
        }
        int min = Math.min(2, max);
        return min + random.nextInt(max - min + 1);
    }

    private EnemyType chooseType() {
        if (level % MINI_BOSS_INTERVAL == 0 && remainingToSpawn == 1) {
            return EnemyType.NAGA;
        }
        return WaveWeights.pick(level, difficulty, random);
    }

    /**
     * True once the tier's last wave has been cleared.
     *
     * <p>Not the same as winning any more: this is the cue for the final boss to
     * arrive, and the run is only won once that fight is over. A tier with no
     * final boss (Endless) never returns true here, which is the whole of what
     * "endless" means mechanically.
     */
    public boolean isRunComplete() {
        return difficulty.isWinnable()
                && !levelInProgress
                && level >= difficulty.getFinalLevel();
    }

    /** The last level of a run on this tier, or {@code Integer.MAX_VALUE}. */
    public int getFinalLevel() {
        return difficulty.getFinalLevel();
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
