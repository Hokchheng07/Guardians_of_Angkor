package com.guardiansofangkor.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small, dependency-free JSON reader — just enough to load the word bank.
 *
 * <p>The word bank used to be read by scanning for {@code "key"} and grabbing
 * the bracketed run after it. That worked only while every key in the file was
 * unique, which stopped being true the moment the file grew a per-difficulty
 * tuning table (both a pool list and each level band have a {@code pools} key).
 * A scanner cannot tell those apart; a parser can, so this replaces it rather
 * than patching around it.
 *
 * <p>Deliberately not a general-purpose library: it parses the JSON subset the
 * game's own resources use (objects, arrays, strings, numbers, booleans, null),
 * rejects anything malformed with a {@link JsonException}, and stops there. It
 * is not a dependency to avoid the team having to resolve one for a single file.
 *
 * <p>Values come back as plain Java: {@code Map<String, Object>} for objects,
 * {@code List<Object>} for arrays, {@code String}, {@code Double},
 * {@code Boolean} and {@code null}. The typed helpers at the bottom
 * ({@link #objectAt}, {@link #stringsAt}, …) exist so callers do not litter
 * themselves with unchecked casts.
 */
public final class Json {

    /** Thrown when the text is not valid JSON. Always carries the offset. */
    public static class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }
    }

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    /**
     * Parses a complete JSON document.
     *
     * @return the root value, typically a {@code Map<String, Object>}
     * @throws JsonException if the text is null, empty or malformed
     */
    public static Object parse(String text) {
        if (text == null) {
            throw new JsonException("no JSON text");
        }
        Json parser = new Json(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.pos < text.length()) {
            throw new JsonException("trailing content at offset " + parser.pos);
        }
        return value;
    }

    // ---- grammar -----------------------------------------------------------

    private Object readValue() {
        if (pos >= text.length()) {
            throw new JsonException("unexpected end of input");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return readObject();
            case '[':
                return readArray();
            case '"':
                return readString();
            case 't':
                return readLiteral("true", Boolean.TRUE);
            case 'f':
                return readLiteral("false", Boolean.FALSE);
            case 'n':
                return readLiteral("null", null);
            default:
                return readNumber();
        }
    }

    private Map<String, Object> readObject() {
        // LinkedHashMap so the file's own ordering survives — level bands are
        // written in ascending order and read back the same way.
        Map<String, Object> result = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return result;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            result.put(key, readValue());
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return result;
            }
            if (c != ',') {
                throw new JsonException("expected ',' or '}' at offset " + (pos - 1));
            }
        }
    }

    private List<Object> readArray() {
        List<Object> result = new ArrayList<>();
        expect('[');
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return result;
        }
        while (true) {
            skipWhitespace();
            result.add(readValue());
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return result;
            }
            if (c != ',') {
                throw new JsonException("expected ',' or ']' at offset " + (pos - 1));
            }
        }
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw new JsonException("unterminated string");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char escape = next();
            switch (escape) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    // Khmer vocabulary is commonly written as \\uXXXX escapes by
                    // whatever tool exported it, so this branch is load-bearing.
                    if (pos + 4 > text.length()) {
                        throw new JsonException("truncated \\u escape at offset " + pos);
                    }
                    String hex = text.substring(pos, pos + 4);
                    pos += 4;
                    try {
                        sb.append((char) Integer.parseInt(hex, 16));
                    } catch (NumberFormatException e) {
                        throw new JsonException("bad \\u escape '" + hex + "'");
                    }
                }
                default -> throw new JsonException("bad escape '\\" + escape + "'");
            }
        }
    }

    private Double readNumber() {
        int start = pos;
        while (pos < text.length() && "-+.eE0123456789".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        if (start == pos) {
            throw new JsonException("unexpected character '" + text.charAt(pos)
                    + "' at offset " + pos);
        }
        try {
            return Double.valueOf(text.substring(start, pos));
        } catch (NumberFormatException e) {
            throw new JsonException("bad number at offset " + start);
        }
    }

    private Object readLiteral(String literal, Object value) {
        if (!text.startsWith(literal, pos)) {
            throw new JsonException("expected '" + literal + "' at offset " + pos);
        }
        pos += literal.length();
        return value;
    }

    // ---- scanning ----------------------------------------------------------

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private char next() {
        if (pos >= text.length()) {
            throw new JsonException("unexpected end of input");
        }
        return text.charAt(pos++);
    }

    private void expect(char expected) {
        char c = next();
        if (c != expected) {
            throw new JsonException("expected '" + expected + "' but found '" + c
                    + "' at offset " + (pos - 1));
        }
    }

    // ---- typed access ------------------------------------------------------
    // Every helper returns an empty value rather than throwing when the shape is
    // not what the caller hoped for. A word bank with one mistyped section
    // should degrade to fewer words, not take the game down.

    /** The object at {@code key}, or an empty map. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> objectAt(Map<String, Object> parent, String key) {
        if (parent == null) {
            return Map.of();
        }
        Object value = parent.get(key);
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    /** The array at {@code key}, or an empty list. */
    @SuppressWarnings("unchecked")
    public static List<Object> arrayAt(Map<String, Object> parent, String key) {
        if (parent == null) {
            return List.of();
        }
        Object value = parent.get(key);
        return value instanceof List ? (List<Object>) value : List.of();
    }

    /** The array at {@code key} as strings, skipping any non-string entries. */
    public static List<String> stringsAt(Map<String, Object> parent, String key) {
        List<String> result = new ArrayList<>();
        for (Object item : arrayAt(parent, key)) {
            if (item instanceof String s && !s.isBlank()) {
                result.add(s.trim());
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** The string at {@code key}, or {@code fallback}. */
    public static String stringAt(Map<String, Object> parent, String key, String fallback) {
        if (parent == null) {
            return fallback;
        }
        Object value = parent.get(key);
        return value instanceof String s ? s : fallback;
    }

    /** The number at {@code key} as an int, or {@code fallback}. */
    public static int intAt(Map<String, Object> parent, String key, int fallback) {
        if (parent == null) {
            return fallback;
        }
        Object value = parent.get(key);
        return value instanceof Number n ? n.intValue() : fallback;
    }

    /** Every object in the array at {@code key}, skipping non-object entries. */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> objectsAt(Map<String, Object> parent, String key) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : arrayAt(parent, key)) {
            if (item instanceof Map) {
                result.add((Map<String, Object>) item);
            }
        }
        return result;
    }
}
