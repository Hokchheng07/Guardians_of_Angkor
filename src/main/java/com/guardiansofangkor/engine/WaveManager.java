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

public class WaveManager {

    private static final int INTERMISSION_TICKS = GameConfig.TARGET_FPS * 2;

    private WordBank wordBank;
    private final Random random;

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

    public WaveManager(WordBank wordBank, Random random) {
        this(wordBank, Difficulty.defaultChoice(), random);
    }

    public WaveManager(WordBank wordBank, Difficulty difficulty, Random random) {
        this.wordBank = wordBank == null ? new WordBank(null) : wordBank;
        this.difficulty = difficulty == null ? Difficulty.defaultChoice() : difficulty;
        this.random = random == null ? new Random() : random;
    }

    private boolean isPlazaFull(List<Enemy> activeEnemies) {
        int alive = 0;
        for (Enemy enemy : activeEnemies) {
            if (enemy.isActive()) {
                alive++;
            }
        }
        return alive >= difficulty.getMaxConcurrentEnemies();
    }

    public List<Enemy> update(List<Enemy> activeEnemies) {
        List<Enemy> spawned = new ArrayList<>();

        if (!levelInProgress) {
            // The intermission counts down FIRST, even on the last level. The
            // run-complete check used to come before it, which froze the
            // countdown on the final level — and isBossMilestoneDue waits for
            // it to reach zero, so the final boss never arrived and the run
            // hung in an endless intermission instead of being won.
            if (intermissionCooldown > 0) {
                intermissionCooldown--;
                return spawned;
            }
            if (isRunComplete()) {
                return spawned;
            }
            // GAUNTLET UPGRADE: Hit the brakes! If it's a multiple of 10, stop spawning and let GameState trigger the boss.
            if (level > 0 && level % 10 == 0) {
                return spawned;
            }
            beginLevel(level + 1);
        }

        if (remainingToSpawn > 0) {
            if (isPlazaFull(activeEnemies)) {
                return spawned;
            }
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
        return spawnOne(activeEnemies, chooseType(), List.of());
    }

    public Enemy spawnBossMinion(List<Enemy> activeEnemies, List<String> reservedWords) {
        EnemyType type = WaveWeights.pick(Math.max(1, level), difficulty, random);
        return spawnOne(activeEnemies == null ? List.of() : activeEnemies,
                type, reservedWords == null ? List.of() : reservedWords);
    }

    public Enemy spawnBossMinion(EnemyType type, List<Enemy> activeEnemies, List<String> reservedWords) {
        return spawnOne(activeEnemies == null ? List.of() : activeEnemies,
                type, reservedWords == null ? List.of() : reservedWords);
    }

    public Enemy spawnSpecific(EnemyType type, List<Enemy> activeEnemies) {
        return spawnOne(activeEnemies == null ? List.of() : activeEnemies, type, List.of());
    }

    private Enemy spawnOne(List<Enemy> activeEnemies, EnemyType type,
                           List<String> reservedWords) {
        List<String> inPlay = new ArrayList<>(reservedWords);
        for (Enemy enemy : activeEnemies) {
            inPlay.addAll(enemy.getAllWords());
        }

        WordPolicy policy = currentPolicy();
        List<String> words = new ArrayList<>();
        int chainLength = chainLengthFor(type);

        for (int i = 0; i < chainLength; i++) {
            String word = wordFor(type, inPlay, policy);
            words.add(word);
            inPlay.add(word);
        }

        int direction = random.nextInt(4) == 0 ? lastDirection : -lastDirection;
        lastDirection = direction;

        ApproachPath[] routes = ApproachPath.forBehaviour(type.getGroundBehavior());
        ApproachPath path = routes[random.nextInt(routes.length)];

        int maxRun = path.maxRunFor(type.anchorTargetY(), type.spawnHeadroom());
        int run = path.runMin() + random.nextInt(Math.max(1, maxRun - path.runMin() + 1));

        double speed = DifficultyCurve.speedFor(type, level, difficulty);

        return new Enemy(type, path, words, run, direction, speed);
    }

    public WordPolicy currentPolicy() {
        return wordBank.policyFor(difficulty.getWordBankKey(), Math.max(1, level));
    }

    private String wordFor(EnemyType type, List<String> inPlay, WordPolicy policy) {
        if (type.isChainedType()) {
            return wordBank.bossWord(inPlay, policy);
        }
        return wordBank.wordFor(type, inPlay, policy,
                difficulty.getWordMinShift(), difficulty.getWordMaxShift());
    }

    private int chainLengthFor(EnemyType type) {
        int max = type.getMaxChainLength();
        if (max <= 1) {
            return 1;
        }
        int min = Math.min(2, max);
        return min + random.nextInt(max - min + 1);
    }

    private EnemyType chooseType() {
        // Removed hardcoded Naga logic. We rely on the Gauntlet now!
        return WaveWeights.pick(level, difficulty, random);
    }

    // NEW GAUNTLET METHODS
    public boolean isBossMilestoneDue() {
        // Tells GameState that the intermission is over and a boss wave has been reached
        return !levelInProgress && intermissionCooldown == 0 && level > 0 && level % 10 == 0;
    }

    public void resumeAfterBoss() {
        // Restarts the engine for the next wave
        beginLevel(level + 1);
    }

    public boolean isRunComplete() {
        return difficulty.isWinnable()
                && !levelInProgress
                && level >= difficulty.getFinalLevel();
    }

    public int getFinalLevel() {
        return difficulty.getFinalLevel();
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty == null ? Difficulty.defaultChoice() : difficulty;
    }

    /**
     * Switches the vocabulary the next spawn draws from, e.g. after a language
     * change. Words already in play are untouched — only what the next spawn
     * asks for changes.
     */
    public void setWordBank(WordBank wordBank) {
        this.wordBank = wordBank == null ? new WordBank(null) : wordBank;
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

    public boolean isIntermission() {
        return !levelInProgress && intermissionCooldown > 0;
    }

    public void resumeAtLevel(int savedLevel) {
        this.level = Math.max(0, savedLevel);
        this.levelInProgress = false;
        this.remainingToSpawn = 0;
        this.intermissionCooldown = INTERMISSION_TICKS;
    }

    public void reset() {
        this.level = 0;
        this.levelInProgress = false;
        this.remainingToSpawn = 0;
        this.spawnCooldown = 0;
        this.intermissionCooldown = 0;
        this.lastDirection = -1;
    }
}