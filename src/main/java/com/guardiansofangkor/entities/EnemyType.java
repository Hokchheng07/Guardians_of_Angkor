package com.guardiansofangkor.entities;

/**
 * The enemy roster from dev brief Section 6, with render sizing from Section 7
 * and ground behaviour attached.
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

    /** Common. Bread-and-butter enemy. Art pending. */
    BEISACH("Beisach", "បិសាច", "Common",
            "beisach_transparent.png", GroundBehavior.GROUNDED,
            96, 0, 3, 5, 1.0),

    /** Grunt. Slower but longer words. */
    YEAK("Yeak", "យក្ស", "Grunt",
            "yeak_transparent.png", GroundBehavior.GROUNDED,
            150, 0, 5, 7, 0.9),

    /** Swarm. Short words, fast, spawns in groups of 2-3. Flies. */
    AHP("Ahp", "អាប", "Swarm",
            "ahp_transparent.png", GroundBehavior.FLOATING,
            96, 150, 2, 4, 1.8),

    /** Heavy. Long words, slow approach. Art pending. */
    PRET("Pret", "ប្រេត", "Heavy",
            "pret_transparent.png", GroundBehavior.GROUNDED,
            160, 0, 8, 12, 0.7),

    /** Mimic. Its word shifts mid-type (Phase 10 behaviour). Floats. Art pending. */
    STEC_KANTOAB("Stec Kantoab", "សើចកន្តួប", "Mimic",
            "stec_kantoab_transparent.png", GroundBehavior.FLOATING,
            110, 110, 4, 6, 1.0),

    /** Mini-boss. Chains 2-3 words before dying. Coiled on the ground. */
    NAGA("Naga", "នាគ", "Mini-boss",
            "naga_transparent.png", GroundBehavior.GROUNDED,
            190, 0, 5, 8, 0.6),

    /** Final boss. Full-phrase typing. */
    KRONG_REAP("Krong Reap", "ក្រុងរាព", "Final boss",
            "krong_reap_transparent.png", GroundBehavior.GROUNDED,
            300, 0, 10, 24, 0.5);

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

    EnemyType(String displayName, String khmerName, String tier,
              String spriteFile, GroundBehavior groundBehavior,
              int targetHeight, int hoverHeight,
              int minWordLength, int maxWordLength, double speedMultiplier) {
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
     * On-screen height in pixels. Width is derived from the sprite's own content
     * aspect ratio rather than being fixed, because the delivered art is not
     * uniformly square — forcing everything into the brief's square boxes would
     * squash the taller monsters. Height is what carries the tier hierarchy.
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

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }
}
