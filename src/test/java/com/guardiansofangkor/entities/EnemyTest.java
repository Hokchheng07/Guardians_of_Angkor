package com.guardiansofangkor.entities;

import com.guardiansofangkor.util.GameConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Enemy — diagonal approach, depth and attacks")
class EnemyTest {

    private static final int RUN = 380;

    /** Builds an enemy on the diagonal route appropriate to its category. */
    private static Enemy onApproach(EnemyType type, String word, int direction, double speed) {
        ApproachPath path = type.isGrounded()
                ? ApproachPath.GROUND_DIAGONAL
                : ApproachPath.AIR_DIAGONAL;
        return new Enemy(type, path, word, RUN, direction, speed);
    }

    /** Builds an enemy walking in horizontally from a flank. */
    private static Enemy onFlank(EnemyType type, String word, int direction, double speed) {
        ApproachPath path = type.isGrounded()
                ? ApproachPath.GROUND_FLANK
                : ApproachPath.AIR_FLANK;
        return new Enemy(type, path, word, GameConfig.FLANK_RUN_MIN, direction, speed);
    }

    @Test
    @DisplayName("grounded enemies settle exactly on the ground line")
    void groundedSettlesOnGroundLine() {
        Enemy yeak = onApproach(EnemyType.YEAK, "temple", 1, 4.0);

        assertTrue(yeak.getAnchorY() < GameConfig.GROUND_LINE_Y, "starts up the causeway");

        for (int i = 0; i < 2000; i++) {
            yeak.update();
        }
        assertEquals(GameConfig.GROUND_LINE_Y, yeak.getAnchorY(), 0.001,
                "a legged monster must end up with its feet on the plaza");
    }

    @Test
    @DisplayName("grounded enemies never sink below the ground line")
    void groundedNeverOvershoots() {
        Enemy pret = onApproach(EnemyType.PRET, "monument", 1, 9.0);

        for (int i = 0; i < 500; i++) {
            pret.update();
            assertTrue(pret.getAnchorY() <= GameConfig.GROUND_LINE_Y + 0.001,
                    "overshot the ground line to " + pret.getAnchorY());
        }
    }

    @Test
    @DisplayName("floating enemies settle above the ground by their hover height")
    void floatingSettlesAboveGround() {
        Enemy ahp = onApproach(EnemyType.AHP, "mist", 1, 4.0);

        for (int i = 0; i < 2000; i++) {
            ahp.update();
        }
        assertEquals(GameConfig.GROUND_LINE_Y - EnemyType.AHP.getHoverHeight(),
                ahp.getAnchorY(), 0.001);
    }

    @Test
    @DisplayName("flanking enemies enter level, with no vertical drift")
    void flankRouteIsPurelyHorizontal() {
        Enemy enemy = onFlank(EnemyType.BEISACH, "ash", 1, 2.0);
        double startY = enemy.getAnchorY();

        assertEquals(GameConfig.GROUND_LINE_Y, startY, 0.001,
                "a ground flanker starts already on the plaza");

        for (int i = 0; i < 100; i++) {
            enemy.update();
            assertEquals(startY, enemy.getAnchorY(), 0.001,
                    "flank routes must not drift vertically");
        }
    }

    @Test
    @DisplayName("flying flankers cross at hover altitude, not on the ground")
    void airFlankStaysAirborne() {
        Enemy ahp = onFlank(EnemyType.AHP, "mist", 1, 2.0);

        double expected = GameConfig.GROUND_LINE_Y - EnemyType.AHP.getHoverHeight();
        assertEquals(expected, ahp.getAnchorY(), 0.001);

        for (int i = 0; i < 100; i++) {
            ahp.update();
        }
        assertEquals(expected, ahp.getAnchorY(), 0.001,
                "a flying flanker must stay at hover altitude");
    }

