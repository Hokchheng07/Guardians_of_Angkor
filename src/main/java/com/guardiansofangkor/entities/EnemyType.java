package com.guardiansofangkor.entities;

import com.guardiansofangkor.util.GameConfig;

/**
 * The enemy roster from dev brief Section 6, with render sizing, ground
 * behaviour and ranged-attack configuration attached.
 *
 * <p>This is an enum of <em>configuration</em>, not a class hierarchy: per the
 * team's composition-over-inheritance rule there is one concrete {@link Enemy}
 * class that behaves differently based on the type it is handed.
 *
 * <p>Every type here is fully configured even when its artwork has not been
 * delivered yet — SpriteCache falls back to a placeholder shape drawn at the
 * same dimensions, so dropping the real PNG in later needs no code change.
 *
 * <p>NOTE: the Khmer display names below were transcribed from the brief PDF and
 * should be proofread by a native speaker before they ship — PDF text extraction
 * frequently reorders Khmer diacritics.
 */
public enum EnemyType {

    /** Common. Bread-and-butter enemy. */
    BEISACH("Beisach", "បិសាច", "Common",
            "Beisach.png", GroundBehavior.GROUNDED,
            115, 0, 3, 5,
            1.0, 0.075, 2.1, 0, 1, false),

    /**
     * Grunt. The big ogre — deliberately the largest non-boss on screen, and the
     * only type that throws for now.
     */
    YEAK("Yeak", "យក្ស", "Grunt",
            "yeak_transparent.png", GroundBehavior.GROUNDED,
            215, 0, 5, 7,
            0.85, 0.035, 1.4, 330, 1, true),

    /** Swarm. Short words, fast, spawns in groups. Flies. Gets frantic fast. */
    AHP("Ahp", "អាប", "Swarm",
            "ahp_transparent.png", GroundBehavior.FLOATING,
            105, 155, 2, 4,
            1.7, 0.11, 3.2, 0, 1, false),

    /**
     * Heavy. Long words, slow approach. Barely speeds up.
     *
     * <p>The tallest thing in the roster short of a boss — taller than Yeak,
     * who is the bulkiest. The two read as heavy in different ways, which is
     * the point: Yeak is wide and solid, Pret is a gaunt column. Only the
     * height is set here; width follows the trimmed art's own proportions, so
     * making him taller cannot stretch him.
     */
    PRET("Pret", "ប្រេត", "Heavy",
            "Pret.png", GroundBehavior.GROUNDED,
            250, 0, 8, 12,
            0.65, 0.018, 1.0, 0, 1, true),

    /** Mimic. Its word shifts mid-type (Phase 10 behaviour). Floats. */
    KMAOCH("Kmaoch", "ខ្មោច", "Mimic",
            "Kmaoch.png", GroundBehavior.FLOATING,
            175, 120, 4, 6,
            1.0, 0.06, 2.0, 0, 1, false),

    /** Mini-boss. Chains 2-3 words before dying. Coiled on the ground. */
    NAGA("Naga", "នាគ", "Mini-boss",
            "Naga.png", GroundBehavior.GROUNDED,
            240, 0, 5, 8,
            0.6, 0.02, 1.0, 0, 3, true),

    /** Final boss. Full-phrase typing. */
    KRONG_REAP("Krong Reap", "ក្រុងរាព", "Final boss",
            "krong_reap_transparent.png", GroundBehavior.GROUNDED,
            330, 0, 10, 24,
            0.5, 0.015, 0.9, 0, 1, true);

    private final String displayName;
    private final String khmerName;
    private final String tier;
    private final String spriteFile;
    private final GroundBehavior groundBehavior;
    private final int targetHeight;
    private final int hoverHeight;
    private final int minWordLength;
    private final int maxWordLength;
    private final double speedMultiplier;
    private final double levelSpeedGain;
    private final double maxSpeedMultiplier;
    private final int throwIntervalTicks;
    private final int maxChainLength;
    private final boolean dropsBoons;

