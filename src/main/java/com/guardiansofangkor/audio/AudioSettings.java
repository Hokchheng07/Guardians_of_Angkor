package com.guardiansofangkor.audio;

/**
 * The player's three volume sliders, each 0 to 100.
 *
 * <p>Master scales everything; SFX and Music scale their own channel on top of
 * it. Anything that plays a sound should ask {@link #sfxGain()} or
 * {@link #musicGain()} rather than combining the sliders itself, so the rule for
 * how they stack lives in exactly one place.
 *
 * <p>There is no sound in the game yet. The settings exist, persist and are
 * adjustable now so that audio, when it lands, only has to read them — the
 * Options screen is not waiting on it.
 *
 * <p>A value type in its own package rather than in the engine, because the
 * save package stores it and must not import the engine.
 */
public record AudioSettings(int master, int sfx, int music) {

    public static final int MIN = 0;
    public static final int MAX = 100;

    /** How far one arrow-key press moves a slider. */
    public static final int STEP = 5;

    public AudioSettings {
        master = clamp(master);
        sfx = clamp(sfx);
        music = clamp(music);
    }

    /**
     * What a brand-new player hears. Music sits a little under effects, so the
     * cue that a word landed is never buried under the soundtrack.
     */
    public static AudioSettings defaults() {
        return new AudioSettings(80, 80, 60);
    }

    public AudioSettings withMaster(int value) {
        return new AudioSettings(value, sfx, music);
    }

    public AudioSettings withSfx(int value) {
        return new AudioSettings(master, value, music);
    }

    public AudioSettings withMusic(int value) {
        return new AudioSettings(master, sfx, value);
    }

    /** Effective effects volume, 0 to 1. */
    public double sfxGain() {
        return master / (double) MAX * (sfx / (double) MAX);
    }

    /** Effective music volume, 0 to 1. */
    public double musicGain() {
        return master / (double) MAX * (music / (double) MAX);
    }

    private static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }
}
