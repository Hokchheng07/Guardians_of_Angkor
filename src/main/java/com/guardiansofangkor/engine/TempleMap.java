package com.guardiansofangkor.engine;

import java.util.Locale;

/**
 * The Cambodian temple grounds a run can defend.
 *
 * <p>Each map owns both its painting and its score. Keeping those together
 * means choosing a temple changes the complete atmosphere of the run rather
 * than swapping the image while unrelated music continues to play.
 */
public enum TempleMap {

    ANGKOR_WAT(
            "angkor_wat",
            "Angkor Wat",
            "Moonlit causeway beneath the five towers.",
            "/images/Background.png",
            new String[] {"Background2.wav", "Background3.wav"}),

    BAYON(
            "bayon",
            "Bayon",
            "Stone faces watch through the jungle mist.",
            "/images/Bayon-Background.png",
            new String[] {"Background4.wav", "Background5.wav"});

    private final String key;
    private final String displayName;
    private final String tagline;
    private final String backgroundPath;
    private final String[] musicTracks;

    TempleMap(String key, String displayName, String tagline,
              String backgroundPath, String[] musicTracks) {
        this.key = key;
        this.displayName = displayName;
        this.tagline = tagline;
        this.backgroundPath = backgroundPath;
        this.musicTracks = musicTracks.clone();
    }

    public static TempleMap defaultChoice() {
        return ANGKOR_WAT;
    }

    /** Resolves hand-editable or older save data without ever failing launch. */
    public static TempleMap fromKey(String key) {
        if (key != null) {
            String wanted = key.trim().toLowerCase(Locale.ROOT);
            for (TempleMap map : values()) {
                if (map.key.equals(wanted)) {
                    return map;
                }
            }
        }
        return defaultChoice();
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTagline() {
        return tagline;
    }

    public String getBackgroundPath() {
        return backgroundPath;
    }

    /** A defensive copy because the sound thread keeps this array while it plays. */
    public String[] getMusicTracks() {
        return musicTracks.clone();
    }
}
