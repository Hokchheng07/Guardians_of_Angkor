package com.guardiansofangkor.util;

/**
 * Central place for tuning constants.
 *
 * <p>Per the dev brief (Section 7) the play area is a hardcoded 1280x720 rather
 * than a dynamically stretched panel — this keeps spawn positions and HUD layout
 * math simple and predictable.
 */
public final class GameConfig {

    /** Play area width in pixels. */
    public static final int SCREEN_WIDTH = 1280;

    /** Play area height in pixels. */
    public static final int SCREEN_HEIGHT = 720;

    /** Target frames (ticks) per second for the Swing timer game loop. */
    public static final int TARGET_FPS = 60;

    /** Milliseconds between ticks, derived from {@link #TARGET_FPS}. */
    public static final int TICK_INTERVAL_MS = 1000 / TARGET_FPS;

    /** Lives the player starts a run with. */
    public static final int STARTING_LIVES = 3;

    /**
     * How long a defeated enemy lingers to play its death animation before
     * being culled.
     *
     * <p>Shared deliberately: GameState uses it to decide when to remove the
     * enemy, GamePanel uses it to drive the fade. If these two ever disagree the
     * sprite either pops out mid-fade or lingers as an invisible ghost.
     */
    public static final int DEFEAT_ANIMATION_TICKS = 15;

    /** How long a correct keystroke flashes an enemy white. */
    public static final int HIT_FLASH_TICKS = 4;

    /** How long the input bar flashes red after a typo. */
    public static final int TYPO_FLASH_TICKS = 12;

    // ---- Play field geometry ---------------------------------------------
    //
    // The background art has a temple causeway across the middle and a dark
    // plaza below it. Grounded enemies march along that plaza, so the ground
    // line is measured from the art, not picked arbitrarily.

    /** Y coordinate where grounded enemies' feet rest. */
    public static final int GROUND_LINE_Y = 660;

    /**
     * Horizontal centre of the temple entrance — what the enemies are marching
     * toward. Reaching it costs the player a life.
     */
    public static final int TEMPLE_CENTER_X = SCREEN_WIDTH / 2;

    /**
     * How close to {@link #TEMPLE_CENTER_X} an enemy must get to count as having
     * breached the temple.
     */
    public static final int BREACH_RADIUS = 90;

    /** How far off-screen enemies spawn, so they walk in rather than pop in. */
    public static final int SPAWN_MARGIN = 140;

    private GameConfig() {
        // Utility class — not instantiable.
    }
}
