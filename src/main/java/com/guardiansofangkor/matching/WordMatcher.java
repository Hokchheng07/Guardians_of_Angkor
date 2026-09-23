package com.guardiansofangkor.matching;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure prefix-matching helpers. No game state, no side effects, no rendering —
 * everything here is a function of its arguments, which is what makes the
 * game's highest-risk logic cheap to unit-test.
 *
 * <p>See dev brief Section 4: targeting is prefix-based and never pre-locked.
 */
public final class WordMatcher {

    private WordMatcher() {
        // Utility class — not instantiable.
    }

    /**
     * Filters {@code targets} down to those that are active and whose word
     * starts with {@code typedSoFar}.
     *
     * <p>An empty or null {@code typedSoFar} matches every active target, which
     * is the correct "nothing typed yet, everything is still a candidate" state.
     *
     * <p>Note on Khmer: {@link String#startsWith} compares UTF-16 code units,
     * which is correct for prefix testing because typed input arrives in the
     * same encoding as the stored word. Grapheme clusters only matter for
     * <em>length/difficulty classification</em> (Phase 9), not for prefixing.
     *
     * @return a new mutable list; never null
     */
    public static <T extends WordTarget> List<T> candidates(List<T> targets, String typedSoFar) {
        if (targets == null || targets.isEmpty()) {
            return new ArrayList<>();
        }
        String prefix = typedSoFar == null ? "" : typedSoFar;

        List<T> matches = new ArrayList<>();
        for (T target : targets) {
            if (target == null || !target.isActive()) {
                continue;
            }
            String word = target.getWord();
            if (word != null && word.startsWith(prefix)) {
                matches.add(target);
            }
        }
        return matches;
    }

    /**
     * True when {@code typed} is the complete word for {@code target} — i.e. the
     * player has finished it, not merely matched a prefix of it.
     */
    public static boolean isComplete(WordTarget target, String typed) {
        if (target == null || typed == null || typed.isEmpty()) {
            return false;
        }
        return typed.equals(target.getWord());
    }

    /**
     * Longest prefix shared by every word in {@code targets}. Useful for the
     * renderer when highlighting several ambiguous candidates at once — the
     * shared head can be drawn as "already typed" on all of them.
     *
     * @return the shared prefix, or an empty string if there is none
     */
    public static String commonPrefix(List<? extends WordTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return "";
        }
        String shared = null;
        for (WordTarget target : targets) {
            if (target == null || target.getWord() == null) {
                continue;
            }
            String word = target.getWord();
            if (shared == null) {
                shared = word;
                continue;
            }
            int limit = Math.min(shared.length(), word.length());
            int i = 0;
            while (i < limit && shared.charAt(i) == word.charAt(i)) {
                i++;
            }
            shared = shared.substring(0, i);
            if (shared.isEmpty()) {
                return "";
            }
        }
        return shared == null ? "" : shared;
    }

    /**
     * Defensive copy helper so callers can hand out candidate lists without
     * letting the renderer mutate matcher output.
     */
    public static <T> List<T> unmodifiable(List<T> list) {
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }
}
