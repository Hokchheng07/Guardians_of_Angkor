package com.guardiansofangkor.entities;

/**
 * Stages of a ranged enemy's throw.
 *
 * <p>The phases exist so a convincing attack can be built from a single static
 * sprite: anticipation and follow-through carry the read, not drawn poses. The
 * renderer maps each phase to a lean angle and a lunge offset. When proper
 * pose art arrives, the sprite swap plugs into these same phases and the timing
 * does not change.
 */
public enum AttackPhase {

    /** Walking normally, no attack in progress. */
    NONE,

    /** Leaning back, arm cocked. The telegraph that gives the player warning. */
    WINDUP,

    /** The snap forward. The projectile is spawned on the first tick of this. */
    RELEASE,

    /** Settling back to neutral. */
    RECOVER;

    /** Ticks spent in each phase. */
    public int durationTicks() {
        return switch (this) {
            case WINDUP -> 20;
            case RELEASE -> 5;
            case RECOVER -> 16;
            case NONE -> 0;
        };
    }
}
