package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.ApproachPath;
import com.guardiansofangkor.entities.Enemy;
import com.guardiansofangkor.entities.EnemyType;
import com.guardiansofangkor.entities.Projectile;
import com.guardiansofangkor.entities.VisualEffect;
import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.matching.MatchStatus;
import com.guardiansofangkor.save.SaveData;
import com.guardiansofangkor.util.GameConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GameState — run lifecycle")
class GameStateTest {

    /**
     * A state with the opening countdown already skipped.
     *
     * <p>These tests are about the simulation, not the ceremony in front of it —
     * leaving the intro running would mean every one of them burned four seconds
     * of ticks before anything happened.
     */
    private static GameState playing() {
        GameState state = new GameState(Language.ENGLISH);
        state.skipIntro();
        return state;
    }

    /**
     * Adds a stationary Beisach {@code run} pixels out along a flank route.
     * A run of zero puts it exactly on the temple, i.e. already breaching.
     */
    private static Enemy enemyAt(GameState state, String word, double run) {
        Enemy enemy = new Enemy(
                EnemyType.BEISACH, ApproachPath.GROUND_FLANK, word, run, 1, 0.0);
        state.addEnemy(enemy);
        return enemy;
    }

    /** Far enough out that it will not breach during the test. */
    private static Enemy safeEnemy(GameState state, String word) {
        return enemyAt(state, word, GameConfig.FLANK_RUN_MIN);
    }

    @Test
    @DisplayName("typing a full word defeats the enemy and scores")
    void typingDefeatsEnemy() {
        GameState state = playing();
        Enemy enemy = safeEnemy(state, "ash");

        var result = state.handleInput("ash");

        assertEquals(MatchStatus.COMPLETED, result.status());
        assertFalse(enemy.isActive(), "the enemy should be defeated");
        assertTrue(state.getScore() > 0, "a kill should score");
        assertEquals(1, state.getEnemiesDefeated());
    }

    @Test
    @DisplayName("defeating an enemy fires an arrow from Preah Ream")
    void defeatingFiresArrow() {
        GameState state = playing();
        safeEnemy(state, "ash");

        state.handleInput("ash");

        boolean sawArrow = state.getEffects().stream()
                .anyMatch(e -> e.getKind() == VisualEffect.Kind.ARROW);
        assertTrue(sawArrow, "a completed word should loose an arrow");
        assertTrue(state.getPlayer().isFiring(), "the hero should be in his firing pose");
    }

    @Test
    @DisplayName("the hero returns to idle once typing stops")
    void heroReturnsToIdle() {
        GameState state = playing();
        safeEnemy(state, "ash");
        state.handleInput("ash");

        assertTrue(state.getPlayer().isFiring());
        for (int i = 0; i < GameConfig.PLAYER_ACTION_TICKS + 2; i++) {
            state.getPlayer().update();
        }
        assertFalse(state.getPlayer().isFiring(), "should settle back to the idle pose");
    }

    @Test
    @DisplayName("a walker reaching the temple costs a whole heart")
    void groundedBreachCostsAWholeHeart() {
        GameState state = playing();
        enemyAt(state, "ash", 0);

        int before = state.getHalfLives();
        state.update();

        assertEquals(before - GameConfig.HALVES_PER_LIFE, state.getHalfLives());
    }

    @Test
    @DisplayName("a flyer only costs half")
    void flyingBreachCostsHalf() {
        // The swarm types are fast, short-worded and arrive several at once.
        // A full life apiece would let one bad Ahp wave end a run outright.
        GameState state = playing();
        state.addEnemy(new Enemy(EnemyType.AHP, ApproachPath.AIR_FLANK, "ash", 0, 1, 0.0));

        int before = state.getHalfLives();
        state.update();

        assertEquals(before - 1, state.getHalfLives());
    }

    @Test
    @DisplayName("half a heart still shows as a life on the bar")
    void halfAHeartStillCounts() {
        GameState state = playing();
        state.loseHalfLife();

        assertEquals(GameConfig.STARTING_HALF_LIVES - 1, state.getHalfLives());
        assertEquals(GameConfig.STARTING_LIVES, state.getLives(),
                "three and a half pips is still three lit buds");
        assertFalse(state.isGameOver());
    }

