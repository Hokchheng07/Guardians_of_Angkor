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
 */
public class BossFight implements WordTarget {

    public enum Phase {
        ARRIVING,
        BRIEFING,
        FIGHTING,
        FALLING,
        DONE
    }

    public enum Stance {
        TYPING,
        ATTACKING
    }

    public enum Result {
        NONE,
        PROGRESS,
        TYPO,
        WORD_CLEARED,
        STAGE_CLEARED,
        PARAGRAPH_CLEARED,
        DEFEATED
    }

    public static final int ARRIVAL_TICKS = GameConfig.TARGET_FPS * 2;
    public static final int BRIEFING_TICKS = GameConfig.TARGET_FPS * 5;
    public static final int DEATH_TICKS = GameConfig.TARGET_FPS * 2;
    private static final int FIRST_VENOM_DELAY = GameConfig.TARGET_FPS * 3;
    public static final int PHASE_STUCK_TICKS = GameConfig.TARGET_FPS * 60;
    private static final int MINIONS_PER_PHASE = 3;
    private static final int VENOM_PER_PHASE = 3;
    private static final int ATTACKS_ADDED_PER_PHASE = 1;
    private static final int MAX_ATTACKS_PER_PHASE = 8;
    private static final int HIT_FLASH_TICKS = GameConfig.TARGET_FPS / 2;

    private final EnemyType type;
    private final List<String> sentences;
    private final List<List<String>> verseWords;
    private final int sentencesPerParagraph;
    private final Difficulty difficulty;
    private final Random random;

    private Phase phase = Phase.ARRIVING;
    private double phaseTicks;
    private int stage;
    private int wordIndex;
    private String typed = "";

    private double venomCooldown = FIRST_VENOM_DELAY;
    private boolean venomDue;
    private int hitFlashTicks;
    private int typoFlashTicks;

    private Stance stance = Stance.TYPING;
    private BossPhase attackPhase;
    private BossPhase lastAttackPhase;
    private boolean phaseJustEnded;
    private double attackPhaseTicks;
    private int attacksScheduled;
    private int attacksSent;
    private int liveAttacks;
    private int phasesElapsed;

    private double harassCooldown;
    private int harassmentSent;
    private final int harassmentPerFight;
    private double minionCooldown;
    private boolean minionDue;

    public BossFight(EnemyType type, List<String> sentences, Difficulty difficulty) {
        this(type, sentences, Integer.MAX_VALUE, difficulty, new Random());
    }

    public BossFight(EnemyType type, List<String> sentences, Difficulty difficulty, Random random) {
        this(type, sentences, Integer.MAX_VALUE, difficulty, random);
    }

    public BossFight(EnemyType type, List<String> sentences, int sentencesPerParagraph,
                     Difficulty difficulty, Random random) {
        if (type == null) throw new IllegalArgumentException("a boss needs a type");
        if (sentences == null || sentences.isEmpty()) throw new IllegalArgumentException("a boss needs something to type");

        this.type = type;
        this.sentences = List.copyOf(sentences);
        this.sentencesPerParagraph = Math.max(1, Math.min(sentencesPerParagraph, this.sentences.size()));
        this.difficulty = difficulty == null ? Difficulty.reference() : difficulty;
        this.random = random == null ? new Random() : random;

        int paragraphs = Math.max(1, this.sentences.size() / this.sentencesPerParagraph);

        // REAM EYSO MECHANIC: He shoots at you constantly while you type!
        if (this.type.name().equals("REAM_EYSO")) {
            this.harassmentPerFight = 99; // Basically infinite harassment
        } else {
            this.harassmentPerFight = (int) Math.round(paragraphs * this.difficulty.getEnemyCountScale());
        }

        this.harassCooldown = harassIntervalTicks();

        List<List<String>> split = new ArrayList<>(this.sentences.size());
        for (String sentence : this.sentences) {
            List<String> words = new ArrayList<>();
            for (String word : sentence.split("\\s+")) {
                if (!word.isEmpty()) words.add(word);
            }
            if (words.isEmpty()) throw new IllegalArgumentException("a verse needs at least one word");
            split.add(List.copyOf(words));
        }
        this.verseWords = List.copyOf(split);
    }

    public void update() {
        update(1.0);
    }

