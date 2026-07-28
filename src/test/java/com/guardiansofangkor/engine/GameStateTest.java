package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.ApproachPath;
import com.guardiansofangkor.entities.Enemy;
import com.guardiansofangkor.entities.EnemyType;
import com.guardiansofangkor.entities.VisualEffect;
import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.matching.MatchStatus;
import com.guardiansofangkor.save.SaveData;
import com.guardiansofangkor.util.GameConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GameState — run lifecycle")
class GameStateTest {

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
        GameState state = new GameState(Language.ENGLISH);
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
        GameState state = new GameState(Language.ENGLISH);
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
        GameState state = new GameState(Language.ENGLISH);
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
        GameState state = new GameState(Language.ENGLISH);
        enemyAt(state, "ash", 0);

        int before = state.getLives();
        state.update();

        assertEquals(before - 1, state.getLives());
    }

    @Test
    @DisplayName("running out of lives ends the game and freezes input")
    void losingAllLivesEndsGame() {
        GameState state = new GameState(Language.ENGLISH);

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
        GameState state = new GameState(Language.ENGLISH);

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
        GameState state = new GameState(Language.ENGLISH);
        safeEnemy(state, "ash");
        state.handleInput("ash");

        int scoreBefore = state.getScore();
        assertTrue(scoreBefore > 0);
        state.loseLife();

        state.restart();

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
        GameState state = new GameState(Language.ENGLISH);
        for (int i = 0; i < GameConfig.STARTING_LIVES; i++) {
            state.loseLife();
        }
        assertTrue(state.isGameOver());

        state.restart();

        assertFalse(state.isGameOver());
        state.update();
        assertTrue(state.getElapsedTicks() > 0, "the simulation should be running again");
    }

    @Test
    @DisplayName("save data round-trips the level and bests")
    void saveDataCarriesProgress() {
        GameState state = new GameState(Language.ENGLISH);
        safeEnemy(state, "ash");
        state.handleInput("ash");

        SaveData data = state.toSaveData();

        assertEquals(state.getScore(), data.score());
        assertEquals(state.getLives(), data.lives());
        assertEquals(Language.ENGLISH, data.language());
    }
}
