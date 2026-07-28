package com.guardiansofangkor.save;

import com.guardiansofangkor.i18n.Language;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Reads and writes run progress.
 *
 * <p>Uses {@link Properties} as the brief specifies (Section 5.4) — no database,
 * no new dependency. Every method here is total: a corrupt, unreadable or absent
 * save yields {@link SaveData#empty()} rather than an exception, because losing
 * a save must never stop the game from starting.
 *
 * <p>Writes go to a temporary file which is then atomically moved into place, so
 * a crash mid-write cannot leave a half-written save that fails to parse next
 * launch.
 */
public class SaveManager {

    private static final String SAVE_DIR = ".guardiansofangkor";
    private static final String SAVE_FILE = "progress.properties";

    private static final String KEY_WAVE = "wave";
    private static final String KEY_SCORE = "score";
    private static final String KEY_LIVES = "lives";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_BEST_SCORE = "bestScore";
    private static final String KEY_BEST_WAVE = "bestWave";

    private final Path saveFile;

    /** Saves under the user's home directory. */
    public SaveManager() {
        this(defaultSavePath());
    }

    /** Explicit path constructor, used by tests to write to a temp directory. */
    public SaveManager(Path saveFile) {
        this.saveFile = saveFile;
    }

    private static Path defaultSavePath() {
        try {
            String home = System.getProperty("user.home");
            if (home != null && !home.isBlank()) {
                return Paths.get(home, SAVE_DIR, SAVE_FILE);
            }
        } catch (SecurityException e) {
            System.err.println("[SaveManager] Cannot read user.home (" + e.getMessage()
                    + ") — saving beside the jar instead.");
        }
        return Paths.get(SAVE_FILE);
    }

    /**
     * Loads saved progress. Returns {@link SaveData#empty()} when the file is
     * missing, unreadable, or contains values that will not parse.
     */
    public SaveData load() {
        if (!Files.exists(saveFile)) {
            return SaveData.empty();
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(saveFile)) {
            props.load(in);
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            System.err.println("[SaveManager] Could not read save file ("
                    + e.getMessage() + ") — starting fresh.");
            return SaveData.empty();
        }

        return new SaveData(
                readInt(props, KEY_WAVE),
                readInt(props, KEY_SCORE),
                readInt(props, KEY_LIVES),
                Language.fromCode(props.getProperty(KEY_LANGUAGE)),
                readInt(props, KEY_BEST_SCORE),
                readInt(props, KEY_BEST_WAVE));
    }

    /**
     * Writes progress to disk.
     *
     * @return true when the save landed; false when it failed (already logged)
     */
    public boolean save(SaveData data) {
        if (data == null) {
            return false;
        }
        Properties props = new Properties();
        props.setProperty(KEY_WAVE, Integer.toString(data.wave()));
        props.setProperty(KEY_SCORE, Integer.toString(data.score()));
        props.setProperty(KEY_LIVES, Integer.toString(data.lives()));
        props.setProperty(KEY_LANGUAGE, data.language().getCode());
        props.setProperty(KEY_BEST_SCORE, Integer.toString(data.bestScore()));
        props.setProperty(KEY_BEST_WAVE, Integer.toString(data.bestWave()));

        try {
            Path parent = saveFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // Write to a sibling temp file, then move — a crash mid-write leaves
            // the previous good save intact rather than a truncated one.
            Path temp = saveFile.resolveSibling(saveFile.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                props.store(out, "Guardians of Angkor — run progress");
            }
            try {
                Files.move(temp, saveFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                // Some filesystems cannot move atomically — fall back to a plain
                // replace rather than losing the save entirely.
                Files.move(temp, saveFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | SecurityException e) {
            System.err.println("[SaveManager] Could not write save file ("
                    + e.getMessage() + ") — progress this run may be lost.");
            return false;
        }
    }

    /** Deletes the save, e.g. after a game over is acknowledged. */
    public boolean clear() {
        try {
            return Files.deleteIfExists(saveFile);
        } catch (IOException | SecurityException e) {
            System.err.println("[SaveManager] Could not delete save file ("
                    + e.getMessage() + ").");
            return false;
        }
    }

    public Path getSaveFile() {
        return saveFile;
    }

    private static int readInt(Properties props, String key) {
        try {
            return Integer.parseInt(props.getProperty(key, "0").trim());
        } catch (NumberFormatException e) {
            // A hand-edited or corrupted value should not sink the whole load.
            return 0;
        }
    }
}
