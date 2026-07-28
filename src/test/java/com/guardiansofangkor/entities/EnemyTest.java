package com.guardiansofangkor.entities;

import com.guardiansofangkor.util.GameConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Enemy — marching and anchoring")
class EnemyTest {

    @Test
    @DisplayName("grounded enemies anchor exactly on the ground line")
    void groundedAnchorsOnGroundLine() {
        Enemy yeak = new Enemy(EnemyType.YEAK, "temple", 0, 1, 1.0);

        assertEquals(GameConfig.GROUND_LINE_Y, yeak.getAnchorY(), 0.001,
                "a legged monster's feet must sit on the plaza");
    }

    @Test
    @DisplayName("floating enemies anchor above the ground by their hover height")
    void floatingAnchorsAboveGround() {
        Enemy ahp = new Enemy(EnemyType.AHP, "mist", 0, 1, 1.0);

        assertEquals(GameConfig.GROUND_LINE_Y - EnemyType.AHP.getHoverHeight(),
                ahp.getAnchorY(), 0.001);
        assertTrue(ahp.getAnchorY() < GameConfig.GROUND_LINE_Y, "should hover above the plaza");
    }

    @Test
    @DisplayName("enemies march horizontally in their spawn direction")
    void marchesHorizontally() {
        Enemy fromLeft = new Enemy(EnemyType.BEISACH, "ash", 0, 1, 2.0);
        Enemy fromRight = new Enemy(EnemyType.BEISACH, "owl", 1000, -1, 2.0);

        double leftStart = fromLeft.getX();
        double rightStart = fromRight.getX();
        fromLeft.update();
        fromRight.update();

        assertTrue(fromLeft.getX() > leftStart, "left spawn should march rightward");
        assertTrue(fromRight.getX() < rightStart, "right spawn should march leftward");
    }

    @Test
    @DisplayName("anchor Y never changes as an enemy marches")
    void anchorIsStableWhileMarching() {
        Enemy pret = new Enemy(EnemyType.PRET, "monument", 0, 1, 2.0);
        double anchor = pret.getAnchorY();

        for (int i = 0; i < 200; i++) {
            pret.update();
        }
        assertEquals(anchor, pret.getAnchorY(), 0.001,
                "grounded monsters must not drift vertically while walking");
    }

    @Test
    @DisplayName("reaching the temple centre counts as a breach")
    void breachesAtTempleCenter() {
        Enemy enemy = new Enemy(EnemyType.BEISACH, "ash", 0, 1, 4.0);
        assertFalse(enemy.hasBreached(), "should not breach at the spawn edge");

        int guard = 0;
        while (!enemy.hasBreached() && guard++ < 10_000) {
            enemy.update();
        }

        assertTrue(enemy.hasBreached(), "should eventually reach the temple");
        assertTrue(Math.abs(enemy.getX() - GameConfig.TEMPLE_CENTER_X)
                <= GameConfig.BREACH_RADIUS);
    }

    @Test
    @DisplayName("a defeated enemy stops counting as a breach threat")
    void defeatedEnemyCannotBreach() {
        Enemy enemy = new Enemy(EnemyType.BEISACH, "ash", GameConfig.TEMPLE_CENTER_X, 1, 1.0);
        assertTrue(enemy.hasBreached());

        enemy.defeat();
        assertFalse(enemy.hasBreached(), "a dying enemy must not also cost a life");
    }

    @Test
    @DisplayName("defeated enemies expire only after the animation completes")
    void defeatedEnemiesExpireAfterAnimation() {
        Enemy enemy = new Enemy(EnemyType.BEISACH, "ash", 0, 1, 1.0);
        enemy.defeat();

        assertFalse(enemy.isExpired(GameConfig.DEFEAT_ANIMATION_TICKS));
        for (int i = 0; i < GameConfig.DEFEAT_ANIMATION_TICKS; i++) {
            enemy.update();
        }
        assertTrue(enemy.isExpired(GameConfig.DEFEAT_ANIMATION_TICKS));
    }

    @Test
    @DisplayName("constructor rejects invalid configuration")
    void constructorValidates() {
        assertThrows(IllegalArgumentException.class,
                () -> new Enemy(null, "word", 0, 1, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new Enemy(EnemyType.BEISACH, null, 0, 1, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new Enemy(EnemyType.BEISACH, "", 0, 1, 1.0));
    }
}
