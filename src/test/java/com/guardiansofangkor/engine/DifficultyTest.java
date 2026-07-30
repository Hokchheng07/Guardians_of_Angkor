package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.EnemyType;
import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.i18n.WordBank;
import com.guardiansofangkor.util.GraphemeCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Difficulty — tiers and their effect on the curves")
class DifficultyTest {

    @Test
    @DisplayName("Medium is the reference tuning, so all its scales are neutral")
    void mediumIsTheBaseline() {
        assertEquals(Difficulty.MEDIUM, Difficulty.reference());
        assertEquals(1.0, Difficulty.MEDIUM.getSpeedScale(), 0.0001);
        assertEquals(1.0, Difficulty.MEDIUM.getSpawnIntervalScale(), 0.0001);
        assertEquals(0, Difficulty.MEDIUM.getWordMinShift());
        assertEquals(0, Difficulty.MEDIUM.getWordMaxShift());
    }

    @Test
    @DisplayName("Medium matches the single-argument curves exactly")
    void mediumMatchesBareCurves() {
        for (int level = 1; level <= 20; level++) {
            assertEquals(DifficultyCurve.baseSpeed(level),
                    DifficultyCurve.baseSpeed(level, Difficulty.MEDIUM), 0.0001,
                    "level " + level);
            assertEquals(DifficultyCurve.spawnIntervalTicks(level),
                    DifficultyCurve.spawnIntervalTicks(level, Difficulty.MEDIUM),
                    "level " + level);
        }
    }

    @Test
    @DisplayName("Easy is meaningfully slower than Medium, around a quarter")
    void easyIsSlowerThanMedium() {
        double ratio = Difficulty.EASY.getSpeedScale() / Difficulty.MEDIUM.getSpeedScale();

        assertTrue(ratio < 1.0, "Easy must be slower");
        assertTrue(ratio >= 0.68 && ratio <= 0.78,
                "expected roughly 25-30% slower, got " + Math.round((1 - ratio) * 100) + "%");
    }

    @Test
    @DisplayName("Easy is slower at every level, not just early on")
    void easyIsSlowerAtEveryLevel() {
        for (int level = 1; level <= 30; level++) {
            assertTrue(DifficultyCurve.baseSpeed(level, Difficulty.EASY)
                            < DifficultyCurve.baseSpeed(level, Difficulty.MEDIUM),
                    "Easy caught up to Medium at level " + level);
        }
    }

    @Test
    @DisplayName("Easy gives more room between spawns")
    void easySpawnsLessOften() {
        for (int level = 1; level <= 20; level++) {
            assertTrue(DifficultyCurve.spawnIntervalTicks(level, Difficulty.EASY)
                            >= DifficultyCurve.spawnIntervalTicks(level, Difficulty.MEDIUM),
                    "level " + level);
        }
    }

    @Test
    @DisplayName("the spawn interval still has a floor after tier scaling")
    void spawnIntervalKeepsItsFloor() {
        for (Difficulty difficulty : Difficulty.values()) {
            assertTrue(DifficultyCurve.spawnIntervalTicks(500, difficulty) >= 20,
                    difficulty + " let the interval collapse");
        }
    }

    @Test
    @DisplayName("per-type speed relationships survive every tier")
    void typeRelationshipsHoldAcrossTiers() {
        // The tier scales the base, so a Pret must never outrun an Ahp on any
        // tier — the split between light and heavy types is tier-independent.
        for (Difficulty difficulty : Difficulty.values()) {
            for (int level = 1; level <= 40; level++) {
                assertTrue(DifficultyCurve.speedFor(EnemyType.PRET, level, difficulty)
                                < DifficultyCurve.speedFor(EnemyType.AHP, level, difficulty),
                        difficulty + " level " + level);
            }
        }
    }

    // ---- Easy's shorter words ---------------------------------------------

    @Test
    @DisplayName("Easy pulls word lengths down")
    void easyShortensWords() {
        assertTrue(Difficulty.EASY.getWordMaxShift() < 0,
                "Easy should cap words shorter than the baseline");
    }

