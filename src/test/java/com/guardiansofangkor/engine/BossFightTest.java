package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.ApproachPath;
import com.guardiansofangkor.entities.Enemy;
import com.guardiansofangkor.entities.EnemyType;
import com.guardiansofangkor.entities.PowerUp;
import com.guardiansofangkor.entities.PowerUpType;
import com.guardiansofangkor.entities.Projectile;
import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.matching.MatchStatus;
import com.guardiansofangkor.matching.ResolveResult;
import com.guardiansofangkor.util.GameConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BossFight — the paragraph finale")
class BossFightTest {

    private static final List<String> VERSES = List.of("one two", "three four", "five six");

    private static BossFight fighting() {
        BossFight boss = new BossFight(EnemyType.NAGA, VERSES, Difficulty.EASY);
        settleArrival(boss);
        return boss;
    }

    /** Ticks through the rise AND the held briefing, to the live fight. */
    private static void settleArrival(BossFight boss) {
        for (int i = 0; i <= BossFight.ARRIVAL_TICKS + BossFight.BRIEFING_TICKS; i++) {
            boss.update();
        }
    }

    /**
     * Types one word, a character at a time, then the confirming space, as the
     * input field would. The word only advances on that space — see
     * {@link BossFight#submit(String)}.
     */
    private static BossFight.Result typeWord(BossFight boss, String word) {
        BossFight.Result last = BossFight.Result.NONE;
        for (int i = 1; i <= word.length(); i++) {
            last = boss.submit(word.substring(0, i));
        }
        last = boss.submit(word + " ");
        return last;
    }

    /**
     * Types the whole of the current verse.
     *
     * <p>Word by word, with the buffer clearing between them — which is how the
     * input field behaves, since each finished word comes back as COMPLETED.
     */
    private static BossFight.Result typeVerse(BossFight boss) {
        BossFight.Result last = BossFight.Result.NONE;
        for (String word : List.copyOf(boss.currentVerseWords())) {
            last = typeWord(boss, word);
        }
        return last;
    }

    // ---- phases ------------------------------------------------------------

    @Test
    @DisplayName("nothing is typeable while the boss is still rising")
    void arrivalRefusesInput() {
        BossFight boss = new BossFight(EnemyType.NAGA, VERSES, Difficulty.EASY);

        assertTrue(boss.isArriving());
        assertEquals(BossFight.Result.NONE, boss.submit("o"),
                "a keystroke landing during the entrance should not count");
        assertFalse(boss.isActive(), "and the matcher must not see it as a target");
    }

    @Test
    @DisplayName("the fight opens once the entrance finishes")
    void arrivalEnds() {
        BossFight boss = fighting();

        assertTrue(boss.isFighting());
        assertTrue(boss.isActive());
        assertEquals(VERSES.get(0), boss.currentSentence());
    }

    @Test
    @DisplayName("no venom flies during the entrance or the briefing")
    void noVenomWhileArriving() {
        BossFight boss = new BossFight(EnemyType.NAGA, VERSES, Difficulty.EASY);

        for (int i = 0; i < BossFight.ARRIVAL_TICKS + BossFight.BRIEFING_TICKS; i++) {
            boss.update();
            assertFalse(boss.isVenomDue(),
                    "spat at tick " + i + ", before the fight was live");
        }
    }

    // ---- the briefing ------------------------------------------------------

    /** Ticks only far enough to finish the rise, leaving the briefing up. */
    private static BossFight briefing() {
        BossFight boss = new BossFight(EnemyType.NAGA, VERSES, Difficulty.EASY);
        for (int i = 0; i <= BossFight.ARRIVAL_TICKS; i++) {
            boss.update();
        }
        return boss;
    }

    @Test
    @DisplayName("the rules are held on screen once the boss has risen")
    void briefingFollowsTheArrival() {
        BossFight boss = briefing();

        assertTrue(boss.isBriefing());
        assertFalse(boss.isArriving(), "the rise is over");
        assertFalse(boss.isFighting(), "but the fight has not started either");
    }

    @Test
    @DisplayName("nothing is typeable while the briefing is up")
    void briefingRefusesInput() {
        // The overlay covers the verse. Accepting keystrokes under it would ask
        // the player to type a sentence they cannot see.
        BossFight boss = briefing();

        assertEquals(BossFight.Result.NONE, boss.submit("o"));
        assertFalse(boss.isActive(), "the matcher must not see it as a target");
    }

