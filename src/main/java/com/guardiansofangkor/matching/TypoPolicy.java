package com.guardiansofangkor.matching;

/**
 * What the input buffer does when the player types something that matches no
 * remaining candidate. The dev brief (Section 4) leaves this as an explicit
 * design choice, so it is configurable rather than hardcoded.
 */
public enum TypoPolicy {

    /**
     * Keep the last valid prefix and simply reject the offending character.
     * Forgiving — the player can carry on from where they were.
     */
    KEEP_PREFIX,

    /**
     * Clear the buffer entirely, forcing the player to restart the word.
     * Punishing — makes accuracy matter more.
     */
    RESET
}
