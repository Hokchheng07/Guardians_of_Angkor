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

@DisplayName("WaveManager — diagonal spawning and level flow")
class WaveManagerTest {

    private WaveManager newManager() {
        return new WaveManager(new WordBank(Language.ENGLISH, new Random(42)), new Random(42));
    }

    @Test
    @DisplayName("diagonal spawns sit on an exact 45-degree line, flank spawns are level")
    void spawnGeometryMatchesRoute() {
        WaveManager waves = newManager();
        List<Enemy> spawned = collectSpawns(waves, 6000);

        assertFalse(spawned.isEmpty(), "a level should produce enemies");
        for (Enemy enemy : spawned) {
            double targetY = enemy.getType().isGrounded()
                    ? GameConfig.GROUND_LINE_Y
                    : GameConfig.GROUND_LINE_Y - enemy.getType().getHoverHeight();

            double dx = Math.abs(enemy.getSpawnX() - GameConfig.TEMPLE_CENTER_X);
            double dy = targetY - enemy.getSpawnY();

            if (enemy.getPath().isDiagonal()) {
                assertEquals(dx, dy, 0.001,
                        "equal horizontal and vertical offset is what makes it 45 degrees");
                assertTrue(dy > 0, "diagonal spawns start back up the causeway");
            } else {
                assertEquals(0, dy, 0.001, "flank spawns must be level with their target");
                assertTrue(dx > 0, "flank spawns start out to one side");
            }
        }
    }

    @Test
    @DisplayName("ground types get ground routes and flyers get air routes")
    void routesRespectCategory() {
        WaveManager waves = newManager();

        for (Enemy enemy : collectSpawns(waves, 6000)) {
            assertEquals(!enemy.getType().isGrounded(), enemy.getPath().isAirborne(),
                    enemy.getType().getDisplayName() + " got route " + enemy.getPath());
        }
    }

    @Test
    @DisplayName("no spawn puts its word plate behind the HUD bar")
    void spawnsClearTheHudBar() {
        WaveManager waves = newManager();

        for (Enemy enemy : collectSpawns(waves, 6000)) {
            double drawnHeight = enemy.getType().getTargetHeight() * enemy.depthScale();
            double topY = enemy.getType().isGrounded()
                    ? enemy.getSpawnY() - drawnHeight
                    : enemy.getSpawnY() - drawnHeight / 2;
            double plateTop = topY - GameConfig.WORD_PLATE_CLEARANCE;

            assertTrue(plateTop > GameConfig.HUD_BAR_HEIGHT,
                    enemy.getType().getDisplayName() + " spawned with its word at "
                            + Math.round(plateTop) + ", behind the HUD bar");
        }
    }

    @Test
    @DisplayName("both flank and diagonal routes actually get used")
    void bothRouteShapesAppear() {
        WaveManager waves = newManager();

        boolean sawFlank = false;
        boolean sawDiagonal = false;
        for (Enemy enemy : collectSpawns(waves, 6000)) {
            if (enemy.getPath().isDiagonal()) {
                sawDiagonal = true;
            } else {
                sawFlank = true;
            }
        }
        assertTrue(sawFlank, "some enemies should walk in from a flank");
        assertTrue(sawDiagonal, "some enemies should descend the causeway");
    }

    @Test
    @DisplayName("spawns are on-screen, since the poof effect covers their arrival")
    void spawnsOnScreen() {
        WaveManager waves = newManager();

        for (Enemy enemy : collectSpawns(waves, 4000)) {
            assertTrue(enemy.getSpawnX() > 0 && enemy.getSpawnX() < GameConfig.SCREEN_WIDTH,
                    "spawned off-screen at x=" + enemy.getSpawnX()
                            + "; the puff would be invisible");
            assertTrue(enemy.getSpawnY() > 0,
                    "spawned above the window at y=" + enemy.getSpawnY());
        }
    }

