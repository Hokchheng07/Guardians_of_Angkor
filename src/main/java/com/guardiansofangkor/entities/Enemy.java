package com.guardiansofangkor.entities;

import com.guardiansofangkor.matching.WordTarget;
import com.guardiansofangkor.util.GameConfig;

/**
 * A shadow spirit advancing on the temple with a word above its head.
 *
 * <p>Deliberately a single concrete class configured by an {@link EnemyType}
 * rather than a subclass per monster — composition over inheritance, per the
 * team conventions.
 *
 * <p>Enemies march horizontally in from the left or right screen edge toward the
 * temple entrance at the centre. Grounded types keep their feet on the plaza;
 * floating types hover above it. This class holds gameplay state only and knows
 * nothing about Graphics2D — the animation counters below are plain numbers the
 * renderer reads and turns into transforms.
 */
public class Enemy implements WordTarget {

    private final EnemyType type;
    private final String word;

    /** Horizontal centre of the sprite. */
    private double x;

    /**
     * The anchor Y. For grounded types this is where the feet rest; for floating
     * types it is the centre of the sprite before bobbing is applied.
     */
    private final double anchorY;

    /** +1 marching rightward (spawned on the left), -1 marching leftward. */
    private final int direction;

    private final double baseSpeed;

    private boolean alive = true;

    /** Ticks since spawn — drives the idle bob phase so enemies desynchronise. */
    private long ticksAlive;

    /** Counts down after a correct keystroke; non-zero means "draw a hit flash". */
    private int hitFlashTicks;

    /** Counts up once defeated; drives the scale-down + fade-out. */
    private int defeatTicks;

    /**
     * @param x         starting horizontal centre, usually just off-screen
     * @param direction +1 to march right, -1 to march left
     * @param baseSpeed pixels per tick before the type's speed multiplier
     */
    public Enemy(EnemyType type, String word, double x, int direction, double baseSpeed) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (word == null || word.isEmpty()) {
            throw new IllegalArgumentException("word must not be null or empty");
        }
        this.type = type;
        this.word = word;
        this.x = x;
        this.direction = direction >= 0 ? 1 : -1;
        this.baseSpeed = baseSpeed;
        this.anchorY = type.isGrounded()
                ? GameConfig.GROUND_LINE_Y
                : GameConfig.GROUND_LINE_Y - type.getHoverHeight();
    }

    /** Advances one tick. Called by GameState, never by the renderer. */
    public void update() {
        ticksAlive++;

        if (hitFlashTicks > 0) {
            hitFlashTicks--;
        }

        if (!alive) {
            defeatTicks++;
            return;
        }

        x += direction * baseSpeed * type.getSpeedMultiplier();
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

    public double getX() {
        return x;
    }

    /**
     * Feet position for grounded types, pre-bob sprite centre for floating types.
     * The renderer decides how to anchor the sprite from this.
     */
    public double getAnchorY() {
        return anchorY;
    }

    /** +1 if marching rightward, -1 if leftward. Renderer uses this to face the sprite. */
    public int getDirection() {
        return direction;
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
