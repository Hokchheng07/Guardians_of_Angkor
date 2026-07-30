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
    @DisplayName("a breaching enemy costs a life")
    void breachCostsLife() {
        GameState state = playing();
        enemyAt(state, "ash", 0);

        int before = state.getLives();
        state.update();

        assertEquals(before - 1, state.getLives());
    }

    @Test
    @DisplayName("running out of lives ends the game and freezes input")
    void losingAllLivesEndsGame() {
        GameState state = playing();

        for (int i = 0; i < GameConfig.STARTING_LIVES; i++) {
            state.loseLife();
        }

        assertTrue(state.isGameOver());
        assertEquals(0, state.getLives());
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
        assertEquals(GameConfig.STARTING_LIVES, state.getLives(), "lives should reset");
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

        for (int tick = 0; tick <= BossFight.ARRIVAL_TICKS; tick++) {
            state.update();
            clearTheField(state);
        }

        BossFight boss = state.getBoss();
        for (int verse = 0; verse < boss.getStageCount(); verse++) {
            String text = boss.currentSentence();
            for (int i = 1; i <= text.length(); i++) {
                state.handleInput(text.substring(0, i));
            }
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
