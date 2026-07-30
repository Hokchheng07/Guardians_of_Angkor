package com.guardiansofangkor.matching;

import java.util.Collections;
import java.util.List;

/**
 * Owns the "what am I currently typing at?" state for a run.
 *
 * <p>Implements the live-filtering candidate approach from dev brief Section 4:
 * on every buffer change the full active target list is re-filtered by prefix.
 * A target is only locked once the prefix identifies exactly one of them, so the
 * classic "can" vs "cat" ambiguity can never commit to the wrong enemy early.
 *
 * <p>One edge case that isn't obvious from Section 4 alone: a target's word can
 * itself be a prefix of another target's word (e.g. "car" and "cartel" on
 * screen together). The moment the buffer exactly equals "car", that target is
 * complete and is defeated immediately — even though "cartel" still shares the
 * prefix and remains active for the player to finish separately.
 *
 * <p>Projectiles (Phase 5) are passed as the priority list and are checked
 * before enemies, because they are time-critical and must preempt as the active
 * typing target.
 *
 * <p>The API is driven by the <em>whole current buffer</em> rather than single
 * characters. This is deliberate: Khmer input arrives from a DocumentListener as
 * multi-codepoint edits, so a char-by-char API would break on it (Section 5.1).
 */
public class TargetResolver {

    private final TypoPolicy typoPolicy;

    /** The last buffer that successfully matched at least one target. */
    private String validBuffer = "";

    private WordTarget lockedTarget;
    private List<WordTarget> highlighted = Collections.emptyList();

    /** Running counts for the HUD's accuracy readout (Phase 4). */
    private int correctInputs;
    private int typoCount;

    public TargetResolver() {
        this(TypoPolicy.KEEP_PREFIX);
    }

    public TargetResolver(TypoPolicy typoPolicy) {
        this.typoPolicy = typoPolicy == null ? TypoPolicy.KEEP_PREFIX : typoPolicy;
    }

    /**
     * Re-resolves against the current buffer.
     *
     * @param typedSoFar the full contents of the input field right now
     * @param projectiles time-critical targets, checked first; may be empty
     * @param enemies     standard targets, checked only if no projectile matches
     * @return an immutable snapshot describing what the caller should do next
     */
    public ResolveResult submit(String typedSoFar,
                                List<? extends WordTarget> projectiles,
                                List<? extends WordTarget> enemies) {
        return submit(typedSoFar, projectiles, Collections.emptyList(), enemies);
    }

    /**
     * Re-resolves against the current buffer, with power-up pickups sitting
     * between the two existing tiers.
     *
     * <p>The ordering is by time budget, shortest first. A bolt lands in a
     * second or two, a dropped boon fades in seven, an enemy takes as long as it
     * takes to walk — so a prefix that could mean any of them should mean the
     * one about to disappear. Putting pickups above enemies is also what makes
     * collecting one a real decision: reaching for a boon breaks off whatever
     * word you were part-way through.
     *
     * @param pickups power-up drops waiting to be claimed; may be empty
     */
    public ResolveResult submit(String typedSoFar,
                                List<? extends WordTarget> projectiles,
                                List<? extends WordTarget> pickups,
                                List<? extends WordTarget> enemies) {

        String typed = typedSoFar == null ? "" : typedSoFar;

        if (typed.isEmpty()) {
            validBuffer = "";
            lockedTarget = null;
            highlighted = Collections.emptyList();
            return ResolveResult.empty();
        }

        // Each tier preempts the ones below it — only fall through when nothing
        // in the higher list matches this prefix.
        List<WordTarget> matches = widen(WordMatcher.candidates(projectiles, typed));
        if (matches.isEmpty()) {
            matches = widen(WordMatcher.candidates(pickups, typed));
        }
        if (matches.isEmpty()) {
            matches = widen(WordMatcher.candidates(enemies, typed));
        }

        if (matches.isEmpty()) {
            typoCount++;
            if (typoPolicy == TypoPolicy.RESET) {
                validBuffer = "";
            }
            lockedTarget = null;
            highlighted = Collections.emptyList();
            return ResolveResult.typo(validBuffer);
        }

        // The input was accepted, so this becomes the new known-good buffer.
        validBuffer = typed;
        correctInputs++;

        if (matches.size() == 1) {
            WordTarget only = matches.get(0);
            lockedTarget = only;
            highlighted = List.of(only);
            if (WordMatcher.isComplete(only, typed)) {
                return ResolveResult.completed(only, typed);
            }
            return ResolveResult.locked(only, typed);
        }

        // More than one candidate still shares this prefix — but one of them
        // ("car") can itself be a complete word while another ("cartel") is
        // merely using it as a prefix. That target is done the instant its
        // exact word is typed; the still-typing target(s) are untouched and
        // stay on the field for the player to finish separately.
        WordTarget exactMatch = null;
        int exactCount = 0;
        for (WordTarget candidate : matches) {
            if (WordMatcher.isComplete(candidate, typed)) {
                exactMatch = candidate;
                exactCount++;
            }
        }
        if (exactCount == 1) {
            lockedTarget = exactMatch;
            highlighted = List.of(exactMatch);
            return ResolveResult.completed(exactMatch, typed);
        }

        // Still genuinely ambiguous — highlight everything, commit to nothing.
        lockedTarget = null;
        highlighted = List.copyOf(matches);
        return ResolveResult.ambiguous(matches, typed);
    }

    /**
     * Clears typing state. Call after a word is completed and its target is
     * removed from the field, or when a wave ends.
     */
    public void reset() {
        validBuffer = "";
        lockedTarget = null;
        highlighted = Collections.emptyList();
    }

    /** Clears typing state <em>and</em> the accuracy counters. For a new run. */
    public void resetAll() {
        reset();
        correctInputs = 0;
        typoCount = 0;
    }

    public String getValidBuffer() {
        return validBuffer;
    }

    /** The currently locked target, or null while still ambiguous. */
    public WordTarget getLockedTarget() {
        return lockedTarget;
    }

    /** Every target the renderer should highlight this frame. Never null. */
    public List<WordTarget> getHighlighted() {
        return Collections.unmodifiableList(highlighted);
    }

    public TypoPolicy getTypoPolicy() {
        return typoPolicy;
    }

    /**
     * Records a keystroke resolved somewhere other than here.
     *
     * <p>The final boss is typed against a paragraph rather than against a
     * prefix-matched target, so it bypasses {@link #submit}. Without this the
     * HUD's accuracy readout would freeze for the whole fight, which reads as a
     * broken stat rather than as a different mechanic.
     *
     * @param correct true for an accepted keystroke, false for a mistype
     */
    public void noteExternalInput(boolean correct) {
        if (correct) {
            correctInputs++;
        } else {
            typoCount++;
        }
    }

    public int getCorrectInputs() {
        return correctInputs;
    }

    public int getTypoCount() {
        return typoCount;
    }

    /**
     * Accuracy as a 0.0-1.0 ratio for the HUD. Returns 1.0 before any input,
     * so a fresh run does not display 0% accuracy.
     */
    public double getAccuracy() {
        int total = correctInputs + typoCount;
        return total == 0 ? 1.0 : (double) correctInputs / total;
    }

    /** Widens {@code List<? extends WordTarget>} to {@code List<WordTarget>}. */
    private static List<WordTarget> widen(List<? extends WordTarget> source) {
        return (source == null || source.isEmpty())
                ? Collections.emptyList()
                : List.copyOf(source);
    }
}
