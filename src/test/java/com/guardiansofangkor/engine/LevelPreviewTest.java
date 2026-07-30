package com.guardiansofangkor.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("every mini-boss level is telegraphed, including past the table")
    void miniBossLevelsAreTelegraphed() {
        for (int level : new int[] {5, 10, 20, 25, 100}) {
            LevelPreview preview = LevelPreview.forLevel(level, Difficulty.MEDIUM);
            assertNotNull(preview, "level " + level + " is a mini-boss level");
            assertTrue(preview.hint().contains("Naga"),
                    "level " + level + " should mention the Naga, got: " + preview.hint());
        }
    }

    @Test
    @DisplayName("the final boss level gets its own hint, not the Naga one")
    void finalBossOverridesMiniBoss() {
        LevelPreview preview = LevelPreview.forLevel(15, Difficulty.MEDIUM);

        assertNotNull(preview);
        assertTrue(preview.hint().contains("Krong Reap"));
        assertFalse(preview.hint().contains("coils"),
                "level 15 on Medium is the finale, not a mini-boss visit");
    }

    @Test
    @DisplayName("the hint follows the tier's own boss")
    void hintFollowsTheTierBoss() {
        // Easy ends with the Naga at 10; Medium with Krong Reap at 15.
        // Announcing the wrong finale is worse than announcing nothing.
        LevelPreview easyFinale = LevelPreview.forLevel(10, Difficulty.EASY);
        assertNotNull(easyFinale);
        assertTrue(easyFinale.hint().contains("Naga"),
                "got: " + easyFinale.hint());

        LevelPreview mediumAtTen = LevelPreview.forLevel(10, Difficulty.MEDIUM);
        assertNotNull(mediumAtTen);
        assertTrue(mediumAtTen.hint().contains("coils"),
                "level 10 on Medium is only a mini-boss, got: " + mediumAtTen.hint());
    }

    @Test
    @DisplayName("quiet levels return null rather than filler")
    void quietLevelsReturnNull() {
        // A banner that always carries a third line trains players to ignore it.
        assertNull(LevelPreview.forLevel(8));
        assertNull(LevelPreview.forLevel(12));
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
