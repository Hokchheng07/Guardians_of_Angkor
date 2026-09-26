package com.guardiansofangkor.util;

import java.text.BreakIterator;

/**
 * Counts <em>visual</em> characters rather than Java chars.
 *
 * <p>Dev brief Section 5.1: a single Khmer character is frequently several
 * codepoints — a base consonant plus subscript and vowel marks. Using
 * {@link String#length()} to bucket Khmer words into difficulty tiers puts short
 * words in the boss tier. {@link BreakIterator#getCharacterInstance()} walks
 * grapheme cluster boundaries, which is correct for both scripts.
 *
 * <p>Example: "ស្រុក" (srok) is 5 Java chars but 2 grapheme clusters.
 */
public final class GraphemeCounter {

    private GraphemeCounter() {
        // Utility class — not instantiable.
    }

    /** Number of user-perceived characters in {@code text}. Null-safe. */
    public static int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        BreakIterator it = BreakIterator.getCharacterInstance();
        it.setText(text);

        int count = 0;
        int start = it.first();
        for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next()) {
            count++;
        }
        return count;
    }

    /**
     * True when {@code text} has between {@code min} and {@code max} visual
     * characters inclusive.
     */
    public static boolean isWithin(String text, int min, int max) {
        int n = count(text);
        return n >= min && n <= max;
    }
}
