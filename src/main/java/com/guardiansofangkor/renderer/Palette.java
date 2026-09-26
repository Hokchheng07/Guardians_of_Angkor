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

    // ---- design tokens -----------------------------------------------------
    //
    // The five golds and three stones the Figma design is written in. Everything
    // below is expressed in terms of these rather than repeating hex literals,
    // so retuning the palette is one edit here.

    /** Primary accent — borders, rules, glow. */
    public static final Color GOLD = new Color(0xD4, 0xAF, 0x37);

    /** Bright highlight and text on dark. */
    public static final Color GOLD_LIGHT = new Color(0xF7, 0xD1, 0x6E);

    /** Muted flourishes and inactive ornament. */
    public static final Color GOLD_DIM = new Color(0x9A, 0x7B, 0x3A);

    /** Secondary text and idle button labels. */
    public static final Color GOLD_MID = new Color(0xC9, 0xA8, 0x4C);

    /** Subtitles and captions. */
    public static final Color GOLD_WARM = new Color(0xA0, 0x88, 0x40);

    /** Deepest panel background. */
    public static final Color STONE_DARK = new Color(0x1E, 0x19, 0x14);

    /** Button hover/selected background. */
    public static final Color STONE_MID = new Color(0x30, 0x20, 0x18);

    /** Modal background, top of the gradient. */
    public static final Color STONE_LIGHT = new Color(0x24, 0x19, 0x0F);

    /** Modal background, middle of the gradient. */
    public static final Color STONE_MODAL_MID = new Color(0x1E, 0x15, 0x10);

    /** Modal background, bottom of the gradient. */
    public static final Color STONE_MODAL_LOW = new Color(0x1A, 0x12, 0x08);

    /** Stat labels and the footer line — gold pushed almost to brown. */
    public static final Color GOLD_FAINT = new Color(0x7A, 0x60, 0x30);

    /** Stat values that are not the highlighted one. */
    public static final Color GOLD_VALUE = new Color(0xD4, 0xB8, 0x6A);

    /** The version string under the menu — the quietest text in the game. */
    public static final Color GOLD_GHOST = new Color(0x6B, 0x55, 0x30);

    /** Mortar lines in the worn-stone texture overlay. */
    public static final Color STONE_MORTAR = new Color(0x8B, 0x73, 0x55);

    /** Surface cracks in the worn-stone texture overlay. */
    public static final Color STONE_CRACK = new Color(0x6B, 0x55, 0x40);

    // ---- chrome ------------------------------------------------------------

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

    /**
     * The dim behind the end-of-run card.
     *
     * <p>Darker and cooler than {@link #SCRIM} — the design pairs it with a
     * backdrop blur, and without the blur the scrim has to do that separation on
     * its own.
     */
    public static final Color MODAL_SCRIM = new Color(0x08, 0x05, 0x02, 184);

    /** Failure accent. */
    public static final Color DANGER = new Color(0xD9, 0x63, 0x54);

    /**
     * The bright cut of the failure accent.
     *
     * <p>Exists so the end-of-run card can be written once and take either
     * palette: a won run uses gold/gold-light, a lost one danger/danger-light,
     * and every glow, rule and bracket in that layout reads the pair rather than
     * branching on the outcome.
     */
    public static final Color DANGER_LIGHT = new Color(0xF2, 0x93, 0x86);

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

    /**
     * Mixes two chrome colours, {@code t} of the way from {@code from} to
     * {@code to}.
     *
     * <p>Here rather than in whichever renderer wanted it first, for the same
     * reason the colours themselves are: a second copy of this would eventually
     * round differently, and two parts of one frame drifting apart is exactly
     * what keeping colour in one file prevents. Alpha is interpolated too, so
     * fading between a solid and a transparent colour behaves.
     */
    public static Color blend(Color from, Color to, double t) {
        double mix = Math.max(0, Math.min(1, t));
        return new Color(
                (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * mix),
                (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * mix),
                (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * mix),
                (int) Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * mix));
    }
}
