package com.guardiansofangkor.entities;

import com.guardiansofangkor.matching.WordTarget;

/**
 * A cursed bolt hurled at the temple, carrying a short word.
 *
 * <p>Dev brief Section 5.2: projectiles are time-critical and preempt enemies as
 * the active typing target. That is handled by {@code TargetResolver} checking
 * the projectile list first — a Yeak throwing at you forces a decision, because
 * you must break off whatever word you were typing to intercept.
 *
 * <p>Words are short (2-3 characters) precisely because the time budget is
 * short; a long word here would be undodgeable rather than tense.
 */
public class Projectile implements WordTarget {

    /** What threw this, which decides whether it can be typed away at all. */
    public enum Kind {
        /**
         * A Yeak's cursed bolt. Carries a short word and is cleared by typing
         * it, preempting whatever the player was already part-way through.
         */
        CURSED_BOLT,

        /**
         * Boss venom. Typeable, like everything else — deflected by typing the
         * word it carries.
         *
         * <p>It only works because the finale's verse is typed one word at a
         * time: the buffer is always a partial word, so the prefix matcher can
         * weigh a bolt and the verse's next word against the same keystrokes.
         * The words are drawn to never collide with the verse's own.
         *
         * <p>Kept as a separate kind from a Yeak's bolt because it is drawn
         * differently, flies far more slowly, and is spawned by a phase rather
         * than by an enemy.
         */
        VENOM
    }

    private final String word;
    private final Kind kind;

    private final double startX;
    private final double startY;
    private final double targetX;
    private final double targetY;

    /** Total ticks the flight should take. Shrinks with level. */
    private final int flightTicks;

    /**
     * Fractional so a Slow Tide genuinely slows a bolt in flight. Rounding this
     * to whole ticks would let a slowed bolt arrive at full speed, which reads
     * as the boon being broken rather than as a design choice.
     */
    private double ticksFlown;

    private boolean alive = true;
    private boolean landed;

    /**
     * True only on the tick the bolt lands.
     *
     * <p>Separate from {@link #landed} on purpose: a sticky flag would make
     * GameState charge the player a life on every tick of the fade-out, draining
     * the whole run from one missed bolt.
     */
    private boolean justLanded;

    private int hitFlashTicks;
    private int defeatTicks;

    /** A typeable cursed bolt. */
    public Projectile(String word,
                      double startX, double startY,
                      double targetX, double targetY,
                      int flightTicks) {
        this(word, startX, startY, targetX, targetY, flightTicks, Kind.CURSED_BOLT);
    }

    public Projectile(String word,
                      double startX, double startY,
                      double targetX, double targetY,
                      int flightTicks, Kind kind) {
        this.kind = kind == null ? Kind.CURSED_BOLT : kind;
        if (word == null || word.isEmpty()) {
            throw new IllegalArgumentException("a projectile must carry a word");
        }
        this.word = word;
        this.startX = startX;
        this.startY = startY;
        this.targetX = targetX;
        this.targetY = targetY;
        this.flightTicks = Math.max(1, flightTicks);
    }

    /** Advances one tick at full pace. */
    public void update() {
        update(1.0);
    }

    /**
     * Advances one tick, scaled by how fast the world is running — 0 under a
     * Time Freeze, a fraction under a Slow Tide.
     *
     * <p>The fade after interception is deliberately not scaled: it is feedback,
     * not threat, and a frozen death animation looks like a stall.
     */
    public void update(double timeScale) {
        justLanded = false;

        if (hitFlashTicks > 0) {
            hitFlashTicks--;
        }
        if (!alive) {
            defeatTicks++;
            return;
        }

        double scale = Math.max(0.0, timeScale);
        if (scale <= 0.0001) {
            return;
        }

        ticksFlown += scale;
        if (ticksFlown >= flightTicks) {
            landed = true;
            justLanded = true;
            alive = false;
        }
    }

    /** Flight progress, 0 at the throwing hand and 1 at the temple. */
    public double getProgress() {
        return Math.min(1.0, ticksFlown / flightTicks);
    }

    public double getX() {
        return startX + (targetX - startX) * getProgress();
    }

    /**
     * Y along a shallow arc rather than a straight line — a bolt that travels
     * dead straight reads as a UI element, not a thrown object.
     */
    public double getY() {
        double t = getProgress();
        double linear = startY + (targetY - startY) * t;
        double arc = Math.sin(t * Math.PI) * 60;
        return linear - arc;
    }

    /**
     * True only on the tick it reached the temple without being typed. Costs the
     * player a life, exactly once.
     */
    public boolean hasJustLanded() {
        return justLanded;
    }

    /** True once it has landed, and stays true. For rendering the impact. */
    public boolean hasLanded() {
        return landed;
    }

    /** Cleared by the player before it landed. */
    public void intercept() {
        this.alive = false;
    }

    public boolean isExpired(int defeatAnimationTicks) {
        return !alive && defeatTicks >= defeatAnimationTicks;
    }

    public void flashHit(int ticks) {
        this.hitFlashTicks = Math.max(this.hitFlashTicks, ticks);
    }

    @Override
    public String getWord() {
        return word;
    }

    @Override
    public boolean isActive() {
        return alive;
    }

    public Kind getKind() {
        return kind;
    }

    /** True when this is boss venom rather than a thrown bolt. */
    public boolean isVenom() {
        return kind == Kind.VENOM;
    }

    public int getHitFlashTicks() {
        return hitFlashTicks;
    }

    public int getDefeatTicks() {
        return defeatTicks;
    }

    /** Rotation in radians so the sprite points along its flight path. */
    public double getHeading() {
        double t = getProgress();
        double dx = targetX - startX;
        double dy = (targetY - startY) - Math.cos(t * Math.PI) * Math.PI * 60;
        return Math.atan2(dy, dx);
    }

    @Override
    public String toString() {
        return "Projectile[\"" + word + "\"]";
    }
}
