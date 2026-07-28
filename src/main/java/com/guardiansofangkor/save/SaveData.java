package com.guardiansofangkor.save;

import com.guardiansofangkor.i18n.Language;

/**
 * A snapshot of run progress. Deliberately a small value type — only what is
 * needed to resume, per dev brief Section 5.4 (wave, score, difficulty).
 */
public record SaveData(
        int wave,
        int score,
        int lives,
        Language language,
        int bestScore,
        int bestWave) {

    public SaveData {
        wave = Math.max(0, wave);
        score = Math.max(0, score);
        lives = Math.max(0, lives);
        language = language == null ? Language.ENGLISH : language;
        bestScore = Math.max(0, bestScore);
        bestWave = Math.max(0, bestWave);
    }

    /** A fresh-start save with no progress. */
    public static SaveData empty() {
        return new SaveData(0, 0, 0, Language.ENGLISH, 0, 0);
    }

    /** True when there is a run worth offering to continue. */
    public boolean hasResumableRun() {
        return wave > 0 && lives > 0;
    }

    /** Returns a copy with the personal-best fields updated if this run beat them. */
    public SaveData withBests(int runScore, int runWave) {
        return new SaveData(
                wave, score, lives, language,
                Math.max(bestScore, runScore),
                Math.max(bestWave, runWave));
    }
}
