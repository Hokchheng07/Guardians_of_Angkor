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
    @DisplayName("unlocked tiers round-trip with the run")
    void clearedTiersRoundTrip(@TempDir Path dir) {
        SaveManager manager = new SaveManager(dir.resolve("progress.properties"));
        SaveData original = new SaveData(7, 4200, 2, Language.ENGLISH, 9000, 12,
                new java.util.LinkedHashSet<>(java.util.List.of("easy", "medium")));

        assertTrue(manager.save(original));
        SaveData loaded = manager.load();

        assertTrue(loaded.hasCleared("easy"));
        assertTrue(loaded.hasCleared("medium"));
        assertFalse(loaded.hasCleared("hard"),
                "a tier that was never beaten must not come back unlocked");
    }

    @Test
    @DisplayName("a save written before unlocks existed still loads")
    void oldSavesHaveNoUnlocks(@TempDir Path dir) throws IOException {
        // The field is simply absent in an older file. That should read as
        // "nothing cleared yet", not as a parse failure that costs the run too.
        Path file = dir.resolve("progress.properties");
        Files.writeString(file, "wave=4\nscore=120\nlives=2\nlanguage=en\n");

        SaveData loaded = new SaveManager(file).load();

        assertEquals(4, loaded.wave(), "the rest of the save must still load");
        assertTrue(loaded.clearedTiers().isEmpty());
    }

    @Test
    @DisplayName("the chosen hero round-trips with the run")
    void heroRoundTrips(@TempDir Path dir) {
        SaveManager manager = new SaveManager(dir.resolve("progress.properties"));
        SaveData original = new SaveData(3, 90, 2, Language.ENGLISH, 90, 3,
                java.util.Set.of("easy"), "apsara");

        assertTrue(manager.save(original));
        SaveData loaded = manager.load();

        assertEquals("apsara", loaded.heroKey());
        assertEquals(original, loaded);
    }

    @Test
    @DisplayName("the chosen temple round-trips with the run")
    void templeRoundTrips(@TempDir Path dir) {
        SaveManager manager = new SaveManager(dir.resolve("progress.properties"));
        SaveData original = new SaveData(3, 90, 2, Language.ENGLISH, 90, 3,
                java.util.Set.of("easy"), "apsara", "bayon");

        assertTrue(manager.save(original));
        SaveData loaded = manager.load();

        assertEquals("bayon", loaded.templeMapKey());
        assertEquals(original, loaded);
    }

    @Test
    @DisplayName("a save written before heroes existed loads with no hero")
    void oldSavesHaveNoHero(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("progress.properties");
        Files.writeString(file, "wave=4\nscore=120\nlives=2\nlanguage=en\n");

        SaveData loaded = new SaveManager(file).load();

        assertEquals(4, loaded.wave());
        assertEquals("", loaded.heroKey(), "blank, which the engine reads as the default hero");
        assertEquals("", loaded.templeMapKey(),
                "blank, which the engine reads as the default temple");
    }

    @Test
    @DisplayName("language and volumes round-trip")
    void settingsRoundTrip(@TempDir Path dir) {
        SaveManager manager = new SaveManager(dir.resolve("progress.properties"));
        SaveData original = SaveData.empty().withSettings(Language.KHMER,
                new com.guardiansofangkor.audio.AudioSettings(35, 90, 10));

        assertTrue(manager.save(original));
        SaveData loaded = manager.load();

        assertEquals(Language.KHMER, loaded.language());
        assertEquals(new com.guardiansofangkor.audio.AudioSettings(35, 90, 10), loaded.audio());
    }

    @Test
    @DisplayName("a save from before the sliders opens at default volume, not muted")
    void oldSavesAreNotMuted(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("progress.properties");
        Files.writeString(file, "wave=4\nscore=120\nlives=2\nlanguage=en\n");

        assertEquals(com.guardiansofangkor.audio.AudioSettings.defaults(),
                new SaveManager(file).load().audio());
    }

    @Test
    @DisplayName("withSettings changes only the settings")
    void withSettingsKeepsRun() {
        SaveData run = new SaveData(6, 800, 2, Language.ENGLISH, 900, 7,
                java.util.Set.of("easy"), "apsara");
        SaveData changed = run.withSettings(Language.KHMER,
                new com.guardiansofangkor.audio.AudioSettings(10, 20, 30));

        assertEquals(6, changed.wave());
        assertEquals(800, changed.score());
        assertTrue(changed.hasResumableRun(), "the saved run must survive a settings change");
        assertEquals("apsara", changed.heroKey());
        assertEquals(Language.KHMER, changed.language());
    }

    @Test
    @DisplayName("bests and clears keep the hero")
    void copiesKeepHero() {
        SaveData data = new SaveData(3, 90, 2, Language.ENGLISH, 90, 3,
                java.util.Set.of(), "apsara");

        assertEquals("apsara", data.withBests(500, 9).heroKey());
        assertEquals("apsara", data.withCleared("easy").heroKey());
    }

    @Test
    @DisplayName("a hand-edited unlock list is cleaned up rather than trusted")
    void clearedTiersAreNormalised(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("progress.properties");
        Files.writeString(file, "wave=1\nlives=3\nclearedTiers= EASY , ,medium \n");

        SaveData loaded = new SaveManager(file).load();

        assertTrue(loaded.hasCleared("easy"), "capitals and padding should still count");
        assertTrue(loaded.hasCleared("medium"));
        assertEquals(2, loaded.clearedTiers().size(), "the blank entry should be dropped");
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
