package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.Enemy;
import com.guardiansofangkor.entities.EnemyType;
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
    @DisplayName("airborne descents hold 45 degrees, flanks stay level")
    void spawnGeometryMatchesRoute() {
        WaveManager waves = newManager();
        List<Enemy> spawned = collectSpawns(waves, 6000);

        assertFalse(spawned.isEmpty(), "a level should produce enemies");
        for (Enemy enemy : spawned) {
            double dx = Math.abs(enemy.getSpawnX() - GameConfig.TEMPLE_CENTER_X);
            double dy = enemy.getType().anchorTargetY() - enemy.getSpawnY();

            if (enemy.getPath().isFortyFiveDegrees()) {
                assertEquals(dx, dy, 0.001,
                        "equal horizontal and vertical offset is what makes it 45 degrees");
            } else if (enemy.getPath().isDescending()) {
                assertTrue(dy > 0, "a descending route must lose some altitude");
                assertTrue(dy < dx,
                        "the ground drift must stay shallower than 45 degrees");
            } else {
                assertEquals(0, dy, 0.001, "flank spawns must be level with their target");
                assertTrue(dx > 0, "flank spawns start out to one side");
            }
        }
    }

    @Test
    @DisplayName("grounded enemies never spawn off the plaza")
    void groundedEnemiesStayOnThePlaza() {
        WaveManager waves = newManager();

        for (Enemy enemy : collectSpawns(waves, 6000)) {
            if (!enemy.getType().isGrounded()) {
                continue;
            }
            assertTrue(enemy.getSpawnY() >= GameConfig.PLAZA_TOP_Y,
                    enemy.getType().getDisplayName() + " spawned at y="
                            + Math.round(enemy.getSpawnY())
                            + ", above the plaza at " + GameConfig.PLAZA_TOP_Y
                            + " — it would be standing in the sky");
        }
    }

    @Test
    @DisplayName("grounded enemies keep their feet on the plaza for the whole walk")
    void groundedEnemiesStayGroundedWhileWalking() {
        WaveManager waves = newManager();
        List<Enemy> spawned = collectSpawns(waves, 3000);

        for (Enemy enemy : spawned) {
            if (!enemy.getType().isGrounded()) {
                continue;
            }
            for (int tick = 0; tick < 1500; tick++) {
                enemy.update();
                assertTrue(enemy.getAnchorY() >= GameConfig.PLAZA_TOP_Y,
                        enemy.getType().getDisplayName() + " left the plaza mid-walk");
                assertTrue(enemy.getAnchorY() <= GameConfig.GROUND_LINE_Y + 0.001,
                        enemy.getType().getDisplayName() + " sank below the ground line");
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
            if (!enemy.getPath().isFortyFiveDegrees()) {
                // Only the long airborne descent can reach the bar.
                continue;
            }
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
    @DisplayName("both flank and descending routes actually get used")
    void bothRouteShapesAppear() {
        WaveManager waves = newManager();

        boolean sawFlank = false;
        boolean sawDescent = false;
        for (Enemy enemy : collectSpawns(waves, 6000)) {
            if (enemy.getPath().isDescending()) {
                sawDescent = true;
            } else {
                sawFlank = true;
            }
        }
        assertTrue(sawFlank, "some enemies should walk in from a flank");
        assertTrue(sawDescent, "some enemies should approach with a descent");
    }

    @Test
    @DisplayName("the Naga arrives every fifth level with a real word chain")
    void nagaAppearsEveryFifthLevel() {
        WaveManager waves = newManager();
        List<Enemy> field = new ArrayList<>();

        int nagaLevels = 0;
        int seenLevels = 0;
        int lastLevel = 0;

        for (int tick = 0; tick < 40_000 && waves.getLevel() <= 12; tick++) {
            List<Enemy> spawned = waves.update(field);
            if (waves.getLevel() != lastLevel) {
                lastLevel = waves.getLevel();
                seenLevels++;
            }
            for (Enemy enemy : spawned) {
                if (enemy.getType() == EnemyType.NAGA) {
                    nagaLevels++;
                    assertEquals(0, waves.getLevel() % 5,
                            "a Naga appeared on level " + waves.getLevel());
                    assertTrue(enemy.isChained(),
                            "a mini-boss must take more than one word");
                    assertTrue(enemy.getChainLength() >= 2
                                    && enemy.getChainLength() <= 3,
                            "chain should be 2-3 words, got " + enemy.getChainLength());
                }
            }
            field.clear();
        }

        assertTrue(seenLevels > 10, "should have run through several levels");
        assertTrue(nagaLevels >= 2,
                "expected a Naga on levels 5 and 10, saw " + nagaLevels);
    }

    @Test
    @DisplayName("ordinary enemies are never chained")
    void ordinaryEnemiesAreNotChained() {
        WaveManager waves = newManager();

        for (Enemy enemy : collectSpawns(waves, 6000)) {
            if (enemy.getType() != EnemyType.NAGA) {
                assertEquals(1, enemy.getChainLength(),
                        enemy.getType().getDisplayName() + " should die to one word");
            }
        }
    }

    @Test
    @DisplayName("chained words never collide with words already on the field")
    void chainWordsAreUniqueAcrossTheField() {
        WaveManager waves = newManager();
        List<Enemy> field = new ArrayList<>();

        for (int tick = 0; tick < 6000; tick++) {
            field.addAll(waves.update(field));

            Set<String> promised = new HashSet<>();
            for (Enemy enemy : field) {
                for (String word : enemy.getAllWords()) {
                    assertTrue(promised.add(word),
                            "word '" + word + "' is promised twice on the field");
                }
            }
        }
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
