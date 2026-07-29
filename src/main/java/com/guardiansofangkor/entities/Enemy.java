package com.guardiansofangkor.entities;

import com.guardiansofangkor.matching.WordTarget;
import com.guardiansofangkor.util.GameConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A shadow spirit converging on the temple with a word above its head.
 *
 * <p>Deliberately a single concrete class configured by an {@link EnemyType}
 * rather than a subclass per monster — composition over inheritance, per the
 * team conventions.
 *
 * <p>Most enemies carry one word. Mini-bosses carry a <em>chain</em>: clearing
 * one word staggers them and reveals the next, and only the last one kills. The
 * chain lives here rather than in a Naga subclass so the same update, render and
 * matching paths serve both.
 *
 * <p>Movement is a straight line from the spawn puff to the temple, described by
 * a horizontal run and a vertical rise. See {@link ApproachPath} for why those
 * are separate rather than a single angle.
 *
 * <p>This class holds gameplay state only and knows nothing about Graphics2D.
 * The animation counters below are plain numbers the renderer reads and turns
 * into transforms.
 */
public class Enemy implements WordTarget {

    /** How long a mini-boss recoils after losing a word in its chain. */
    private static final int STAGGER_TICKS = 32;

    private final EnemyType type;
    private final ApproachPath path;

    /** Words to be typed in order. Length one for ordinary enemies. */
    private final List<String> wordChain;
    private int chainIndex;

    private double x;

    /**
     * Current Y of the anchor. For grounded types this is where the feet rest;
     * for floating types it is the sprite centre before bobbing. It descends
     * toward the arrival altitude as the enemy approaches.
     */
    private double y;

    private final double spawnX;
    private final double spawnY;
    private final double targetY;

    /** Unit travel vector, derived from the route's run and rise. */
    private final double unitX;
    private final double unitY;

    /** +1 marching rightward (spawned on the left), -1 marching leftward. */
    private final int direction;

    private double speed;

    private boolean alive = true;

    /** Ticks since spawn — drives the idle bob phase so enemies desynchronise. */
    private long ticksAlive;

    private int hitFlashTicks;
    private int defeatTicks;
    private int staggerTicks;

    // ---- ranged attack state ----------------------------------------------

    private AttackPhase attackPhase = AttackPhase.NONE;
    private int attackPhaseTicks;
    private int throwCooldown;
    private boolean projectileDue;

    /** Single-word constructor, for ordinary enemies. */
    public Enemy(EnemyType type, ApproachPath path, String word,
                 double run, int direction, double speed) {
        this(type, path, List.of(word == null ? "" : word), run, direction, speed);
    }

    /**
     * @param path      which route this enemy takes to the temple
     * @param words     the chain to type, in order; one entry for most enemies
     * @param run       horizontal distance back from the temple to materialise at
     * @param direction +1 to march right, -1 to march left
     * @param speed     pixels per tick along the path
     */
    public Enemy(EnemyType type, ApproachPath path, List<String> words,
                 double run, int direction, double speed) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (words == null || words.isEmpty()) {
            throw new IllegalArgumentException("an enemy needs at least one word");
        }
        for (String word : words) {
            if (word == null || word.isEmpty()) {
                throw new IllegalArgumentException("words must not be null or empty");
            }
        }

        this.type = type;
        this.path = path;
        this.wordChain = List.copyOf(words);
        this.direction = direction >= 0 ? 1 : -1;
        this.speed = speed;

        this.targetY = type.anchorTargetY();
        this.spawnX = path.spawnX(this.direction, run);
        this.spawnY = path.spawnY(this.targetY, run);
        this.x = spawnX;
        this.y = spawnY;

        // Normalise run and rise into a unit vector, so speed stays "pixels
        // along the path" regardless of how steep the route is.
        double rise = path.riseFor(run);
        double length = Math.hypot(run, rise);
        if (length < 0.0001) {
            this.unitX = 1;
            this.unitY = 0;
        } else {
            this.unitX = run / length;
            this.unitY = rise / length;
        }

