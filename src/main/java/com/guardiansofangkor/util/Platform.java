package com.guardiansofangkor.util;

import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;

/**
 * Host-platform differences that both the input and rendering layers need.
 *
 * <p>Lives in {@code util} rather than in either of them because both need it:
 * {@code KeyboardHandler} binds the pause chord and {@code HUDRenderer} labels
 * it. Putting it in one of those packages would make the other depend on it and
 * create a cycle between input and rendering.
 */
public final class Platform {

    private Platform() {
        // Utility class — not instantiable.
    }

    /**
     * The platform's primary shortcut modifier — Command on macOS, Control on
     * Windows and Linux.
     *
     * <p>Read from the toolkit rather than sniffing {@code os.name}, so the game
     * agrees with whatever the user's other applications do.
     */
    public static int commandModifier() {
        try {
            return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (HeadlessException | UnsupportedOperationException e) {
            // Headless test runs have no toolkit; Control is the safe default.
            return KeyEvent.CTRL_DOWN_MASK;
        }
    }

    /** True when the primary modifier is the macOS Command key. */
    public static boolean isMacCommand() {
        return commandModifier() == KeyEvent.META_DOWN_MASK;
    }

    /**
     * Human-readable name of the pause chord, so the pause overlay tells macOS
     * players "CMD" and everyone else "CTRL".
     */
    public static String pauseShortcutLabel() {
        return isMacCommand() ? "CMD + P" : "CTRL + P";
    }
}
