package com.guardiansofangkor.engine;

/**
 * Entries on the main menu, in the order the design lists them.
 *
 * <p>Bestiary is still a placeholder — present in the design, not yet built.
 * It renders disabled rather than being dropped, so the menu keeps the
 * proportions the artwork was composed around. Options opens the language and
 * volume settings; see {@link MenuState.Screen#OPTIONS}.
 */
public enum MenuItem {

    NEW_GAME("New Game", true),
    CONTINUE("Continue", true),
    SANDBOX("Sandbox", true),
    OPTIONS("Options", true),
    BESTIARY("Bestiary", false),
    EXIT("Exit", true);

    private final String label;
    private final boolean implemented;

    MenuItem(String label, boolean implemented) {
        this.label = label;
        this.implemented = implemented;
    }

    public String getLabel() {
        return label;
    }

    /**
     * False for entries that are drawn but cannot be activated yet.
     *
     * <p>Note that Continue is "implemented" here yet still needs a save to
     * exist — availability is a separate question, answered by
     * {@link MenuState#isEnabled(MenuItem)}.
     */
    public boolean isImplemented() {
        return implemented;
    }
}
