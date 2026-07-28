package com.guardiansofangkor.save;

import java.util.function.Supplier;

/**
 * Last-resort save trigger.
 *
 * <p>Dev brief Section 5.4 asks for a JVM shutdown hook so progress survives a
 * crash or a force-quit, not just a clean exit. The hook pulls current progress
 * from a supplier at shutdown time rather than holding a stale snapshot.
 *
 * <p>The hook thread swallows everything it catches — an exception thrown during
 * shutdown is both unloggable and unhelpful, and must not stall the JVM exiting.
 */
public final class AutosaveHook {

    private final SaveManager saveManager;
    private final Supplier<SaveData> progressSupplier;
    private Thread hookThread;

    public AutosaveHook(SaveManager saveManager, Supplier<SaveData> progressSupplier) {
        if (saveManager == null || progressSupplier == null) {
            throw new IllegalArgumentException("saveManager and progressSupplier are required");
        }
        this.saveManager = saveManager;
        this.progressSupplier = progressSupplier;
    }

    /** Registers the shutdown hook. Safe to call once; repeat calls are ignored. */
    public synchronized void register() {
        if (hookThread != null) {
            return;
        }
        hookThread = new Thread(this::saveQuietly, "goa-autosave-shutdown");
        try {
            Runtime.getRuntime().addShutdownHook(hookThread);
        } catch (IllegalStateException | SecurityException e) {
            // Already shutting down, or a security manager forbids hooks.
            System.err.println("[AutosaveHook] Could not register shutdown hook ("
                    + e.getMessage() + ") — relying on wave-clear autosaves only.");
            hookThread = null;
        }
    }

    /** Removes the hook, e.g. when the player exits cleanly and has already saved. */
    public synchronized void unregister() {
        if (hookThread == null) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(hookThread);
        } catch (IllegalStateException | SecurityException e) {
            // Shutdown already underway — the hook will simply run. Nothing to do.
        }
        hookThread = null;
    }

    /** Saves now, swallowing any failure. Also used for the wave-clear autosave. */
    public void saveQuietly() {
        try {
            SaveData data = progressSupplier.get();
            if (data != null) {
                saveManager.save(data);
            }
        } catch (RuntimeException e) {
            // Nothing useful can be done here, and throwing would stall shutdown.
        }
    }
}
