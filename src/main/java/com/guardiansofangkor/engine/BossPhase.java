package com.guardiansofangkor.engine;

import java.util.Random;

/**
 * What the final boss is doing right now.
 *
 * <p>Separate from {@link BossFight.Phase}, which is the fight's lifecycle —
 * rising, briefing, fighting, dying. This is the attack pattern <em>inside</em>
 * the fighting part, and only exists while that lasts.
 *
 * <p>The finale used to have exactly one behaviour: spit venom on a five-to-ten
 * second timer, forever. It read as a single long attrition check, and because
 * every bolt competed with the verse for the same keyboard the whole fight
 * pulled in one direction. Phases give the fight a shape — a stretch of bolts,
 * then a stretch of monsters walking in, then something else — so the player is
 * being asked a different question every few seconds rather than the same one
 * for two minutes.
 *
 * <p>Phases are picked at random rather than rotated. A fixed order is a rhythm
 * the player memorises by the second cycle, and a boss whose next move is known
 * is a boss that is no longer reacted to.
 */
public enum BossPhase {

    /**
     * Venom, carrying words, for as long as the phase lasts.
     *
     * <p>The bolts keep coming until the phase timer runs out, even if the
     * player has already finished the paragraph that started it. Letting a fast
     * typist cut the barrage short would make speed a way to skip the fight
     * rather than a way to win it.
     */
    PROJECTILE("Venom Barrage"),

    /**
     * Shadow spirits called up out of the plaza, scaled to the tier.
     *
     * <p>They are ordinary enemies and are typed down like ordinary enemies,
     * which is the point: it drags the rest of the game's vocabulary into the
     * finale instead of leaving the last two minutes as a pure prose exercise.
     */
    MINIONS("Summoning");

    private final String displayName;

    BossPhase(String displayName) {
        this.displayName = displayName;
    }

    /** Short label for the HUD, e.g. above the boss health bar. */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * A phase at random, never repeating the one just finished.
     *
     * <p>Excluding the previous phase matters more than it looks: with only two
     * phases built, a plain uniform roll would sit on the same one twice in a
     * row a quarter of the time, and a "new phase" that is visibly the old phase
     * reads as the transition having failed. As more phases are added this
     * degrades naturally into ordinary random selection.
     */
    public static BossPhase rollAfter(BossPhase previous, Random random) {
        BossPhase[] all = values();
        if (all.length == 1) {
            return all[0];
        }
        Random source = random == null ? new Random() : random;

        BossPhase chosen;
        do {
            chosen = all[source.nextInt(all.length)];
        } while (chosen == previous);
        return chosen;
    }
}
