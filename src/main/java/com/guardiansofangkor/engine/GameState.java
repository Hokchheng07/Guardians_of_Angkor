package com.guardiansofangkor.engine;

import com.guardiansofangkor.entities.*;
import com.guardiansofangkor.i18n.Language;
import com.guardiansofangkor.i18n.WordBank;
import com.guardiansofangkor.matching.ResolveResult;
import com.guardiansofangkor.matching.TargetResolver;
import com.guardiansofangkor.matching.WordTarget;
import com.guardiansofangkor.save.SaveData;
import com.guardiansofangkor.util.GameConfig;
import com.guardiansofangkor.util.GraphemeCounter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class GameState {

    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<PowerUp> powerUps = new ArrayList<>();
    private final List<VisualEffect> effects = new ArrayList<>();
    private final TargetResolver resolver = new TargetResolver();
    private final WaveManager waveManager;

    /**
     * Not final: the Options screen can change the language between (or even
     * during) runs. Changed only via {@link #setLanguage(Language)}, which is
     * what keeps this field and the word bank it feeds in step.
     */
    private WordBank wordBank;
    private Language language;
    private int reamEysoShotIndex = 0;
    private int apsaraShotIndex = 0;
    private int apsaraCounterIndex = 0;

    /** Ticks since the boss last loosed a projectile; see BOSS_VENOM_MIN_GAP_TICKS. */
    private double ticksSinceBossVenom = Double.MAX_VALUE;

    /** Boss shots that fell due while the previous one was still too recent. */
    private int pendingBossVenom;

    /**
     * Shots loosed this session. The UI plays the bow sound when this rises;
     * the engine itself never touches audio, the same way it never touches
     * Swing, so the tests stay silent and deterministic.
     */
    private int shotsLoosed;
    private final Player player = new Player();

    private final PowerUpState powerUpState = new PowerUpState();
    private final ComboTracker combo = new ComboTracker();
    private BossFight boss;
    private final Random random;
    private Difficulty difficulty;

    /**
     * Who is defending the temple. Cosmetic — nothing in the simulation reads
     * it — but it lives here rather than in the renderer because it is part of
     * the run: it is saved with it, restored with it, and like the tier it is
     * only changed between runs.
     */
    private Hero hero = Hero.defaultChoice();

    /** The environment and score chosen for this run. */
    private TempleMap templeMap = TempleMap.defaultChoice();

    private boolean isSandbox;
    private IntroSequence intro = new IntroSequence();
    private int score;
    private int halfLives = GameConfig.STARTING_HALF_LIVES;
    private long elapsedTicks;
    private boolean running = true;
    private boolean gameOver;
    private boolean victory;
    private int powerUpsCollected;
    private boolean paused;

    private int charactersTyped;
    private int enemiesDefeated;
    private int projectilesIntercepted;
    private int resolvedThisLevel;
    private int lastLevelSeen;

    private int bestScore;
    private int bestLevel;
    private boolean levelJustCleared;
    private ResolveResult lastResult;

    private String bossBuffer = "";
    private boolean bufferInvalidated;
    private final Set<String> clearedTiers = new LinkedHashSet<>();

    public GameState() {
        this(Language.ENGLISH, Difficulty.defaultChoice());
    }

    public GameState(Language language) {
        this(language, Difficulty.defaultChoice());
    }

    public GameState(Language language, Difficulty difficulty) {
        this(language, difficulty, new Random());
    }

    public GameState(Language language, Difficulty difficulty, Random random) {
        this.language = language == null ? Language.ENGLISH : language;
        this.difficulty = difficulty == null ? Difficulty.defaultChoice() : difficulty;
        this.random = random == null ? new Random() : random;
        this.wordBank = new WordBank(this.language, this.random);
        this.waveManager = new WaveManager(this.wordBank, this.difficulty, this.random);
        this.intro = new IntroSequence(this.difficulty);
    }

    public void update() {
        levelJustCleared = false;

        if (paused || !running || gameOver) {
            return;
        }

        if (intro != null && intro.isActive()) {
            intro.update();
            return;
        }

        if (boss == null || !boss.isBriefing()) {
            elapsedTicks++;
        }

        double timeScale = powerUpState.getTimeScale() * languageSpeedScale();

        powerUpState.update();
        player.update();
        updateEffects();
        updatePowerUps();
        updateEnemies(timeScale);
        updateProjectiles(timeScale);
        updateBoss(timeScale);

        if (!gameOver && !victory && !isSandbox) {
            spawnFromWaveManager();
        }

        if (waveManager.getLevel() != lastLevelSeen) {
            lastLevelSeen = waveManager.getLevel();
            resolvedThisLevel = 0;
        }

        dropStaleBuffer();
    }

    private void dropStaleBuffer() {
        String buffer = getTypedBuffer();
        if (buffer.isEmpty() || matchesSomethingLive(buffer)) {
            return;
        }
        resolver.reset();
        bossBuffer = "";
        if (boss != null) {
            boss.clearTyping();
        }
        bufferInvalidated = true;
    }

    private boolean matchesSomethingLive(String buffer) {
        for (Enemy enemy : enemies) {
            if (enemy.isActive() && enemy.getWord().startsWith(buffer)) return true;
        }
        for (Projectile projectile : projectiles) {
            if (projectile.isActive() && projectile.getWord().startsWith(buffer)) return true;
        }
        for (PowerUp powerUp : powerUps) {
            if (powerUp.isActive() && powerUp.getWord().startsWith(buffer)) return true;
        }
        return boss != null && boss.isTyping() && boss.currentWord().startsWith(buffer);
    }

    public boolean consumeBufferInvalidated() {
        boolean invalidated = bufferInvalidated;
        bufferInvalidated = false;
        return invalidated;
    }

    private void updateEffects() {
        for (VisualEffect effect : effects) {
            effect.update();
        }
        effects.removeIf(VisualEffect::isExpired);
    }

    private void updateEnemies(double timeScale) {
        List<Enemy> newSummons = new ArrayList<>();

        for (Enemy enemy : enemies) {
            enemy.update(timeScale);
            if (enemy.isProjectileDue()) {
                throwProjectileFrom(enemy);
            }

            if (enemy.isSummonSpawnDue()) {
                for (int i = 0; i < 3; i++) {
                    Enemy ahp = waveManager.spawnSpecific(EnemyType.AHP, enemies);
                    if (ahp != null) {
                        double spawnX = enemy.getX() + ((i - 1) * 110);
                        double spawnY = enemy.getAnchorY()
                                - (enemy.getType().getTargetHeight() * enemy.depthScale() * 0.6)
                                + (Math.random() * 40 - 20);

                        ahp.redeployAt(spawnX, spawnY, GameConfig.TEMPLE_CENTER_X);
                        newSummons.add(ahp);
                    }
                }
                enemy.clearSummonSpawnDue();
            }
        }

        for (Enemy minion : newSummons) {
            enemies.add(minion);
            effects.add(new VisualEffect(
                    VisualEffect.Kind.SPAWN_POOF,
                    minion.getX(),
                    minion.getAnchorY() - minion.getType().getTargetHeight()
                            * minion.depthScale() * 0.35,
                    GameConfig.POOF_TICKS,
                    minion.depthScale()));
        }

        List<Enemy> breached = new ArrayList<>();
        for (Enemy enemy : enemies) {
            if (enemy.hasBreached()) {
                breached.add(enemy);
            }
        }
        for (Enemy enemy : breached) {
            enemies.remove(enemy);
            resolvedThisLevel++;
            absorbOrLoseLife(enemy.getX(), enemy.getAnchorY(), enemy.getType().breachDamage());
        }

        enemies.removeIf(e -> e.isExpired(GameConfig.DEFEAT_ANIMATION_TICKS));
    }

    private void updateProjectiles(double timeScale) {
        for (Projectile projectile : projectiles) {
            projectile.update(timeScale * GameConfig.PROJECTILE_SPEED_SCALE);
            if (projectile.hasJustLanded()) {
                absorbOrLoseLife(projectile.getX(), projectile.getY(), GameConfig.DAMAGE_PROJECTILE);
            }
        }
        projectiles.removeIf(p -> p.isExpired(GameConfig.DEFEAT_ANIMATION_TICKS));
    }

    private void updatePowerUps() {
        for (PowerUp powerUp : powerUps) {
            powerUp.update();
        }
        powerUps.removeIf(p -> p.isExpired(GameConfig.DEFEAT_ANIMATION_TICKS));
    }

    private void updateBoss(double timeScale) {
        if (boss == null) {
            return;
        }

        // Queued shots count as live, so an attack phase cannot end while one
        // is still waiting to be fired.
        boss.reportField(countLiveBossAttacks() + pendingBossVenom);
        boss.update(timeScale);

        ticksSinceBossVenom += timeScale;
        if (boss.isVenomDue()) {
            pendingBossVenom++;
        }
        if (pendingBossVenom > 0 && bossVenomReady()) {
            pendingBossVenom--;
            spitVenom();
            ticksSinceBossVenom = 0;
        }
        if (boss.isMinionDue()) {
            summonMinion();
        }
        if (boss.isPhaseJustEnded()) {
            clearBossField();
        }

        if (boss.isFinished()) {
            if (isSandbox) {
                boss = null;
                bossBuffer = "";
                resolver.reset();
                clearBossField();
            } else if (waveManager.getLevel() >= difficulty.getFinalLevel()) {
                // If we beat the final milestone (Wave 20/40/50), we win!
                declareVictory();
            } else {
                // MILESTONE CLEARED! Clean up the boss and resume the waves!
                boss = null;
                bossBuffer = "";
                resolver.reset();
                clearBossField();
                waveManager.resumeAfterBoss();
            }
        }
    }

    private void spawnNagaHead() {
        int headsToSpawn = 2;
        for (int i = 0; i < headsToSpawn; i++) {
            List<String> reserved = new ArrayList<>(boss.remainingWords());
            for (Projectile p : projectiles) reserved.add(p.getWord());
            for (Enemy e : enemies) reserved.addAll(e.getAllWords());

            Enemy head = waveManager.spawnBossMinion(EnemyType.NAGA_HEAD, enemies, reserved);
            if (head != null) {
                double spreadX = GameConfig.TEMPLE_CENTER_X + (random.nextDouble() * 600 - 300);
                double spawnY = GameConfig.GROUND_LINE_Y - 350 + (random.nextDouble() * 100);

                head.redeployAt(spreadX, spawnY, GameConfig.TEMPLE_CENTER_X);
                enemies.add(head);

                effects.add(new VisualEffect(
                        VisualEffect.Kind.SPAWN_POOF,
                        spreadX, spawnY,
                        GameConfig.POOF_TICKS, head.depthScale() * 1.5));
            }
        }
    }

    private int countLiveBossAttacks() {
        int live = 0;
        for (Enemy enemy : enemies) {
            if (enemy.isActive()) live++;
        }
        for (Projectile projectile : projectiles) {
            if (projectile.isActive() && projectile.getKind() == Projectile.Kind.VENOM) live++;
        }
        return live;
    }

    private boolean bossVenomReady() {
        return ticksSinceBossVenom >= GameConfig.BOSS_VENOM_MIN_GAP_TICKS;
    }

    private void clearBossField() {
        pendingBossVenom = 0;
        for (Enemy enemy : enemies) {
            if (enemy.isActive()) enemy.defeat();
        }
        for (Projectile projectile : projectiles) {
            if (projectile.isActive()) projectile.intercept();
        }
        resolver.reset();
        bossBuffer = "";
    }

    private void summonMinion() {
        List<String> reserved = new ArrayList<>(boss.remainingWords());
        for (Projectile projectile : projectiles) {
            reserved.add(projectile.getWord());
        }

        Enemy minion = null;

        if (boss.getType().name().equals("ABYSSAL_NAGA")) {
            spawnNagaHead();
            return;
        }

        if (boss.getType().name().equals("YAKSHA_COMMANDER")
                || boss.getType().name().equals("KRONG_REAP")) {
            EnemyType[] elites = {EnemyType.ARAK, EnemyType.SPLITTER, EnemyType.GARUDA, EnemyType.NAGA_HEAD};
            EnemyType eliteType = elites[random.nextInt(elites.length)];
            minion = waveManager.spawnBossMinion(eliteType, enemies, reserved);
        } else {
            minion = waveManager.spawnBossMinion(enemies, reserved);
        }

        if (minion == null) {
            return;
        }

        enemies.add(minion);
        effects.add(new VisualEffect(
                VisualEffect.Kind.SPAWN_POOF,
                minion.getX(),
                minion.getAnchorY() - minion.getType().getTargetHeight()
                        * minion.depthScale() * 0.35,
                GameConfig.POOF_TICKS,
                minion.depthScale()));
    }

    private void beginBossFight() {
        if (boss != null) return;

        // GAUNTLET UPGRADE: Grab the specific boss for this level milestone!
        EnemyType bossType = difficulty.getMilestoneBoss(waveManager.getLevel());
        if (bossType == null) {
            declareVictory();
            return;
        }

        // Short fights before level 30, the tier's full length from there on —
        // see Difficulty.FULL_LENGTH_BOSS_LEVEL.
        int level = waveManager.getLevel();
        int sentencesPerParagraph = difficulty.bossSentencesPerParagraphAt(level);
        List<String> script = wordBank.bossScript(
                difficulty.getWordBankKey(),
                difficulty.bossParagraphCountAt(level),
                sentencesPerParagraph,
                random);
        boss = new BossFight(bossType, script, sentencesPerParagraph, difficulty, random);

        powerUps.clear();
        resolver.reset();
        effects.add(new VisualEffect(
                VisualEffect.Kind.SPAWN_POOF,
                GameConfig.TEMPLE_CENTER_X,
                GameConfig.GROUND_LINE_Y - GameConfig.BOSS_HEIGHT * 0.4,
                GameConfig.POOF_TICKS * 3, 3.0));
    }

    private void spitVenom() {
        if (boss.getType().name().equals("ABYSSAL_NAGA")) {
            spawnNagaHead();
            return;
        }

        List<String> taken = new ArrayList<>(boss.remainingWords());
        for (Projectile projectile : projectiles) taken.add(projectile.getWord());
        for (Enemy minion : enemies) taken.addAll(minion.getAllWords());

        String onNow = boss.isTyping() ? boss.currentWord() : "";
        for (String word : List.copyOf(wordBank.getActionWords())) {
            if (!onNow.isEmpty() && (word.startsWith(onNow) || onNow.startsWith(word))) {
                taken.add(word);
            }
        }

        double spawnX = boss.getVenomOriginX();
        double spawnY = boss.getVenomOriginY();

        if (boss.getType().name().equals("YAKSHA_COMMANDER")) {
            spawnX += 45;
            spawnY -= 120;
        }

        if (boss.getType().name().equals("REAM_EYSO")) {
            double[] offsetsX = {-200, 200, -100, 100, 0};
            double[] offsetsY = {-30, -30, 20, 20, -50};
            double[] speedMods = {1.1, 0.7, 0.9, 0.8, 0.6};

            int index = reamEysoShotIndex % 5;
            reamEysoShotIndex++;

            double startX = spawnX + offsetsX[index];
            double startY = spawnY - 60 + offsetsY[index];

            effects.add(new VisualEffect(
                    VisualEffect.Kind.WARD_BREAK,
                    startX, startY,
                    GameConfig.POWERUP_FLASH_TICKS, 3.0));

            String word = wordBank.venomWord(taken);
            taken.add(word);

            double destX = player.getX();
            double destY = GameConfig.PLAYER_FEET_Y - GameConfig.PLAYER_HEIGHT * 0.5;

            int flightTicks = (int)(boss.venomFlightTicks() * speedMods[index]);

            projectiles.add(new Projectile(
                    word,
                    startX, startY,
                    destX, destY,
                    flightTicks,
                    Projectile.Kind.VENOM));
            return;
        }

        if (boss.getType().name().equals("CORRUPTED_APSARA")) {
            double[] offsetsX = {-150, 150, -75, 75, 0};
            double[] offsetsY = {-20, -20, 10, 10, -40};
            double[] speedMods = {1.0, 0.7, 0.9, 0.8, 0.6};

            int index = apsaraShotIndex % 5;
            apsaraShotIndex++;

            double startX = spawnX + offsetsX[index];
            double startY = spawnY - 40 + offsetsY[index];

            effects.add(new VisualEffect(
                    VisualEffect.Kind.SPAWN_POOF,
                    startX, startY,
                    GameConfig.POWERUP_FLASH_TICKS, 3.0));

            String word = wordBank.venomWord(taken);
            taken.add(word);

            double destX = player.getX();
            double destY = GameConfig.PLAYER_FEET_Y - GameConfig.PLAYER_HEIGHT * 0.5;

            int flightTicks = (int)(boss.venomFlightTicks() * speedMods[index]);

            projectiles.add(new Projectile(
                    word,
                    startX, startY,
                    destX, destY,
                    flightTicks,
                    Projectile.Kind.VENOM));
            return;
        }

        projectiles.add(new Projectile(
                wordBank.venomWord(taken),
                spawnX, spawnY,
                player.getX(), GameConfig.PLAYER_FEET_Y - GameConfig.PLAYER_HEIGHT * 0.5,
                boss.venomFlightTicks(),
                Projectile.Kind.VENOM));
    }

    private ResolveResult handleBossInput(String typedSoFar) {
        String buffer = typedSoFar == null ? "" : typedSoFar;
        bossBuffer = buffer;
        if (buffer.isEmpty()) {
            boss.clearTyping();
            return ResolveResult.EMPTY_RESULT;
        }

        List<Projectile> venom = projectilesOfKind(Projectile.Kind.VENOM);

        for (Projectile bolt : venom) {
            if (bolt.isActive() && bolt.getWord().equals(buffer)) {
                return deflectVenom(bolt, buffer);
            }
        }
        for (Enemy minion : enemies) {
            if (minion.isActive() && minion.getWord().equals(buffer)) {
                return strikeMinion(minion, buffer);
            }
        }
        if (!boss.isTyping()) {
            return trackFieldOnly(buffer, venom);
        }

        if (buffer.equals(boss.currentWord() + " ")) {
            return advanceVerse(buffer);
        }

        List<WordTarget> alive = new ArrayList<>();
        for (Projectile bolt : venom) {
            if (bolt.isActive() && bolt.getWord().startsWith(buffer)) {
                bolt.flashHit(GameConfig.HIT_FLASH_TICKS);
                alive.add(bolt);
            }
        }
        for (Enemy minion : enemies) {
            if (minion.isActive() && minion.getWord().startsWith(buffer)) {
                minion.flashHit(GameConfig.HIT_FLASH_TICKS);
                alive.add(minion);
            }
        }
        boolean matchesVerse = boss.currentWord().startsWith(buffer);
        if (matchesVerse) {
            alive.add(boss);
        }

        if (alive.isEmpty()) {
            resolver.noteExternalInput(false);
            resolver.noteExternalCandidates(List.of());
            boss.restartWord();
            bossBuffer = "";
            return ResolveResult.typo("");
        }

        resolver.noteExternalInput(true);
        resolver.noteExternalCandidates(alive);
        boss.trackTyping(matchesVerse ? buffer : "");
        return ResolveResult.locked(alive.get(0), buffer);
    }

    private ResolveResult trackFieldOnly(String buffer, List<Projectile> venom) {
        List<WordTarget> alive = new ArrayList<>();
        for (Projectile bolt : venom) {
            if (bolt.isActive() && bolt.getWord().startsWith(buffer)) {
                bolt.flashHit(GameConfig.HIT_FLASH_TICKS);
                alive.add(bolt);
            }
        }
        for (Enemy minion : enemies) {
            if (minion.isActive() && minion.getWord().startsWith(buffer)) {
                minion.flashHit(GameConfig.HIT_FLASH_TICKS);
                alive.add(minion);
            }
        }

        if (alive.isEmpty()) {
            resolver.noteExternalInput(false);
            resolver.noteExternalCandidates(List.of());
            bossBuffer = "";
            return ResolveResult.typo("");
        }
        resolver.noteExternalInput(true);
        resolver.noteExternalCandidates(alive);
        return ResolveResult.locked(alive.get(0), buffer);
    }

    private ResolveResult strikeMinion(Enemy minion, String typedSoFar) {
        bossBuffer = "";
        boss.clearTyping();
        handleCompleted(minion);
        return ResolveResult.completed(minion, typedSoFar);
    }

    private ResolveResult deflectVenom(Projectile bolt, String typedSoFar) {
        bossBuffer = "";
        boss.clearTyping();
        bolt.intercept();
        projectilesIntercepted++;
        charactersTyped += GraphemeCounter.count(bolt.getWord());
        score += scoreForProjectile(bolt);

        player.tryFire();
        spawnArrowAt(bolt.getX(), bolt.getY(), false, false);
        resolver.reset();
        return ResolveResult.completed(bolt, typedSoFar);
    }

    private void handleCompleted(WordTarget target) {
        if (target instanceof Enemy enemy) {
            charactersTyped += GraphemeCounter.count(enemy.getWord());
            score += scoreForEnemy(enemy);

            if (enemy.hasMoreWords()) {
                enemy.advanceChain();
                enemy.flashHit(GameConfig.HIT_FLASH_TICKS * 2);
            } else {
                enemy.defeat();
                enemiesDefeated++;
                resolvedThisLevel++;
                maybeDropPowerUp(enemy);

                if (enemy.getType() == EnemyType.SPLITTER) {
                    triggerSplitterBurst(enemy);
                }
            }
        } else if (target instanceof Projectile projectile) {
            projectile.intercept();
            projectilesIntercepted++;
            charactersTyped += GraphemeCounter.count(projectile.getWord());
            score += scoreForProjectile(projectile);
        } else if (target instanceof PowerUp powerUp) {
            charactersTyped += GraphemeCounter.count(powerUp.getWord());
            claimPowerUp(powerUp);
        }

        player.tryFire();
        spawnArrowAt(aimPointX(target), aimPointY(target), false, true);

        resolver.reset();
    }

    private void triggerGarudaDash() {
        for (Enemy enemy : enemies) {
            if (enemy.getType() == EnemyType.GARUDA && enemy.isActive()) {
                enemy.dashForward(50.0);
                effects.add(new VisualEffect(
                        VisualEffect.Kind.IMPACT,
                        enemy.getX(),
                        enemy.getAnchorY() - enemy.getType().getTargetHeight() * enemy.depthScale() * 0.5,
                        GameConfig.POOF_TICKS,
                        enemy.depthScale() * 1.5));
            }
        }
    }

    private void triggerApsaraCounterAttack() {
        // A burst of typos would otherwise throw a bolt per keystroke.
        if (boss == null || !bossVenomReady()) return;
        ticksSinceBossVenom = 0;

        double spawnX = GameConfig.TEMPLE_CENTER_X;
        double spawnY = 250;

        double[] offsetsX = {-150, 150, -75, 75, 0};
        double[] offsetsY = {-20, -20, 10, 10, -40};
        double[] speedMods = {1.1, 0.7, 0.9, 0.8, 0.6};

        int index = apsaraCounterIndex % 5;
        apsaraCounterIndex++;

        double startX = spawnX + offsetsX[index];
        double startY = spawnY + offsetsY[index];

        effects.add(new VisualEffect(
                VisualEffect.Kind.ENRAGE_BURST,
                startX, startY,
                GameConfig.POWERUP_FLASH_TICKS, 3.0));

        List<String> taken = collectWordsInPlay();
        String word = wordBank.venomWord(taken);

        double destX = player.getX();
        double destY = GameConfig.PLAYER_FEET_Y - GameConfig.PLAYER_HEIGHT * 0.5;

        int baseTicks = (int)(boss.venomFlightTicks() * 0.7);
        int flightTicks = (int)(baseTicks * speedMods[index]);

        projectiles.add(new Projectile(
                word,
                startX, startY,
                destX, destY,
                flightTicks,
                Projectile.Kind.VENOM));
    }

    private void triggerSplitterBurst(Enemy enemy) {
        double spawnY = enemy.getAnchorY()
                - (enemy.getType().getTargetHeight() * enemy.depthScale() * 0.5);

        int flightTicks = 150;

        for (int i = 0; i < 2; i++) {
            String word = wordBank.projectileWord(collectWordsInPlay());
            double offsetX = (i == 0) ? -60 : 60;

            projectiles.add(new Projectile(
                    word,
                    enemy.getX() + offsetX, spawnY,
                    GameConfig.TEMPLE_CENTER_X, GameConfig.GROUND_LINE_Y - 40,
                    flightTicks));
        }

        effects.add(new VisualEffect(
                VisualEffect.Kind.WARD_BREAK,
                enemy.getX(), spawnY,
                GameConfig.POWERUP_FLASH_TICKS, enemy.depthScale()));
    }

    private ResolveResult advanceVerse(String typedSoFar) {
        String word = boss.currentWord();
        BossFight.Result result = boss.submit(typedSoFar);

        charactersTyped += GraphemeCounter.count(word) + 1;
        score += scoreForVerseWord(word);
        bossBuffer = "";
        resolver.reset();

        if (result == BossFight.Result.STAGE_CLEARED
                || result == BossFight.Result.PARAGRAPH_CLEARED
                || result == BossFight.Result.DEFEATED) {
            player.tryFire();
            spawnArrowAt(GameConfig.TEMPLE_CENTER_X, boss.getVenomOriginY(), true, true);
        }
        return ResolveResult.completed(boss, typedSoFar);
    }

    private int scoreForVerseWord(String word) {
        int base = GraphemeCounter.count(word) * 20;
        return (int) Math.round(base
                * DifficultyCurve.scoreMultiplier(getLevel()) * combo.getMultiplier());
    }

    private void absorbOrLoseLife(double x, double y, int halves) {
        if (powerUpState.consumeShield()) {
            powerUpState.markFired(PowerUpType.NAGA_SHIELD);
            effects.add(new VisualEffect(
                    VisualEffect.Kind.WARD_BREAK, x, y, GameConfig.POWERUP_FLASH_TICKS, 1.0));
            return;
        }
        damage(halves);
    }

    private void spawnFromWaveManager() {
        if (boss != null) {
            return;
        }
        // GAUNTLET UPGRADE: Check if WaveManager parked at a milestone
        if (waveManager.isBossMilestoneDue() && enemies.isEmpty()) {
            beginBossFight();
            return;
        }

        List<Enemy> spawned = waveManager.update(enemies);
        for (Enemy enemy : spawned) {
            enemies.add(enemy);
            effects.add(new VisualEffect(
                    VisualEffect.Kind.SPAWN_POOF,
                    enemy.getX(),
                    enemy.getAnchorY() - enemy.getType().getTargetHeight()
                            * enemy.depthScale() * 0.35,
                    GameConfig.POOF_TICKS,
                    enemy.depthScale()));
        }
        if (waveManager.isLevelCleared()) {
            levelJustCleared = true;
        }
    }

    private void throwProjectileFrom(Enemy enemy) {
        String word = wordBank.projectileWord(collectWordsInPlay());
        projectiles.add(new Projectile(
                word,
                enemy.getThrowOriginX(), enemy.getThrowOriginY(),
                GameConfig.TEMPLE_CENTER_X, GameConfig.GROUND_LINE_Y - 40,
                DifficultyCurve.projectileFlightTicks(getLevel(), difficulty)));
    }

    private List<Projectile> projectilesOfKind(Projectile.Kind kind) {
        List<Projectile> matching = new ArrayList<>(projectiles.size());
        for (Projectile projectile : projectiles) {
            if (projectile.getKind() == kind) {
                matching.add(projectile);
            }
        }
        return matching;
    }

    private List<String> collectWordsInPlay() {
        List<String> words = new ArrayList<>();
        for (Enemy enemy : enemies) {
            words.addAll(enemy.getAllWords());
        }
        for (Projectile projectile : projectiles) {
            words.add(projectile.getWord());
        }
        for (PowerUp powerUp : powerUps) {
            words.add(powerUp.getWord());
        }
        return words;
    }

    public ResolveResult handleInput(String typedSoFar) {
        if (gameOver || paused || isIntroActive()) {
            return ResolveResult.EMPTY_RESULT;
        }

        if (boss != null && !boss.isFighting()) {
            return ResolveResult.EMPTY_RESULT;
        }

        if (boss != null && boss.isFighting()) {
            ResolveResult bossResult = handleBossInput(typedSoFar);
            lastResult = bossResult;
            applyToCombo(bossResult);

            if (bossResult.status().name().equals("TYPO")) {
                triggerGarudaDash();

                if (boss.getType().name().equals("CORRUPTED_APSARA")
                        || boss.getType().name().equals("KRONG_REAP")) {
                    triggerApsaraCounterAttack();
                }
            }
            return bossResult;
        }

        ResolveResult result = resolver.submit(typedSoFar,
                projectilesOfKind(Projectile.Kind.CURSED_BOLT), powerUps, enemies);
        lastResult = result;

        applyToCombo(result);

        switch (result.status()) {
            case COMPLETED -> handleCompleted(result.target());
            case LOCKED, AMBIGUOUS -> handleProgress(result.candidates());
            case TYPO -> triggerGarudaDash();
            case EMPTY -> {}
        }
        return result;
    }

    private void applyToCombo(ResolveResult result) {
        switch (result.status()) {
            case COMPLETED -> combo.noteCleanWord();
            case TYPO -> combo.breakStreak();
            default -> {}
        }
    }

    private void handleProgress(List<WordTarget> candidates) {
        for (WordTarget candidate : candidates) {
            if (candidate instanceof Enemy enemy) {
                enemy.flashHit(GameConfig.HIT_FLASH_TICKS);
                if (enemy.getType() == EnemyType.CHARGER && !enemy.isSprinting()) {
                    enemy.triggerSprint();
                    effects.add(new VisualEffect(
                            VisualEffect.Kind.ENRAGE_BURST,
                            enemy.getX(),
                            enemy.getAnchorY() - 30,
                            GameConfig.POOF_TICKS + 10,
                            1.0));
                }
            } else if (candidate instanceof Projectile projectile) {
                projectile.flashHit(GameConfig.HIT_FLASH_TICKS);
            }
        }

        boolean fired = player.tryFire();
        if (fired && candidates.size() == 1) {
            WordTarget only = candidates.get(0);
            spawnArrowAt(aimPointX(only), aimPointY(only), false, false);
        }
    }

    private static double aimPointX(WordTarget target) {
        if (target instanceof Enemy enemy) return enemy.getX();
        if (target instanceof Projectile projectile) return projectile.getX();
        if (target instanceof PowerUp powerUp) return powerUp.getX();
        return GameConfig.TEMPLE_CENTER_X;
    }

    private static double aimPointY(WordTarget target) {
        if (target instanceof Enemy enemy) {
            return enemy.getAnchorY()
                    - enemy.getType().getTargetHeight() * enemy.depthScale() * 0.5;
        }
        if (target instanceof Projectile projectile) return projectile.getY();
        if (target instanceof PowerUp powerUp) return powerUp.getY();
        return GameConfig.GROUND_LINE_Y;
    }

    private void maybeDropPowerUp(Enemy enemy) {
        if (boss != null) return;
        if (!PowerUpDrops.shouldDrop(enemy.getType(), difficulty, getLevel(), getLives(), random)) {
            return;
        }
        boolean shieldsFull = powerUpState.getShieldCharges() >= GameConfig.MAX_SHIELD_CHARGES;
        PowerUpType type = PowerUpDrops.roll(getLives(), shieldsFull, random);

        String word = wordBank.pickupWord(collectWordsInPlay());
        double dropY = enemy.getAnchorY() - enemy.getType().getTargetHeight() * enemy.depthScale() * 0.55;

        powerUps.add(new PowerUp(type, word, enemy.getX(), dropY));
    }

    private void claimPowerUp(PowerUp powerUp) {
        powerUp.claim();
        powerUpsCollected++;
        score += GameConfig.TARGET_FPS;

        effects.add(new VisualEffect(
                VisualEffect.Kind.BOON_CLAIMED,
                powerUp.getX(), powerUp.getY(),
                GameConfig.POWERUP_FLASH_TICKS, 1.0));

        applyPowerUp(powerUp.getType());
    }

    void applyPowerUp(PowerUpType type) {
        if (type == null) return;
        switch (type) {
            case TIME_FREEZE, SLOW_TIDE -> powerUpState.activate(type, difficulty);
            case NAGA_SHIELD -> powerUpState.addShield();
            case PURGE -> {
                purgeField();
                powerUpState.markFired(type);
            }
            case MEND -> {
                halfLives = Math.min(GameConfig.STARTING_HALF_LIVES,
                        halfLives + GameConfig.HALVES_PER_LIFE);
                powerUpState.markFired(type);
            }
        }
    }

    private void purgeField() {
        for (Enemy enemy : enemies) {
            if (!enemy.isActive()) continue;
            score += scoreForEnemy(enemy);
            enemy.defeat();
            enemiesDefeated++;
            resolvedThisLevel++;
            effects.add(new VisualEffect(
                    VisualEffect.Kind.IMPACT,
                    enemy.getX(),
                    enemy.getAnchorY() - enemy.getType().getTargetHeight() * enemy.depthScale() * 0.5,
                    GameConfig.ARROW_FLIGHT_TICKS + 10, enemy.depthScale()));
        }
        for (Projectile projectile : projectiles) {
            if (projectile.isActive()) {
                projectile.intercept();
                projectilesIntercepted++;
            }
        }
        resolver.reset();
    }

    private void declareVictory() {
        if (victory) return;
        victory = true;
        gameOver = true;
        if (difficulty.isWinnable()) {
            clearedTiers.add(difficulty.getWordBankKey());
        }
        resolver.reset();
    }

    /**
     * Looses a shot at a point and schedules its impact.
     *
     * @param majorShot   a climactic shot, such as the blow a finished verse lands
     * @param majorImpact a word finished rather than a letter typed
     */
    private void spawnArrowAt(double targetX, double targetY,
                              boolean majorShot, boolean majorImpact) {
        player.aimAt(targetX);
        effects.add(new VisualEffect(
                VisualEffect.Kind.ARROW,
                player.getX(), player.getBowY(),
                targetX, targetY,
                GameConfig.ARROW_FLIGHT_TICKS, 1.0, majorShot));
        effects.add(new VisualEffect(
                VisualEffect.Kind.IMPACT,
                targetX, targetY,
                GameConfig.ARROW_FLIGHT_TICKS + 10, 1.0, majorImpact));
        shotsLoosed++;
    }

    private int scoreForEnemy(Enemy enemy) {
        int base = GraphemeCounter.count(enemy.getWord()) * 10;
        double tierBonus = 1.0 / Math.max(0.4, enemy.getType().getSpeedMultiplier());
        return (int) Math.round(base * tierBonus
                * DifficultyCurve.scoreMultiplier(getLevel()) * combo.getMultiplier());
    }

    private int scoreForProjectile(Projectile projectile) {
        int base = GraphemeCounter.count(projectile.getWord()) * 25;
        return (int) Math.round(base
                * DifficultyCurve.scoreMultiplier(getLevel()) * combo.getMultiplier());
    }

    public void spawnInSandbox(EnemyType type) {
        if (!isSandbox || waveManager == null) return;

        if (type.name().equals("YAKSHA_COMMANDER")
                || type.name().equals("REAM_EYSO")
                || type.name().equals("CORRUPTED_APSARA")
                || type.name().equals("ABYSSAL_NAGA")
                || type.name().equals("KRONG_REAP")) {
            enemies.clear();
            projectiles.clear();

            List<String> script = wordBank.bossScript(
                    difficulty.getWordBankKey(),
                    difficulty.getBossParagraphCount(),
                    difficulty.getBossSentencesPerParagraph(),
                    random);

            boss = new BossFight(type, script,
                    difficulty.getBossSentencesPerParagraph(), difficulty, random);

            resolver.reset();

            effects.add(new VisualEffect(
                    VisualEffect.Kind.SPAWN_POOF,
                    GameConfig.TEMPLE_CENTER_X,
                    GameConfig.GROUND_LINE_Y - GameConfig.BOSS_HEIGHT * 0.4,
                    GameConfig.POOF_TICKS * 3, 3.0));
            return;
        }

        Enemy enemy = waveManager.spawnSpecific(type, enemies);
        if (enemy != null) {
            enemies.add(enemy);
            effects.add(new VisualEffect(
                    VisualEffect.Kind.SPAWN_POOF,
                    enemy.getX(),
                    enemy.getAnchorY() - enemy.getType().getTargetHeight()
                            * enemy.depthScale() * 0.35,
                    GameConfig.POOF_TICKS,
                    enemy.depthScale()));
        }
    }

    public void restart() {
        int runBestScore = Math.max(bestScore, score);
        int runBestLevel = Math.max(bestLevel, getLevel());

        enemies.clear();
        projectiles.clear();
        powerUps.clear();
        effects.clear();
        resolver.resetAll();
        waveManager.reset();
        player.reset();
        powerUpState.reset();
        combo.reset();
        boss = null;
        bossBuffer = "";
        pendingBossVenom = 0;
        ticksSinceBossVenom = Double.MAX_VALUE;
        bufferInvalidated = false;
        wordBank.resetUsage();

        score = 0;
        halfLives = GameConfig.STARTING_HALF_LIVES;
        elapsedTicks = 0;
        charactersTyped = 0;
        enemiesDefeated = 0;
        projectilesIntercepted = 0;
        powerUpsCollected = 0;
        resolvedThisLevel = 0;
        lastLevelSeen = 0;
        victory = false;
        beginIntro();
        gameOver = false;
        running = true;
        levelJustCleared = false;
        lastResult = null;

        bestScore = runBestScore;
        bestLevel = runBestLevel;
    }

    public void addEnemy(Enemy enemy) {
        if (enemy != null) enemies.add(enemy);
    }

    public List<Enemy> getEnemies() { return Collections.unmodifiableList(enemies); }
    public List<Projectile> getProjectiles() { return Collections.unmodifiableList(projectiles); }
    public List<PowerUp> getPowerUps() { return Collections.unmodifiableList(powerUps); }
    public PowerUpState getPowerUpState() { return powerUpState; }
    public ComboTracker getCombo() { return combo; }
    public BossFight getBoss() { return boss; }
    public boolean isBossActive() { return boss != null; }

    public void addPowerUp(PowerUp powerUp) {
        if (powerUp != null) powerUps.add(powerUp);
    }

    public List<VisualEffect> getEffects() { return Collections.unmodifiableList(effects); }
    public Player getPlayer() { return player; }
    public TargetResolver getResolver() { return resolver; }
    public WaveManager getWaveManager() { return waveManager; }
    public WordBank getWordBank() { return wordBank; }
    public Language getLanguage() { return language; }

    /** Khmer slows the whole battlefield; see GameConfig.KHMER_SPEED_SCALE. */
    private double languageSpeedScale() {
        return language == Language.KHMER ? GameConfig.KHMER_SPEED_SCALE : 1.0;
    }
    public ResolveResult getLastResult() { return lastResult; }

    public String getTypedBuffer() {
        if (boss != null && boss.isFighting()) return bossBuffer;
        return resolver.getValidBuffer();
    }

    public int getScore() { return score; }
    public int getLevel() { return waveManager.getLevel(); }
    public int getLives() { return (int) Math.ceil(halfLives / (double) GameConfig.HALVES_PER_LIFE); }
    public int getHalfLives() { return halfLives; }

    public void loseLife() { damage(GameConfig.HALVES_PER_LIFE); }
    public void loseHalfLife() { damage(1); }

    private void damage(int halves) {
        if (isSandbox) return;
        halfLives -= Math.max(0, halves);
        if (halfLives <= 0) {
            halfLives = 0;
            gameOver = true;
        }
    }

    public boolean isGameOver() { return gameOver; }
    public boolean isVictory() { return victory; }
    public int getPowerUpsCollected() { return powerUpsCollected; }
    public int getFinalLevel() { return waveManager.getFinalLevel(); }
    public boolean isLevelJustCleared() { return levelJustCleared; }
    public long getElapsedTicks() { return elapsedTicks; }
    public double getElapsedSeconds() { return (double) elapsedTicks / GameConfig.TARGET_FPS; }

    public double getWpm() {
        double minutes = getElapsedSeconds() / 60.0;
        if (minutes <= 0.01) return 0;
        return (charactersTyped / 5.0) / minutes;
    }

    public int getCharactersTyped() { return charactersTyped; }
    public int getEnemiesDefeated() { return enemiesDefeated; }
    public int getResolvedThisLevel() { return resolvedThisLevel; }
    public int getEnemiesInLevel() { return DifficultyCurve.enemyCount(getLevel(), difficulty); }

    public double getLevelProgress() {
        if (getLevel() < 1) return 0;
        int total = getEnemiesInLevel();
        if (total <= 0) return 0;
        return Math.max(0.0, Math.min(1.0, resolvedThisLevel / (double) total));
    }

    public int getProjectilesIntercepted() { return projectilesIntercepted; }
    public boolean isRunning() { return running; }
    public boolean isSandbox() { return isSandbox; }
    public void setSandboxMode(boolean isSandbox) { this.isSandbox = isSandbox; }
    public void setRunning(boolean running) { this.running = running; }
    public boolean isPaused() { return paused; }
    public IntroSequence getIntro() { return intro; }
    public boolean isIntroActive() { return intro != null && intro.isActive(); }
    public void beginIntro() { intro = new IntroSequence(difficulty); }
    public void skipIntro() { if (intro != null) intro.skip(); }
    public Difficulty getDifficulty() { return difficulty; }

    public void restartWith(Difficulty difficulty) {
        this.difficulty = difficulty == null ? Difficulty.defaultChoice() : difficulty;
        waveManager.setDifficulty(this.difficulty);
        restart();
    }

    /**
     * Switches the typing language in place, without restarting the run.
     *
     * <p>Unlike the tier, language decides nothing about pacing or which boss
     * ends the game — only which script the word bank hands out — so there is
     * no reason to force a restart. Words already on the field keep the
     * language they spawned with; the next word drawn is in the new one. A
     * no-op when nothing changed. The renderer notices the change and swaps
     * its fonts itself; this class knows nothing about Swing.
     */
    public void setLanguage(Language language) {
        Language resolved = language == null ? Language.ENGLISH : language;
        if (resolved == this.language) {
            return;
        }
        this.language = resolved;
        this.wordBank = new WordBank(this.language, this.random);
        waveManager.setWordBank(this.wordBank);
    }

    /** Starts a fresh run on a chosen tier with a chosen hero. */
    public void restartWith(Difficulty difficulty, Hero hero) {
        setHero(hero);
        restartWith(difficulty);
    }

    /** Running count of shots loosed, for the UI to sound them. Never reset. */
    public int getShotsLoosed() {
        return shotsLoosed;
    }

    public Hero getHero() {
        return hero;
    }

    /**
     * Picks the hero for the next run. Meant for between runs — callers follow
     * it with a restart, as the Sandbox and {@link #restartWith(Difficulty, Hero)}
     * both do.
     */
    public void setHero(Hero hero) {
        this.hero = hero == null ? Hero.defaultChoice() : hero;
    }

    public TempleMap getTempleMap() {
        return templeMap;
    }

    /** Picks the temple for the next run; safe to call before a restart. */
    public void setTempleMap(TempleMap templeMap) {
        this.templeMap = templeMap == null ? TempleMap.defaultChoice() : templeMap;
    }

    /**
     * Flips the pause state and reports the result.
     *
     * <p>Refuses to pause a finished run — there is nothing to come back to, and
     * a pause overlay stacked on the game-over screen would hide the restart
     * prompt.
     */
    public boolean togglePause() {
        if (gameOver) return false;
        paused = !paused;
        return paused;
    }

    public SaveData toSaveData() {
        return new SaveData(
                getLevel(), score, getLives(), language,
                Math.max(bestScore, score),
                Math.max(bestLevel, getLevel()),
                clearedTiers,
                hero.getKey(),
                templeMap.getKey());
    }

    public DifficultyProgress getProgress() {
        return new DifficultyProgress(clearedTiers);
    }

    public void restoreProgress(SaveData data) {
        if (data != null) {
            clearedTiers.addAll(data.clearedTiers());
        }
    }

    public void restoreFrom(SaveData data) {
        if (data == null) return;

        this.bestScore = data.bestScore();
        this.bestLevel = data.bestWave();
        restoreProgress(data);
        this.hero = Hero.fromKey(data.heroKey());
        this.templeMap = TempleMap.fromKey(data.templeMapKey());

        if (data.hasResumableRun()) {
            this.score = data.score();
            this.halfLives = Math.max(1, data.lives()) * GameConfig.HALVES_PER_LIFE;
            this.gameOver = false;
            enemies.clear();
            projectiles.clear();
            resolver.reset();
            waveManager.resumeAtLevel(data.wave() - 1);
        }
    }

    public int getBestScore() { return Math.max(bestScore, score); }
    public int getBestLevel() { return Math.max(bestLevel, getLevel()); }
}
