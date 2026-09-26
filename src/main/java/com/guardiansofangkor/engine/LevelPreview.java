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

    static {
        ARRIVAL.put(EnemyType.AHP, "Ahp swarms take to the air");
        ARRIVAL.put(EnemyType.YEAK, "Yeak arrives — and he throws");
        ARRIVAL.put(EnemyType.KMAOCH, "Kmaoch drifts in");
        ARRIVAL.put(EnemyType.PRET, "Pret drags a long name behind it");
        ARRIVAL.put(EnemyType.BEISACH, "Beisach walk the causeway");
        ARRIVAL.put(EnemyType.CHARGER, "Chargers lower their horns");
        ARRIVAL.put(EnemyType.GARUDA, "The Punisher circles — typos feed it");
        ARRIVAL.put(EnemyType.SPLITTER, "Splitters break into more");
        ARRIVAL.put(EnemyType.ARAK, "Arak summons from the dark");
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

        // The gauntlet: a boss closes every tenth level, and which one comes
        // from the tier's own list (Difficulty.getMilestoneBoss) — the same
        // lookup GameState uses to summon it, so the banner cannot promise a
        // different boss than the one that arrives. It used to announce a Naga
        // every fifth level, a mini-boss the gauntlet replaced.
        // getMilestoneBoss answers for the whole block of ten (11-19 all return
        // the level-10 boss), so the banner asks only on the tenth level itself.
        EnemyType boss = tier.isBossLevel(level) ? tier.getMilestoneBoss(level) : null;
        EnemyType arriving = firstAnnounceable(WaveWeights.newlyUnlockedAt(level, tier));

        // A boss level can also be an arrival level, and which ones coincide
        // moves with the tier — so rather than silently dropping one, say both.
        if (boss != null && arriving != null) {
            return new LevelPreview(boss.getDisplayName() + " at the gate — "
                    + arriving.getDisplayName() + " too");
        }
        if (boss != null) {
            return new LevelPreview(boss.getDisplayName() + " comes for the temple");
        }
        if (arriving != null) {
            return new LevelPreview(ARRIVAL.get(arriving));
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
