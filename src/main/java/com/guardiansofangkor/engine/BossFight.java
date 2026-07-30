package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.EnemyType;
import com.guardiansofangkor.matching.WordTarget;
import com.guardiansofangkor.util.GameConfig;
import com.guardiansofangkor.util.GraphemeCounter;

import java.util.Collections;
import java.util.List;

/**
 * The finale: one enormous monster at the centre of the plaza and a paragraph
 * to type at it.
 *
 * <p>Deliberately not another {@code Enemy}. Everything the roster does — walk a
 * route, breach the temple, die to a word — is wrong here. The boss stands
 * still, cannot be reached, and is beaten by sustained typing rather than by a
 * single word. Forcing that into the enemy class would mean an enemy that
 * ignores its own movement, its own hitbox and its own word, which is not an
 * enemy any more.
 *
 * <p>The paragraph is delivered in three stages, one sentence at a time. Thirty
 * words dumped on screen at once reads as a punishment rather than a fight;
 * revealed a sentence at a time it has a rhythm, and each cleared sentence is a
 * beat where the player can see they are winning.
 *
 * <p>A mistype resets the current sentence. That is harsher than the rest of the
 * game, and it is the point — the finale is the one place accuracy is supposed
 * to matter more than speed. It only ever costs the sentence in progress, never
 * a stage already cleared.
 *
 * <p>Implements {@link WordTarget} so {@link GameState} can hand it back through
 * the ordinary {@code ResolveResult}, which is what lets the input field's
 * existing typo-flash and clear-on-complete behaviour work unchanged.
 */
public class BossFight implements WordTarget {

    /** Where the fight is in its own little story. */
    public enum Phase {
        /** Rising into place. Nothing is typed and no venom flies yet. */
        ARRIVING,

        /** The paragraph is live. */
        FIGHTING,

        /** Beaten, playing its death. The run is not won until this ends. */
        FALLING,

        /** Finished. GameState turns this into a victory. */
        DONE
    }

    /** What a keystroke did. */
    public enum Result {
        /** Nothing to report — empty buffer, or the fight is not accepting input. */
        NONE,

        /** A correct letter. */
        PROGRESS,

        /** A wrong letter. The current sentence is back to the start. */
        TYPO,

        /** The sentence is done and the next one is up. */
        STAGE_CLEARED,

        /** The last sentence is done. The boss is falling. */
        DEFEATED
    }

    /** How long the boss takes to rise before it will accept a keystroke. */
    public static final int ARRIVAL_TICKS = GameConfig.TARGET_FPS * 2;

    /** How long the death plays before the victory screen. */
    public static final int DEATH_TICKS = GameConfig.TARGET_FPS * 2;

    /** Ticks between venom spits at the reference tuning, before escalation. */
    private static final int VENOM_INTERVAL_TICKS = 170;

    /** Fraction knocked off the venom interval for each sentence cleared. */
    private static final double VENOM_ESCALATION = 0.18;

    /** However fast it escalates, never quicker than this. */
    private static final int VENOM_INTERVAL_FLOOR = 70;

    /** Grace after arriving before the first spit, so stage one is readable. */
    private static final int FIRST_VENOM_DELAY = GameConfig.TARGET_FPS * 3;

    /** How long the boss flashes when a sentence lands. */
    private static final int HIT_FLASH_TICKS = GameConfig.TARGET_FPS / 2;

    private final EnemyType type;
    private final List<String> sentences;
    private final Difficulty difficulty;

    private Phase phase = Phase.ARRIVING;
    private double phaseTicks;

    private int stage;
    private String typed = "";

    private double venomCooldown = FIRST_VENOM_DELAY;

    /**
     * True for exactly one tick, when a spit is due.
     *
     * <p>One-shot for the same reason {@code Projectile.hasJustLanded()} is: a
     * sticky flag would spawn a bolt on every frame for the rest of the fight.
     */
    private boolean venomDue;

