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
import java.util.Set;
import java.util.EnumSet;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Difficulty — tiers and their effect on the curves")
class DifficultyTest {

    @Test
    @DisplayName("Hard is the reference for spawning and enemy count")
    void hardIsTheBaseline() {
        // Hard keeps the neutral spawn gap and enemy count that the bare curves
        // describe. It is no longer neutral on speed or words: the easier-game
        // rebalance locked every tier's speed at 0.60 and made Hard's words
        // one to two letters longer than the baseline.
        assertEquals(Difficulty.HARD, Difficulty.reference());
        assertEquals(1.0, Difficulty.HARD.getSpawnIntervalScale(), 0.0001);
        assertEquals(1.0, Difficulty.HARD.getEnemyCountScale(), 0.0001);
        assertTrue(Difficulty.HARD.getWordMinShift() > 0, "Hard asks for longer words");
    }

    @Test
    @DisplayName("Hard's spawn gap matches the single-argument curve exactly")
    void hardMatchesBareCurves() {
        for (int level = 1; level <= 20; level++) {
            assertEquals(DifficultyCurve.spawnIntervalTicks(level),
                    DifficultyCurve.spawnIntervalTicks(level, Difficulty.HARD),
                    "level " + level);
        }
    }

    @Test
    @DisplayName("every campaign tier moves at the same locked speed")
    void speedIsLockedAcrossTiers() {
        // The rebalance stopped using speed as the difficulty lever: fast
        // monsters were what made the game feel unfair. Tiers now differ in how
        // often enemies come, how many, and how long their words are.
        for (Difficulty tier : List.of(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)) {
            assertEquals(0.60, tier.getSpeedScale(), 0.0001, tier + " speed scale");
        }
    }

    @Test
    @DisplayName("the three playable tiers form a ladder with no cliff in it")
    void tiersEscalateEvenly() {
        // The reported problem was a single jump from Easy to what is now Hard.
        // Medium exists to halve that jump, so it has to sit genuinely between
        // the two on every lever that still differs between tiers. Speed is not
        // one of them any more — it is locked, see speedIsLockedAcrossTiers.
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
        // Measured on the spawn gap, the main lever left once speed was locked.
        double easy = Difficulty.EASY.getSpawnIntervalScale();
        double hard = Difficulty.HARD.getSpawnIntervalScale();
        double medium = Difficulty.MEDIUM.getSpawnIntervalScale();

        double position = (medium - easy) / (hard - easy);
        assertTrue(position > 0.35 && position < 0.65,
                "Medium sits at " + Math.round(position * 100)
                        + "% of the way from Easy to Hard, which puts the cliff back");
    }

    @Test
    @DisplayName("campaign speed does not climb with the level; only Endless ramps")
    void campaignSpeedIsFlat() {
        // Late levels were reported as unreactable even for a fast typist. The
        // rebalance answered that by holding campaign speed at its level-one
        // value for the whole run; Endless alone keeps escalating.
        for (Difficulty tier : List.of(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)) {
            assertEquals(DifficultyCurve.baseSpeed(1, tier),
                    DifficultyCurve.baseSpeed(tier.getFinalLevel(), tier), 0.0001,
                    tier + " should be as fast on its last level as its first");
        }
        assertTrue(DifficultyCurve.baseSpeed(40, Difficulty.ENDLESS)
                        > DifficultyCurve.baseSpeed(1, Difficulty.ENDLESS),
                "Endless must still get faster");
        assertEquals(0.20, DifficultyCurve.baseSpeed(1), 0.0001, "the bare curve opens slow");
        assertTrue(DifficultyCurve.baseSpeed(500) <= 0.75 + 0.0001, "and is capped");
    }

