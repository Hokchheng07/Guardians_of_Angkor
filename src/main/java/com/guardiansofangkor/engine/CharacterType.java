package com.guardiansofangkor.engine;

public enum CharacterType {

    PREAH_REAM(
            "Preah Ream",
            "/images/Prea_Ream(idle).png",
            "/images/Preas_Ream(Action).png"
    ),

    PRET(
            "Pret",
            "/images/Pret.png",
            "/images/Pret.png"
    ),

    YEAK(
            "Yeak",
            "/images/yeak_transparent.png",
            "/images/yeak_transparent.png"
    );

    private final String displayName;
    private final String imagePath;
    private final String actionImagePath;

    CharacterType(String displayName, String imagePath) {
        this(displayName, imagePath, imagePath);
    }

    CharacterType(String displayName, String imagePath, String actionImagePath) {
        this.displayName = displayName;
        this.imagePath = imagePath;
        this.actionImagePath = actionImagePath;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getImagePath() {
        return imagePath;
    }

    public String getActionImagePath() {
        return actionImagePath != null ? actionImagePath : imagePath;
    }
}