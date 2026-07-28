package com.guardiansofangkor.save;

import com.guardiansofangkor.i18n.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SaveManager — crash-safe persistence")
class SaveManagerTest {

    @Test
    @DisplayName("a saved run round-trips intact")
    void roundTrip(@TempDir Path dir) {
        SaveManager manager = new SaveManager(dir.resolve("progress.properties"));
        SaveData original = new SaveData(7, 4200, 2, Language.KHMER, 9000, 12);

        assertTrue(manager.save(original), "save should succeed");
        SaveData loaded = manager.load();

        assertEquals(original, loaded);
    }

    @Test
    @DisplayName("a missing save file yields an empty save, not an exception")
    void missingFileYieldsEmpty(@TempDir Path dir) {
        SaveManager manager = new SaveManager(dir.resolve("does-not-exist.properties"));

        SaveData loaded = manager.load();

        assertEquals(SaveData.empty(), loaded);
        assertFalse(loaded.hasResumableRun());
    }

    @Test
    @DisplayName("a corrupt save file degrades gracefully instead of crashing")
    void corruptFileDegradesGracefully(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("progress.properties");
        Files.writeString(file, "wave=not-a-number\nscore=???\nlanguage=zz\n");

        SaveManager manager = new SaveManager(file);
        SaveData loaded = manager.load();

        assertEquals(0, loaded.wave(), "unparseable values should fall back to zero");
        assertEquals(0, loaded.score());
        assertEquals(Language.ENGLISH, loaded.language(),
                "an unknown language code should fall back to English");
    }

    @Test
    @DisplayName("save creates missing parent directories")
    void createsParentDirectories(@TempDir Path dir) {
        Path nested = dir.resolve("deeply").resolve("nested").resolve("progress.properties");
        SaveManager manager = new SaveManager(nested);

        assertTrue(manager.save(new SaveData(1, 10, 3, Language.ENGLISH, 10, 1)));
        assertTrue(Files.exists(nested));
    }

    @Test
    @DisplayName("saving leaves no stray temp file behind")
    void noTempFileLeftBehind(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("progress.properties");
        SaveManager manager = new SaveManager(file);
        manager.save(new SaveData(3, 100, 2, Language.ENGLISH, 100, 3));

        try (var stream = Files.list(dir)) {
            assertTrue(stream.noneMatch(p -> p.toString().endsWith(".tmp")),
                    "atomic write should clean up its temp file");
        }
    }

    @Test
    @DisplayName("clear removes the save")
    void clearRemovesSave(@TempDir Path dir) {
        Path file = dir.resolve("progress.properties");
        SaveManager manager = new SaveManager(file);
        manager.save(new SaveData(2, 50, 1, Language.ENGLISH, 50, 2));

        assertTrue(manager.clear());
        assertFalse(Files.exists(file));
        assertEquals(SaveData.empty(), manager.load());
    }

    @Test
    @DisplayName("negative values are clamped rather than persisted")
    void negativesAreClamped() {
        SaveData data = new SaveData(-5, -100, -3, null, -1, -2);

        assertEquals(0, data.wave());
        assertEquals(0, data.score());
        assertEquals(0, data.lives());
        assertEquals(Language.ENGLISH, data.language());
    }

    @Test
    @DisplayName("only a run with progress and lives left is resumable")
    void resumableRunDetection() {
        assertFalse(SaveData.empty().hasResumableRun());
        assertFalse(new SaveData(5, 100, 0, Language.ENGLISH, 100, 5).hasResumableRun(),
                "a dead run should not be resumable");
        assertTrue(new SaveData(5, 100, 2, Language.ENGLISH, 100, 5).hasResumableRun());
    }
}
