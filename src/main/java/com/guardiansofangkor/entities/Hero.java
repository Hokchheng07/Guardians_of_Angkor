package com.guardiansofangkor.entities;

import java.util.Locale;

/**
 * The playable guardians, chosen before a run.
 *
 * <p>Cosmetic only. Both heroes play by exactly the same rules — same lives,
 * same arrows-per-keystroke, same scoring — so every number in DifficultyCurve
 * and every test written against it stays true whoever is picked. What differs
 * is the art: the sprites, the bolt that flies, and the burst where it lands.
 *
 * <p>Like {@link EnemyType}, each entry names its own files, so swapping in new
 * art is a data change here rather than a code change in the renderer. A hero
 * whose files are missing still draws — as a placeholder — and still plays.
 *
 * <p>The save file stores {@link #getKey()}, not the enum, because the save
 * package sits below the engine and must not import it.
 */
public enum Hero {

    PREAH_REAM("preah_ream", "Preah Ream", "Prince of the Reamker",
            "A bow drawn for every word.",
            "/images/PreasReamCharacter/PreasReamIdle.png",
            "/images/PreasReamCharacter/PreasReamShootLeft.png",
            "/images/PreasReamCharacter/PreasReamShootRight.png",
            "/images/PreasReamCharacter/PreasReamProjectile.png",
            "PreahReamShot.wav"),

    APSARA("apsara", "Apsara", "Celestial Dancer",
            "Lotus light, loosed in a turn.",
            "/images/ApsaraCharacter/ApsaraIdle.png",
            "/images/ApsaraCharacter/ApsaraAttackLeft.png",
            "/images/ApsaraCharacter/ApsaraAttackRight.png",
            "/images/ApsaraCharacter/ProjectilesOfApsara.png",
            "ApsaraShot.wav");

    private final String key;
    private final String displayName;
    private final String epithet;
    private final String tagline;
    private final String idlePath;
    private final String attackLeftPath;
    private final String attackRightPath;
    private final String effectSheetPath;
    private final String shotSound;

    Hero(String key, String displayName, String epithet, String tagline,
         String idlePath, String attackLeftPath, String attackRightPath,
         String effectSheetPath, String shotSound) {
        this.key = key;
        this.displayName = displayName;
        this.epithet = epithet;
        this.tagline = tagline;
        this.idlePath = idlePath;
        this.attackLeftPath = attackLeftPath;
        this.attackRightPath = attackRightPath;
        this.effectSheetPath = effectSheetPath;
        this.shotSound = shotSound;
    }

    /** The hero a brand-new player starts on. */
    public static Hero defaultChoice() {
        return PREAH_REAM;
    }

    /**
     * Looks a hero up by its save key.
     *
     * <p>Total by design: the key comes from a hand-editable file, so anything
     * blank or unknown falls back to the default rather than failing the load.
     */
    public static Hero fromKey(String key) {
        if (key != null) {
            String wanted = key.trim().toLowerCase(Locale.ROOT);
            for (Hero hero : values()) {
                if (hero.key.equals(wanted)) {
                    return hero;
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

    public String getEpithet() {
        return epithet;
    }

    /** One line for the picker. Kept short enough for the menu panel. */
    public String getTagline() {
        return tagline;
    }

    public String getIdlePath() {
        return idlePath;
    }

    public String getAttackLeftPath() {
        return attackLeftPath;
    }

    public String getAttackRightPath() {
        return attackRightPath;
    }

    /**
     * The sheet this hero's shots and impacts are cut from. Which pieces of it
     * are used, and where they sit, is the renderer's business (SpriteCache).
     */
    public String getEffectSheetPath() {
        return effectSheetPath;
    }

    /** The distinct sound identity played when this guardian attacks. */
    public String getShotSound() {
        return shotSound;
    }

    /**
     * True when this hero has distinct left and right attack poses, and so
     * turns toward the target. Both current heroes do; a hero given a single
     * action pose would name the same file twice and simply never turn.
     */
    public boolean hasDirectionalAttack() {
        return !attackLeftPath.equals(attackRightPath);
    }
}
