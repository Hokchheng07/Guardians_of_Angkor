package com.guardiansofangkor.entities;

import com.guardiansofangkor.matching.WordTarget;
import com.guardiansofangkor.util.GameConfig;

/**
 * A shadow spirit converging on the temple with a word above its head.
 *
 * <p>Deliberately a single concrete class configured by an {@link EnemyType}
 * rather than a subclass per monster — composition over inheritance, per the
 * team conventions.
 *
 * <p>Enemies materialise in a puff of smoke back along a 45-degree line from the
 * temple and walk down-and-inward toward it. Because they start further away
 * they are also drawn smaller, growing to full size as they arrive — that depth
 * cue is what stops the diagonal from looking like sliding.
 *
 * <p>This class holds gameplay state only and knows nothing about Graphics2D.
 * The animation counters below are plain numbers the renderer reads and turns
 * into transforms.
 */
public class Enemy implements WordTarget {

    private final EnemyType type;
    private final ApproachPath path;
    private final String word;

    private double x;

    /**
     * Current Y of the anchor. For grounded types this is where the feet rest;
     * for floating types it is the sprite centre before bobbing. It rises toward
     * the ground line as the enemy approaches.
     */
    private double y;

    private final double spawnX;
    private final double spawnY;
    private final double targetY;

    /** +1 marching rightward (spawned on the left), -1 marching leftward. */
    private final int direction;

    private double speed;

    private boolean alive = true;

    /** Ticks since spawn — drives the idle bob phase so enemies desynchronise. */
    private long ticksAlive;

    private int hitFlashTicks;
    private int defeatTicks;

    // ---- ranged attack state ----------------------------------------------

    private AttackPhase attackPhase = AttackPhase.NONE;
    private int attackPhaseTicks;
    private int throwCooldown;
    private boolean projectileDue;

    /**
     * @param path      which route this enemy takes to the temple
     * @param run       path distance back from the temple to materialise at
     * @param direction +1 to march right, -1 to march left
     * @param speed     pixels per tick along the path
     */
    public Enemy(EnemyType type, ApproachPath path, String word,
                 double run, int direction, double speed) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (word == null || word.isEmpty()) {
            throw new IllegalArgumentException("word must not be null or empty");
        }
        this.type = type;
        this.path = path;
        this.word = word;
        this.direction = direction >= 0 ? 1 : -1;
        this.speed = speed;

        this.targetY = type.anchorTargetY();

        this.spawnX = path.spawnX(this.direction, run);
        this.spawnY = path.spawnY(this.targetY, run);
        this.x = spawnX;
        this.y = spawnY;

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

        advanceAttack();

        // A throwing enemy plants itself to wind up rather than walking through
        // the animation — otherwise the throw reads as a stumble.
        if (attackPhase == AttackPhase.NONE) {
            march();
        }
    }

    private void march() {
        x += direction * speed * path.unitX();

        if (y < targetY) {
            y = Math.min(targetY, y + speed * path.unitY());
        }
    }

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
        return x + direction * (getScaledWidthHint() * 0.25);
    }

    /** Vertical release point — roughly shoulder height. */
    public double getThrowOriginY() {
        return y - (type.getTargetHeight() * depthScale() * 0.62);
    }

    private double getScaledWidthHint() {
        return type.getTargetHeight() * depthScale() * 0.5;
    }

    /**
     * How far along its approach this enemy is, 0 at the spawn puff and 1 at the
     * temple. Drives the depth scale.
     */
    public double getApproachProgress() {
        double span = targetY - spawnY;
        if (span <= 0.0001) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, (y - spawnY) / span));
    }

    /**
     * Render scale from distance. Far enemies are drawn smaller, which is what
     * makes the diagonal read as walking toward the temple instead of sliding
     * down the screen.
     *
     * <p>Full size is reached at {@link GameConfig#DEPTH_FULL_SIZE_AT} rather
     * than at the very end of the path. Enemies breach a
     * {@code BREACH_RADIUS} short of the temple centre, so scaling all the way
     * to 1.0 at the centre would mean they were never actually drawn at full
     * size before being removed.
     */
    public double depthScale() {
        double eased = Math.min(1.0, getApproachProgress() / GameConfig.DEPTH_FULL_SIZE_AT);
        return GameConfig.DEPTH_SCALE_MIN + (1.0 - GameConfig.DEPTH_SCALE_MIN) * eased;
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
    }

    /** True once the defeat animation has finished and this can be culled. */
    public boolean isExpired(int defeatAnimationTicks) {
        return !alive && defeatTicks >= defeatAnimationTicks;
    }

    @Override
    public String getWord() {
        return word;
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
        return type.getDisplayName() + "[\"" + word + "\"]";
    }
}
