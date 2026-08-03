package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.EnemyType;
import com.guardiansofangkor.matching.WordTarget;
import com.guardiansofangkor.util.GameConfig;
import com.guardiansofangkor.util.GraphemeCounter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

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
 * <p>The paragraph is delivered one sentence at a time. Thirty words dumped on
 * screen at once reads as a punishment rather than a fight; revealed a sentence
 * at a time it has a rhythm, and each cleared sentence is a beat where the
 * player can see they are winning.
 *
 * <p>Sentences are grouped into <em>paragraphs</em> — see
 * {@link #getSentencesPerParagraph()} — and a finished paragraph is what changes
 * the boss's attack. The whole script is the boss's health: Easy asks for two
 * paragraphs of two sentences twice over, Medium and Hard for three of three
 * three times over. The grouping is a plain integer rather than a nested list
 * because everything else here — the verse panel, the health bar, the venom
 * exclusion list — works on a flat run of sentences, and nesting the data would
 * have meant rewriting all of it to express one boundary.
 *
 * <p>What the boss is <em>doing</em> between those boundaries is a
 * {@link BossPhase}, on its own seven-to-ten second timer. The two clocks are
 * deliberately independent: the phase runs its full length whether or not the
 * player has finished the paragraph, so typing fast wins the fight sooner
 * without letting the player skip the part where the boss fights back.
 *
 * <p>Within a verse the player types ONE WORD AT A TIME and the input field
 * clears between them, exactly as it does after killing an enemy. That is not
 * cosmetic: venom bolts carry words too, and if the verse were typed as one
 * continuous string there would be no moment mid-verse at which a venom word
 * could be started. Word-at-a-time keeps the buffer a partial word at all
 * times, so the ordinary prefix matcher can weigh the verse's next word and
 * every bolt in the air against the same keystrokes.
 *
 * <p>The word only advances on an explicit SPACE, not the moment its last
 * letter lands. Auto-advancing there was tried first and it broke the
 * player's own rhythm: prose is typed word-then-space, and a field that
 * clears itself out from under a keystroke the player was already about to
 * make (the space) leaves that space landing on an empty buffer instead,
 * which reads as the first letter of a word that is not there yet.
 *
 * <p>A mistype resets the current sentence. That is harsher than the rest of the
 * game, and it is the point — the finale is the one place accuracy is supposed
 * to matter more than speed. It only ever costs the sentence in progress, never
 * a stage already cleared.
 *
 * <p>Implements {@link WordTarget} so {@link GameState} can put it straight into
 * {@code TargetResolver} alongside the venom, which is what makes the two share
 * one keyboard without either of them needing a mode switch.
 */
public class BossFight implements WordTarget {

    /** Where the fight is in its own little story. */
    public enum Phase {
        /** Rising into place. Nothing is typed and no venom flies yet. */
        ARRIVING,

        /**
         * Risen, but held while the rules of the fight are on screen.
         *
         * <p>Its own phase rather than a banner drawn over a live fight,
         * because the overlay covers the verse: leaving the fight running
         * under it would ask the player to type a sentence they cannot see,
         * and spit at them for the privilege. Nothing is typeable and no venom
         * flies, exactly as during the rise.
         */
        BRIEFING,

        /** The paragraph is live. */
        FIGHTING,

        /** Beaten, playing its death. The run is not won until this ends. */
        FALLING,

        /** Finished. GameState turns this into a victory. */
        DONE
    }

    /**
     * Which half of the fight's rhythm the boss is in.
     *
     * <p>The finale alternates strictly: the player types a paragraph, the boss
     * answers with a phase, and neither happens while the other is running. The
     * paragraph is taken off screen for the duration of a phase, which is the
     * whole reason the two are separate states rather than two clocks running at
     * once — asking the player to read a sentence while dodging is asking them
     * to do two things with one pair of eyes, and the earlier version of this
     * fight did exactly that.
     */
    public enum Stance {
        /** The paragraph is up and typeable. The boss is not attacking. */
        TYPING,

        /** A phase is running. The paragraph is hidden and cannot be typed. */
        ATTACKING
    }

    /** What a keystroke did. */
    public enum Result {
        /** Nothing to report — empty buffer, or the fight is not accepting input. */
        NONE,

        /** A correct letter. */
        PROGRESS,

        /** A wrong letter. The current verse is back to the start. */
        TYPO,

        /** A word of the verse is done and the next word is up. */
        WORD_CLEARED,

        /** The verse is done and the next one is up. */
        STAGE_CLEARED,

        /**
         * A whole paragraph is done, so the boss changes what it is doing.
         *
         * <p>Distinct from {@link #STAGE_CLEARED} because callers react to it
         * differently, and because a caller that only knows about sentences
         * would silently treat a paragraph boundary as an ordinary one. Every
         * paragraph boundary is also a sentence boundary, so anything that only
         * cares about the sentence should handle both.
         */
        PARAGRAPH_CLEARED,

        /** The last sentence is done. The boss is falling. */
        DEFEATED
    }

    /** How long the boss takes to rise before it will accept a keystroke. */
    public static final int ARRIVAL_TICKS = GameConfig.TARGET_FPS * 2;

    /**
     * How long the rules of the fight are held on screen before play resumes.
     *
     * <p>Five seconds, which is a long time to hold a game still and is the
     * point: the finale changes three rules at once (words confirm on space,
     * orbs are typed down, a slip costs the verse) and the alternative to
     * saying so is letting the first mistake teach it.
     */
    public static final int BRIEFING_TICKS = GameConfig.TARGET_FPS * 5;

    /** How long the death plays before the victory screen. */
    public static final int DEATH_TICKS = GameConfig.TARGET_FPS * 2;

    /** Grace after the briefing before the first spit, so verse one is readable. */
    private static final int FIRST_VENOM_DELAY = GameConfig.TARGET_FPS * 3;

    /** Shortest an attack phase runs before the boss changes what it is doing. */
    public static final int PHASE_MIN_TICKS = GameConfig.TARGET_FPS * 7;

    /**
     * Longest an attack phase runs.
     *
     * <p>Seven to ten seconds. Short enough that the fight keeps turning over,
     * long enough that a phase is a stretch of play with its own shape rather
     * than a flicker — under about five seconds the summoning phase cannot get
     * its monsters across the plaza before it is over, which makes the whole
     * phase read as decoration.
     */
    public static final int PHASE_MAX_TICKS = GameConfig.TARGET_FPS * 10;

    /**
     * Monsters a summoning phase calls up at the reference tuning.
     *
     * <p>Scaled by the tier's enemy-count scale like an ordinary wave, so the
     * finale gets lighter on Easy for the same reason every other level does
     * rather than needing its own table.
     */
    private static final int MINIONS_PER_PHASE = 6;

    /** How long the boss flashes when a verse lands. */
    private static final int HIT_FLASH_TICKS = GameConfig.TARGET_FPS / 2;

    private final EnemyType type;
    private final List<String> sentences;

    /** Each verse pre-split into its words, since that is how it is typed. */
    private final List<List<String>> verseWords;

    /**
     * How many sentences make one paragraph — the boss's phase boundary.
     *
     * <p>At least one and never more than the whole script, so a mis-set tier
     * cannot produce a boss whose attack never changes or one that changes on
     * every word.
     */
    private final int sentencesPerParagraph;

    private final Difficulty difficulty;
    private final Random random;

    private Phase phase = Phase.ARRIVING;
    private double phaseTicks;

    private int stage;

    /** Words of the current verse already cleared. */
    private int wordIndex;

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

    /** Which half of the rhythm the fight is in. Only meaningful while FIGHTING. */
    private Stance stance = Stance.TYPING;

    /** Which attack is running, or null whenever the boss is not attacking. */
    private BossPhase attackPhase;

    /**
     * The attack that ran last, remembered across the typing window.
     *
     * <p>Separate from {@link #attackPhase} because that one is nulled the
     * moment a phase ends — the renderer must not keep drawing an attack that
     * is over. Without somewhere to keep the previous value, every roll would
     * see a null predecessor and the same attack could come up twice running,
     * which reads as the phase having failed to change.
     */
    private BossPhase lastAttackPhase;

    /** True for exactly one tick, when a phase has just finished. */
    private boolean phaseJustEnded;

    private double attackPhaseTicks;

    /** Length rolled for the current phase, in ticks. */
    private int attackPhaseDuration = PHASE_MIN_TICKS;

    /** Phases finished so far, for the renderer and for tests. */
    private int phasesElapsed;

    private double minionCooldown;

    /** True for exactly one tick, when a summon is due. One-shot like the spit. */
    private boolean minionDue;

    /**
     * A boss whose whole script is one paragraph.
     *
     * <p>{@code Integer.MAX_VALUE} is clamped down to the script's length by the
     * full constructor, which is exactly "however many sentences there are".
     * That keeps this the pre-phase behaviour — the attack only ever changes on
     * its own timer — so callers that do not care about paragraphs are not
     * silently opted into a phase change on every sentence.
     */
    public BossFight(EnemyType type, List<String> sentences, Difficulty difficulty) {
        this(type, sentences, Integer.MAX_VALUE, difficulty, new Random());
    }

    /** Seeded constructor so the attack rhythm is reproducible in tests. */
    public BossFight(EnemyType type, List<String> sentences, Difficulty difficulty,
                     Random random) {
        this(type, sentences, Integer.MAX_VALUE, difficulty, random);
    }

    /**
     * The full constructor.
     *
     * @param sentencesPerParagraph how many sentences the boss treats as one
     *                              paragraph, which is how often it changes
     *                              attack. Clamped into range rather than
     *                              rejected — a boss that refuses to exist is
     *                              worse than one whose phases are the wrong
     *                              length.
     */
    public BossFight(EnemyType type, List<String> sentences, int sentencesPerParagraph,
                     Difficulty difficulty, Random random) {
        if (type == null) {
            throw new IllegalArgumentException("a boss needs a type");
        }
        if (sentences == null || sentences.isEmpty()) {
            throw new IllegalArgumentException("a boss needs something to type");
        }
        this.type = type;
        this.sentences = List.copyOf(sentences);
        this.sentencesPerParagraph =
                Math.max(1, Math.min(sentencesPerParagraph, this.sentences.size()));
        this.difficulty = difficulty == null ? Difficulty.reference() : difficulty;
        this.random = random == null ? new Random() : random;

        List<List<String>> split = new ArrayList<>(this.sentences.size());
        for (String sentence : this.sentences) {
            List<String> words = new ArrayList<>();
            for (String word : sentence.split("\\s+")) {
                if (!word.isEmpty()) {
                    words.add(word);
                }
            }
            if (words.isEmpty()) {
                throw new IllegalArgumentException("a verse needs at least one word");
            }
            split.add(List.copyOf(words));
        }
        this.verseWords = List.copyOf(split);
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
        minionDue = false;
        phaseJustEnded = false;
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
                    phase = Phase.BRIEFING;
                    phaseTicks = 0;
                }
            }
            case BRIEFING -> {
                // Deliberately not scaled by timeScale. A Time Freeze running
                // into the boss door must not stretch the briefing to a minute,
                // and freezing a screen that is already held would look like
                // the game had hung.
                phaseTicks++;
                if (phaseTicks >= BRIEFING_TICKS) {
                    phase = Phase.FIGHTING;
                    phaseTicks = 0;
                    // The fight opens on a paragraph, not on an attack. The
                    // briefing has just explained the rules and the player
                    // should get to use them before being shot at.
                    stance = Stance.TYPING;
                }
            }
            case FIGHTING -> {
                phaseTicks++;
                if (scale <= 0.0001) {
                    return;
                }
                if (stance == Stance.ATTACKING) {
                    updateAttackPhase(scale);
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

    // ---- attack phases -----------------------------------------------------

    /**
     * Runs the current phase's clock and its own attack.
     *
     * <p>Only ever called while the boss is {@link Stance#ATTACKING}. When the
     * timer runs out the boss goes quiet and the next paragraph comes up — the
     * two stances strictly alternate, and neither can interrupt the other.
     */
    private void updateAttackPhase(double scale) {
        attackPhaseTicks += scale;
        if (attackPhaseTicks >= attackPhaseDuration) {
            endAttackPhase();
            return;
        }

        switch (attackPhase) {
            case PROJECTILE -> {
                venomCooldown -= scale;
                if (venomCooldown <= 0) {
                    venomDue = true;
                    venomCooldown = venomIntervalTicks();
                }
            }
            case MINIONS -> {
                minionCooldown -= scale;
                if (minionCooldown <= 0) {
                    minionDue = true;
                    minionCooldown = minionIntervalTicks();
                }
            }
        }
    }

    /**
     * The boss stops attacking and hands the floor back to the player.
     *
     * <p>{@link #isPhaseJustEnded()} goes true for one tick so {@link GameState}
     * can clear whatever is still on the field. That sweep is what makes the
     * typing window safe, and it is a deliberate, reversible choice rather than
     * a rule of the design — see that method.
     */
    private void endAttackPhase() {
        phasesElapsed++;
        stance = Stance.TYPING;
        lastAttackPhase = attackPhase;
        attackPhase = null;
        attackPhaseTicks = 0;
        phaseJustEnded = true;
    }

    /**
     * Starts an attack phase: rolls its length and resets what it attacks with.
     *
     * <p>Called when a paragraph is finished, never on a timer — the paragraph
     * is what provokes the boss.
     */
    private void beginAttackPhase() {
        stance = Stance.ATTACKING;
        attackPhase = BossPhase.rollAfter(lastAttackPhase, random);
        attackPhaseTicks = 0;
        attackPhaseDuration = rollPhaseDuration();

        // Both cooldowns start short so a phase attacks almost immediately.
        // Rolling a full interval here would let a seven-second phase pass with
        // nothing in it, which reads as the boss having skipped its turn — and
        // now that the paragraph is off screen for the duration, an empty phase
        // is simply the game doing nothing at all.
        double opening = GameConfig.TARGET_FPS / 2.0;

        venomCooldown = attackPhase == BossPhase.PROJECTILE
                ? opening
                : venomIntervalTicks();
        minionCooldown = attackPhase == BossPhase.MINIONS
                ? opening
                : minionIntervalTicks();
    }

    /** A fresh seven-to-ten seconds. */
    private int rollPhaseDuration() {
        int span = PHASE_MAX_TICKS - PHASE_MIN_TICKS;
        return PHASE_MIN_TICKS + random.nextInt(Math.max(1, span + 1));
    }

    /** How many monsters one summoning phase calls up on this tier. */
    public int minionsPerPhase() {
        double scaled = MINIONS_PER_PHASE * difficulty.getEnemyCountScale();
        return Math.max(2, (int) Math.round(scaled));
    }

    /**
     * Ticks between summons, so a phase's monsters arrive spread across it
     * rather than all on the first frame.
     */
    public int minionIntervalTicks() {
        return Math.max(GameConfig.TARGET_FPS / 2, PHASE_MIN_TICKS / minionsPerPhase());
    }

    /** True for exactly one tick, when a monster should be summoned. */
    public boolean isMinionDue() {
        return minionDue;
    }

    /**
     * True for exactly one tick, when an attack phase has just ended.
     *
     * <p>{@link GameState} answers this by clearing everything the phase left on
     * the field, which is what makes the typing window that follows completely
     * safe. That sweep is a tuning decision, not a rule of the design: the
     * alternation still works if the leftovers are allowed to stand, and the
     * only change needed to try it is to stop acting on this flag. It is a
     * separate one-shot signal rather than a check on the stance precisely so
     * that choice lives in one place.
     */
    public boolean isPhaseJustEnded() {
        return phaseJustEnded;
    }

    /** Which half of the rhythm the fight is in. */
    public Stance getStance() {
        return stance;
    }

    /** True while the paragraph is on screen and the boss is holding off. */
    public boolean isTyping() {
        return phase == Phase.FIGHTING && stance == Stance.TYPING;
    }

    /** True while a phase is running and the paragraph is off screen. */
    public boolean isAttacking() {
        return phase == Phase.FIGHTING && stance == Stance.ATTACKING;
    }

    /** What the boss is doing, or null whenever it is not attacking. */
    public BossPhase getAttackPhase() {
        return attackPhase;
    }

    /** Progress through the current attack phase, 0 to 1. For the renderer. */
    public double getAttackPhaseProgress() {
        if (attackPhase == null || attackPhaseDuration <= 0) {
            return 0;
        }
        return Math.min(1.0, attackPhaseTicks / attackPhaseDuration);
    }

    /** How many attack phases have ended so far. */
    public int getPhasesElapsed() {
        return phasesElapsed;
    }

    /**
     * How long until the next spit, in ticks.
     *
     * <p>A fresh random five to ten seconds each time rather than a metronome.
     * A fixed cadence turns into a rhythm the player memorises and stops
     * reacting to; an unpredictable one keeps them watching the sky, which is
     * the whole reason the venom is there.
     *
     * <p>Not scaled by the tier. The window is generous enough at both ends
     * that stretching it further on Easy would leave the boss barely attacking
     * at all, and the difficulty already lives in the length of the paragraph.
     */
    public int venomIntervalTicks() {
        int span = GameConfig.VENOM_INTERVAL_MAX_TICKS
                - GameConfig.VENOM_INTERVAL_MIN_TICKS;
        return GameConfig.VENOM_INTERVAL_MIN_TICKS + random.nextInt(Math.max(1, span + 1));
    }

    /**
     * How long a bolt takes to arrive on this tier.
     *
     * <p>Scaled by the tier's spawn-interval scale, which is the number that
     * means "how much time the player is given" everywhere else in the game.
     * The gap between attacks is deliberately not scaled — stretching a
     * five-to-ten-second window further would leave Easy's boss barely
     * attacking — so this is where a gentler tier's extra room comes from.
     */
    public int venomFlightTicks() {
        double scaled = GameConfig.VENOM_FLIGHT_TICKS * difficulty.getSpawnIntervalScale();

        // Capped to land inside the phase that threw it. Bolts used to outlive
        // their own phase on the gentler tiers — Easy stretches the flight to
        // over eight seconds and a phase can end after seven — so the sweep
        // that clears the field for the next paragraph was quietly disarming
        // most of the barrage. A threat the player never has to answer is not a
        // threat, and worse, it teaches them to ignore the one that follows.
        int mustLandBy = PHASE_MIN_TICKS - GameConfig.TARGET_FPS;
        int capped = Math.min((int) Math.round(scaled), mustLandBy);
        return Math.max(GameConfig.TARGET_FPS * 2, capped);
    }

    /** True for exactly one tick, when a venom bolt should be spawned. */
    public boolean isVenomDue() {
        return venomDue;
    }

    /** True while the rules of the fight are held on screen. */
    public boolean isBriefing() {
        return phase == Phase.BRIEFING;
    }

    /** Progress through the briefing, 0 to 1. Drives the overlay's fade. */
    public double getBriefingProgress() {
        return BRIEFING_TICKS <= 0 ? 1.0
                : Math.min(1.0, phaseTicks / (double) BRIEFING_TICKS);
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
        if (phase != Phase.FIGHTING || stance != Stance.TYPING) {
            // Mid-phase the paragraph is not on screen. Accepting keystrokes
            // against a sentence the player cannot see — and charging them a
            // verse reset for guessing wrong — is the exact trap the briefing
            // phase exists to avoid, so the same rule applies here.
            return Result.NONE;
        }
        String input = buffer == null ? "" : buffer;
        if (input.isEmpty()) {
            typed = "";
            return Result.NONE;
        }

        String want = currentWord();

        // The space is the real, deliberate keystroke that moves the verse on —
        // see the class comment. It is checked before the prefix test because
        // "want + space" is not a prefix of want; it is want with one more
        // character the player chose to type.
        if (input.equals(want + " ")) {
            return confirmWord();
        }

        if (!want.startsWith(input)) {
            resetVerse();
            return Result.TYPO;
        }

        // Fully typed but not yet confirmed sits here too — want.startsWith(want)
        // is true — so the word shows as complete while still waiting on the
        // space that actually advances it.
        typed = input;
        return Result.PROGRESS;
    }

    /** Confirms the word just typed — the space landed — and moves on. */
    private Result confirmWord() {
        // The input field clears itself on a COMPLETED result, which is what
        // leaves the buffer empty and ready for either the next word of the
        // verse or a bolt that has arrived in the meantime.
        typed = "";
        wordIndex++;
        if (wordIndex < currentVerseWords().size()) {
            return Result.WORD_CLEARED;
        }

        wordIndex = 0;
        stage++;
        hitFlashTicks = HIT_FLASH_TICKS;

        if (stage >= sentences.size()) {
            phase = Phase.FALLING;
            phaseTicks = 0;
            return Result.DEFEATED;
        }

        // A finished paragraph is what provokes the boss. Finishing a sentence
        // inside one does not: the paragraph is the unit of damage, and a phase
        // that turned over every sentence would never last long enough for its
        // own monsters to arrive.
        if (stage % sentencesPerParagraph == 0) {
            beginAttackPhase();
            return Result.PARAGRAPH_CLEARED;
        }
        return Result.STAGE_CLEARED;
    }

    /**
     * Throws the current verse away and starts it again.
     *
     * <p>Back to the start of this verse — but only this verse. Stages already
     * cleared stay cleared, or one slip on the last word of a paragraph would
     * undo the whole fight.
     */
    public void resetVerse() {
        typed = "";
        wordIndex = 0;
        typoFlashTicks = GameConfig.TYPO_FLASH_TICKS;
    }

    /**
     * Records how much of the current word is typed, for the verse panel.
     *
     * <p>Separate from {@link #submit} because the two answer different
     * questions. {@code submit} decides what a finished word <em>did</em>;
     * this only says what the player currently has on screen, which the
     * renderer needs on every keystroke including the ones that turn out to be
     * aimed at a venom bolt instead.
     */
    public void trackTyping(String buffer) {
        String input = buffer == null ? "" : buffer;
        typed = currentWord().startsWith(input) ? input : "";
    }

    /** Forgets the partial word, without touching verse progress. */
    public void clearTyping() {
        typed = "";
    }

    /** The word the player is on right now. This is what the matcher sees. */
    public String currentWord() {
        List<String> words = currentVerseWords();
        return words.get(Math.min(wordIndex, words.size() - 1));
    }

    /** Words of the current verse, in order. */
    public List<String> currentVerseWords() {
        return verseWords.get(Math.min(stage, verseWords.size() - 1));
    }

    /** How many words of the current verse are already done. */
    public int getWordIndex() {
        return wordIndex;
    }

    /**
     * Every word this fight will ask for that has not been cleared yet.
     *
     * <p>Handed to the venom spawner as an exclusion list. A bolt carrying a
     * word the verse also wants would make one set of keystrokes mean two
     * different things at once, which is exactly what the word-at-a-time
     * arrangement exists to prevent.
     */
    public List<String> remainingWords() {
        List<String> remaining = new ArrayList<>();
        for (int verse = stage; verse < verseWords.size(); verse++) {
            List<String> words = verseWords.get(verse);
            int from = verse == stage ? wordIndex : 0;
            for (int i = from; i < words.size(); i++) {
                remaining.add(words.get(i));
            }
        }
        return remaining;
    }

    // ---- what the renderer and GameState ask --------------------------------

    /** The sentence currently being typed. The last one once the boss is down. */
    public String currentSentence() {
        return sentences.get(Math.min(stage, sentences.size() - 1));
    }

    /** The part of the current WORD already typed correctly. */
    public String getTyped() {
        return typed;
    }

    /** The part of the current word still to go. */
    public String getRemaining() {
        String want = currentWord();
        return want.startsWith(typed) ? want.substring(typed.length()) : want;
    }

    /**
     * How much of the current verse is behind the player, 0 to 1.
     *
     * <p>Counts whole cleared words plus the fraction of the word in progress,
     * so the renderer can split the verse into gold and white at exactly the
     * character the player is on.
     */
    public int getClearedCharacters() {
        List<String> words = currentVerseWords();
        int cleared = 0;
        for (int i = 0; i < wordIndex && i < words.size(); i++) {
            // Plus one for the space that follows each cleared word.
            cleared += words.get(i).length() + 1;
        }
        return cleared + typed.length();
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

    // ---- paragraphs --------------------------------------------------------

    /** Sentences the boss treats as one paragraph. Its phase boundary. */
    public int getSentencesPerParagraph() {
        return sentencesPerParagraph;
    }

    /** Which paragraph the player is on, counting from zero. */
    public int getParagraphIndex() {
        return Math.min(stage / sentencesPerParagraph, getParagraphCount() - 1);
    }

    /**
     * Paragraphs in the whole fight.
     *
     * <p>Rounded up so a script whose length is not an exact multiple still
     * reports its trailing part-paragraph rather than losing it.
     */
    public int getParagraphCount() {
        return (sentences.size() + sentencesPerParagraph - 1) / sentencesPerParagraph;
    }

    /** Paragraphs already finished. */
    public int getParagraphsCleared() {
        return stage / sentencesPerParagraph;
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
        double done = (stage + getSentenceProgress()) / (double) sentences.size();
        return Math.max(0.0, Math.min(1.0, 1.0 - done));
    }

    /** Progress through the current verse, 0 to 1. Drives the stage bar. */
    public double getSentenceProgress() {
        int length = currentSentence().length();
        return length == 0 ? 0 : Math.min(1.0, getClearedCharacters() / (double) length);
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

    /** Progress through the current timed phase, 0 to 1. For the renderer. */
    public double getPhaseProgress() {
        int duration = switch (phase) {
            case ARRIVING -> ARRIVAL_TICKS;
            case BRIEFING -> BRIEFING_TICKS;
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
        return GameConfig.BOSS_BASE_Y - GameConfig.BOSS_HEIGHT * 0.72;
    }

    // ---- WordTarget --------------------------------------------------------

    /**
     * What the prefix matcher compares keystrokes against — the current word,
     * not the whole verse. See the class comment: this is what lets a venom
     * bolt and the verse share one buffer.
     */
    @Override
    public String getWord() {
        return currentWord();
    }

    /**
     * Only a target while the paragraph is actually on screen.
     *
     * <p>Mid-phase the verse is not typeable at all, so the matcher must not see
     * it — otherwise a keystroke aimed at a venom bolt could resolve against a
     * sentence the player cannot read.
     */
    @Override
    public boolean isActive() {
        return isTyping();
    }

    @Override
    public String toString() {
        return "BossFight[" + type.getDisplayName() + " verse " + (stage + 1)
                + "/" + sentences.size() + " word " + (wordIndex + 1) + "]";
    }
}
