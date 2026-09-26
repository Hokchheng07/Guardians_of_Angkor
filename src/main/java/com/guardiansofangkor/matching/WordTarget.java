package com.guardiansofangkor.matching;

/**
 * Anything the player can defeat by typing its word.
 *
 * <p>Implemented by both {@code Enemy} and (from Phase 5) {@code Projectile}.
 * The matching package deliberately depends on this small interface rather than
 * on the entities package, so the resolver can be unit-tested against plain
 * stub objects with no game state attached.
 */
public interface WordTarget {

    /** The word the player must type to defeat this target. Never null. */
    String getWord();

    /**
     * Whether this target is still on the field and typeable. Defeated or
     * off-screen targets return false and are skipped during matching.
     */
    boolean isActive();
}