    private int hitFlashTicks;
    private int typoFlashTicks;

    public BossFight(EnemyType type, List<String> sentences, Difficulty difficulty) {
        if (type == null) {
            throw new IllegalArgumentException("a boss needs a type");
        }
        if (sentences == null || sentences.isEmpty()) {
            throw new IllegalArgumentException("a boss needs something to type");
        }
        this.type = type;
        this.sentences = List.copyOf(sentences);
        this.difficulty = difficulty == null ? Difficulty.reference() : difficulty;
    }

    // ---- simulation --------------------------------------------------------

    /** Advances one tick at full pace. */
    public void update() {
        update(1.0);
    }

    /**
     * Advances one tick, scaled by how fast the world is running.
     *
     * <p>A Time Freeze collected before the boss arrived still holds here, and
     * still stops the venom — the boon was earned, and quietly cancelling it at
     * the door would be worse than it being briefly very strong.
     */
    public void update(double timeScale) {
        double scale = Math.max(0.0, timeScale);

        venomDue = false;
        if (hitFlashTicks > 0) {
            hitFlashTicks--;
        }
        if (typoFlashTicks > 0) {
            typoFlashTicks--;
        }

        switch (phase) {
            case ARRIVING -> {
                phaseTicks++;
                if (phaseTicks >= ARRIVAL_TICKS) {
                    phase = Phase.FIGHTING;
                    phaseTicks = 0;
                }
            }
            case FIGHTING -> {
                phaseTicks++;
                if (scale <= 0.0001) {
                    return;
                }
                venomCooldown -= scale;
                if (venomCooldown <= 0) {
                    venomDue = true;
                    venomCooldown = venomIntervalTicks();
                }
            }
            case FALLING -> {
                phaseTicks++;
                if (phaseTicks >= DEATH_TICKS) {
                    phase = Phase.DONE;
                }
            }
            case DONE -> {
                // Nothing left to do; GameState has already taken the win.
            }
        }
    }

    /**
     * How often venom comes, in ticks.
     *
     * <p>Tightens as sentences fall, so the last stage is the loudest — and is
     * widened by the tier's spawn-interval scale, so Easy's boss gives the same
     * proportionally generous room the rest of an Easy run does.
     */
    public int venomIntervalTicks() {
        double escalated = VENOM_INTERVAL_TICKS * (1.0 - VENOM_ESCALATION * stage);
        double tiered = escalated * difficulty.getSpawnIntervalScale();
        return Math.max(VENOM_INTERVAL_FLOOR, (int) Math.round(tiered));
    }

    /** True for exactly one tick, when a venom bolt should be spawned. */
    public boolean isVenomDue() {
        return venomDue;
    }

    // ---- typing ------------------------------------------------------------

    /**
     * Feeds the whole input buffer at the current sentence.
     *
     * <p>Buffer-at-a-time rather than character-at-a-time for the same reason
     * the rest of the game works that way: Khmer input arrives from the
     * document listener as multi-codepoint edits, and a per-character API would
     * break on it.
     */
    public Result submit(String buffer) {
        if (phase != Phase.FIGHTING) {
            return Result.NONE;
        }
        String input = buffer == null ? "" : buffer;
        if (input.isEmpty()) {
            typed = "";
            return Result.NONE;
        }

        String want = currentSentence();
        if (!want.startsWith(input)) {
            // Back to the start of this sentence — but only this sentence.
            // Stages already cleared stay cleared, or one slip at word thirty
            // would undo the whole fight.
            typed = "";
            typoFlashTicks = GameConfig.TYPO_FLASH_TICKS;
            return Result.TYPO;
        }

        typed = input;
        if (!input.equals(want)) {
            return Result.PROGRESS;
        }

        typed = "";
        stage++;
        hitFlashTicks = HIT_FLASH_TICKS;

        if (stage >= sentences.size()) {
            phase = Phase.FALLING;
            phaseTicks = 0;
            return Result.DEFEATED;
        }
        return Result.STAGE_CLEARED;
    }

