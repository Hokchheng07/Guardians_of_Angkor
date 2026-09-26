package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.EnemyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LevelPreview — next-level hints")
class LevelPreviewTest {

    @Test
    @DisplayName("announces a type the level it unlocks")
    void announcesUnlocks() {
        // WaveWeights unlocks Ahp at 2, Yeak at 3, Pret at 6.
        assertNotNull(LevelPreview.forLevel(2));
        assertNotNull(LevelPreview.forLevel(3));
        assertNotNull(LevelPreview.forLevel(6));
    }

    @Test
    @DisplayName("every boss level names its boss, including Endless past any table")
    void bossLevelsAreTelegraphed() {
        for (int level = 10; level <= 40; level += 10) {
            LevelPreview preview = LevelPreview.forLevel(level, Difficulty.MEDIUM);
            String boss = Difficulty.MEDIUM.getMilestoneBoss(level).getDisplayName();
            assertNotNull(preview, "Medium level " + level + " is a boss level");
            assertTrue(preview.hint().contains(boss),
                    "level " + level + " should name " + boss + ", got: " + preview.hint());
        }
        for (int level : new int[] {60, 100}) {
            LevelPreview preview = LevelPreview.forLevel(level, Difficulty.ENDLESS);
            String boss = Difficulty.ENDLESS.getMilestoneBoss(level).getDisplayName();
            assertNotNull(preview, "Endless level " + level);
            assertTrue(preview.hint().contains(boss), "got: " + preview.hint());
        }
        assertNull(Difficulty.MEDIUM.getMilestoneBoss(5), "the fifth-level Naga is gone");
    }

    @Test
    @DisplayName("the finale names the tier's final boss")
    void finaleNamesTheFinalBoss() {
        LevelPreview preview = LevelPreview.forLevel(
                Difficulty.MEDIUM.getFinalLevel(), Difficulty.MEDIUM);

        assertNotNull(preview);
        assertTrue(preview.hint().contains("Ream Eyso"), "got: " + preview.hint());
    }

    @Test
    @DisplayName("the hint follows the tier's own boss")
    void hintFollowsTheTierBoss() {
        // The tiers end on different levels and with different monsters.
        // Announcing the wrong boss is worse than announcing nothing, and
        // announcing it on the wrong level is worse still.
        LevelPreview easyFinale = LevelPreview.forLevel(
                Difficulty.EASY.getFinalBossLevel(), Difficulty.EASY);
        assertNotNull(easyFinale);
        assertTrue(easyFinale.hint().contains("Naga"),
                "Easy's finale is the Naga, got: " + easyFinale.hint());

        LevelPreview mediumFinale = LevelPreview.forLevel(
                Difficulty.MEDIUM.getFinalBossLevel(), Difficulty.MEDIUM);
        assertNotNull(mediumFinale);
        assertTrue(mediumFinale.hint().contains("Ream Eyso"),
                "Medium's finale is Ream Eyso, got: " + mediumFinale.hint());

        LevelPreview hardFinale = LevelPreview.forLevel(
                Difficulty.HARD.getFinalBossLevel(), Difficulty.HARD);
        assertNotNull(hardFinale);
        assertTrue(hardFinale.hint().contains("Krong Reap"),
                "Hard's finale is Krong Reap, got: " + hardFinale.hint());

        LevelPreview mediumAtTen = LevelPreview.forLevel(10, Difficulty.MEDIUM);
        assertNotNull(mediumAtTen);
        assertTrue(mediumAtTen.hint().contains("Yaksha Commander"),
                "level 10 opens every gauntlet, got: " + mediumAtTen.hint());
    }

    /** The level a type first appears on for a tier, from the same table the game uses. */
    private static int arrivalLevelOf(EnemyType type, Difficulty tier) {
        for (int level = 1; level <= 30; level++) {
            if (WaveWeights.newlyUnlockedAt(level, tier).contains(type)) {
                return level;
            }
        }
        throw new AssertionError(type + " never arrives on " + tier);
    }

    @Test
    @DisplayName("arrivals follow the tier, not a fixed table")
    void arrivalsFollowTheTier() {
        // Gentler tiers hold the roster back, so a hint tied to a hardcoded
        // level would promise Yeak before he actually turns up. The levels are
        // read from the unlock table rather than written down here, or this test
        // would need editing every time the tiers are retuned — which is the
        // very failure it exists to catch.
        int mediumYeak = arrivalLevelOf(EnemyType.YEAK, Difficulty.MEDIUM);
        LevelPreview onTime = LevelPreview.forLevel(mediumYeak, Difficulty.MEDIUM);
        assertNotNull(onTime);
        assertTrue(onTime.hint().contains("Yeak"), "got: " + onTime.hint());

        int easyYeak = arrivalLevelOf(EnemyType.YEAK, Difficulty.EASY);
        assertTrue(easyYeak > mediumYeak,
                "Easy should meet Yeak later than Medium does, got " + easyYeak
                        + " against " + mediumYeak);

        LevelPreview tooEarly = LevelPreview.forLevel(mediumYeak, Difficulty.EASY);
        if (tooEarly != null) {
            assertFalse(tooEarly.hint().contains("Yeak"),
                    "Yeak has not unlocked yet on Easy at level " + mediumYeak);
        }
    }

    @Test
    @DisplayName("a level that is both a boss and an arrival says both")
    void collisionsMentionBoth() {
        // Which levels coincide moves with the tier — on Medium, the Punisher
        // arrives on level 10, which is also the first boss. Dropping either
        // fact silently would misinform the player, so the banner says both.
        boolean found = false;

        for (Difficulty tier : List.of(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)) {
            for (int level = 10; level <= tier.getFinalLevel(); level += 10) {
                List<EnemyType> arrivals = WaveWeights.newlyUnlockedAt(level, tier);
                if (arrivals.isEmpty()) {
                    continue;
                }

                LevelPreview preview = LevelPreview.forLevel(level, tier);
                assertNotNull(preview, tier + " level " + level);
                String boss = tier.getMilestoneBoss(level).getDisplayName();
                assertTrue(preview.hint().contains(boss),
                        tier + " level " + level + " dropped the boss, got: " + preview.hint());

                boolean named = false;
                for (EnemyType type : arrivals) {
                    named |= preview.hint().contains(type.getDisplayName());
                }
                assertTrue(named,
                        tier + " level " + level + " dropped the arrival " + arrivals
                                + ", got: " + preview.hint());
                found = true;
            }
        }

        assertTrue(found,
                "no tier has a level that is both a boss level and an arrival, "
                        + "so this rule is no longer being exercised at all");
    }

    @Test
    @DisplayName("quiet levels return null rather than filler")
    void quietLevelsReturnNull() {
        // A banner that always carries a third line trains players to ignore it.
        assertNull(LevelPreview.forLevel(8));
        assertNull(LevelPreview.forLevel(14));
    }

    @Test
    @DisplayName("guards against level zero and negatives")
    void guardsNonPositiveLevels() {
        assertNull(LevelPreview.forLevel(0));
        assertNull(LevelPreview.forLevel(-3));
    }

    @Test
    @DisplayName("hints are short enough to stay one line")
    void hintsStayShort() {
        for (int level = 1; level <= 30; level++) {
            LevelPreview preview = LevelPreview.forLevel(level);
            if (preview != null) {
                assertTrue(preview.hint().length() <= 40,
                        "level " + level + " hint is too long for the banner: "
                                + preview.hint());
            }
        }
    }

    @Test
    @DisplayName("record exposes its hint")
    void recordExposesHint() {
        assertEquals("test", new LevelPreview("test").hint());
    }
}
