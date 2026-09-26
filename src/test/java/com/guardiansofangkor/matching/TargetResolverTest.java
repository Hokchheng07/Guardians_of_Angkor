package com.guardiansofangkor.matching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TargetResolver — live candidate filtering (dev brief Section 4)")
class TargetResolverTest {

    private static final List<WordTarget> NO_PROJECTILES = List.of();

    @Test
    @DisplayName("empty buffer resolves to EMPTY with nothing locked")
    void emptyBuffer() {
        TargetResolver resolver = new TargetResolver();
        List<WordTarget> enemies = List.of(new StubTarget("can"));

        ResolveResult result = resolver.submit("", NO_PROJECTILES, enemies);

        assertEquals(MatchStatus.EMPTY, result.status());
        assertNull(resolver.getLockedTarget());
    }

    @Test
    @DisplayName("'ca' stays AMBIGUOUS and locks nothing — the teacher's can/cat case")
    void ambiguousPrefixLocksNothing() {
        TargetResolver resolver = new TargetResolver();
        List<WordTarget> enemies = List.of(new StubTarget("can"), new StubTarget("cat"));

        ResolveResult result = resolver.submit("ca", NO_PROJECTILES, enemies);

        assertEquals(MatchStatus.AMBIGUOUS, result.status());
        assertEquals(2, result.candidates().size());
        assertNull(result.target(), "must not commit to a target while ambiguous");
        assertNull(resolver.getLockedTarget());
    }

    @Test
    @DisplayName("the disambiguating keystroke locks the right target")
    void disambiguationLocksTarget() {
        TargetResolver resolver = new TargetResolver();
        StubTarget can = new StubTarget("can");
        StubTarget cat = new StubTarget("cat");
        List<WordTarget> enemies = List.of(can, cat);

        resolver.submit("ca", NO_PROJECTILES, enemies);
        ResolveResult result = resolver.submit("cat", NO_PROJECTILES, enemies);

        // "cat" is both unique and complete, so it resolves straight to COMPLETED.
        assertEquals(MatchStatus.COMPLETED, result.status());
        assertSame(cat, result.target());
    }

    @Test
    @DisplayName("a word that is itself a prefix of another word completes immediately "
            + "(regression: 'car' getting stuck waiting for 'cartel')")
    void wordThatIsPrefixOfAnotherCompletesImmediately() {
        TargetResolver resolver = new TargetResolver();
        StubTarget car = new StubTarget("car");
        StubTarget cartel = new StubTarget("cartel");
        List<WordTarget> enemies = List.of(car, cartel);

        ResolveResult result = resolver.submit("car", NO_PROJECTILES, enemies);

        assertEquals(MatchStatus.COMPLETED, result.status(),
                "'car' is a complete word and must resolve even though 'cartel' "
                        + "still shares the prefix");
        assertSame(car, result.target());
        assertTrue(cartel.isActive(), "'cartel' must remain untouched, still typeable");
    }

    @Test
    @DisplayName("a unique but unfinished prefix reports LOCKED, not COMPLETED")
    void uniquePrefixLocksBeforeCompletion() {
        TargetResolver resolver = new TargetResolver();
        StubTarget cartel = new StubTarget("cartel");
        List<WordTarget> enemies = List.of(cartel, new StubTarget("dog"));

        ResolveResult result = resolver.submit("car", NO_PROJECTILES, enemies);

        assertEquals(MatchStatus.LOCKED, result.status());
        assertSame(cartel, result.target());
        assertSame(cartel, resolver.getLockedTarget());
    }

    @Test
    @DisplayName("typing the full word reports COMPLETED")
    void fullWordCompletes() {
        TargetResolver resolver = new TargetResolver();
        StubTarget can = new StubTarget("can");
        List<WordTarget> enemies = List.of(can);

        ResolveResult result = resolver.submit("can", NO_PROJECTILES, enemies);

        assertEquals(MatchStatus.COMPLETED, result.status());
        assertTrue(result.isCompleted());
        assertSame(can, result.target());
    }

