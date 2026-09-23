package com.guardiansofangkor.entities;

import com.guardiansofangkor.util.GameConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Enemy — diagonal approach, depth and attacks")
class EnemyTest {

    /** Builds an enemy on the descending route appropriate to its category. */
    private static Enemy onApproach(EnemyType type, String word, int direction, double speed) {
        ApproachPath path = type.isGrounded()
                ? ApproachPath.GROUND_DIAGONAL
                : ApproachPath.AIR_DIAGONAL;
        return new Enemy(type, path, word, path.runMin(), direction, speed);
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
    @DisplayName("flying enemies descend on an exact 45-degree diagonal")
    void flyersTravelOnDiagonal() {
        Enemy ahp = onApproach(EnemyType.AHP, "mist", 1, 2.0);
        double x0 = ahp.getX();
        double y0 = ahp.getAnchorY();

        for (int i = 0; i < 50; i++) {
            ahp.update();
        }

        double dx = ahp.getX() - x0;
        double dy = ahp.getAnchorY() - y0;

        assertTrue(dx > 0, "should advance horizontally");
        assertEquals(dx, dy, 0.001,
                "equal horizontal and vertical travel is what makes it 45 degrees");
    }

    @Test
    @DisplayName("walkers drift shallowly and never leave the plaza")
    void walkersStayOnThePlaza() {
        // The plaza is only ~55px deep above the ground line, so a walker's
        // descent has to be far shallower than 45 degrees or its feet end up in
        // the sky above the temple.
        for (EnemyType type : EnemyType.values()) {
            if (!type.isGrounded()) {
                continue;
            }
            Enemy enemy = new Enemy(type, ApproachPath.GROUND_DIAGONAL, "word",
                    ApproachPath.GROUND_DIAGONAL.runMax(), 1, 2.0);

            assertTrue(enemy.getSpawnY() >= GameConfig.PLAZA_TOP_Y,
                    type.getDisplayName() + " spawned above the plaza at y="
                            + Math.round(enemy.getSpawnY()));

            double dx = Math.abs(enemy.getSpawnX() - GameConfig.TEMPLE_CENTER_X);
            double dy = GameConfig.GROUND_LINE_Y - enemy.getSpawnY();
            assertTrue(dy < dx,
                    type.getDisplayName() + " descends too steeply to stay on stone");
            assertTrue(dy > 0, type.getDisplayName() + " should still drift a little");
        }
    }

    @Test
    @DisplayName("walkers barely shrink, flyers shrink a lot")
    void depthShrinkMatchesRoute() {
        assertEquals(GameConfig.GROUND_DEPTH_SCALE_MIN,
                ApproachPath.GROUND_DIAGONAL.depthScaleMin(), 0.001);
        assertEquals(GameConfig.DEPTH_SCALE_MIN,
                ApproachPath.AIR_DIAGONAL.depthScaleMin(), 0.001);
        assertTrue(ApproachPath.GROUND_DIAGONAL.depthScaleMin()
                        > ApproachPath.AIR_DIAGONAL.depthScaleMin(),
                "a walker descending fifty pixels must not shrink like a "
                        + "flyer descending three hundred");
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
        Enemy enemy = onApproach(EnemyType.AHP, "mist", 1, 3.0);

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

    // ---- mini-boss word chains --------------------------------------------

    @Test
    @DisplayName("an ordinary enemy is not chained")
    void ordinaryEnemyIsNotChained() {
        Enemy enemy = onFlank(EnemyType.BEISACH, "ash", 1, 1.0);

        assertFalse(enemy.isChained());
        assertEquals(1, enemy.getChainLength());
        assertFalse(enemy.hasMoreWords(), "one word should be enough to kill it");
    }

    @Test
    @DisplayName("a chained enemy reveals its words in order")
    void chainRevealsWordsInOrder() {
        Enemy naga = new Enemy(EnemyType.NAGA, ApproachPath.GROUND_FLANK,
                List.of("first", "second", "third"),
                GameConfig.FLANK_RUN_MIN, 1, 1.0);

        assertEquals("first", naga.getWord());
        assertTrue(naga.hasMoreWords());

        assertTrue(naga.advanceChain());
        assertEquals("second", naga.getWord());
        assertEquals(1, naga.getChainCleared());

        assertTrue(naga.advanceChain());
        assertEquals("third", naga.getWord());
        assertFalse(naga.hasMoreWords(), "the last word should be the killing one");

        assertFalse(naga.advanceChain(), "there is nothing left to advance to");
    }

    @Test
    @DisplayName("clearing a chain word staggers it instead of killing it")
    void chainWordStaggersRatherThanKills() {
        Enemy naga = new Enemy(EnemyType.NAGA, ApproachPath.GROUND_FLANK,
                List.of("one", "two"), GameConfig.FLANK_RUN_MIN, 1, 2.0);

        naga.advanceChain();

        assertTrue(naga.isActive(), "a mid-chain hit must not kill it");
        assertTrue(naga.isStaggered(), "it should recoil so the hit is readable");

        double heldAt = naga.getX();
        naga.update();
        assertEquals(heldAt, naga.getX(), 0.001,
                "it should hold position while staggered, giving the player a "
                        + "beat to read the next word");
    }

    @Test
    @DisplayName("the stagger wears off and it resumes advancing")
    void staggerWearsOff() {
        Enemy naga = new Enemy(EnemyType.NAGA, ApproachPath.GROUND_FLANK,
                List.of("one", "two"), GameConfig.FLANK_RUN_MIN, 1, 2.0);
        naga.advanceChain();

        for (int i = 0; i < 200; i++) {
            naga.update();
        }

        assertFalse(naga.isStaggered());
        assertTrue(naga.getX() > GameConfig.TEMPLE_CENTER_X - GameConfig.FLANK_RUN_MIN,
                "it should be walking again once the stagger ends");
    }

    @Test
    @DisplayName("every word in a chain is exposed for duplicate checking")
    void chainExposesAllWords() {
        Enemy naga = new Enemy(EnemyType.NAGA, ApproachPath.GROUND_FLANK,
                List.of("alpha", "beta"), GameConfig.FLANK_RUN_MIN, 1, 1.0);

        assertEquals(List.of("alpha", "beta"), naga.getAllWords());
    }

    @Test
    @DisplayName("defeating a chained enemy clears its stagger")
    void defeatClearsStagger() {
        Enemy naga = new Enemy(EnemyType.NAGA, ApproachPath.GROUND_FLANK,
                List.of("one", "two"), GameConfig.FLANK_RUN_MIN, 1, 1.0);
        naga.advanceChain();
        naga.defeat();

        assertFalse(naga.isStaggered(),
                "a dying enemy must not hold a stagger that blocks its fade");
        assertFalse(naga.isActive());
    }

    @Test
    @DisplayName("constructor rejects invalid configuration")
    void constructorValidates() {
        assertThrows(IllegalArgumentException.class,
                () -> new Enemy(null, ApproachPath.GROUND_FLANK, "word", 100, 1, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new Enemy(EnemyType.BEISACH, null, "word", 100, 1, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new Enemy(EnemyType.BEISACH, ApproachPath.GROUND_FLANK,
                        (String) null, 100, 1, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new Enemy(EnemyType.BEISACH, ApproachPath.GROUND_FLANK, "", 100, 1, 1.0));
    }

    @Test
    @DisplayName("the chain constructor rejects an empty or holey word list")
    void chainConstructorValidates() {
        assertThrows(IllegalArgumentException.class,
                () -> new Enemy(EnemyType.NAGA, ApproachPath.GROUND_FLANK,
                        (List<String>) null, 100, 1, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new Enemy(EnemyType.NAGA, ApproachPath.GROUND_FLANK,
                        List.of(), 100, 1, 1.0));

        // A null inside the list would only blow up later, when that word was
        // finally revealed — catch it at construction instead.
        List<String> holey = new ArrayList<>();
        holey.add("alpha");
        holey.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> new Enemy(EnemyType.NAGA, ApproachPath.GROUND_FLANK,
                        holey, 100, 1, 1.0));
    }
}
