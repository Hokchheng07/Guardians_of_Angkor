package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.Enemy;
import com.guardiansofangkor.entities.Player;
import com.guardiansofangkor.entities.PowerUp;
import com.guardiansofangkor.entities.PowerUpType;
import com.guardiansofangkor.entities.Projectile;
import com.guardiansofangkor.entities.VisualEffect;
import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.i18n.WordBank;
import com.guardiansofangkor.matching.ResolveResult;
import com.guardiansofangkor.matching.TargetResolver;
import com.guardiansofangkor.matching.WordTarget;
import com.guardiansofangkor.save.SaveData;
import com.guardiansofangkor.util.GameConfig;
import com.guardiansofangkor.util.GraphemeCounter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * All mutable gameplay state for a run, plus the per-tick update.
 *
 * <p>This class is pure logic — it must never import anything from
 * {@code java.awt} or {@code javax.swing}. The renderer reads from it; it never
 * reads from the renderer.
 */
public class GameState {

    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<PowerUp> powerUps = new ArrayList<>();
    private final List<VisualEffect> effects = new ArrayList<>();
    private final TargetResolver resolver = new TargetResolver();
    private final WaveManager waveManager;
    private final WordBank wordBank;
    private final Language language;
    private final Player player = new Player();

    /** Timed boons and banked shield charges. */
    private final PowerUpState powerUpState = new PowerUpState();

    /** The run of perfectly typed words, and the score multiplier it earns. */
    private final ComboTracker combo = new ComboTracker();

    /**
     * The finale, once the last wave has been cleared. Null for the whole rest
     * of a run, and on tiers that never end.
     */
    private BossFight boss;

    /** Drives drop rolls. Seedable so wave-and-drop composition is reproducible. */
    private final Random random;

    /**
     * Not final: chosen in the menu after this state exists, and a new run can
     * pick a different one. Changed only via {@link #restartWith(Difficulty)}.
     */
    private Difficulty difficulty;

    /**
     * The loading-and-countdown beat before play starts.
     *
     * <p>While this is active the simulation is skipped entirely, so nothing
     * walks and no clock runs — but the loop keeps ticking so the renderer can
     * draw it, the same arrangement pause uses.
     */
    private IntroSequence intro = new IntroSequence();

    private int score;

    /**
     * Lives, counted in halves.
     *
     * <p>An integer count of half-hearts rather than a fractional life, so a
     * flyer's half-hit and a walker's full hit can never disagree by a rounding
     * error about whether the run is over.
     */
    private int halfLives = GameConfig.STARTING_HALF_LIVES;
    private long elapsedTicks;
    private boolean running = true;
    private boolean gameOver;

    /**
     * True when the run ended by clearing the tier's last level rather than by
     * running out of lives.
     *
     * <p>Kept separate from {@link #gameOver} rather than inferred from "game
     * over with lives remaining". They are different events with different
     * screens, and the inference breaks the moment anything else can end a run.
     */
    private boolean victory;

    /** Boons collected this run, for the end-of-run summary. */
    private int powerUpsCollected;

    /**
     * True while the player has paused.
     *
     * <p>Kept separate from {@link #running} and handled by skipping the
     * simulation rather than by stopping the game loop. The loop still ticks, so
     * the renderer keeps painting and can show the pause overlay — stopping the
     * timer outright would freeze the last frame with no indication that
     * anything had happened, which reads as a crash.
     */
    private boolean paused;

    private int charactersTyped;
    private int enemiesDefeated;
    private int projectilesIntercepted;

    /**
     * Enemies dealt with in the current level, whether killed or lost to a
     * breach.
     *
     * <p>Counting breaches as well as kills is deliberate: the progress bar
     * reports how far through the level you are, not how well you are doing. If
     * it only counted kills, a single leaked enemy would leave the bar stuck
     * short of full for the rest of the level, which reads as a bug.
     */
    private int resolvedThisLevel;

    /** Tracks level changes so {@link #resolvedThisLevel} can be reset. */
    private int lastLevelSeen;

    private int bestScore;
    private int bestLevel;

    private boolean levelJustCleared;

    private ResolveResult lastResult;

    /**
     * What the player has typed right now during the finale.
     *
     * <p>The boss bypasses {@link TargetResolver}, so the resolver's own buffer
     * goes stale for the length of the fight. The renderer needs a live one to
     * split words into typed and untyped, and asking the wrong source would
     * leave every venom bolt looking untouched however much of it was typed.
     */
    private String bossBuffer = "";

    /** True for one tick after the typed buffer was dropped. See dropStaleBuffer. */
    private boolean bufferInvalidated;

    /**
     * Difficulty tiers beaten at least once, as word-bank keys.
     *
     * <p>The one piece of state here that is not about the current run, and the
     * reason it lives on {@code GameState} anyway is that winning is the event
     * that produces it. Deliberately untouched by {@link #restart()} — losing a
     * run must not cost the player a tier they already earned.
     */
    private final Set<String> clearedTiers = new LinkedHashSet<>();

    public GameState() {
        this(Language.ENGLISH, Difficulty.defaultChoice());
    }

    public GameState(Language language) {
        this(language, Difficulty.defaultChoice());
    }

    public GameState(Language language, Difficulty difficulty) {
        this(language, difficulty, new Random());
    }

    /** Seeded constructor so waves and drops are reproducible in tests. */
    public GameState(Language language, Difficulty difficulty, Random random) {
        this.language = language == null ? Language.ENGLISH : language;
        this.difficulty = difficulty == null ? Difficulty.defaultChoice() : difficulty;
        this.random = random == null ? new Random() : random;
        this.wordBank = new WordBank(this.language, this.random);
        this.waveManager = new WaveManager(this.wordBank, this.difficulty, this.random);
        this.intro = new IntroSequence(this.difficulty);
    }