    @Test
    @DisplayName("no venom flies while the briefing is up")
    void briefingHoldsTheVenom() {
        BossFight boss = briefing();

        for (int i = 0; i < BossFight.BRIEFING_TICKS; i++) {
            assertFalse(boss.isVenomDue(), "spat during its own warning, at tick " + i);
            boss.update();
        }
    }

    @Test
    @DisplayName("the briefing ends on its own and hands over to the fight")
    void briefingEnds() {
        BossFight boss = briefing();

        for (int i = 0; i <= BossFight.BRIEFING_TICKS; i++) {
            boss.update();
        }

        assertFalse(boss.isBriefing(), "the overlay never came down");
        assertTrue(boss.isFighting(), "and play should have resumed");
        assertEquals(VERSES.get(0), boss.currentSentence(),
                "the fight starts at the first verse, not part-way in");
    }

    @Test
    @DisplayName("the briefing is long enough to actually be read")
    void briefingIsReadable() {
        // Three lines of rules. Anything much shorter and the panel is a flash
        // the player registers as decoration rather than as instructions.
        assertTrue(BossFight.BRIEFING_TICKS >= GameConfig.TARGET_FPS * 4,
                "too brief to read three lines");
    }

    @Test
    @DisplayName("a Time Freeze cannot stretch the briefing")
    void briefingIgnoresTimeScale() {
        // Freezing a screen that is already held would read as a hang, and a
        // boon should never make the player wait longer to start playing.
        BossFight boss = new BossFight(EnemyType.NAGA, VERSES, Difficulty.EASY);

        for (int i = 0; i <= BossFight.ARRIVAL_TICKS + BossFight.BRIEFING_TICKS; i++) {
            boss.update(0.0);
        }
        assertTrue(boss.isFighting(), "the briefing outstayed its window under a freeze");
    }

    @Test
    @DisplayName("a boss needs something to type")
    void refusesAnEmptyParagraph() {
        assertThrows(IllegalArgumentException.class,
                () -> new BossFight(EnemyType.NAGA, List.of(), Difficulty.EASY));
        assertThrows(IllegalArgumentException.class,
                () -> new BossFight(EnemyType.NAGA, null, Difficulty.EASY));
    }

    // ---- typing ------------------------------------------------------------

    @Test
    @DisplayName("correct letters advance the current word")
    void correctLettersProgress() {
        BossFight boss = fighting();

        assertEquals(BossFight.Result.PROGRESS, boss.submit("o"));
        assertEquals(BossFight.Result.PROGRESS, boss.submit("on"));
        assertEquals("on", boss.getTyped());
        assertEquals("e", boss.getRemaining());
    }

    @Test
    @DisplayName("the matcher is offered one word at a time, not the whole verse")
    void oneWordAtATime() {
        // This is what makes room for a venom bolt mid-verse: the buffer is
        // always a partial word, never a partial sentence.
        BossFight boss = fighting();

        assertEquals("one", boss.getWord());
        typeWord(boss, "one");
        assertEquals("two", boss.getWord());
        assertEquals(1, boss.getWordIndex());
    }

    @Test
    @DisplayName("finishing a word clears the buffer without clearing the verse")
    void wordsClearIndividually() {
        BossFight boss = fighting();

        assertEquals(BossFight.Result.WORD_CLEARED, typeWord(boss, "one"));
        assertEquals(0, boss.getStage(), "one word is not a whole verse");
        assertEquals("", boss.getTyped());
    }

    @Test
    @DisplayName("venom words never collide with words the verse still wants")
    void remainingWordsAreExcludable() {
        BossFight boss = fighting();
        typeWord(boss, "one");

        List<String> remaining = boss.remainingWords();

        assertFalse(remaining.contains("one"), "a cleared word is no longer wanted");
        assertTrue(remaining.contains("two"));
        assertTrue(remaining.contains("five"), "later verses count too");
    }

    @Test
    @DisplayName("finishing a verse reveals the next one")
    void clearingAVerseAdvances() {
        BossFight boss = fighting();

        assertEquals(BossFight.Result.STAGE_CLEARED, typeVerse(boss));

        assertEquals(1, boss.getStage());
        assertEquals("three four", boss.currentSentence());
        assertEquals("", boss.getTyped(), "the next verse starts empty");
        assertFalse(boss.isBeaten());
    }

