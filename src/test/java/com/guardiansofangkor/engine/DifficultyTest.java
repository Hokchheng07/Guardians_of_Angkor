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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Difficulty — tiers and their effect on the curves")
class DifficultyTest {

    @Test
    @DisplayName("Hard is the reference tuning, so all its scales are neutral")
    void hardIsTheBaseline() {
        // The reference moved here from Medium when the tiers were rebalanced:
        // what shipped as Medium was promoted to Hard unchanged, and the label
        // moved with the numbers rather than the numbers being rewritten.
        assertEquals(Difficulty.HARD, Difficulty.reference());
        assertEquals(1.0, Difficulty.HARD.getSpeedScale(), 0.0001);
        assertEquals(1.0, Difficulty.HARD.getSpawnIntervalScale(), 0.0001);
        assertEquals(0, Difficulty.HARD.getWordMinShift());
        assertEquals(0, Difficulty.HARD.getWordMaxShift());
    }

    @Test
    @DisplayName("Hard matches the single-argument curves exactly")
    void hardMatchesBareCurves() {
        for (int level = 1; level <= 20; level++) {
            assertEquals(DifficultyCurve.baseSpeed(level),
                    DifficultyCurve.baseSpeed(level, Difficulty.HARD), 0.0001,
                    "level " + level);
            assertEquals(DifficultyCurve.spawnIntervalTicks(level),
                    DifficultyCurve.spawnIntervalTicks(level, Difficulty.HARD),
                    "level " + level);
        }
    }

    @Test
    @DisplayName("Easy is substantially slower than the reference tuning")
    void easyIsSlowerThanTheReference() {
        double ratio = Difficulty.EASY.getSpeedScale() / Difficulty.HARD.getSpeedScale();

        assertTrue(ratio < 1.0, "Easy must be slower");
        assertTrue(ratio <= 0.65,
                "Easy was reported as still too hard to finish; expected at least 35% "
                        + "slower, got " + Math.round((1 - ratio) * 100) + "%");
    }

    @Test
    @DisplayName("the three playable tiers form a ladder with no cliff in it")
    void tiersEscalateEvenly() {
        // The reported problem was a single jump from Easy to what is now Hard.
        // Medium exists to halve that jump, so it has to sit genuinely between
        // the two on every lever rather than hugging one end.
        assertTrue(Difficulty.EASY.getSpeedScale() < Difficulty.MEDIUM.getSpeedScale());
        assertTrue(Difficulty.MEDIUM.getSpeedScale() < Difficulty.HARD.getSpeedScale());

        assertTrue(Difficulty.EASY.getSpawnIntervalScale()
                > Difficulty.MEDIUM.getSpawnIntervalScale());
        assertTrue(Difficulty.MEDIUM.getSpawnIntervalScale()
                > Difficulty.HARD.getSpawnIntervalScale());

        assertTrue(Difficulty.EASY.getEnemyCountScale()
                < Difficulty.MEDIUM.getEnemyCountScale());
        assertTrue(Difficulty.MEDIUM.getEnemyCountScale()
                < Difficulty.HARD.getEnemyCountScale());
    }

    @Test
    @DisplayName("Medium sits near the midpoint rather than beside either neighbour")
    void mediumIsActuallyInTheMiddle() {
        double easy = Difficulty.EASY.getSpeedScale();
        double hard = Difficulty.HARD.getSpeedScale();
        double medium = Difficulty.MEDIUM.getSpeedScale();

        double position = (medium - easy) / (hard - easy);
        assertTrue(position > 0.35 && position < 0.65,
                "Medium sits at " + Math.round(position * 100)
                        + "% of the way from Easy to Hard, which puts the cliff back");
    }

