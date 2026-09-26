package com.guardiansofangkor.entities;

import com.guardiansofangkor.matching.WordTarget;
import com.guardiansofangkor.util.GameConfig;

/**
 * A boon left behind by a defeated spirit, waiting to be typed.
 *
 * <p>Collected the same way everything else in this game is dealt with: it
 * carries a short word, and typing that word claims it. Deliberately not an
 * inventory with a hotkey — the typing field legitimately consumes every letter,
 * so any keyboard shortcut for spending a power-up would have to be a modifier
 * chord, and a chord is a second control scheme bolted onto a game whose whole
 * proposition is that you only ever type.
 *
 * <p>Because it implements {@link WordTarget} it goes through the ordinary
 * prefix matcher, which means grabbing one competes with whatever word is
 * already in progress. That is the intended tension: the drop is a reward, but
 * reaching for it costs you the enemy you were part-way through.
 *
 * <p>Words are short — the pickup pool, shared with projectiles — because the
 * time budget is short and because it has to be legible at a glance next to
 * longer words already on screen.
 */
public class PowerUp implements WordTarget {

    private final PowerUpType type;
    private final String word;
    private final double x;
    private final double spawnY;
    private final int lifetimeTicks;

    private int ticks;
    private boolean claimed;

    /** Ticks since the claim. Drives the collect burst, not the drop's lifetime. */
    private int claimTicks;

    /**
     * True only on the tick it fades away untouched.
     *
     * <p>One-shot for the same reason {@code Projectile.hasJustLanded()} is: a
     * sticky flag would have the renderer replay the miss puff on every frame of
     * the fade.
     */
    private boolean justLapsed;

    /**
     * @param x      where the spirit fell
     * @param y      the anchor the icon rises from
     */
    public PowerUp(PowerUpType type, String word, double x, double y) {
        this(type, word, x, y, GameConfig.POWERUP_LIFETIME_TICKS);
    }

    public PowerUp(PowerUpType type, String word, double x, double y, int lifetimeTicks) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (word == null || word.isEmpty()) {
            throw new IllegalArgumentException("word must not be null or empty");
        }
        this.type = type;
        this.word = word;
        this.x = x;
        this.spawnY = y;
        this.lifetimeTicks = Math.max(1, lifetimeTicks);
    }

    /**
     * Advances one tick.
     *
     * <p>Runs on real time rather than on the frozen clock: a Time Freeze must
     * not also stop the drop that is sitting there waiting, or the boon would be
     * bankable indefinitely by chaining freezes.
     */
    public void update() {
        justLapsed = false;

        if (claimed) {
            claimTicks++;
            return;
        }
        ticks++;
        if (ticks == lifetimeTicks) {
            justLapsed = true;
        }
    }

    /** Life spent so far, 0 at the drop and 1 at the fade. */
    public double getProgress() {
        return Math.min(1.0, ticks / (double) lifetimeTicks);
    }

    /**
     * How urgent this pickup looks, 1 while it is fresh and 0 as it lapses.
     *
     * <p>Exposed rather than derived in the renderer so the warning pulse and the
     * actual expiry can never drift apart — the same reason the defeat-fade timer
     * is shared.
     */
    public double getRemaining() {
        return 1.0 - getProgress();
    }

    public double getX() {
        return x;
    }

    /** Y of the icon centre — it drifts upward as it ages. */
    public double getY() {
        return spawnY - GameConfig.POWERUP_DRIFT * getProgress();
    }

    /** Claimed by the player. Lingers a moment so the collect flourish can play. */
    public void claim() {
        this.claimed = true;
        this.claimTicks = 0;
    }

    public boolean isClaimed() {
        return claimed;
    }

    /** Ticks since the claim, for the renderer's burst. Zero until claimed. */
    public int getClaimTicks() {
        return claimTicks;
    }

    /** True only on the tick it faded untaken. */
    public boolean hasJustLapsed() {
        return justLapsed;
    }

    /**
     * True once it is gone, whether claimed or lapsed.
     *
     * <p>A claimed pickup is timed from the claim, not from the drop, so one
     * grabbed at the very last moment still plays its full flourish instead of
     * blinking out the instant it is earned.
     */
    public boolean isExpired(int collectAnimationTicks) {
        if (claimed) {
            return claimTicks >= Math.max(1, collectAnimationTicks);
        }
        return ticks > lifetimeTicks;
    }

    @Override
    public String getWord() {
        return word;
    }

    @Override
    public boolean isActive() {
        return !claimed && ticks < lifetimeTicks;
    }

    public PowerUpType getType() {
        return type;
    }

    public int getTicks() {
        return ticks;
    }

    @Override
    public String toString() {
        return "PowerUp[" + type.getDisplayName() + " \"" + word + "\"]";
    }
}
