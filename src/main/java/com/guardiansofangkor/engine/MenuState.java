package com.guardiansofangkor.engine;

/**
 * Navigation state for the front end: which screen is showing, what is
 * highlighted, and what activating it should do.
 *
 * <p>Pure logic with no Swing in it, so the whole menu flow is unit-testable
 * without opening a window — the same split the game itself uses.
 *
 * <p>Locked entries stay reachable rather than being skipped. Skipping them
 * would make the highlight jump unpredictably past items the player can plainly
 * see; landing on one and being told it is not ready yet is less confusing than
 * a cursor that refuses to go where it is sent.
 */
public class MenuState {

    /** Which screen the front end is showing. */
    public enum Screen {
        /** Title and the main entry list. */
        MAIN,

        /** Difficulty picker, reached from New Game. */
        DIFFICULTY
    }

    /** What the caller should do in response to an activation. */
    public enum Outcome {
        /** Nothing happened, or the highlighted entry is not ready. */
        NONE,

        /** Move to the difficulty picker. */
        OPEN_DIFFICULTY,

        /** Begin a fresh run on {@link MenuState#getSelectedDifficulty()}. */
        START_RUN,

        /** Resume the saved run. */
        RESUME_RUN,

        /** Return to the main list. */
        BACK,

        /** Close the game. */
        EXIT
    }

    /** How long the "not ready" nudge stays on screen, in ticks. */
    private static final int LOCKED_FLASH_TICKS = 90;

    private Screen screen = Screen.MAIN;
    private int mainIndex;
    private int difficultyIndex = Difficulty.defaultChoice().ordinal();

    /** Whether a resumable run exists, which decides if Continue is usable. */
    private boolean continueAvailable;

    /** Counts down while a locked entry's explanation is showing. */
    private int lockedFlashTicks;

    private String lockedMessage = "";

    public MenuState() {
        this(false);
    }

    public MenuState(boolean continueAvailable) {
        this.continueAvailable = continueAvailable;
    }

    // ---- navigation --------------------------------------------------------

    public void moveUp() {
        int count = itemCount();
        setIndex((currentIndex() - 1 + count) % count);
    }

    public void moveDown() {
        setIndex((currentIndex() + 1) % itemCount());
    }

    /**
     * Activates the highlighted entry.
     *
     * @return what the caller should do about it
     */
    public Outcome activate() {
        if (screen == Screen.MAIN) {
            return activateMainItem();
        }
        return activateDifficulty();
    }

    private Outcome activateMainItem() {
        MenuItem item = getSelectedItem();
        if (!isEnabled(item)) {
            flashLocked(lockedReasonFor(item));
            return Outcome.NONE;
        }
        return switch (item) {
            case NEW_GAME -> {
                screen = Screen.DIFFICULTY;
                difficultyIndex = Difficulty.defaultChoice().ordinal();
                clearLockedFlash();
                yield Outcome.OPEN_DIFFICULTY;
            }
            case CONTINUE -> Outcome.RESUME_RUN;
            case EXIT -> Outcome.EXIT;
            default -> Outcome.NONE;
        };
    }

    private Outcome activateDifficulty() {
        Difficulty difficulty = getSelectedDifficulty();
        if (!difficulty.isImplemented()) {
            flashLocked(difficulty.getDisplayName() + " is not ready yet.");
            return Outcome.NONE;
        }
        clearLockedFlash();
        return Outcome.START_RUN;
    }

    /**
     * Backs out of the current screen.
     *
     * @return {@link Outcome#BACK} on the difficulty screen, or
     *         {@link Outcome#EXIT} when already at the top
     */
    public Outcome back() {
        clearLockedFlash();
        if (screen == Screen.DIFFICULTY) {
            screen = Screen.MAIN;
            return Outcome.BACK;
        }
        return Outcome.EXIT;
    }

    /** Jumps the highlight straight to an entry, e.g. from a mouse hover. */
    public void select(MenuItem item) {
        if (screen == Screen.MAIN && item != null) {
            mainIndex = item.ordinal();
        }
    }

    public void select(Difficulty difficulty) {
        if (screen == Screen.DIFFICULTY && difficulty != null) {
            difficultyIndex = difficulty.ordinal();
        }
    }

    /** Resets to a freshly opened main menu. */
    public void reset() {
        screen = Screen.MAIN;
        mainIndex = 0;
        difficultyIndex = Difficulty.defaultChoice().ordinal();
        clearLockedFlash();
    }

    // ---- availability ------------------------------------------------------

    /**
     * True when this entry can actually be activated. Distinct from
     * {@link MenuItem#isImplemented()}: Continue is built, but is only usable
     * when there is something to continue.
     */
    public boolean isEnabled(MenuItem item) {
        if (item == null || !item.isImplemented()) {
            return false;
        }
        return item != MenuItem.CONTINUE || continueAvailable;
    }

    public boolean isEnabled(Difficulty difficulty) {
        return difficulty != null && difficulty.isImplemented();
    }

    private String lockedReasonFor(MenuItem item) {
        if (item == MenuItem.CONTINUE) {
            return "No saved run to continue.";
        }
        return item.getLabel() + " is not ready yet.";
    }

    // ---- locked-entry feedback ---------------------------------------------

    private void flashLocked(String message) {
        lockedMessage = message;
        lockedFlashTicks = LOCKED_FLASH_TICKS;
    }

    private void clearLockedFlash() {
        lockedFlashTicks = 0;
        lockedMessage = "";
    }

    /** Ticked by the menu's animation timer so the nudge fades on its own. */
    public void tick() {
        if (lockedFlashTicks > 0) {
            lockedFlashTicks--;
            if (lockedFlashTicks == 0) {
                lockedMessage = "";
            }
        }
    }

    /** The "not ready" line to show, or empty when there is none. */
    public String getLockedMessage() {
        return lockedMessage;
    }

    /** Opacity for the locked message, 0 to 1, so it can fade out. */
    public double getLockedMessageAlpha() {
        if (lockedFlashTicks <= 0) {
            return 0;
        }
        // Hold at full strength, then fade over the final third.
        double remaining = lockedFlashTicks / (double) LOCKED_FLASH_TICKS;
        return Math.min(1.0, remaining * 3);
    }

    // ---- accessors ---------------------------------------------------------

    public Screen getScreen() {
        return screen;
    }

    public MenuItem getSelectedItem() {
        return MenuItem.values()[mainIndex];
    }

    public Difficulty getSelectedDifficulty() {
        return Difficulty.values()[difficultyIndex];
    }

    public int getSelectedIndex() {
        return currentIndex();
    }

    public boolean isContinueAvailable() {
        return continueAvailable;
    }

    public void setContinueAvailable(boolean continueAvailable) {
        this.continueAvailable = continueAvailable;
    }

    private int itemCount() {
        return screen == Screen.MAIN
                ? MenuItem.values().length
                : Difficulty.values().length;
    }

    private int currentIndex() {
        return screen == Screen.MAIN ? mainIndex : difficultyIndex;
    }

    private void setIndex(int index) {
        if (screen == Screen.MAIN) {
            mainIndex = index;
        } else {
            difficultyIndex = index;
        }
    }
}
