package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.Enemy;
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
    private final TargetResolver resolver = new TargetResolver();
    private final WaveManager waveManager;
    private final WordBank wordBank;
    private final Language language;

    private int score;
    private int lives = GameConfig.STARTING_LIVES;
    private long elapsedTicks;
    private boolean running = true;
    private boolean gameOver;

    /** Visual characters correctly typed, for the WPM readout. */
    private int charactersTyped;

    private int bestScore;
    private int bestWave;

    /** Set for one tick when a wave is cleared, so the caller can autosave. */
    private boolean waveJustCleared;

    /** Latest resolution snapshot, for the renderer to draw highlights from. */
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
        waveJustCleared = false;

        if (!running || gameOver) {
            return;
        }
        elapsedTicks++;

        for (Enemy enemy : enemies) {
            enemy.update();
        }

        // Breaches cost a life and remove the enemy.
        List<Enemy> breached = new ArrayList<>();
        for (Enemy enemy : enemies) {
            if (enemy.hasBreached()) {
                breached.add(enemy);
            }
        }
        for (Enemy enemy : breached) {
            enemies.remove(enemy);
            if (resolver.getLockedTarget() == enemy) {
                resolver.reset();
            }
            loseLife();
        }

        enemies.removeIf(e -> e.isExpired(GameConfig.DEFEAT_ANIMATION_TICKS));

        if (!gameOver) {
            enemies.addAll(waveManager.update(enemies));
            if (waveManager.isWaveCleared()) {
                waveJustCleared = true;
            }
        }
    }

    /**
     * Feeds the current input buffer through the resolver and applies the
     * consequences (flash, defeat, score).
     *
     * @param typedSoFar full contents of the input field
     * @return the resolution snapshot, so the caller can drive feedback
     */
    public ResolveResult handleInput(String typedSoFar) {
        if (gameOver) {
            return ResolveResult.EMPTY_RESULT;
        }
        // Phase 5 will pass the live projectile list here instead of an empty one.
        List<WordTarget> projectiles = Collections.emptyList();

        ResolveResult result = resolver.submit(typedSoFar, projectiles, enemies);
        lastResult = result;

        switch (result.status()) {
            case COMPLETED -> {
                WordTarget target = result.target();
                if (target instanceof Enemy enemy) {
                    enemy.defeat();
                    charactersTyped += GraphemeCounter.count(enemy.getWord());
                    score += scoreFor(enemy);
                }
                resolver.reset();
            }
            case LOCKED, AMBIGUOUS -> {
                for (WordTarget candidate : result.candidates()) {
                    if (candidate instanceof Enemy enemy) {
                        enemy.flashHit(GameConfig.HIT_FLASH_TICKS);
                    }
                }
            }
            case TYPO, EMPTY -> {
                // No state change beyond what the resolver already tracked.
            }
        }
        return result;
    }

    /** Longer words and tougher tiers are worth more. */
    private int scoreFor(Enemy enemy) {
        int base = GraphemeCounter.count(enemy.getWord()) * 10;
        double tierBonus = 1.0 / Math.max(0.4, enemy.getType().getSpeedMultiplier());
        return (int) Math.round(base * tierBonus);
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

    public int getWave() {
        return waveManager.getWave();
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

    /** True for exactly one tick after a wave is cleared. Drives the autosave. */
    public boolean isWaveJustCleared() {
        return waveJustCleared;
    }

    public long getElapsedTicks() {
        return elapsedTicks;
    }

    /** Seconds elapsed, derived from tick count — used for the WPM readout. */
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

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    // ---- persistence -------------------------------------------------------

    /** Current progress, for the autosave hook and wave-clear saves. */
    public SaveData toSaveData() {
        return new SaveData(
                getWave(), score, lives, language,
                Math.max(bestScore, score),
                Math.max(bestWave, getWave()));
    }

    /** Restores a previous run. Enemies are not persisted — the wave restarts. */
    public void restoreFrom(SaveData data) {
        if (data == null) {
            return;
        }
        this.bestScore = data.bestScore();
        this.bestWave = data.bestWave();

        if (data.hasResumableRun()) {
            this.score = data.score();
            this.lives = data.lives();
            this.gameOver = false;
            enemies.clear();
            resolver.reset();
            waveManager.resumeAtWave(data.wave() - 1);
        }
    }

    public int getBestScore() {
        return Math.max(bestScore, score);
    }

    public int getBestWave() {
        return Math.max(bestWave, getWave());
    }
}