    /** Advances the whole simulation one tick. */
    public void update() {
        levelJustCleared = false;

        if (paused || !running || gameOver) {
            return;
        }

        // The opening beat runs before anything else exists. Returning here
        // keeps elapsedTicks at zero, so the countdown does not count against
        // the player's words-per-minute.
        if (intro != null && intro.isActive()) {
            intro.update();
            return;
        }

        // The boss's briefing is a held beat with nothing typeable on screen.
        // Counting it would charge the player five seconds of words-per-minute
        // for a screen that forbids typing — the same reason the opening
        // countdown does not count against them either.
        if (boss == null || !boss.isBriefing()) {
            elapsedTicks++;
        }

        // One scale for the whole tick, read once. Asking the power-up state
        // per entity would let a boon expire half way through a frame and
        // advance the back half of the field further than the front half.
        double timeScale = powerUpState.getTimeScale();

        powerUpState.update();
        player.update();
        updateEffects();
        updatePowerUps();
        updateEnemies(timeScale);
        updateProjectiles(timeScale);
        updateBoss(timeScale);

        if (!gameOver && !victory) {
            spawnFromWaveManager();
        }

        if (waveManager.getLevel() != lastLevelSeen) {
            lastLevelSeen = waveManager.getLevel();
            resolvedThisLevel = 0;
        }

        // Last, after everything that can remove a target has run.
        dropStaleBuffer();
    }

    /**
     * Throws away half-typed input once the thing it was aimed at has gone.
     *
     * <p>Fixes a bug the player experiences as the word "sticking" to a monster
     * that already hit them. Typing at an enemy that then breaches used to leave
     * the letters sitting in the field: the engine's own buffer was cleared, but
     * only when that enemy happened to be the LOCKED target — and the lock is
     * null for as long as the prefix still matches more than one enemy, which is
     * precisely when a breach is most likely to be a surprise. Even when it did
     * clear, nothing told the text field, so the next keystroke was measured
     * against a buffer the engine had already forgotten: a red flash and an
     * accuracy penalty for an enemy that was taken away from them.
     *
     * <p>Checked once a tick against everything still alive rather than wired
     * into each removal site, so a breach, a landed bolt, a lapsed pickup and
     * the boss's own field sweep are all covered by one rule. A buffer that
     * still matches something live is left alone — if another enemy shares the
     * prefix, the player's keystrokes are still good and taking them would be
     * its own small theft.
     *
     * <p>THIS IS THE ONLY PLACE {@code update} may clear the buffer, and that
     * exclusivity is load-bearing. The removal sites used to reset the resolver
     * themselves when the departing thing was the locked target. Left in
     * alongside this check they emptied the buffer first, so the check found
     * nothing stale, never raised the flag, and the text field was never told —
     * which is the original bug surviving its own fix, for the single-target
     * case that provokes it most often.
     */
    private void dropStaleBuffer() {
        String buffer = getTypedBuffer();
        if (buffer.isEmpty() || matchesSomethingLive(buffer)) {
            return;
        }
        resolver.reset();
        bossBuffer = "";
        if (boss != null) {
            boss.clearTyping();
        }
        // One-shot. The field lives in Swing and this class must not touch it,
        // so the loop is told once and does the clearing.
        bufferInvalidated = true;
    }

    /** True when {@code buffer} is still a live prefix of something typeable. */
    private boolean matchesSomethingLive(String buffer) {
        for (Enemy enemy : enemies) {
            if (enemy.isActive() && enemy.getWord().startsWith(buffer)) {
                return true;
            }
        }
        for (Projectile projectile : projectiles) {
            if (projectile.isActive() && projectile.getWord().startsWith(buffer)) {
                return true;
            }
        }
        for (PowerUp powerUp : powerUps) {
            if (powerUp.isActive() && powerUp.getWord().startsWith(buffer)) {
                return true;
            }
        }
        return boss != null && boss.isTyping()
                && boss.currentWord().startsWith(buffer);
    }

    /**
     * True once when the typed buffer was dropped out from under the player.
     *
     * <p>Consumed by the game loop, which clears the input field. One-shot, so
     * reading it is what acknowledges it.
     */
    public boolean consumeBufferInvalidated() {
        boolean invalidated = bufferInvalidated;
        bufferInvalidated = false;
        return invalidated;
    }

    private void updateEffects() {
        for (VisualEffect effect : effects) {
            effect.update();
        }
        effects.removeIf(VisualEffect::isExpired);
    }

    private void updateEnemies(double timeScale) {
        for (Enemy enemy : enemies) {
            enemy.update(timeScale);
            if (enemy.isProjectileDue()) {
                throwProjectileFrom(enemy);
            }
        }

        List<Enemy> breached = new ArrayList<>();
        for (Enemy enemy : enemies) {
            if (enemy.hasBreached()) {
                breached.add(enemy);
            }
        }
        for (Enemy enemy : breached) {
            enemies.remove(enemy);
            resolvedThisLevel++;
            // Deliberately does NOT clear the buffer here. dropStaleBuffer owns
            // that at the end of the tick — see the note there on why two
            // authorities were worse than one.
            absorbOrLoseLife(enemy.getX(), enemy.getAnchorY(),
                    enemy.getType().breachDamage());
        }

        enemies.removeIf(e -> e.isExpired(GameConfig.DEFEAT_ANIMATION_TICKS));
    }

    private void updateProjectiles(double timeScale) {
        for (Projectile projectile : projectiles) {
            projectile.update(timeScale);
            if (projectile.hasJustLanded()) {
                absorbOrLoseLife(projectile.getX(), projectile.getY(),
                        GameConfig.DAMAGE_PROJECTILE);
            }
        }
        projectiles.removeIf(p -> p.isExpired(GameConfig.DEFEAT_ANIMATION_TICKS));
    }

    private void updatePowerUps() {
        for (PowerUp powerUp : powerUps) {
            powerUp.update();
            // A boon fading mid-claim is handled by dropStaleBuffer with
            // everything else that can leave the field. Clearing the lock here
            // as well would empty the buffer before that check ran, and the
            // check would then find nothing to report.
        }
        powerUps.removeIf(p -> p.isExpired(GameConfig.DEFEAT_ANIMATION_TICKS));
    }

    // ---- the finale --------------------------------------------------------

    private void updateBoss(double timeScale) {
        if (boss == null) {
            return;
        }
        // The census first, then the decision. BossFight knows what it
        // scheduled; only this class owns the enemy and projectile lists, so
        // the world is reported in and the boss decides whether its phase is
        // done. Both calls sit here, in this order, so the dependency is
        // visible rather than an ordering rule someone has to remember.
        boss.reportField(countLiveBossAttacks());
        boss.update(timeScale);

        if (boss.isVenomDue()) {
            spitVenom();
        }
        if (boss.isMinionDue()) {
            summonMinion();
        }
        if (boss.isPhaseJustEnded()) {
            clearBossField();
        }
        if (boss.isFinished()) {
            declareVictory();
        }
    }