    // ---- what the renderer and GameState ask --------------------------------

    /** The sentence currently being typed. The last one once the boss is down. */
    public String currentSentence() {
        return sentences.get(Math.min(stage, sentences.size() - 1));
    }

    /** The part of the current sentence already typed correctly. */
    public String getTyped() {
        return typed;
    }

    /** The part still to go. */
    public String getRemaining() {
        String want = currentSentence();
        return want.startsWith(typed) ? want.substring(typed.length()) : want;
    }

    /** Every sentence, so the renderer can show how many stages there are. */
    public List<String> getSentences() {
        return Collections.unmodifiableList(sentences);
    }

    /** Sentences cleared so far. */
    public int getStage() {
        return stage;
    }

    public int getStageCount() {
        return sentences.size();
    }

    /**
     * Health remaining, 1 at the start and 0 when it falls.
     *
     * <p>Counts part-typed progress within the current sentence, not just whole
     * stages: a bar that only moves three times in a two-minute fight tells the
     * player nothing while they are actually typing.
     */
    public double getHealthFraction() {
        if (phase == Phase.FALLING || phase == Phase.DONE) {
            return 0;
        }
        String want = currentSentence();
        double within = want.isEmpty()
                ? 0
                : Math.min(1.0, typed.length() / (double) want.length());
        double done = (stage + within) / (double) sentences.size();
        return Math.max(0.0, Math.min(1.0, 1.0 - done));
    }

    /** Progress through the current sentence, 0 to 1. Drives the stage bar. */
    public double getSentenceProgress() {
        String want = currentSentence();
        return want.isEmpty() ? 0 : Math.min(1.0, typed.length() / (double) want.length());
    }

    /** Characters in the whole paragraph, for the words-per-minute tally. */
    public int totalCharacters() {
        int total = 0;
        for (String sentence : sentences) {
            total += GraphemeCounter.count(sentence);
        }
        return total;
    }

    public EnemyType getType() {
        return type;
    }

    public Phase getPhase() {
        return phase;
    }

    public boolean isArriving() {
        return phase == Phase.ARRIVING;
    }

    /** True while the paragraph is live and keystrokes count. */
    public boolean isFighting() {
        return phase == Phase.FIGHTING;
    }

    /** True once the death animation has finished and the run can be won. */
    public boolean isFinished() {
        return phase == Phase.DONE;
    }

    /** True once beaten, whether or not the death has finished playing. */
    public boolean isBeaten() {
        return phase == Phase.FALLING || phase == Phase.DONE;
    }

    /** Progress through the arrival or the death, 0 to 1. For the renderer. */
    public double getPhaseProgress() {
        int duration = switch (phase) {
            case ARRIVING -> ARRIVAL_TICKS;
            case FALLING -> DEATH_TICKS;
            default -> 0;
        };
        return duration <= 0 ? 1.0 : Math.min(1.0, phaseTicks / duration);
    }

    /** Ticks the boss has existed, for idle animation. */
    public double getTicks() {
        return phaseTicks;
    }

    public int getHitFlashTicks() {
        return hitFlashTicks;
    }

    public int getTypoFlashTicks() {
        return typoFlashTicks;
    }

    /** Where venom leaves the boss — roughly its mouth. */
    public double getVenomOriginX() {
        return GameConfig.TEMPLE_CENTER_X;
    }

    public double getVenomOriginY() {
        return GameConfig.GROUND_LINE_Y - GameConfig.BOSS_HEIGHT * 0.72;
    }

    // ---- WordTarget --------------------------------------------------------

    @Override
    public String getWord() {
        return currentSentence();
    }

    @Override
    public boolean isActive() {
        return phase == Phase.FIGHTING;
    }

    @Override
    public String toString() {
        return "BossFight[" + type.getDisplayName() + " " + (stage + 1)
                + "/" + sentences.size() + "]";
    }
}
