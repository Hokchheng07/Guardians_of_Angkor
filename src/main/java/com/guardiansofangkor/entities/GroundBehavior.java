package com.guardiansofangkor.entities;

/**
 * How an enemy sits relative to the temple plaza.
 *
 * <p>The background art only has walkable ground in the bottom band of the
 * screen, so this distinction is what keeps legged monsters visually planted
 * instead of drifting through the sky.
 */
public enum GroundBehavior {

    /**
     * Has legs (or coils) and must stand on the plaza. Its sprite is anchored by
     * the <em>bottom</em> of its content box to the ground line, and it never
     * bobs vertically — feet stay welded to the floor.
     *
     * <p>Beisach, Yeak, Pret, Naga, Krong Reap.
     */
    GROUNDED,

    /**
     * Floats. Its sprite is anchored by its <em>centre</em> to a hover height
     * above the ground, and it bobs on a sine wave to sell that it is airborne.
     *
     * <p>Ahp (a flying head trailing entrails) and Stec Kantoab.
     */
    FLOATING
}
