package com.guardiansofangkor.i18n;

import java.util.Collections;
import java.util.List;

/**
 * Which vocabulary a particular stretch of a particular run is allowed to draw
 * from — the resolved answer to "difficulty X, level Y: what words may appear?".
 *
 * <p>This is the piece that stops early levels serving late-game words. Enemy
 * types describe how long a word they <em>want</em> ({@code minWordLength} /
 * {@code maxWordLength}), but that want is expressed in absolute letters and has
 * no idea what run it is in, so on its own a Pret demands an eight-letter word
 * on level one of Easy. A policy is the ceiling that sits over the top: the type
 * still picks the longest thing it can, but only from what the band offers.
 *
 * <p>Policies are resolved from the word bank's own JSON (see the {@code
 * difficulties} section), never from constants in Java, so retuning which levels
 * get which words is a data edit.
 */
public final class WordPolicy {

    /** Used when a tier has no table of its own — permissive, so nothing stalls. */
    static final WordPolicy UNRESTRICTED =
            new WordPolicy("default", 1, List.of(), "");

    private final String tierKey;
    private final int level;
    private final List<String> poolNames;
    private final String bossPoolName;

    WordPolicy(String tierKey, int level, List<String> poolNames, String bossPoolName) {
        this.tierKey = tierKey == null ? "default" : tierKey;
        this.level = Math.max(1, level);
        this.poolNames = poolNames == null ? List.of() : List.copyOf(poolNames);
        this.bossPoolName = bossPoolName == null ? "" : bossPoolName;
    }

    /** The difficulty this policy was resolved for, e.g. {@code "easy"}. */
    public String getTierKey() {
        return tierKey;
    }

    /** The level this policy was resolved for. */
    public int getLevel() {
        return level;
    }

    /**
     * Names of the vocabulary pools this band may use, in file order.
     *
     * <p>Empty means "no restriction" — every pool is fair game. That is the
     * behaviour for an unknown tier or a word bank with no tuning table, and it
     * is deliberately the permissive direction: a missing table should make the
     * game generic, not unplayable.
     */
    public List<String> getPoolNames() {
        return Collections.unmodifiableList(poolNames);
    }

    /** The boss pool this band's bosses draw from, or empty for no preference. */
    public String getBossPoolName() {
        return bossPoolName;
    }

    /** True when this policy narrows the vocabulary at all. */
    public boolean restrictsPools() {
        return !poolNames.isEmpty();
    }

    @Override
    public String toString() {
        return "WordPolicy[" + tierKey + " L" + level + " pools=" + poolNames
                + " boss=" + bossPoolName + "]";
    }
}
