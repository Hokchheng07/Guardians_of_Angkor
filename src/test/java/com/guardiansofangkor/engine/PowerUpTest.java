package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.ApproachPath;
import com.guardiansofangkor.entities.Enemy;
import com.guardiansofangkor.entities.EnemyType;
import com.guardiansofangkor.entities.PowerUp;
import com.guardiansofangkor.entities.PowerUpType;
import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.util.GameConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Power-ups — drops, claiming and what each boon does")
class PowerUpTest {

    private static GameState playing() {
        GameState state = new GameState(Language.ENGLISH, Difficulty.EASY, new Random(4));
        state.skipIntro();
        return state;
    }

    /** A walker part-way down the plaza, far enough back not to breach at once. */
    private static Enemy walker(String word) {
        return new Enemy(EnemyType.BEISACH, ApproachPath.GROUND_DIAGONAL, word,
                400, 1, 0.5);
    }

    /** A type that is allowed to leave a boon behind. */
    private static Enemy heavy(String word) {
        return new Enemy(EnemyType.PRET, ApproachPath.GROUND_DIAGONAL, word,
                400, 1, 0.5);
    }

    /** A walker dropped exactly on the breach point, to test what happens there. */
    private static Enemy atTheGate(String word) {
        return new Enemy(EnemyType.BEISACH, ApproachPath.GROUND_FLANK, word,
                0, 1, 0.5);
    }

    // ---- the pickup itself -------------------------------------------------

    @Test
    @DisplayName("a fresh pickup is typeable and lapses on schedule")
    void pickupLapses() {
        PowerUp boon = new PowerUp(PowerUpType.MEND, "orb", 200, 400, 30);

        assertTrue(boon.isActive());
        for (int i = 0; i < 29; i++) {
            boon.update();
            assertFalse(boon.hasJustLapsed(), "lapsed early at tick " + i);
        }
        boon.update();

        assertTrue(boon.hasJustLapsed());
        assertFalse(boon.isActive());
    }

    @Test
    @DisplayName("the lapse flag is true for exactly one tick")
    void lapseFlagIsOneShot() {
        // A sticky flag would have GameState clear the player's lock, and the
        // renderer replay the miss puff, on every remaining frame.
        PowerUp boon = new PowerUp(PowerUpType.PURGE, "urn", 100, 300, 3);

        boon.update();
        boon.update();
        boon.update();
        assertTrue(boon.hasJustLapsed());

        boon.update();
        assertFalse(boon.hasJustLapsed(), "the lapse flag stuck");
    }

    @Test
    @DisplayName("a claimed pickup is timed from the claim, not from the drop")
    void claimStartsItsOwnClock() {
        PowerUp boon = new PowerUp(PowerUpType.MEND, "orb", 200, 400, 30);

        // Grabbed at the very last moment — it must still get its full flourish
        // rather than blinking out the instant it is earned.
        for (int i = 0; i < 29; i++) {
            boon.update();
        }
        boon.claim();

        assertFalse(boon.isExpired(GameConfig.DEFEAT_ANIMATION_TICKS));
        for (int i = 0; i < GameConfig.DEFEAT_ANIMATION_TICKS; i++) {
            boon.update();
        }
        assertTrue(boon.isExpired(GameConfig.DEFEAT_ANIMATION_TICKS));
    }

    @Test
    @DisplayName("a pickup drifts upward as it ages")
    void pickupDrifts() {
        PowerUp boon = new PowerUp(PowerUpType.SLOW_TIDE, "ash", 200, 500, 60);
        double start = boon.getY();

        for (int i = 0; i < 60; i++) {
            boon.update();
        }

        assertTrue(boon.getY() < start, "the icon should rise, not sink");
    }

    // ---- claiming ----------------------------------------------------------

    @Test
    @DisplayName("typing a pickup's word claims it")
    void typingClaimsAPickup() {
        GameState state = playing();
        state.addPowerUp(new PowerUp(PowerUpType.SLOW_TIDE, "orb", 400, 400));

        state.handleInput("o");
        state.handleInput("or");
        state.handleInput("orb");

        assertEquals(1, state.getPowerUpsCollected());
        assertTrue(state.getPowerUpState().isSlowed());
    }

