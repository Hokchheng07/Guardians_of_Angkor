package com.guardiansofangkor.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Json — the small reader the word bank loads through")
class JsonTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(String text) {
        return (Map<String, Object>) Json.parse(text);
    }

    @Test
    @DisplayName("reads the shapes the word bank actually uses")
    void readsNestedShapes() {
        Map<String, Object> root = object("""
                {
                  "language": "en",
                  "pools": { "tiny": ["ash", "orb"] },
                  "difficulties": {
                    "easy": { "bands": [ { "throughLevel": 5, "pools": ["tiny"] } ] }
                  }
                }
                """);

        assertEquals("en", Json.stringAt(root, "language", ""));
        assertEquals(List.of("ash", "orb"),
                Json.stringsAt(Json.objectAt(root, "pools"), "tiny"));

        Map<String, Object> easy =
                Json.objectAt(Json.objectAt(root, "difficulties"), "easy");
        List<Map<String, Object>> bands = Json.objectsAt(easy, "bands");

        assertEquals(1, bands.size());
        assertEquals(5, Json.intAt(bands.get(0), "throughLevel", -1));
        assertEquals(List.of("tiny"), Json.stringsAt(bands.get(0), "pools"));
    }

    @Test
    @DisplayName("tells apart two keys with the same name at different depths")
    void distinguishesRepeatedKeys() {
        // This is the reason the old key-scanning loader had to be replaced: the
        // word bank has a "pools" list of vocabulary and a "pools" field inside
        // every level band, and a scanner cannot tell which one it found.
        Map<String, Object> root = object("""
                { "pools": { "short": ["mist"] },
                  "bands": [ { "pools": ["short"] } ] }
                """);

        assertEquals(List.of("mist"), Json.stringsAt(Json.objectAt(root, "pools"), "short"));
        assertEquals(List.of("short"),
                Json.stringsAt(Json.objectsAt(root, "bands").get(0), "pools"));
    }

    @Test
    @DisplayName("decodes unicode escapes so Khmer survives the round trip")
    void decodesUnicodeEscapes() {
        // Codepoint 1782 is the Khmer letter KO. Written escaped here so the
        // test proves the parser decodes it rather than the file carrying the
        // literal glyph.
        Map<String, Object> root = object("{ \"words\": [\"\\u1782\"] }");

        assertEquals(List.of("\u1782"), Json.stringsAt(root, "words"));
    }

    @Test
    @DisplayName("handles escapes, empty containers and negative numbers")
    void handlesTheAwkwardCorners() {
        Map<String, Object> root = object("""
                { "quoted": "a \\"b\\" c", "slash": "a\\\\b",
                  "empty": {}, "none": [], "shift": -2, "flag": true, "gone": null }
                """);

        assertEquals("a \"b\" c", Json.stringAt(root, "quoted", ""));
        assertEquals("a\\b", Json.stringAt(root, "slash", ""));
        assertTrue(Json.objectAt(root, "empty").isEmpty());
        assertTrue(Json.arrayAt(root, "none").isEmpty());
        assertEquals(-2, Json.intAt(root, "shift", 0));
        assertEquals(Boolean.TRUE, root.get("flag"));
        assertTrue(root.containsKey("gone"));
    }

    @Test
    @DisplayName("preserves key order, so level bands stay in the order written")
    void preservesOrder() {
        Map<String, Object> root = object("{ \"c\": 1, \"a\": 2, \"b\": 3 }");

        assertEquals(List.of("c", "a", "b"), List.copyOf(root.keySet()));
    }

    @Test
    @DisplayName("rejects malformed text instead of returning something wrong")
    void rejectsMalformedText() {
        // Loud failure matters here: WordBank catches it and falls back to the
        // built-in list, which is a recoverable outcome. Silently parsing half a
        // file would not be.
        assertThrows(Json.JsonException.class, () -> Json.parse(null));
        assertThrows(Json.JsonException.class, () -> Json.parse(""));
        assertThrows(Json.JsonException.class, () -> Json.parse("{ \"a\": }"));
        assertThrows(Json.JsonException.class, () -> Json.parse("{ \"a\": [1, 2 }"));
        assertThrows(Json.JsonException.class, () -> Json.parse("{ \"a\": 1 } trailing"));
        assertThrows(Json.JsonException.class, () -> Json.parse("{ \"unterminated\": \"x }"));
    }

    @Test
    @DisplayName("typed helpers return empties rather than throwing on a wrong shape")
    void typedHelpersDegradeGracefully() {
        // One mistyped section should cost the game some vocabulary, not the
        // whole word bank.
        Map<String, Object> root = object("{ \"pools\": \"not an object\" }");

        assertTrue(Json.objectAt(root, "pools").isEmpty());
        assertTrue(Json.arrayAt(root, "pools").isEmpty());
        assertTrue(Json.stringsAt(root, "pools").isEmpty());
        assertTrue(Json.objectsAt(root, "missing").isEmpty());
        assertEquals(7, Json.intAt(root, "missing", 7));
        assertEquals("x", Json.stringAt(null, "anything", "x"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("the shipped word list parses")
    void theShippedListParses() throws Exception {
        // A guard against someone hand-editing words_en.json into invalid JSON:
        // the game would still run, but silently on the fallback list, which is
        // exactly the failure that went unnoticed once already.
        try (var in = JsonTest.class.getResourceAsStream("/words/words_en.json")) {
            assertTrue(in != null, "/words/words_en.json is not on the classpath");
            String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Object> root = (Map<String, Object>) Json.parse(text);

            assertTrue(root.containsKey("pools"));
            assertTrue(root.containsKey("bossPools"));
            assertTrue(root.containsKey("difficulties"));
        }
    }
}
