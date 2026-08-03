package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.ApproachPath;
import com.guardiansofangkor.entities.Enemy;
import com.guardiansofangkor.entities.EnemyType;
import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.util.GameConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ComboTracker — the run of perfectly typed words")
class ComboTrackerTest {

    @Test
    @DisplayName("a clean word adds one, and nothing else does")
    void cleanWordsCount() {
        ComboTracker combo = new ComboTracker();

        assertEquals(0, combo.getCount());
        combo.noteCleanWord();
        combo.noteCleanWord();
        assertEquals(2, combo.getCount());
    }

    @Test
    @DisplayName("one wrong letter ends the whole run")
    void oneMistakeEndsIt() {
        // Not a decay and not a partial loss. The combo is a claim about
        // accuracy, and one that survived a mistake would be a claim about
        // mostly-accuracy, which is not worth a number on screen.
        ComboTracker combo = new ComboTracker();
        for (int i = 0; i < 12; i++) {
            combo.noteCleanWord();
        }

        combo.breakStreak();

        assertEquals(0, combo.getCount());
        assertEquals(1.0, combo.getMultiplier(), 0.0001);
    }

    @Test
    @DisplayName("the best run survives being broken")
    void bestIsRemembered() {
        ComboTracker combo = new ComboTracker();
        for (int i = 0; i < 7; i++) {
            combo.noteCleanWord();
        }
        combo.breakStreak();
        combo.noteCleanWord();

        assertEquals(7, combo.getBest(), "the end-of-run summary reports the best, not the last");
        assertEquals(1, combo.getCount());
    }

    @Test
    @DisplayName("the multiplier climbs with the run and then stops")
    void multiplierClimbsThenCaps() {
        // Uncapped, a long enough run makes every earlier level irrelevant to
        // the total, which measures endurance rather than play.
        ComboTracker combo = new ComboTracker();
        assertEquals(1.0, combo.getMultiplier(), 0.0001);

        for (int i = 0; i < ComboTracker.CAP; i++) {
            combo.noteCleanWord();
        }
        double atCap = combo.getMultiplier();
        assertEquals(1.0 + ComboTracker.CAP * ComboTracker.STEP, atCap, 0.0001);

        for (int i = 0; i < 200; i++) {
            combo.noteCleanWord();
        }
        assertEquals(atCap, combo.getMultiplier(), 0.0001,
                "the multiplier kept climbing past its ceiling");
    }

    @Test
    @DisplayName("the multiplier never falls below neutral")
    void multiplierNeverPenalises() {
        // A combo is a bonus for playing well, never a tax for playing badly.
        ComboTracker combo = new ComboTracker();
        combo.breakStreak();

        assertTrue(combo.getMultiplier() >= 1.0);
    }

    @Test
    @DisplayName("a short run is not put on screen")
    void shortRunsStayHidden() {
        // Below the threshold the counter would spend a level flickering
        // between one and zero, which reads as a broken HUD element.
        ComboTracker combo = new ComboTracker();
        combo.noteCleanWord();

        assertFalse(combo.isWorthShowing());

        for (int i = 0; i < ComboTracker.DISPLAY_THRESHOLD; i++) {
            combo.noteCleanWord();
        }
        assertTrue(combo.isWorthShowing());
    }

    @Test
    @DisplayName("a reset clears the best too")
    void resetClearsEverything() {
        ComboTracker combo = new ComboTracker();
        combo.noteCleanWord();
        combo.reset();

        assertEquals(0, combo.getCount());
        assertEquals(0, combo.getBest());
    }

    // ---- inside a run ------------------------------------------------------

    private static GameState playing() {
        GameState state = new GameState(Language.ENGLISH);
        state.skipIntro();
        return state;
    }

    /** An enemy parked far from the temple so it cannot breach mid-test. */
    private static Enemy safeEnemy(GameState state, String word) {
        Enemy enemy = new Enemy(EnemyType.BEISACH, ApproachPath.GROUND_FLANK,
                word, GameConfig.FLANK_RUN_MAX, 1, 0.0);
        state.addEnemy(enemy);
        return enemy;
    }

    @Test
    @DisplayName("killing an enemy cleanly builds the combo")
    void killsBuildTheCombo() {
        GameState state = playing();
        safeEnemy(state, "ash");

        state.handleInput("a");
        state.handleInput("as");
        state.handleInput("ash");

        assertEquals(1, state.getCombo().getCount());
    }

    @Test
    @DisplayName("a single wrong letter breaks the combo mid-run")
    void aTypoBreaksTheCombo() {
        GameState state = playing();
        safeEnemy(state, "ash");
        state.handleInput("ash");
        assertEquals(1, state.getCombo().getCount());

        safeEnemy(state, "moon");
        state.handleInput("m");
        state.handleInput("mx");

        assertEquals(0, state.getCombo().getCount(),
                "one wrong letter should have ended it");
        assertEquals(1, state.getCombo().getBest());
    }

    @Test
    @DisplayName("a combo makes the same kill worth more")
    void theComboPaysOut() {
        GameState fresh = playing();
        safeEnemy(fresh, "ash");
        fresh.handleInput("ash");
        int firstKill = fresh.getScore();

        GameState hot = playing();
        for (int i = 0; i < ComboTracker.CAP; i++) {
            hot.getCombo().noteCleanWord();
        }
        int before = hot.getScore();
        safeEnemy(hot, "ash");
        hot.handleInput("ash");
        int withCombo = hot.getScore() - before;

        assertTrue(withCombo > firstKill,
                "the same word scored " + withCombo + " on a full combo against "
                        + firstKill + " cold, which is no reward at all");
    }

    @Test
    @DisplayName("a restart wipes the combo")
    void restartClearsTheCombo() {
        GameState state = playing();
        safeEnemy(state, "ash");
        state.handleInput("ash");

        state.restart();

        assertEquals(0, state.getCombo().getCount());
        assertEquals(0, state.getCombo().getBest());
    }
}
