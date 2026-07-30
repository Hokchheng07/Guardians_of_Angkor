package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.Enemy;
import com.guardiansofangkor.entities.EnemyType;
import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.i18n.WordBank;
import com.guardiansofangkor.i18n.WordPolicy;
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
    @DisplayName("Easy is substantially slower than Medium")
    void easyIsSlowerThanMedium() {
        double ratio = Difficulty.EASY.getSpeedScale() / Difficulty.MEDIUM.getSpeedScale();

        assertTrue(ratio < 1.0, "Easy must be slower");
        assertTrue(ratio <= 0.65,
                "Easy was reported as still too hard to finish; expected at least 35% "
                        + "slower, got " + Math.round((1 - ratio) * 100) + "%");
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
    @DisplayName("Easy gives markedly more room between spawns")
    void easySpawnsLessOften() {
        for (int level = 1; level <= 20; level++) {
            assertTrue(DifficultyCurve.spawnIntervalTicks(level, Difficulty.EASY)
                            > DifficultyCurve.spawnIntervalTicks(level, Difficulty.MEDIUM),
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
        WordPolicy easy = bank.policyFor(Difficulty.EASY.getWordBankKey(), 1);
        WordPolicy medium = bank.policyFor(Difficulty.MEDIUM.getWordBankKey(), 1);

        int easyTotal = 0;
        int mediumTotal = 0;
        int samples = 60;

        for (int i = 0; i < samples; i++) {
            easyTotal += GraphemeCounter.count(bank.wordFor(EnemyType.YEAK, null, easy,
                    Difficulty.EASY.getWordMinShift(), Difficulty.EASY.getWordMaxShift()));
            mediumTotal += GraphemeCounter.count(bank.wordFor(EnemyType.YEAK, null, medium,
                    Difficulty.MEDIUM.getWordMinShift(),
                    Difficulty.MEDIUM.getWordMaxShift()));
        }

        assertTrue(easyTotal < mediumTotal,
                "Easy averaged " + (easyTotal / (double) samples)
                        + " chars vs Medium " + (mediumTotal / (double) samples));
    }

    @Test
    @DisplayName("no tier serves a long word on level one")
    void earlyLevelsStayShort() {
        // The reported problem was that a beginner met eight-letter words in the
        // opening minute. Guard it for every playable tier, not just Easy.
        for (Difficulty tier : List.of(Difficulty.EASY, Difficulty.MEDIUM)) {
            WordBank bank = new WordBank(Language.ENGLISH, new Random(5));
            WordPolicy policy = bank.policyFor(tier.getWordBankKey(), 1);

            for (EnemyType type : EnemyType.values()) {
                for (int i = 0; i < 25; i++) {
                    String word = bank.wordFor(type, null, policy,
                            tier.getWordMinShift(), tier.getWordMaxShift());
                    assertTrue(GraphemeCounter.count(word) <= 7,
                            tier + " level 1 served '" + word + "' ("
                                    + GraphemeCounter.count(word) + " letters) to a " + type);
                }
            }
        }
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

    // ---- the shared level-15 finale ----------------------------------------

    @Test
    @DisplayName("Easy, Medium and Hard all finish on level 15")
    void finiteTiersShareTheirLength() {
        // A tier changes how hard the same run is, not how long it is — a player
        // moving up from Easy should recognise the shape of what they attempt.
        for (Difficulty tier : List.of(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)) {
            assertTrue(tier.isWinnable(), tier + " must be finishable");
            assertEquals(15, tier.getFinalLevel(), tier + " should end on level 15");
            assertEquals(15, tier.getFinalBossLevel(), tier + " boss level");
        }
    }

    @Test
    @DisplayName("Easy ends with a three-word Naga")
    void easyBossIsTheNaga() {
        assertEquals(EnemyType.NAGA, Difficulty.EASY.getFinalBossType());
        assertEquals(3, Difficulty.EASY.getFinalBossChainLength());
        assertTrue(Difficulty.EASY.hasFinalBoss());
    }

    @Test
    @DisplayName("Medium and Hard end with Krong Reap")
    void heavierTiersEndWithKrongReap() {
        assertEquals(EnemyType.KRONG_REAP, Difficulty.MEDIUM.getFinalBossType());
        assertEquals(EnemyType.KRONG_REAP, Difficulty.HARD.getFinalBossType());
    }

    @Test
    @DisplayName("the boss word bank climbs as the tier does")
    void bossVocabularyClimbsWithTheTier() {
        WordBank bank = new WordBank(Language.ENGLISH, new Random(13));

        int easy = GraphemeCounter.count(bank.finalBossWord(null,
                bank.policyFor(Difficulty.EASY.getWordBankKey(), 15)));
        int medium = GraphemeCounter.count(bank.finalBossWord(null,
                bank.policyFor(Difficulty.MEDIUM.getWordBankKey(), 15)));
        int hard = GraphemeCounter.count(bank.finalBossWord(null,
                bank.policyFor(Difficulty.HARD.getWordBankKey(), 15)));

        assertTrue(easy < medium, "Easy's finale (" + easy
                + ") should ask less than Medium's (" + medium + ")");
        assertTrue(medium < hard, "Medium's finale (" + medium
                + ") should ask less than Hard's (" + hard + ")");
    }

    @Test
    @DisplayName("Endless has no final boss")
    void endlessNeverEnds() {
        assertFalse(Difficulty.ENDLESS.hasFinalBoss());
        assertFalse(Difficulty.ENDLESS.isWinnable());
    }

    @Test
    @DisplayName("the Easy boss really spawns as a chained Naga at level 15")
    void easyBossSpawnsAtTheFinale() {
        WaveManager waves = new WaveManager(
                new WordBank(Language.ENGLISH, new Random(11)),
                Difficulty.EASY, new Random(11));
        waves.resumeAtLevel(14);

        List<Enemy> field = new ArrayList<>();
        boolean sawBoss = false;

        for (int tick = 0; tick < 30_000 && waves.getLevel() <= 15; tick++) {
            for (Enemy enemy : waves.update(field)) {
                if (waves.getLevel() == 15 && enemy.getType() == EnemyType.NAGA
                        && enemy.getChainLength() == 3) {
                    sawBoss = true;
                }
            }
            field.clear();
        }

        assertTrue(sawBoss,
                "Easy should present a three-word Naga as its finale on level 15");
    }

    // ---- power-up generosity -----------------------------------------------

    @Test
    @DisplayName("Easy sends fewer enemies per level")
    void easySendsFewerEnemies() {
        // Slowing Easy down and shortening its words still left a beginner
        // facing twenty monsters on the last level, which no amount of extra
        // time per monster makes reasonable.
        for (int level = 1; level <= 15; level++) {
            assertTrue(DifficultyCurve.enemyCount(level, Difficulty.EASY)
                            <= DifficultyCurve.enemyCount(level, Difficulty.MEDIUM),
                    "level " + level);
        }
        assertTrue(DifficultyCurve.enemyCount(15, Difficulty.EASY)
                        < DifficultyCurve.enemyCount(15, Difficulty.MEDIUM),
                "the finale in particular should be lighter on Easy");
    }

    @Test
    @DisplayName("no tier can produce an empty level")
    void everyLevelSendsSomething() {
        for (Difficulty tier : Difficulty.values()) {
            for (int level = 1; level <= 30; level++) {
                assertTrue(DifficultyCurve.enemyCount(level, tier) >= 2,
                        tier + " level " + level + " would be empty");
            }
        }
    }

    @Test
    @DisplayName("Medium's enemy count matches the bare curve")
    void mediumEnemyCountIsTheBaseline() {
        for (int level = 1; level <= 30; level++) {
            assertEquals(DifficultyCurve.enemyCount(level),
                    DifficultyCurve.enemyCount(level, Difficulty.MEDIUM), "level " + level);
        }
    }

    @Test
    @DisplayName("gentler tiers drop more boons and hold them longer")
    void gentlerTiersAreMoreGenerous() {
        assertTrue(Difficulty.EASY.getPowerUpDropChance()
                > Difficulty.MEDIUM.getPowerUpDropChance());
        assertTrue(Difficulty.MEDIUM.getPowerUpDropChance()
                > Difficulty.HARD.getPowerUpDropChance());
        assertTrue(Difficulty.EASY.getPowerUpDurationScale()
                > Difficulty.HARD.getPowerUpDurationScale());
    }

    @Test
    @DisplayName("every tier has a word bank key matching its JSON section")
    void tiersMapOntoTheWordBank() {
        WordBank bank = new WordBank(Language.ENGLISH, new Random(2));

        for (Difficulty tier : Difficulty.values()) {
            assertEquals(tier.name().toLowerCase(java.util.Locale.ROOT), tier.getWordBankKey());
            assertTrue(bank.policyFor(tier.getWordBankKey(), 1).restrictsPools(),
                    tier + " has no band table in words_en.json");
        }
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