    @Test
    @DisplayName("Easy gives more room between spawns at every level, not just early on")
    void easyIsGentlerAtEveryLevel() {
        for (int level = 1; level <= 40; level++) {
            assertTrue(DifficultyCurve.spawnIntervalTicks(level, Difficulty.EASY)
                            >= DifficultyCurve.spawnIntervalTicks(level, Difficulty.MEDIUM),
                    "Easy crowded in faster than Medium at level " + level);
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
        // Climbing the ladder means signing up for a longer run.
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
    @DisplayName("Endless is playable survival mode")
    void endlessIsPlayable() {
        assertTrue(Difficulty.ENDLESS.isImplemented());
        assertEquals(Integer.MAX_VALUE, Difficulty.ENDLESS.getFinalLevel());
        assertTrue(Difficulty.ENDLESS.getEnemyCountScale() > 0,
                "the survival tuning must be configured, not left at zero");
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
    @DisplayName("Medium ends with Ream Eyso, Hard with Krong Reap")
    void heavierTiersFinales() {
        assertEquals(EnemyType.REAM_EYSO, Difficulty.MEDIUM.getFinalBossType());
        assertEquals(EnemyType.KRONG_REAP, Difficulty.HARD.getFinalBossType());
    }

    @Test
    @DisplayName("boss fights are short before level 30 and full length from 30 or at the finale")
    void longBossFightsWaitForLevelThirty() {
        int shortSentences = Difficulty.EASY.getBossParagraphCount()
                * Difficulty.EASY.getBossSentencesPerParagraph();
        for (Difficulty tier : Difficulty.values()) {
            for (int level : new int[] {10, 20}) {
                if (level == tier.getFinalBossLevel()) {
                    continue;
                }
                int sentences = tier.bossParagraphCountAt(level)
                        * tier.bossSentencesPerParagraphAt(level);
                assertTrue(sentences <= shortSentences,
                        tier + " level " + level + " asks for " + sentences
                                + " sentences; early bosses must stay short");
            }
            assertEquals(tier.getBossSentenceCount(),
                    tier.bossParagraphCountAt(30) * tier.bossSentencesPerParagraphAt(30),
                    tier + " should reach its full-length boss at level 30");
            if (tier.hasFinalBoss()) {
                int finale = tier.getFinalBossLevel();
                assertEquals(tier.getBossSentenceCount(),
                        tier.bossParagraphCountAt(finale) * tier.bossSentencesPerParagraphAt(finale),
                        tier + " finale should be full length");
            }
        }
    }

    @Test
    @DisplayName("the tougher bosses never come before level 30, except as a finale")
    void toughBossesComeLate() {
        Set<EnemyType> tough = EnumSet.of(EnemyType.CORRUPTED_APSARA,
                EnemyType.REAM_EYSO, EnemyType.KRONG_REAP);
        for (Difficulty tier : Difficulty.values()) {
            int last = tier == Difficulty.ENDLESS ? 200 : tier.getFinalLevel();
            for (int level = 10; level <= last; level += 10) {
                EnemyType boss = tier.getMilestoneBoss(level);
                if (boss != null && tough.contains(boss) && level != tier.getFinalBossLevel()) {
                    assertTrue(level >= Difficulty.FULL_LENGTH_BOSS_LEVEL,
                            tier + " sends " + boss + " on level " + level);
                }
            }
        }
    }

    @Test
    @DisplayName("a boss closes every tenth level and the final level, in the tier's own order")
    void gauntletEveryTenthLevel() {
        for (Difficulty tier : List.of(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)) {
            for (int level = 1; level <= tier.getFinalLevel(); level++) {
                boolean expected = level % 10 == 0 || level == tier.getFinalLevel();
                assertEquals(expected, tier.isBossLevel(level), tier + " level " + level);
                if (expected) {
                    assertNotNull(tier.getMilestoneBoss(level),
                            tier + " level " + level + " needs a boss");
                }
            }
            assertEquals(tier.getFinalBossType(), tier.getMilestoneBoss(tier.getFinalLevel()),
                    tier + " must end on its final boss");
        }
        assertEquals(EnemyType.YAKSHA_COMMANDER, Difficulty.MEDIUM.getMilestoneBoss(10),
                "Medium opens its gauntlet with the Yaksha Commander");
        assertEquals(EnemyType.YAKSHA_COMMANDER, Difficulty.HARD.getMilestoneBoss(10),
                "Hard opens its gauntlet with the Yaksha Commander");
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