    /**
     * What the boss's current phase put on the field and has not got back.
     *
     * <p>Counts ACTIVE entities only. A defeated summon stays in the list
     * through its death fade and a deflected bolt through its dissolve, and
     * counting those would hold every phase open for an extra second per kill —
     * so the player would clear the field and watch nothing happen.
     */
    private int countLiveBossAttacks() {
        int live = 0;
        for (Enemy enemy : enemies) {
            if (enemy.isActive()) {
                live++;
            }
        }
        for (Projectile projectile : projectiles) {
            if (projectile.isActive() && projectile.getKind() == Projectile.Kind.VENOM) {
                live++;
            }
        }
        return live;
    }

    /**
     * Wipes whatever the boss's phase left behind, so the paragraph that
     * follows is read in peace.
     *
     * <p>The finale alternates between a paragraph the player types and a phase
     * the boss attacks through, and the paragraph is off screen for the whole of
     * the latter. Letting bolts and summons survive the handover would put the
     * player back to reading a sentence while something walked at them, which is
     * the arrangement the alternation exists to replace.
     *
     * <p>Nothing is scored. These were not killed — they are dismissed when
     * their phase ends, and paying the player for outlasting them would make
     * ignoring a phase the profitable option. The phase still has teeth while it
     * runs: a bolt that lands or a summon that breaches costs a life then,
     * exactly as it would in a wave.
     */
    private void clearBossField() {
        for (Enemy enemy : enemies) {
            if (enemy.isActive()) {
                enemy.defeat();
            }
        }
        for (Projectile projectile : projectiles) {
            if (projectile.isActive()) {
                projectile.intercept();
            }
        }
        resolver.reset();
        bossBuffer = "";
    }

    /**
     * The boss calls a shadow spirit up out of the plaza.
     *
     * <p>An ordinary enemy in every respect — it walks the same routes, carries
     * the same kind of word and is typed down the same way. That is the point of
     * the summoning phase: it puts the rest of the game back into the finale
     * rather than leaving the last two minutes as prose and nothing else.
     *
     * <p>Its word is drawn to avoid everything the paragraph still wants, for
     * the same reason venom is.
     */
    private void summonMinion() {
        List<String> reserved = new ArrayList<>(boss.remainingWords());
        for (Projectile projectile : projectiles) {
            reserved.add(projectile.getWord());
        }

        Enemy minion = waveManager.spawnBossMinion(enemies, reserved);
        if (minion == null) {
            return;
        }
        enemies.add(minion);
        effects.add(new VisualEffect(
                VisualEffect.Kind.SPAWN_POOF,
                minion.getX(),
                minion.getAnchorY() - minion.getType().getTargetHeight()
                        * minion.depthScale() * 0.35,
                GameConfig.POOF_TICKS,
                minion.depthScale()));
    }

    /**
     * Brings the finale on.
     *
     * <p>Everything on the ground goes with it. Leaving uncollected boons lying
     * around through a fight that cannot drop any more would be a strange
     * half-state — and a Purge or a Mend banked from the last wave and cashed in
     * mid-paragraph would undercut the one fight in the run that is supposed to
     * be only about typing. Boons already <em>running</em> are left alone: those
     * were earned and spent, and cancelling them at the door would feel like a
     * cheat.
     */
    private void beginBossFight() {
        if (boss != null) {
            return;
        }
        if (!difficulty.hasFinalBoss()) {
            declareVictory();
            return;
        }

        // The boss's health is the length of what it asks the player to type,
        // so the tier decides the script rather than the word bank offering one
        // paragraph and hoping it is the right size.
        List<String> script = wordBank.bossScript(
                difficulty.getWordBankKey(),
                difficulty.getBossParagraphCount(),
                difficulty.getBossSentencesPerParagraph(),
                random);
        boss = new BossFight(difficulty.getFinalBossType(), script,
                difficulty.getBossSentencesPerParagraph(), difficulty, random);

        powerUps.clear();
        resolver.reset();
        effects.add(new VisualEffect(
                VisualEffect.Kind.SPAWN_POOF,
                GameConfig.TEMPLE_CENTER_X,
                GameConfig.GROUND_LINE_Y - GameConfig.BOSS_HEIGHT * 0.4,
                GameConfig.POOF_TICKS * 3, 3.0));
    }

    /**
     * The boss spits.
     *
     * <p>The word is drawn to avoid everything the verse still wants and every
     * bolt already flying, so no two live targets can ever share a word. Flight
     * is a fixed, slow constant rather than the level curve: the player is
     * already holding a verse in their head, and a bolt they cannot read in
     * time is a hazard rather than a decision.
     */
    private void spitVenom() {
        List<String> taken = new ArrayList<>(boss.remainingWords());
        for (Projectile projectile : projectiles) {
            taken.add(projectile.getWord());
        }
        for (Enemy minion : enemies) {
            taken.addAll(minion.getAllWords());
        }

        // Now that a bolt can arrive DURING the verse, an exact-match exclusion
        // is not enough. A bolt whose word is a prefix of the verse word the
        // player is on — or the other way round — makes the shared buffer
        // genuinely undecidable: `sea` and `seal` cannot both be finished, and
        // the player cannot tell which one their keystrokes are going to.
        // Excluding the pair outright is cheaper than teaching the matcher to
        // arbitrate something that has no right answer.
        String onNow = boss.isTyping() ? boss.currentWord() : "";
        for (String word : List.copyOf(wordBank.getActionWords())) {
            if (!onNow.isEmpty()
                    && (word.startsWith(onNow) || onNow.startsWith(word))) {
                taken.add(word);
            }
        }

        projectiles.add(new Projectile(
                wordBank.venomWord(taken),
                boss.getVenomOriginX(), boss.getVenomOriginY(),
                player.getX(), GameConfig.PLAYER_FEET_Y - GameConfig.PLAYER_HEIGHT * 0.5,
                boss.venomFlightTicks(),
                Projectile.Kind.VENOM));
    }

