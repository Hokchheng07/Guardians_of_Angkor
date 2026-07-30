package com.guardiansofangkor.i18n;

import com.guardiansofangkor.entities.EnemyType;
import com.guardiansofangkor.util.GraphemeCounter;
import com.guardiansofangkor.util.Json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Supplies words for spawning enemies, filtered by difficulty and by how far
 * into the run the player is.
 *
 * <p>All vocabulary lives in {@code /words/words_en.json} and
 * {@code /words/words_km.json} on the classpath — this class only loads and
 * selects, it never stores content. Expanding or re-theming the word bank is a
 * JSON edit, not a code change. If a resource is missing, unreadable, or empty,
 * the bank falls back to a small built-in list rather than crashing.
 *
 * <p>Expected JSON shape:
 * <pre>
 * {
 *   "language": "en",
 *   "pools":     { "tiny": [...], "short": [...], "medium": [...],
 *                  "long": [...], "epic": [...] },
 *   "bossPools": { "novice": [...], "adept": [...],
 *                  "master": [...], "legend": [...] },
 *   "projectile": [...],
 *   "difficulties": {
 *     "easy": { "bands": [
 *       { "throughLevel": 5,  "pools": ["tiny","short"],  "boss": "novice" },
 *       ...
 *     ]},
 *     ...
 *   }
 * }
 * </pre>
 *
 * <p>The split between {@code pools} and {@code difficulties} is the important
 * part. Pools hold the words once; the difficulties table says which pools a
 * given tier may draw from at a given level. So making Easy gentler, or stopping
 * level one handing out ten-letter words, is a change to the table — the
 * vocabulary itself never needs touching, and no tier can accidentally be
 * retuned while editing another.
 *
 * <p>Bosses have their own ranked pools ({@code novice} → {@code legend}) rather
 * than sharing the regular tiers, so a boss word can never turn up on an
 * ordinary enemy first, and so a boss fight scales with the tier independently
 * of what the rank and file are typing.
 *
 * <p>Length bucketing counts <em>grapheme clusters</em>, not Java chars, so
 * Khmer words land in the right pool.
 */
public class WordBank {

    // ---- fallback content --------------------------------------------------
    // Small, hand-picked safety net used only when the JSON resource for a
    // language is missing, unreadable, or empty. The real word bank lives in
    // JSON; this is deliberately compact rather than a duplicate of it.

    private static final List<String> FALLBACK_TINY = List.of(
            "sun", "sky", "owl", "ash", "orb", "urn", "axe", "bow", "fog", "ice",
            "mud", "oak", "elm", "gem", "pit", "map", "imp", "hex", "dew", "bog");

    private static final List<String> FALLBACK_SHORT = List.of(
            "mist", "moon", "fang", "claw", "dusk", "veil", "bone", "gold", "iron",
            "leaf", "palm", "reed", "root", "rope", "rust", "salt", "sand", "silk",
            "stone", "spire", "shade", "curse", "flame", "night", "ghost", "altar");

    private static final List<String> FALLBACK_MEDIUM = List.of(
            "temple", "shadow", "spirit", "hollow", "wraith", "banner", "beacon",
            "cinder", "ritual", "shrine", "statue", "thunder", "lantern", "monsoon");

    private static final List<String> FALLBACK_LONG = List.of(
            "obsidian", "sandstone", "moonlight", "guardian", "ancestor", "crescent",
            "darkness", "festival", "monument", "sanctuary", "reliquary");

    private static final List<String> FALLBACK_EPIC = List.of(
            "procession", "battlements", "inheritance", "restoration", "remembrance");

    /** Offline builds still get reserved words for elite fights, ranked as usual. */
    private static final Map<String, List<String>> FALLBACK_BOSS_POOLS = Map.of(
            "novice", List.of("curse", "demon", "ghoul", "shade", "wraith", "terror"),
            "adept", List.of("apparition", "invocation", "necropolis", "cataclysm"),
            "master", List.of("incantation", "malediction", "abomination"),
            "legend", List.of("transmigration", "transfiguration", "disintegration"));

    private static final List<String> FALLBACK_PROJECTILE =
            List.of("ka", "om", "ra", "ox", "urn", "orb", "axe", "sun", "ivy", "hex");

