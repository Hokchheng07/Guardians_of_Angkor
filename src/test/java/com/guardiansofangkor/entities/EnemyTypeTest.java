package com.guardiansofangkor.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EnemyType — roster configuration")
class EnemyTypeTest {

    /** The roster split the team asked for: legs (or coils) stay on the plaza. */
    private static final Set<EnemyType> EXPECTED_GROUNDED = EnumSet.of(
            EnemyType.BEISACH, EnemyType.YEAK, EnemyType.PRET,
            EnemyType.NAGA, EnemyType.KRONG_REAP);

    private static final Set<EnemyType> EXPECTED_FLOATING = EnumSet.of(
            EnemyType.AHP, EnemyType.KMAOCH);

    @Test
    @DisplayName("every legged or coiled monster is grounded")
    void leggedMonstersAreGrounded() {
        for (EnemyType type : EXPECTED_GROUNDED) {
            assertTrue(type.isGrounded(),
                    type.getDisplayName() + " has legs/coils and must stand on the ground");
            assertEquals(0, type.getHoverHeight(),
                    type.getDisplayName() + " is grounded so must not hover");
        }
    }

    @Test
    @DisplayName("only Ahp and Kmaoch float")
    void onlyIntendedMonstersFloat() {
        for (EnemyType type : EXPECTED_FLOATING) {
            assertFalse(type.isGrounded(),
                    type.getDisplayName() + " should float");
            assertTrue(type.getHoverHeight() > 0,
                    type.getDisplayName() + " floats so needs a hover height");
        }
        assertEquals(EnemyType.values().length,
                EXPECTED_GROUNDED.size() + EXPECTED_FLOATING.size(),
                "every roster entry must be classified as grounded or floating");
    }

    @ParameterizedTest
    @EnumSource(EnemyType.class)
    @DisplayName("every type is fully configured, including ones without art yet")
    void everyTypeIsConfigured(EnemyType type) {
        assertFalse(type.getDisplayName().isBlank(), "needs a display name");
        assertFalse(type.getKhmerName().isBlank(), "needs a Khmer name");
        assertTrue(type.getSpritePath().startsWith("/images/"), "needs a sprite path");
        assertTrue(type.getSpritePath().endsWith(".png"), "sprite must be a png");
        assertTrue(type.getTargetHeight() > 0, "needs a positive render height");
        assertTrue(type.getSpeedMultiplier() > 0, "needs a positive speed");
    }

    @ParameterizedTest
    @EnumSource(EnemyType.class)
    @DisplayName("word length tiers are sane and ordered")
    void wordLengthTiersAreSane(EnemyType type) {
        assertTrue(type.getMinWordLength() >= 2,
                type + " min word length should be at least 2");
        assertTrue(type.getMaxWordLength() >= type.getMinWordLength(),
                type + " max must not be below min");
    }

    @Test
    @DisplayName("tier hierarchy holds — bigger tiers render larger")
    void tierHierarchyHolds() {
        assertTrue(EnemyType.KRONG_REAP.getTargetHeight() > EnemyType.NAGA.getTargetHeight(),
                "final boss should out-size the mini-boss");
        assertTrue(EnemyType.PRET.getTargetHeight() > EnemyType.BEISACH.getTargetHeight(),
                "heavy should out-size the common enemy");
        assertTrue(EnemyType.AHP.getSpeedMultiplier() > EnemyType.PRET.getSpeedMultiplier(),
                "the swarm type should be faster than the heavy type");
    }

    @Test
    @DisplayName("sprite paths are unique — no two monsters share art")
    void spritePathsAreUnique() {
        Set<String> seen = new java.util.HashSet<>();
        for (EnemyType type : EnemyType.values()) {
            assertTrue(seen.add(type.getSpritePath()),
                    "duplicate sprite path: " + type.getSpritePath());
        }
    }
}