    /**
     * Typing during the finale.
     *
     * <p>The verse, the venom and anything the boss has summoned all share one
     * buffer, with the field taking priority over the verse. That is only
     * possible because the verse is typed a word at a time, and it is what lets
     * one buffer serve all three without a mode key: the same ambiguity rules
     * that already govern two enemies sharing a prefix govern a bolt, a summon
     * and a verse sharing one.
     *
     * <p>A summon's word can still be a strict <em>prefix</em> of the verse word
     * in progress, in which case those keystrokes go to the monster. That is
     * deliberate and is the same rule the ordinary matcher applies everywhere
     * else — the completed target wins. Exact collisions are impossible because
     * summons are drawn against {@code boss.remainingWords()}.
     */
    private ResolveResult handleBossInput(String typedSoFar) {
        String buffer = typedSoFar == null ? "" : typedSoFar;
        bossBuffer = buffer;
        if (buffer.isEmpty()) {
            boss.clearTyping();
            return ResolveResult.EMPTY_RESULT;
        }

        List<Projectile> venom = projectilesOfKind(Projectile.Kind.VENOM);

        // Completions are checked before prefixes, and the field before the
        // verse.
        //
        // Checking exact matches first is what stops a verse word that happens
        // to be a prefix of something live — "the" while "temple" is in the
        // air — from being impossible to finish. It is the same rule the
        // ordinary matcher already applies between two enemies whose words
        // share a prefix; it just has to be applied across the kinds here,
        // because they are not in one list.
        for (Projectile bolt : venom) {
            if (bolt.isActive() && bolt.getWord().equals(buffer)) {
                return deflectVenom(bolt, buffer);
            }
        }
        // Summoned monsters are ordinary enemies and are answered like ordinary
        // enemies, down to the shared completion handler — so a summon dies,
        // scores and looses an arrow exactly as it would have during a wave.
        for (Enemy minion : enemies) {
            if (minion.isActive() && minion.getWord().equals(buffer)) {
                return strikeMinion(minion, buffer);
            }
        }
        // Mid-phase the paragraph is off screen, so it is not a target and a
        // stray keystroke is just a stray keystroke — there is no verse to
        // reset and nothing to charge the player for.
        if (!boss.isTyping()) {
            return trackFieldOnly(buffer, venom);
        }

        // A word of the verse only confirms on the space after it, not the
        // moment its last letter lands — see BossFight for why. No enemy or
        // bolt word contains a space, so this can never collide with the checks
        // above.
        if (buffer.equals(boss.currentWord() + " ")) {
            return advanceVerse(buffer);
        }

        // Nothing finished. Whatever is still reachable stays lit.
        List<WordTarget> alive = new ArrayList<>();
        for (Projectile bolt : venom) {
            if (bolt.isActive() && bolt.getWord().startsWith(buffer)) {
                bolt.flashHit(GameConfig.HIT_FLASH_TICKS);
                alive.add(bolt);
            }
        }
        for (Enemy minion : enemies) {
            if (minion.isActive() && minion.getWord().startsWith(buffer)) {
                minion.flashHit(GameConfig.HIT_FLASH_TICKS);
                alive.add(minion);
            }
        }
        boolean matchesVerse = boss.currentWord().startsWith(buffer);
        if (matchesVerse) {
            alive.add(boss);
        }

        if (alive.isEmpty()) {
            // A mistype during the finale throws the verse away, whether the
            // player was aiming at the verse or at a bolt. The empty valid
            // buffer is what tells the input field to clear itself.
            resolver.noteExternalInput(false);
            resolver.noteExternalCandidates(List.of());
            boss.resetVerse();
            bossBuffer = "";
            return ResolveResult.typo("");
        }

        resolver.noteExternalInput(true);
        // Publish what is lit. This path bypasses the resolver, so without
        // this the renderer asks "is this target highlighted?" and is told no
        // for the whole fight — which is why summoned monsters never turned
        // gold as they were typed.
        resolver.noteExternalCandidates(alive);
        boss.trackTyping(matchesVerse ? buffer : "");
        return ResolveResult.locked(alive.get(0), buffer);
    }

    /**
     * Resolves a keystroke against the field alone, with no verse in play.
     *
     * <p>Used mid-phase. A buffer that matches nothing is still a mistype and
     * still flashes the bar — the player has to know they missed — but it costs
     * nothing beyond that, because the paragraph it would otherwise have reset
     * is not on screen to have been aimed at.
     */
    private ResolveResult trackFieldOnly(String buffer, List<Projectile> venom) {
        List<WordTarget> alive = new ArrayList<>();
        for (Projectile bolt : venom) {
            if (bolt.isActive() && bolt.getWord().startsWith(buffer)) {
                bolt.flashHit(GameConfig.HIT_FLASH_TICKS);
                alive.add(bolt);
            }
        }
        for (Enemy minion : enemies) {
            if (minion.isActive() && minion.getWord().startsWith(buffer)) {
                minion.flashHit(GameConfig.HIT_FLASH_TICKS);
                alive.add(minion);
            }
        }

        if (alive.isEmpty()) {
            resolver.noteExternalInput(false);
            resolver.noteExternalCandidates(List.of());
            bossBuffer = "";
            return ResolveResult.typo("");
        }
        resolver.noteExternalInput(true);
        resolver.noteExternalCandidates(alive);
        return ResolveResult.locked(alive.get(0), buffer);
    }

    /**
     * A summoned monster's word landed.
     *
     * <p>Routed through {@link #handleCompleted} rather than reimplemented, so
     * chained summons, scoring and the arrow all behave exactly as they do in an
     * ordinary wave. The only finale-specific part is clearing the boss's own
     * partial word: the keystrokes went to the monster, so whatever the verse
     * thought was in progress is not.
     */
    private ResolveResult strikeMinion(Enemy minion, String typedSoFar) {
        bossBuffer = "";
        boss.clearTyping();
        handleCompleted(minion);
        return ResolveResult.completed(minion, typedSoFar);
    }

    private ResolveResult deflectVenom(Projectile bolt, String typedSoFar) {
        bossBuffer = "";
        boss.clearTyping();
        bolt.intercept();
        projectilesIntercepted++;
        charactersTyped += GraphemeCounter.count(bolt.getWord());
        score += scoreForProjectile(bolt);

        player.tryFire();
        spawnArrowAt(bolt.getX(), bolt.getY());
        resolver.reset();
        return ResolveResult.completed(bolt, typedSoFar);
    }

