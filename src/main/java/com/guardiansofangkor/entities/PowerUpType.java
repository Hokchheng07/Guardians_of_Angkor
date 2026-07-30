package com.guardiansofangkor.entities;

import com.guardiansofangkor.util.GameConfig;

/**
 * The boons a defeated spirit can leave behind.
 *
 * <p>An enum of configuration, like {@link EnemyType} — there is one concrete
 * {@link PowerUp} pickup class and one {@code PowerUpState} holder, both driven
 * by the values here. Adding a boon is a new constant plus one branch in
 * {@code GameState.applyPowerUp}, not a new class.
 *
 * <p>Every entry is fully configured even though none of the artwork exists yet.
 * {@code SpriteCache} draws a placeholder at the declared size when the PNG is
 * absent — the same graceful path the unfinished enemy art already uses — so
 * dropping real icons into {@code resources/images} later needs no code change.
 *
 * <p>No colours here. This is an entities class, so it must not import
 * {@code java.awt}; the renderer keeps the palette entry for each type.
 */
public enum PowerUpType {

    /**
     * Everything on the field stops for a few seconds.
     *
     * <p>The panic button. Rare and short, because its value is that it buys a
     * moment to read a screen that has got away from the player — not that it
     * removes the threat.
     */
    TIME_FREEZE("Time Freeze", "ពេលឈប់", "The advance halts",
            Effect.TIMED, GameConfig.TARGET_FPS * 4, 3,
            "powerup_time_freeze.png"),

    /**
     * Everything moves at less than half pace for a good while.
     *
     * <p>The gentler sibling of Time Freeze: much longer, far less absolute. It
     * is the most useful drop for a player who is losing on typing speed rather
     * than on a single bad moment, which is why it is weighted highest.
     */
    SLOW_TIDE("Slow Tide", "ទឹកយឺត", "The tide slows",
            Effect.TIMED, GameConfig.TARGET_FPS * 8, 4,
            "powerup_slow_tide.png"),

    /** Clears every enemy currently on the field. Scores as if each were typed. */
    PURGE("Purge", "បោសសម្អាត", "The causeway is swept",
            Effect.INSTANT, 0, 2,
            "powerup_purge.png"),

    /** Restores one life, up to the starting maximum. */
    MEND("Mend", "ព្យាបាល", "The temple is mended",
            Effect.INSTANT, 0, 2,
            "powerup_mend.png"),

    /**
     * A coiled ward that turns aside the next thing to reach the temple.
     *
     * <p>Stacks up to {@link GameConfig#MAX_SHIELD_CHARGES}, and is consumed by a
     * breach or a landed bolt instead of a life. Unlike the timed boons it never
     * expires on its own, so it is the one drop that is always worth taking.
     */
    NAGA_SHIELD("Naga Shield", "ខែលនាគ", "A naga coils around the gate",
            Effect.CHARGE, 0, 3,
            "powerup_naga_shield.png");

    /** How a boon takes hold once collected. */
    public enum Effect {
        /** Fires once, immediately, and is gone. */
        INSTANT,

        /** Runs for a duration, tracked by {@code PowerUpState}. */
        TIMED,

        /** Banked until something spends it. */
        CHARGE
    }

    private final String displayName;
    private final String khmerName;
    private final String flavour;
    private final Effect effect;
    private final int baseDurationTicks;
    private final int dropWeight;
    private final String spriteFile;

    PowerUpType(String displayName, String khmerName, String flavour,
                Effect effect, int baseDurationTicks, int dropWeight,
                String spriteFile) {
        this.displayName = displayName;
        this.khmerName = khmerName;
        this.flavour = flavour;
        this.effect = effect;
        this.baseDurationTicks = baseDurationTicks;
        this.dropWeight = dropWeight;
        this.spriteFile = spriteFile;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * NOTE: transcribed rather than authored — proofread by a native speaker
     * before this ships, exactly as the enemy names need.
     */
    public String getKhmerName() {
        return khmerName;
    }

    /** One line shown on the banner when the boon fires. */
    public String getFlavour() {
        return flavour;
    }

    public Effect getEffect() {
        return effect;
    }

    /** Duration at the reference tier. Zero for instant and charge boons. */
    public int getBaseDurationTicks() {
        return baseDurationTicks;
    }

    /** Relative likelihood of being the boon that drops. */
    public int getDropWeight() {
        return dropWeight;
    }

    /** Classpath location of this boon's icon, e.g. {@code /images/powerup_mend.png}. */
    public String getSpritePath() {
        return "/images/" + spriteFile;
    }

    public boolean isTimed() {
        return effect == Effect.TIMED;
    }

    public boolean isInstant() {
        return effect == Effect.INSTANT;
    }

    public boolean isCharge() {
        return effect == Effect.CHARGE;
    }
}
