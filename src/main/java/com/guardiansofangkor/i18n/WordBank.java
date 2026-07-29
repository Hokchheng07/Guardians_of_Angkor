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
import java.util.List;
import java.util.Random;

/**
 * Supplies words for spawning enemies, bucketed by difficulty tier.
 *
 * <p>Loads {@code /words/words_en.json} or {@code /words/words_km.json} from the
 * classpath when present. Those files do not exist yet, so the bank falls back
 * to a built-in English list rather than crashing — per dev brief Section 5.4,
 * every I/O boundary fails gracefully to a default. Dropping the JSON files into
 * {@code src/main/resources/words/} later needs no code change.
 *
 * <p>Expected JSON shape (deliberately simple, no parser dependency needed):
 * <pre>
 * { "language": "en", "words": ["temple", "stone", "guardian"] }
 * </pre>
 *
 * <p>Tier bucketing counts <em>grapheme clusters</em>, not Java chars, so Khmer
 * words land in the right tier (Section 5.1).
 */
public class WordBank {

    /** Used whenever the word list resource is missing or unreadable. */
    private static final List<String> FALLBACK_WORDS = List.of(
            // short (Ahp / swarm) — 100 words
            "bone", "gold", "iron", "leaf", "palm", "reed", "root", "rope", "rust", "salt",
            "sand", "silk", "silt", "soil", "star", "wind", "rain", "fire", "frog", "toad",
            "newt", "worm", "wolf", "bear", "deer", "hawk", "wren", "lark", "dove", "hare",
            "mole", "rice", "corn", "bean", "herb", "seed", "husk", "jade", "ruby", "coal",
            "clay", "rock", "cave", "pond", "lake", "peak", "hill", "dale", "glen", "path",
            "road", "gate", "wall", "roof", "door", "lamp", "monk", "idol", "lore", "rune",
            "omen", "doom", "pyre", "vale", "tomb", "sage", "moss", "vine", "bark", "bird",
            "crow", "wasp", "moth", "oak", "elm", "fern", "ice", "fog", "mud", "webs",
            "cat", "pig", "cow", "hen", "egg", "ink", "rat", "orb", "urn", "key","sun",
            "sky", "owl", "ash", "mist", "moon", "fang", "claw", "dusk", "veil",

            // medium (Beisach / Stec Kantoab) — 70 words

            "altar", "amber", "ashes", "blade", "chant", "chime", "cloak", "crown", "crypt", "demon",
            "draft", "dream", "ember", "faith", "feast", "flare", "flock", "frost", "glyph", "grave",
            "groan", "grove", "haunt", "karma", "magic", "mango", "marsh", "mirth", "nectar", "omens",
            "orbit", "plume", "prayer", "quest", "raven", "realm", "relic", "ritual", "sacred", "scroll",
            "shrine", "sigil", "skull", "smoke", "sound", "spell", "spore", "stalk", "storm", "swamp",
            "sword", "totem", "trance", "valor", "venom", "vigil", "wisdom", "wrath","stone", "spire",
            "shade", "curse", "flame", "night", "ghost", "wraith", "temple", "shadow", "spirit", "hollow",

            // longer (Yeak / Naga) — 40 words
            "amethyst", "ancestor", "blossoms", "cinnamon", "crescent", "darkness", "doorways", "dreamers",
            "elephant", "festival", "footpath", "fountain", "fragment", "gargoyle", "gateways", "graveyard",
            "hallways", "harvests", "highland", "ironwood", "jungles", "lakeside", "labyrinth", "marigold",
            "medicine", "midnight", "monolith", "mountains", "mudbrick", "mythical", "nightfall", "obelisks",
            "outpost", "pagodas",  "lantern", "monsoon", "obsidian", "sandstone", "moonlight", "guardian",

            // long (Pret / boss) — 30 words
            "monastery", "catacombs", "excavation", "expedition", "foundation", "hieroglyph",
            "inheritance", "inscription", "lamentation", "malediction", "manuscript", "mausoleum",
            "necropolis", "overgrowth", "pilgrimage", "possession", "premonition", "resurrection",
            "sarcophagus", "stronghold", "subterranean", "wilderness", "monument", "sanctuary",
            "incantation", "reliquary", "procession","labyrinthine", "invocation", "apparition");