    /** A word of the verse landed. Only a whole verse hurts the boss. */
    private ResolveResult advanceVerse(String typedSoFar) {
        String word = boss.currentWord();
        BossFight.Result result = boss.submit(typedSoFar);

        // The space is counted too — it is a keystroke the player actually
        // made now, not one the game inserted for them.
        charactersTyped += GraphemeCounter.count(word) + 1;
        score += scoreForVerseWord(word);
        bossBuffer = "";
        resolver.reset();

        if (result == BossFight.Result.STAGE_CLEARED
                || result == BossFight.Result.PARAGRAPH_CLEARED
                || result == BossFight.Result.DEFEATED) {
            // A finished verse lands a visible blow on the boss, and nothing
            // else. It used to sweep every bolt in the air as well; that made
            // typing quickly a way to clear the field rather than a way to
            // win, so a fast player never actually had to deal with what the
            // boss had put there. Venom and summons are each answerable on
            // their own terms — the player is not powerless without the sweep,
            // just obliged to spend keystrokes on it.
            player.tryFire();
            spawnArrowAt(GameConfig.TEMPLE_CENTER_X, boss.getVenomOriginY());
        }
        return ResolveResult.completed(boss, typedSoFar);
    }

    /** A verse word is worth more than an ordinary one — the finale is harder. */
    private int scoreForVerseWord(String word) {
        int base = GraphemeCounter.count(word) * 20;
        return (int) Math.round(base
                * DifficultyCurve.scoreMultiplier(getLevel()) * combo.getMultiplier());
    }

    /**
     * Something reached the temple. Spends a Naga Shield if one is banked, and
     * only charges a life when none is.
     *
     * <p>Routed through one method so the shield can never be honoured for a
     * breach and forgotten for a bolt.
     */
    private void absorbOrLoseLife(double x, double y, int halves) {
        if (powerUpState.consumeShield()) {
            powerUpState.markFired(PowerUpType.NAGA_SHIELD);
            effects.add(new VisualEffect(
                    VisualEffect.Kind.WARD_BREAK, x, y, GameConfig.POWERUP_FLASH_TICKS, 1.0));
            return;
        }
        damage(halves);
    }

    private void spawnFromWaveManager() {
        // Once the finale is on the field it owns the game — no more waves.
        if (boss != null) {
            return;
        }
        // The tier's last wave is done. Checked before the wave manager ticks so
        // the boss arrives on the frame the field empties, not an intermission
        // later.
        if (waveManager.isRunComplete() && enemies.isEmpty()) {
            beginBossFight();
            return;
        }

        List<Enemy> spawned = waveManager.update(enemies);
        for (Enemy enemy : spawned) {
            enemies.add(enemy);
            // Materialise in a puff so on-screen spawning does not read as popping in.
            effects.add(new VisualEffect(
                    VisualEffect.Kind.SPAWN_POOF,
                    enemy.getX(),
                    enemy.getAnchorY() - enemy.getType().getTargetHeight()
                            * enemy.depthScale() * 0.35,
                    GameConfig.POOF_TICKS,
                    enemy.depthScale()));
        }
        if (waveManager.isLevelCleared()) {
            levelJustCleared = true;
        }
    }

    private void throwProjectileFrom(Enemy enemy) {
        String word = wordBank.projectileWord(collectWordsInPlay());
        projectiles.add(new Projectile(
                word,
                enemy.getThrowOriginX(), enemy.getThrowOriginY(),
                GameConfig.TEMPLE_CENTER_X, GameConfig.GROUND_LINE_Y - 40,
                DifficultyCurve.projectileFlightTicks(getLevel(), difficulty)));
    }

    /**
     * Bolts still in the air, of one kind.
     *
     * <p>Venom and thrown bolts share the projectile list — they fly, land and
     * cost life through the same code — but they are never live at the same
     * time, and each phase only ever offers its own.
     */
    private List<Projectile> projectilesOfKind(Projectile.Kind kind) {
        List<Projectile> matching = new ArrayList<>(projectiles.size());
        for (Projectile projectile : projectiles) {
            if (projectile.getKind() == kind) {
                matching.add(projectile);
            }
        }
        return matching;
    }

    private List<String> collectWordsInPlay() {
        List<String> words = new ArrayList<>();
        for (Enemy enemy : enemies) {
            words.addAll(enemy.getAllWords());
        }
        for (Projectile projectile : projectiles) {
            words.add(projectile.getWord());
        }
        for (PowerUp powerUp : powerUps) {
            words.add(powerUp.getWord());
        }
        return words;
    }

    /**
     * Feeds the current input buffer through the resolver and applies the
     * consequences (flash, defeat, score, arrows).
     *
     * @param typedSoFar full contents of the input field
     * @return the resolution snapshot, so the caller can drive feedback
     */
    public ResolveResult handleInput(String typedSoFar) {
        if (gameOver || paused || isIntroActive()) {
            return ResolveResult.EMPTY_RESULT;
        }
        // The boss has arrived but has not finished rising, or has already
        // fallen. Nothing is typeable in either window.
        if (boss != null && !boss.isFighting()) {
            return ResolveResult.EMPTY_RESULT;
        }

        // The finale takes the keyboard entirely — it resolves the verse, the
        // venom and the boss's summons against one buffer itself rather than
        // going through the ordinary resolver, because the verse is not a
        // WordTarget the resolver knows how to advance.
        if (boss != null && boss.isFighting()) {
            ResolveResult bossResult = handleBossInput(typedSoFar);
            lastResult = bossResult;
            // The finale bypasses the resolver, so it has to feed the combo
            // itself — otherwise accuracy would stop mattering at exactly the
            // point in the run where it matters most.
            applyToCombo(bossResult);
            return bossResult;
        }

        // Tiers are passed shortest-time-budget first: a bolt preempts a dropped
        // boon, which preempts an enemy. See TargetResolver.submit.
        ResolveResult result = resolver.submit(typedSoFar,
                projectilesOfKind(Projectile.Kind.CURSED_BOLT), powerUps, enemies);
        lastResult = result;

        applyToCombo(result);

        switch (result.status()) {
            case COMPLETED -> handleCompleted(result.target());
            case LOCKED, AMBIGUOUS -> handleProgress(result.candidates());
            case TYPO, EMPTY -> {
                // No state change beyond what the resolver already tracked.
            }
        }
        return result;
    }

