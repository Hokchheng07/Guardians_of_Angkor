package com.guardiansofangkor.i18n;

import com.guardiansofangkor.entities.EnemyType;
import com.guardiansofangkor.util.GraphemeCounter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Supplies words for spawning enemies, bucketed by difficulty tier.
 *
 * <p>All actual vocabulary lives in {@code /words/words_en.json} and
 * {@code /words/words_km.json} on the classpath — this class only loads and
 * selects, it never stores content. Expanding or re-theming the word bank is
 * a JSON edit, not a code change. If a resource is missing, unreadable, or
 * empty, the bank falls back to a small built-in list rather than crashing
 * (dev brief Section 5.4: every I/O boundary fails gracefully to a default).
 *
 * <p>Expected JSON shape:
 * <pre>
 * {
 *   "language": "en",
 *   "tiers": {
 *     "short":  ["sun", "ash", ...],
 *     "medium": ["stone", "spire", ...],
 *     "longer": ["lantern", "monsoon", ...],
 *     "long":   ["monument", "sanctuary", ...]
 *   },
 *   "boss": ["incantation", "apparition", ...],
 *   "projectile": ["om", "ka", "urn", ...]
 * }
 * </pre>
 *
 * <p>{@code short}/{@code medium}/{@code longer}/{@code long} feed regular
 * enemy spawns via {@link #wordFor}, exactly as before. {@code boss} is a
 * separate, reserved pool for elite/boss encounters (Section 8 of the word
 * bank brief) — deliberately kept out of the regular rotation so a boss word
 * never shows up on an ordinary enemy first. {@code projectile} is a curated
 * set of maximally-legible 2-3 character words for thrown attacks.
 *
 * <p>Difficulty is <em>not</em> length alone: within each tier, words are
 * hand-picked in the JSON for a mix of easy and trickier spellings (double
 * letters, less-common letters, awkward digraphs), since {@link EnemyType}
 * only exposes a min/max length window to bucket against. If finer-grained
 * difficulty scoring is ever wanted, it would need a new field on
 * {@link EnemyType} — out of scope here, since this pass only touches the
 * word bank.
 *
 * <p>Tier bucketing counts <em>grapheme clusters</em>, not Java chars, so
 * Khmer words land in the right tier (Section 5.1).
 */
public class WordBank {

    // ---- fallback content --------------------------------------------------
    // Small, hand-picked safety net used only when the JSON resource for a
    // language is missing, unreadable, or empty. The real word bank lives in
    // JSON; this is deliberately compact rather than a duplicate of it.

    private static final List<String> FALLBACK_SHORT = List.of(
            "sun", "sky", "owl", "ash", "mist", "moon", "fang", "claw", "dusk", "veil",
            "bone", "gold", "iron", "leaf", "palm", "reed", "root", "rope", "rust", "salt",
            "sand", "silk", "silt", "soil", "star", "wind", "rain", "fire", "frog", "toad",
            "newt", "worm", "wolf", "bear", "deer", "hawk", "wren", "lark", "dove", "hare");

    private static final List<String> FALLBACK_MEDIUM = List.of(
            "stone", "spire", "shade", "curse", "flame", "night", "ghost", "wraith",
            "temple", "shadow", "spirit", "hollow", "altar", "amber", "blade", "chant",
            "crown", "crypt", "demon", "dream");

    private static final List<String> FALLBACK_LONGER = List.of(
            "lantern", "monsoon", "obsidian", "sandstone", "moonlight", "guardian",
            "ancestor", "crescent", "darkness", "festival");

    private static final List<String> FALLBACK_LONG = List.of(
            "monument", "sanctuary", "incantation", "reliquary", "procession",
            "labyrinthine", "invocation", "apparition");

    /** Offline builds still get a couple of reserved words for elite fights. */
    private static final List<String> FALLBACK_BOSS = List.of(
            "incantation", "labyrinthine", "invocation", "apparition", "necropolis");

    private static final List<String> FALLBACK_PROJECTILE =
            List.of("ka", "om", "ra", "sok", "vy", "nak", "sar");

    private static final WordData FALLBACK = new WordData(
            FALLBACK_SHORT, FALLBACK_MEDIUM, FALLBACK_LONGER, FALLBACK_LONG,
            FALLBACK_BOSS, FALLBACK_PROJECTILE);

    // ---- repeat-cap tuning --------------------------------------------------

    /** Words at or under this length count as short/medium for repeat purposes. */
    private static final int MEDIUM_MAX_LEN = 6;
    /** Short & medium words may appear twice before being retired for the run. */
    private static final int SHORT_MEDIUM_MAX_REPEATS = 2;
    /** Longer/long-tier words only appear once — rarer enemies, rarer words. */
    private static final int LONGER_LONG_MAX_REPEATS = 1;

    private final Language language;
    private final Random random;
    private final boolean usingFallback;

    private final List<String> shortWords;
    private final List<String> mediumWords;
    private final List<String> longerWords;
    private final List<String> longWords;
    private final List<String> bossWords;
    private final List<String> projectileWords;

    /** Union of short+medium+longer+long — the regular enemy spawn pool. */
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
        this.shortWords = data.shortWords;
        this.mediumWords = data.mediumWords;
        this.longerWords = data.longerWords;
        this.longWords = data.longWords;
        this.bossWords = data.bossWords;
        this.projectileWords = data.projectileWords;

        List<String> combined = new ArrayList<>(shortWords.size() + mediumWords.size()
                + longerWords.size() + longWords.size());
        combined.addAll(shortWords);
        combined.addAll(mediumWords);
        combined.addAll(longerWords);
        combined.addAll(longWords);
        this.words = List.copyOf(combined);
    }

    /**
     * A word sized for the given enemy tier.
     *
     * <p>Falls back progressively rather than failing: if no word matches the
     * tier exactly, the closest available length is used, and if the bank is
     * somehow empty a hardcoded word is returned. A missing word must never stop
     * a wave from spawning. Regular (non-boss) words are capped in how many
     * times they can repeat during a run — see {@link #maxRepeatsFor}.
     *
     * @param exclude words already in play, to avoid two enemies sharing a word
     */
    public String wordFor(EnemyType type, List<String> exclude) {
        int min = type.getMinWordLength();
        int max = type.getMaxWordLength();
        int maxRepeats = maxRepeatsFor(type);

        List<String> inTier = new ArrayList<>();
        for (String word : words) {
            if (GraphemeCounter.isWithin(word, min, max)
                    && (exclude == null || !exclude.contains(word))
                    && usageCount.getOrDefault(word, 0) < maxRepeats) {
                inTier.add(word);
            }
        }
        if (!inTier.isEmpty()) {
            String chosen = inTier.get(random.nextInt(inTier.size()));
            recordUsage(chosen);
            return chosen;
        }

        // Nothing in tier under the repeat cap — widen to anything unused/allowed.
        List<String> unused = new ArrayList<>();
        for (String word : words) {
            if ((exclude == null || !exclude.contains(word))
                    && usageCount.getOrDefault(word, 0) < maxRepeats) {
                unused.add(word);
            }
        }
        if (!unused.isEmpty()) {
            String chosen = unused.get(random.nextInt(unused.size()));
            recordUsage(chosen);
            return chosen;
        }

        // Repeat cap is exhausted everywhere — ignore it rather than stall a
        // wave; a reused word still beats no word at all.
        String chosen = words.isEmpty() ? "temple" : words.get(random.nextInt(words.size()));
        recordUsage(chosen);
        return chosen;
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

    /**
     * A word for an elite/boss-tier enemy, drawn from the reserved {@code
     * boss} pool rather than the regular tiers, so these fights read as
     * distinct from an ordinary strong enemy. Repeats are capped the same as
     * the longer/long tiers (once per run) until the pool is exhausted, at
     * which point it widens to the full regular pool rather than stalling.
     */
    public String bossWord(List<String> exclude) {
        List<String> pool = bossWords.isEmpty() ? words : bossWords;

        List<String> candidates = new ArrayList<>();
        for (String word : pool) {
            if ((exclude == null || !exclude.contains(word))
                    && usageCount.getOrDefault(word, 0) < LONGER_LONG_MAX_REPEATS) {
                candidates.add(word);
            }
        }
        if (candidates.isEmpty()) {
            for (String word : words) {
                if (exclude == null || !exclude.contains(word)) {
                    candidates.add(word);
                }
            }
        }
        if (candidates.isEmpty()) {
            candidates.addAll(words.isEmpty() ? List.of("temple") : words);
        }
        String chosen = candidates.get(random.nextInt(candidates.size()));
        recordUsage(chosen);
        return chosen;
    }

    /**
     * A word for the final boss — reserved from words that have never
     * appeared yet this run, so the climactic fight feels distinct rather
     * than reusing something the player already typed. Checks the {@code
     * boss} pool first (most impactful vocabulary), then widens to the rest
     * of the bank; if literally everything has appeared already, falls back
     * to whichever word has been used least.
     */
    public String finalBossWord(List<String> exclude) {
        String fromBossPool = pickNeverUsed(bossWords, exclude);
        if (fromBossPool != null) {
            recordUsage(fromBossPool);
            return fromBossPool;
        }

        String fromAnywhere = pickNeverUsed(words, exclude);
        if (fromAnywhere != null) {
            recordUsage(fromAnywhere);
            return fromAnywhere;
        }

        String leastUsed = pickLeastUsed(bossWords, exclude);
        if (leastUsed == null) {
            leastUsed = pickLeastUsed(words, exclude);
        }
        if (leastUsed != null) {
            recordUsage(leastUsed);
            return leastUsed;
        }
        return "temple";
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

    /**
     * A very short word for a thrown projectile (dev brief Section 5.2).
     *
     * <p>Drawn from the curated {@code projectile} pool first — words picked
     * for visual distinctness under pressure, not just length. Falls back to
     * any 2-3 character word from the main bank, then to a fixed emergency
     * set, so an attack never fails to spawn for lack of a short word.
     * Repeats are intentionally uncapped here: projectiles are thrown
     * constantly and read for under a second, so tracking their repeats adds
     * bookkeeping with no real payoff.
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

        List<String> emergency = new ArrayList<>(
                List.of("ka", "om", "ra", "sok", "vy", "nak", "sar"));
        if (exclude != null) {
            emergency.removeAll(exclude);
        }
        return emergency.isEmpty() ? "ka" : emergency.get(random.nextInt(emergency.size()));
    }

    public Language getLanguage() {
        return language;
    }

    /** Immutable view of every regular-tier word (short+medium+longer+long). */
    public List<String> getWords() {
        return Collections.unmodifiableList(words);
    }

    public int size() {
        return words.size();
    }

    /** True when the JSON resource was missing/empty and the built-in list is in use. */
    public boolean isUsingFallback() {
        return usingFallback;
    }

    // ---- loading ---------------------------------------------------------

    /** Plain holder for a language's loaded (or fallback) word pools. */
    private static final class WordData {
        final List<String> shortWords;
        final List<String> mediumWords;
        final List<String> longerWords;
        final List<String> longWords;
        final List<String> bossWords;
        final List<String> projectileWords;

        WordData(List<String> shortWords, List<String> mediumWords, List<String> longerWords,
                 List<String> longWords, List<String> bossWords, List<String> projectileWords) {
            this.shortWords = shortWords;
            this.mediumWords = mediumWords;
            this.longerWords = longerWords;
            this.longWords = longWords;
            this.bossWords = bossWords;
            this.projectileWords = projectileWords;
        }

        /** True when none of the regular spawn tiers loaded anything usable. */
        boolean isEmpty() {
            return shortWords.isEmpty() && mediumWords.isEmpty()
                    && longerWords.isEmpty() && longWords.isEmpty();
        }
    }

    private static WordData loadWords(Language language) {
        try (InputStream in = WordBank.class.getResourceAsStream(language.getWordListPath())) {
            if (in == null) {
                System.out.println("[WordBank] " + language.getWordListPath()
                        + " not found — using built-in fallback word list.");
                return FALLBACK;
            }
            String json = readAll(in);
            WordData parsed = parseWordData(json);
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

    /** Parses the new tiered schema: {@code tiers.short/medium/longer/long}, {@code boss}, {@code projectile}. */
    private static WordData parseWordData(String json) {
        List<String> shortWords = extractArray(json, "short");
        List<String> mediumWords = extractArray(json, "medium");
        List<String> longerWords = extractArray(json, "longer");
        List<String> longWords = extractArray(json, "long");
        List<String> bossWords = extractArray(json, "boss");
        List<String> projectileWords = extractArray(json, "projectile");
        return new WordData(shortWords, mediumWords, longerWords, longWords, bossWords, projectileWords);
    }

    /**
     * Extracts the string entries of the array following {@code "key"} in the
     * JSON text.
     *
     * <p>Hand-rolled rather than pulling in a JSON dependency, because the
     * shape is fixed and small — this looks for {@code "key"}, then reads
     * quoted strings until the next closing bracket, honouring backslash
     * escapes so Khmer codepoint escapes ({@code \\u} + four hex digits)
     * survive. Since each tier/pool key is unique within the file, this needs
     * no real object-nesting awareness to stay correct.
     */
    static List<String> extractArray(String json, String key) {
        List<String> result = new ArrayList<>();
        if (json == null) {
            return result;
        }
        int keyIndex = json.indexOf("\"" + key + "\"");
        if (keyIndex < 0) {
            return result;
        }
        int open = json.indexOf('[', keyIndex);
        int close = json.indexOf(']', open + 1);
        if (open < 0 || close < 0) {
            return result;
        }

        String body = json.substring(open + 1, close);
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (escaped) {
                current.append(unescape(c, body, i));
                if (c == 'u') {
                    i += 4;
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                if (inString) {
                    String word = current.toString().trim();
                    if (!word.isEmpty()) {
                        result.add(word);
                    }
                    current.setLength(0);
                }
                inString = !inString;
            } else if (inString) {
                current.append(c);
            }
        }
        return result;
    }

    /**
     * Backward-compatible with the old flat {@code "words": [...]} schema, in
     * case any other code or test still calls this directly.
     */
    static List<String> parseWordArray(String json) {
        return extractArray(json, "words");
    }

    private static String unescape(char c, String body, int index) {
        switch (c) {
            case 'n':
                return "\n";
            case 't':
                return "\t";
            case 'r':
                return "\r";
            case 'u':
                if (index + 5 <= body.length()) {
                    try {
                        return String.valueOf(
                                (char) Integer.parseInt(body.substring(index + 1, index + 5), 16));
                    } catch (NumberFormatException e) {
                        return "";
                    }
                }
                return "";
            default:
                return String.valueOf(c);
        }
    }
}