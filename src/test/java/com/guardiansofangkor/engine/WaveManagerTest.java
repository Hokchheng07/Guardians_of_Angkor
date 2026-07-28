package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.Enemy;
import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.i18n.WordBank;
import com.guardiansofangkor.util.GameConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WaveManager — spawning and escalation")
class WaveManagerTest {

    private WaveManager newManager() {
        return new WaveManager(new WordBank(Language.ENGLISH, new Random(42)), new Random(42));
    }

    @Test
    @DisplayName("waves escalate in enemy count and cap out")
    void enemyCountEscalatesThenCaps() {
        WaveManager waves = newManager();

        assertEquals(4, waves.enemyCountFor(1));
        assertTrue(waves.enemyCountFor(5) > waves.enemyCountFor(2), "later waves are bigger");
        assertEquals(waves.enemyCountFor(50), waves.enemyCountFor(99),
                "count must cap so the screen stays readable");
    }

    @Test
    @DisplayName("march speed escalates but is capped")
    void speedEscalatesThenCaps() {
        WaveManager waves = newManager();

        assertTrue(waves.baseSpeedFor(5) > waves.baseSpeedFor(1));
        assertEquals(waves.baseSpeedFor(50), waves.baseSpeedFor(99), 0.0001,
                "speed must cap so late waves stay playable");
    }

    @Test
    @DisplayName("enemies spawn off-screen so they walk in rather than pop in")
    void spawnsOffScreen() {
        WaveManager waves = newManager();
        List<Enemy> field = new ArrayList<>();

        List<Enemy> spawned = collectSpawns(waves, field, 600);

        assertFalse(spawned.isEmpty(), "a wave should produce enemies");
        for (Enemy enemy : spawned) {
            boolean offLeft = enemy.getX() < 0;
            boolean offRight = enemy.getX() > GameConfig.SCREEN_WIDTH;
            assertTrue(offLeft || offRight,
                    "spawned at " + enemy.getX() + " which is on-screen");
        }
    }

    @Test
    @DisplayName("enemies arrive from both edges, not just one")
    void spawnsFromBothSides() {
        WaveManager waves = newManager();
        List<Enemy> field = new ArrayList<>();

        List<Enemy> spawned = collectSpawns(waves, field, 3000);

        boolean sawLeft = false;
        boolean sawRight = false;
        for (Enemy enemy : spawned) {
            if (enemy.getDirection() > 0) {
                sawLeft = true;
            } else {
                sawRight = true;
            }
        }
        assertTrue(sawLeft, "should spawn some enemies marching rightward");
        assertTrue(sawRight, "should spawn some enemies marching leftward");
    }

    @Test
    @DisplayName("enemies on the field at once never share a word")
    void wordsAreUniqueOnField() {
        WaveManager waves = newManager();
        List<Enemy> field = new ArrayList<>();

        for (int tick = 0; tick < 3000; tick++) {
            field.addAll(waves.update(field));

            Set<String> words = new HashSet<>();
            for (Enemy enemy : field) {
                assertTrue(words.add(enemy.getWord()),
                        "duplicate word on field: " + enemy.getWord());
            }
        }
    }

    @Test
    @DisplayName("wave advances only after the field is cleared")
    void waveAdvancesOnlyWhenCleared() {
        WaveManager waves = newManager();
        List<Enemy> field = new ArrayList<>();

        // Spawn a full wave but never kill anything.
        for (int tick = 0; tick < 3000; tick++) {
            field.addAll(waves.update(field));
        }
        int waveWithEnemiesAlive = waves.getWave();

        // Now clear the field and let the intermission elapse.
        field.clear();
        for (int tick = 0; tick < 3000; tick++) {
            field.addAll(waves.update(field));
            field.clear();
        }

        assertTrue(waves.getWave() > waveWithEnemiesAlive,
                "clearing the field should let waves advance");
    }

    @Test
    @DisplayName("resuming from a save restarts at the saved wave")
    void resumeAtWaveRestoresProgress() {
        WaveManager waves = newManager();
        waves.resumeAtWave(7);

        assertEquals(7, waves.getWave());
        assertFalse(waves.isWaveInProgress(), "should resume into the intermission");

        List<Enemy> field = new ArrayList<>();
        for (int tick = 0; tick < 600; tick++) {
            field.addAll(waves.update(field));
        }
        assertEquals(8, waves.getWave(), "next wave after resuming at 7 should be 8");
    }

    /**
     * Runs the manager for {@code ticks}, killing everything instantly so waves
     * keep advancing and a large sample of spawns is produced.
     */
    private static List<Enemy> collectSpawns(WaveManager waves, List<Enemy> field, int ticks) {
        List<Enemy> all = new ArrayList<>();
        for (int tick = 0; tick < ticks; tick++) {
            List<Enemy> spawned = waves.update(field);
            all.addAll(spawned);
            field.clear();
        }
        return all;
    }
}
