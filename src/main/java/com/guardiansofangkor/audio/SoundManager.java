package com.guardiansofangkor.audio;

import com.guardiansofangkor.engine.TempleMap;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * Plays the game's music and sound effects.
 *
 * <p>Called only from the UI layer (Main, MenuPanel) — never from the engine.
 * GameState and MenuState stay free of audio the same way they stay free of
 * Swing, so the tests run silent and deterministic; the engine exposes events
 * (e.g. GameState.getShotsLoosed) and the UI sounds them.
 *
 * <p>Volume comes from the Options sliders via {@link #apply(AudioSettings)}:
 * effects play at Master x Effects, music at Master x Music. Moving the Music
 * slider changes the track that is already playing.
 */
public class SoundManager {

    /** Effective effects volume, 0 to 1, from the sliders. */
    private static volatile double sfxGain = AudioSettings.defaults().sfxGain();

    /** Effective music volume, 0 to 1, from the sliders. */
    private static volatile double musicGain = AudioSettings.defaults().musicGain();

    /**
     * Decoded effect audio, by file name. An effect is decoded once and each
     * play opens a fresh Clip over the cached bytes — re-reading the WAV on
     * every shot meant a disk read and a decode per keystroke.
     */
    private static final Map<String, Decoded> SFX_CACHE = new ConcurrentHashMap<>();

    private record Decoded(AudioFormat format, byte[] bytes) {
    }

    private static Clip bgmClip;
    private static Thread playlistThread;
    private static volatile boolean playlistRunning = false;

    private static final String[] PLAYLIST = {
            "Background1.wav",
            "Background2.wav",
            "Background3.wav",
            "Background4.wav",
            "Background5.wav"
    };

    private static final String[] MENU_PLAYLIST = {"Background1.wav"};

    private SoundManager() {}

    /** Takes the Options sliders. Applies to the music already playing, too. */
    public static void apply(AudioSettings settings) {
        AudioSettings resolved = settings == null ? AudioSettings.defaults() : settings;
        sfxGain = resolved.sfxGain();
        musicGain = resolved.musicGain();
        Clip playing = bgmClip;
        if (playing != null) {
            setGain(playing, toDecibels(musicGain));
        }
    }

    /** Plays an effect at the sliders' effects volume. Silent when that is zero. */
    public static void playSFX(String fileName) {
        if (sfxGain <= 0) {
            return;
        }
        playSFX(fileName, toDecibels(sfxGain));
    }

    public static void playSFX(String fileName, float volume) {
        try {
            Decoded decoded = SFX_CACHE.computeIfAbsent(fileName, SoundManager::decodeBytes);
            if (decoded == NOT_FOUND) return;
            Clip clip = AudioSystem.getClip();
            clip.open(decoded.format(), decoded.bytes(), 0, decoded.bytes().length);

            setGain(clip, volume);

            clip.start();
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });
        } catch (Exception e) {
            System.err.println("Error playing SFX: " + fileName);
            e.printStackTrace();
        }
    }

    /** Starts the background playlist at the sliders' music volume. */
    public static void startPlaylist() {
        startPlaylist(toDecibels(musicGain));
    }

    public static void startPlaylist(float volume) {
        startPlaylist(volume, PLAYLIST);
    }

    /** Starts the quiet title score from its first beat. */
    public static void startMenuMusic() {
        startPlaylist(toDecibels(musicGain), MENU_PLAYLIST);
    }

    /** Starts a playlist belonging to the selected temple. */
    public static void startGameplayMusic(TempleMap temple) {
        TempleMap resolved = temple == null ? TempleMap.defaultChoice() : temple;
        startPlaylist(toDecibels(musicGain), resolved.getMusicTracks());
    }

    private static void startPlaylist(float volume, String[] requestedTracks) {
        stopBGM();
        String[] tracks = requestedTracks == null || requestedTracks.length == 0
                ? PLAYLIST.clone() : requestedTracks.clone();
        playlistRunning = true;

        playlistThread = new Thread(() -> {
            int currentIndex = 0;
            System.out.println("[SoundManager] Playlist thread started.");
            while (playlistRunning) {
                String track = tracks[currentIndex];
                try {
                    System.out.println("[SoundManager] Loading track: " + track);
                    bgmClip = loadClip(track);
                    if (bgmClip == null) {
                        System.err.println("[SoundManager] Failed to load track: " + track);
                        break;
                    }

                    // Use a latch to block the thread until this specific clip finishes playing
                    CountDownLatch latch = new CountDownLatch(1);
                    bgmClip.addLineListener(event -> {
                        if (event.getType() == LineEvent.Type.STOP) {
                            latch.countDown();
                        }
                    });

                    System.out.println("[SoundManager] Playing: " + track);
                    // Each track fades in to the CURRENT music volume, so a
                    // slider moved mid-track carries over to the next one.
                    fadeIn(bgmClip, toDecibels(musicGain), 2000);
                    bgmClip.start();

                    // Wait here until the clip finishes playing naturally
                    latch.await();

                    bgmClip.close();

                    // Move to the next track, looping back to 0 at the end
                    currentIndex = (currentIndex + 1) % tracks.length;

                } catch (InterruptedException e) {
                    // Playlist was stopped/interrupted cleanly
                    break;
                } catch (Exception e) {
                    System.err.println("[SoundManager] Error in playlist loop: " + track);
                    e.printStackTrace();
                    break;
                }
            }
        });
        playlistThread.setDaemon(true);
        playlistThread.start();
    }

    public static void playBGM(String fileName, float volume) {
        stopBGM();
        try {
            bgmClip = loadClip(fileName);
            if (bgmClip == null) return;

            if (bgmClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gainControl = (FloatControl) bgmClip.getControl(FloatControl.Type.MASTER_GAIN);
                gainControl.setValue(volume);
            }
            bgmClip.loop(Clip.LOOP_CONTINUOUSLY);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void stopBGM() {
        playlistRunning = false;
        if (playlistThread != null) {
            playlistThread.interrupt();
            playlistThread = null;
        }
        if (bgmClip != null) {
            if (bgmClip.isRunning()) {
                bgmClip.stop();
            }
            bgmClip.close();
            bgmClip = null;
        }
    }

    /** Linear gain 0-1 to the decibels MASTER_GAIN takes. Zero is effectively silent. */
    static float toDecibels(double gain) {
        if (gain <= 0.0001) {
            return -80f;
        }
        return (float) (20.0 * Math.log10(gain));
    }

    /** Sets a clip's gain, clamped to what its line supports. */
    private static void setGain(Clip clip, float decibels) {
        if (clip == null || !clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        control.setValue(Math.max(control.getMinimum(), Math.min(control.getMaximum(), decibels)));
    }

    /** Marker for an effect whose file could not be read, so it is not retried every play. */
    private static final Decoded NOT_FOUND = new Decoded(null, new byte[0]);

    private static Decoded decodeBytes(String fileName) {
        try {
            URL url = SoundManager.class.getResource("/audio/" + fileName);
            if (url == null) {
                return NOT_FOUND;
            }
            try (AudioInputStream source = AudioSystem.getAudioInputStream(url)) {
                AudioFormat base = source.getFormat();
                AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                        base.getSampleRate(), 16, base.getChannels(),
                        base.getChannels() * 2, base.getSampleRate(), false);
                try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, source)) {
                    return new Decoded(target, pcm.readAllBytes());
                }
            }
        } catch (Exception e) {
            System.err.println("[SoundManager] Could not decode effect: " + fileName);
            return NOT_FOUND;
        }
    }

    private static Clip loadClip(String fileName) {
        try {
            URL url = SoundManager.class.getResource("/audio/" + fileName);
            if (url == null) {
                url = SoundManager.class.getClassLoader().getResource("audio/" + fileName);
            }

            if (url == null) {
                System.err.println("[SoundManager] ERROR: Audio file URL is null for: " + fileName);
                return null;
            }

            AudioInputStream sourceStream = AudioSystem.getAudioInputStream(url);
            AudioFormat baseFormat = sourceStream.getFormat();

            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false
            );

            AudioInputStream pcmStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = pcmStream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            byte[] audioBytes = out.toByteArray();

            Clip clip = AudioSystem.getClip();
            clip.open(targetFormat, audioBytes, 0, audioBytes.length);

            sourceStream.close();
            pcmStream.close();

            return clip;
        } catch (Exception e) {
            System.err.println("[SoundManager] EXCEPTION while loading clip: " + fileName);
            e.printStackTrace();
            return null;
        }
    }

    private static void fadeIn(Clip clip, float targetVolume, int durationMillis) {
        if (clip == null || !clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
        FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);

        new Thread(() -> {
            try {
                float startVolume = -40.0f;
                gainControl.setValue(startVolume);
                int steps = 20;
                int sleepTime = durationMillis / steps;
                float volumeStep = (targetVolume - startVolume) / steps;

                for (int i = 0; i < steps && clip.isRunning(); i++) {
                    startVolume += volumeStep;
                    gainControl.setValue(startVolume);
                    Thread.sleep(sleepTime);
                }
                gainControl.setValue(targetVolume);
            } catch (Exception e) {}
        }).start();
    }
}
