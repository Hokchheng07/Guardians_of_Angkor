package com.guardiansofangkor.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("MenuState — front-end navigation")
class MenuStateTest {

    /**
     * Presses the highlighted entry and runs the press delay out, returning the
     * outcome the caller would act on.
     */
    private static MenuState.Outcome press(MenuState state) {
        MenuState.Outcome immediate = state.activate();
        if (immediate != MenuState.Outcome.PENDING) {
            return immediate;
        }
        return settle(state);
    }

    /** Ticks until any pending press has fired. */
    private static MenuState.Outcome settle(MenuState state) {
        for (int i = 0; i < 120; i++) {
            state.tick();
            MenuState.Outcome ready = state.pollReady();
            if (ready != MenuState.Outcome.NONE) {
                return ready;
            }
        }
        return MenuState.Outcome.NONE;
    }

    private static MenuState atDifficulty() {
        MenuState state = new MenuState();
        state.select(MenuItem.NEW_GAME);
        press(state);
        return state;
    }

    // ---- main list ---------------------------------------------------------

    @Test
    @DisplayName("opens on the main list with New Game highlighted")
    void opensOnNewGame() {
        MenuState state = new MenuState();

        assertEquals(MenuState.Screen.MAIN, state.getScreen());
        assertEquals(MenuItem.NEW_GAME, state.getSelectedItem());
    }

    @Test
    @DisplayName("the highlight wraps at both ends")
    void highlightWraps() {
        MenuState state = new MenuState();

        state.moveUp();
        assertEquals(MenuItem.EXIT, state.getSelectedItem(), "up from the top wraps");

        state.moveDown();
        assertEquals(MenuItem.NEW_GAME, state.getSelectedItem(), "and back down again");
    }

    @Test
    @DisplayName("locked entries stay reachable rather than being skipped")
    void lockedEntriesAreReachable() {
        // Skipping them would make the highlight jump past items the player can
        // plainly see, which is more confusing than landing on one.
        MenuState state = new MenuState();
        state.select(MenuItem.OPTIONS);

        assertEquals(MenuItem.OPTIONS, state.getSelectedItem());
        assertFalse(state.isEnabled(MenuItem.OPTIONS));
    }

    @Test
    @DisplayName("activating a locked entry does nothing but explain itself")
    void lockedEntryExplainsItself() {
        MenuState state = new MenuState();
        state.select(MenuItem.BESTIARY);

        assertEquals(MenuState.Outcome.NONE, press(state));
        assertFalse(state.getLockedMessage().isEmpty());
        assertTrue(state.getLockedMessageAlpha() > 0);
    }

    @Test
    @DisplayName("New Game opens the difficulty picker")
    void newGameOpensDifficulty() {
        MenuState state = new MenuState();

        assertEquals(MenuState.Outcome.OPEN_DIFFICULTY, press(state));
        assertEquals(MenuState.Screen.DIFFICULTY, state.getScreen());
    }

    @Test
    @DisplayName("Exit reports an exit")
    void exitReportsExit() {
        MenuState state = new MenuState();
        state.select(MenuItem.EXIT);

        assertEquals(MenuState.Outcome.EXIT, press(state));
    }

    // ---- continue ----------------------------------------------------------

    @Test
    @DisplayName("Continue is disabled with no saved run")
    void continueDisabledWithoutSave() {
        MenuState state = new MenuState(false);
        state.select(MenuItem.CONTINUE);

        assertFalse(state.isEnabled(MenuItem.CONTINUE));
        assertEquals(MenuState.Outcome.NONE, press(state));
        assertTrue(state.getLockedMessage().toLowerCase().contains("no saved run"));
    }

    @Test
    @DisplayName("Continue resumes when a saved run exists")
    void continueEnabledWithSave() {
        MenuState state = new MenuState(true);
        state.select(MenuItem.CONTINUE);

        assertTrue(state.isEnabled(MenuItem.CONTINUE));
        assertEquals(MenuState.Outcome.RESUME_RUN, press(state));
    }

    // ---- difficulty screen -------------------------------------------------

    @Test
    @DisplayName("the difficulty picker opens on Easy")
    void difficultyDefaultsToEasy() {
        MenuState state = atDifficulty();

        assertEquals(Difficulty.EASY, state.getSelectedDifficulty());
        assertEquals(Difficulty.defaultChoice(), state.getSelectedDifficulty());
    }

    @Test
    @DisplayName("all four tiers are listed; only Endless is unbuilt")
    void allTiersAreListed() {
        assertEquals(4, Difficulty.values().length);
        assertTrue(Difficulty.EASY.isImplemented());
        assertTrue(Difficulty.MEDIUM.isImplemented());
        assertTrue(Difficulty.HARD.isImplemented());
        assertFalse(Difficulty.ENDLESS.isImplemented());
    }