    @Test
    @DisplayName("finishing the last verse beats the boss")
    void clearingEveryVerseWins() {
        BossFight boss = fighting();

        typeVerse(boss);
        typeVerse(boss);
        assertEquals(BossFight.Result.DEFEATED, typeVerse(boss));

        assertTrue(boss.isBeaten());
        assertEquals(0, boss.getHealthFraction(), 0.0001);
    }

    @Test
    @DisplayName("the run is not won until the death has played out")
    void deathHasToFinish() {
        BossFight boss = fighting();
        typeVerse(boss);
        typeVerse(boss);
        typeVerse(boss);

        assertTrue(boss.isBeaten());
        assertFalse(boss.isFinished(), "the victory screen must not cut off the death");

        for (int i = 0; i <= BossFight.DEATH_TICKS; i++) {
            boss.update();
        }
        assertTrue(boss.isFinished());
    }

    @Test
    @DisplayName("a mistype resets the verse in progress")
    void typoResetsTheVerse() {
        BossFight boss = fighting();
        boss.submit("o");
        boss.submit("on");

        assertEquals(BossFight.Result.TYPO, boss.submit("onx"));
        assertEquals("", boss.getTyped(), "the verse should be back to the start");
        assertEquals(0, boss.getWordIndex());
        assertEquals("one", boss.getRemaining());
    }

    @Test
    @DisplayName("a mistype never costs a verse already cleared")
    void typoDoesNotUndoClearedVerses() {
        // One slip at word thirty undoing the whole fight would make the finale
        // a lottery rather than a test.
        BossFight boss = fighting();
        typeVerse(boss);
        typeVerse(boss);
        assertEquals(2, boss.getStage());

        boss.submit("f");
        boss.submit("fx");

        assertEquals(2, boss.getStage(), "cleared verses must stay cleared");
        assertEquals("five six", boss.currentSentence());
        assertEquals(0, boss.getWordIndex(), "but the verse itself starts over");
    }

    @Test
    @DisplayName("a reset verse can be typed again from scratch")
    void resetVersesAreStillWinnable() {
        BossFight boss = fighting();
        typeWord(boss, "one");
        boss.submit("tx");

        assertEquals(BossFight.Result.STAGE_CLEARED, typeVerse(boss));
    }

    @Test
    @DisplayName("clearing the buffer is not a mistype")
    void anEmptyBufferIsInert() {
        BossFight boss = fighting();
        boss.submit("one");

        assertEquals(BossFight.Result.NONE, boss.submit(""));
        assertEquals("", boss.getTyped());
    }

    @Test
    @DisplayName("keystrokes after the boss falls do nothing")
    void beatenBossIgnoresInput() {
        BossFight boss = fighting();
        typeVerse(boss);
        typeVerse(boss);
        typeVerse(boss);

        assertEquals(BossFight.Result.NONE, boss.submit("f"));
    }

    @Test
    @DisplayName("a fully typed word waits for the space, it does not auto-advance")
    void fullyTypedWordWaitsForTheSpace() {
        BossFight boss = fighting();

        assertEquals(BossFight.Result.PROGRESS, boss.submit("one"),
                "the last letter alone must not clear the word");
        assertEquals("one", boss.getWord(), "still on the same word");
        assertEquals(0, boss.getWordIndex());
        assertEquals("", boss.getRemaining(), "nothing left to type but the space");
    }

    @Test
    @DisplayName("the space is what actually clears the word")
    void theSpaceConfirms() {
        BossFight boss = fighting();
        boss.submit("one");

        assertEquals(BossFight.Result.WORD_CLEARED, boss.submit("one "));
        assertEquals("two", boss.getWord());
        assertEquals(1, boss.getWordIndex());
    }

    @Test
    @DisplayName("typing the next word without a space is a mistype")
    void skippingTheSpaceIsATypo() {
        // The whole point: the player must actually press space, the game must
        // not paper over a missing one.
        BossFight boss = fighting();
        boss.submit("one");

        assertEquals(BossFight.Result.TYPO, boss.submit("onet"));
        assertEquals(0, boss.getWordIndex(), "the verse should be back to the start");
    }

