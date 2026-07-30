package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.EnemyType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A one-line hint about what the next level brings, shown on the
 * level-cleared banner.
 *
 * <p>Telegraphing the change matters because the difficulty curve introduces new
 * enemy types and new behaviours silently. Without a hint the player only learns
 * that Yeak throws by being hit by something they had no reason to expect.
 *
 * <p>Everything here is derived rather than listed. Which monster arrives when
 * comes from {@link WaveWeights}, and which one ends the run comes from
 * {@link Difficulty} — because both of those move with the tier, and a table of
 * fixed levels would be right for Medium and wrong for the rest. The only
 * hardcoded strings are the descriptions of each monster, which do not vary.
 */
public record LevelPreview(String hint) {

    /** How each type is announced the level it first appears. */
    private static final Map<EnemyType, String> ARRIVAL = new EnumMap<>(EnemyType.class);

    /** Filler hints for levels with no arrival, keyed by how deep the run is. */
    private static final Map<Integer, String> ATMOSPHERE = Map.of(
            4, "The spirits quicken",
            7, "Swarms grow bolder",
            9, "The causeway fills",
            11, "Little is slow now",
            13, "The temple lights dim");

    /** Levels that are a multiple of this are Naga mini-boss levels. */
    private static final int MINI_BOSS_INTERVAL = 5;

    static {
        ARRIVAL.put(EnemyType.AHP, "Ahp swarms take to the air");
        ARRIVAL.put(EnemyType.YEAK, "Yeak arrives — and he throws");
        ARRIVAL.put(EnemyType.STEC_KANTOAB, "Stec Kantoab drifts in");
        ARRIVAL.put(EnemyType.PRET, "Pret drags a long name behind it");
        ARRIVAL.put(EnemyType.BEISACH, "Beisach walk the causeway");
    }

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
     * <p>Tier-aware throughout: the finale, the arrivals and therefore the whole
     * banner change with the difficulty. Announcing the wrong one would be worse
     * than announcing nothing.
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

        EnemyType arriving = firstAnnounceable(WaveWeights.newlyUnlockedAt(level, tier));
        boolean miniBoss = level % MINI_BOSS_INTERVAL == 0;

        // The two can land on the same level, and which one that is moves with
        // the tier — so rather than picking a winner and silently dropping the
        // other, say both. Naming the monster first keeps the line scannable.
        if (miniBoss && arriving != null) {
            return new LevelPreview(
                    "Naga at the gate — " + arriving.getDisplayName() + " too");
        }
        if (arriving != null) {
            return new LevelPreview(ARRIVAL.get(arriving));
        }
        // Mini-bosses are rule-based, so they keep working past any table.
        if (miniBoss) {
            return new LevelPreview("A Naga coils at the gate");
        }

        String atmosphere = ATMOSPHERE.get(level);
        return atmosphere == null ? null : new LevelPreview(atmosphere);
    }

    /** The first newly-unlocked type that has an announcement written for it. */
    private static EnemyType firstAnnounceable(List<EnemyType> arriving) {
        for (EnemyType type : arriving) {
            if (ARRIVAL.containsKey(type)) {
                return type;
            }
        }
        return null;
    }
}
