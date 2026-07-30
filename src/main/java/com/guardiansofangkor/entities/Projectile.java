package com.guardiansofangkor.entities;

import com.guardiansofangkor.matching.WordTarget;

/**
 * A cursed bolt hurled at the temple, carrying a short word.
 *
 * <p>Dev brief Section 5.2: projectiles are time-critical and preempt enemies as
 * the active typing target. That is handled by {@code TargetResolver} checking
 * the projectile list first — a Yeak throwing at you forces a decision, because
 * you must break off whatever word you were typing to intercept.
 *
 * <p>Words are short (2-3 characters) precisely because the time budget is
 * short; a long word here would be undodgeable rather than tense.
 */
public class Projectile implements WordTarget {

    private final String word;

    private final double startX;
    private final double startY;
    private final double targetX;
    private final double targetY;

    /** Total ticks the flight should take. Shrinks with level. */
    private final int flightTicks;

    /**
     * Fractional so a Slow Tide genuinely slows a bolt in flight. Rounding this
     * to whole ticks would let a slowed bolt arrive at full speed, which reads
     * as the boon being broken rather than as a design choice.
     */
    private double ticksFlown;

    private boolean alive = true;
    private boolean landed;

    /**
     * True only on the tick the bolt lands.
     *
     * <p>Separate from {@link #landed} on purpose: a sticky flag would make
     * GameState charge the player a life on every tick of the fade-out, draining
     * the whole run from one missed bolt.
     */
    private boolean justLanded;

    private int hitFlashTicks;
    private int defeatTicks;

    public Projectile(String word,
                      double startX, double startY,
                      double targetX, double targetY,
                      int flightTicks) {
        if (word == null || word.isEmpty()) {
            throw new IllegalArgumentException("word must not be null or empty");
        }
        this.word = word;
        this.startX = startX;
        this.startY = startY;
        this.targetX = targetX;
        this.targetY = targetY;
        this.flightTicks = Math.max(1, flightTicks);
    }

    /** Advances one tick at full pace. */
    public void update() {
        update(1.0);
    }

    /**
     * Advances one tick, scaled by how fast the world is running — 0 under a
     * Time Freeze, a fraction under a Slow Tide.
     *
     * <p>The fade after interception is deliberately not scaled: it is feedback,
     * not threat, and a frozen death animation looks like a stall.
     */
    public void update(double timeScale) {
        justLanded = false;

        if (hitFlashTicks > 0) {
            hitFlashTicks--;
        }
        if (!alive) {
            defeatTicks++;
            return;
        }

        double scale = Math.max(0.0, timeScale);
        if (scale <= 0.0001) {
            return;
        }

        ticksFlown += scale;
        if (ticksFlown >= flightTicks) {
            landed = true;
            justLanded = true;
            alive = false;
        }
    }

    /** Flight progress, 0 at the throwing hand and 1 at the temple. */
    public double getProgress() {
        return Math.min(1.0, ticksFlown / flightTicks);
    }

    public double getX() {
        return startX + (targetX - startX) * getProgress();
    }

    /**
     * Y along a shallow arc rather than a straight line — a bolt that travels
     * dead straight reads as a UI element, not a thrown object.
     */
    public double getY() {
        double t = getProgress();
        double linear = startY + (targetY - startY) * t;
        double arc = Math.sin(t * Math.PI) * 60;
        return linear - arc;
    }

    /**
     * True only on the tick it reached the temple without being typed. Costs the
     * player a life, exactly once.
     */
    public boolean hasJustLanded() {
        return justLanded;
    }

    /** True once it has landed, and stays true. For rendering the impact. */
    public boolean hasLanded() {
        return landed;
    }

    /** Cleared by the player before it landed. */
    public void intercept() {
        this.alive = false;
    }

    public boolean isExpired(int defeatAnimationTicks) {
        return !alive && defeatTicks >= defeatAnimationTicks;
    }

    public void flashHit(int ticks) {
        this.hitFlashTicks = Math.max(this.hitFlashTicks, ticks);
    }

    @Override
    public String getWord() {
        return word;
    }

    @Override
    public boolean isActive() {
        return alive;
    }

    public int getHitFlashTicks() {
        return hitFlashTicks;
    }

    public int getDefeatTicks() {
        return defeatTicks;
    }

    /** Rotation in radians so the sprite points along its flight path. */
    public double getHeading() {
        double t = getProgress();
        double dx = targetX - startX;
        double dy = (targetY - startY) - Math.cos(t * Math.PI) * Math.PI * 60;
        return Math.atan2(dy, dx);
    }

    @Override
    public String toString() {
        return "Projectile[\"" + word + "\"]";
    }
}