    @Test
    @DisplayName("a verse of one word still works")
    void singleWordVerses() {
        BossFight boss = new BossFight(EnemyType.NAGA,
                List.of("alpha", "beta"), Difficulty.EASY, new Random(1));
        settleArrival(boss);

        assertEquals(BossFight.Result.STAGE_CLEARED, typeWord(boss, "alpha"));
        assertEquals(BossFight.Result.DEFEATED, typeWord(boss, "beta"));
    }

    // ---- health ------------------------------------------------------------

    @Test
    @DisplayName("health falls as the paragraph is typed, not only per verse")
    void healthTracksLetters() {
        // A bar that moves three times in a two-minute fight tells the player
        // nothing while they are actually typing.
        BossFight boss = fighting();
        double full = boss.getHealthFraction();

        boss.submit("on");
        double partway = boss.getHealthFraction();

        assertTrue(partway < full, "mid-word typing should show on the bar");
        assertTrue(partway > 0.5, "and not finish the first verse's worth early");
    }

    @Test
    @DisplayName("health is monotonic across the whole fight")
    void healthOnlyFalls() {
        BossFight boss = fighting();
        double last = boss.getHealthFraction();

        for (int verse = 0; verse < VERSES.size(); verse++) {
            for (String word : List.copyOf(boss.currentVerseWords())) {
                for (int i = 1; i <= word.length(); i++) {
                    boss.submit(word.substring(0, i));
                    double now = boss.getHealthFraction();
                    assertTrue(now <= last + 0.0001,
                            "health went back up at '" + word.substring(0, i) + "'");
                    last = now;
                }
                // The confirming space is what actually advances the word — see
                // BossFight.submit — and it must not raise the bar either.
                boss.submit(word + " ");
                double afterConfirm = boss.getHealthFraction();
                assertTrue(afterConfirm <= last + 0.0001,
                        "health went back up confirming '" + word + "'");
                last = afterConfirm;
            }
        }
        assertEquals(0, last, 0.0001);
    }

    // ---- venom -------------------------------------------------------------

    @Test
    @DisplayName("venom starts flying once the fight is live")
    void venomEventuallyFlies() {
        BossFight boss = fighting();

        boolean spat = false;
        for (int i = 0; i < 2000 && !spat; i++) {
            boss.update();
            spat = boss.isVenomDue();
        }
        assertTrue(spat, "the boss never attacked");
    }

    @Test
    @DisplayName("the venom-due flag is true for exactly one tick")
    void venomFlagIsOneShot() {
        // A sticky flag would spawn a bolt on every frame for the rest of the
        // fight, which is not a boss, it is a wall.
        BossFight boss = fighting();

        for (int i = 0; i < 2000; i++) {
            boss.update();
            if (boss.isVenomDue()) {
                boss.update();
                assertFalse(boss.isVenomDue(), "the spit flag stuck");
                return;
            }
        }
        throw new AssertionError("the boss never attacked");
    }

    @Test
    @DisplayName("the attack gap is a random five to ten seconds")
    void venomIntervalStaysInItsWindow() {
        BossFight boss = fighting();

        for (int i = 0; i < 500; i++) {
            int gap = boss.venomIntervalTicks();
            assertTrue(gap >= GameConfig.VENOM_INTERVAL_MIN_TICKS
                            && gap <= GameConfig.VENOM_INTERVAL_MAX_TICKS,
                    "gap of " + gap + " ticks is outside five to ten seconds");
        }
    }

    @Test
    @DisplayName("the gap actually varies rather than being a metronome")
    void venomIntervalIsUnpredictable() {
        // A fixed cadence becomes a rhythm the player memorises and stops
        // reacting to, which is the opposite of what the venom is for.
        BossFight boss = fighting();
        java.util.Set<Integer> seen = new java.util.HashSet<>();

        for (int i = 0; i < 200; i++) {
            seen.add(boss.venomIntervalTicks());
        }
        assertTrue(seen.size() > 20, "only saw " + seen.size() + " distinct gaps");
    }

    @Test
    @DisplayName("a Time Freeze still holds during the finale")
    void freezeStopsTheVenomClock() {
        // The boon was earned and spent. Quietly cancelling it at the boss door
        // would feel like a cheat.
        BossFight boss = fighting();

        for (int i = 0; i < 2000; i++) {
            boss.update(0.0);
            assertFalse(boss.isVenomDue(), "venom flew through a Time Freeze");
        }
    }

    // ---- the fight inside a run --------------------------------------------