        this.throwCooldown = type.getThrowIntervalTicks();
    }

    /** Advances one tick. Called by GameState, never by the renderer. */
    public void update() {
        ticksAlive++;
        projectileDue = false;

        if (hitFlashTicks > 0) {
            hitFlashTicks--;
        }

        if (!alive) {
            defeatTicks++;
            return;
        }

        if (staggerTicks > 0) {
            // Recoiling from losing a word — hold position so the player gets a
            // clear beat to read the next one.
            staggerTicks--;
            return;
        }

        advanceAttack();

        // A throwing enemy plants itself to wind up rather than walking through
        // the animation — otherwise the throw reads as a stumble.
        if (attackPhase == AttackPhase.NONE) {
            march();
        }
    }

    private void march() {
        x += direction * speed * unitX;

        if (y < targetY) {
            y = Math.min(targetY, y + speed * unitY);
        }
    }

    // ---- word chain --------------------------------------------------------

    /** True when clearing the current word will not yet kill this enemy. */
    public boolean hasMoreWords() {
        return chainIndex < wordChain.size() - 1;
    }

    /**
     * Moves to the next word in the chain and staggers.
     *
     * @return true if there was another word to move to
     */
    public boolean advanceChain() {
        if (!hasMoreWords()) {
            return false;
        }
        chainIndex++;
        staggerTicks = STAGGER_TICKS;
        return true;
    }

    /** How many words this enemy started with. One for ordinary enemies. */
    public int getChainLength() {
        return wordChain.size();
    }

    /** How many words have already been cleared. */
    public int getChainCleared() {
        return chainIndex;
    }

    /** True when this enemy takes more than one word to kill. */
    public boolean isChained() {
        return wordChain.size() > 1;
    }

    /** True while recoiling from a cleared word. */
    public boolean isStaggered() {
        return staggerTicks > 0;
    }

    /** Stagger progress, 1 at the moment of the hit down to 0. For the renderer. */
    public double getStaggerProgress() {
        return staggerTicks / (double) STAGGER_TICKS;
    }

    /**
     * Every word this enemy will ever show, so the spawner can avoid handing
     * another enemy a word that would collide later in this chain.
     */
    public List<String> getAllWords() {
        return Collections.unmodifiableList(wordChain);
    }

    // ---- ranged attacks ----------------------------------------------------

    private void advanceAttack() {
        if (!type.canThrow()) {
            return;
        }

        if (attackPhase == AttackPhase.NONE) {
            if (throwCooldown > 0) {
                throwCooldown--;
            } else if (isOnScreen()) {
                // Only start a throw once actually visible, so the player can
                // see the telegraph rather than being hit from off-screen.
                attackPhase = AttackPhase.WINDUP;
                attackPhaseTicks = 0;
            }
            return;
        }

        attackPhaseTicks++;
        if (attackPhaseTicks < attackPhase.durationTicks()) {
            return;
        }

        attackPhaseTicks = 0;
        switch (attackPhase) {
            case WINDUP -> {
                attackPhase = AttackPhase.RELEASE;
                projectileDue = true;
            }
            case RELEASE -> attackPhase = AttackPhase.RECOVER;
            case RECOVER -> {
                attackPhase = AttackPhase.NONE;
                throwCooldown = type.getThrowIntervalTicks();
            }
            case NONE -> {
                // Unreachable — guarded above.
            }
        }
    }

    private boolean isOnScreen() {
        return x > 60 && x < GameConfig.SCREEN_WIDTH - 60;
    }

    /**
     * True for exactly one tick, when the throw animation reaches its release
     * point and the projectile should be spawned.
     */
    public boolean isProjectileDue() {
        return projectileDue;
    }

    /** Where a thrown projectile leaves this enemy's hand. */
    public double getThrowOriginX() {
        return x + direction * (type.getTargetHeight() * depthScale() * 0.30);
    }

    /** Vertical release point — roughly shoulder height. */
    public double getThrowOriginY() {
        return y - (type.getTargetHeight() * depthScale() * 0.66);
    }

    // ---- geometry ----------------------------------------------------------

    /**
     * How far along its approach this enemy is, 0 at the spawn puff and 1 at the
     * temple. Drives the depth scale.
     */
    public double getApproachProgress() {
        double span = targetY - spawnY;
        if (span <= 0.0001) {
            // A flank route has no vertical travel, so fall back to horizontal.
            double horizontal = Math.abs(spawnX - GameConfig.TEMPLE_CENTER_X);
            if (horizontal <= 0.0001) {
                return 1.0;
            }
            double covered = Math.abs(x - spawnX);
            return Math.max(0.0, Math.min(1.0, covered / horizontal));
        }
        return Math.max(0.0, Math.min(1.0, (y - spawnY) / span));
    }

    /**
     * Render scale from distance. Far enemies are drawn smaller, which is what
     * makes an approach read as coming toward the temple rather than sliding
     * across it.
     *
     * <p>The floor comes from the route: a walker on its shallow plaza drift
     * barely shrinks, while a flyer descending 45 degrees from the sky shrinks a
     * lot. Full size is reached at {@link GameConfig#DEPTH_FULL_SIZE_AT} rather
     * than at the very end, because enemies breach a {@code BREACH_RADIUS} short
     * of the centre and would otherwise never be drawn at 100%.
     */
    public double depthScale() {
        double min = path.depthScaleMin();
        if (min >= 1.0) {
            return 1.0;
        }
        double eased = Math.min(1.0, getApproachProgress() / GameConfig.DEPTH_FULL_SIZE_AT);
        return min + (1.0 - min) * eased;
    }

    /**
     * True once this enemy has reached the temple entrance. The player loses a
     * life and the enemy is removed.
     */
    public boolean hasBreached() {
        return alive
                && Math.abs(x - GameConfig.TEMPLE_CENTER_X) <= GameConfig.BREACH_RADIUS;
    }

    /** Marks a correct keystroke so the renderer can flash this enemy. */
    public void flashHit(int ticks) {
        this.hitFlashTicks = Math.max(this.hitFlashTicks, ticks);
    }

    /** Marks this enemy defeated. It stays around briefly to play its death animation. */
    public void defeat() {
        this.alive = false;
        this.attackPhase = AttackPhase.NONE;
        this.staggerTicks = 0;
    }

    /** True once the defeat animation has finished and this can be culled. */
    public boolean isExpired(int defeatAnimationTicks) {
        return !alive && defeatTicks >= defeatAnimationTicks;
    }

    @Override
    public String getWord() {
        return wordChain.get(chainIndex);
    }

    @Override
    public boolean isActive() {
        return alive;
    }

    public EnemyType getType() {
        return type;
    }

    public ApproachPath getPath() {
        return path;
    }

    public double getX() {
        return x;
    }

    public double getAnchorY() {
        return y;
    }

    public double getSpawnX() {
        return spawnX;
    }

    public double getSpawnY() {
        return spawnY;
    }

    /** +1 if marching rightward, -1 if leftward. Renderer uses this to face the sprite. */
    public int getDirection() {
        return direction;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public AttackPhase getAttackPhase() {
        return attackPhase;
    }

    /** Progress through the current attack phase, 0 to 1. For the renderer's lean. */
    public double getAttackPhaseProgress() {
        int duration = attackPhase.durationTicks();
        return duration <= 0 ? 0 : Math.min(1.0, attackPhaseTicks / (double) duration);
    }

    public long getTicksAlive() {
        return ticksAlive;
    }

    public int getHitFlashTicks() {
        return hitFlashTicks;
    }

    public int getDefeatTicks() {
        return defeatTicks;
    }

    @Override
    public String toString() {
        String label = type.getDisplayName() + "[\"" + getWord() + "\"";
        if (isChained()) {
            label += " " + (chainIndex + 1) + "/" + wordChain.size();
        }
        return label + "]";
    }

    /** Convenience for tests and spawning. */
    public static List<String> chainOf(String... words) {
        return new ArrayList<>(List.of(words));
    }
}
