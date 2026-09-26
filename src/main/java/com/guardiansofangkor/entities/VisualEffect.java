package com.guardiansofangkor.entities;

/**
 * A short-lived, purely cosmetic thing on the field — a spawn puff, an arrow in
 * flight, an impact burst.
 *
 * <p>Effects live in the engine rather than the renderer because their lifetime
 * is measured in game ticks and must pause when the game pauses. The renderer
 * decides what each {@link Kind} looks like; it never decides when one exists.
 */
public class VisualEffect {

    public enum Kind {
        /** Smoke cloud where an enemy materialises. */
        SPAWN_POOF,

        /** Shot travelling from the hero to a target. */
        ARROW,

        /** Burst where an arrow connects. */
        IMPACT,

        /** A Naga Shield charge turning aside something that reached the temple. */
        WARD_BREAK,

        /** Flourish where a power-up is claimed. */
        BOON_CLAIMED,

        ENRAGE_BURST,
    }

    private final Kind kind;
    private final double startX;
    private final double startY;
    private final double endX;
    private final double endY;
    private final int lifetimeTicks;
    private final double scale;

    /**
     * Marks the heavier version of an effect — a killing blow rather than a
     * mid-word shot, a verse finished rather than a bolt deflected. The engine
     * only says which moments matter; the renderer decides whether a given
     * hero draws them any differently.
     */
    private final boolean major;

    private int ticks;

    /** Stationary effect, e.g. a spawn puff. */
    public VisualEffect(Kind kind, double x, double y, int lifetimeTicks, double scale) {
        this(kind, x, y, x, y, lifetimeTicks, scale, false);
    }

    /** Stationary effect that may be flagged {@link #isMajor() major}. */
    public VisualEffect(Kind kind, double x, double y, int lifetimeTicks, double scale,
                        boolean major) {
        this(kind, x, y, x, y, lifetimeTicks, scale, major);
    }

    /** Travelling effect, e.g. an arrow. */
    public VisualEffect(Kind kind,
                        double startX, double startY,
                        double endX, double endY,
                        int lifetimeTicks, double scale) {
        this(kind, startX, startY, endX, endY, lifetimeTicks, scale, false);
    }

    /** Travelling effect that may be flagged {@link #isMajor() major}. */
    public VisualEffect(Kind kind,
                        double startX, double startY,
                        double endX, double endY,
                        int lifetimeTicks, double scale, boolean major) {
        this.kind = kind;
        this.major = major;
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.lifetimeTicks = Math.max(1, lifetimeTicks);
        this.scale = scale;
    }

    public void update() {
        ticks++;
    }

    public boolean isExpired() {
        return ticks >= lifetimeTicks;
    }

    /** 0 at spawn, 1 at expiry. */
    public double getProgress() {
        return Math.min(1.0, ticks / (double) lifetimeTicks);
    }

    public double getX() {
        return startX + (endX - startX) * getProgress();
    }

    public double getY() {
        return startY + (endY - startY) * getProgress();
    }

    /** Direction of travel in radians, for orienting arrows. */
    public double getHeading() {
        return Math.atan2(endY - startY, endX - startX);
    }

    public Kind getKind() {
        return kind;
    }

    public double getScale() {
        return scale;
    }

    public boolean isMajor() {
        return major;
    }

    public double getEndX() {
        return endX;
    }

    public double getEndY() {
        return endY;
    }
}