    private static GameState atTheFinale() {
        GameState state = new GameState(Language.ENGLISH, Difficulty.EASY, new Random(3));
        state.skipIntro();
        state.getWaveManager().resumeAtLevel(state.getFinalLevel() - 1);

        for (int tick = 0; tick < 60_000 && !state.isBossActive(); tick++) {
            state.update();
            for (Enemy enemy : List.copyOf(state.getEnemies())) {
                enemy.defeat();
            }
            for (Projectile bolt : List.copyOf(state.getProjectiles())) {
                bolt.intercept();
            }
        }
        return state;
    }

    @Test
    @DisplayName("the boss arrives after the last wave, not inside it")
    void bossArrivesAfterTheFinalWave() {
        GameState state = atTheFinale();

        assertTrue(state.isBossActive(), "the finale never started");
        assertNotNull(state.getBoss());
        assertEquals(Difficulty.EASY.getFinalBossType(), state.getBoss().getType());
        assertFalse(state.isVictory(), "arriving is not the same as being beaten");
    }

    @Test
    @DisplayName("the boss stands alone — no ordinary enemies join it")
    void noWavesDuringTheFinale() {
        GameState state = atTheFinale();

        for (int i = 0; i < 3000; i++) {
            state.update();
            assertTrue(state.getEnemies().isEmpty(),
                    "a wave spawned during the finale");
        }
    }

    @Test
    @DisplayName("power-ups left on the ground are swept up when the boss arrives")
    void groundBoonsAreCleared() {
        GameState state = new GameState(Language.ENGLISH, Difficulty.EASY, new Random(3));
        state.skipIntro();
        state.getWaveManager().resumeAtLevel(state.getFinalLevel() - 1);
        state.addPowerUp(new PowerUp(PowerUpType.PURGE, "orb", 400, 400, 100_000));

        for (int tick = 0; tick < 60_000 && !state.isBossActive(); tick++) {
            state.update();
            for (Enemy enemy : List.copyOf(state.getEnemies())) {
                enemy.defeat();
            }
            for (Projectile bolt : List.copyOf(state.getProjectiles())) {
                bolt.intercept();
            }
        }

        assertTrue(state.isBossActive());
        assertTrue(state.getPowerUps().isEmpty(),
                "an uncollected boon survived into a fight that cannot drop any");
    }

    @Test
    @DisplayName("no new power-ups drop once the finale has begun")
    void noDropsDuringTheFinale() {
        GameState state = atTheFinale();

        for (int i = 0; i < 4000; i++) {
            state.update();
            assertTrue(state.getPowerUps().isEmpty(), "a boon dropped mid-finale");
        }
    }

    /** Ticks a state past the rise and the held briefing, into the live fight. */
    private static void settleIntoTheFight(GameState state) {
        for (int i = 0; i <= BossFight.ARRIVAL_TICKS + BossFight.BRIEFING_TICKS; i++) {
            state.update();
        }
    }

    /** Runs the fight until a bolt is in the air, and returns it. */
    private static Projectile waitForVenom(GameState state) {
        for (int i = 0; i < 4000; i++) {
            state.update();
            for (Projectile p : state.getProjectiles()) {
                if (p.getKind() == Projectile.Kind.VENOM && p.isActive()) {
                    return p;
                }
            }
        }
        throw new AssertionError("the boss never spat");
    }

    @Test
    @DisplayName("venom carries a word of its own")
    void venomIsTypeable() {
        GameState state = atTheFinale();
        Projectile venom = waitForVenom(state);

        assertNotNull(venom);
        assertTrue(venom.isVenom());
        assertFalse(venom.getWord().isBlank(), "venom must be answerable");
        assertTrue(venom.getWord().length() >= 5,
                "a middling word, not a two-letter freebie: " + venom.getWord());
    }

    @Test
    @DisplayName("a venom word never collides with a word the verse still wants")
    void venomNeverStealsAVerseWord() {
        // Otherwise one set of keystrokes would mean two things at once, which
        // is exactly what typing the verse word-at-a-time exists to avoid.
        GameState state = atTheFinale();

        for (int i = 0; i < 12_000; i++) {
            state.update();
            if (state.getBoss() == null || !state.getBoss().isFighting()) {
                break;
            }
            List<String> wanted = state.getBoss().remainingWords();
            for (Projectile bolt : state.getProjectiles()) {
                assertFalse(wanted.contains(bolt.getWord()),
                        "venom '" + bolt.getWord() + "' is also a word of the verse");
            }
        }
    }