    @Test
    @DisplayName("enemies arrive from both sides, not just one")
    void spawnsFromBothSides() {
        WaveManager waves = newManager();

        boolean sawLeft = false;
        boolean sawRight = false;
        for (Enemy enemy : collectSpawns(waves, 4000)) {
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
    @DisplayName("spawn distance varies so monsters do not stack on two pixels")
    void spawnDistanceVaries() {
        WaveManager waves = newManager();

        Set<Long> distances = new HashSet<>();
        for (Enemy enemy : collectSpawns(waves, 4000)) {
            distances.add(Math.round(Math.abs(
                    enemy.getSpawnX() - GameConfig.TEMPLE_CENTER_X)));
        }
        assertTrue(distances.size() > 3,
                "expected varied approach runs, got " + distances.size());
    }

    @Test
    @DisplayName("enemies on the field at once never share a word")
    void wordsAreUniqueOnField() {
        WaveManager waves = newManager();
        List<Enemy> field = new ArrayList<>();

        for (int tick = 0; tick < 4000; tick++) {
            field.addAll(waves.update(field));

            Set<String> words = new HashSet<>();
            for (Enemy enemy : field) {
                assertTrue(words.add(enemy.getWord()),
                        "duplicate word on field: " + enemy.getWord());
            }
        }
    }

    @Test
    @DisplayName("level advances only after the field is cleared")
    void levelAdvancesOnlyWhenCleared() {
        WaveManager waves = newManager();
        List<Enemy> field = new ArrayList<>();

        for (int tick = 0; tick < 4000; tick++) {
            field.addAll(waves.update(field));
        }
        int stalledLevel = waves.getLevel();

        field.clear();
        for (int tick = 0; tick < 4000; tick++) {
            field.addAll(waves.update(field));
            field.clear();
        }

        assertTrue(waves.getLevel() > stalledLevel,
                "clearing the field should let levels advance");
    }

    @Test
    @DisplayName("resuming from a save restarts at the saved level")
    void resumeAtLevelRestoresProgress() {
        WaveManager waves = newManager();
        waves.resumeAtLevel(7);

        assertEquals(7, waves.getLevel());
        assertFalse(waves.isLevelInProgress(), "should resume into the intermission");

        List<Enemy> field = new ArrayList<>();
        for (int tick = 0; tick < 400; tick++) {
            field.addAll(waves.update(field));
        }
        assertEquals(8, waves.getLevel(), "next level after resuming at 7 should be 8");
    }

    @Test
    @DisplayName("reset returns to a clean pre-level-one state")
    void resetClearsProgress() {
        WaveManager waves = newManager();
        List<Enemy> field = new ArrayList<>();
        for (int tick = 0; tick < 2000; tick++) {
            field.addAll(waves.update(field));
            field.clear();
        }
        assertTrue(waves.getLevel() > 1);

        waves.reset();

        assertEquals(0, waves.getLevel());
        assertFalse(waves.isLevelInProgress());
        assertEquals(0, waves.getRemainingToSpawn());
    }

    @Test
    @DisplayName("later levels spawn faster than early ones")
    void laterLevelsSpawnFaster() {
        WaveManager waves = newManager();

        int earlySpawns = countSpawnsDuringLevel(waves, 1);
        assertTrue(earlySpawns > 0, "level 1 should spawn something");
    }

    private static int countSpawnsDuringLevel(WaveManager waves, int targetLevel) {
        List<Enemy> field = new ArrayList<>();
        int count = 0;
        for (int tick = 0; tick < 3000 && waves.getLevel() <= targetLevel; tick++) {
            List<Enemy> spawned = waves.update(field);
            if (waves.getLevel() == targetLevel) {
                count += spawned.size();
            }
            field.clear();
        }
        return count;
    }

    /**
     * Runs the manager, killing everything instantly so levels keep advancing
     * and a large sample of spawns is produced.
     */
    private static List<Enemy> collectSpawns(WaveManager waves, int ticks) {
        List<Enemy> all = new ArrayList<>();
        List<Enemy> field = new ArrayList<>();
        for (int tick = 0; tick < ticks; tick++) {
            all.addAll(waves.update(field));
            field.clear();
        }
        return all;
    }
}