    @Test
    @DisplayName("flanking enemies are drawn at full size the whole way")
    void flankRouteIsAlwaysFullSize() {
        Enemy enemy = onFlank(EnemyType.YEAK, "temple", 1, 2.0);

        for (int i = 0; i < 200; i++) {
            enemy.update();
            assertEquals(1.0, enemy.depthScale(), 0.001,
                    "flank spawns enter on the near plane, so no depth shrink");
        }
    }

    @Test
    @DisplayName("every enemy type gets a route matching its category")
    void routesMatchCategory() {
        for (EnemyType type : EnemyType.values()) {
            for (ApproachPath path : ApproachPath.forBehaviour(type.getGroundBehavior())) {
                assertEquals(!type.isGrounded(), path.isAirborne(),
                        type.getDisplayName() + " was given route " + path);
            }
        }
    }

    @Test
    @DisplayName("enemies travel on an exact 45-degree diagonal")
    void travelsOnDiagonal() {
        Enemy enemy = onApproach(EnemyType.BEISACH, "ash", 1, 2.0);
        double x0 = enemy.getX();
        double y0 = enemy.getAnchorY();

        for (int i = 0; i < 50; i++) {
            enemy.update();
        }

        double dx = enemy.getX() - x0;
        double dy = enemy.getAnchorY() - y0;

        assertTrue(dx > 0, "should advance horizontally");
        assertEquals(dx, dy, 0.001,
                "equal horizontal and vertical travel is what makes it 45 degrees");
    }

    @Test
    @DisplayName("enemies converge on the temple from both sides")
    void convergesFromBothSides() {
        Enemy fromLeft = onApproach(EnemyType.BEISACH, "ash", 1, 2.0);
        Enemy fromRight = onApproach(EnemyType.BEISACH, "owl", -1, 2.0);

        assertTrue(fromLeft.getX() < GameConfig.TEMPLE_CENTER_X);
        assertTrue(fromRight.getX() > GameConfig.TEMPLE_CENTER_X);

        for (int i = 0; i < 60; i++) {
            fromLeft.update();
            fromRight.update();
        }

        assertTrue(fromLeft.getX() > GameConfig.TEMPLE_CENTER_X - 400,
                "left spawn should close on the temple");
        assertTrue(fromRight.getX() < GameConfig.TEMPLE_CENTER_X + 400,
                "right spawn should close on the temple");
    }

    @Test
    @DisplayName("depth scale grows from the minimum to full size")
    void depthScaleGrowsToFullSize() {
        Enemy enemy = onApproach(EnemyType.YEAK, "temple", 1, 3.0);

        assertEquals(GameConfig.DEPTH_SCALE_MIN, enemy.depthScale(), 0.001,
                "should start at the far-away size");

        double previous = enemy.depthScale();
        for (int i = 0; i < 2000; i++) {
            enemy.update();
            assertTrue(enemy.depthScale() >= previous - 0.0001, "scale must not shrink");
            previous = enemy.depthScale();
        }
        assertEquals(1.0, enemy.depthScale(), 0.001, "should reach full size");
    }

    @Test
    @DisplayName("full size is reached before the breach point, not after")
    void reachesFullSizeBeforeBreaching() {
        Enemy enemy = onApproach(EnemyType.BEISACH, "ash", 1, 2.0);

        int guard = 0;
        while (!enemy.hasBreached() && guard++ < 20_000) {
            enemy.update();
        }

        assertTrue(enemy.hasBreached(), "should reach the temple");
        assertEquals(1.0, enemy.depthScale(), 0.001,
                "an enemy must be drawn at full size by the time it breaches");
    }

    @Test
    @DisplayName("reaching the temple centre counts as a breach")
    void breachesAtTempleCenter() {
        Enemy enemy = onApproach(EnemyType.BEISACH, "ash", 1, 4.0);
        assertFalse(enemy.hasBreached(), "should not breach at the spawn puff");

        int guard = 0;
        while (!enemy.hasBreached() && guard++ < 20_000) {
            enemy.update();
        }

        assertTrue(enemy.hasBreached());
        assertTrue(Math.abs(enemy.getX() - GameConfig.TEMPLE_CENTER_X)
                <= GameConfig.BREACH_RADIUS);
    }

