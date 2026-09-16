package com.guardiansofangkor.audio;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages background music playback and sound effect dispatching using {@code javax.sound.sampled}.
 *
 * <p>Operates as a singleton. Music tracks are single-instance and mutually exclusive (only one
 * plays at a time). Sound effects are pooled (round-robin) to support overlapping playback. All
 * volume controls accept linear gains in [0.0, 1.0] and translate to decibels via
 * {@link FloatControl.Type#MASTER_GAIN}.
 *
 * <p>All failure paths log warnings via {@link java.util.logging.Logger} and return gracefully,
 * ensuring missing files, unsupported formats, or missing audio mixers never crash the game.
 */
public final class AudioManager {

    private static final Logger LOGGER = Logger.getLogger(AudioManager.class.getName());

    public enum Track {
        MENU_THEME("menu_theme.wav"),
        GAMEPLAY_THEME("gameplay_theme.wav");

        private final String fileName;

        Track(String fileName) {
            this.fileName = fileName;
        }

        public String getFileName() {
            return fileName;
        }
    }

    public enum Sfx {
        HIT_CORRECT("hit_correct.wav"),
        HIT_WRONG("hit_wrong.wav"),
        ENEMY_SPAWN("enemy_spawn.wav"),
        BOSS_SPAWN("boss_spawn.wav"),
        POWERUP_COLLECT("powerup_collect.wav"),
        WAVE_COMPLETE("wave_complete.wav"),
        GAME_OVER("game_over.wav"),
        VICTORY("victory.wav");

        private final String fileName;

        Sfx(String fileName) {
            this.fileName = fileName;
        }

        public String getFileName() {
            return fileName;
        }
    }

    private static final AudioManager INSTANCE = new AudioManager();

    private static final String AUDIO_PATH_PREFIX = "/audio/";
    private static final int SFX_POOL_SIZE = 4;
    private static final long ENEMY_SPAWN_THROTTLE_MS = 125L; // Max ~8 spawns/sec

    private final Map<Track, Clip> musicClips = new EnumMap<>(Track.class);
    private final Map<Sfx, List<Clip>> sfxPools = new EnumMap<>(Sfx.class);
    private final Map<Sfx, Integer> sfxIndices = new EnumMap<>(Sfx.class);

    private Track currentTrack = null;
    private float masterVolume = 1.0f;
    private float musicVolume = 1.0f;
    private float sfxVolume = 1.0f;
    private boolean musicMuted = false;
    private boolean sfxMuted = false;
    private boolean preloaded = false;
    private boolean isShutdown = false;

    private long lastEnemySpawnPlayTimeMs = 0L;

    public static AudioManager getInstance() {
        return INSTANCE;
    }

    private AudioManager() {
    }

    /**
     * Preloads all background music and SFX clips from the classpath into memory.
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    public synchronized void preloadAll() {
        if (preloaded || isShutdown) {
            return;
        }

        for (Track track : Track.values()) {
            Clip clip = loadClip(track.getFileName());
            if (clip != null) {
                musicClips.put(track, clip);
            }
        }

        for (Sfx sfx : Sfx.values()) {
            List<Clip> pool = loadClipPool(sfx.getFileName(), SFX_POOL_SIZE);
            if (!pool.isEmpty()) {
                sfxPools.put(sfx, pool);
                sfxIndices.put(sfx, 0);
            }
        }

        preloaded = true;
        updateMusicVolumes();
        updateSfxVolumes();
    }

    /**
     * Plays the specified music track, stopping any currently playing music first.
     *
     * @param track the music track to play
     * @param loop whether to loop continuously
     */
    public synchronized void playMusic(Track track, boolean loop) {
        if (isShutdown) {
            return;
        }
        stopMusic();
        if (track == null) {
            return;
        }

        currentTrack = track;
        Clip clip = musicClips.get(track);
        if (clip == null) {
            return;
        }

        try {
            clip.setFramePosition(0);
            applyVolume(clip, masterVolume * musicVolume, musicMuted);
            if (loop) {
                clip.loop(Clip.LOOP_CONTINUOUSLY);
            } else {
                clip.start();
            }
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Failed to start music track: " + track, t);
        }
    }

    /**
     * Switches to the specified music track. If that track is already playing,
     * this call is a no-op.
     *
     * @param track the target music track
     * @param loop whether to loop continuously if started
     */
    public synchronized void switchMusic(Track track, boolean loop) {
        if (track == null) {
            stopMusic();
            return;
        }
        if (track == currentTrack) {
            Clip clip = musicClips.get(track);
            if (clip != null && clip.isRunning()) {
                return; // Already playing
            }
        }
        playMusic(track, loop);
    }

    /**
     * Stops any currently playing music track and resets the playback position.
     */
    public synchronized void stopMusic() {
        if (currentTrack != null) {
            Clip clip = musicClips.get(currentTrack);
            if (clip != null) {
                try {
                    if (clip.isRunning()) {
                        clip.stop();
                    }
                    clip.setFramePosition(0);
                } catch (Throwable t) {
                    LOGGER.log(Level.WARNING, "Failed to stop music clip: " + currentTrack, t);
                }
            }
            currentTrack = null;
        }
    }

    /**
     * Plays a sound effect one-shot from its round-robin clip pool, supporting overlapping playback.
     * Automatically throttles {@link Sfx#ENEMY_SPAWN} to ~8/sec internally.
     *
     * @param sfx the sound effect to play
     */
    public synchronized void playSFX(Sfx sfx) {
        if (sfx == null || isShutdown) {
            return;
        }

        if (sfx == Sfx.ENEMY_SPAWN) {
            long now = System.currentTimeMillis();
            if (now - lastEnemySpawnPlayTimeMs < ENEMY_SPAWN_THROTTLE_MS) {
                return;
            }
            lastEnemySpawnPlayTimeMs = now;
        }

        List<Clip> pool = sfxPools.get(sfx);
        if (pool == null || pool.isEmpty()) {
            return;
        }

        int index = sfxIndices.getOrDefault(sfx, 0);
        sfxIndices.put(sfx, (index + 1) % pool.size());

        Clip clip = pool.get(index);
        if (clip == null) {
            return;
        }

        try {
            if (clip.isRunning()) {
                clip.stop();
            }
            clip.setFramePosition(0);
            applyVolume(clip, masterVolume * sfxVolume, sfxMuted);
            clip.start();
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Failed to play SFX: " + sfx, t);
        }
    }

    public synchronized void setMasterVolume(float volume) {
        this.masterVolume = Math.max(0.0f, Math.min(1.0f, volume));
        updateMusicVolumes();
        updateSfxVolumes();
    }

    public synchronized void setMusicVolume(float volume) {
        this.musicVolume = Math.max(0.0f, Math.min(1.0f, volume));
        updateMusicVolumes();
    }

    public synchronized void setSfxVolume(float volume) {
        this.sfxVolume = Math.max(0.0f, Math.min(1.0f, volume));
        updateSfxVolumes();
    }

    public synchronized float getMasterVolume() {
        return masterVolume;
    }

    public synchronized float getMusicVolume() {
        return musicVolume;
    }

    public synchronized float getSfxVolume() {
        return sfxVolume;
    }

    public synchronized void toggleMusicMuted() {
        this.musicMuted = !this.musicMuted;
        updateMusicVolumes();
    }

    public synchronized void toggleSfxMuted() {
        this.sfxMuted = !this.sfxMuted;
        updateSfxVolumes();
    }

    public synchronized boolean isMusicMuted() {
        return musicMuted;
    }

    public synchronized boolean isSfxMuted() {
        return sfxMuted;
    }

    public synchronized Track getCurrentTrack() {
        return currentTrack;
    }

    /**
     * Releases all Clip and audio line resources on exit.
     */
    public synchronized void shutdown() {
        if (isShutdown) {
            return;
        }
        isShutdown = true;
        stopMusic();

        for (Clip clip : musicClips.values()) {
            closeClip(clip);
        }
        musicClips.clear();

        for (List<Clip> pool : sfxPools.values()) {
            for (Clip clip : pool) {
                closeClip(clip);
            }
        }
        sfxPools.clear();
        sfxIndices.clear();
    }

    private void updateMusicVolumes() {
        for (Clip clip : musicClips.values()) {
            applyVolume(clip, masterVolume * musicVolume, musicMuted);
        }
    }

    private void updateSfxVolumes() {
        for (List<Clip> pool : sfxPools.values()) {
            for (Clip clip : pool) {
                applyVolume(clip, masterVolume * sfxVolume, sfxMuted);
            }
        }
    }

    private void applyVolume(Clip clip, float volume, boolean muted) {
        if (clip == null || !clip.isOpen()) {
            return;
        }
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float min = gain.getMinimum();
                float max = gain.getMaximum();
                if (muted || volume <= 0.0001f) {
                    gain.setValue(min);
                } else {
                    float dB = (float) (20.0 * Math.log10(volume));
                    dB = Math.max(min, Math.min(dB, max));
                    gain.setValue(dB);
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Failed to apply volume to clip", t);
        }
    }

    private Clip loadClip(String fileName) {
        byte[] bytes = readAudioBytes(fileName);
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))) {
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            return clip;
        } catch (UnsupportedAudioFileException e) {
            LOGGER.log(Level.WARNING, "Unsupported audio file format: " + fileName, e);
        } catch (LineUnavailableException e) {
            LOGGER.log(Level.WARNING, "Audio line unavailable for: " + fileName, e);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "I/O error opening audio clip: " + fileName, e);
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Unexpected error loading clip: " + fileName, t);
        }
        return null;
    }

    private List<Clip> loadClipPool(String fileName, int poolSize) {
        List<Clip> pool = new ArrayList<>(poolSize);
        byte[] bytes = readAudioBytes(fileName);
        if (bytes == null || bytes.length == 0) {
            return pool;
        }
        for (int i = 0; i < poolSize; i++) {
            try (AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))) {
                Clip clip = AudioSystem.getClip();
                clip.open(ais);
                pool.add(clip);
            } catch (UnsupportedAudioFileException e) {
                LOGGER.log(Level.WARNING, "Unsupported audio file format: " + fileName, e);
                break;
            } catch (LineUnavailableException e) {
                LOGGER.log(Level.WARNING, "Audio line unavailable for: " + fileName, e);
                break;
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "I/O error creating clip pool: " + fileName, e);
                break;
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Unexpected error creating clip pool: " + fileName, t);
                break;
            }
        }
        return pool;
    }

    private byte[] readAudioBytes(String fileName) {
        String path = AUDIO_PATH_PREFIX + fileName;
        try (InputStream in = openResourceStream(path, fileName)) {
            if (in == null) {
                LOGGER.log(Level.WARNING, "Audio resource not found on classpath: " + path);
                return null;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "I/O error reading audio resource: " + path, e);
            return null;
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Unexpected error reading audio resource: " + path, t);
            return null;
        }
    }

    private InputStream openResourceStream(String path, String fileName) {
        InputStream in = AudioManager.class.getResourceAsStream(path);
        if (in == null) {
            in = AudioManager.class.getClassLoader().getResourceAsStream("audio/" + fileName);
        }
        return in;
    }

    private void closeClip(Clip clip) {
        if (clip != null) {
            try {
                if (clip.isRunning()) {
                    clip.stop();
                }
                clip.close();
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Error closing audio clip", t);
            }
        }
    }
}
