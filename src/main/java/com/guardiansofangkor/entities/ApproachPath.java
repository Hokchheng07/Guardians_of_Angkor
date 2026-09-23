package com.guardiansofangkor.entities;

import com.guardiansofangkor.util.GameConfig;

/**
 * How an enemy travels from its spawn puff to the temple.
 *
 * <p>Routes are defined by a horizontal run and a vertical rise rather than by
 * an angle, because the two categories genuinely cannot share one:
 *
 * <ul>
 *   <li><b>Grounded</b> enemies must keep their feet on the plaza, and the
 *       plaza is only {@code GROUND_LINE_Y - PLAZA_TOP_Y} pixels deep. A true
 *       45-degree walk would need equal horizontal and vertical travel, which
 *       over that depth means spawning about fifty pixels from the breach
 *       point. Anything longer puts them in the sky. So walkers get a long run
 *       with a shallow rise — a perspective drift of roughly six degrees.</li>
 *   <li><b>Airborne</b> enemies have no such limit, so they keep the full
 *       45-degree descent and start high.</li>
 * </ul>
 *
 * <p>Flank routes have no vertical component at all, so they stay at full size
 * the whole way — correct, because they enter on the near plane rather than
 * from up the causeway.
 */
public enum ApproachPath {

    /** Walks straight in along the plaza from the left or right edge. */
    GROUND_FLANK(false, false),

    /** Walks in along the plaza with a shallow descent for perspective. */
    GROUND_DIAGONAL(false, true),

    /** Drifts in horizontally at hover altitude. */
    AIR_FLANK(true, false),

    /** Descends on an exact 45-degree diagonal, levelling out at hover altitude. */
    AIR_DIAGONAL(true, true);

    private final boolean airborne;
    private final boolean descending;

    ApproachPath(boolean airborne, boolean descending) {
        this.airborne = airborne;
        this.descending = descending;
    }

    public boolean isAirborne() {
        return airborne;
    }

    /** True when this route loses altitude on the way in. */
    public boolean isDescending() {
        return descending;
    }

    /**
     * True only for routes that hold an exact 45 degrees.
     *
     * <p>Just the airborne descent. The ground descent is deliberately shallow,
     * so asserting 45 degrees on it would be wrong.
     */
    public boolean isFortyFiveDegrees() {
        return airborne && descending;
    }

    /** Shortest horizontal distance from the temple that this route starts at. */
    public int runMin() {
        if (isFortyFiveDegrees()) {
            return (int) Math.round(GameConfig.APPROACH_RUN_MIN * GameConfig.DIAGONAL);
        }
        return GameConfig.FLANK_RUN_MIN;
    }

    /** Longest horizontal distance from the temple that this route starts at. */
    public int runMax() {
        if (isFortyFiveDegrees()) {
            return (int) Math.round(GameConfig.APPROACH_RUN_MAX * GameConfig.DIAGONAL);
        }
        return GameConfig.FLANK_RUN_MAX;
    }

    /**
     * How far above its arrival altitude this route starts, for a given
     * horizontal run.
     *
     * <p>For the airborne descent this equals the run, which is what makes it
     * 45 degrees. For the ground drift it is capped at the plaza depth, so a
     * longer run makes the angle shallower rather than lifting the enemy off
     * the stone.
     */
    public double riseFor(double run) {
        if (!descending) {
            return 0;
        }
        if (airborne) {
            return run;
        }
        // Tuned so a long run uses most of the plaza's depth without a short one
        // spawning right on top of the breach point.
        double proportional = run * 0.18;
        return Math.max(GameConfig.GROUND_RISE_MIN,
                Math.min(GameConfig.GROUND_RISE_MAX, proportional));
    }

    /** How small an enemy on this route is drawn at spawn distance. */
    public double depthScaleMin() {
        if (!descending) {
            return 1.0;
        }
        return airborne ? GameConfig.DEPTH_SCALE_MIN : GameConfig.GROUND_DEPTH_SCALE_MIN;
    }

    /**
     * The longest run that still keeps the enemy's word plate clear of the HUD
     * bar once it is drawn at spawn distance.
     *
     * <p>Only the airborne descent can reach the bar; ground routes are already
     * confined to the plaza and are returned unchanged.
     *
     * @param targetY  the anchor Y the enemy settles at on arrival
     * @param headroom how far the artwork extends above its anchor at spawn size
     */
    public int maxRunFor(double targetY, double headroom) {
        if (!isFortyFiveDegrees()) {
            return runMax();
        }
        double floorY = GameConfig.HUD_BAR_HEIGHT
                + GameConfig.WORD_PLATE_CLEARANCE
                + GameConfig.HUD_SAFETY_MARGIN
                + headroom;
        // Rise equals run on this route, so the ceiling on one is the other.
        double allowed = targetY - floorY;
        return (int) Math.max(runMin(), Math.min(runMax(), allowed));
    }

    /**
     * Where this route starts, horizontally.
     *
     * @param direction +1 for an enemy that will march rightward, -1 leftward
     * @param run       horizontal distance back from the temple
     */
    public double spawnX(int direction, double run) {
        return GameConfig.TEMPLE_CENTER_X - direction * run;
    }

    /**
     * Where this route starts, vertically.
     *
     * @param targetY the anchor Y the enemy settles at on arrival
     * @param run     horizontal distance back from the temple
     */
    public double spawnY(double targetY, double run) {
        return targetY - riseFor(run);
    }

    /** The correct family of routes for a given ground behaviour. */
    public static ApproachPath[] forBehaviour(GroundBehavior behaviour) {
        return behaviour == GroundBehavior.GROUNDED
                ? new ApproachPath[] {GROUND_FLANK, GROUND_DIAGONAL}
                : new ApproachPath[] {AIR_FLANK, AIR_DIAGONAL};
    }
}
