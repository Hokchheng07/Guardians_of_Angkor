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

@DisplayName("WordBank — word supply, difficulty banding and graceful fallback")
class WordBankTest {

    private static WordBank bank(long seed) {
        return new WordBank(Language.ENGLISH, new Random(seed));
    }

    // ---- loading -----------------------------------------------------------

    @Test
    @DisplayName("loads the real word list rather than the built-in fallback")
    void loadsTheShippedList() {
        WordBank bank = bank(1);

        // This asserts the resource is actually reachable. It has silently not
        // been once already — the file was named wordBankEng.json while Language
        // asked for words_en.json, so every run quietly used the ~80-word
        // fallback and nobody noticed.
        assertFalse(bank.isUsingFallback(),
                "words_en.json should be on the classpath and parseable");
        assertTrue(bank.size() > 300,
                "expected the full vocabulary, got " + bank.size() + " words");
    }

    @Test
    @DisplayName("every declared pool has words in it")
    void everyPoolIsPopulated() {
        WordBank bank = bank(2);

        for (String pool : List.of("tiny", "short", "medium", "long", "epic", "tricky")) {
            assertFalse(bank.getPool(pool).isEmpty(), "pool '" + pool + "' is empty");
        }
        for (String rank : List.of("novice", "adept", "master", "legend")) {
            assertFalse(bank.getBossPool(rank).isEmpty(), "boss rank '" + rank + "' is empty");
        }
    }

    @Test
    @DisplayName("pools stay inside the length band they are named for")
    void poolsRespectTheirLengthBands() {
        WordBank bank = bank(3);

        assertLengthsWithin(bank.getPool("tiny"), 2, 3);
        assertLengthsWithin(bank.getPool("short"), 4, 5);
        assertLengthsWithin(bank.getPool("medium"), 6, 7);
        assertLengthsWithin(bank.getPool("long"), 8, 10);
        assertLengthsWithin(bank.getPool("epic"), 11, 99);
    }

    @Test
    @DisplayName("boss ranks climb in length, novice through legend")
    void bossRanksClimb() {
        WordBank bank = bank(4);

        assertLengthsWithin(bank.getBossPool("novice"), 5, 7);
        assertLengthsWithin(bank.getBossPool("adept"), 8, 10);
        assertLengthsWithin(bank.getBossPool("master"), 11, 13);
        assertLengthsWithin(bank.getBossPool("legend"), 14, 99);
    }

    @Test
    @DisplayName("boss vocabulary never leaks into the ordinary spawn pool")
    void bossWordsAreReserved() {
        WordBank bank = bank(5);

        // Otherwise the climactic word could already have turned up on a
        // Beisach in level two, which costs the fight all of its weight.
        for (String rank : List.of("novice", "adept", "master", "legend")) {
            for (String word : bank.getBossPool(rank)) {
                assertFalse(bank.getWords().contains(word),
                        "'" + word + "' is both a boss word and an ordinary one");
            }
        }
    }

    @Test
    @DisplayName("the tricky pool is graded by typing difficulty, not by length")
    void trickyPoolIsItsOwnThing() {
        WordBank bank = bank(6);
        List<String> tricky = bank.getPool("tricky");

        assertFalse(tricky.isEmpty());
        // It deliberately overlaps the length pools — the point is that a short
        // word can still be hard. Assert only that it is not secretly one of
        // them by another name.
        assertTrue(tricky.stream().anyMatch(w -> GraphemeCounter.count(w) <= 6),
                "the pool should contain genuinely short words");
        assertTrue(tricky.contains("rhythm") && tricky.contains("sphinx"),
                "the canonical awkward words should be in it");
    }

    // ---- flavour -----------------------------------------------------------

    @Test
    @DisplayName("the vocabulary is about Angkor, not generic fantasy")
    void vocabularyIsAngkorFlavoured() {
        // The game is called Guardians of Angkor. The words the player types for
        // an hour are the loudest place it can keep saying so.
        WordBank bank = bank(8);
        List<String> all = bank.getWords();

        for (String term : List.of("angkor", "apsara", "prasat", "devata", "garuda",
                "laterite", "sandstone", "baray", "bayon", "banteay", "stupa",
                "bodhi", "lintel", "naga", "khmer", "gopura", "kulen", "mekong")) {
            assertTrue(all.contains(term),
                    "'" + term + "' should be somewhere in the word bank");
        }
    }

    @Test
    @DisplayName("uncommon, memorable words are in there to be met once")
    void rareWordsExist() {
        WordBank bank = bank(9);
        List<String> all = bank.getWords();

        for (String term : List.of("petrichor", "cenotaph", "oubliette",
                "palanquin", "reliquary", "censer", "verdigris")) {
            assertTrue(all.contains(term), "'" + term + "' is missing");
        }
    }

    @Test
    @DisplayName("no lecture-hall vocabulary")
    void modernWordsAreGone() {
        // Technically fine English, but it belongs to a seminar rather than to a
        // temple swallowed by jungle.
        WordBank bank = bank(10);
        List<String> all = bank.getWords();

        for (String term : List.of("examination", "observation", "translation",
                "understanding", "civilization", "archaeology", "scaffolding")) {
            assertFalse(all.contains(term),
                    "'" + term + "' does not belong in this game");
        }
    }