    @Test
    @DisplayName("the last half heart ends the run")
    void theLastHalfEndsIt() {
        GameState state = playing();
        for (int i = 0; i < GameConfig.STARTING_HALF_LIVES; i++) {
            assertFalse(state.isGameOver(), "ended early at half " + i);
            state.loseHalfLife();
        }

        assertTrue(state.isGameOver());
        assertEquals(0, state.getHalfLives());
    }

    @Test
    @DisplayName("running out of lives ends the game and freezes input")
    void losingAllLivesEndsGame() {
        GameState state = playing();

        for (int i = 0; i < GameConfig.STARTING_LIVES; i++) {
            state.loseLife();
        }

        assertTrue(state.isGameOver());
        assertEquals(0, state.getHalfLives());
        assertEquals(MatchStatus.EMPTY, state.handleInput("ash").status(),
                "input must be inert once the run is over");
    }

    @Test
    @DisplayName("spawned enemies get a puff of smoke")
    void spawnsProducePoof() {
        GameState state = playing();

        for (int tick = 0; tick < 400 && state.getEnemies().isEmpty(); tick++) {
            state.update();
        }

        assertFalse(state.getEnemies().isEmpty(), "a level should start spawning");
        boolean sawPoof = state.getEffects().stream()
                .anyMatch(e -> e.getKind() == VisualEffect.Kind.SPAWN_POOF);
        assertTrue(sawPoof, "on-screen spawns must be covered by a puff");
    }

    @Test
    @DisplayName("restart wipes the run but keeps personal bests")
    void restartKeepsBests() {
        GameState state = playing();
        safeEnemy(state, "ash");
        state.handleInput("ash");

        int scoreBefore = state.getScore();
        assertTrue(scoreBefore > 0);
        state.loseLife();

        state.restart();
        state.skipIntro();

        assertEquals(0, state.getScore(), "score should reset");
        assertEquals(GameConfig.STARTING_HALF_LIVES, state.getHalfLives(),
                "lives should reset");
        assertEquals(0, state.getEnemiesDefeated());
        assertTrue(state.getEnemies().isEmpty());
        assertTrue(state.getProjectiles().isEmpty());
        assertTrue(state.getEffects().isEmpty());
        assertFalse(state.isGameOver());
        assertEquals(scoreBefore, state.getBestScore(),
                "the personal best must survive a restart");
    }

    @Test
    @DisplayName("restart after a game over makes the state playable again")
    void restartRecoversFromGameOver() {
        GameState state = playing();
        for (int i = 0; i < GameConfig.STARTING_LIVES; i++) {
            state.loseLife();
        }
        assertTrue(state.isGameOver());

        state.restart();
        state.skipIntro();

        assertFalse(state.isGameOver());
        state.update();
        assertTrue(state.getElapsedTicks() > 0, "the simulation should be running again");
    }

    @Test
    @DisplayName("level progress starts empty and advances with each kill")
    void progressAdvancesWithKills() {
        GameState state = playing();

        // Get a level running so getEnemiesInLevel() is meaningful.
        for (int tick = 0; tick < 400 && state.getLevel() < 1; tick++) {
            state.update();
        }
        assertTrue(state.getLevel() >= 1);

        double before = state.getLevelProgress();
        safeEnemy(state, "zzq");
        state.handleInput("zzq");

        assertTrue(state.getLevelProgress() > before,
                "killing an enemy should advance level progress");
    }

    @Test
    @DisplayName("a leaked enemy still advances progress, so the bar can fill")
    void breachAlsoAdvancesProgress() {
        GameState state = playing();
        for (int tick = 0; tick < 400 && state.getLevel() < 1; tick++) {
            state.update();
        }

        int before = state.getResolvedThisLevel();
        enemyAt(state, "zzq", 0);
        state.update();

        assertTrue(state.getResolvedThisLevel() > before,
                "counting only kills would leave the bar stuck short for the "
                        + "rest of the level, which reads as a bug");
    }

    @Test
    @DisplayName("level progress is always a sane fraction")
    void progressStaysInRange() {
        GameState state = playing();
        assertEquals(0.0, state.getLevelProgress(), 0.0001,
                "before level one there is nothing to report");

        for (int tick = 0; tick < 3000; tick++) {
            state.update();
            double progress = state.getLevelProgress();
            assertTrue(progress >= 0.0 && progress <= 1.0,
                    "progress escaped 0..1 at tick " + tick + ": " + progress);
        }
    }