    /**
     * Feeds a resolution to the combo.
     *
     * <p>Applied at the single point every keystroke passes through, rather than
     * at each of the places a word can be completed, so there is no route by
     * which a mistype can miss the combo or a completion can be counted twice.
     *
     * <p>A completion is only clean if the streak is still alive when it lands —
     * which it is, because a typo would already have broken it on the keystroke
     * that caused it. That ordering is what makes "no wrong letters in this
     * word" the actual rule rather than an approximation of it.
     */
    private void applyToCombo(ResolveResult result) {
        switch (result.status()) {
            case COMPLETED -> combo.noteCleanWord();
            case TYPO -> combo.breakStreak();
            default -> {
                // A partial word is neither yet.
            }
        }
    }

    private void handleCompleted(WordTarget target) {
        if (target instanceof Enemy enemy) {
            charactersTyped += GraphemeCounter.count(enemy.getWord());
            score += scoreForEnemy(enemy);

            if (enemy.hasMoreWords()) {
                // A mini-boss: this word only staggers it. It stays on the
                // field, and progress does not advance, because it has not
                // actually been resolved yet.
                enemy.advanceChain();
                enemy.flashHit(GameConfig.HIT_FLASH_TICKS * 2);
            } else {
                enemy.defeat();
                enemiesDefeated++;
                resolvedThisLevel++;
                maybeDropPowerUp(enemy);
            }
        } else if (target instanceof Projectile projectile) {
            projectile.intercept();
            projectilesIntercepted++;
            charactersTyped += GraphemeCounter.count(projectile.getWord());
            score += scoreForProjectile(projectile);
        } else if (target instanceof PowerUp powerUp) {
            charactersTyped += GraphemeCounter.count(powerUp.getWord());
            claimPowerUp(powerUp);
        }

        // A kill always looses an arrow, regardless of the shot cooldown — the
        // hero must visibly be the one who landed the blow.
        player.tryFire();
        spawnArrowAt(aimPointX(target), aimPointY(target));

        resolver.reset();
    }

    private void handleProgress(List<WordTarget> candidates) {
        for (WordTarget candidate : candidates) {
            if (candidate instanceof Enemy enemy) {
                enemy.flashHit(GameConfig.HIT_FLASH_TICKS);
            } else if (candidate instanceof Projectile projectile) {
                projectile.flashHit(GameConfig.HIT_FLASH_TICKS);
            }
        }

        // Mid-word keystrokes fire too, but rate-limited, and only once a single
        // target is locked — spraying arrows at every ambiguous candidate would
        // fill the screen and destroy the read on which one is targeted.
        boolean fired = player.tryFire();
        if (fired && candidates.size() == 1) {
            WordTarget only = candidates.get(0);
            spawnArrowAt(aimPointX(only), aimPointY(only));
        }
    }

    private static double aimPointX(WordTarget target) {
        if (target instanceof Enemy enemy) {
            return enemy.getX();
        }
        if (target instanceof Projectile projectile) {
            return projectile.getX();
        }
        if (target instanceof PowerUp powerUp) {
            return powerUp.getX();
        }
        return GameConfig.TEMPLE_CENTER_X;
    }

    /** Aims at the middle of the target's body rather than its feet. */
    private static double aimPointY(WordTarget target) {
        if (target instanceof Enemy enemy) {
            return enemy.getAnchorY()
                    - enemy.getType().getTargetHeight() * enemy.depthScale() * 0.5;
        }
        if (target instanceof Projectile projectile) {
            return projectile.getY();
        }
        if (target instanceof PowerUp powerUp) {
            return powerUp.getY();
        }
        return GameConfig.GROUND_LINE_Y;
    }

    // ---- power-ups ---------------------------------------------------------

    /**
     * Rolls whether a defeated enemy leaves a boon behind, and drops it where
     * it fell.
     *
     * <p>Only ordinary kills roll. Chained mini-bosses drop on their final word
     * like anything else, but a word cleared mid-chain does not — the enemy is
     * still standing, and a boon falling out of something that has not died
     * would read as a bug.
     */
    private void maybeDropPowerUp(Enemy enemy) {
        // The finale is a typing test and nothing else. Nothing drops during it,
        // and beginBossFight has already swept whatever was lying around.
        if (boss != null) {
            return;
        }
        if (!PowerUpDrops.shouldDrop(enemy.getType(), difficulty, getLevel(),
                getLives(), random)) {
            return;
        }
        boolean shieldsFull =
                powerUpState.getShieldCharges() >= GameConfig.MAX_SHIELD_CHARGES;
        PowerUpType type = PowerUpDrops.roll(getLives(), shieldsFull, random);

        String word = wordBank.pickupWord(collectWordsInPlay());
        double dropY = enemy.getAnchorY()
                - enemy.getType().getTargetHeight() * enemy.depthScale() * 0.55;

        powerUps.add(new PowerUp(type, word, enemy.getX(), dropY));
    }

    /** Claims a pickup the player has typed and applies what it does. */
    private void claimPowerUp(PowerUp powerUp) {
        powerUp.claim();
        powerUpsCollected++;
        score += GameConfig.TARGET_FPS;

        effects.add(new VisualEffect(
                VisualEffect.Kind.BOON_CLAIMED,
                powerUp.getX(), powerUp.getY(),
                GameConfig.POWERUP_FLASH_TICKS, 1.0));

        applyPowerUp(powerUp.getType());
    }

    /**
     * Applies a boon.
     *
     * <p>Timed and charge boons are handed to {@link PowerUpState}, which owns
     * anything that outlives the moment of collection. The instant two act on
     * the field and the life count, which are this class's to change — putting
     * them in the state holder as well would leave two objects able to decide
     * what a Purge does.
     */
    void applyPowerUp(PowerUpType type) {
        if (type == null) {
            return;
        }
        switch (type) {
            case TIME_FREEZE, SLOW_TIDE -> powerUpState.activate(type, difficulty);
            case NAGA_SHIELD -> powerUpState.addShield();
            case PURGE -> {
                purgeField();
                powerUpState.markFired(type);
            }
            case MEND -> {
                halfLives = Math.min(GameConfig.STARTING_HALF_LIVES,
                        halfLives + GameConfig.HALVES_PER_LIFE);
                powerUpState.markFired(type);
            }
        }
    }

