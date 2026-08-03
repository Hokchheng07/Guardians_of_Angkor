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

        /**
         * The press registered and is playing out. The real outcome arrives from
         * {@link MenuState#pollReady()} once the button has finished depressing.
         */
        PENDING,

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

    /**
     * How long a button stays depressed before its action fires.
     *
     * <p>Roughly a fifth of a second. Long enough that the press is visible and
     * the menu feels like it has weight, short enough that it never reads as lag.
     */
    private static final int PRESS_TICKS = 13;

    private Screen screen = Screen.MAIN;
    private int mainIndex;
    private int difficultyIndex = Difficulty.defaultChoice().ordinal();

    /** Whether a resumable run exists, which decides if Continue is usable. */
    private boolean continueAvailable;

    /**
     * Which tiers have been earned. Starts at nothing cleared, so a build with
     * no save wired in still behaves — only Easy opens.
     */
    private DifficultyProgress progress = DifficultyProgress.fresh();

    /** Counts down while a locked entry's explanation is showing. */
    private int lockedFlashTicks;

    private String lockedMessage = "";

    /** The outcome waiting for its button press to finish. */
    private Outcome pendingOutcome = Outcome.NONE;

    /** Counts down while a button is depressed. */
    private int pressTicks;

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
     * Presses the highlighted entry.
     *
     * <p>Does not act immediately. A valid press starts a short depress
     * animation and returns {@link Outcome#PENDING}; the caller then watches
     * {@link #pollReady()} for the real outcome. Instant response makes the menu
     * feel like a list of hyperlinks rather than carved stone.
     *
     * @return {@link Outcome#PENDING} when the press took, {@link Outcome#NONE}
     *         when the entry is locked or a press is already running
     */
    public Outcome activate() {
        if (pressTicks > 0 || pendingOutcome != Outcome.NONE) {
            // Already committed — ignore a mashed second press.
            return Outcome.NONE;
        }

        Outcome resolved = screen == Screen.MAIN
                ? resolveMainItem()
                : resolveDifficulty();

        if (resolved == Outcome.NONE) {
            return Outcome.NONE;
        }

        clearLockedFlash();
        pendingOutcome = resolved;
        pressTicks = PRESS_TICKS;
        return Outcome.PENDING;
    }

    /**
     * Collects the outcome of a completed press, exactly once.
     *
     * <p>Screen changes are applied here rather than in {@link #activate()}, so
     * the button the player pressed is still the one on screen while it is
     * depressing.
     *
     * @return the outcome, or {@link Outcome#NONE} if none is ready
     */
    public Outcome pollReady() {
        if (pendingOutcome == Outcome.NONE || pressTicks > 0) {
            return Outcome.NONE;
        }
        Outcome outcome = pendingOutcome;
        pendingOutcome = Outcome.NONE;

        switch (outcome) {
            case OPEN_DIFFICULTY -> {
                screen = Screen.DIFFICULTY;
                difficultyIndex = Difficulty.defaultChoice().ordinal();
            }
            case BACK -> screen = Screen.MAIN;
            default -> {
                // START_RUN, RESUME_RUN and EXIT are the caller's business.
            }
        }
        return outcome;
    }

    /** True while a button is depressed, for the renderer. */
    public boolean isPressed() {
        return pressTicks > 0;
    }

    /** Press progress, 1 at the moment of the press down to 0. */
    public double getPressProgress() {
        return pressTicks / (double) PRESS_TICKS;
    }

    private Outcome resolveMainItem() {
        MenuItem item = getSelectedItem();
        if (!isEnabled(item)) {
            flashLocked(lockedReasonFor(item));
            return Outcome.NONE;
        }
        return switch (item) {
            case NEW_GAME -> Outcome.OPEN_DIFFICULTY;
            case CONTINUE -> Outcome.RESUME_RUN;
            case EXIT -> Outcome.EXIT;
            default -> Outcome.NONE;
        };
    }

    /**
     * Decides what pressing a difficulty does.
     *
     * <p>There is now exactly one way a tier can refuse: it has not been built.
     * The second reason — "clear Medium first" — is gone, because every built
     * tier is open from the start. {@link DifficultyProgress} still records what
     * has been cleared; it simply no longer decides what may be played.
     */
    private Outcome resolveDifficulty() {
        Difficulty difficulty = getSelectedDifficulty();
        if (!difficulty.isImplemented()) {
            flashLocked(difficulty.getDisplayName() + " is not ready yet.");
            return Outcome.NONE;
        }
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
        if (pressTicks > 0 || pendingOutcome != Outcome.NONE) {
            // A press is already committed; do not race it.
            return Outcome.NONE;
        }
        if (screen == Screen.DIFFICULTY) {
            pendingOutcome = Outcome.BACK;
            pressTicks = PRESS_TICKS;
            return Outcome.PENDING;
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
        pendingOutcome = Outcome.NONE;
        pressTicks = 0;
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

    /**
     * True when a run can actually be started on this tier — it is both built
     * and earned. This is what greys the button out.
     */
    public boolean isEnabled(Difficulty difficulty) {
        // Built is the only requirement. Tiers used to also have to be EARNED —
        // clear Easy to open Medium, and so on — and that gate is gone: a
        // player who wants Hard on their first run can have it, and anyone
        // marking or demonstrating this does not have to play through two
        // tiers to see the third.
        //
        // {@link DifficultyProgress} is deliberately still tracked. It is no
        // longer a lock, but it is still the record of what has actually been
        // beaten, which the end-of-run card and the save file both want.
        return difficulty != null && difficulty.isImplemented();
    }

    /** Which tiers the player has earned. */
    public DifficultyProgress getProgress() {
        return progress;
    }

    /** Updates the unlock state, e.g. after loading a save or winning a run. */
    public void setProgress(DifficultyProgress progress) {
        this.progress = progress == null ? DifficultyProgress.fresh() : progress;
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
        if (pressTicks > 0) {
            pressTicks--;
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
