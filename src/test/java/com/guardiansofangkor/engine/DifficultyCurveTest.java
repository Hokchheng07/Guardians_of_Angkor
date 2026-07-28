package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.EnemyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DifficultyCurve — escalation with level")
class DifficultyCurveTest {

    @Test
    @DisplayName("enemy count rises then plateaus")
    void enemyCountRisesThenPlateaus() {
        assertEquals(4, DifficultyCurve.enemyCount(1));
        assertTrue(DifficultyCurve.enemyCount(5) > DifficultyCurve.enemyCount(2));
        assertEquals(DifficultyCurve.enemyCount(60), DifficultyCurve.enemyCount(200),
                "count must cap so the screen stays readable");
    }

    @Test
    @DisplayName("spawn interval tightens then floors")
    void spawnIntervalTightensThenFloors() {
        assertTrue(DifficultyCurve.spawnIntervalTicks(5)
                < DifficultyCurve.spawnIntervalTicks(1));
        assertEquals(DifficultyCurve.spawnIntervalTicks(60),
                DifficultyCurve.spawnIntervalTicks(200),
                "interval must floor so waves stay survivable");
        assertTrue(DifficultyCurve.spawnIntervalTicks(200) > 0);
    }

    @Test
    @DisplayName("base speed rises then caps")
    void baseSpeedRisesThenCaps() {
        assertTrue(DifficultyCurve.baseSpeed(5) > DifficultyCurve.baseSpeed(1));
        assertEquals(DifficultyCurve.baseSpeed(60), DifficultyCurve.baseSpeed(200), 0.0001);
    }

    @ParameterizedTest
    @EnumSource(EnemyType.class)
    @DisplayName("every type speeds up with level, and every type is capped")
    void everyTypeScalesAndCaps(EnemyType type) {
        double atOne = DifficultyCurve.speedMultiplier(type, 1);
        double atTwenty = DifficultyCurve.speedMultiplier(type, 20);
        double atHundred = DifficultyCurve.speedMultiplier(type, 100);

        assertEquals(type.getSpeedMultiplier(), atOne, 0.0001,
                "level 1 should be the configured base speed");
        assertTrue(atTwenty >= atOne, "should not slow down as levels climb");
        assertTrue(atHundred <= type.getMaxSpeedMultiplier() + 0.0001,
                "must respect the per-type ceiling");
    }

    @Test
    @DisplayName("weaker enemies get faster sooner than heavies")
    void lighterEnemiesAccelerateFaster() {
        // This is the whole point of the split: the swarm becomes frantic while
        // heavies stay ponderous. Comparing the *gain*, not the absolute speed.
        double ahpGain = DifficultyCurve.speedMultiplier(EnemyType.AHP, 12)
                - DifficultyCurve.speedMultiplier(EnemyType.AHP, 1);
        double pretGain = DifficultyCurve.speedMultiplier(EnemyType.PRET, 12)
                - DifficultyCurve.speedMultiplier(EnemyType.PRET, 1);
        double bossGain = DifficultyCurve.speedMultiplier(EnemyType.KRONG_REAP, 12)
                - DifficultyCurve.speedMultiplier(EnemyType.KRONG_REAP, 1);

        assertTrue(ahpGain > pretGain,
                "the swarm type should accelerate faster than the heavy type");
        assertTrue(pretGain > bossGain || pretGain >= bossGain,
                "heavies should not accelerate slower than the boss");
    }

    @Test
    @DisplayName("heavies never become as fast as the swarm")
    void heaviesNeverOutrunTheSwarm() {
        for (int level = 1; level <= 100; level++) {
            assertTrue(DifficultyCurve.speedMultiplier(EnemyType.PRET, level)
                            < DifficultyCurve.speedMultiplier(EnemyType.AHP, level),
                    "at level " + level + " a Pret was as fast as an Ahp, which is unfair");
        }
    }

    @Test
    @DisplayName("only ranged types have a throw interval")
    void onlyRangedTypesThrow() {
        assertTrue(DifficultyCurve.throwIntervalTicks(EnemyType.YEAK, 1) > 0);
        assertEquals(0, DifficultyCurve.throwIntervalTicks(EnemyType.BEISACH, 1));
        assertEquals(0, DifficultyCurve.throwIntervalTicks(EnemyType.AHP, 9));
    }

    @Test
    @DisplayName("throws come thicker with level but never continuously")
    void throwIntervalTightensThenFloors() {
        int early = DifficultyCurve.throwIntervalTicks(EnemyType.YEAK, 1);
        int late = DifficultyCurve.throwIntervalTicks(EnemyType.YEAK, 20);

        assertTrue(late < early, "later levels should attack more often");
        assertTrue(late >= 90, "there must be a floor, or the player cannot keep up");
    }

    @Test
    @DisplayName("projectile flight time shrinks with level but stays clearable")
    void projectileFlightShrinksWithFloor() {
        assertTrue(DifficultyCurve.projectileFlightTicks(10)
                < DifficultyCurve.projectileFlightTicks(1));
        assertTrue(DifficultyCurve.projectileFlightTicks(200) >= 70,
                "a bolt must always be clearable in time");
    }

    @Test
    @DisplayName("score multiplier rewards deeper levels")
    void scoreMultiplierRewardsDepth() {
        assertEquals(1.0, DifficultyCurve.scoreMultiplier(1), 0.0001);
        assertTrue(DifficultyCurve.scoreMultiplier(10) > DifficultyCurve.scoreMultiplier(5));
    }

    @Test
    @DisplayName("level zero and negatives are treated as level one")
    void guardsAgainstNonPositiveLevels() {
        assertEquals(DifficultyCurve.enemyCount(1), DifficultyCurve.enemyCount(0));
        assertEquals(DifficultyCurve.enemyCount(1), DifficultyCurve.enemyCount(-5));
        assertEquals(DifficultyCurve.baseSpeed(1), DifficultyCurve.baseSpeed(0), 0.0001);
    }
}
