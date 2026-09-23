package com.guardiansofangkor.matching;

import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of one resolution pass.
 *
 * @param status       what happened this keystroke
 * @param candidates   every target still matching the prefix (empty on TYPO)
 * @param target       the locked target, or null while still ambiguous
 * @param validBuffer  the buffer the input field should now display — on a typo
 *                     this is the last <em>valid</em> prefix, not the rejected text
 */
public record ResolveResult(
        MatchStatus status,
        List<WordTarget> candidates,
        WordTarget target,
        String validBuffer) {

    public ResolveResult {
        candidates = candidates == null
                ? Collections.emptyList()
                : List.copyOf(candidates);
        validBuffer = validBuffer == null ? "" : validBuffer;
    }

    /** Shared no-op result, for callers that must return something inert. */
    public static final ResolveResult EMPTY_RESULT =
            new ResolveResult(MatchStatus.EMPTY, List.of(), null, "");

    static ResolveResult empty() {
        return EMPTY_RESULT;
    }

    static ResolveResult ambiguous(List<? extends WordTarget> candidates, String buffer) {
        return new ResolveResult(
                MatchStatus.AMBIGUOUS, List.copyOf(candidates), null, buffer);
    }

    // The three below are public because the final boss resolves its own typing
    // against a paragraph rather than through TargetResolver, and still has to
    // answer in this shape — that is what lets the input field's typo flash and
    // clear-on-complete keep working for a target the matcher never sees.

    /**
     * @param lastValidBuffer what the input field should fall back to. Empty
     *                        means "clear it", which is how the boss expresses
     *                        a sentence being reset by a mistype.
     */
    public static ResolveResult typo(String lastValidBuffer) {
        return new ResolveResult(MatchStatus.TYPO, List.of(), null, lastValidBuffer);
    }

    public static ResolveResult locked(WordTarget target, String buffer) {
        return new ResolveResult(MatchStatus.LOCKED, List.of(target), target, buffer);
    }

    public static ResolveResult completed(WordTarget target, String buffer) {
        return new ResolveResult(MatchStatus.COMPLETED, List.of(target), target, buffer);
    }

    /** Convenience for the renderer: is there more than one thing to highlight? */
    public boolean isAmbiguous() {
        return status == MatchStatus.AMBIGUOUS;
    }

    /** Convenience for audio/HUD: did the player just finish a word? */
    public boolean isCompleted() {
        return status == MatchStatus.COMPLETED;
    }
}