    /**
     * Sweeps every enemy and bolt currently on the field.
     *
     * <p>Scores and counts them as kills, because from the player's side they
     * were killed — awarding nothing would make the strongest boon in the game
     * cost them their score, which is a strange thing to punish. Progress
     * advances too, so the level bar does not stall after a Purge.
     */
    private void purgeField() {
        for (Enemy enemy : enemies) {
            if (!enemy.isActive()) {
                continue;
            }
            score += scoreForEnemy(enemy);
            enemy.defeat();
            enemiesDefeated++;
            resolvedThisLevel++;
            effects.add(new VisualEffect(
                    VisualEffect.Kind.IMPACT,
                    enemy.getX(),
                    enemy.getAnchorY() - enemy.getType().getTargetHeight()
                            * enemy.depthScale() * 0.5,
                    GameConfig.ARROW_FLIGHT_TICKS + 10, enemy.depthScale()));
        }
        for (Projectile projectile : projectiles) {
            if (projectile.isActive()) {
                projectile.intercept();
                projectilesIntercepted++;
            }
        }
        resolver.reset();
    }

    /**
     * Ends the run as a win, and records the tier as beaten.
     *
     * <p>The clear is banked here rather than when the save is written, because
     * this is the only moment that knows a run was won. It survives
     * {@link #restart()} on purpose: unlocks are the player's, not the run's.
     */
    private void declareVictory() {
        if (victory) {
            return;
        }
        victory = true;
        gameOver = true;
        if (difficulty.isWinnable()) {
            clearedTiers.add(difficulty.getWordBankKey());
        }
        resolver.reset();
    }

    private void spawnArrowAt(double targetX, double targetY) {
        effects.add(new VisualEffect(
                VisualEffect.Kind.ARROW,
                player.getX(), player.getBowY(),
                targetX, targetY,
                GameConfig.ARROW_FLIGHT_TICKS, 1.0));
        effects.add(new VisualEffect(
                VisualEffect.Kind.IMPACT,
                targetX, targetY,
                GameConfig.ARROW_FLIGHT_TICKS + 10, 1.0));
    }

    private int scoreForEnemy(Enemy enemy) {
        int base = GraphemeCounter.count(enemy.getWord()) * 10;
        double tierBonus = 1.0 / Math.max(0.4, enemy.getType().getSpeedMultiplier());
        return (int) Math.round(base * tierBonus
                * DifficultyCurve.scoreMultiplier(getLevel()) * combo.getMultiplier());
    }

    private int scoreForProjectile(Projectile projectile) {
        // Short words but urgent — worth a flat premium so intercepting feels
        // rewarding rather than a distraction from scoring on enemies.
        int base = GraphemeCounter.count(projectile.getWord()) * 25;
        return (int) Math.round(base
                * DifficultyCurve.scoreMultiplier(getLevel()) * combo.getMultiplier());
    }

    /** Wipes the run and starts over from level 1, keeping personal bests. */
    public void restart() {
        int runBestScore = Math.max(bestScore, score);
        int runBestLevel = Math.max(bestLevel, getLevel());

        enemies.clear();
        projectiles.clear();
        powerUps.clear();
        effects.clear();
        resolver.resetAll();
        waveManager.reset();
        player.reset();
        powerUpState.reset();
        combo.reset();
        boss = null;
        bossBuffer = "";
        bufferInvalidated = false;
        // Repeat tracking is per-run, so a fresh run gets the whole vocabulary
        // back rather than starting where the last one left off.
        wordBank.resetUsage();

        score = 0;
        halfLives = GameConfig.STARTING_HALF_LIVES;
        elapsedTicks = 0;
        charactersTyped = 0;
        enemiesDefeated = 0;
        projectilesIntercepted = 0;
        powerUpsCollected = 0;
        resolvedThisLevel = 0;
        lastLevelSeen = 0;
        victory = false;
        // A restart earns the same countdown, so the player is never dropped
        // straight back into a wave already in motion.
        beginIntro();
        gameOver = false;
        running = true;
        levelJustCleared = false;
        lastResult = null;

        bestScore = runBestScore;
        bestLevel = runBestLevel;
    }

    public void addEnemy(Enemy enemy) {
        if (enemy != null) {
            enemies.add(enemy);
        }
    }

    /** Read-only view for the renderer. */
    public List<Enemy> getEnemies() {
        return Collections.unmodifiableList(enemies);
    }

    public List<Projectile> getProjectiles() {
        return Collections.unmodifiableList(projectiles);
    }

    /** Power-up drops currently on the field. Read-only view for the renderer. */
    public List<PowerUp> getPowerUps() {
        return Collections.unmodifiableList(powerUps);
    }

    /** Running boons and banked shield charges, for the HUD. */
    public PowerUpState getPowerUpState() {
        return powerUpState;
    }

    /** The run of perfectly typed words, for the HUD and the end-of-run summary. */
    public ComboTracker getCombo() {
        return combo;
    }

    /** The finale, or null while the ordinary waves are still running. */
    public BossFight getBoss() {
        return boss;
    }

    /** True from the moment the boss rises until the run ends. */
    public boolean isBossActive() {
        return boss != null;
    }

    /** Drops a boon on the field directly. For tests and for scripted moments. */
    public void addPowerUp(PowerUp powerUp) {
        if (powerUp != null) {
            powerUps.add(powerUp);
        }
    }

    public List<VisualEffect> getEffects() {
        return Collections.unmodifiableList(effects);
    }

    public Player getPlayer() {
        return player;
    }

    public TargetResolver getResolver() {
        return resolver;
    }

    public WaveManager getWaveManager() {
        return waveManager;
    }

    public WordBank getWordBank() {
        return wordBank;
    }

    public Language getLanguage() {
        return language;
    }

    public ResolveResult getLastResult() {
        return lastResult;
    }

    /**
     * What the player currently has typed, from whichever system owns the
     * keyboard — the resolver normally, the boss during the finale.
     */
    public String getTypedBuffer() {
        if (boss != null && boss.isFighting()) {
            return bossBuffer;
        }
        return resolver.getValidBuffer();
    }

    public int getScore() {
        return score;
    }