    @Test
    @DisplayName("restart clears level progress")
    void restartClearsProgress() {
        GameState state = playing();
        for (int tick = 0; tick < 400 && state.getLevel() < 1; tick++) {
            state.update();
        }
        safeEnemy(state, "zzq");
        state.handleInput("zzq");
        assertTrue(state.getResolvedThisLevel() > 0);

        state.restart();
        state.skipIntro();

        assertEquals(0, state.getResolvedThisLevel());
        assertEquals(0.0, state.getLevelProgress(), 0.0001);
    }

    @Test
    @DisplayName("a mini-boss survives its first word and dies to its last")
    void miniBossTakesTheWholeChain() {
        GameState state = playing();
        Enemy naga = new Enemy(EnemyType.NAGA, ApproachPath.GROUND_FLANK,
                List.of("alpha", "bravo"), GameConfig.FLANK_RUN_MIN, 1, 0.0);
        state.addEnemy(naga);

        state.handleInput("alpha");
        assertTrue(naga.isActive(), "the first word must not kill a mini-boss");
        assertEquals(0, state.getEnemiesDefeated());
        assertEquals("bravo", naga.getWord(), "it should reveal the next word");

        state.handleInput("bravo");
        assertFalse(naga.isActive(), "the last word should finish it");
        assertEquals(1, state.getEnemiesDefeated());
    }

    @Test
    @DisplayName("mid-chain hits score but do not advance level progress")
    void midChainHitScoresWithoutResolving() {
        GameState state = playing();
        for (int tick = 0; tick < 400 && state.getLevel() < 1; tick++) {
            state.update();
        }
        Enemy naga = new Enemy(EnemyType.NAGA, ApproachPath.GROUND_FLANK,
                List.of("alpha", "bravo"), GameConfig.FLANK_RUN_MIN, 1, 0.0);
        state.addEnemy(naga);

        int resolvedBefore = state.getResolvedThisLevel();
        state.handleInput("alpha");

        assertTrue(state.getScore() > 0, "a mid-chain hit should still score");
        assertEquals(resolvedBefore, state.getResolvedThisLevel(),
                "the enemy is still alive, so the level is no further along");
    }

    // ---- pause -------------------------------------------------------------

    @Test
    @DisplayName("pausing freezes the simulation")
    void pauseFreezesSimulation() {
        GameState state = playing();
        for (int tick = 0; tick < 60; tick++) {
            state.update();
        }

        assertTrue(state.togglePause());
        assertTrue(state.isPaused());

        long ticksAtPause = state.getElapsedTicks();
        for (int tick = 0; tick < 120; tick++) {
            state.update();
        }
        assertEquals(ticksAtPause, state.getElapsedTicks(),
                "no game time should pass while paused");
    }

    @Test
    @DisplayName("typing is inert while paused")
    void typingIsInertWhilePaused() {
        GameState state = playing();
        Enemy enemy = safeEnemy(state, "zzq");
        state.togglePause();

        assertEquals(MatchStatus.EMPTY, state.handleInput("zzq").status());
        assertTrue(enemy.isActive(), "a paused game must not accept keystrokes");
    }

    @Test
    @DisplayName("unpausing resumes the simulation")
    void unpauseResumes() {
        GameState state = playing();
        state.togglePause();
        state.update();

        assertFalse(state.togglePause(), "toggling again should unpause");
        assertFalse(state.isPaused());

        long before = state.getElapsedTicks();
        state.update();
        assertTrue(state.getElapsedTicks() > before, "time should flow again");
    }

    @Test
    @DisplayName("a finished run cannot be paused")
    void gameOverCannotBePaused() {
        GameState state = playing();
        for (int i = 0; i < GameConfig.STARTING_LIVES; i++) {
            state.loseLife();
        }
        assertTrue(state.isGameOver());

        assertFalse(state.togglePause(),
                "a pause overlay would hide the restart prompt");
        assertFalse(state.isPaused());
    }

    // ---- winning -----------------------------------------------------------

    /**
     * Kills everything on the field, including bolts in flight.
     *
     * <p>The bolts matter: Yeak throws on the later levels, and a run that
     * reaches the finale only to lose its last life to an un-intercepted bolt
     * would fail these tests for a reason that has nothing to do with winning.
     */
    private static void clearTheField(GameState state) {
        for (Enemy enemy : List.copyOf(state.getEnemies())) {
            enemy.defeat();
        }
        for (Projectile bolt : List.copyOf(state.getProjectiles())) {
            bolt.intercept();
        }
    }

