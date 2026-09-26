package com.guardiansofangkor.i18n;

import com.guardiansofangkor.entities.EnemyType;
import com.guardiansofangkor.util.GraphemeCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors {@link WordBankTest}, but against {@code words_km.json}.
 *
 * <p>Two things are deliberately different from the English suite, both
 * because Khmer script behaves differently, not because the rules are
 * relaxed:
 *
 * <ul>
 *   <li>Khmer bands here are tiny&lt;=2, short=3, medium=4, long=5-6,
 *       epic=7-8 grapheme clusters — <em>not</em> the same numbers as
 *       {@code words_en.json}, and not a simple "Khmer is shorter" guess
 *       either. Java's {@link com.guardiansofangkor.util.GraphemeCounter}
 *       (backed by {@code java.text.BreakIterator}) does not merge a Khmer
 *       coeng (subscript-consonant) sequence into one grapheme the way some
 *       other Unicode libraries do, so real counts run higher than a naive
 *       check suggests — these bands were set by running the actual
 *       {@code GraphemeCounter} against every word, not by eye. See the
 *       {@code _readme} field in {@code words_km.json}.</li>
 *   <li>{@code actionWordsNeverCollideWithTheVerse} cannot split a sentence on
 *       spaces the way the English test does, because Khmer does not put
 *       spaces between words. It checks for the action word as a substring
 *       of the whole sentence instead, which is a stricter and more honest
 *       check for this script.</li>
 * </ul>
 *
 * <p>This is a starter vocabulary (~70 words, versus English's ~960), so
 * thresholds below are set to what the current file actually contains, not
 * to English's numbers. Raise them as the word list grows.
 */
@DisplayName("WordBank (Khmer) — word supply, difficulty banding and graceful fallback")
class WordBankKhmerTest {

    private static WordBank bank(long seed) {
        return new WordBank(Language.KHMER, new Random(seed));
    }

    // ---- loading -------------------------------------------------------

    @Test
    @DisplayName("loads words_km.json rather than the built-in English fallback")
    void loadsTheShippedList() {
        WordBank bank = bank(1);

        assertFalse(bank.isUsingFallback(),
                "words_km.json should be on the classpath and parseable");
        assertTrue(bank.size() >= 60,
                "expected the starter Khmer vocabulary, got " + bank.size() + " words");
    }

    @Test
    @DisplayName("every declared pool has words in it")
    void everyPoolIsPopulated() {
        WordBank bank = bank(2);

        for (String pool : List.of("tiny", "short", "medium", "long", "epic",
                "tricky", "lore")) {
            assertFalse(bank.getPool(pool).isEmpty(), "pool '" + pool + "' is empty");
        }
        for (String rank : List.of("novice", "adept", "master", "legend")) {
            assertFalse(bank.getBossPool(rank).isEmpty(), "boss rank '" + rank + "' is empty");
        }
    }

    @Test
    @DisplayName("pools stay inside the Khmer length band they are named for")
    void poolsRespectTheirLengthBands() {
        WordBank bank = bank(3);

        // Recalibrated for Khmer grapheme clusters — see class Javadoc.
        assertLengthsWithin(bank.getPool("tiny"), 1, 2);
        assertLengthsWithin(bank.getPool("short"), 3, 3);
        assertLengthsWithin(bank.getPool("medium"), 4, 4);
        assertLengthsWithin(bank.getPool("long"), 5, 6);
        assertLengthsWithin(bank.getPool("epic"), 7, 99);
    }

    @Test
    @DisplayName("boss vocabulary never leaks into the ordinary spawn pool")
    void bossWordsAreReserved() {
        WordBank bank = bank(5);

        for (String rank : List.of("novice", "adept", "master", "legend")) {
            for (String word : bank.getBossPool(rank)) {
                assertFalse(bank.getWords().contains(word),
                        "'" + word + "' is both a boss word and an ordinary one");
            }
        }
    }

    @Test
    @DisplayName("no word lives in two pools")
    void poolsDoNotOverlap() {
        // This is the whole reason the difficulty bands work — see the
        // English test's comment for the "glyph/myrrh/psalm" incident this
        // guards against. Same rule, same reasoning, different script.
        WordBank bank = bank(20);
        List<String> names = List.of("tiny", "short", "medium", "long", "epic",
                "tricky", "lore");

        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                List<String> a = new ArrayList<>(bank.getPool(names.get(i)));
                a.retainAll(bank.getPool(names.get(j)));
                assertTrue(a.isEmpty(), names.get(i) + " and " + names.get(j)
                        + " both contain " + a);
            }
        }
    }

    // ---- action words ----------------------------------------------------

    @Test
    @DisplayName("action words are an imperative pool of their own")
    void actionWordsExist() {
        WordBank bank = bank(30);
        List<String> action = bank.getActionWords();

        assertFalse(action.isEmpty(), "boss venom has nothing to say");
        assertTrue(action.size() >= 15,
                "too few to avoid repeating within one fight: " + action.size());
        for (String word : action) {
            int len = GraphemeCounter.count(word);
            assertTrue(len >= 1 && len <= 4,
                    "'" + word + "' is the wrong size for a five-second order");
            assertTrue(word.matches("[\\u1780-\\u17FF]+"),
                    "'" + word + "' is not plain Khmer script");
        }
    }

    @Test
    @DisplayName("an action word is never anything else in the game")
    void actionWordsAreExclusive() {
        WordBank bank = bank(31);
        List<String> action = bank.getActionWords();

        for (String word : action) {
            assertFalse(bank.getWords().contains(word),
                    "'" + word + "' is also an enemy word");
            assertFalse(bank.getProjectileWords().contains(word),
                    "'" + word + "' is also a thrown-bolt word");
            for (String rank : List.of("novice", "adept", "master", "legend")) {
                assertFalse(bank.getBossPool(rank).contains(word),
                        "'" + word + "' is also a boss word");
            }
        }
    }

    @Test
    @DisplayName("no action word appears inside a boss paragraph, as a substring or whole")
    void actionWordsNeverCollideWithTheVerse() {
        // Khmer does not space-delimit words, so splitting on " " (as the
        // English test does) would not isolate anything. A substring check
        // is the stricter, more honest equivalent for this script — and it
        // is the check that actually caught a real bug in this file's first
        // draft (action words like "ការពារ" and "មើល" were embedded in the
        // sample paragraphs before this test existed).
        WordBank bank = bank(32);
        List<String> action = bank.getActionWords();

        for (String sentence : bank.getAllParagraphSentences()) {
            for (String word : action) {
                assertFalse(sentence.contains(word),
                        "'" + word + "' (action) appears inside verse sentence: " + sentence);
            }
        }
    }

    @Test
    @DisplayName("venom draws orders, not nouns")
    void venomDrawsFromTheActionPool() {
        WordBank bank = bank(33);

        for (int i = 0; i < 200; i++) {
            String venom = bank.venomWord(List.of());
            assertTrue(bank.getActionWords().contains(venom),
                    "'" + venom + "' did not come from the action pool");
        }
    }

    // ---- script sanity -----------------------------------------------------

    @Test
    @DisplayName("nothing carries Latin letters, digits, spaces or punctuation")
    void everyWordIsPlainKhmerScript() {
        // The typing field is fed one continuous word at a time; anything
        // else means a key the player cannot reach or a character that does
        // not belong. U+1780-U+17FF is the Khmer Unicode block.
        WordBank bank = bank(14);

        for (String word : bank.getWords()) {
            assertTrue(word.matches("[\\u1780-\\u17FF]+"),
                    "'" + word + "' is not plain Khmer script");
        }
        for (String rank : List.of("novice", "adept", "master", "legend")) {
            for (String word : bank.getBossPool(rank)) {
                assertTrue(word.matches("[\\u1780-\\u17FF]+"),
                        "'" + word + "' is not plain Khmer script");
            }
        }
    }

    // ---- selection -----------------------------------------------------

    @Test
    @DisplayName("never returns null or empty, for any enemy type")
    void alwaysReturnsAWord() {
        WordBank bank = bank(7);

        for (EnemyType type : EnemyType.values()) {
            String word = bank.wordFor(type, List.of());
            assertNotNull(word, type + " got a null word");
            assertFalse(word.isBlank(), type + " got a blank word");
        }
    }

    @Test
    @DisplayName("avoids words already in play")
    void avoidsExcludedWords() {
        WordBank bank = bank(11);

        List<String> inPlay = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String word = bank.wordFor(EnemyType.BEISACH, inPlay);
            assertFalse(inPlay.contains(word), "reused a word already on the field: " + word);
            inPlay.add(word);
        }
    }

    // ---- policy ----------------------------------------------------------

    @Test
    @DisplayName("resolves a level to the band that covers it (tier/pool names match English)")
    void resolvesBands() {
        WordBank bank = bank(13);

        WordPolicy early = bank.policyFor("easy", 1);
        WordPolicy late = bank.policyFor("easy", 10);

        assertTrue(early.restrictsPools(), "Easy level 1 should be banded");
        assertTrue(early.getPoolNames().contains("tiny"));
        assertFalse(early.getPoolNames().contains("long"),
                "Easy must not open with the longest Khmer compounds");
        assertFalse(late.getPoolNames().contains("tiny"),
                "by the finale the tiny pool should have been left behind");
    }

    @Test
    @DisplayName("a level past the last band still resolves, rather than falling off")
    void unboundedLevelsUseTheLastBand() {
        WordBank bank = bank(17);

        WordPolicy far = bank.policyFor("endless", 900);

        assertNotNull(far);
        assertTrue(far.restrictsPools(), "Endless must keep a band at any depth");
    }

    @Test
    @DisplayName("an unknown tier widens rather than empties")
    void unknownTiersAreUnrestricted() {
        WordBank bank = bank(19);

        WordPolicy nonsense = bank.policyFor("brutal", 3);

        assertFalse(nonsense.restrictsPools());
        assertFalse(bank.vocabularyFor(nonsense).isEmpty());
    }

    @Test
    @DisplayName("a heavy enemy in a gentle band gets the band's longest, not a long word")
    void heavyTypesAreClampedToTheBand() {
        WordBank bank = bank(29);
        WordPolicy easyEarly = bank.policyFor("easy", 1);

        // Easy level 1's band is tiny+short here, which tops out at 3
        // graphemes. A Pret wants 8-12 English letters worth of difficulty,
        // but must still take the band's longest rather than reach outside
        // it — same guarantee as English, much smaller numbers.
        for (int i = 0; i < 60; i++) {
            String word = bank.wordFor(EnemyType.PRET, null, easyEarly, 0, 0);
            assertTrue(GraphemeCounter.count(word) <= 3,
                    "Easy level 1 served a " + GraphemeCounter.count(word)
                            + "-grapheme word to a Pret: " + word);
        }
    }

    @Test
    @DisplayName("a boss word comes from the rank the policy names")
    void bossWordsFollowTheRank() {
        WordBank bank = bank(31);
        WordPolicy easyEarly = bank.policyFor("easy", 5);

        String word = bank.bossWord(null, easyEarly);

        assertTrue(bank.getBossPool("novice").contains(word),
                "Easy's early boss should draw a novice word, got: " + word);
    }

    @Test
    @DisplayName("the final boss word is the longest unused one in its rank")
    void finalBossTakesTheHardestWord() {
        WordBank bank = bank(37);
        WordPolicy hardLate = bank.policyFor("hard", 15);

        String word = bank.finalBossWord(null, hardLate);

        assertTrue(bank.getBossPool("legend").contains(word),
                "Hard's finale should draw a legend word, got: " + word);
        for (String other : bank.getBossPool("legend")) {
            assertTrue(GraphemeCounter.count(word) >= GraphemeCounter.count(other),
                    "'" + word + "' is not the longest in its rank; '" + other + "' is longer");
        }
    }

    @Test
    @DisplayName("a boss rank exhausted mid-run widens instead of stalling")
    void exhaustedBossRankWidens() {
        WordBank bank = bank(41);
        WordPolicy policy = bank.policyFor("easy", 5);

        for (int i = 0; i < bank.getBossPool("novice").size() * 3 + 10; i++) {
            String word = bank.bossWord(null, policy);
            assertNotNull(word);
            assertFalse(word.isBlank());
        }
    }

    // ---- helpers -----------------------------------------------------------

    private static void assertLengthsWithin(List<String> pool, int min, int max) {
        for (String word : pool) {
            int length = GraphemeCounter.count(word);
            assertTrue(length >= min && length <= max,
                    "'" + word + "' is " + length + " graphemes, outside " + min + "-" + max);
        }
    }
}