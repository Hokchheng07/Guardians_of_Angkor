package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.Enemy;
import com.guardiansofangkor.entities.Player;
import com.guardiansofangkor.entities.Projectile;
import com.guardiansofangkor.entities.VisualEffect;
import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.i18n.WordBank;
import com.guardiansofangkor.matching.ResolveResult;
import com.guardiansofangkor.matching.TargetResolver;
import com.guardiansofangkor.matching.WordTarget;
import com.guardiansofangkor.save.SaveData;
import com.guardiansofangkor.util.GameConfig;
import com.guardiansofangkor.util.GraphemeCounter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * All mutable gameplay state for a run, plus the per-tick update.
 *
 * <p>This class is pure logic — it must never import anything from
 * {@code java.awt} or {@code javax.swing}. The renderer reads from it; it never
 * reads from the renderer.
 */
public class GameState {

    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<VisualEffect> effects = new ArrayList<>();
    private final TargetResolver resolver = new TargetResolver();
    private final WaveManager waveManager;
    private final WordBank wordBank;
    private final Language language;
    private final Player player = new Player();

    private int score;
    private int lives = GameConfig.STARTING_LIVES;
    private long elapsedTicks;
    private boolean running = true;
    private boolean gameOver;

    private int charactersTyped;
    private int enemiesDefeated;
    private int projectilesIntercepted;

    /**
     * Enemies dealt with in the current level, whether killed or lost to a
     * breach.
     *
     * <p>Counting breaches as well as kills is deliberate: the progress bar
     * reports how far through the level you are, not how well you are doing. If
     * it only counted kills, a single leaked enemy would leave the bar stuck
     * short of full for the rest of the level, which reads as a bug.
     */
    private int resolvedThisLevel;

    /** Tracks level changes so {@link #resolvedThisLevel} can be reset. */
    private int lastLevelSeen;

    private int bestScore;
    private int bestLevel;

    private boolean levelJustCleared;

    private ResolveResult lastResult;

    public GameState() {
        this(Language.ENGLISH);
    }

    public GameState(Language language) {
        this.language = language == null ? Language.ENGLISH : language;
        this.wordBank = new WordBank(this.language);
        this.waveManager = new WaveManager(this.wordBank);
    }

    /** Advances the whole simulation one tick. */
    public void update() {
        levelJustCleared = false;

        if (!running || gameOver) {
            return;
        }
        elapsedTicks++;

        player.update();
        updateEffects();
        updateEnemies();
        updateProjectiles();

        if (!gameOver) {
            spawnFromWaveManager();
        }

        if (waveManager.getLevel() != lastLevelSeen) {
            lastLevelSeen = waveManager.getLevel();
            resolvedThisLevel = 0;
        }
    }

    private void updateEffects() {
        for (VisualEffect effect : effects) {
            effect.update();
        }
        effects.removeIf(VisualEffect::isExpired);
    }

    private void updateEnemies() {
        for (Enemy enemy : enemies) {
            enemy.update();
            if (enemy.isProjectileDue()) {
                throwProjectileFrom(enemy);
            }
        }

        List<Enemy> breached = new ArrayList<>();
        for (Enemy enemy : enemies) {
            if (enemy.hasBreached()) {
                breached.add(enemy);
            }
        }
        for (Enemy enemy : breached) {
            enemies.remove(enemy);
            resolvedThisLevel++;
            if (resolver.getLockedTarget() == enemy) {
                resolver.reset();
            }
            loseLife();
        }

        enemies.removeIf(e -> e.isExpired(GameConfig.DEFEAT_ANIMATION_TICKS));
    }

    private void updateProjectiles() {
        for (Projectile projectile : projectiles) {
            projectile.update();
            if (projectile.hasJustLanded()) {
                if (resolver.getLockedTarget() == projectile) {
                    resolver.reset();
                }
                loseLife();
            }
        }
        projectiles.removeIf(p -> p.isExpired(GameConfig.DEFEAT_ANIMATION_TICKS));
    }

    private void spawnFromWaveManager() {
        List<Enemy> spawned = waveManager.update(enemies);
        for (Enemy enemy : spawned) {
            enemies.add(enemy);
            // Materialise in a puff so on-screen spawning does not read as popping in.
            effects.add(new VisualEffect(
                    VisualEffect.Kind.SPAWN_POOF,
                    enemy.getX(),
                    enemy.getAnchorY() - enemy.getType().getTargetHeight()
                            * enemy.depthScale() * 0.35,
                    GameConfig.POOF_TICKS,
                    enemy.depthScale()));
        }
        if (waveManager.isLevelCleared()) {
            levelJustCleared = true;
        }
    }

