package com.guardiansofangkor.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Projectile — flight and interception")
class ProjectileTest {

    private static Projectile bolt(int flightTicks) {
        return new Projectile("ka", 100, 300, 640, 600, flightTicks);
    }

    @Test
    @DisplayName("travels from the throwing hand to the temple")
    void travelsToTarget() {
        Projectile projectile = bolt(40);

        assertEquals(100, projectile.getX(), 0.001, "starts at the hand");

        for (int i = 0; i < 40; i++) {
            projectile.update();
        }
        assertEquals(640, projectile.getX(), 0.001, "ends at the temple");
    }

    @Test
    @DisplayName("flies on an arc rather than a straight line")
    void fliesOnAnArc() {
        Projectile projectile = bolt(40);

        for (int i = 0; i < 20; i++) {
            projectile.update();
        }
        // Halfway along, a straight line would sit at the midpoint Y of 450.
        // The arc must lift it above that.
        assertTrue(projectile.getY() < 450,
                "a dead-straight bolt reads as a UI element, not a thrown object");
    }

    @Test
    @DisplayName("landing is reported exactly once, at the end of the flight")
    void landsAtEndOfFlight() {
        Projectile projectile = bolt(10);

        for (int i = 0; i < 9; i++) {
            projectile.update();
            assertFalse(projectile.hasJustLanded(), "should still be in the air");
        }
        projectile.update();
        assertTrue(projectile.hasJustLanded());
        assertFalse(projectile.isActive(), "a landed bolt is no longer typeable");
    }

    @Test
    @DisplayName("a landed bolt charges a life once, not on every fade tick")
    void landingIsChargedOnlyOnce() {
        Projectile projectile = bolt(5);

        int landedTicks = 0;
        for (int i = 0; i < 60; i++) {
            projectile.update();
            if (projectile.hasJustLanded()) {
                landedTicks++;
            }
        }

        assertEquals(1, landedTicks,
                "a sticky landed flag would drain the whole run from one bolt");
        assertTrue(projectile.hasLanded(), "it should still know it landed, for the impact");
    }

    @Test
    @DisplayName("interception stops it before it lands")
    void interceptionPreventsLanding() {
        Projectile projectile = bolt(40);
        for (int i = 0; i < 10; i++) {
            projectile.update();
        }

        projectile.intercept();

        for (int i = 0; i < 60; i++) {
            projectile.update();
            assertFalse(projectile.hasJustLanded(),
                    "an intercepted bolt must never land");
        }
        assertFalse(projectile.hasLanded());
        assertFalse(projectile.isActive());
    }

    @Test
    @DisplayName("expires only after its fade animation completes")
    void expiresAfterFade() {
        Projectile projectile = bolt(10);
        projectile.intercept();

        assertFalse(projectile.isExpired(15));
        for (int i = 0; i < 15; i++) {
            projectile.update();
        }
        assertTrue(projectile.isExpired(15));
    }

    @Test
    @DisplayName("progress is clamped to one")
    void progressIsClamped() {
        Projectile projectile = bolt(5);
        for (int i = 0; i < 50; i++) {
            projectile.update();
        }
        assertEquals(1.0, projectile.getProgress(), 0.0001);
    }

    @Test
    @DisplayName("rejects an empty word")
    void rejectsEmptyWord() {
        assertThrows(IllegalArgumentException.class,
                () -> new Projectile(null, 0, 0, 1, 1, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new Projectile("", 0, 0, 1, 1, 10));
    }
}
