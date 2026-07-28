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

    /**
     * Sine and cosine of 45 degrees. Enemies approach the temple on an exact
     * diagonal, so their per-tick X and Y steps are both speed times this.
     */
    public static final double DIAGONAL = Math.sqrt(0.5);

    /** Target frames (ticks) per second for the Swing timer game loop. */
    public static final int TARGET_FPS = 60;

    /** Milliseconds between ticks, derived from {@link #TARGET_FPS}. */
    public static final int TICK_INTERVAL_MS = 1000 / TARGET_FPS;

    /** Lives the player starts a run with. */
    public static final int STARTING_LIVES = 3;

    // ---- Shared animation timing ------------------------------------------

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
    // plaza below it. Enemies converge on the temple along 45-degree lines,
    // materialising in a puff of smoke rather than sliding in from off-screen.

    /** Y coordinate of the temple floor — where a fully-arrived enemy stands. */
    public static final int GROUND_LINE_Y = 640;

    /**
     * Horizontal centre of the temple entrance — what the enemies are marching
     * toward, and where Preah Ream stands. Reaching it costs the player a life.
     */
    public static final int TEMPLE_CENTER_X = SCREEN_WIDTH / 2;

    /**
     * How close to {@link #TEMPLE_CENTER_X} an enemy must get to count as having
     * breached the temple.
     */
    public static final int BREACH_RADIUS = 105;

    /**
     * Shortest distance back along the 45-degree approach line that an enemy can
     * materialise. Measured along the diagonal, so both the horizontal and
     * vertical offsets are this over root two.
     */
    public static final int APPROACH_RUN_MIN = 340;

    /** Longest distance back along the approach line. */
    public static final int APPROACH_RUN_MAX = 500;

    /**
     * Shortest distance out to the side that a flanking enemy materialises.
     * Flank routes are purely horizontal, so this is a plain X offset.
     */
    public static final int FLANK_RUN_MIN = 470;

    /** Longest flank offset. Kept inside the window so the spawn puff is visible. */
    public static final int FLANK_RUN_MAX = 590;

    /** Height of the HUD stat bar. Shared so spawns can avoid painting under it. */
    public static final int HUD_BAR_HEIGHT = 82;

    /**
     * Vertical space a word plate needs above the top of its sprite, plus a
     * breathing margin below the HUD bar.
     *
     * <p>This exists because a long diagonal run can otherwise put a tall
     * monster's word plate behind the HUD bar, making it unreadable and so
     * effectively untypeable. Spawn runs are shortened to respect it rather than
     * positions being clamped, which would break the exact 45-degree angle.
     */
    public static final int WORD_PLATE_CLEARANCE = 44;

    /**
     * How small a freshly spawned enemy is drawn, relative to its full size.
     * It grows to 1.0 as it nears the temple, which is what reads as depth.
     */
    public static final double DEPTH_SCALE_MIN = 0.55;

    /**
     * Fraction of the approach after which an enemy is drawn at full size.
     *
     * <p>Below 1.0 on purpose: enemies breach {@link #BREACH_RADIUS} short of
     * the temple centre, so a curve that only reached full size at the centre
     * would mean monsters were culled before ever being drawn at 100%.
     *
     * <p>The shortest approach run puts the breach at roughly 56% of the path,
     * so this must stay below that with margin — otherwise close-spawning
     * monsters visibly pop out mid-growth.
     */
    public static final double DEPTH_FULL_SIZE_AT = 0.45;

    // ---- Effects -----------------------------------------------------------

    /** Lifetime of the spawn smoke puff. */
    public static final int POOF_TICKS = 26;

    /** How long an arrow takes to travel from Preah Ream to its target. */
    public static final int ARROW_FLIGHT_TICKS = 9;

    /** How long Preah Ream holds his firing pose after a shot. */
    public static final int PLAYER_ACTION_TICKS = 16;

    /** Preah Ream's on-screen height. */
    public static final int PLAYER_HEIGHT = 250;

    /**
     * Preah Ream stands in front of the breach line, in the foreground — the
     * spirits pour down the causeway from the temple and he meets them.
     */
    public static final int PLAYER_FEET_Y = GROUND_LINE_Y + 72;

    // ---- Restart chord -----------------------------------------------------

    /**
     * How long the restart chord stays armed after Tab is pressed. Enter within
     * this window confirms; otherwise the chord silently disarms.
     */
    public static final int RESTART_ARMED_TICKS = TARGET_FPS * 3;

    private GameConfig() {
        // Utility class — not instantiable.
    }
}
