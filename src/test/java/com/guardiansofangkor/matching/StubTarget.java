package com.guardiansofangkor.matching;

/**
 * Minimal {@link WordTarget} for tests — no position, no sprite, no game state.
 * Proves the matching package is genuinely decoupled from entities.
 */
final class StubTarget implements WordTarget {

    private final String word;
    private boolean active;

    StubTarget(String word) {
        this(word, true);
    }

    StubTarget(String word, boolean active) {
        this.word = word;
        this.active = active;
    }

    @Override
    public String getWord() {
        return word;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    void deactivate() {
        this.active = false;
    }

    @Override
    public String toString() {
        return "Stub(" + word + ")";
    }
}
