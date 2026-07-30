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

    /**
     * Steps each life is divided into.
     *
     * <p>Damage is tracked in halves so a light hit can cost less than a heavy
     * one. Held as an integer count of halves rather than a fractional life, so
     * three half-hits and one full hit can never disagree by a rounding error
     * about whether the player is dead.
     */
    public static final int HALVES_PER_LIFE = 2;

    /** Half-hearts a run starts with. */
    public static final int STARTING_HALF_LIVES = STARTING_LIVES * HALVES_PER_LIFE;

    /**
     * What a grounded monster costs when it reaches the temple.
     *
     * <p>The heavies walk slowly and carry the long words — reaching the temple
     * means the player was beaten on a word they had plenty of time for, so it
     * costs the full heart.
     */
    public static final int DAMAGE_GROUNDED_BREACH = HALVES_PER_LIFE;

    /**
     * What a flyer costs.
     *
     * <p>Half, because the swarm types are fast, short-worded and arrive several
     * at a time. Charging a full life for each would make a single bad Ahp wave
     * end a run outright, which is not the kind of pressure they are for.
     */
    public static final int DAMAGE_FLYING_BREACH = 1;

    /** What a bolt or a venom spit costs when it lands. Half a heart. */
    public static final int DAMAGE_PROJECTILE = 1;

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
     *
     * <p>Deliberately tight. At 105 this was wider than Preah Ream is drawn, so
     * enemies died to the hitbox while still visibly a stride away from him and
     * the life felt stolen rather than lost. At 58 the sprites have to actually
     * reach him, which is both fairer and more legible — the player can see the
     * moment coming.
     *
     * <p>Cannot be raised without re-checking {@link #DEPTH_FULL_SIZE_AT}: the
     * breach must happen after enemies reach full size, or monsters are culled
     * mid-growth. Lowering it is always safe on that count.
     */
    public static final int BREACH_RADIUS = 58;

    /**
     * Top of the walkable plaza, measured from the background art.
     *
     * <p>Everything above this line is temple terracing and sky. A grounded
     * enemy whose feet go above it is standing on masonry or thin air.
     *
     * <p>Measured at screen y 540 on the current background, where the open
     * flagstone paving begins below the lowest terrace. Re-measure this if the
     * background is ever replaced — it is the single number that keeps walkers
     * on the ground, and it does not survive an art change.
     */
    public static final int PLAZA_TOP_Y = 540;

    /**
     * Largest descent a grounded enemy may make on its approach, kept inside
     * the plaza with a small safety margin.
     */
    public static final int GROUND_RISE_MAX = GROUND_LINE_Y - PLAZA_TOP_Y - 5;

    /** Smallest descent, so the drift is still perceptible. */
    public static final int GROUND_RISE_MIN = 28;

    /**
     * Shortest distance back along the 45-degree approach line that an airborne
     * enemy can materialise. Measured along the diagonal, so both the horizontal
     * and vertical offsets are this over root two.
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
     * Extra slack between a capped spawn and the HUD bar.
     *
     * <p>Without it the cap is exact: solving the run so the plate just clears
     * the bar puts the plate <em>on</em> the bar, and a pixel of rounding either
     * way decides whether the word is readable. This buys unambiguous daylight.
     */
    public static final int HUD_SAFETY_MARGIN = 12;

    /**
     * How small a freshly spawned airborne enemy is drawn, relative to full
     * size. It grows to 1.0 as it nears the temple, which is what reads as
     * depth over the long 45-degree descent.
     */
    public static final double DEPTH_SCALE_MIN = 0.55;

    /**
     * Depth shrink for a grounded enemy on its plaza drift.
     *
     * <p>Milder than the airborne figure on purpose: a walker descends around a
     * hundred pixels, not three hundred, and shrinking it as hard as a flyer
     * would read as the monster deflating rather than as perspective.
     */
    public static final double GROUND_DEPTH_SCALE_MIN = 0.78;

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

    // ---- Power-ups ---------------------------------------------------------

    /**
     * How long a dropped power-up waits to be typed before it fades.
     *
     * <p>Seven seconds is long enough to finish the word already in progress and
     * still reach for it, and short enough that ignoring one is a real decision
     * rather than a free option to bank forever.
     */
    public static final int POWERUP_LIFETIME_TICKS = TARGET_FPS * 7;

    /** How far a pickup drifts upward over its lifetime, in pixels. */
    public static final int POWERUP_DRIFT = 46;

    /** On-screen size of a power-up icon. */
    public static final int POWERUP_ICON_SIZE = 54;

    /**
     * How many Naga Shield charges can be held at once.
     *
     * <p>Capped so a lucky streak of drops cannot bank an unlosable run. Three
     * matches {@link #STARTING_LIVES}, which is also how it reads on the HUD —
     * a full ward is a second set of lives, not an infinite one.
     */
    public static final int MAX_SHIELD_CHARGES = 3;

    /** Speed multiplier applied to everything while Slow Tide is running. */
    public static final double SLOW_TIDE_FACTOR = 0.45;

    /** How long the screen flashes when an instant power-up fires. */
    public static final int POWERUP_FLASH_TICKS = 22;

    // ---- Final boss --------------------------------------------------------

    /**
     * On-screen height of the boss.
     *
     * <p>Far larger than anything in the roster, and that is the whole point:
     * the finale has to announce itself before a single word is read.
     */
    public static final int BOSS_HEIGHT = 380;

    /**
     * Where the boss's lowest point sits.
     *
     * <p>Well above {@link #GROUND_LINE_Y}, which reads as the serpent rearing
     * up out of the plaza behind the temple rather than standing on it. It also
     * moves the whole monster back and up, out from behind Preah Ream — at
     * ground level the hero was planted squarely in front of it.
     */
    public static final int BOSS_BASE_Y = GROUND_LINE_Y - 110;

    /** Top of the boss's artwork, derived so the two can never disagree. */
    public static final int BOSS_TOP_Y = BOSS_BASE_Y - BOSS_HEIGHT;

    /**
     * Bottom edge of the verse panel.
     *
     * <p>Derived from the hero rather than picked: the panel must clear the top
     * of Preah Ream's head, because he is drawn in the foreground and any part
     * of a sentence behind him is a part the player cannot read. Sitting it here
     * also lands it over the boss's middle, which is where the eye already is.
     */
    public static final int VERSE_PANEL_BOTTOM_Y =
            PLAYER_FEET_Y - PLAYER_HEIGHT - 18;

    /**
     * How long a venom bolt takes to reach the hero.
     *
     * <p>Slow — five and a half seconds. Venom carries a real word to type, so
     * the flight has to be long enough to notice it, read it and answer it while
     * a verse is already in progress.
     */
    public static final int VENOM_FLIGHT_TICKS = TARGET_FPS * 5 + TARGET_FPS / 2;

    /** Shortest gap between boss attacks. */
    public static final int VENOM_INTERVAL_MIN_TICKS = TARGET_FPS * 5;

    /** Longest gap between boss attacks. */
    public static final int VENOM_INTERVAL_MAX_TICKS = TARGET_FPS * 10;

    /** Height of the boss health bar under the HUD. */
    public static final int BOSS_BAR_HEIGHT = 14;

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