    public void update(double timeScale) {
        double scale = Math.max(0.0, timeScale);

        venomDue = false;
        minionDue = false;
        phaseJustEnded = false;
        if (hitFlashTicks > 0) hitFlashTicks--;
        if (typoFlashTicks > 0) typoFlashTicks--;

        switch (phase) {
            case ARRIVING -> {
                phaseTicks++;
                if (phaseTicks >= ARRIVAL_TICKS) { phase = Phase.BRIEFING; phaseTicks = 0; }
            }
            case BRIEFING -> {
                phaseTicks++;
                if (phaseTicks >= BRIEFING_TICKS) { phase = Phase.FIGHTING; phaseTicks = 0; stance = Stance.TYPING; }
            }
            case FIGHTING -> {
                phaseTicks++;
                if (scale <= 0.0001) return;

                if (stance == Stance.ATTACKING) {
                    updateAttackPhase(scale);
                } else {
                    updateHarassment(scale);
                }
            }
            case FALLING -> {
                phaseTicks++;
                if (phaseTicks >= DEATH_TICKS) phase = Phase.DONE;
            }
            case DONE -> {}
        }
    }

    private void updateAttackPhase(double scale) {
        attackPhaseTicks += scale;

        if (attacksSent >= attacksScheduled && liveAttacks <= 0) {
            endAttackPhase();
            return;
        }

        if (attackPhaseTicks >= PHASE_STUCK_TICKS) {
            System.err.println("[BossFight] " + attackPhase + " ran "
                    + (int) (attackPhaseTicks / GameConfig.TARGET_FPS) + "s with "
                    + attacksSent + "/" + attacksScheduled + " sent and "
                    + liveAttacks + " still live — ending it to unstick the run.");
            endAttackPhase();
            return;
        }

        if (attacksSent >= attacksScheduled) return;

        switch (attackPhase) {
            case PROJECTILE -> {
                venomCooldown -= scale;
                if (venomCooldown <= 0) {
                    venomDue = true;
                    attacksSent++;
                    venomCooldown = venomIntervalTicks();
                }
            }
            case MINIONS -> {
                minionCooldown -= scale;
                if (minionCooldown <= 0) {
                    minionDue = true;
                    attacksSent++;
                    minionCooldown = minionIntervalTicks();
                }
            }
        }
    }

    private void updateHarassment(double scale) {
        if (harassmentPerFight <= 0 || harassmentSent >= harassmentPerFight) return;

        harassCooldown -= scale;
        if (harassCooldown <= 0) {
            venomDue = true;
            harassmentSent++;
            harassCooldown = harassIntervalTicks();
        }
    }

    private int harassIntervalTicks() {
        // REAM EYSO MECHANIC: Harass while the player is typing!
        if (type.name().equals("REAM_EYSO")) {
            // FIX: Increased the delay so he shoots much less often during the verse
            int base = GameConfig.TARGET_FPS * 4; // 4 seconds minimum
            int span = GameConfig.TARGET_FPS * 4; // Up to +4 seconds (8s total)
            return base + random.nextInt(Math.max(1, span + 1));
        }

        // Default harassment for other bosses
        int base = GameConfig.TARGET_FPS * 9;
        int span = GameConfig.TARGET_FPS * 7;
        return base + random.nextInt(Math.max(1, span + 1));
    }

    public void reportField(int liveAttacks) {
        this.liveAttacks = Math.max(0, liveAttacks);
    }

    private void endAttackPhase() {
        phasesElapsed++;
        stance = Stance.TYPING;
        lastAttackPhase = attackPhase;
        attackPhase = null;
        attackPhaseTicks = 0;
        attacksScheduled = 0;
        attacksSent = 0;
        liveAttacks = 0;
        phaseJustEnded = true;
    }

    private void beginAttackPhase() {
        stance = Stance.ATTACKING;
        attackPhase = BossPhase.rollAfter(lastAttackPhase, random);

        // REAM EYSO MECHANIC: Pure Bullet Hell! He NEVER summons minions, only shoots!
        if (type.name().equals("REAM_EYSO")) {
            attackPhase = BossPhase.PROJECTILE;
        }

        attackPhaseTicks = 0;
        attacksSent = 0;
        liveAttacks = 0;
        attacksScheduled = attacksFor(attackPhase);

        double opening = GameConfig.TARGET_FPS / 2.0;
        venomCooldown = attackPhase == BossPhase.PROJECTILE ? opening : venomIntervalTicks();
        minionCooldown = attackPhase == BossPhase.MINIONS ? opening : minionIntervalTicks();
    }