    EnemyType(String displayName, String khmerName, String tier,
              String spriteFile, GroundBehavior groundBehavior,
              int targetHeight, int hoverHeight,
              int minWordLength, int maxWordLength,
              double speedMultiplier, double levelSpeedGain, double maxSpeedMultiplier,
              int throwIntervalTicks, int maxChainLength, boolean dropsBoons) {
        this.displayName = displayName;
        this.khmerName = khmerName;
        this.tier = tier;
        this.spriteFile = spriteFile;
        this.groundBehavior = groundBehavior;
        this.targetHeight = targetHeight;
        this.hoverHeight = hoverHeight;
        this.minWordLength = minWordLength;
        this.maxWordLength = maxWordLength;
        this.speedMultiplier = speedMultiplier;
        this.levelSpeedGain = levelSpeedGain;
        this.maxSpeedMultiplier = maxSpeedMultiplier;
        this.throwIntervalTicks = throwIntervalTicks;
        this.maxChainLength = maxChainLength;
        this.dropsBoons = dropsBoons;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getKhmerName() {
        return khmerName;
    }

    public String getTier() {
        return tier;
    }

    /** Classpath location of this type's sprite, e.g. {@code /images/yeak_transparent.png}. */
    public String getSpritePath() {
        return "/images/" + spriteFile;
    }

    public GroundBehavior getGroundBehavior() {
        return groundBehavior;
    }

    public boolean isGrounded() {
        return groundBehavior == GroundBehavior.GROUNDED;
    }

    /**
     * On-screen height in pixels at full depth. Width is derived from the
     * sprite's own content aspect ratio rather than being fixed, because the
     * delivered art is not uniformly square — forcing everything into the
     * brief's square boxes would squash the taller monsters.
     */
    public int getTargetHeight() {
        return targetHeight;
    }

    /**
     * For {@link GroundBehavior#FLOATING} types, how far the sprite's centre
     * sits above the ground line. Zero for grounded types.
     */
    public int getHoverHeight() {
        return hoverHeight;
    }

    public int getMinWordLength() {
        return minWordLength;
    }

    public int getMaxWordLength() {
        return maxWordLength;
    }

    /** Speed multiplier at level 1. */
    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    /**
     * Extra speed multiplier gained per level.
     *
     * <p>Light, short-word types carry a large value here so they become
     * genuinely frantic later on, while heavies barely change — a Pret that
     * moved as fast as an Ahp would be unfair rather than hard.
     */
    public double getLevelSpeedGain() {
        return levelSpeedGain;
    }

    /** Ceiling on the level-scaled multiplier, so late levels stay playable. */
    public double getMaxSpeedMultiplier() {
        return maxSpeedMultiplier;
    }

    /**
     * Ticks between ranged attacks, or zero for melee types.
     *
     * <p>Only Yeak throws for now. The other entries are configured at zero so
     * enabling them later is a one-number change rather than new code.
     */
    public int getThrowIntervalTicks() {
        return throwIntervalTicks;
    }

    /** True when this type can hurl projectiles at the temple. */
    public boolean canThrow() {
        return throwIntervalTicks > 0;
    }

    /**
     * Most words this type can demand before it dies.
     *
     * <p>One for ordinary enemies. Mini-bosses carry more, and the spawner
     * randomises within the range so two encounters are not identical.
     */
    public int getMaxChainLength() {
        return maxChainLength;
    }

    /**
     * True when killing this type can leave a power-up behind.
     *
     * <p>Only the grounded heavies and the mini-boss: Yeak, Pret and Naga. Two
     * reasons. They are the slow, long-word, genuinely difficult kills, so a
     * boon reads as payment for the effort rather than as loot that fell out of
     * the trash mob. And they arrive one or two at a time rather than in
     * swarms, so drops stay spaced out instead of arriving in clusters the
     * player cannot collect anyway.
     */
    public boolean dropsBoons() {
        return dropsBoons;
    }

    /**
     * Half-hearts lost when one of these reaches the temple.
     *
     * <p>Flyers cost half. They are fast, short-worded and arrive several at
     * once; charging a full life apiece would let one bad Ahp wave end a run,
     * which is not the kind of pressure the swarm is for. Walkers cost the full
     * heart, because reaching the temple means the player lost a word they had
     * a long time to type.
     */
    public int breachDamage() {
        return isGrounded()
                ? GameConfig.DAMAGE_GROUNDED_BREACH
                : GameConfig.DAMAGE_FLYING_BREACH;
    }

    /** True when this type takes several words to kill. */
    public boolean isChainedType() {
        return maxChainLength > 1;
    }

    /**
     * The anchor Y this type settles at on arrival — the ground line for walkers,
     * the hover altitude for flyers.
     */
    public double anchorTargetY() {
        return isGrounded()
                ? GameConfig.GROUND_LINE_Y
                : GameConfig.GROUND_LINE_Y - hoverHeight;
    }

    /**
     * How far above its anchor this type's artwork extends when drawn at spawn
     * distance. Used to stop tall monsters spawning so high that their word
     * plate lands behind the HUD bar.
     *
     * <p>Grounded types are anchored by their feet so the whole height counts;
     * flyers are anchored by their centre so only half does.
     */
    public double spawnHeadroom() {
        double drawnHeight = targetHeight * GameConfig.DEPTH_SCALE_MIN;
        return isGrounded() ? drawnHeight : drawnHeight / 2.0;
    }
}
