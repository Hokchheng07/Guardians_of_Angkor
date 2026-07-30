package com.guardiansofangkor.engine;

import java.util.Map;

/**
 * A one-line hint about what the next level brings, shown on the
 * level-cleared banner.
 *
 * <p>Telegraphing the change matters because the difficulty curve introduces new
 * enemy types and new behaviours silently. Without a hint the player only learns
 * that Yeak throws by being hit by something they had no reason to expect.
 *
 * <p>Kept as data rather than hardcoded strings in the renderer so hints stay in
 * step with {@link WaveWeights} unlocks and can be translated later.
 */
public record LevelPreview(String hint) {

    /** Hints tied to a specific level, usually a new type unlocking. */
    private static final Map<Integer, String> BY_LEVEL = Map.ofEntries(
            Map.entry(2, "Ahp swarms take to the air"),
            Map.entry(3, "Yeak arrives — and he throws"),
            Map.entry(4, "The spirits quicken"),
            Map.entry(6, "Pret drags a long name behind it"),
            Map.entry(7, "Swarms grow bolder"),
            Map.entry(9, "The causeway fills"),
            Map.entry(11, "Little is slow now"),
            Map.entry(13, "The temple lights dim"));

    /** Levels that are a multiple of this are Naga mini-boss levels. */
    private static final int MINI_BOSS_INTERVAL = 5;

    /**
     * The hint for an upcoming level, or {@code null} when that level has
     * nothing new worth announcing.
     *
     * <p>Returning null rather than a filler string is deliberate — a banner that
     * always carries a third line trains the player to stop reading it.
     */
    public static LevelPreview forLevel(int level) {
        return forLevel(level, Difficulty.reference());
    }

    /**
     * The hint for an upcoming level on a given tier.
     *
     * <p>Tier-aware because the final boss moves: Easy ends with the Naga at
     * level 10, Medium with Krong Reap at 15. Announcing the wrong one would be
     * worse than announcing nothing.
     */
    public static LevelPreview forLevel(int level, Difficulty difficulty) {
        if (level <= 0) {
            return null;
        }
        Difficulty tier = difficulty == null ? Difficulty.reference() : difficulty;

        // The tier's own finale takes precedence over everything below.
        if (tier.hasFinalBoss() && level == tier.getFinalBossLevel()) {
            return new LevelPreview(
                    tier.getFinalBossType().getDisplayName() + " comes for the temple");
        }

        String specific = BY_LEVEL.get(level);
        if (specific != null) {
            return new LevelPreview(specific);
        }
        // Mini-bosses are rule-based, so they keep working past the table.
        if (level % MINI_BOSS_INTERVAL == 0) {
            return new LevelPreview("A Naga coils at the gate");
        }
        return null;
    }
}
