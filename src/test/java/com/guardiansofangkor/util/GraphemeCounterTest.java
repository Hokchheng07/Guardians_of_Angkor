package com.guardiansofangkor.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GraphemeCounter — Khmer-safe length")
class GraphemeCounterTest {

    @Test
    @DisplayName("counts ASCII the same as String.length")
    void asciiMatchesStringLength() {
        assertEquals(6, GraphemeCounter.count("temple"));
        assertEquals(3, GraphemeCounter.count("cat"));
    }

    @Test
    @DisplayName("null and empty are zero")
    void nullAndEmptyAreZero() {
        assertEquals(0, GraphemeCounter.count(null));
        assertEquals(0, GraphemeCounter.count(""));
    }

    @Test
    @DisplayName("Khmer clusters count as one visual character each")
    void khmerClustersCountAsOne() {
        // "ស្រុក" (srok) — 5 Java chars, but far fewer visual characters.
        String srok = "ស្រុក";

        assertEquals(5, srok.length(), "sanity: this really is 5 Java chars");
        assertTrue(GraphemeCounter.count(srok) < srok.length(),
                "Khmer must count fewer clusters than chars, or tiers misclassify");
    }

    @Test
    @DisplayName("a short Khmer word does not land in the boss tier")
    void shortKhmerWordStaysInLowTier() {
        String srok = "ស្រុក";

        // Pret (heavy) demands 8+ characters. Counting Java chars would wrongly
        // qualify this short word for the heaviest tier.
        assertFalse(GraphemeCounter.isWithin(srok, 8, 12),
                "grapheme counting must keep short Khmer words out of the heavy tier");
        assertTrue(GraphemeCounter.isWithin(srok, 2, 5),
                "it belongs in a low tier");
    }

    @Test
    @DisplayName("isWithin is inclusive at both bounds")
    void isWithinIsInclusive() {
        assertTrue(GraphemeCounter.isWithin("cat", 3, 5));
        assertTrue(GraphemeCounter.isWithin("stone", 3, 5));
        assertFalse(GraphemeCounter.isWithin("ox", 3, 5));
        assertFalse(GraphemeCounter.isWithin("monument", 3, 5));
    }
}
