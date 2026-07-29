package com.guardiansofangkor.renderer;

import java.awt.Color;

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

    private Palette() {
        // Constants only.
    }

    /** Returns {@code base} at the given alpha, 0 to 1. */
    public static Color alpha(Color base, double alpha) {
        int a = (int) Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
    }
}