    private final Language language;
    private final List<String> words;
    private final Random random;

    public WordBank(Language language) {
        this(language, new Random());
    }

    /** Seeded constructor so wave composition is reproducible in tests. */
    public WordBank(Language language, Random random) {
        this.language = language == null ? Language.ENGLISH : language;
        this.random = random == null ? new Random() : random;
        this.words = loadWords(this.language);
    }

    /**
     * A word sized for the given enemy tier.
     *
     * <p>Falls back progressively rather than failing: if no word matches the
     * tier exactly, the closest available length is used, and if the bank is
     * somehow empty a hardcoded word is returned. A missing word must never stop
     * a wave from spawning.
     *
     * @param exclude words already in play, to avoid two enemies sharing a word
     */
    public String wordFor(EnemyType type, List<String> exclude) {
        int min = type.getMinWordLength();
        int max = type.getMaxWordLength();

        List<String> inTier = new ArrayList<>();
        for (String word : words) {
            if (GraphemeCounter.isWithin(word, min, max)
                    && (exclude == null || !exclude.contains(word))) {
                inTier.add(word);
            }
        }
        if (!inTier.isEmpty()) {
            return inTier.get(random.nextInt(inTier.size()));
        }

        // Nothing in tier — widen to anything unused.
        List<String> unused = new ArrayList<>();
        for (String word : words) {
            if (exclude == null || !exclude.contains(word)) {
                unused.add(word);
            }
        }
        if (!unused.isEmpty()) {
            return unused.get(random.nextInt(unused.size()));
        }
        return words.isEmpty() ? "temple" : words.get(random.nextInt(words.size()));
    }

    /**
     * A very short word for a thrown projectile (dev brief Section 5.2).
     *
     * <p>Kept to 2-3 characters deliberately: projectiles have a short time
     * budget, so a long word here would be impossible to intercept rather than
     * merely tense.
     */
    public String projectileWord(List<String> exclude) {
        List<String> candidates = new ArrayList<>();
        for (String word : words) {
            int length = GraphemeCounter.count(word);
            if (length >= 2 && length <= 3
                    && (exclude == null || !exclude.contains(word))) {
                candidates.add(word);
            }
        }
        if (!candidates.isEmpty()) {
            return candidates.get(random.nextInt(candidates.size()));
        }
        // The loaded list may have no words this short — fall back to a fixed
        // set rather than letting an attack fail to spawn.
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

    /** Immutable view of every loaded word. */
    public List<String> getWords() {
        return Collections.unmodifiableList(words);
    }

    public int size() {
        return words.size();
    }

    /** True when the JSON resource was missing and the built-in list is in use. */
    public boolean isUsingFallback() {
        return words == FALLBACK_WORDS;
    }

    // ---- loading ---------------------------------------------------------

    private static List<String> loadWords(Language language) {
        try (InputStream in = WordBank.class.getResourceAsStream(language.getWordListPath())) {
            if (in == null) {
                System.out.println("[WordBank] " + language.getWordListPath()
                        + " not found — using built-in fallback word list.");
                return FALLBACK_WORDS;
            }
            String json = readAll(in);
            List<String> parsed = parseWordArray(json);
            if (parsed.isEmpty()) {
                System.err.println("[WordBank] " + language.getWordListPath()
                        + " contained no words — using built-in fallback.");
                return FALLBACK_WORDS;
            }
            return List.copyOf(parsed);
        } catch (IOException | RuntimeException e) {
            System.err.println("[WordBank] Failed to read " + language.getWordListPath()
                    + " (" + e.getMessage() + ") — using built-in fallback.");
            return FALLBACK_WORDS;
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

    /**
     * Extracts the string entries of the {@code "words"} array.
     *
     * <p>Hand-rolled rather than pulling in a JSON dependency, because the shape
     * is fixed and tiny. It scans for the {@code "words"} key, then reads quoted
     * strings until the closing bracket, honouring backslash escapes so
     * Khmer codepoint escapes (backslash-u followed by four hex digits) survive.
     */
    static List<String> parseWordArray(String json) {
        List<String> result = new ArrayList<>();
        if (json == null) {
            return result;
        }
        int keyIndex = json.indexOf("\"words\"");
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