    @Test
    @DisplayName("a pickup preempts an enemy sharing its prefix")
    void pickupsOutrankEnemies() {
        // Reaching for a boon should cost you the word you were part-way
        // through — that tension is the point of collecting by typing.
        GameState state = playing();
        state.addEnemy(walker("orbit"));
        state.addPowerUp(new PowerUp(PowerUpType.MEND, "orb", 500, 400));

        state.handleInput("or");

        assertTrue(state.getResolver().getLockedTarget() instanceof PowerUp,
                "the shorter-deadline target should win the prefix");
    }

    // ---- what each boon does -----------------------------------------------

    @Test
    @DisplayName("Time Freeze stops the field without stopping the game")
    void timeFreezeStopsTheField() {
        GameState state = playing();
        Enemy marching = walker("stone");
        state.addEnemy(marching);
        state.applyPowerUp(PowerUpType.TIME_FREEZE);

        double before = marching.getX();
        for (int i = 0; i < 30; i++) {
            state.update();
        }

        assertEquals(before, marching.getX(), 0.0001, "a frozen enemy must not march");
        assertTrue(state.getElapsedTicks() > 0,
                "the run's clock keeps running — only the field is stopped");
    }

    @Test
    @DisplayName("Slow Tide halves the advance rather than stopping it")
    void slowTideSlowsTheField() {
        GameState fast = playing();
        GameState slow = playing();

        Enemy quick = walker("stone");
        Enemy dawdling = walker("stone");
        double origin = quick.getX();
        fast.addEnemy(quick);
        slow.addEnemy(dawdling);
        slow.applyPowerUp(PowerUpType.SLOW_TIDE);

        for (int i = 0; i < 40; i++) {
            fast.update();
            slow.update();
        }

        double fastTravel = quick.getX() - origin;
        double slowTravel = dawdling.getX() - origin;
        assertTrue(slowTravel > 0, "Slow Tide is not a freeze");
        assertTrue(slowTravel < fastTravel, "Slow Tide should be slower than normal");
    }

    @Test
    @DisplayName("a timed boon expires on its own")
    void timedBoonsExpire() {
        GameState state = playing();
        state.applyPowerUp(PowerUpType.TIME_FREEZE);
        assertTrue(state.getPowerUpState().isFrozen());

        int duration = (int) Math.round(PowerUpType.TIME_FREEZE.getBaseDurationTicks()
                * Difficulty.EASY.getPowerUpDurationScale());
        for (int i = 0; i <= duration + 2; i++) {
            state.update();
        }

        assertFalse(state.getPowerUpState().isFrozen(), "the freeze never ended");
    }

    @Test
    @DisplayName("collecting a second of the same boon refreshes rather than stacks")
    void timedBoonsRefresh() {
        PowerUpState boons = new PowerUpState();
        boons.activate(PowerUpType.SLOW_TIDE, Difficulty.MEDIUM);

        for (int i = 0; i < 60; i++) {
            boons.update();
        }
        int partway = boons.getActive().get(0).remainingTicks();

        boons.activate(PowerUpType.SLOW_TIDE, Difficulty.MEDIUM);
        int refreshed = boons.getActive().get(0).remainingTicks();

        assertTrue(refreshed > partway, "a second pickup should extend the boon");
        assertEquals(PowerUpType.SLOW_TIDE.getBaseDurationTicks(), refreshed,
                "and reset it to full, not add two durations together");
    }

    @Test
    @DisplayName("Mend restores a life but never past the maximum")
    void mendHealsWithACeiling() {
        GameState state = playing();
        state.loseLife();
        state.loseLife();
        assertEquals(GameConfig.STARTING_LIVES - 2, state.getLives());

        state.applyPowerUp(PowerUpType.MEND);
        assertEquals(GameConfig.STARTING_LIVES - 1, state.getLives());

        state.applyPowerUp(PowerUpType.MEND);
        state.applyPowerUp(PowerUpType.MEND);
        assertEquals(GameConfig.STARTING_LIVES, state.getLives(),
                "Mend must not bank lives above the starting count");
    }