    private int attacksFor(BossPhase kind) {
        // REAM EYSO MECHANIC: Always exactly 5 rapid shots!
        if (type.name().equals("REAM_EYSO") && kind == BossPhase.PROJECTILE) {
            return 5;
        }

        int base = kind == BossPhase.MINIONS ? MINIONS_PER_PHASE : VENOM_PER_PHASE;
        double scaled = base * difficulty.getEnemyCountScale();
        int escalated = (int) Math.round(scaled) + phasesElapsed * ATTACKS_ADDED_PER_PHASE;
        return Math.max(2, Math.min(MAX_ATTACKS_PER_PHASE, escalated));
    }


    public int minionsPerPhase() {
        return attackPhase == BossPhase.MINIONS && attacksScheduled > 0
                ? attacksScheduled : attacksFor(BossPhase.MINIONS);
    }

    public int venomPerPhase() {
        return attackPhase == BossPhase.PROJECTILE && attacksScheduled > 0
                ? attacksScheduled : attacksFor(BossPhase.PROJECTILE);
    }

    public int attacksRemaining() {
        return Math.max(0, attacksScheduled - attacksSent);
    }

    public int objectiveRemaining() {
        return attacksRemaining() + Math.max(0, liveAttacks);
    }

    public int minionIntervalTicks() {
        return GameConfig.TARGET_FPS * 3 / 2;
    }

    public boolean isMinionDue() {
        return minionDue;
    }

    public boolean isPhaseJustEnded() {
        return phaseJustEnded;
    }

    public Stance getStance() {
        return stance;
    }

    public boolean isTyping() {
        return phase == Phase.FIGHTING && stance == Stance.TYPING;
    }

    public boolean isAttacking() {
        return phase == Phase.FIGHTING && stance == Stance.ATTACKING;
    }

    public BossPhase getAttackPhase() {
        return attackPhase;
    }

    public double getAttackPhaseProgress() {
        if (attackPhase == null || attacksScheduled <= 0) return 0;
        int outstanding = objectiveRemaining();
        int total = Math.max(attacksScheduled, outstanding);
        return Math.min(1.0, 1.0 - outstanding / (double) total);
    }

    public int getPhasesElapsed() {
        return phasesElapsed;
    }

    public int venomIntervalTicks() {
        // REAM EYSO MECHANIC: True machine-gun fire!
        if (type.name().equals("REAM_EYSO")) {
            return GameConfig.TARGET_FPS / 5; // Fires every 0.2 seconds! Very fast!
        }

        int span = GameConfig.VENOM_INTERVAL_MAX_TICKS - GameConfig.VENOM_INTERVAL_MIN_TICKS;
        return GameConfig.VENOM_INTERVAL_MIN_TICKS + random.nextInt(Math.max(1, span + 1));
    }

    public int venomFlightTicks() {
        double scaled = GameConfig.VENOM_FLIGHT_TICKS * difficulty.getSpawnIntervalScale();
        return Math.max(GameConfig.TARGET_FPS * 2, (int) Math.round(scaled));
    }

    public boolean isVenomDue() {
        return venomDue;
    }

    public boolean isBriefing() {
        return phase == Phase.BRIEFING;
    }

    public double getBriefingProgress() {
        return BRIEFING_TICKS <= 0 ? 1.0 : Math.min(1.0, phaseTicks / (double) BRIEFING_TICKS);
    }

    public Result submit(String buffer) {
        if (phase != Phase.FIGHTING || stance != Stance.TYPING) {
            return Result.NONE;
        }

        String input = buffer == null ? "" : buffer;
        if (input.isEmpty()) {
            typed = "";
            return Result.NONE;
        }

        String want = currentWord();

        if (input.equals(want + " ")) {
            return confirmWord();
        }

        if (!want.startsWith(input)) {
            resetVerse();
            return Result.TYPO;
        }

        typed = input;
        return Result.PROGRESS;
    }

    private Result confirmWord() {
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

        if (stage % sentencesPerParagraph == 0) {
            beginAttackPhase();
            return Result.PARAGRAPH_CLEARED;
        }
        return Result.STAGE_CLEARED;
    }

    public void resetVerse() {
        typed = "";
        wordIndex = 0;
        typoFlashTicks = GameConfig.TYPO_FLASH_TICKS;
    }

    public void trackTyping(String buffer) {
        String input = buffer == null ? "" : buffer;
        typed = currentWord().startsWith(input) ? input : "";
    }

    public void clearTyping() {
        typed = "";
    }

