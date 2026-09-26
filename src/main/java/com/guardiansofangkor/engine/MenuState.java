package com.guardiansofangkor.engine;

import com.guardiansofangkor.audio.AudioSettings;
import com.guardiansofangkor.entities.Hero;
import com.guardiansofangkor.i18n.Language;

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

        /** Hero picker, reached from New Game and from Sandbox. */
        HERO,

        /** Difficulty picker, reached from the hero picker on the way to a run. */
        DIFFICULTY,

        /** Language and volume settings, reached from Options. */
        OPTIONS
    }

    /**
     * Rows on the Options screen, top to bottom.
     *
     * <p>Up and Down move between rows; Left and Right change the highlighted
     * one. Settings take effect the moment they change rather than on a
     * confirm button — a volume slider you have to confirm is a slider you
     * cannot hear while you move it.
     */
    public enum OptionRow {
        LANGUAGE("Language"),
        MASTER("Master Volume"),
        SFX("Effects"),
        MUSIC("Music"),
        BACK("Back");

        private final String label;

        OptionRow(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        /** True for the three volume rows. */
        public boolean isSlider() {
            return this == MASTER || this == SFX || this == MUSIC;
        }
    }

    /** What the caller should do in response to an activation. */
    public enum Outcome {
        /** Nothing happened, or the highlighted entry could not be activated. */
        NONE,

        /**
         * The press registered and is playing out. The real outcome arrives from
         * {@link MenuState#pollReady()} once the button has finished depressing.
         */
        PENDING,

        /** Move to the hero picker. */
        OPEN_HERO,

        /** Move to the difficulty picker. */
        OPEN_DIFFICULTY,

        /** Move to the Options screen. */
        OPEN_OPTIONS,

        /**
         * A setting changed — read {@link MenuState#getLanguage()} and
         * {@link MenuState#getAudio()} and apply them. Arrives immediately, with
         * no press delay, because the change is already visible on screen.
         */
        SETTINGS_CHANGED,

        /** Begin a fresh run on {@link MenuState#getSelectedDifficulty()}. */
        START_RUN,

        /** Resume the saved run. */
        RESUME_RUN,

        START_SANDBOX,

        /** Return to the main list. */
        BACK,

        /** Close the game. */
        EXIT
    }

    /** How long a locked-entry nudge stays on screen, in ticks. */
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
    private int heroIndex = Hero.defaultChoice().ordinal();

    /**
     * The hero the picker opens on — the one last played, restored from the
     * save. Browsing without confirming does not change it.
     */
    private Hero preferredHero = Hero.defaultChoice();

    /**
     * Whether the hero picker was opened from Sandbox rather than New Game.
     * The same screen leads two places: New Game still has a tier to choose,
     * the Sandbox does not.
     */
    private boolean heroForSandbox;

    /** Highlighted row on the Options screen. */
    private int optionIndex;

    /** The settings the Options screen shows and edits; seeded from the save. */
    private Language language = Language.ENGLISH;
    private AudioSettings audio = AudioSettings.defaults();

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

        if (screen == Screen.OPTIONS) {
            OptionRow row = getSelectedOption();
            if (row == OptionRow.BACK) {
                return back();
            }
            // Enter on the language row flips it, same as an arrow key would.
            // On a slider it does nothing: there is no one value to jump to.
            return row == OptionRow.LANGUAGE && adjust(1)
                    ? Outcome.SETTINGS_CHANGED
                    : Outcome.NONE;
        }

        Outcome resolved = switch (screen) {
            case MAIN -> resolveMainItem();
            case HERO -> resolveHero();
            case DIFFICULTY -> resolveDifficulty();
            case OPTIONS -> Outcome.NONE;
        };

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
            case OPEN_HERO -> {
                screen = Screen.HERO;
                heroIndex = preferredHero.ordinal();
            }
            case OPEN_DIFFICULTY -> {
                screen = Screen.DIFFICULTY;
                difficultyIndex = Difficulty.defaultChoice().ordinal();
            }
            case OPEN_OPTIONS -> {
                screen = Screen.OPTIONS;
                optionIndex = 0;
            }
            // One step back at a time: the tier list returns to the hero it
            // was reached from, still highlighted, rather than all the way out.
            case BACK -> screen = screen == Screen.DIFFICULTY ? Screen.HERO : Screen.MAIN;
            case START_RUN, START_SANDBOX -> preferredHero = getSelectedHero();
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
            case NEW_GAME -> openHero(false);
            case CONTINUE -> Outcome.RESUME_RUN;
            case SANDBOX -> openHero(true);
            case OPTIONS -> Outcome.OPEN_OPTIONS;
            case EXIT -> Outcome.EXIT;
            default -> Outcome.NONE;
        };
    }

    private Outcome openHero(boolean forSandbox) {
        heroForSandbox = forSandbox;
        return Outcome.OPEN_HERO;
    }

    /** Confirming a hero moves on to the tier, or straight into the Sandbox. */
    private Outcome resolveHero() {
        return heroForSandbox ? Outcome.START_SANDBOX : Outcome.OPEN_DIFFICULTY;
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
     * @return {@link Outcome#PENDING} (becoming {@link Outcome#BACK}) on the
     *         hero, difficulty and options screens, or {@link Outcome#EXIT} when
     *         already at the top
     */
    public Outcome back() {
        clearLockedFlash();
        if (pressTicks > 0 || pendingOutcome != Outcome.NONE) {
            // A press is already committed; do not race it.
            return Outcome.NONE;
        }
        if (screen != Screen.MAIN) {
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

    public void select(OptionRow row) {
        if (screen == Screen.OPTIONS && row != null) {
            optionIndex = row.ordinal();
        }
    }

    // ---- options -----------------------------------------------------------

    /**
     * Changes the highlighted option by one step: the next language, or a
     * slider nudged by {@link AudioSettings#STEP}.
     *
     * @param direction negative for left, positive for right
     * @return true when a setting actually changed, so the caller applies it
     */
    public boolean adjust(int direction) {
        if (screen != Screen.OPTIONS || direction == 0) {
            return false;
        }
        OptionRow row = getSelectedOption();
        if (row == OptionRow.LANGUAGE) {
            Language[] all = Language.values();
            int step = direction > 0 ? 1 : -1;
            language = all[(language.ordinal() + step + all.length) % all.length];
            return true;
        }
        if (row.isSlider()) {
            int delta = direction > 0 ? AudioSettings.STEP : -AudioSettings.STEP;
            return setSlider(row, sliderValue(row) + delta);
        }
        return false;
    }

    /**
     * Sets a volume row outright, e.g. from a mouse drag.
     *
     * @return true when the value actually changed
     */
    public boolean setSlider(OptionRow row, int value) {
        if (row == null || !row.isSlider()) {
            return false;
        }
        AudioSettings before = audio;
        audio = switch (row) {
            case MASTER -> audio.withMaster(value);
            case SFX -> audio.withSfx(value);
            case MUSIC -> audio.withMusic(value);
            default -> audio;
        };
        return !audio.equals(before);
    }

    /** Picks a language outright, e.g. from a click on it. */
    public boolean chooseLanguage(Language chosen) {
        if (chosen == null || chosen == language) {
            return false;
        }
        language = chosen;
        return true;
    }

    /** A volume row's current value, 0 to 100. */
    public int sliderValue(OptionRow row) {
        return switch (row) {
            case MASTER -> audio.master();
            case SFX -> audio.sfx();
            case MUSIC -> audio.music();
            default -> 0;
        };
    }

    public OptionRow getSelectedOption() {
        return OptionRow.values()[optionIndex];
    }

    public Language getLanguage() {
        return language;
    }

    /** Seeds the language from the save. */
    public void setLanguage(Language language) {
        this.language = language == null ? Language.ENGLISH : language;
    }

    public AudioSettings getAudio() {
        return audio;
    }

    /** Seeds the volumes from the save. */
    public void setAudio(AudioSettings audio) {
        this.audio = audio == null ? AudioSettings.defaults() : audio;
    }

    public void select(Hero hero) {
        if (screen == Screen.HERO && hero != null) {
            heroIndex = hero.ordinal();
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
        heroIndex = preferredHero.ordinal();
        heroForSandbox = false;
        optionIndex = 0;
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

    /** The locked-entry line to show, or empty when there is none. */
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

    public Hero getSelectedHero() {
        return Hero.values()[heroIndex];
    }

    /** The hero the picker opens on. */
    public Hero getPreferredHero() {
        return preferredHero;
    }

    /** Sets the hero the picker opens on, e.g. from the save or the last run. */
    public void setPreferredHero(Hero hero) {
        preferredHero = hero == null ? Hero.defaultChoice() : hero;
    }

    /** True when the hero picker leads into the Sandbox rather than a run. */
    public boolean isHeroForSandbox() {
        return heroForSandbox;
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
        return switch (screen) {
            case MAIN -> MenuItem.values().length;
            case HERO -> Hero.values().length;
            case DIFFICULTY -> Difficulty.values().length;
            case OPTIONS -> OptionRow.values().length;
        };
    }

    private int currentIndex() {
        return switch (screen) {
            case MAIN -> mainIndex;
            case HERO -> heroIndex;
            case DIFFICULTY -> difficultyIndex;
            case OPTIONS -> optionIndex;
        };
    }

    private void setIndex(int index) {
        switch (screen) {
            case MAIN -> mainIndex = index;
            case HERO -> heroIndex = index;
            case DIFFICULTY -> difficultyIndex = index;
            case OPTIONS -> optionIndex = index;
        }
    }
}