    private void throwProjectileFrom(Enemy enemy) {
        String word = wordBank.projectileWord(collectWordsInPlay());
        projectiles.add(new Projectile(
                word,
                enemy.getThrowOriginX(), enemy.getThrowOriginY(),
                GameConfig.TEMPLE_CENTER_X, GameConfig.GROUND_LINE_Y - 40,
                DifficultyCurve.projectileFlightTicks(getLevel())));
    }

    private List<String> collectWordsInPlay() {
        List<String> words = new ArrayList<>();
        for (Enemy enemy : enemies) {
            words.add(enemy.getWord());
        }
        for (Projectile projectile : projectiles) {
            words.add(projectile.getWord());
        }
        return words;
    }

    /**
     * Feeds the current input buffer through the resolver and applies the
     * consequences (flash, defeat, score, arrows).
     *
     * @param typedSoFar full contents of the input field
     * @return the resolution snapshot, so the caller can drive feedback
     */
    public ResolveResult handleInput(String typedSoFar) {
        if (gameOver) {
            return ResolveResult.EMPTY_RESULT;
        }

        // Projectiles are passed as the priority list — they preempt enemies as
        // the active target because their time budget is far shorter.
        ResolveResult result = resolver.submit(typedSoFar, projectiles, enemies);
        lastResult = result;

        switch (result.status()) {
            case COMPLETED -> handleCompleted(result.target());
            case LOCKED, AMBIGUOUS -> handleProgress(result.candidates());
            case TYPO, EMPTY -> {
                // No state change beyond what the resolver already tracked.
            }
        }
        return result;
    }

    private void handleCompleted(WordTarget target) {
        if (target instanceof Enemy enemy) {
            enemy.defeat();
            enemiesDefeated++;
            resolvedThisLevel++;
            charactersTyped += GraphemeCounter.count(enemy.getWord());
            score += scoreForEnemy(enemy);
        } else if (target instanceof Projectile projectile) {
            projectile.intercept();
            projectilesIntercepted++;
            charactersTyped += GraphemeCounter.count(projectile.getWord());
            score += scoreForProjectile(projectile);
        }

        // A kill always looses an arrow, regardless of the shot cooldown — the
        // hero must visibly be the one who landed the blow.
        player.tryFire();
        spawnArrowAt(aimPointX(target), aimPointY(target));

        resolver.reset();
    }

    private void handleProgress(List<WordTarget> candidates) {
        for (WordTarget candidate : candidates) {
            if (candidate instanceof Enemy enemy) {
                enemy.flashHit(GameConfig.HIT_FLASH_TICKS);
            } else if (candidate instanceof Projectile projectile) {
                projectile.flashHit(GameConfig.HIT_FLASH_TICKS);
            }
        }

        // Mid-word keystrokes fire too, but rate-limited, and only once a single
        // target is locked — spraying arrows at every ambiguous candidate would
        // fill the screen and destroy the read on which one is targeted.
        boolean fired = player.tryFire();
        if (fired && candidates.size() == 1) {
            WordTarget only = candidates.get(0);
            spawnArrowAt(aimPointX(only), aimPointY(only));
        }
    }

    private static double aimPointX(WordTarget target) {
        if (target instanceof Enemy enemy) {
            return enemy.getX();
        }
        if (target instanceof Projectile projectile) {
            return projectile.getX();
        }
        return GameConfig.TEMPLE_CENTER_X;
    }

    /** Aims at the middle of the target's body rather than its feet. */
    private static double aimPointY(WordTarget target) {
        if (target instanceof Enemy enemy) {
            return enemy.getAnchorY()
                    - enemy.getType().getTargetHeight() * enemy.depthScale() * 0.5;
        }
        if (target instanceof Projectile projectile) {
            return projectile.getY();
        }
        return GameConfig.GROUND_LINE_Y;
    }

    private void spawnArrowAt(double targetX, double targetY) {
        effects.add(new VisualEffect(
                VisualEffect.Kind.ARROW,
                player.getX(), player.getBowY(),
                targetX, targetY,
                GameConfig.ARROW_FLIGHT_TICKS, 1.0));
        effects.add(new VisualEffect(
                VisualEffect.Kind.IMPACT,
                targetX, targetY,
                GameConfig.ARROW_FLIGHT_TICKS + 10, 1.0));
    }

    private int scoreForEnemy(Enemy enemy) {
        int base = GraphemeCounter.count(enemy.getWord()) * 10;
        double tierBonus = 1.0 / Math.max(0.4, enemy.getType().getSpeedMultiplier());
        return (int) Math.round(base * tierBonus * DifficultyCurve.scoreMultiplier(getLevel()));
    }

