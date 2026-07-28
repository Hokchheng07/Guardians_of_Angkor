package com.guardiansofangkor.matching;

/**
 * Outcome of feeding the current typed buffer through the {@link TargetResolver}.
 * The renderer and audio layers switch on this to decide what feedback to show.
 */
public enum MatchStatus {

    /** Buffer is empty — nothing typed, no candidates narrowed. */
    EMPTY,

    /**
     * Several targets still share this prefix (the "can" vs "cat" case).
     * All of them should be highlighted simultaneously; do not lock one.
     */
    AMBIGUOUS,

    /** Exactly one target matches — it is now the active locked target. */
    LOCKED,

    /** The locked target's word has been typed in full; it should be defeated. */
    COMPLETED,

    /** No target matches this input — flash red, do not advance the buffer. */
    TYPO
}
