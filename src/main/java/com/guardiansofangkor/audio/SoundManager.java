package com.guardiansofangkor.audio;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.concurrent.CountDownLatch;

public class SoundManager {

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

    private SoundManager() {}

    public static void playSFX(String fileName, float volume) {
        try {
            Clip clip = loadClip(fileName);
            if (clip == null) return;

            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                gainControl.setValue(volume);
            }

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

    public static void playSFX(String fileName) {
        playSFX(fileName, 0.0f);
    }

    public static void startPlaylist(float volume) {
        stopBGM();
        playlistRunning = true;

        playlistThread = new Thread(() -> {
            int currentIndex = 0;
            System.out.println("[SoundManager] Playlist thread started.");
            while (playlistRunning) {
                String track = PLAYLIST[currentIndex];
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
                    fadeIn(bgmClip, volume, 2000);
                    bgmClip.start();

                    // Wait here until the clip finishes playing naturally
                    latch.await();

                    bgmClip.close();

                    // Move to the next track, looping back to 0 at the end
                    currentIndex = (currentIndex + 1) % PLAYLIST.length;

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