package com.guardiansofangkor.renderer;

import com.guardiansofangkor.entities.PowerUpType;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

/**
 * The one place UI chrome colours are defined.
 *
 * <p>Stone-dark and temple gold, pulled from the Angkor sandstone and gilding in
 * the background art. The previous indigo/lavender scheme fought the artwork
 * rather than sitting inside it.
 *
 * <p>Both the top HUD bar and the bottom typing bar source their frame colours
 * from here on purpose: they are one system framing the play area, and if the
 * two drift apart the screen stops reading as a single interface.
 */
public final class Palette {

    /** Chrome background — warm stone, near-opaque. */
    public static final Color HUD_BG = new Color(0x1E, 0x19, 0x14, 230);

    /** Slightly lighter stone for gradient bottoms and inset panels. */
    public static final Color HUD_BG_SOFT = new Color(0x2A, 0x23, 0x1B, 214);

    /** Hairline rules and borders. */
    public static final Color HUD_DIVIDER = new Color(0xD4, 0xAF, 0x37);

    /** The same gold at low alpha, for secondary rules. */
    public static final Color HUD_DIVIDER_SOFT = new Color(0xD4, 0xAF, 0x37, 70);

    /** Secondary labels — field names, captions. */
    public static final Color HUD_TEXT_DIM = new Color(0xA8, 0x9A, 0x82);

    /** Primary values — LEVEL and SCORE. */
    public static final Color HUD_TEXT_GOLD = new Color(0xF7, 0xD1, 0x6E);

    /** Secondary values — WPM, ACCURACY, SLAIN, BEST. */
    public static final Color HUD_TEXT_WHITE = new Color(0xF5, 0xEF, 0xE3);

    /** Progress bar track. */
    public static final Color PROGRESS_TRACK = new Color(0x30, 0x20, 0x18);

    /** Progress bar fill. */
    public static final Color PROGRESS_FILL = HUD_DIVIDER;

    /** A life still held. */
    public static final Color LIFE_FILLED = HUD_DIVIDER;

    /** A life spent — outline only, receding into the bar. */
    public static final Color LIFE_LOST = new Color(0x3B, 0x34, 0x2A);

    /** Full-screen dim behind the game over panel. */
    public static final Color SCRIM = new Color(0x14, 0x10, 0x0C, 226);

    /** Failure accent. */
    public static final Color DANGER = new Color(0xD9, 0x63, 0x54);

    /** Warm glow used for the hero rim-light and lock highlights. */
    public static final Color GLOW = HUD_TEXT_GOLD;

    /** Success accent — a won run, a claimed boon. */
    public static final Color BOON = new Color(0x8F, 0xD6, 0xA6);

    // ---- the finale --------------------------------------------------------

    /**
     * Boss venom, outer.
     *
     * <p>Purple because nothing else on screen is. The play area is sandstone
     * and gold and the cursed bolts are ember orange; a venom bolt has to be
     * distinguishable at a glance from a bolt the player could have typed away,
     * since mistaking one for the other wastes the only seconds they have.
     */
    public static final Color VENOM_EDGE = new Color(0x7B, 0x3F, 0xA8);

    /** Boss venom, inner. */
    public static final Color VENOM_CORE = new Color(0xC9, 0x8C, 0xF0);

    /** The boss health bar's fill. */
    public static final Color BOSS_HEALTH = new Color(0xB4, 0x4A, 0x4A);

    /** Backing panel for the paragraph the finale asks for. */
    public static final Color BOSS_PANEL = new Color(0x14, 0x10, 0x0C, 232);

    // ---- power-ups ---------------------------------------------------------

    /**
     * One accent colour per boon.
     *
     * <p>Lives here rather than on {@link PowerUpType} because that is an
     * entities class and entities must not import AWT. Keeping the mapping on
     * this side is also what stops a boon's icon, its HUD pip and its screen
     * flash drifting to three different greens.
     *
     * <p>Every colour is a desaturated jewel tone rather than a primary: the
     * play area is warm sandstone, and a saturated icon on it reads as a UI
     * element pasted over the game instead of a thing lying on the plaza.
     */
    private static final Map<PowerUpType, Color> POWERUP_COLOURS =
            new EnumMap<>(PowerUpType.class);

    static {
        // Cold pale blue — the field has stopped.
        POWERUP_COLOURS.put(PowerUpType.TIME_FREEZE, new Color(0x8F, 0xCF, 0xE8));
        // Green-jade — the tide, slowed.
        POWERUP_COLOURS.put(PowerUpType.SLOW_TIDE, new Color(0x7F, 0xC4, 0xA0));
        // Ember orange — the causeway swept.
        POWERUP_COLOURS.put(PowerUpType.PURGE, new Color(0xE8, 0x92, 0x54));
        // Lotus red — a life returned.
        POWERUP_COLOURS.put(PowerUpType.MEND, new Color(0xE0, 0x76, 0x90));
        // Temple gold — the naga ward, matching the life pips it stands in for.
        POWERUP_COLOURS.put(PowerUpType.NAGA_SHIELD, HUD_DIVIDER);
    }

    /** The accent colour for a boon. Falls back to gold for anything unmapped. */
    public static Color powerUp(PowerUpType type) {
        return POWERUP_COLOURS.getOrDefault(type, HUD_TEXT_GOLD);
    }

    private Palette() {
        // Constants only.
    }

    /** Returns {@code base} at the given alpha, 0 to 1. */
    public static Color alpha(Color base, double alpha) {
        int a = (int) Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
    }
}