    @Test
    @DisplayName("Purge sweeps the field and still scores the kills")
    void purgeClearsAndScores() {
        GameState state = playing();
        state.addEnemy(walker("stone"));
        state.addEnemy(walker("flame"));
        state.addEnemy(walker("shade"));

        int before = state.getScore();
        state.applyPowerUp(PowerUpType.PURGE);

        assertEquals(3, state.getEnemiesDefeated());
        assertTrue(state.getScore() > before,
                "the strongest boon in the game must not cost the player their score");
        for (Enemy enemy : state.getEnemies()) {
            assertFalse(enemy.isActive(), "a purged enemy is still standing");
        }
    }

    @Test
    @DisplayName("Purge advances level progress, so the bar does not stall")
    void purgeCountsAsProgress() {
        GameState state = playing();
        state.addEnemy(walker("stone"));
        state.addEnemy(walker("flame"));

        state.applyPowerUp(PowerUpType.PURGE);

        assertEquals(2, state.getResolvedThisLevel());
    }

    // ---- the Naga Shield ---------------------------------------------------

    @Test
    @DisplayName("a Naga Shield absorbs a breach instead of a life")
    void shieldAbsorbsABreach() {
        GameState state = playing();
        state.applyPowerUp(PowerUpType.NAGA_SHIELD);

        // Dropped right on the breach point, so it counts on the first tick.
        state.addEnemy(atTheGate("stone"));
        int lives = state.getLives();

        state.update();

        assertEquals(lives, state.getLives(), "the ward should have taken the hit");
        assertEquals(0, state.getPowerUpState().getShieldCharges(),
                "and been spent doing it");
    }

    @Test
    @DisplayName("without a shield the same breach costs a life")
    void breachCostsALifeWithoutAShield() {
        GameState state = playing();
        state.addEnemy(atTheGate("stone"));
        int lives = state.getLives();

        state.update();

        assertEquals(lives - 1, state.getLives());
    }

    @Test
    @DisplayName("shield charges stack but are capped")
    void shieldsAreCapped() {
        PowerUpState boons = new PowerUpState();

        for (int i = 0; i < GameConfig.MAX_SHIELD_CHARGES + 4; i++) {
            boons.addShield();
        }

        assertEquals(GameConfig.MAX_SHIELD_CHARGES, boons.getShieldCharges(),
                "a lucky streak of drops must not bank an unlosable run");
    }

    @Test
    @DisplayName("spending a shield you do not have reports failure")
    void consumingAnEmptyWardFails() {
        PowerUpState boons = new PowerUpState();
        assertFalse(boons.consumeShield());

        boons.addShield();
        assertTrue(boons.consumeShield());
        assertFalse(boons.consumeShield());
    }

    // ---- drop rates --------------------------------------------------------

    @Test
    @DisplayName("nothing drops on level one, so the opening stays legible")
    void noDropsOnTheFirstLevel() {
        for (Difficulty tier : Difficulty.values()) {
            assertEquals(0.0, PowerUpDrops.chanceFor(tier, 1, GameConfig.STARTING_LIVES),
                    0.0001, tier + " dropped a boon on level one");
        }
    }

    @Test
    @DisplayName("only the grounded heavies and the mini-boss leave boons")
    void onlyHeaviesDrop() {
        // A boon should be payment for a slow, long-word, genuinely hard kill —
        // not loot that falls out of the trash mob or off a passing swarm.
        assertTrue(EnemyType.YEAK.dropsBoons());
        assertTrue(EnemyType.PRET.dropsBoons());
        assertTrue(EnemyType.NAGA.dropsBoons());

        assertFalse(EnemyType.BEISACH.dropsBoons(), "the common walker is not a reward");
        assertFalse(EnemyType.AHP.dropsBoons(), "flyers never drop");
        assertFalse(EnemyType.STEC_KANTOAB.dropsBoons(), "flyers never drop");
    }