    @Test
    @DisplayName("a defeated enemy stops counting as a breach threat")
    void defeatedEnemyCannotBreach() {
        // A run of zero puts the enemy exactly on the temple.
        Enemy enemy = new Enemy(EnemyType.BEISACH, ApproachPath.GROUND_FLANK, "ash", 0, 1, 1.0);
        assertTrue(enemy.hasBreached());

        enemy.defeat();
        assertFalse(enemy.hasBreached(), "a dying enemy must not also cost a life");
    }

    @Test
    @DisplayName("defeated enemies expire only after the animation completes")
    void defeatedEnemiesExpireAfterAnimation() {
        Enemy enemy = onApproach(EnemyType.BEISACH, "ash", 1, 1.0);
        enemy.defeat();

        assertFalse(enemy.isExpired(GameConfig.DEFEAT_ANIMATION_TICKS));
        for (int i = 0; i < GameConfig.DEFEAT_ANIMATION_TICKS; i++) {
            enemy.update();
        }
        assertTrue(enemy.isExpired(GameConfig.DEFEAT_ANIMATION_TICKS));
    }

    // ---- ranged attacks ----------------------------------------------------

    @Test
    @DisplayName("melee types never enter an attack phase")
    void meleeTypesNeverAttack() {
        Enemy beisach = onApproach(EnemyType.BEISACH, "ash", 1, 1.0);

        for (int i = 0; i < 3000; i++) {
            beisach.update();
            assertEquals(AttackPhase.NONE, beisach.getAttackPhase(),
                    "Beisach cannot throw and must never wind up");
            assertFalse(beisach.isProjectileDue());
        }
    }

    @Test
    @DisplayName("Yeak winds up, releases exactly one projectile, then recovers")
    void yeakThrowsThroughFullCycle() {
        Enemy yeak = onApproach(EnemyType.YEAK, "temple", 1, 0.6);

        boolean sawWindup = false;
        boolean sawRelease = false;
        boolean sawRecover = false;
        int projectilesDue = 0;

        for (int i = 0; i < 3000; i++) {
            yeak.update();
            switch (yeak.getAttackPhase()) {
                case WINDUP -> sawWindup = true;
                case RELEASE -> sawRelease = true;
                case RECOVER -> sawRecover = true;
                case NONE -> { }
            }
            if (yeak.isProjectileDue()) {
                projectilesDue++;
            }
        }

        assertTrue(sawWindup, "should telegraph with a windup");
        assertTrue(sawRelease, "should reach the release");
        assertTrue(sawRecover, "should settle back through recovery");
        assertTrue(projectilesDue >= 1, "should have thrown at least once");
    }

    @Test
    @DisplayName("a throwing enemy plants its feet instead of walking through the animation")
    void doesNotWalkWhileThrowing() {
        Enemy yeak = onApproach(EnemyType.YEAK, "temple", 1, 0.6);

        for (int i = 0; i < 3000; i++) {
            double beforeX = yeak.getX();
            yeak.update();
            if (yeak.getAttackPhase() != AttackPhase.NONE) {
                assertEquals(beforeX, yeak.getX(), 0.001,
                        "must not slide forward mid-throw");
            }
        }
    }

    @Test
    @DisplayName("constructor rejects invalid configuration")
    void constructorValidates() {
        assertThrows(IllegalArgumentException.class,
                () -> new Enemy(null, ApproachPath.GROUND_FLANK, "word", 100, 1, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new Enemy(EnemyType.BEISACH, null, "word", 100, 1, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new Enemy(EnemyType.BEISACH, ApproachPath.GROUND_FLANK, null, 100, 1, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new Enemy(EnemyType.BEISACH, ApproachPath.GROUND_FLANK, "", 100, 1, 1.0));
    }
}