    @Test
    @DisplayName("typing a venom word deflects it")
    void venomCanBeDeflected() {
        GameState state = atTheFinale();
        Projectile venom = waitForVenom(state);
        String word = venom.getWord();

        for (int i = 1; i <= word.length(); i++) {
            state.handleInput(word.substring(0, i));
        }

        assertFalse(venom.isActive(), "the bolt should have been shot out of the air");
    }

    @Test
    @DisplayName("deflecting a bolt does not cost the verse")
    void deflectingKeepsVerseProgress() {
        GameState state = atTheFinale();
        Projectile venom = waitForVenom(state);
        int verseBefore = state.getBoss().getStage();

        String word = venom.getWord();
        for (int i = 1; i <= word.length(); i++) {
            state.handleInput(word.substring(0, i));
        }

        assertEquals(verseBefore, state.getBoss().getStage());
    }

    @Test
    @DisplayName("venom flies slowly enough to be answered")
    void venomIsSlow() {
        // Five and a half seconds. The player is already holding a verse in
        // their head; a bolt they cannot read in time is a hazard, not a
        // decision.
        assertTrue(GameConfig.VENOM_FLIGHT_TICKS >= GameConfig.TARGET_FPS * 4,
                "venom would arrive before it could be read");
    }

    @Test
    @DisplayName("typing goes to the verse when no bolt matches")
    void typingIsOwnedByTheBoss() {
        GameState state = atTheFinale();
        settleIntoTheFight(state);

        String word = state.getBoss().currentWord();
        ResolveResult result = state.handleInput(word.substring(0, 1));

        assertEquals(MatchStatus.LOCKED, result.status());
        assertTrue(result.target() instanceof BossFight);
    }

    @Test
    @DisplayName("a mistype tells the input field to clear itself")
    void typoClearsTheField() {
        GameState state = atTheFinale();
        settleIntoTheFight(state);

        ResolveResult result = state.handleInput("zzz");

        assertEquals(MatchStatus.TYPO, result.status());
        assertEquals("", result.validBuffer(),
                "an empty valid buffer is how the verse reset reaches the field");
    }

    @Test
    @DisplayName("finishing a verse sweeps the venom already in the air")
    void counterVolleyClearsTheSky() {
        GameState state = atTheFinale();
        settleIntoTheFight(state);

        waitForVenom(state);

        typeVerseThrough(state);

        for (Projectile bolt : state.getProjectiles()) {
            assertFalse(bolt.isActive(),
                    "a verse landed but the sky was not cleared, so the paragraph "
                            + "is not actually a defence against anything");
        }
    }

    /**
     * Types the boss's current verse through GameState, one word at a time,
     * confirming each with the trailing space that actually advances it.
     */
    private static void typeVerseThrough(GameState state) {
        BossFight boss = state.getBoss();
        int verse = boss.getStage();
        while (boss.getStage() == verse && boss.isFighting()) {
            String word = boss.currentWord();
            for (int i = 1; i <= word.length(); i++) {
                state.handleInput(word.substring(0, i));
            }
            state.handleInput(word + " ");
        }
    }

    @Test
    @DisplayName("the run is won only once the boss is finished")
    void victoryWaitsForTheBoss() {
        GameState state = atTheFinale();
        settleIntoTheFight(state);
        assertFalse(state.isVictory());

        for (int verse = 0; verse < state.getBoss().getStageCount(); verse++) {
            typeVerseThrough(state);
        }
        assertFalse(state.isVictory(), "the death animation should still be playing");

        for (int i = 0; i <= BossFight.DEATH_TICKS + 2; i++) {
            state.update();
        }

        assertTrue(state.isVictory(), "beating the boss should win the run");
        assertTrue(state.isGameOver());
    }

    @Test
    @DisplayName("a restart clears the finale")
    void restartClearsTheBoss() {
        GameState state = atTheFinale();

        state.restart();

        assertNull(state.getBoss());
        assertFalse(state.isBossActive());
    }

    @Test
    @DisplayName("venom that lands costs a life like anything else")
    void venomStillHurts() {
        GameState state = atTheFinale();
        int halves = state.getHalfLives();

        for (int i = 0; i < 20_000 && state.getHalfLives() == halves; i++) {
            state.update();
        }

        assertEquals(halves - GameConfig.DAMAGE_PROJECTILE, state.getHalfLives(),
                "a bolt costs half a heart, like every other projectile");
    }