    /**
     * One paragraph per tier for the finale, used when the resource is missing.
     *
     * <p>Short on purpose. The point of the fallback is that the last fight
     * still happens and can still be won, not that it is as good as the real
     * thing.
     */
    private static final Map<String, List<List<String>>> FALLBACK_PARAGRAPHS = Map.of(
            "easy", List.of(List.of(
                    "the naga rises from the flooded baray",
                    "seven stone heads turn toward the causeway",
                    "hold the terrace until the venom runs dry")),
            "medium", List.of(List.of(
                    "krong reap walks the causeway of the dead",
                    "the laterite splits beneath every step he takes",
                    "hold the last terrace and do not falter")),
            "hard", List.of(List.of(
                    "the sarcophagus in the sanctum has been opened",
                    "every lintel of the temple bleeds verdigris and ash",
                    "there is no ward left but the words you carry")),
            "endless", List.of(List.of(
                    "the serpent is older than the stone it guards",
                    "it has swallowed every name ever carved here",
                    "it will swallow yours and keep on going")));

    /**
     * Band tables for the built-in fallback, mirroring the real file's shape so
     * a missing resource still produces gentle early levels rather than a flat
     * pool where level one can serve an eleven-letter word.
     */
    private static final Map<String, List<Band>> FALLBACK_BANDS = Map.of(
            "easy", List.of(
                    new Band(5, List.of("tiny", "short"), "novice"),
                    new Band(10, List.of("tiny", "short", "medium"), "novice"),
                    new Band(15, List.of("short", "medium"), "adept")),
            "medium", List.of(
                    new Band(4, List.of("tiny", "short", "medium"), "novice"),
                    new Band(9, List.of("short", "medium", "long"), "adept"),
                    new Band(15, List.of("short", "medium", "long"), "master")),
            "hard", List.of(
                    new Band(4, List.of("short", "medium", "long"), "adept"),
                    new Band(9, List.of("short", "medium", "long", "epic"), "master"),
                    new Band(15, List.of("medium", "long", "epic"), "legend")),
            "endless", List.of(
                    new Band(4, List.of("tiny", "short", "medium"), "novice"),
                    new Band(9, List.of("short", "medium", "long"), "adept"),
                    new Band(19, List.of("short", "medium", "long", "epic"), "master"),
                    new Band(99, List.of("medium", "long", "epic"), "legend")));

    private static final WordData FALLBACK = new WordData(
            orderedMap(
                    "tiny", FALLBACK_TINY,
                    "short", FALLBACK_SHORT,
                    "medium", FALLBACK_MEDIUM,
                    "long", FALLBACK_LONG,
                    "epic", FALLBACK_EPIC),
            new LinkedHashMap<>(FALLBACK_BOSS_POOLS),
            FALLBACK_PROJECTILE,
            new LinkedHashMap<>(FALLBACK_BANDS),
            new LinkedHashMap<>(FALLBACK_PARAGRAPHS));

    // ---- repeat-cap tuning --------------------------------------------------

    /** Words at or under this length count as short/medium for repeat purposes. */
    private static final int MEDIUM_MAX_LEN = 6;
    /** Short & medium words may appear twice before being retired for the run. */
    private static final int SHORT_MEDIUM_MAX_REPEATS = 2;
    /** Longer/long-tier words only appear once — rarer enemies, rarer words. */
    private static final int LONGER_LONG_MAX_REPEATS = 1;

    /** Ranked worst-to-best, so a missing boss pool can widen to a nearby one. */
    private static final List<String> BOSS_RANKS =
            List.of("novice", "adept", "master", "legend");

    private final Language language;
    private final Random random;
    private final boolean usingFallback;

    private final Map<String, List<String>> pools;
    private final Map<String, List<String>> bossPools;
    private final List<String> projectileWords;
    private final Map<String, List<Band>> bands;
    private final Map<String, List<List<String>>> bossParagraphs;

    /** Union of every regular pool — the unrestricted spawn vocabulary. */
    private final List<String> words;

    /** How many times each word has been handed out so far this run. */
    private final Map<String, Integer> usageCount = new HashMap<>();