    public int getLevel() {
        return waveManager.getLevel();
    }

    /**
     * Whole lotus buds still lit, rounding a half up.
     *
     * <p>Rounds up so a player on half a heart is shown as still having one —
     * which is true, and which is what the last pip on the bar is for.
     */
    public int getLives() {
        return (int) Math.ceil(halfLives / (double) GameConfig.HALVES_PER_LIFE);
    }

    /** Lives in half-hearts, for the HUD's half-filled buds. */
    public int getHalfLives() {
        return halfLives;
    }

    /** Takes a full heart. */
    public void loseLife() {
        damage(GameConfig.HALVES_PER_LIFE);
    }

    /** Takes half a heart. */
    public void loseHalfLife() {
        damage(1);
    }

    /**
     * Applies damage in half-hearts and ends the run at zero.
     *
     * <p>Every route to losing life goes through here, so nothing can take a
     * half and forget to check whether that was the last one.
     */
    private void damage(int halves) {
        halfLives -= Math.max(0, halves);
        if (halfLives <= 0) {
            halfLives = 0;
            gameOver = true;
        }
    }

    public boolean isGameOver() {
        return gameOver;
    }

    /**
     * True when the run ended by clearing the tier's last level.
     *
     * <p>{@link #isGameOver()} is also true then — a won run is still a finished
     * one — so anything drawing the end screen must check this first or it will
     * congratulate the player with a defeat banner.
     */
    public boolean isVictory() {
        return victory;
    }

    /** Boons claimed this run. */
    public int getPowerUpsCollected() {
        return powerUpsCollected;
    }

    /** The last level of a run on the current tier. */
    public int getFinalLevel() {
        return waveManager.getFinalLevel();
    }

    /** True for exactly one tick after a level is cleared. Drives the autosave. */
    public boolean isLevelJustCleared() {
        return levelJustCleared;
    }

    public long getElapsedTicks() {
        return elapsedTicks;
    }

    public double getElapsedSeconds() {
        return (double) elapsedTicks / GameConfig.TARGET_FPS;
    }

    /**
     * Words per minute, using the standard 5-characters-per-word convention so
     * it is comparable with typing tests.
     */
    public double getWpm() {
        double minutes = getElapsedSeconds() / 60.0;
        if (minutes <= 0.01) {
            return 0;
        }
        return (charactersTyped / 5.0) / minutes;
    }

    public int getCharactersTyped() {
        return charactersTyped;
    }

    public int getEnemiesDefeated() {
        return enemiesDefeated;
    }

    /** Enemies dealt with so far in the current level, killed or leaked. */
    public int getResolvedThisLevel() {
        return resolvedThisLevel;
    }

    /** Total enemies the current level will send, on the current tier. */
    public int getEnemiesInLevel() {
        return DifficultyCurve.enemyCount(getLevel(), difficulty);
    }

    /**
     * How far through the current level the player is, 0 to 1. Drives the HUD
     * progress bar.
     */
    public double getLevelProgress() {
        if (getLevel() < 1) {
            return 0;
        }
        int total = getEnemiesInLevel();
        if (total <= 0) {
            return 0;
        }
        return Math.max(0.0, Math.min(1.0, resolvedThisLevel / (double) total));
    }

    public int getProjectilesIntercepted() {
        return projectilesIntercepted;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public boolean isPaused() {
        return paused;
    }

    /** The opening loading-and-countdown beat, for the renderer. */
    public IntroSequence getIntro() {
        return intro;
    }

    /** True while the opening beat is still playing and the sim is frozen. */
    public boolean isIntroActive() {
        return intro != null && intro.isActive();
    }

    /** Restarts the opening beat, e.g. when a new run begins. */
    public void beginIntro() {
        intro = new IntroSequence(difficulty);
    }

    /** Skips the remainder of the opening beat. */
    public void skipIntro() {
        if (intro != null) {
            intro.skip();
        }
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    /**
     * Starts a fresh run on a chosen tier.
     *
     * <p>The tier is fixed for the life of a run — it decides speeds, word
     * lengths and which monster ends the game — so switching it always goes
     * through a full restart rather than taking effect mid-level.
     */
    public void restartWith(Difficulty difficulty) {
        this.difficulty = difficulty == null ? Difficulty.defaultChoice() : difficulty;
        waveManager.setDifficulty(this.difficulty);
        restart();
    }

    /**
     * Flips the pause state and reports the result.
     *
     * <p>Refuses to pause a finished run — there is nothing to come back to, and
     * a pause overlay stacked on the game-over screen would hide the restart
     * prompt.
     */
    public boolean togglePause() {
        if (gameOver) {
            return false;
        }
        paused = !paused;
        return paused;
    }

    // ---- persistence -------------------------------------------------------

    public SaveData toSaveData() {
        return new SaveData(
                getLevel(), score, getLives(), language,
                Math.max(bestScore, score),
                Math.max(bestLevel, getLevel()),
                clearedTiers);
    }

    /** Which tiers have been beaten, as a progress ladder the menu can read. */
    public DifficultyProgress getProgress() {
        return new DifficultyProgress(clearedTiers);
    }

    /**
     * Seeds the unlock state from a save without touching the current run.
     *
     * <p>Separate from {@link #restoreFrom} because the two are wanted at
     * different moments: unlocks matter the instant the menu opens, whereas
     * resuming a run only happens if the player asks for it.
     */
    public void restoreProgress(SaveData data) {
        if (data != null) {
            clearedTiers.addAll(data.clearedTiers());
        }
    }

    /** Restores a previous run. Enemies are not persisted — the level restarts. */
    public void restoreFrom(SaveData data) {
        if (data == null) {
            return;
        }
        this.bestScore = data.bestScore();
        this.bestLevel = data.bestWave();
        restoreProgress(data);

        if (data.hasResumableRun()) {
            this.score = data.score();
            this.halfLives = Math.max(1, data.lives()) * GameConfig.HALVES_PER_LIFE;
            this.gameOver = false;
            enemies.clear();
            projectiles.clear();
            resolver.reset();
            waveManager.resumeAtLevel(data.wave() - 1);
        }
    }

    public int getBestScore() {
        return Math.max(bestScore, score);
    }

    public int getBestLevel() {
        return Math.max(bestLevel, getLevel());
    }
}
