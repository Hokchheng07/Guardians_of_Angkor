package com.guardiansofangkor.entities;

import com.guardiansofangkor.util.GameConfig;

/**
 * How an enemy travels from its spawn puff to the temple.
 *
 * <p>Ground types get three routes — in from the left, in from the right, or
 * down a 45-degree causeway line. Flying types get the same two shapes but at
 * hover altitude, so they cross above the plaza rather than along it.
 *
 * <p>The flank routes deliberately have no vertical component. That means their
 * depth scale stays at full size the whole way, which is correct: they enter
 * from the near edges of the plaza, whereas diagonal spawns start far back up
 * the causeway and grow as they descend.
 */
public enum ApproachPath {

    /** Walks straight in along the plaza from the left or right edge. */
    GROUND_FLANK(false, false),

    /** Descends the causeway on an exact 45-degree diagonal. */
    GROUND_DIAGONAL(false, true),

    /** Drifts in horizontally at hover altitude. */
    AIR_FLANK(true, false),

    /** Descends diagonally, levelling out at hover altitude. */
    AIR_DIAGONAL(true, true);

    private final boolean airborne;
    private final boolean diagonal;

    ApproachPath(boolean airborne, boolean diagonal) {
        this.airborne = airborne;
        this.diagonal = diagonal;
    }

    public boolean isAirborne() {
        return airborne;
    }

    public boolean isDiagonal() {
        return diagonal;
    }

    /** Horizontal component of the unit travel vector. */
    public double unitX() {
        return diagonal ? GameConfig.DIAGONAL : 1.0;
    }

    /** Vertical component of the unit travel vector. Zero for flank routes. */
    public double unitY() {
        return diagonal ? GameConfig.DIAGONAL : 0.0;
    }

    /**
     * How far back along this route an enemy materialises, measured as the
     * straight-line path distance.
     */
    public int runMin() {
        return diagonal ? GameConfig.APPROACH_RUN_MIN : GameConfig.FLANK_RUN_MIN;
    }

    public int runMax() {
        return diagonal ? GameConfig.APPROACH_RUN_MAX : GameConfig.FLANK_RUN_MAX;
    }

    /**
     * The longest run that still keeps the enemy's word plate clear of the HUD
     * bar once it is drawn at spawn distance.
     *
     * <p>Shortening the run rather than clamping the resulting Y is deliberate:
     * clamping would break the equal horizontal and vertical offsets that make
     * the route a true 45 degrees.
     *
     * @param targetY  the anchor Y the enemy settles at on arrival
     * @param headroom how far the artwork extends above its anchor at spawn size
     */
    public int maxRunFor(double targetY, double headroom) {
        if (!diagonal) {
            return runMax();
        }
        double floorY = GameConfig.HUD_BAR_HEIGHT
                + GameConfig.WORD_PLATE_CLEARANCE + headroom;
        double allowed = (targetY - floorY) / unitY();
        return (int) Math.max(runMin(), Math.min(runMax(), allowed));
    }

    /**
     * Where this route starts, horizontally.
     *
     * @param direction +1 for an enemy that will march rightward, -1 leftward
     * @param run       path distance back from the temple
     */
    public double spawnX(int direction, double run) {
        return GameConfig.TEMPLE_CENTER_X - direction * run * unitX();
    }

    /**
     * Where this route starts, vertically.
     *
     * @param targetY the anchor Y the enemy settles at on arrival
     * @param run     path distance back from the temple
     */
    public double spawnY(double targetY, double run) {
        return targetY - run * unitY();
    }

    /** The correct family of routes for a given ground behaviour. */
    public static ApproachPath[] forBehaviour(GroundBehavior behaviour) {
        return behaviour == GroundBehavior.GROUNDED
                ? new ApproachPath[] {GROUND_FLANK, GROUND_DIAGONAL}
                : new ApproachPath[] {AIR_FLANK, AIR_DIAGONAL};
    }
}
