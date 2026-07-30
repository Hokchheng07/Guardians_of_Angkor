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

    private static void settleArrival(BossFight boss) {
        for (int i = 0; i <= BossFight.ARRIVAL_TICKS; i++) {
            boss.update();
        }
    }

    /** Types a whole string one character at a time, as the input field would. */
    private static BossFight.Result typeOut(BossFight boss, String text) {
        BossFight.Result last = BossFight.Result.NONE;
        for (int i = 1; i <= text.length(); i++) {
            last = boss.submit(text.substring(0, i));
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
    @DisplayName("no venom flies during the entrance")
    void noVenomWhileArriving() {
        BossFight boss = new BossFight(EnemyType.NAGA, VERSES, Difficulty.EASY);

        for (int i = 0; i < BossFight.ARRIVAL_TICKS; i++) {
            boss.update();
            assertFalse(boss.isVenomDue(), "spat at tick " + i + ", before it had arrived");
        }
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
    @DisplayName("correct letters advance the verse")
    void correctLettersProgress() {
        BossFight boss = fighting();

        assertEquals(BossFight.Result.PROGRESS, boss.submit("o"));
        assertEquals(BossFight.Result.PROGRESS, boss.submit("on"));
        assertEquals("on", boss.getTyped());
        assertEquals("e two", boss.getRemaining());
    }

    @Test
    @DisplayName("finishing a verse reveals the next one")
    void clearingAVerseAdvances() {
        BossFight boss = fighting();

        assertEquals(BossFight.Result.STAGE_CLEARED, typeOut(boss, "one two"));

        assertEquals(1, boss.getStage());
        assertEquals("three four", boss.currentSentence());
        assertEquals("", boss.getTyped(), "the next verse starts empty");
        assertFalse(boss.isBeaten());
    }

    @Test
    @DisplayName("finishing the last verse beats the boss")
    void clearingEveryVerseWins() {
        BossFight boss = fighting();

        typeOut(boss, "one two");
        typeOut(boss, "three four");
        assertEquals(BossFight.Result.DEFEATED, typeOut(boss, "five six"));

        assertTrue(boss.isBeaten());
        assertEquals(0, boss.getHealthFraction(), 0.0001);
    }

    @Test
    @DisplayName("the run is not won until the death has played out")
    void deathHasToFinish() {
        BossFight boss = fighting();
        typeOut(boss, "one two");
        typeOut(boss, "three four");
        typeOut(boss, "five six");

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
        assertEquals(VERSES.get(0), boss.getRemaining());
    }

    @Test
    @DisplayName("a mistype never costs a verse already cleared")
    void typoDoesNotUndoClearedVerses() {
        // One slip at word thirty undoing the whole fight would make the finale
        // a lottery rather than a test.
        BossFight boss = fighting();
        typeOut(boss, "one two");
        typeOut(boss, "three four");
        assertEquals(2, boss.getStage());

        boss.submit("f");
        boss.submit("fx");

        assertEquals(2, boss.getStage(), "cleared verses must stay cleared");
        assertEquals("five six", boss.currentSentence());
    }

    @Test
    @DisplayName("a reset verse can be typed again from scratch")
    void resetVersesAreStillWinnable() {
        BossFight boss = fighting();
        boss.submit("onx");

        assertEquals(BossFight.Result.STAGE_CLEARED, typeOut(boss, "one two"));
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
        typeOut(boss, "one two");
        typeOut(boss, "three four");
        typeOut(boss, "five six");

        assertEquals(BossFight.Result.NONE, boss.submit("f"));
    }

    // ---- health ------------------------------------------------------------

    @Test
    @DisplayName("health falls as the paragraph is typed, not only per verse")
    void healthTracksLetters() {
        // A bar that moves three times in a two-minute fight tells the player
        // nothing while they are actually typing.
        BossFight boss = fighting();
        double full = boss.getHealthFraction();

        boss.submit("one");
        double partway = boss.getHealthFraction();

        assertTrue(partway < full, "mid-verse typing should show on the bar");
        assertTrue(partway > 0.5, "and not finish the first verse's worth early");
    }

    @Test
    @DisplayName("health is monotonic across the whole fight")
    void healthOnlyFalls() {
        BossFight boss = fighting();
        double last = boss.getHealthFraction();

        for (String verse : VERSES) {
            for (int i = 1; i <= verse.length(); i++) {
                boss.submit(verse.substring(0, i));
                double now = boss.getHealthFraction();
                assertTrue(now <= last + 0.0001,
                        "health went back up at '" + verse.substring(0, i) + "'");
                last = now;
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
    @DisplayName("venom comes faster as verses fall")
    void venomEscalates() {
        BossFight boss = fighting();
        int opening = boss.venomIntervalTicks();

        typeOut(boss, "one two");
        int later = boss.venomIntervalTicks();

        assertTrue(later < opening,
                "the last verse should be the loudest: " + opening + " then " + later);
    }

    @Test
    @DisplayName("the venom interval has a floor")
    void venomHasAFloor() {
        BossFight boss = new BossFight(EnemyType.KRONG_REAP,
                List.of("a", "b", "c", "d", "e", "f", "g", "h"), Difficulty.HARD);
        settleArrival(boss);

        for (int i = 0; i < 7; i++) {
            typeOut(boss, boss.currentSentence());
            assertTrue(boss.venomIntervalTicks() >= 60,
                    "escalation ran away at stage " + i);
        }
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

    @Test
    @DisplayName("the boss spits venom, and venom cannot be typed away")
    void venomIsAHazardNotATarget() {
        GameState state = atTheFinale();

        Projectile venom = null;
        for (int i = 0; i < 4000 && venom == null; i++) {
            state.update();
            for (Projectile p : state.getProjectiles()) {
                if (p.getKind() == Projectile.Kind.VENOM) {
                    venom = p;
                }
            }
        }

        assertNotNull(venom, "the boss never spat");
        assertFalse(venom.isTypeable());
        assertEquals("", venom.getWord(), "venom carries nothing to type");
    }

    @Test
    @DisplayName("typing goes to the paragraph, never to a bolt")
    void typingIsOwnedByTheBoss() {
        GameState state = atTheFinale();
        for (int i = 0; i <= BossFight.ARRIVAL_TICKS; i++) {
            state.update();
        }

        String verse = state.getBoss().currentSentence();
        ResolveResult result = state.handleInput(verse.substring(0, 1));

        assertEquals(MatchStatus.LOCKED, result.status());
        assertTrue(result.target() instanceof BossFight);
    }

    @Test
    @DisplayName("a mistype tells the input field to clear itself")
    void typoClearsTheField() {
        GameState state = atTheFinale();
        for (int i = 0; i <= BossFight.ARRIVAL_TICKS; i++) {
            state.update();
        }

        ResolveResult result = state.handleInput("zzz");

        assertEquals(MatchStatus.TYPO, result.status());
        assertEquals("", result.validBuffer(),
                "an empty valid buffer is how the verse reset reaches the field");
    }

    @Test
    @DisplayName("finishing a verse sweeps the venom already in the air")
    void counterVolleyClearsTheSky() {
        GameState state = atTheFinale();
        for (int i = 0; i <= BossFight.ARRIVAL_TICKS; i++) {
            state.update();
        }

        // Let it spit a few times.
        for (int i = 0; i < 4000 && state.getProjectiles().isEmpty(); i++) {
            state.update();
        }
        assertFalse(state.getProjectiles().isEmpty(), "the boss never spat");

        String verse = state.getBoss().currentSentence();
        for (int i = 1; i <= verse.length(); i++) {
            state.handleInput(verse.substring(0, i));
        }

        for (Projectile bolt : state.getProjectiles()) {
            assertFalse(bolt.isActive(),
                    "a verse landed but the sky was not cleared, so the paragraph "
                            + "is not actually a defence against anything");
        }
    }

    @Test
    @DisplayName("the run is won only once the boss is finished")
    void victoryWaitsForTheBoss() {
        GameState state = atTheFinale();
        for (int i = 0; i <= BossFight.ARRIVAL_TICKS; i++) {
            state.update();
        }
        assertFalse(state.isVictory());

        for (int verse = 0; verse < state.getBoss().getStageCount(); verse++) {
            String text = state.getBoss().currentSentence();
            for (int i = 1; i <= text.length(); i++) {
                state.handleInput(text.substring(0, i));
            }
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
        int lives = state.getLives();

        for (int i = 0; i < 20_000 && state.getLives() == lives; i++) {
            state.update();
        }

        assertTrue(state.getLives() < lives,
                "ignoring the boss entirely should eventually kill you");
    }

    @Test
    @DisplayName("a banked ward absorbs venom, as it does everything else")
    void wardAbsorbsVenom() {
        GameState state = atTheFinale();
        state.applyPowerUp(PowerUpType.NAGA_SHIELD);
        int lives = state.getLives();

        for (int i = 0; i < 20_000; i++) {
            state.update();
            if (state.getPowerUpState().getShieldCharges() == 0) {
                assertEquals(lives, state.getLives(),
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