    @Test
    @DisplayName("no word appears in both singular and plural")
    void noPluralDuplicates() {
        WordBank bank = bank(12);
        List<String> all = bank.getWords();

        for (String word : all) {
            if (word.endsWith("ss") || !word.endsWith("s")) {
                continue;
            }
            assertFalse(all.contains(word.substring(0, word.length() - 1)),
                    "'" + word + "' duplicates its own singular");
        }
    }

    @Test
    @DisplayName("nothing carries punctuation, spaces or capitals")
    void everyWordIsTypeable() {
        // The typing field is fed one plain word at a time; anything else means
        // a key the player cannot reach or a character they cannot see.
        WordBank bank = bank(14);

        for (String word : bank.getWords()) {
            assertTrue(word.matches("[a-z]+"), "'" + word + "' is not plainly typeable");
        }
        for (String rank : List.of("novice", "adept", "master", "legend")) {
            for (String word : bank.getBossPool(rank)) {
                assertTrue(word.matches("[a-z]+"), "'" + word + "' is not plainly typeable");
            }
        }
    }

    // ---- selection ---------------------------------------------------------

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
    @DisplayName("respects the requested word-length tier when it can")
    void respectsTierWhenPossible() {
        WordBank bank = bank(3);

        for (int i = 0; i < 50; i++) {
            String word = bank.wordFor(EnemyType.AHP, List.of());
            assertTrue(GraphemeCounter.count(word) <= EnemyType.AHP.getMaxWordLength(),
                    "swarm enemies should get short words, got: " + word);
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

    // ---- policy ------------------------------------------------------------

    @Test
    @DisplayName("resolves a level to the band that covers it")
    void resolvesBands() {
        WordBank bank = bank(13);

        WordPolicy early = bank.policyFor("easy", 1);
        WordPolicy late = bank.policyFor("easy", 10);

        assertTrue(early.restrictsPools(), "Easy level 1 should be banded");
        assertTrue(early.getPoolNames().contains("tiny"));
        assertFalse(early.getPoolNames().contains("long"),
                "Easy must not open with eight-letter words");
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

        // A typo in the tuning table should make the game generic, not
        // unplayable — an empty vocabulary would stall every spawn.
        WordPolicy nonsense = bank.policyFor("brutal", 3);

        assertFalse(nonsense.restrictsPools());
        assertFalse(bank.vocabularyFor(nonsense).isEmpty());
    }

    @Test
    @DisplayName("a banded pick never leaves its band")
    void bandedPicksStayInTheBand() {
        WordBank bank = bank(23);
        WordPolicy easyEarly = bank.policyFor("easy", 1);
        List<String> allowed = bank.vocabularyFor(easyEarly);

        for (int i = 0; i < 120; i++) {
            String word = bank.wordFor(EnemyType.BEISACH, null, easyEarly, 0, 0);
            assertTrue(allowed.contains(word),
                    "'" + word + "' is outside the Easy level 1 band");
        }
    }

    @Test
    @DisplayName("a heavy enemy in a gentle band gets the band's longest, not a long word")
    void heavyTypesAreClampedToTheBand() {
        WordBank bank = bank(29);
        WordPolicy easyEarly = bank.policyFor("easy", 1);

        // A Pret asks for 8-12 letters. Easy's opening band tops out at 5. It
        // must take the longest thing the band has rather than reaching outside
        // it, which is precisely the "too hard, too early" problem.
        for (int i = 0; i < 60; i++) {
            String word = bank.wordFor(EnemyType.PRET, null, easyEarly, 0, 0);
            assertTrue(GraphemeCounter.count(word) <= 5,
                    "Easy level 1 served a " + GraphemeCounter.count(word)
                            + "-letter word to a Pret: " + word);
        }
    }

    @Test
    @DisplayName("a boss word comes from the rank the policy names")
    void bossWordsFollowTheRank() {
        WordBank bank = bank(31);
        WordPolicy easyEarly = bank.policyFor("easy", 5);

        String word = bank.bossWord(null, easyEarly);

        assertTrue(bank.getBossPool("novice").contains(word),
                "Easy's early Naga should draw a novice word, got: " + word);
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

        // Drain the novice rank several times over. Every call must still
        // produce something — a wave that cannot spawn is far worse than a
        // repeated word.
        for (int i = 0; i < bank.getBossPool("novice").size() * 3 + 10; i++) {
            String word = bank.bossWord(null, policy);
            assertNotNull(word);
            assertFalse(word.isBlank());
        }
    }

    // ---- projectiles and pickups -------------------------------------------

    @Test
    @DisplayName("projectile and pickup words stay very short")
    void shortWordsForShortDeadlines() {
        WordBank bank = bank(43);

        for (int i = 0; i < 60; i++) {
            assertTrue(GraphemeCounter.count(bank.projectileWord(null)) <= 3);
            assertTrue(GraphemeCounter.count(bank.pickupWord(null)) <= 3);
        }
    }

    // ---- helpers -----------------------------------------------------------

    private static void assertLengthsWithin(List<String> pool, int min, int max) {
        for (String word : pool) {
            int length = GraphemeCounter.count(word);
            assertTrue(length >= min && length <= max,
                    "'" + word + "' is " + length + " long, outside " + min + "-" + max);
        }
    }
}