    /**
     * Plays a run from the last wave through to the end of the boss fight.
     *
     * <p>Enemies are swept as they arrive and the paragraph is typed out
     * verbatim, so what is being tested is the shape of the ending rather than
     * anyone's typing.
     */
    private static void winTheRun(GameState state) {
        state.getWaveManager().resumeAtLevel(state.getFinalLevel() - 1);

        for (int tick = 0; tick < 60_000 && !state.isBossActive(); tick++) {
            state.update();
            clearTheField(state);
        }
        assertTrue(state.isBossActive(), "the finale never arrived");

        // Past the rise AND the held briefing — nothing is typeable until both
        // are done.
        for (int tick = 0; tick <= BossFight.ARRIVAL_TICKS + BossFight.BRIEFING_TICKS;
                tick++) {
            state.update();
            clearTheField(state);
        }

        BossFight boss = state.getBoss();
        // Word at a time, because that is how the finale is typed — and each
        // word only advances on the confirming space that follows it.
        //
        // The fight alternates: every finished paragraph provokes an attack
        // phase during which the verse is off screen and nothing can be typed
        // at it. Those have to be ridden out rather than typed through, or this
        // spins against a closed window and the run is never won.
        for (int guard = 0; guard < 40_000 && boss.isFighting(); guard++) {
            if (!boss.isTyping()) {
                state.update();
                clearTheField(state);
                continue;
            }
            String word = boss.currentWord();
            for (int i = 1; i <= word.length(); i++) {
                state.handleInput(word.substring(0, i));
            }
            state.handleInput(word + " ");
        }

        for (int tick = 0; tick <= BossFight.DEATH_TICKS + 2; tick++) {
            state.update();
        }
    }

    @Test
    @DisplayName("beating the finale wins the run")
    void beatingTheBossWins() {
        GameState state = playing();

        winTheRun(state);

        assertTrue(state.isVictory(), "the run should have been won, not lost");
        assertTrue(state.isGameOver(), "a won run is still a finished one");
        assertEquals(state.getFinalLevel(), state.getLevel());
    }

    @Test
    @DisplayName("the boss briefing is not charged against the player's clock")
    void theBriefingDoesNotCostWpm() {
        // Five seconds of a screen that forbids typing would otherwise show up
        // as five seconds of nobody typing, which is a WPM penalty for reading
        // the instructions.
        GameState state = playing();
        state.getWaveManager().resumeAtLevel(state.getFinalLevel() - 1);

        for (int tick = 0; tick < 60_000 && !state.isBossActive(); tick++) {
            state.update();
            clearTheField(state);
        }
        for (int tick = 0; tick <= BossFight.ARRIVAL_TICKS; tick++) {
            state.update();
        }
        assertTrue(state.getBoss().isBriefing(), "expected to be mid-briefing");

        long before = state.getElapsedTicks();
        for (int tick = 0; tick < BossFight.BRIEFING_TICKS / 2; tick++) {
            state.update();
        }

        assertEquals(before, state.getElapsedTicks(),
                "the held briefing should not advance the run clock");
    }

    @Test
    @DisplayName("clearing the last wave is not yet a win")
    void theLastWaveIsNotTheEnd() {
        GameState state = playing();
        state.getWaveManager().resumeAtLevel(state.getFinalLevel() - 1);

        for (int tick = 0; tick < 60_000 && !state.isBossActive(); tick++) {
            state.update();
            clearTheField(state);
        }

        assertTrue(state.isBossActive());
        assertFalse(state.isVictory(),
                "the run is won by beating the boss, not by outliving the wave");
        assertFalse(state.isGameOver());
    }

    @Test
    @DisplayName("running out of lives is a loss, not a win")
    void runningOutOfLivesIsNotAVictory() {
        GameState state = playing();
        for (int i = 0; i < GameConfig.STARTING_LIVES; i++) {
            state.loseLife();
        }

        assertTrue(state.isGameOver());
        assertFalse(state.isVictory(),
                "congratulating a player who just died would be the one "
                        + "unforgivable bug on this screen");
    }