    public WordBank(Language language) {
        this(language, new Random());
    }

    /** Seeded constructor so wave composition is reproducible in tests. */
    public WordBank(Language language, Random random) {
        this.language = language == null ? Language.ENGLISH : language;
        this.random = random == null ? new Random() : random;

        WordData data = loadWords(this.language);
        this.usingFallback = data == FALLBACK;
        this.pools = data.pools();
        this.bossPools = data.bossPools();
        this.projectileWords = data.projectileWords();
        this.bands = data.bands();
        this.bossParagraphs = data.bossParagraphs();

        // Deduped so a word listed in two pools cannot be twice as likely.
        LinkedHashSet<String> combined = new LinkedHashSet<>();
        for (List<String> pool : pools.values()) {
            combined.addAll(pool);
        }
        this.words = List.copyOf(combined);
    }

    // ---- policy ------------------------------------------------------------

    /**
     * Resolves what a given tier may use at a given level.
     *
     * <p>Bands are matched in file order by {@code throughLevel}; the last band
     * also covers everything past it, so Endless never runs off the end of its
     * own table. An unknown tier resolves to {@link WordPolicy#UNRESTRICTED}
     * rather than to nothing, because a typo in the table should widen the
     * vocabulary, not empty it.
     *
     * @param tierKey lower-case difficulty name, e.g. {@code "easy"}
     */
    public WordPolicy policyFor(String tierKey, int level) {
        if (tierKey == null) {
            return WordPolicy.UNRESTRICTED;
        }
        List<Band> table = bands.get(tierKey.toLowerCase(java.util.Locale.ROOT));
        if (table == null || table.isEmpty()) {
            return WordPolicy.UNRESTRICTED;
        }
        int effectiveLevel = Math.max(1, level);
        for (Band band : table) {
            if (effectiveLevel <= band.throughLevel()) {
                return new WordPolicy(tierKey, effectiveLevel, band.pools(), band.boss());
            }
        }
        Band last = table.get(table.size() - 1);
        return new WordPolicy(tierKey, effectiveLevel, last.pools(), last.boss());
    }

    /** Every word a policy permits, in pool order. Never empty. */
    public List<String> vocabularyFor(WordPolicy policy) {
        if (policy == null || !policy.restrictsPools()) {
            return words;
        }
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        for (String poolName : policy.getPoolNames()) {
            allowed.addAll(pools.getOrDefault(poolName, List.of()));
        }
        return allowed.isEmpty() ? words : List.copyOf(allowed);
    }

    // ---- regular enemy words -----------------------------------------------

    /** A word sized for the given enemy tier, drawing on the whole bank. */
    public String wordFor(EnemyType type, List<String> exclude) {
        return wordFor(type, exclude, WordPolicy.UNRESTRICTED, 0, 0);
    }

    /** A word sized for the enemy tier with the length window shifted. */
    public String wordFor(EnemyType type, List<String> exclude, int minShift, int maxShift) {
        return wordFor(type, exclude, WordPolicy.UNRESTRICTED, minShift, maxShift);
    }

    /** A word for the given enemy tier, restricted to what {@code policy} allows. */
    public String wordFor(EnemyType type, List<String> exclude, WordPolicy policy) {
        return wordFor(type, exclude, policy, 0, 0);
    }

    /**
     * A word for the given enemy tier, restricted to what {@code policy} allows
     * and with the type's length window shifted by the difficulty.
     *
     * <p>Selection degrades in steps rather than ever failing: the exact window
     * inside the band, then the band ignoring repeat caps, then the
     * <em>closest</em> lengths the band has, then the whole bank. A wave must
     * never stall for want of a word.
     *
     * <p>The third step is what makes a heavy enemy work on an early level. A
     * Pret asks for eight to twelve letters; Easy's opening band only holds
     * words up to five. Rather than reaching outside the band — which is exactly
     * the "too hard, too early" problem — it takes the longest words the band
     * does have, so the Pret is still visibly the hardest thing on screen
     * without being unfair.
     *
     * @param exclude words already in play, to avoid two enemies sharing a word
     */
    public String wordFor(EnemyType type, List<String> exclude, WordPolicy policy,
                          int minShift, int maxShift) {
        if (type == null) {
            return anyWord(exclude);
        }
        int min = Math.max(2, type.getMinWordLength() + minShift);
        int max = Math.max(min, type.getMaxWordLength() + maxShift);
        int maxRepeats = maxRepeatsFor(type);

        List<String> allowed = vocabularyFor(policy);

        String chosen = pickInWindow(allowed, exclude, min, max, maxRepeats);
        if (chosen == null) {
            chosen = pickInWindow(allowed, exclude, min, max, Integer.MAX_VALUE);
        }
        if (chosen == null) {
            chosen = pickClosestToWindow(allowed, exclude, min, max);
        }
        if (chosen == null && allowed != words) {
            chosen = pickClosestToWindow(words, exclude, min, max);
        }
        if (chosen == null) {
            chosen = anyWord(exclude);
        }
        recordUsage(chosen);
        return chosen;
    }

