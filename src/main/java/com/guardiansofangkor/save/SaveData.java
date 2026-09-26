package com.guardiansofangkor.save;

import com.guardiansofangkor.audio.AudioSettings;
import com.guardiansofangkor.i18n.Language;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A snapshot of run progress. Deliberately a small value type — only what is
 * needed to resume, per dev brief Section 5.4 (wave, score, difficulty), plus
 * the one thing that outlives a run: which tiers have been beaten.
 *
 * <p>{@code clearedTiers} holds lower-case difficulty keys rather than the
 * {@code Difficulty} enum itself. The engine already imports this package, so
 * importing the engine back to name one identifier would be a package cycle for
 * no benefit — the same reason the word bank is keyed by string. It is a set
 * rather than a "highest tier reached" number so a save that somehow records an
 * out-of-order clear cannot re-lock something the player has already beaten.
 *
 * <p>{@code heroKey} is the hero of the saved run, which is also the one the
 * picker should open on next time. A string for the same package-cycle reason;
 * blank means "never chosen" and the engine resolves it to the default hero.
 *
 * <p>{@code language} and {@code audio} are the Options screen's settings. They
 * are preferences rather than run state, but they live in the same file so
 * there is one save to lose rather than two to keep in step.
 */
public record SaveData(
        int wave,
        int score,
        int lives,
        Language language,
        int bestScore,
        int bestWave,
        Set<String> clearedTiers,
        String heroKey,
        AudioSettings audio) {

    public SaveData {
        wave = Math.max(0, wave);
        score = Math.max(0, score);
        lives = Math.max(0, lives);
        language = language == null ? Language.ENGLISH : language;
        bestScore = Math.max(0, bestScore);
        bestWave = Math.max(0, bestWave);
        clearedTiers = normalise(clearedTiers);
        heroKey = heroKey == null ? "" : heroKey.trim().toLowerCase(Locale.ROOT);
        audio = audio == null ? AudioSettings.defaults() : audio;
    }

    /** Constructor for callers with no audio settings, e.g. saves from older builds. */
    public SaveData(int wave, int score, int lives, Language language,
                    int bestScore, int bestWave, Set<String> clearedTiers,
                    String heroKey) {
        this(wave, score, lives, language, bestScore, bestWave, clearedTiers, heroKey,
                AudioSettings.defaults());
    }

    /** Constructor for callers with no hero choice, e.g. saves from older builds. */
    public SaveData(int wave, int score, int lives, Language language,
                    int bestScore, int bestWave, Set<String> clearedTiers) {
        this(wave, score, lives, language, bestScore, bestWave, clearedTiers, "");
    }

    /** Backwards-compatible constructor for callers with no unlock state. */
    public SaveData(int wave, int score, int lives, Language language,
                    int bestScore, int bestWave) {
        this(wave, score, lives, language, bestScore, bestWave, Set.of());
    }

    /**
     * Lower-cases, trims and de-blanks the tier keys, then freezes them.
     *
     * <p>Total by design: the set arrives from a hand-editable properties file,
     * and a stray space or capital in it should widen nothing and break nothing.
     */
    private static Set<String> normalise(Set<String> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return Set.of();
        }
        Set<String> cleaned = new LinkedHashSet<>();
        for (String tier : tiers) {
            if (tier == null) {
                continue;
            }
            String key = tier.trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty()) {
                cleaned.add(key);
            }
        }
        return Set.copyOf(cleaned);
    }

    /** A fresh-start save with no progress and nothing unlocked. */
    public static SaveData empty() {
        return new SaveData(0, 0, 0, Language.ENGLISH, 0, 0, Set.of(), "",
                AudioSettings.defaults());
    }

    /** True when there is a run worth offering to continue. */
    public boolean hasResumableRun() {
        return wave > 0 && lives > 0;
    }

    /** True when the named tier has been beaten at least once. */
    public boolean hasCleared(String tierKey) {
        return tierKey != null
                && clearedTiers.contains(tierKey.trim().toLowerCase(Locale.ROOT));
    }

    /** Returns a copy with the personal-best fields updated if this run beat them. */
    public SaveData withBests(int runScore, int runWave) {
        return new SaveData(
                wave, score, lives, language,
                Math.max(bestScore, runScore),
                Math.max(bestWave, runWave),
                clearedTiers, heroKey, audio);
    }

    /** Returns a copy that also records {@code tierKey} as beaten. */
    public SaveData withCleared(String tierKey) {
        if (tierKey == null || tierKey.isBlank()) {
            return this;
        }
        Set<String> merged = new LinkedHashSet<>(clearedTiers);
        merged.add(tierKey.trim().toLowerCase(Locale.ROOT));
        return new SaveData(wave, score, lives, language, bestScore, bestWave, merged,
                heroKey, audio);
    }

    /**
     * Returns a copy carrying the Options screen's settings.
     *
     * <p>The game state does not own the volume sliders, so the autosave
     * layers them onto its snapshot with this — otherwise every autosave would
     * quietly reset the player's volume to default.
     */
    public SaveData withSettings(Language chosenLanguage, AudioSettings chosenAudio) {
        return new SaveData(wave, score, lives, chosenLanguage, bestScore, bestWave,
                clearedTiers, heroKey, chosenAudio);
    }
}
