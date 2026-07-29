package com.guardiansofangkor.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("MenuState — front-end navigation")
class MenuStateTest {

    private static MenuState atDifficulty() {
        MenuState state = new MenuState();
        state.select(MenuItem.NEW_GAME);
        state.activate();
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

        assertEquals(MenuState.Outcome.NONE, state.activate());
        assertFalse(state.getLockedMessage().isEmpty());
        assertTrue(state.getLockedMessageAlpha() > 0);
    }

    @Test
    @DisplayName("New Game opens the difficulty picker")
    void newGameOpensDifficulty() {
        MenuState state = new MenuState();

        assertEquals(MenuState.Outcome.OPEN_DIFFICULTY, state.activate());
        assertEquals(MenuState.Screen.DIFFICULTY, state.getScreen());
    }

    @Test
    @DisplayName("Exit reports an exit")
    void exitReportsExit() {
        MenuState state = new MenuState();
        state.select(MenuItem.EXIT);

        assertEquals(MenuState.Outcome.EXIT, state.activate());
    }

    // ---- continue ----------------------------------------------------------

    @Test
    @DisplayName("Continue is disabled with no saved run")
    void continueDisabledWithoutSave() {
        MenuState state = new MenuState(false);
        state.select(MenuItem.CONTINUE);

        assertFalse(state.isEnabled(MenuItem.CONTINUE));
        assertEquals(MenuState.Outcome.NONE, state.activate());
        assertTrue(state.getLockedMessage().toLowerCase().contains("no saved run"));
    }

    @Test
    @DisplayName("Continue resumes when a saved run exists")
    void continueEnabledWithSave() {
        MenuState state = new MenuState(true);
        state.select(MenuItem.CONTINUE);

        assertTrue(state.isEnabled(MenuItem.CONTINUE));
        assertEquals(MenuState.Outcome.RESUME_RUN, state.activate());
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
    @DisplayName("all four tiers are listed, even the unbuilt ones")
    void allTiersAreListed() {
        assertEquals(4, Difficulty.values().length);
        assertTrue(Difficulty.EASY.isImplemented());
        assertFalse(Difficulty.MEDIUM.isImplemented());
        assertFalse(Difficulty.HARD.isImplemented());
        assertFalse(Difficulty.ENDLESS.isImplemented());
    }

    @Test
    @DisplayName("Easy starts a run")
    void easyStartsRun() {
        MenuState state = atDifficulty();

        assertEquals(MenuState.Outcome.START_RUN, state.activate());
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

            assertEquals(MenuState.Outcome.NONE, state.activate(),
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

        assertEquals(MenuState.Outcome.BACK, state.back());
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

        state.select(MenuItem.NEW_GAME);
        state.activate();

        assertEquals(Difficulty.EASY, state.getSelectedDifficulty(),
                "a previous browse must not become the new default");
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
