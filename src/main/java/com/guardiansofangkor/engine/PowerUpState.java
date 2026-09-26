package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.PowerUpType;
import com.guardiansofangkor.util.GameConfig;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Which boons are currently running, and for how much longer.
 *
 * <p>Only holds the <em>durable</em> half of the power-up system: the timed
 * effects and the banked shield charges. Instant boons (Purge, Mend) never live
 * here — they act on the field and the life count, which is
 * {@link GameState}'s business, and giving them a home here would mean two
 * classes owned the same decision.
 *
 * <p>Pure logic, like everything else in {@code engine} — no Swing, no AWT. The
 * HUD reads {@link #getActive()} and paints it; it never asks this class what
 * anything should look like.
 */
public class PowerUpState {

    /** Remaining ticks per timed boon. Absent means not running. */
    private final Map<PowerUpType, Integer> remaining = new EnumMap<>(PowerUpType.class);

    /** What each running boon started at, so the HUD can draw a drain bar. */
    private final Map<PowerUpType, Integer> granted = new EnumMap<>(PowerUpType.class);

    private int shieldCharges;

    /** Counts down after any boon fires, so the renderer can flash the screen. */
    private int flashTicks;

    /** The boon that most recently fired, for the banner. Null once it clears. */
    private PowerUpType lastFired;

    /** A snapshot of one running boon, for the HUD. */
    public record Active(PowerUpType type, int remainingTicks, int totalTicks) {

        /** How much of the duration is left, 1 at the start and 0 at expiry. */
        public double fraction() {
            return totalTicks <= 0 ? 0 : Math.max(0.0,
                    Math.min(1.0, remainingTicks / (double) totalTicks));
        }

        /** Whole seconds left, rounded up — what the HUD actually prints. */
        public int secondsLeft() {
            return (int) Math.ceil(remainingTicks / (double) GameConfig.TARGET_FPS);
        }
    }

    /** Advances every running timer by one tick. */
    public void update() {
        if (flashTicks > 0) {
            flashTicks--;
            if (flashTicks == 0) {
                lastFired = null;
            }
        }

        List<PowerUpType> finished = new ArrayList<>();
        for (Map.Entry<PowerUpType, Integer> entry : remaining.entrySet()) {
            int left = entry.getValue() - 1;
            if (left <= 0) {
                finished.add(entry.getKey());
            } else {
                entry.setValue(left);
            }
        }
        for (PowerUpType type : finished) {
            remaining.remove(type);
            granted.remove(type);
        }
    }

    /**
     * Starts a timed boon, or refreshes one already running.
     *
     * <p>Refreshing rather than stacking is deliberate. Two Slow Tides running at
     * once would either have to multiply (bringing the field to a crawl the
     * player never asked for) or silently discard one; taking the longer of the
     * two remaining times is the only version where collecting a second one is
     * never a waste and never a runaway.
     *
     * @param difficulty scales the duration — gentler tiers hold a boon longer
     */
    public void activate(PowerUpType type, Difficulty difficulty) {
        if (type == null || !type.isTimed()) {
            return;
        }
        double scale = difficulty == null
                ? 1.0
                : difficulty.getPowerUpDurationScale();
        int duration = Math.max(1, (int) Math.round(type.getBaseDurationTicks() * scale));

        remaining.merge(type, duration, Math::max);
        granted.merge(type, duration, Math::max);
        markFired(type);
    }

    /** Banks a shield charge, up to {@link GameConfig#MAX_SHIELD_CHARGES}. */
    public void addShield() {
        shieldCharges = Math.min(GameConfig.MAX_SHIELD_CHARGES, shieldCharges + 1);
        markFired(PowerUpType.NAGA_SHIELD);
    }

    /**
     * Spends a shield charge if one is banked.
     *
     * @return true when a charge absorbed the hit, so the caller must not also
     *         take a life
     */
    public boolean consumeShield() {
        if (shieldCharges <= 0) {
            return false;
        }
        shieldCharges--;
        return true;
    }

    /** Records that a boon fired, for the banner and screen flash. */
    public void markFired(PowerUpType type) {
        this.lastFired = type;
        this.flashTicks = GameConfig.POWERUP_FLASH_TICKS;
    }

    // ---- what the simulation asks ------------------------------------------

    public boolean isFrozen() {
        return remaining.containsKey(PowerUpType.TIME_FREEZE);
    }

    public boolean isSlowed() {
        return remaining.containsKey(PowerUpType.SLOW_TIDE);
    }

    /**
     * The multiplier every moving thing is advanced by this tick.
     *
     * <p>Freeze wins over slow rather than combining, because a stopped field
     * cannot be more stopped and because the alternative — freeze silently
     * eating part of the Slow Tide the player also collected — is worse. Slow
     * Tide's own timer keeps running underneath, which is the right trade: the
     * two were never meant to be chained for a longer stop.
     */
    public double getTimeScale() {
        if (isFrozen()) {
            return 0.0;
        }
        return isSlowed() ? GameConfig.SLOW_TIDE_FACTOR : 1.0;
    }

    public int getShieldCharges() {
        return shieldCharges;
    }

    public boolean hasShield() {
        return shieldCharges > 0;
    }

    /** Every running timed boon, longest remaining first, for the HUD strip. */
    public List<Active> getActive() {
        List<Active> active = new ArrayList<>(remaining.size());
        for (Map.Entry<PowerUpType, Integer> entry : remaining.entrySet()) {
            active.add(new Active(entry.getKey(), entry.getValue(),
                    granted.getOrDefault(entry.getKey(), entry.getValue())));
        }
        active.sort((a, b) -> b.remainingTicks() - a.remainingTicks());
        return active;
    }

    /** True while any boon is running or banked — the HUD strip is shown at all. */
    public boolean hasAnything() {
        return !remaining.isEmpty() || shieldCharges > 0;
    }

    /** The boon that most recently fired, or null. Clears when the flash ends. */
    public PowerUpType getLastFired() {
        return lastFired;
    }

    /** Flash intensity, 1 the moment a boon fires and 0 once it settles. */
    public double getFlashStrength() {
        return flashTicks / (double) GameConfig.POWERUP_FLASH_TICKS;
    }

    /** Clears everything. Called on restart. */
    public void reset() {
        remaining.clear();
        granted.clear();
        shieldCharges = 0;
        flashTicks = 0;
        lastFired = null;
    }
}