    public String currentWord() {
        List<String> words = currentVerseWords();
        return words.get(Math.min(wordIndex, words.size() - 1));
    }

    public List<String> currentVerseWords() {
        return verseWords.get(Math.min(stage, verseWords.size() - 1));
    }

    public int getWordIndex() {
        return wordIndex;
    }

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

    public String currentSentence() {
        String baseSentence = sentences.get(Math.min(stage, sentences.size() - 1));

        // APSARA MECHANIC: The Mesmerizer Smoke Pulse!
        if (type.name().equals("CORRUPTED_APSARA") && phase == Phase.FIGHTING && stance == Stance.TYPING) {

            // NEW BUFF: 4-second total loop.
            int cycle = (int) (phaseTicks % (GameConfig.TARGET_FPS * 4));

            // NEW BUFF: The smoke now lasts for 3 FULL SECONDS! (Only visible for 1 sec)
            boolean isSmoking = cycle < (GameConfig.TARGET_FPS * 3);

            if (isSmoking) {
                return applyApsaraSmoke(baseSentence);
            }
        }

        return baseSentence;
    }

    private String applyApsaraSmoke(String baseSentence) {
        StringBuilder smokedSentence = new StringBuilder();
        List<String> words = currentVerseWords();

        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);

            if (i <= wordIndex) {
                smokedSentence.append(word);
            } else {
                if (word.length() <= 2) {
                    smokedSentence.append(word);
                } else {
                    smokedSentence.append(word.charAt(0));
                    for (int j = 1; j < word.length() - 1; j++) {
                        smokedSentence.append("*");
                    }
                    smokedSentence.append(word.charAt(word.length() - 1));
                }
            }
            if (i < words.size() - 1) {
                smokedSentence.append(" ");
            }
        }
        return smokedSentence.toString();
    }

    public String getTyped() {
        return typed;
    }

    public String getRemaining() {
        String want = currentWord();
        return want.startsWith(typed) ? want.substring(typed.length()) : want;
    }

    public int getClearedCharacters() {
        List<String> words = currentVerseWords();
        int cleared = 0;
        for (int i = 0; i < wordIndex && i < words.size(); i++) {
            cleared += words.get(i).length() + 1;
        }
        return cleared + typed.length();
    }

    public List<String> getSentences() {
        return Collections.unmodifiableList(sentences);
    }

    public int getStage() {
        return stage;
    }

    public int getStageCount() {
        return sentences.size();
    }

    public int getSentencesPerParagraph() {
        return sentencesPerParagraph;
    }

    public int getParagraphIndex() {
        return Math.min(stage / sentencesPerParagraph, getParagraphCount() - 1);
    }

    public int getParagraphCount() {
        return (sentences.size() + sentencesPerParagraph - 1) / sentencesPerParagraph;
    }

    public int getParagraphsCleared() {
        return stage / sentencesPerParagraph;
    }

    public double getHealthFraction() {
        if (phase == Phase.FALLING || phase == Phase.DONE) return 0;
        double done = (stage + getSentenceProgress()) / (double) sentences.size();
        return Math.max(0.0, Math.min(1.0, 1.0 - done));
    }

    public double getSentenceProgress() {
        int length = currentSentence().length();
        return length == 0 ? 0 : Math.min(1.0, getClearedCharacters() / (double) length);
    }

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

    public boolean isFighting() {
        return phase == Phase.FIGHTING;
    }

    public boolean isFinished() {
        return phase == Phase.DONE;
    }

    public boolean isBeaten() {
        return phase == Phase.FALLING || phase == Phase.DONE;
    }

    public double getPhaseProgress() {
        int duration = switch (phase) {
            case ARRIVING -> ARRIVAL_TICKS;
            case BRIEFING -> BRIEFING_TICKS;
            case FALLING -> DEATH_TICKS;
            default -> 0;
        };
        return duration <= 0 ? 1.0 : Math.min(1.0, phaseTicks / duration);
    }

    public double getTicks() {
        return phaseTicks;
    }

    public int getHitFlashTicks() {
        return hitFlashTicks;
    }

    public int getTypoFlashTicks() {
        return typoFlashTicks;
    }

    public double getVenomOriginX() {
        return GameConfig.TEMPLE_CENTER_X;
    }

    public double getVenomOriginY() {
        return GameConfig.BOSS_BASE_Y - GameConfig.BOSS_HEIGHT * 0.72;
    }

    @Override
    public String getWord() {
        return currentWord();
    }

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