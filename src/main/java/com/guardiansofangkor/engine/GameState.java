package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.Enemy;
import com.guardiansofangkor.entities.Player;
import com.guardiansofangkor.entities.PowerUp;
import com.guardiansofangkor.entities.PowerUpType;
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
import java.util.Random;

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
    private final List<PowerUp> powerUps = new ArrayList<>();
    private final List<VisualEffect> effects = new ArrayList<>();
    private final TargetResolver resolver = new TargetResolver();
    private final WaveManager waveManager;
    private final WordBank wordBank;
    private final Language language;
    private final Player player = new Player();

    /** Timed boons and banked shield charges. */
    private final PowerUpState powerUpState = new PowerUpState();

    /** Drives drop rolls. Seedable so wave-and-drop composition is reproducible. */
    private final Random random;

    /**
     * Not final: chosen in the menu after this state exists, and a new run can
     * pick a different one. Changed only via {@link #restartWith(Difficulty)}.
     */
    private Difficulty difficulty;

    /**
     * The loading-and-countdown beat before play starts.
     *
     * <p>While this is active the simulation is skipped entirely, so nothing
     * walks and no clock runs — but the loop keeps ticking so the renderer can
     * draw it, the same arrangement pause uses.
     */
    private IntroSequence intro = new IntroSequence();

    private int score;
    private int lives = GameConfig.STARTING_LIVES;
    private long elapsedTicks;
    private boolean running = true;
    private boolean gameOver;

    /**
     * True when the run ended by clearing the tier's last level rather than by
     * running out of lives.
     *
     * <p>Kept separate from {@link #gameOver} rather than inferred from "game
     * over with lives remaining". They are different events with different
     * screens, and the inference breaks the moment anything else can end a run.
     */
    private boolean victory;

    /** Boons collected this run, for the end-of-run summary. */
    private int powerUpsCollected;

    /**
     * True while the player has paused.
     *
     * <p>Kept separate from {@link #running} and handled by skipping the
     * simulation rather than by stopping the game loop. The loop still ticks, so
     * the renderer keeps painting and can show the pause overlay — stopping the
     * timer outright would freeze the last frame with no indication that
     * anything had happened, which reads as a crash.
     */
    private boolean paused;

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
        this(Language.ENGLISH, Difficulty.defaultChoice());
    }

    public GameState(Language language) {
        this(language, Difficulty.defaultChoice());
    }

    public GameState(Language language, Difficulty difficulty) {
        this(language, difficulty, new Random());
    }

    /** Seeded constructor so waves and drops are reproducible in tests. */
    public GameState(Language language, Difficulty difficulty, Random random) {
        this.language = language == null ? Language.ENGLISH : language;
        this.difficulty = difficulty == null ? Difficulty.defaultChoice() : difficulty;
        this.random = random == null ? new Random() : random;
        this.wordBank = new WordBank(this.language, this.random);
        this.waveManager = new WaveManager(this.wordBank, this.difficulty, this.random);
        this.intro = new IntroSequence(this.difficulty);
    }

    /** Advances the whole simulation one tick. */
    public void update() {
        levelJustCleared = false;

        if (paused || !running || gameOver) {
            return;
        }

        // The opening beat runs before anything else exists. Returning here
        // keeps elapsedTicks at zero, so the countdown does not count against
        // the player's words-per-minute.
        if (intro != null && intro.isActive()) {
            intro.update();
            return;
        }

        elapsedTicks++;

        // One scale for the whole tick, read once. Asking the power-up state
        // per entity would let a boon expire half way through a frame and
        // advance the back half of the field further than the front half.
        double timeScale = powerUpState.getTimeScale();

        powerUpState.update();
        player.update();
        updateEffects();
        updatePowerUps();
        updateEnemies(timeScale);
        updateProjectiles(timeScale);

        if (!gameOver && !victory) {
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

    private void updateEnemies(double timeScale) {
        for (Enemy enemy : enemies) {
            enemy.update(timeScale);
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
            absorbOrLoseLife(enemy.getX(), enemy.getAnchorY());
        }

        enemies.removeIf(e -> e.isExpired(GameConfig.DEFEAT_ANIMATION_TICKS));
    }

    private void updateProjectiles(double timeScale) {
        for (Projectile projectile : projectiles) {
            projectile.update(timeScale);
            if (projectile.hasJustLanded()) {
                if (resolver.getLockedTarget() == projectile) {
                    resolver.reset();
                }
                absorbOrLoseLife(projectile.getX(), projectile.getY());
            }
        }
        projectiles.removeIf(p -> p.isExpired(GameConfig.DEFEAT_ANIMATION_TICKS));
    }

    private void updatePowerUps() {
        for (PowerUp powerUp : powerUps) {
            powerUp.update();
            if (powerUp.hasJustLapsed() && resolver.getLockedTarget() == powerUp) {
                // The player was mid-way through claiming it when it faded.
                // Clearing the lock stops the next keystroke reading as a typo
                // against a target that no longer exists.
                resolver.reset();
            }
        }
        powerUps.removeIf(p -> p.isExpired(GameConfig.DEFEAT_ANIMATION_TICKS));
    }

    /**
     * Something reached the temple. Spends a Naga Shield if one is banked, and
     * only charges a life when none is.
     *
     * <p>Routed through one method so the shield can never be honoured for a
     * breach and forgotten for a bolt.
     */
    private void absorbOrLoseLife(double x, double y) {
        if (powerUpState.consumeShield()) {
            powerUpState.markFired(PowerUpType.NAGA_SHIELD);
            effects.add(new VisualEffect(
                    VisualEffect.Kind.WARD_BREAK, x, y, GameConfig.POWERUP_FLASH_TICKS, 1.0));
            return;
        }
        loseLife();
    }

    private void spawnFromWaveManager() {
        // A finite tier stops once its last level is cleared. Checked before the
        // wave manager ticks so victory lands on the frame the field empties,
        // not an intermission later.
        if (waveManager.isRunComplete() && enemies.isEmpty() && projectiles.isEmpty()) {
            declareVictory();
            return;
        }

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
                DifficultyCurve.projectileFlightTicks(getLevel(), difficulty)));
    }

    private List<String> collectWordsInPlay() {
        List<String> words = new ArrayList<>();
        for (Enemy enemy : enemies) {
            words.addAll(enemy.getAllWords());
        }
        for (Projectile projectile : projectiles) {
            words.add(projectile.getWord());
        }
        for (PowerUp powerUp : powerUps) {
            words.add(powerUp.getWord());
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
        if (gameOver || paused || isIntroActive()) {
            return ResolveResult.EMPTY_RESULT;
        }

        // Tiers are passed shortest-time-budget first: a bolt preempts a dropped
        // boon, which preempts an enemy. See TargetResolver.submit.
        ResolveResult result = resolver.submit(typedSoFar, projectiles, powerUps, enemies);
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
            charactersTyped += GraphemeCounter.count(enemy.getWord());
            score += scoreForEnemy(enemy);

            if (enemy.hasMoreWords()) {
                // A mini-boss: this word only staggers it. It stays on the
                // field, and progress does not advance, because it has not
                // actually been resolved yet.
                enemy.advanceChain();
                enemy.flashHit(GameConfig.HIT_FLASH_TICKS * 2);
            } else {
                enemy.defeat();
                enemiesDefeated++;
                resolvedThisLevel++;
                maybeDropPowerUp(enemy);
            }
        } else if (target instanceof Projectile projectile) {
            projectile.intercept();
            projectilesIntercepted++;
            charactersTyped += GraphemeCounter.count(projectile.getWord());
            score += scoreForProjectile(projectile);
        } else if (target instanceof PowerUp powerUp) {
            charactersTyped += GraphemeCounter.count(powerUp.getWord());
            claimPowerUp(powerUp);
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
        if (target instanceof PowerUp powerUp) {
            return powerUp.getX();
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
        if (target instanceof PowerUp powerUp) {
            return powerUp.getY();
        }
        return GameConfig.GROUND_LINE_Y;
    }

    // ---- power-ups ---------------------------------------------------------

    /**
     * Rolls whether a defeated enemy leaves a boon behind, and drops it where
     * it fell.
     *
     * <p>Only ordinary kills roll. Chained mini-bosses drop on their final word
     * like anything else, but a word cleared mid-chain does not — the enemy is
     * still standing, and a boon falling out of something that has not died
     * would read as a bug.
     */
    private void maybeDropPowerUp(Enemy enemy) {
        if (!PowerUpDrops.shouldDrop(difficulty, getLevel(), lives, random)) {
            return;
        }
        boolean shieldsFull =
                powerUpState.getShieldCharges() >= GameConfig.MAX_SHIELD_CHARGES;
        PowerUpType type = PowerUpDrops.roll(lives, shieldsFull, random);

        String word = wordBank.pickupWord(collectWordsInPlay());
        double dropY = enemy.getAnchorY()
                - enemy.getType().getTargetHeight() * enemy.depthScale() * 0.55;

        powerUps.add(new PowerUp(type, word, enemy.getX(), dropY));
    }

    /** Claims a pickup the player has typed and applies what it does. */
    private void claimPowerUp(PowerUp powerUp) {
        powerUp.claim();
        powerUpsCollected++;
        score += GameConfig.TARGET_FPS;

        effects.add(new VisualEffect(
                VisualEffect.Kind.BOON_CLAIMED,
                powerUp.getX(), powerUp.getY(),
                GameConfig.POWERUP_FLASH_TICKS, 1.0));

        applyPowerUp(powerUp.getType());
    }

    /**
     * Applies a boon.
     *
     * <p>Timed and charge boons are handed to {@link PowerUpState}, which owns
     * anything that outlives the moment of collection. The instant two act on
     * the field and the life count, which are this class's to change — putting
     * them in the state holder as well would leave two objects able to decide
     * what a Purge does.
     */
    void applyPowerUp(PowerUpType type) {
        if (type == null) {
            return;
        }
        switch (type) {
            case TIME_FREEZE, SLOW_TIDE -> powerUpState.activate(type, difficulty);
            case NAGA_SHIELD -> powerUpState.addShield();
            case PURGE -> {
                purgeField();
                powerUpState.markFired(type);
            }
            case MEND -> {
                lives = Math.min(GameConfig.STARTING_LIVES, lives + 1);
                powerUpState.markFired(type);
            }
        }
    }

    /**
     * Sweeps every enemy and bolt currently on the field.
     *
     * <p>Scores and counts them as kills, because from the player's side they
     * were killed — awarding nothing would make the strongest boon in the game
     * cost them their score, which is a strange thing to punish. Progress
     * advances too, so the level bar does not stall after a Purge.
     */
    private void purgeField() {
        for (Enemy enemy : enemies) {
            if (!enemy.isActive()) {
                continue;
            }
            score += scoreForEnemy(enemy);
            enemy.defeat();
            enemiesDefeated++;
            resolvedThisLevel++;
            effects.add(new VisualEffect(
                    VisualEffect.Kind.IMPACT,
                    enemy.getX(),
                    enemy.getAnchorY() - enemy.getType().getTargetHeight()
                            * enemy.depthScale() * 0.5,
                    GameConfig.ARROW_FLIGHT_TICKS + 10, enemy.depthScale()));
        }
        for (Projectile projectile : projectiles) {
            if (projectile.isActive()) {
                projectile.intercept();
                projectilesIntercepted++;
            }
        }
        resolver.reset();
    }

    /** Ends the run as a win. */
    private void declareVictory() {
        if (victory) {
            return;
        }
        victory = true;
        gameOver = true;
        resolver.reset();
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
        powerUps.clear();
        effects.clear();
        resolver.resetAll();
        waveManager.reset();
        player.reset();
        powerUpState.reset();
        // Repeat tracking is per-run, so a fresh run gets the whole vocabulary
        // back rather than starting where the last one left off.
        wordBank.resetUsage();

        score = 0;
        lives = GameConfig.STARTING_LIVES;
        elapsedTicks = 0;
        charactersTyped = 0;
        enemiesDefeated = 0;
        projectilesIntercepted = 0;
        powerUpsCollected = 0;
        resolvedThisLevel = 0;
        lastLevelSeen = 0;
        victory = false;
        // A restart earns the same countdown, so the player is never dropped
        // straight back into a wave already in motion.
        beginIntro();
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

    /** Power-up drops currently on the field. Read-only view for the renderer. */
    public List<PowerUp> getPowerUps() {
        return Collections.unmodifiableList(powerUps);
    }

    /** Running boons and banked shield charges, for the HUD. */
    public PowerUpState getPowerUpState() {
        return powerUpState;
    }

    /** Drops a boon on the field directly. For tests and for scripted moments. */
    public void addPowerUp(PowerUp powerUp) {
        if (powerUp != null) {
            powerUps.add(powerUp);
        }
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

    /**
     * True when the run ended by clearing the tier's last level.
     *
     * <p>{@link #isGameOver()} is also true then — a won run is still a finished
     * one — so anything drawing the end screen must check this first or it will
     * congratulate the player with a defeat banner.
     */
    public boolean isVictory() {
        return victory;
    }

    /** Boons claimed this run. */
    public int getPowerUpsCollected() {
        return powerUpsCollected;
    }

    /** The last level of a run on the current tier. */
    public int getFinalLevel() {
        return waveManager.getFinalLevel();
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

    /** Total enemies the current level will send, on the current tier. */
    public int getEnemiesInLevel() {
        return DifficultyCurve.enemyCount(getLevel(), difficulty);
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

    public boolean isPaused() {
        return paused;
    }

    /** The opening loading-and-countdown beat, for the renderer. */
    public IntroSequence getIntro() {
        return intro;
    }

    /** True while the opening beat is still playing and the sim is frozen. */
    public boolean isIntroActive() {
        return intro != null && intro.isActive();
    }

    /** Restarts the opening beat, e.g. when a new run begins. */
    public void beginIntro() {
        intro = new IntroSequence(difficulty);
    }

    /** Skips the remainder of the opening beat. */
    public void skipIntro() {
        if (intro != null) {
            intro.skip();
        }
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    /**
     * Starts a fresh run on a chosen tier.
     *
     * <p>The tier is fixed for the life of a run — it decides speeds, word
     * lengths and which monster ends the game — so switching it always goes
     * through a full restart rather than taking effect mid-level.
     */
    public void restartWith(Difficulty difficulty) {
        this.difficulty = difficulty == null ? Difficulty.defaultChoice() : difficulty;
        waveManager.setDifficulty(this.difficulty);
        restart();
    }

    /**
     * Flips the pause state and reports the result.
     *
     * <p>Refuses to pause a finished run — there is nothing to come back to, and
     * a pause overlay stacked on the game-over screen would hide the restart
     * prompt.
     */
    public boolean togglePause() {
        if (gameOver) {
            return false;
        }
        paused = !paused;
        return paused;
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