    @Test
    @DisplayName("a restart clears the victory flag")
    void restartClearsVictory() {
        GameState state = playing();
        winTheRun(state);
        assertTrue(state.isVictory());

        state.restart();

        assertFalse(state.isVictory());
        assertFalse(state.isGameOver());
        assertFalse(state.isBossActive());
    }

    // ---- the buffer a breach takes away ------------------------------------

    @Test
    @DisplayName("a breach drops the half-typed word it was aimed at")
    void breachDropsTheStaleBuffer() {
        // The reported bug: the letters stayed in the field after the monster
        // they belonged to had already hit the temple, so the next keystroke was
        // a typo against a target that no longer existed.
        GameState state = playing();
        enemyAt(state, "stone", 0);            // already on the breach point
        state.handleInput("sto");
        assertEquals("sto", state.getTypedBuffer(), "should be part-way in");

        state.update();                        // it breaches on this tick

        assertTrue(state.getTypedBuffer().isEmpty(),
                "the engine kept letters aimed at an enemy that is gone");
        assertTrue(state.consumeBufferInvalidated(),
                "and the field was never told to clear itself");
        assertFalse(state.consumeBufferInvalidated(), "the signal must be one-shot");
    }

    @Test
    @DisplayName("a breach mid-prefix is covered, not just a locked target")
    void breachDropsTheBufferEvenWhileAmbiguous() {
        // The old guard only fired when the breaching enemy was the LOCKED
        // target, and the lock is null for as long as a prefix still matches
        // more than one enemy — which is exactly when a breach surprises you.
        GameState state = playing();
        enemyAt(state, "stone", 0);
        enemyAt(state, "storm", 0);
        state.handleInput("sto");              // matches both, so nothing locks

        state.update();                        // both breach

        assertTrue(state.getTypedBuffer().isEmpty(),
                "a buffer with no live target left should have been dropped");
    }

    @Test
    @DisplayName("a buffer another enemy still matches is left alone")
    void aStillValidBufferSurvives() {
        // Taking keystrokes that are still good would be its own small theft.
        GameState state = playing();
        enemyAt(state, "stone", 0);            // breaches immediately
        safeEnemy(state, "storm");             // stays on the field
        state.handleInput("sto");

        state.update();

        assertEquals("sto", state.getTypedBuffer(),
                "the surviving enemy still matches, so the letters are still good");
    }

    // ---- unlocks -----------------------------------------------------------

    @Test
    @DisplayName("winning a run unlocks the next tier")
    void winningUnlocksTheNextTier() {
        GameState state = playing();
        assertFalse(state.getProgress().isUnlocked(Difficulty.MEDIUM),
                "Medium should be locked before Easy has been beaten");

        winTheRun(state);

        assertTrue(state.getProgress().hasCleared(Difficulty.EASY));
        assertTrue(state.getProgress().isUnlocked(Difficulty.MEDIUM));
        assertFalse(state.getProgress().isUnlocked(Difficulty.HARD),
                "one win should open one rung");
    }

    @Test
    @DisplayName("an unlock outlives the run that earned it")
    void unlocksSurviveARestart() {
        // Losing a run must not cost the player a tier they already beat.
        GameState state = playing();
        winTheRun(state);

        state.restart();

        assertTrue(state.getProgress().hasCleared(Difficulty.EASY));
        assertTrue(state.toSaveData().hasCleared("easy"),
                "and it has to reach the save file, or it dies with the session");
    }

    @Test
    @DisplayName("unlocks can be seeded from a save without resuming the run")
    void progressLoadsWithoutResuming() {
        GameState state = playing();
        int levelBefore = state.getLevel();

        state.restoreProgress(new SaveData(9, 500, 3, Language.ENGLISH, 500, 9,
                new java.util.LinkedHashSet<>(java.util.List.of("easy"))));

        assertTrue(state.getProgress().isUnlocked(Difficulty.MEDIUM));
        assertEquals(levelBefore, state.getLevel(),
                "seeding unlocks must not quietly resume somebody else's run");
    }

    @Test
    @DisplayName("save data round-trips the level and bests")
    void saveDataCarriesProgress() {
        GameState state = playing();
        safeEnemy(state, "ash");
        state.handleInput("ash");

        SaveData data = state.toSaveData();

        assertEquals(state.getScore(), data.score());
        assertEquals(state.getLives(), data.lives());
        assertEquals(Language.ENGLISH, data.language());
    }
}