    private int scoreForProjectile(Projectile projectile) {
        // Short words but urgent — worth a flat premium so intercepting feels
        // rewarding rather than a distraction from scoring on enemies.
        int base = GraphemeCounter.count(projectile.getWord()) * 25;
        return (int) Math.round(base * DifficultyCurve.scoreMultiplier(getLevel()));
    }

    /** Wipes the run and starts over from level 1, keeping personal bests. */
    public void restart() {
        int runBestScore = Math.max(bestScore, score);
        int runBestLevel = Math.max(bestLevel, getLevel());

        enemies.clear();
        projectiles.clear();
        effects.clear();
        resolver.resetAll();
        waveManager.reset();
        player.reset();

        score = 0;
        lives = GameConfig.STARTING_LIVES;
        elapsedTicks = 0;
        charactersTyped = 0;
        enemiesDefeated = 0;
        projectilesIntercepted = 0;
        resolvedThisLevel = 0;
        lastLevelSeen = 0;
        gameOver = false;
        running = true;
        levelJustCleared = false;
        lastResult = null;

        bestScore = runBestScore;
        bestLevel = runBestLevel;
    }

    public void addEnemy(Enemy enemy) {
        if (enemy != null) {
            enemies.add(enemy);
        }
    }

    /** Read-only view for the renderer. */
    public List<Enemy> getEnemies() {
        return Collections.unmodifiableList(enemies);
    }

    public List<Projectile> getProjectiles() {
        return Collections.unmodifiableList(projectiles);
    }

    public List<VisualEffect> getEffects() {
        return Collections.unmodifiableList(effects);
    }

    public Player getPlayer() {
        return player;
    }

    public TargetResolver getResolver() {
        return resolver;
    }

    public WaveManager getWaveManager() {
        return waveManager;
    }

    public WordBank getWordBank() {
        return wordBank;
    }

    public Language getLanguage() {
        return language;
    }

    public ResolveResult getLastResult() {
        return lastResult;
    }

    public int getScore() {
        return score;
    }

    public int getLevel() {
        return waveManager.getLevel();
    }

    public int getLives() {
        return lives;
    }

    public void loseLife() {
        lives--;
        if (lives <= 0) {
            lives = 0;
            gameOver = true;
        }
    }

    public boolean isGameOver() {
        return gameOver;
    }

    /** True for exactly one tick after a level is cleared. Drives the autosave. */
    public boolean isLevelJustCleared() {
        return levelJustCleared;
    }

    public long getElapsedTicks() {
        return elapsedTicks;
    }

    public double getElapsedSeconds() {
        return (double) elapsedTicks / GameConfig.TARGET_FPS;
    }

    /**
     * Words per minute, using the standard 5-characters-per-word convention so
     * it is comparable with typing tests.
     */
    public double getWpm() {
        double minutes = getElapsedSeconds() / 60.0;
        if (minutes <= 0.01) {
            return 0;
        }
        return (charactersTyped / 5.0) / minutes;
    }

    public int getCharactersTyped() {
        return charactersTyped;
    }

    public int getEnemiesDefeated() {
        return enemiesDefeated;
    }

    /** Enemies dealt with so far in the current level, killed or leaked. */
    public int getResolvedThisLevel() {
        return resolvedThisLevel;
    }

    /** Total enemies the current level will send. */
    public int getEnemiesInLevel() {
        return DifficultyCurve.enemyCount(getLevel());
    }

    /**
     * How far through the current level the player is, 0 to 1. Drives the HUD
     * progress bar.
     */
    public double getLevelProgress() {
        if (getLevel() < 1) {
            return 0;
        }
        int total = getEnemiesInLevel();
        if (total <= 0) {
            return 0;
        }
        return Math.max(0.0, Math.min(1.0, resolvedThisLevel / (double) total));
    }

    public int getProjectilesIntercepted() {
        return projectilesIntercepted;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    // ---- persistence -------------------------------------------------------

    public SaveData toSaveData() {
        return new SaveData(
                getLevel(), score, lives, language,
                Math.max(bestScore, score),
                Math.max(bestLevel, getLevel()));
    }

    /** Restores a previous run. Enemies are not persisted — the level restarts. */
    public void restoreFrom(SaveData data) {
        if (data == null) {
            return;
        }
        this.bestScore = data.bestScore();
        this.bestLevel = data.bestWave();

        if (data.hasResumableRun()) {
            this.score = data.score();
            this.lives = data.lives();
            this.gameOver = false;
            enemies.clear();
            projectiles.clear();
            resolver.reset();
            waveManager.resumeAtLevel(data.wave() - 1);
        }
    }

    public int getBestScore() {
        return Math.max(bestScore, score);
    }

    public int getBestLevel() {
        return Math.max(bestLevel, getLevel());
    }
}
