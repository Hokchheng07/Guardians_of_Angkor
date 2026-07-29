package com.guardiansofangkor.engine;

/**
 * Entries on the main menu, in the order the design lists them.
 *
 * <p>Options and Bestiary are placeholders — present in the design, not yet
 * built. They render disabled rather than being dropped, so the menu keeps the
 * proportions the artwork was composed around.
 */
public enum MenuItem {

    NEW_GAME("New Game", true),
    CONTINUE("Continue", true),
    OPTIONS("Options", false),
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
