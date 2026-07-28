package com.guardiansofangkor.matching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WordMatcher — prefix filtering")
class WordMatcherTest {

    @Test
    @DisplayName("empty prefix matches every active target")
    void emptyPrefixMatchesAll() {
        List<StubTarget> targets = List.of(
                new StubTarget("can"), new StubTarget("cat"), new StubTarget("dog"));

        assertEquals(3, WordMatcher.candidates(targets, "").size());
        assertEquals(3, WordMatcher.candidates(targets, null).size());
    }

    @Test
    @DisplayName("shared prefix keeps both 'can' and 'cat' as candidates")
    void sharedPrefixKeepsBoth() {
        List<StubTarget> targets = List.of(
                new StubTarget("can"), new StubTarget("cat"), new StubTarget("dog"));

        List<StubTarget> matches = WordMatcher.candidates(targets, "ca");

        assertEquals(2, matches.size(), "'ca' must not disambiguate can/cat");
    }

    @Test
    @DisplayName("prefix narrows to exactly one once it disambiguates")
    void prefixNarrowsToOne() {
        List<StubTarget> targets = List.of(new StubTarget("can"), new StubTarget("cat"));

        List<StubTarget> matches = WordMatcher.candidates(targets, "cat");

        assertEquals(1, matches.size());
        assertEquals("cat", matches.get(0).getWord());
    }

    @Test
    @DisplayName("inactive targets are excluded")
    void inactiveTargetsExcluded() {
        StubTarget dead = new StubTarget("cat");
        dead.deactivate();
        List<StubTarget> targets = List.of(new StubTarget("can"), dead);

        List<StubTarget> matches = WordMatcher.candidates(targets, "ca");

        assertEquals(1, matches.size());
        assertEquals("can", matches.get(0).getWord());
    }

    @Test
    @DisplayName("no match returns an empty list, never null")
    void noMatchReturnsEmpty() {
        List<StubTarget> targets = List.of(new StubTarget("can"));

        assertTrue(WordMatcher.candidates(targets, "z").isEmpty());
        assertTrue(WordMatcher.candidates(Collections.emptyList(), "a").isEmpty());
        assertTrue(WordMatcher.candidates(null, "a").isEmpty());
    }

    @Test
    @DisplayName("isComplete only fires on the full word, not a prefix")
    void isCompleteRequiresFullWord() {
        StubTarget cat = new StubTarget("cat");

        assertFalse(WordMatcher.isComplete(cat, "ca"));
        assertFalse(WordMatcher.isComplete(cat, ""));
        assertFalse(WordMatcher.isComplete(cat, null));
        assertTrue(WordMatcher.isComplete(cat, "cat"));
    }

    @Test
    @DisplayName("commonPrefix finds the shared head across candidates")
    void commonPrefixAcrossCandidates() {
        List<StubTarget> targets = List.of(
                new StubTarget("cartel"), new StubTarget("carve"), new StubTarget("car"));

        assertEquals("car", WordMatcher.commonPrefix(targets));
    }

    @Test
    @DisplayName("commonPrefix is empty when nothing is shared")
    void commonPrefixEmptyWhenDisjoint() {
        List<StubTarget> targets = List.of(new StubTarget("can"), new StubTarget("dog"));

        assertEquals("", WordMatcher.commonPrefix(targets));
        assertEquals("", WordMatcher.commonPrefix(Collections.emptyList()));
    }

    @Test
    @DisplayName("Khmer prefixes match on codepoints without corruption")
    void khmerPrefixMatching() {
        // "ស្រុក" (srok/village) vs "ស្រី" (srey/woman) — shared leading cluster.
        StubTarget srok = new StubTarget("ស្រុក");
        StubTarget srey = new StubTarget("ស្រី");
        List<StubTarget> targets = List.of(srok, srey);

        assertEquals(2, WordMatcher.candidates(targets, "ស").size());
        assertEquals(1, WordMatcher.candidates(targets, "ស្រុ").size());
        assertTrue(WordMatcher.isComplete(srok, "ស្រុក"));
    }
}