    /** A picker open for a player who has beaten everything up to {@code tier}. */
    private static MenuState atDifficultyWithCleared(Difficulty... cleared) {
        MenuState state = atDifficulty();
        DifficultyProgress progress = DifficultyProgress.fresh();
        for (Difficulty tier : cleared) {
            progress = progress.withCleared(tier);
        }
        state.setProgress(progress);
        return state;
    }

    @Test
    @DisplayName("Medium starts a run once Easy has been cleared")
    void mediumStartsRun() {
        MenuState state = atDifficultyWithCleared(Difficulty.EASY);
        state.select(Difficulty.MEDIUM);

        assertEquals(MenuState.Outcome.START_RUN, press(state));
    }

    // ---- the unlock ladder -------------------------------------------------

    @Test
    @DisplayName("a new player can only start Easy")
    void onlyEasyIsOpenAtFirst() {
        MenuState state = atDifficulty();

        assertTrue(state.isEnabled(Difficulty.EASY));
        assertFalse(state.isEnabled(Difficulty.MEDIUM), "Medium has not been earned");
        assertFalse(state.isEnabled(Difficulty.HARD));
    }

    @Test
    @DisplayName("clearing a tier opens exactly the next one, not the whole ladder")
    void clearingOpensOneRung() {
        MenuState state = atDifficultyWithCleared(Difficulty.EASY);

        assertTrue(state.isEnabled(Difficulty.MEDIUM));
        assertFalse(state.isEnabled(Difficulty.HARD),
                "beating Easy must not hand the player Hard as well");

        state = atDifficultyWithCleared(Difficulty.EASY, Difficulty.MEDIUM);
        assertTrue(state.isEnabled(Difficulty.HARD));
    }

    @Test
    @DisplayName("a locked tier refuses to start and says what would unlock it")
    void lockedTierExplainsItself() {
        // "Not ready yet" would be a lie here — the tier is built and one run
        // away, and telling the player it does not exist is worse than telling
        // them nothing.
        MenuState state = atDifficulty();
        state.select(Difficulty.MEDIUM);

        assertEquals(MenuState.Outcome.NONE, press(state));
        assertTrue(state.getLockedMessage().contains("Easy"),
                "the message should name what to clear, got: "
                        + state.getLockedMessage());
        assertTrue(state.getLockedMessage().contains("Medium"),
                "and what it unlocks, got: " + state.getLockedMessage());
    }

    @Test
    @DisplayName("locked and unbuilt are different facts about a tier")
    void lockedIsNotTheSameAsUnbuilt() {
        // The menu badges one SOON and not the other. Medium is finished work
        // waiting on the player; Endless does not exist yet. Badging the first
        // would tell the player a lie, and one that would stop them trying.
        MenuState state = atDifficulty();

        assertTrue(Difficulty.MEDIUM.isImplemented(),
                "Medium is built — it is only locked");
        assertFalse(state.isEnabled(Difficulty.MEDIUM),
                "and it should still be unavailable until Easy is cleared");

        assertFalse(Difficulty.ENDLESS.isImplemented(),
                "Endless is the only tier that is genuinely not built");
    }

    @Test
    @DisplayName("an unbuilt tier says so even when its predecessor is cleared")
    void unbuiltBeatsLocked() {
        // Endless sits behind Hard on the ladder and is also not built. The
        // player should be told the honest reason, which is the second one.
        MenuState state = atDifficultyWithCleared(
                Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD);
        state.select(Difficulty.ENDLESS);

        assertEquals(MenuState.Outcome.NONE, press(state));
        assertTrue(state.getLockedMessage().contains("not ready"),
                "got: " + state.getLockedMessage());
    }

    @Test
    @DisplayName("Easy starts a run")
    void easyStartsRun() {
        MenuState state = atDifficulty();

        assertEquals(MenuState.Outcome.START_RUN, press(state));
    }

    @Test
    @DisplayName("the unbuilt tiers refuse to start and say why")
    void lockedTiersRefuseToStart() {
        for (Difficulty difficulty : Difficulty.values()) {
            if (difficulty.isImplemented()) {
                continue;
            }
            MenuState state = atDifficulty();
            state.select(difficulty);

            assertEquals(MenuState.Outcome.NONE, press(state),
                    difficulty + " must not start a run");
            assertTrue(state.getLockedMessage().contains(difficulty.getDisplayName()),
                    "the message should name the tier, got: " + state.getLockedMessage());
        }
    }

    @Test
    @DisplayName("the highlight wraps on the difficulty list too")
    void difficultyHighlightWraps() {
        MenuState state = atDifficulty();

        state.moveUp();
        assertEquals(Difficulty.ENDLESS, state.getSelectedDifficulty());

        state.moveDown();
        assertEquals(Difficulty.EASY, state.getSelectedDifficulty());
    }

    @Test
    @DisplayName("back returns to the main list without exiting")
    void backReturnsToMain() {
        MenuState state = atDifficulty();

        assertEquals(MenuState.Outcome.PENDING, state.back());
        assertEquals(MenuState.Outcome.BACK, settle(state));
        assertEquals(MenuState.Screen.MAIN, state.getScreen());
    }

