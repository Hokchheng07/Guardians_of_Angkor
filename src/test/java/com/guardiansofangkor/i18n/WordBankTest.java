package com.guardiansofangkor.i18n;

import com.guardiansofangkor.entities.EnemyType;
import com.guardiansofangkor.util.GraphemeCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WordBank — word supply and graceful fallback")
class WordBankTest {

    @Test
    @DisplayName("falls back to the built-in list when the JSON resource is absent")
    void fallsBackWhenResourceMissing() {
        WordBank bank = new WordBank(Language.ENGLISH, new Random(1));

        assertTrue(bank.isUsingFallback(),
                "words_en.json has not been added yet, so the fallback should be active");
        assertTrue(bank.size() > 0, "the fallback list must not be empty");
    }

    @Test
    @DisplayName("never returns null or empty, for any enemy type")
    void alwaysReturnsAWord() {
        WordBank bank = new WordBank(Language.ENGLISH, new Random(7));

        for (EnemyType type : EnemyType.values()) {
            String word = bank.wordFor(type, List.of());
            assertNotNull(word, type + " got a null word");
            assertFalse(word.isBlank(), type + " got a blank word");
        }
    }

    @Test
    @DisplayName("respects the requested word-length tier when it can")
    void respectsTierWhenPossible() {
        WordBank bank = new WordBank(Language.ENGLISH, new Random(3));

        for (int i = 0; i < 50; i++) {
            String word = bank.wordFor(EnemyType.AHP, List.of());
            assertTrue(GraphemeCounter.count(word) <= EnemyType.AHP.getMaxWordLength(),
                    "swarm enemies should get short words, got: " + word);
        }
    }

    @Test
    @DisplayName("avoids words already in play")
    void avoidsExcludedWords() {
        WordBank bank = new WordBank(Language.ENGLISH, new Random(11));

        List<String> inPlay = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String word = bank.wordFor(EnemyType.BEISACH, inPlay);
            assertFalse(inPlay.contains(word), "reused a word already on the field: " + word);
            inPlay.add(word);
        }
    }

    @Test
    @DisplayName("parses the documented JSON shape")
    void parsesJsonArray() {
        String json = "{ \"language\": \"en\", \"words\": [\"temple\", \"stone\", \"naga\"] }";

        List<String> words = WordBank.parseWordArray(json);

        assertEquals(List.of("temple", "stone", "naga"), words);
    }

    @Test
    @DisplayName("parses unicode escapes so Khmer survives the round trip")
    void parsesUnicodeEscapes() {
        // Codepoint 1782 (hex) is the Khmer letter KO. Written escaped here so
        // the test proves the parser decodes it, rather than the file carrying
        // the literal glyph.
        String json = "{ \"words\": [\"\\u1782\"] }";

        List<String> words = WordBank.parseWordArray(json);

        assertEquals(1, words.size());
        assertEquals("\u1782", words.get(0));
    }

    @Test
    @DisplayName("malformed JSON yields no words rather than throwing")
    void malformedJsonIsSafe() {
        assertTrue(WordBank.parseWordArray(null).isEmpty());
        assertTrue(WordBank.parseWordArray("").isEmpty());
        assertTrue(WordBank.parseWordArray("{ \"other\": [1,2] }").isEmpty());
        assertTrue(WordBank.parseWordArray("{ \"words\": ").isEmpty());
    }
}