    /** A random allowed word whose length falls inside the window, or null. */
    private String pickInWindow(List<String> pool, List<String> exclude,
                                int min, int max, int maxRepeats) {
        List<String> candidates = new ArrayList<>();
        for (String word : pool) {
            if (GraphemeCounter.isWithin(word, min, max)
                    && (exclude == null || !exclude.contains(word))
                    && usageCount.getOrDefault(word, 0) < maxRepeats) {
                candidates.add(word);
            }
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * The allowed words whose length sits nearest the window, picked at random
     * among equals. Used when the band simply has nothing that long (or short).
     */
    private String pickClosestToWindow(List<String> pool, List<String> exclude,
                                       int min, int max) {
        List<String> best = new ArrayList<>();
        int bestDistance = Integer.MAX_VALUE;
        for (String word : pool) {
            if (exclude != null && exclude.contains(word)) {
                continue;
            }
            int length = GraphemeCounter.count(word);
            int distance = length < min ? min - length : Math.max(0, length - max);
            if (distance < bestDistance) {
                bestDistance = distance;
                best.clear();
                best.add(word);
            } else if (distance == bestDistance) {
                best.add(word);
            }
        }
        return best.isEmpty() ? null : best.get(random.nextInt(best.size()));
    }

    /** Absolute last resort — a reused word still beats no word at all. */
    private String anyWord(List<String> exclude) {
        List<String> candidates = new ArrayList<>();
        for (String word : words) {
            if (exclude == null || !exclude.contains(word)) {
                candidates.add(word);
            }
        }
        if (candidates.isEmpty()) {
            candidates = words;
        }
        return candidates.isEmpty() ? "temple" : candidates.get(random.nextInt(candidates.size()));
    }

    /** Short/medium tiers tolerate 2 repeats; longer/long tiers only 1. */
    private int maxRepeatsFor(EnemyType type) {
        return type.getMaxWordLength() <= MEDIUM_MAX_LEN
                ? SHORT_MEDIUM_MAX_REPEATS
                : LONGER_LONG_MAX_REPEATS;
    }

    private void recordUsage(String word) {
        usageCount.merge(word, 1, Integer::sum);
    }

    // ---- boss words --------------------------------------------------------

    /** A mini-boss word from the whole reserved pool. */
    public String bossWord(List<String> exclude) {
        return bossWord(exclude, WordPolicy.UNRESTRICTED);
    }

    /**
     * A word for a mini-boss, drawn from the rank {@code policy} names rather
     * than from the regular tiers.
     *
     * <p>Ranks climb with the difficulty and with the level band, which is what
     * makes an Easy Naga and a Hard Naga genuinely different fights even though
     * they are the same monster. Repeats are capped as tightly as the long
     * tiers; when a rank is exhausted the search widens to the neighbouring
     * ranks and finally to the regular vocabulary, rather than stalling.
     */
    public String bossWord(List<String> exclude, WordPolicy policy) {
        for (List<String> pool : bossPoolsFor(policy)) {
            String chosen = pickUnderCap(pool, exclude, LONGER_LONG_MAX_REPEATS);
            if (chosen != null) {
                recordUsage(chosen);
                return chosen;
            }
        }
        String chosen = anyWord(exclude);
        recordUsage(chosen);
        return chosen;
    }

    /** A final-boss word from the whole reserved pool. */
    public String finalBossWord(List<String> exclude) {
        return finalBossWord(exclude, WordPolicy.UNRESTRICTED);
    }

    /**
     * A word for the final boss — the longest never-used word in this tier's
     * boss rank, so the climactic fight is both the hardest typing in the run
     * and something the player has not already typed.
     *
     * <p>Falls back to any never-used word, then to whichever has been used
     * least, so the fight always gets a word.
     */
    public String finalBossWord(List<String> exclude, WordPolicy policy) {
        for (List<String> pool : bossPoolsFor(policy)) {
            String fresh = pickNeverUsed(pool, exclude);
            if (fresh != null) {
                recordUsage(fresh);
                return fresh;
            }
        }

        String fromAnywhere = pickNeverUsed(words, exclude);
        if (fromAnywhere != null) {
            recordUsage(fromAnywhere);
            return fromAnywhere;
        }

        for (List<String> pool : bossPoolsFor(policy)) {
            String leastUsed = pickLeastUsed(pool, exclude);
            if (leastUsed != null) {
                recordUsage(leastUsed);
                return leastUsed;
            }
        }
        String leastUsed = pickLeastUsed(words, exclude);
        if (leastUsed != null) {
            recordUsage(leastUsed);
            return leastUsed;
        }
        return "temple";
    }

    /**
     * The boss pools to try, best match first.
     *
     * <p>The policy's own rank leads; the remaining ranks follow in descending
     * order of prestige, so exhausting {@code master} widens to {@code legend}
     * and then down, never to something that would read as an anticlimax before
     * it has to.
     */
    private List<List<String>> bossPoolsFor(WordPolicy policy) {
        List<List<String>> ordered = new ArrayList<>();
        String preferred = policy == null ? "" : policy.getBossPoolName();

        List<String> exact = bossPools.get(preferred);
        if (exact != null && !exact.isEmpty()) {
            ordered.add(exact);
        }
        for (int i = BOSS_RANKS.size() - 1; i >= 0; i--) {
            String rank = BOSS_RANKS.get(i);
            if (rank.equals(preferred)) {
                continue;
            }
            List<String> pool = bossPools.get(rank);
            if (pool != null && !pool.isEmpty()) {
                ordered.add(pool);
            }
        }
        // Any rank the file invented that is not in BOSS_RANKS still gets a turn.
        for (Map.Entry<String, List<String>> entry : bossPools.entrySet()) {
            if (!BOSS_RANKS.contains(entry.getKey())
                    && !entry.getKey().equals(preferred)
                    && !entry.getValue().isEmpty()) {
                ordered.add(entry.getValue());
            }
        }
        return ordered;
    }

    private String pickUnderCap(List<String> pool, List<String> exclude, int maxRepeats) {
        List<String> candidates = new ArrayList<>();
        for (String word : pool) {
            if ((exclude == null || !exclude.contains(word))
                    && usageCount.getOrDefault(word, 0) < maxRepeats) {
                candidates.add(word);
            }
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    /** The longest never-used word in {@code pool}, or {@code null} if none. */
    private String pickNeverUsed(List<String> pool, List<String> exclude) {
        List<String> candidates = new ArrayList<>();
        for (String word : pool) {
            if (usageCount.getOrDefault(word, 0) == 0
                    && (exclude == null || !exclude.contains(word))) {
                candidates.add(word);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort((a, b) -> GraphemeCounter.count(b) - GraphemeCounter.count(a));
        return candidates.get(0);
    }

    /** The least-used word in {@code pool}, or {@code null} if none available. */
    private String pickLeastUsed(List<String> pool, List<String> exclude) {
        String best = null;
        int lowest = Integer.MAX_VALUE;
        for (String word : pool) {
            if (exclude != null && exclude.contains(word)) {
                continue;
            }
            int count = usageCount.getOrDefault(word, 0);
            if (count < lowest) {
                lowest = count;
                best = word;
            }
        }
        return best;
    }

    /** Clears repeat-tracking — call at the start of a new run/level. */
    public void resetUsage() {
        usageCount.clear();
    }

    // ---- projectiles -------------------------------------------------------

    /**
     * A very short word for a thrown projectile.
     *
     * <p>Drawn from the curated {@code projectile} pool — words picked for
     * visual distinctness under pressure, not just length. Falls back to any 2-3
     * character word from the main bank, then to a fixed emergency set, so an
     * attack never fails to spawn for lack of a short word. Repeats are
     * intentionally uncapped: projectiles are thrown constantly and read for
     * under a second, so tracking their repeats adds bookkeeping with no payoff.
     */
    public String projectileWord(List<String> exclude) {
        List<String> candidates = new ArrayList<>();
        for (String word : projectileWords) {
            if (exclude == null || !exclude.contains(word)) {
                candidates.add(word);
            }
        }
        if (!candidates.isEmpty()) {
            return candidates.get(random.nextInt(candidates.size()));
        }

        List<String> fallbackCandidates = new ArrayList<>();
        for (String word : words) {
            int length = GraphemeCounter.count(word);
            if (length >= 2 && length <= 3
                    && (exclude == null || !exclude.contains(word))) {
                fallbackCandidates.add(word);
            }
        }
        if (!fallbackCandidates.isEmpty()) {
            return fallbackCandidates.get(random.nextInt(fallbackCandidates.size()));
        }

        List<String> emergency = new ArrayList<>(FALLBACK_PROJECTILE);
        if (exclude != null) {
            emergency.removeAll(exclude);
        }
        return emergency.isEmpty() ? "ka" : emergency.get(random.nextInt(emergency.size()));
    }

    /**
     * A short word to label a power-up pickup.
     *
     * <p>Shares the projectile pool on purpose: both are things the player has
     * a few seconds to grab, and both need to be legible at a glance beside
     * whatever longer word they are already mid-way through.
     */
    public String pickupWord(List<String> exclude) {
        return projectileWord(exclude);
    }

    // ---- boss paragraphs ---------------------------------------------------

    /**
     * The paragraph the finale asks the player to type, as its sentences.
     *
     * <p>Several are written per tier and one is drawn per run, so beating the
     * game twice is not the same fight twice. Never empty: an unknown tier or a
     * word bank with no paragraphs falls back to the built-in one, because a
     * boss with nothing to type would be an unwinnable run.
     *
     * @param tierKey lower-case difficulty name, e.g. {@code "easy"}
     */
    public List<String> bossParagraph(String tierKey, Random picker) {
        List<List<String>> options = tierKey == null
                ? null
                : bossParagraphs.get(tierKey.toLowerCase(java.util.Locale.ROOT));

        if (options == null || options.isEmpty()) {
            options = FALLBACK_PARAGRAPHS.get("easy");
        }
        Random source = picker == null ? random : picker;
        return options.get(source.nextInt(options.size()));
    }

    /** How many paragraphs a tier has to choose between. Zero if none. */
    public int bossParagraphCount(String tierKey) {
        List<List<String>> options = tierKey == null
                ? null
                : bossParagraphs.get(tierKey.toLowerCase(java.util.Locale.ROOT));
        return options == null ? 0 : options.size();
    }

    // ---- accessors ---------------------------------------------------------

    public Language getLanguage() {
        return language;
    }

    /** Immutable view of every regular-tier word, across all pools. */
    public List<String> getWords() {
        return Collections.unmodifiableList(words);
    }

    /** Immutable view of one named pool, e.g. {@code "short"}. Never null. */
    public List<String> getPool(String name) {
        return Collections.unmodifiableList(pools.getOrDefault(name, List.of()));
    }

    /** Immutable view of one boss rank, e.g. {@code "master"}. Never null. */
    public List<String> getBossPool(String rank) {
        return Collections.unmodifiableList(bossPools.getOrDefault(rank, List.of()));
    }

    /** Names of every regular pool, in file order. */
    public List<String> getPoolNames() {
        return List.copyOf(pools.keySet());
    }

    public int size() {
        return words.size();
    }

    /** True when the JSON resource was missing/empty and the built-in list is in use. */
    public boolean isUsingFallback() {
        return usingFallback;
    }

    // ---- loading -----------------------------------------------------------

    /** One row of a tier's tuning table: "up to this level, use these pools". */
    record Band(int throughLevel, List<String> pools, String boss) {
        Band {
            pools = pools == null ? List.of() : List.copyOf(pools);
            boss = boss == null ? "" : boss;
        }
    }

    /** Plain holder for a language's loaded (or fallback) content. */
    private record WordData(Map<String, List<String>> pools,
                            Map<String, List<String>> bossPools,
                            List<String> projectileWords,
                            Map<String, List<Band>> bands,
                            Map<String, List<List<String>>> bossParagraphs) {

        /** True when no regular pool loaded anything usable. */
        boolean isEmpty() {
            for (List<String> pool : pools.values()) {
                if (!pool.isEmpty()) {
                    return false;
                }
            }
            return true;
        }
    }

    private static WordData loadWords(Language language) {
        try (InputStream in = WordBank.class.getResourceAsStream(language.getWordListPath())) {
            if (in == null) {
                System.out.println("[WordBank] " + language.getWordListPath()
                        + " not found — using built-in fallback word list.");
                return FALLBACK;
            }
            Object root = Json.parse(readAll(in));
            if (!(root instanceof Map)) {
                System.err.println("[WordBank] " + language.getWordListPath()
                        + " is not a JSON object — using built-in fallback.");
                return FALLBACK;
            }
            @SuppressWarnings("unchecked")
            WordData parsed = parseWordData((Map<String, Object>) root);
            if (parsed.isEmpty()) {
                System.err.println("[WordBank] " + language.getWordListPath()
                        + " contained no words — using built-in fallback.");
                return FALLBACK;
            }
            return parsed;
        } catch (IOException | RuntimeException e) {
            System.err.println("[WordBank] Failed to read " + language.getWordListPath()
                    + " (" + e.getMessage() + ") — using built-in fallback.");
            return FALLBACK;
        }
    }

    private static String readAll(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static WordData parseWordData(Map<String, Object> root) {
        Map<String, List<String>> pools = readStringLists(Json.objectAt(root, "pools"));
        Map<String, List<String>> bossPools = readStringLists(Json.objectAt(root, "bossPools"));
        List<String> projectile = Json.stringsAt(root, "projectile");

        Map<String, List<Band>> bands = new LinkedHashMap<>();
        Map<String, Object> difficulties = Json.objectAt(root, "difficulties");
        for (String tierKey : difficulties.keySet()) {
            Map<String, Object> tier = Json.objectAt(difficulties, tierKey);
            List<Band> table = new ArrayList<>();
            for (Map<String, Object> row : Json.objectsAt(tier, "bands")) {
                table.add(new Band(
                        Json.intAt(row, "throughLevel", Integer.MAX_VALUE),
                        Json.stringsAt(row, "pools"),
                        Json.stringAt(row, "boss", "")));
            }
            if (!table.isEmpty()) {
                bands.put(tierKey.toLowerCase(java.util.Locale.ROOT), table);
            }
        }

        Map<String, List<List<String>>> paragraphs = new LinkedHashMap<>();
        Map<String, Object> paragraphSource = Json.objectAt(root, "bossParagraphs");
        for (String tierKey : paragraphSource.keySet()) {
            List<List<String>> forTier = Json.stringListsAt(paragraphSource, tierKey);
            if (!forTier.isEmpty()) {
                paragraphs.put(tierKey.toLowerCase(java.util.Locale.ROOT), forTier);
            }
        }

        return new WordData(pools, bossPools,
                projectile.isEmpty() ? FALLBACK_PROJECTILE : projectile, bands,
                paragraphs.isEmpty() ? new LinkedHashMap<>(FALLBACK_PARAGRAPHS) : paragraphs);
    }

    /** Reads every {@code "name": ["a","b"]} entry of an object into a map. */
    private static Map<String, List<String>> readStringLists(Map<String, Object> source) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String key : source.keySet()) {
            List<String> values = Json.stringsAt(source, key);
            if (!values.isEmpty()) {
                result.put(key, values);
            }
        }
        return result;
    }

    private static Map<String, List<String>> orderedMap(Object... pairs) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            @SuppressWarnings("unchecked")
            List<String> value = (List<String>) pairs[i + 1];
            map.put((String) pairs[i], value);
        }
        return map;
    }
}
