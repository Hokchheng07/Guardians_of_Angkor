package com.guardiansofangkor.engine;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Which difficulty tiers the player has earned the right to start.
 *
 * <p>The ladder itself lives on {@link Difficulty#requiredPredecessor()}; this
 * class only answers whether a given rung has been reached. Keeping the two
 * apart means the ladder can be reordered without touching the unlock rule, and
 * the unlock rule can be unit-tested without a save file.
 *
 * <p>Immutable, and total: an unknown or misspelled tier key is simply not a
 * clear, and a null set is an empty one. A corrupted save should cost the
 * player their unlocks at worst, never the ability to open the menu.
 *
 * <p>Tiers that are not built at all ({@link Difficulty#isImplemented()}) are
 * deliberately <em>not</em> handled here. Being locked and being unfinished are
 * different facts about a tier and the player is told about them differently —
 * see {@link MenuState}, which is where the two are combined.
 */
public final class DifficultyProgress {

    private static final DifficultyProgress NOTHING_CLEARED =
            new DifficultyProgress(Set.of());

    private final Set<String> cleared;

    public DifficultyProgress(Set<String> clearedTierKeys) {
        Set<String> copy = new LinkedHashSet<>();
        if (clearedTierKeys != null) {
            for (String key : clearedTierKeys) {
                if (key != null && !key.isBlank()) {
                    copy.add(key.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        this.cleared = Collections.unmodifiableSet(copy);
    }

    /** A player who has beaten nothing — only the opening tier is available. */
    public static DifficultyProgress fresh() {
        return NOTHING_CLEARED;
    }

    /** True when {@code tier} has been beaten at least once. */
    public boolean hasCleared(Difficulty tier) {
        return tier != null && cleared.contains(tier.getWordBankKey());
    }

    /**
     * True when a run may be started on {@code tier}.
     *
     * <p>Only the immediately preceding rung is checked, not the whole chain
     * below it. Clearing Medium is the only way to reach Hard in normal play, so
     * requiring Easy as well would only ever punish a save that got out of step,
     * which is not the player's doing.
     */
    public boolean isUnlocked(Difficulty tier) {
        if (tier == null) {
            return false;
        }
        Difficulty required = tier.requiredPredecessor();
        return required == null || hasCleared(required);
    }

    /**
     * Why {@code tier} cannot be started, phrased for the player, or an empty
     * string when it can.
     */
    public String lockReason(Difficulty tier) {
        if (tier == null || isUnlocked(tier)) {
            return "";
        }
        Difficulty required = tier.requiredPredecessor();
        return "Clear " + required.getDisplayName()
                + " to unlock " + tier.getDisplayName() + ".";
    }

    /** Returns a copy that also records {@code tier} as beaten. */
    public DifficultyProgress withCleared(Difficulty tier) {
        if (tier == null || hasCleared(tier)) {
            return this;
        }
        Set<String> merged = new LinkedHashSet<>(cleared);
        merged.add(tier.getWordBankKey());
        return new DifficultyProgress(merged);
    }

    /** The tier keys beaten so far, for persistence. */
    public Set<String> getClearedTierKeys() {
        return cleared;
    }

    /** The hardest tier currently available to start. Never null. */
    public Difficulty highestUnlocked() {
        Difficulty best = Difficulty.defaultChoice();
        for (Difficulty tier : Difficulty.values()) {
            if (tier.isImplemented() && isUnlocked(tier)) {
                best = tier;
            }
        }
        return best;
    }

    @Override
    public String toString() {
        return "DifficultyProgress" + cleared;
    }
}