    @Test
    @DisplayName("back from the main list exits")
    void backFromMainExits() {
        MenuState state = new MenuState();

        assertEquals(MenuState.Outcome.EXIT, state.back());
    }

    @Test
    @DisplayName("reopening the picker resets it to Easy")
    void reopeningResetsToEasy() {
        MenuState state = atDifficulty();
        state.select(Difficulty.HARD);
        state.back();
        settle(state);

        state.select(MenuItem.NEW_GAME);
        press(state);

        assertEquals(Difficulty.EASY, state.getSelectedDifficulty(),
                "a previous browse must not become the new default");
    }

    // ---- press delay -------------------------------------------------------

    @Test
    @DisplayName("a press does not act immediately")
    void pressIsNotInstant() {
        MenuState state = new MenuState();

        assertEquals(MenuState.Outcome.PENDING, state.activate());
        assertEquals(MenuState.Screen.MAIN, state.getScreen(),
                "the screen must not change until the button finishes depressing");
        assertEquals(MenuState.Outcome.NONE, state.pollReady(),
                "nothing is ready yet");
        assertTrue(state.isPressed());
    }

    @Test
    @DisplayName("the outcome arrives once the press completes")
    void pressResolvesAfterDelay() {
        MenuState state = new MenuState();
        state.activate();

        assertEquals(MenuState.Outcome.OPEN_DIFFICULTY, settle(state));
        assertEquals(MenuState.Screen.DIFFICULTY, state.getScreen());
        assertFalse(state.isPressed());
    }

    @Test
    @DisplayName("the outcome is collected exactly once")
    void outcomeIsCollectedOnce() {
        MenuState state = new MenuState();
        state.activate();
        settle(state);

        assertEquals(MenuState.Outcome.NONE, state.pollReady(),
                "polling again must not re-fire the action");
    }

    @Test
    @DisplayName("mashing the key does not queue a second action")
    void doublePressIsIgnored() {
        MenuState state = new MenuState();

        assertEquals(MenuState.Outcome.PENDING, state.activate());
        assertEquals(MenuState.Outcome.NONE, state.activate(),
                "a second press while one is running is dropped");

        assertEquals(MenuState.Outcome.OPEN_DIFFICULTY, settle(state));
        assertEquals(MenuState.Outcome.NONE, state.pollReady(),
                "and only one outcome ever arrives");
    }

    @Test
    @DisplayName("press progress runs from full to zero")
    void pressProgressDecays() {
        MenuState state = new MenuState();
        state.activate();

        double first = state.getPressProgress();
        assertEquals(1.0, first, 0.0001);

        state.tick();
        assertTrue(state.getPressProgress() < first);
    }

    @Test
    @DisplayName("a locked entry starts no press at all")
    void lockedEntryStartsNoPress() {
        MenuState state = new MenuState();
        state.select(MenuItem.OPTIONS);

        assertEquals(MenuState.Outcome.NONE, state.activate());
        assertFalse(state.isPressed(), "there is nothing to animate");
    }

    @Test
    @DisplayName("reset drops a press in flight")
    void resetDropsPendingPress() {
        MenuState state = new MenuState();
        state.activate();
        assertTrue(state.isPressed());

        state.reset();

        assertFalse(state.isPressed());
        assertEquals(MenuState.Outcome.NONE, settle(state),
                "the dropped action must not fire later");
    }

    // ---- locked message lifecycle ------------------------------------------

    @Test
    @DisplayName("the locked message fades on its own")
    void lockedMessageFades() {
        MenuState state = new MenuState();
        state.select(MenuItem.OPTIONS);
        state.activate();
        assertTrue(state.getLockedMessageAlpha() > 0);

        for (int i = 0; i < 200; i++) {
            state.tick();
        }

        assertEquals(0, state.getLockedMessageAlpha(), 0.0001);
        assertTrue(state.getLockedMessage().isEmpty());
    }

    @Test
    @DisplayName("navigating away clears a stale locked message")
    void backClearsLockedMessage() {
        MenuState state = new MenuState();
        state.select(MenuItem.OPTIONS);
        state.activate();

        state.back();

        assertTrue(state.getLockedMessage().isEmpty());
    }

    @Test
    @DisplayName("reset returns to a freshly opened menu")
    void resetReturnsToTop() {
        MenuState state = atDifficulty();
        state.select(Difficulty.ENDLESS);

        state.reset();

        assertEquals(MenuState.Screen.MAIN, state.getScreen());
        assertEquals(MenuItem.NEW_GAME, state.getSelectedItem());
        assertEquals(Difficulty.EASY, state.getSelectedDifficulty());
    }

    @Test
    @DisplayName("every tier carries a tagline for the picker")
    void everyTierHasATagline() {
        for (Difficulty difficulty : Difficulty.values()) {
            assertNotEquals("", difficulty.getTagline().trim(),
                    difficulty + " needs a description");
            assertTrue(difficulty.getTagline().length() <= 48,
                    difficulty + " tagline is too long for the panel");
        }
    }
}
