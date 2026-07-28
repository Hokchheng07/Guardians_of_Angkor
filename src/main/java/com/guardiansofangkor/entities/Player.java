package com.guardiansofangkor.entities;

import com.guardiansofangkor.util.GameConfig;

/**
 * Preah Ream, standing at the temple with his back to the viewer.
 *
 * <p>He has two poses: idle with the bow lowered, and drawn ready to loose. The
 * action pose is held for a short window after each correct keystroke, so
 * sustained typing keeps him firing continuously and pausing drops him back to
 * idle. That coupling is the whole point — the player's hands and the hero's
 * hands are doing the same thing.
 *
 * <p>Gameplay state only. The renderer picks which sprite to draw from
 * {@link #isFiring()}; it never sets it.
 */
public class Player {

    private final double x;
    private final double feetY;

    /** Counts down while the firing pose is held. */
    private int actionTicks;

    /** Ticks since the last shot, used to rate-limit arrow spawning. */
    private int shotCooldown;

    /** Slight recoil offset so rapid fire has some kick to it. */
    private double recoil;

    private int totalShots;

    public Player() {
        this(GameConfig.TEMPLE_CENTER_X, GameConfig.PLAYER_FEET_Y);
    }

    public Player(double x, double feetY) {
        this.x = x;
        this.feetY = feetY;
    }

    public void update() {
        if (actionTicks > 0) {
            actionTicks--;
        }
        if (shotCooldown > 0) {
            shotCooldown--;
        }
        recoil *= 0.82;
        if (Math.abs(recoil) < 0.05) {
            recoil = 0;
        }
    }

    /**
     * Called on a correct keystroke. Puts him in the firing pose and reports
     * whether an arrow should actually be spawned this tick.
     *
     * @return true when the shot cooldown allows a new arrow
     */
    public boolean tryFire() {
        actionTicks = GameConfig.PLAYER_ACTION_TICKS;
        if (shotCooldown > 0) {
            return false;
        }
        shotCooldown = 5;
        recoil = 7;
        totalShots++;
        return true;
    }

    /** True while the drawn-bow pose should be shown. */
    public boolean isFiring() {
        return actionTicks > 0;
    }

    /** Resets to idle, e.g. on restart. */
    public void reset() {
        actionTicks = 0;
        shotCooldown = 0;
        recoil = 0;
        totalShots = 0;
    }

    public double getX() {
        return x;
    }

    public double getFeetY() {
        return feetY;
    }

    /** Where arrows leave the bow — around shoulder height. */
    public double getBowY() {
        return feetY - GameConfig.PLAYER_HEIGHT * 0.62;
    }

    public double getRecoil() {
        return recoil;
    }

    public int getTotalShots() {
        return totalShots;
    }
}