    @Test
    @DisplayName("late levels were pulled back within reach of a fast typist")
    void theLateGameRampWasDamped() {
        // Level eleven on the reference tuning was reported as unreactable at
        // 102 words per minute. That is not difficulty, it is a wall, and the
        // fix is to the slope rather than to the starting speed.
        assertTrue(DifficultyCurve.LEVEL_RAMP_DAMPING < 1.0,
                "the ramp must actually be damped");

        double undampedAtEleven = 0.40 + 10 * 0.035;
        assertTrue(DifficultyCurve.baseSpeed(11) < undampedAtEleven,
                "level eleven should be slower than it used to be");
        assertEquals(0.40, DifficultyCurve.baseSpeed(1), 0.0001,
                "but level one must be untouched — the opening was never the problem");
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

    // ---- run length --------------------------------------------------------

    @Test
    @DisplayName("each tier runs a longer game than the one below it")
    void tiersRunProgressivelyLonger() {
        // Tiers used to share one length, on the theory that a tier changes how
        // hard a run is rather than how long. Climbing the ladder is now meant
        // to be signing up for more as well as for faster, so the counts differ.
        assertEquals(10, Difficulty.EASY.getWaveCount());
        assertEquals(15, Difficulty.MEDIUM.getWaveCount());
        assertEquals(20, Difficulty.HARD.getWaveCount());

        for (Difficulty tier : List.of(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)) {
            assertTrue(tier.isWinnable(), tier + " must be finishable");
            assertEquals(tier.getWaveCount(), tier.getFinalLevel(),
                    tier + " should end on its last wave");
            assertEquals(tier.getWaveCount(), tier.getFinalBossLevel(),
                    tier + " boss level");
        }
    }

    @Test
    @DisplayName("Endless is structured but not playable")
    void endlessIsScaffoldingOnly() {
        // The tuning is real so nothing has to special-case a half-configured
        // tier; the mode itself is simply not built yet.
        assertFalse(Difficulty.ENDLESS.isImplemented());
        assertEquals(Integer.MAX_VALUE, Difficulty.ENDLESS.getFinalLevel());
        assertTrue(Difficulty.ENDLESS.getEnemyCountScale() > 0,
                "the scaffolding still has to be configured, not left at zero");
    }

    // ---- the unlock ladder -------------------------------------------------

    @Test
    @DisplayName("the tiers form a single chain, and Easy opens it")
    void theLadderIsAChain() {
        assertNull(Difficulty.EASY.requiredPredecessor(), "Easy must be open from the start");
        assertEquals(Difficulty.EASY, Difficulty.MEDIUM.requiredPredecessor());
        assertEquals(Difficulty.MEDIUM, Difficulty.HARD.requiredPredecessor());
        assertEquals(Difficulty.HARD, Difficulty.ENDLESS.requiredPredecessor());
    }

    @Test
    @DisplayName("progress opens one rung at a time")
    void progressOpensOneRungAtATime() {
        DifficultyProgress fresh = DifficultyProgress.fresh();
        assertTrue(fresh.isUnlocked(Difficulty.EASY));
        assertFalse(fresh.isUnlocked(Difficulty.MEDIUM));

        DifficultyProgress afterEasy = fresh.withCleared(Difficulty.EASY);
        assertTrue(afterEasy.isUnlocked(Difficulty.MEDIUM));
        assertFalse(afterEasy.isUnlocked(Difficulty.HARD));

        assertTrue(afterEasy.withCleared(Difficulty.MEDIUM).isUnlocked(Difficulty.HARD));
    }

    @Test
    @DisplayName("a locked tier explains what would open it")
    void lockReasonNamesThePredecessor() {
        String reason = DifficultyProgress.fresh().lockReason(Difficulty.HARD);

        assertTrue(reason.contains("Medium"), "got: " + reason);
        assertTrue(reason.contains("Hard"), "got: " + reason);
        assertEquals("", DifficultyProgress.fresh().lockReason(Difficulty.EASY),
                "an open tier has nothing to explain");
    }

    @Test
    @DisplayName("a corrupt or empty unlock set costs unlocks, never the menu")
    void progressIsTotal() {
        DifficultyProgress nulls = new DifficultyProgress(null);
        assertTrue(nulls.isUnlocked(Difficulty.EASY));
        assertFalse(nulls.isUnlocked(Difficulty.MEDIUM));

        DifficultyProgress junk = new DifficultyProgress(
                new java.util.HashSet<>(List.of("  EASY  ", "nonsense")));
        assertTrue(junk.isUnlocked(Difficulty.MEDIUM),
                "whitespace and capitals in a hand-edited save should still count");
    }

    // ---- the finale's length -----------------------------------------------

    @Test
    @DisplayName("the boss asks for the paragraph block its tier specifies")
    void bossHealthMatchesTheTier() {
        assertEquals(2, Difficulty.EASY.getBossParagraphsPerCycle());
        assertEquals(2, Difficulty.EASY.getBossSentencesPerParagraph());
        assertEquals(2, Difficulty.EASY.getBossCycles());
        assertEquals(4, Difficulty.EASY.getBossParagraphCount());
        assertEquals(8, Difficulty.EASY.getBossSentenceCount());

        for (Difficulty tier : List.of(Difficulty.MEDIUM, Difficulty.HARD)) {
            assertEquals(3, tier.getBossParagraphsPerCycle(), tier.toString());
            assertEquals(3, tier.getBossSentencesPerParagraph(), tier.toString());
            assertEquals(3, tier.getBossCycles(), tier.toString());
            assertEquals(9, tier.getBossParagraphCount(), tier.toString());
            assertEquals(27, tier.getBossSentenceCount(), tier.toString());
        }
    }

    @Test
    @DisplayName("the finale gets longer as the tier does")
    void finaleGrowsWithTheTier() {
        assertTrue(Difficulty.EASY.getBossSentenceCount()
                < Difficulty.MEDIUM.getBossSentenceCount());
    }

    @Test
    @DisplayName("Easy ends with the Naga")
    void easyBossIsTheNaga() {
        assertEquals(EnemyType.NAGA, Difficulty.EASY.getFinalBossType());
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

        // Each tier is asked at its own last level, since they no longer share
        // one — asking Easy about level 15 would be asking about a level it
        // never reaches.
        int easy = GraphemeCounter.count(bank.finalBossWord(null,
                bank.policyFor(Difficulty.EASY.getWordBankKey(),
                        Difficulty.EASY.getFinalLevel())));
        int medium = GraphemeCounter.count(bank.finalBossWord(null,
                bank.policyFor(Difficulty.MEDIUM.getWordBankKey(),
                        Difficulty.MEDIUM.getFinalLevel())));
        int hard = GraphemeCounter.count(bank.finalBossWord(null,
                bank.policyFor(Difficulty.HARD.getWordBankKey(),
                        Difficulty.HARD.getFinalLevel())));

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
    @DisplayName("the final wave holds no boss — the finale comes after it")
    void theLastWaveIsOrdinary() {
        // The boss used to be the last enemy of the final wave. It is now a
        // phase of its own that begins when that wave is finished, so nothing
        // in the wave itself should be a Naga or a Krong Reap.
        Difficulty tier = Difficulty.EASY;
        WaveManager waves = new WaveManager(
                new WordBank(Language.ENGLISH, new Random(11)),
                tier, new Random(11));
        waves.resumeAtLevel(tier.getFinalLevel() - 1);

        List<Enemy> field = new ArrayList<>();
        int spawned = 0;

        for (int tick = 0; tick < 30_000 && !waves.isRunComplete(); tick++) {
            for (Enemy enemy : waves.update(field)) {
                spawned++;
                assertTrue(enemy.getType() != EnemyType.KRONG_REAP,
                        "the finale should not be inside the wave");
            }
            field.clear();
        }

        assertTrue(spawned > 0, "the last level should still send a wave");
        assertTrue(waves.isRunComplete(), "and that wave should finish");
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
    @DisplayName("Hard's enemy count matches the bare curve")
    void hardEnemyCountIsTheBaseline() {
        for (int level = 1; level <= 30; level++) {
            assertEquals(DifficultyCurve.enemyCount(level),
                    DifficultyCurve.enemyCount(level, Difficulty.HARD), "level " + level);
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
