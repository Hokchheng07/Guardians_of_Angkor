package com.guardiansofangkor.engine;

import com.guardiansofangkor.i18n.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("IntroSequence — the opening beat")
class IntroSequenceTest {

    private static IntroSequence runUntil(IntroSequence intro,
                                          IntroSequence.Phase phase, int limit) {
        for (int i = 0; i < limit && intro.getPhase() != phase; i++) {
            intro.update();
        }
        return intro;
    }

    @Test
    @DisplayName("starts on the loading phase")
    void startsLoading() {
        IntroSequence intro = new IntroSequence(Difficulty.EASY);

        assertEquals(IntroSequence.Phase.LOADING, intro.getPhase());
        assertTrue(intro.isActive());
        assertFalse(intro.isDone());
    }

    @Test
    @DisplayName("loading progress climbs from zero to one")
    void loadingProgressClimbs() {
        IntroSequence intro = new IntroSequence();

        assertEquals(0.0, intro.getLoadingProgress(), 0.0001);

        double previous = -1;
        while (intro.getPhase() == IntroSequence.Phase.LOADING) {
            intro.update();
            double progress = intro.getLoadingProgress();
            assertTrue(progress >= previous, "progress must not go backwards");
            assertTrue(progress <= 1.0);
            previous = progress;
        }
        assertEquals(1.0, intro.getLoadingProgress(), 0.0001);
    }

    @Test
    @DisplayName("counts three, two, one before going")
    void countsDownFromThree() {
        IntroSequence intro = runUntil(new IntroSequence(),
                IntroSequence.Phase.COUNTDOWN, 600);

        assertEquals(3, intro.getCount());

        boolean sawTwo = false;
        boolean sawOne = false;
        for (int i = 0; i < 600 && intro.getPhase() == IntroSequence.Phase.COUNTDOWN; i++) {
            intro.update();
            if (intro.getCount() == 2) {
                sawTwo = true;
            }
            if (intro.getCount() == 1) {
                sawOne = true;
            }
        }

        assertTrue(sawTwo, "should show 2");
        assertTrue(sawOne, "should show 1");
        assertEquals(IntroSequence.Phase.GO, intro.getPhase());
    }

    @Test
    @DisplayName("reaches DONE and stays there")
    void reachesDoneAndStays() {
        IntroSequence intro = new IntroSequence();

        for (int i = 0; i < 2000; i++) {
            intro.update();
        }

        assertEquals(IntroSequence.Phase.DONE, intro.getPhase());
        assertTrue(intro.isDone());
        assertFalse(intro.isActive());

        intro.update();
        assertEquals(IntroSequence.Phase.DONE, intro.getPhase(), "DONE is terminal");
    }

    @Test
    @DisplayName("the whole beat is short enough not to annoy")
    void beatIsShort() {
        IntroSequence intro = new IntroSequence();

        int ticks = 0;
        while (intro.isActive() && ticks < 10_000) {
            intro.update();
            ticks++;
        }

        assertTrue(ticks < 60 * 5,
                "the opening should take under five seconds, took " + ticks + " ticks");
        assertTrue(ticks > 60, "but long enough to read, took " + ticks + " ticks");
    }

    @Test
    @DisplayName("beat progress stays within range in every phase")
    void beatProgressStaysInRange() {
        IntroSequence intro = new IntroSequence();

        for (int i = 0; i < 500; i++) {
            intro.update();
            double progress = intro.getBeatProgress();
            assertTrue(progress >= 0 && progress <= 1.0,
                    "beat progress escaped range: " + progress);
        }
    }

    @Test
    @DisplayName("the loading label names the tier")
    void labelNamesTheTier() {
        assertTrue(new IntroSequence(Difficulty.MEDIUM).getLoadingLabel()
                .contains("Medium"));
        assertTrue(new IntroSequence(Difficulty.EASY).getLoadingLabel()
                .contains("Easy"));
    }

    @Test
    @DisplayName("skip jumps straight to done")
    void skipEndsIt() {
        IntroSequence intro = new IntroSequence();
        intro.skip();

        assertTrue(intro.isDone());
        assertFalse(intro.isActive());
    }

    // ---- integration with GameState ---------------------------------------

    @Test
    @DisplayName("the simulation is frozen until the intro finishes")
    void simulationIsFrozenDuringIntro() {
        GameState state = new GameState(Language.ENGLISH, Difficulty.EASY);

        assertTrue(state.isIntroActive());

        // Run through the whole intro; nothing should spawn and no time passes.
        while (state.isIntroActive()) {
            state.update();
            assertEquals(0, state.getElapsedTicks(),
                    "the countdown must not count against the player's WPM");
            assertTrue(state.getEnemies().isEmpty(),
                    "nothing should walk before the count reaches one");
        }

        state.update();
        assertTrue(state.getElapsedTicks() > 0, "and then play begins");
    }

    @Test
    @DisplayName("typing is inert during the intro")
    void typingIsInertDuringIntro() {
        GameState state = new GameState(Language.ENGLISH, Difficulty.EASY);

        assertTrue(state.isIntroActive());
        assertEquals(com.guardiansofangkor.matching.MatchStatus.EMPTY,
                state.handleInput("ash").status());
    }

    @Test
    @DisplayName("a restart earns a fresh countdown")
    void restartReplaysTheIntro() {
        GameState state = new GameState(Language.ENGLISH, Difficulty.EASY);
        state.skipIntro();
        assertFalse(state.isIntroActive());

        state.restart();

        assertTrue(state.isIntroActive(),
                "the player should never be dropped into a wave already moving");
    }

    @Test
    @DisplayName("switching tier restarts with that tier's intro")
    void restartWithTierAdoptsIt() {
        GameState state = new GameState(Language.ENGLISH, Difficulty.EASY);

        state.restartWith(Difficulty.MEDIUM);

        assertEquals(Difficulty.MEDIUM, state.getDifficulty());
        assertTrue(state.isIntroActive());
        assertTrue(state.getIntro().getLoadingLabel().contains("Medium"));
    }
}