    @Test
    @DisplayName("a banked ward absorbs venom, as it does everything else")
    void wardAbsorbsVenom() {
        GameState state = atTheFinale();
        state.applyPowerUp(PowerUpType.NAGA_SHIELD);
        int halves = state.getHalfLives();

        for (int i = 0; i < 20_000; i++) {
            state.update();
            if (state.getPowerUpState().getShieldCharges() == 0) {
                assertEquals(halves, state.getHalfLives(),
                        "the ward should have taken the hit, not the player");
                return;
            }
        }
        throw new AssertionError("the boss never landed a hit to absorb");
    }

    // ---- the paragraphs themselves -----------------------------------------

    @Test
    @DisplayName("every tier has a paragraph, and every paragraph has verses")
    void everyTierHasAFinale() {
        com.guardiansofangkor.i18n.WordBank bank =
                new com.guardiansofangkor.i18n.WordBank(Language.ENGLISH, new Random(1));

        for (Difficulty tier : Difficulty.values()) {
            List<String> paragraph = bank.bossParagraph(tier.getWordBankKey(), new Random(1));

            assertNotNull(paragraph, tier + " has no finale");
            assertTrue(paragraph.size() >= 3,
                    tier + " should be at least three verses, got " + paragraph.size());
            for (String verse : paragraph) {
                assertFalse(verse.isBlank(), tier + " has an empty verse");
                assertTrue(verse.length() <= 60,
                        tier + " verse is too long for two lines: \"" + verse + "\"");
                assertEquals(verse.toLowerCase(java.util.Locale.ROOT), verse,
                        tier + " verse has capitals, which cost a shift key the "
                                + "rest of the game never asks for: \"" + verse + "\"");
                assertTrue(verse.matches("[a-z ]+"),
                        tier + " verse has punctuation, which is not on the "
                                + "typing path: \"" + verse + "\"");
            }
        }
    }

    @Test
    @DisplayName("tiers offer more than one paragraph, so a rerun differs")
    void finalesVary() {
        com.guardiansofangkor.i18n.WordBank bank =
                new com.guardiansofangkor.i18n.WordBank(Language.ENGLISH, new Random(1));

        for (Difficulty tier : List.of(Difficulty.EASY, Difficulty.MEDIUM)) {
            assertTrue(bank.bossParagraphCount(tier.getWordBankKey()) >= 2,
                    tier + " would present the identical fight every run");
        }
    }

    @Test
    @DisplayName("an unknown tier still gets a winnable finale")
    void unknownTiersFallBack() {
        com.guardiansofangkor.i18n.WordBank bank =
                new com.guardiansofangkor.i18n.WordBank(Language.ENGLISH, new Random(1));

        List<String> paragraph = bank.bossParagraph("brutal", new Random(1));

        assertNotNull(paragraph);
        assertFalse(paragraph.isEmpty(), "a boss with nothing to type is unwinnable");
    }

    // ---- the tightened hitbox ----------------------------------------------

    @Test
    @DisplayName("an enemy has to reach Preah Ream before it costs a life")
    void breachHitboxIsTight() {
        // At 105 the box was wider than the hero is drawn, so lives were lost
        // while the monster was visibly still a stride away.
        assertTrue(GameConfig.BREACH_RADIUS <= 70,
                "the breach radius is back to feeling like a stolen life");

        // And it must still trigger before the enemy walks past him entirely.
        assertTrue(GameConfig.BREACH_RADIUS >= 30,
                "too tight and enemies would slide through the hero");
    }

    @Test
    @DisplayName("enemies still reach full size before they can breach")
    void breachHappensAfterFullSize() {
        Enemy walker = new Enemy(EnemyType.BEISACH, ApproachPath.GROUND_FLANK,
                "stone", GameConfig.FLANK_RUN_MIN, 1, 1.0);

        double scaleAtBreach = 0;
        for (int i = 0; i < 5000 && !walker.hasBreached(); i++) {
            walker.update();
            scaleAtBreach = walker.depthScale();
        }

        assertTrue(walker.hasBreached(), "the walker never arrived");
        assertEquals(1.0, scaleAtBreach, 0.0001,
                "monsters must be drawn at full size before they are culled");
    }
}