    @Test
    @DisplayName("an ineligible type never drops, however lucky the roll")
    void ineligibleTypesNeverDrop() {
        Random random = new Random(2);

        for (EnemyType type : EnemyType.values()) {
            if (type.dropsBoons()) {
                continue;
            }
            for (int i = 0; i < 500; i++) {
                assertFalse(PowerUpDrops.shouldDrop(type, Difficulty.EASY, 9, 1, random),
                        type + " dropped a boon");
            }
        }
    }

    @Test
    @DisplayName("an eligible type does drop, given enough kills")
    void eligibleTypesDoDrop() {
        Random random = new Random(2);

        boolean dropped = false;
        for (int i = 0; i < 500 && !dropped; i++) {
            dropped = PowerUpDrops.shouldDrop(EnemyType.PRET, Difficulty.EASY, 9, 1, random);
        }
        assertTrue(dropped, "a Pret should leave something behind eventually");
    }

    @Test
    @DisplayName("drops get more generous as lives run out")
    void mercyRises() {
        double healthy = PowerUpDrops.chanceFor(Difficulty.EASY, 5, GameConfig.STARTING_LIVES);
        double hurt = PowerUpDrops.chanceFor(Difficulty.EASY, 5, 1);

        assertTrue(hurt > healthy,
                "a run going badly should quietly get more to reach for");
    }

    @Test
    @DisplayName("the drop chance is capped however bad things get")
    void dropChanceIsCapped() {
        for (Difficulty tier : Difficulty.values()) {
            for (int lives = 0; lives <= GameConfig.STARTING_LIVES; lives++) {
                double chance = PowerUpDrops.chanceFor(tier, 40, lives);
                assertTrue(chance <= 0.30 + 0.0001,
                        tier + " at " + lives + " lives rolled " + chance);
            }
        }
    }

    @Test
    @DisplayName("Mend is withheld at full health rather than rolled and wasted")
    void mendIsNotOfferedAtFullHealth() {
        Random random = new Random(9);

        for (int i = 0; i < 300; i++) {
            PowerUpType rolled = PowerUpDrops.roll(GameConfig.STARTING_LIVES, false, random);
            assertFalse(rolled == PowerUpType.MEND,
                    "a boon that does nothing teaches the player to ignore boons");
        }
    }

    @Test
    @DisplayName("the ward is withheld once the player is already at the cap")
    void shieldIsNotOfferedWhenFull() {
        Random random = new Random(21);

        for (int i = 0; i < 300; i++) {
            assertFalse(PowerUpDrops.roll(1, true, random) == PowerUpType.NAGA_SHIELD);
        }
    }

    @Test
    @DisplayName("every boon has a chance of dropping")
    void everyBoonCanDrop() {
        Random random = new Random(5);
        java.util.EnumSet<PowerUpType> seen = java.util.EnumSet.noneOf(PowerUpType.class);

        for (int i = 0; i < 2000; i++) {
            seen.add(PowerUpDrops.roll(1, false, random));
        }

        assertEquals(PowerUpType.values().length, seen.size(),
                "some boon is configured so it can never appear: " + seen);
    }

    // ---- housekeeping ------------------------------------------------------

    @Test
    @DisplayName("a restart clears every boon")
    void restartClearsBoons() {
        GameState state = playing();
        state.applyPowerUp(PowerUpType.SLOW_TIDE);
        state.applyPowerUp(PowerUpType.NAGA_SHIELD);
        state.addPowerUp(new PowerUp(PowerUpType.MEND, "orb", 100, 200));

        state.restart();

        assertFalse(state.getPowerUpState().hasAnything());
        assertTrue(state.getPowerUps().isEmpty());
        assertEquals(0, state.getPowerUpsCollected());
    }

    @Test
    @DisplayName("the HUD strip stays empty until something is actually running")
    void stripIsEmptyByDefault() {
        assertFalse(new PowerUpState().hasAnything());
    }
}