    @Test
    @DisplayName("KEEP_PREFIX typo policy rejects the char and preserves the last valid buffer")
    void typoKeepsLastValidPrefix() {
        TargetResolver resolver = new TargetResolver(TypoPolicy.KEEP_PREFIX);
        List<WordTarget> enemies = List.of(new StubTarget("can"), new StubTarget("cat"));

        resolver.submit("ca", NO_PROJECTILES, enemies);
        ResolveResult result = resolver.submit("caz", NO_PROJECTILES, enemies);

        assertEquals(MatchStatus.TYPO, result.status());
        assertEquals("ca", result.validBuffer(), "buffer must not advance past a typo");
        assertEquals("ca", resolver.getValidBuffer());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    @DisplayName("RESET typo policy clears the buffer entirely")
    void typoResetClearsBuffer() {
        TargetResolver resolver = new TargetResolver(TypoPolicy.RESET);
        List<WordTarget> enemies = List.of(new StubTarget("can"));

        resolver.submit("ca", NO_PROJECTILES, enemies);
        ResolveResult result = resolver.submit("cz", NO_PROJECTILES, enemies);

        assertEquals(MatchStatus.TYPO, result.status());
        assertEquals("", result.validBuffer());
    }

    @Test
    @DisplayName("projectiles preempt enemies when both match the prefix")
    void projectilesTakePriority() {
        TargetResolver resolver = new TargetResolver();
        StubTarget projectile = new StubTarget("ca");
        StubTarget enemy = new StubTarget("cat");

        ResolveResult result = resolver.submit("c", List.of(projectile), List.of(enemy));

        assertEquals(1, result.candidates().size(),
                "enemy candidates must be ignored while a projectile matches");
        assertSame(projectile, result.target());
    }

    @Test
    @DisplayName("enemies are still reachable when no projectile matches")
    void fallsThroughToEnemies() {
        TargetResolver resolver = new TargetResolver();
        StubTarget projectile = new StubTarget("zz");
        StubTarget enemy = new StubTarget("cat");

        ResolveResult result = resolver.submit("ca", List.of(projectile), List.of(enemy));

        assertEquals(MatchStatus.LOCKED, result.status());
        assertSame(enemy, result.target());
    }

    @Test
    @DisplayName("reset clears typing state but keeps accuracy counters")
    void resetKeepsAccuracyCounters() {
        TargetResolver resolver = new TargetResolver();
        List<WordTarget> enemies = List.of(new StubTarget("can"));

        resolver.submit("ca", NO_PROJECTILES, enemies);
        resolver.submit("cz", NO_PROJECTILES, enemies);
        resolver.reset();

        assertEquals("", resolver.getValidBuffer());
        assertNull(resolver.getLockedTarget());
        assertEquals(1, resolver.getCorrectInputs());
        assertEquals(1, resolver.getTypoCount());

        resolver.resetAll();
        assertEquals(0, resolver.getCorrectInputs());
        assertEquals(0, resolver.getTypoCount());
    }

    @Test
    @DisplayName("accuracy is 1.0 before any input and drops after a typo")
    void accuracyTracking() {
        TargetResolver resolver = new TargetResolver();
        List<WordTarget> enemies = List.of(new StubTarget("can"));

        assertEquals(1.0, resolver.getAccuracy(), 0.0001);

        resolver.submit("c", NO_PROJECTILES, enemies);
        resolver.submit("cz", NO_PROJECTILES, enemies);

        assertEquals(0.5, resolver.getAccuracy(), 0.0001);
    }

    @Test
    @DisplayName("defeated targets drop out of the candidate list mid-word")
    void defeatedTargetsDropOut() {
        TargetResolver resolver = new TargetResolver();
        StubTarget can = new StubTarget("can");
        StubTarget cat = new StubTarget("cat");
        List<WordTarget> enemies = List.of(can, cat);

        ResolveResult before = resolver.submit("ca", NO_PROJECTILES, enemies);
        assertEquals(2, before.candidates().size());

        cat.deactivate();
        ResolveResult after = resolver.submit("ca", NO_PROJECTILES, enemies);

        assertEquals(MatchStatus.LOCKED, after.status());
        assertSame(can, after.target());
    }

    @Test
    @DisplayName("result candidate list is immutable")
    void candidatesAreImmutable() {
        TargetResolver resolver = new TargetResolver();
        List<WordTarget> enemies = List.of(new StubTarget("can"), new StubTarget("cat"));

        ResolveResult result = resolver.submit("ca", NO_PROJECTILES, enemies);
        assertNotNull(result.candidates());

        try {
            result.candidates().add(new StubTarget("dog"));
            org.junit.jupiter.api.Assertions.fail("candidate list should be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // Correct behaviour — the renderer cannot corrupt matcher output.
        }
    }
}