    @Test
    @DisplayName("Easy actually hands out shorter words than Medium")
    void easyWordsAreShorterInPractice() {
        WordBank bank = new WordBank(Language.ENGLISH, new Random(7));

        int easyTotal = 0;
        int mediumTotal = 0;
        int samples = 60;

        for (int i = 0; i < samples; i++) {
            easyTotal += GraphemeCounter.count(bank.wordFor(EnemyType.YEAK, null,
                    Difficulty.EASY.getWordMinShift(), Difficulty.EASY.getWordMaxShift()));
            mediumTotal += GraphemeCounter.count(bank.wordFor(EnemyType.YEAK, null,
                    Difficulty.MEDIUM.getWordMinShift(),
                    Difficulty.MEDIUM.getWordMaxShift()));
        }

        assertTrue(easyTotal <= mediumTotal,
                "Easy averaged " + (easyTotal / (double) samples)
                        + " chars vs Medium " + (mediumTotal / (double) samples));
    }

    @Test
    @DisplayName("a large negative shift cannot ask for empty words")
    void shiftsCannotInvertTheWindow() {
        WordBank bank = new WordBank(Language.ENGLISH, new Random(3));

        for (int i = 0; i < 40; i++) {
            String word = bank.wordFor(EnemyType.BEISACH, null, -20, -20);
            assertNotNull(word);
            assertFalse(word.isEmpty(), "a clamped window must still yield a word");
        }
    }

    // ---- Easy's boss -------------------------------------------------------

    @Test
    @DisplayName("Easy ends with the Naga at level 10, chained three times")
    void easyBossIsTheNaga() {
        assertEquals(EnemyType.NAGA, Difficulty.EASY.getFinalBossType());
        assertEquals(10, Difficulty.EASY.getFinalBossLevel());
        assertEquals(3, Difficulty.EASY.getFinalBossChainLength());
        assertTrue(Difficulty.EASY.hasFinalBoss());
    }

    @Test
    @DisplayName("Easy's boss gets longer words than Easy's ordinary enemies")
    void easyBossWordsAreLonger() {
        assertTrue(Difficulty.EASY.getBossWordLengthBonus() > 0,
                "the boss should be the hardest typing in an Easy run");

        int ordinaryMax = EnemyType.NAGA.getMaxWordLength()
                + Difficulty.EASY.getWordMaxShift();
        int bossMax = ordinaryMax + Difficulty.EASY.getBossWordLengthBonus();

        assertTrue(bossMax > ordinaryMax);
        assertTrue(bossMax > EnemyType.NAGA.getMaxWordLength(),
                "and longer than the untiered baseline too");
    }

    @Test
    @DisplayName("Medium still ends with Krong Reap at level 15")
    void mediumBossIsKrongReap() {
        assertEquals(EnemyType.KRONG_REAP, Difficulty.MEDIUM.getFinalBossType());
        assertEquals(15, Difficulty.MEDIUM.getFinalBossLevel());
    }

    @Test
    @DisplayName("Endless has no final boss")
    void endlessNeverEnds() {
        assertFalse(Difficulty.ENDLESS.hasFinalBoss());
    }

    @Test
    @DisplayName("the Easy boss really spawns as a chained Naga at level 10")
    void easyBossSpawnsAtLevelTen() {
        WaveManager waves = new WaveManager(
                new WordBank(Language.ENGLISH, new Random(11)),
                Difficulty.EASY, new Random(11));
        waves.resumeAtLevel(9);

        List<com.guardiansofangkor.entities.Enemy> field = new ArrayList<>();
        boolean sawBoss = false;

        for (int tick = 0; tick < 30_000 && waves.getLevel() <= 10; tick++) {
            for (var enemy : waves.update(field)) {
                if (waves.getLevel() == 10 && enemy.getType() == EnemyType.NAGA
                        && enemy.getChainLength() == 3) {
                    sawBoss = true;
                }
            }
            field.clear();
        }

        assertTrue(sawBoss,
                "Easy should present a three-word Naga as its finale on level 10");
    }

    @Test
    @DisplayName("every tier carries a tagline short enough for the panel")
    void everyTierHasAShortTagline() {
        for (Difficulty difficulty : Difficulty.values()) {
            assertFalse(difficulty.getTagline().isBlank(), difficulty + " needs a tagline");
            assertTrue(difficulty.getTagline().length() <= 48,
                    difficulty + " tagline is too long: " + difficulty.getTagline().length());
        }
    }
}
