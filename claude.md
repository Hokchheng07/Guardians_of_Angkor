# Guardians of Angkor — Word Defense

Java Swing typing-defense game (ISTAD OOP course project). Player types
words displayed above approaching enemies to defeat them before they
reach the temple.

## Core mechanic — target resolution
Enemy targeting is prefix-based, not pre-locked: after each keystroke,
filter all active enemies to those whose word starts with the typed
string so far. Multiple candidates can be highlighted simultaneously
(e.g. "ca" matches both "can" and "cat"); the target locks only once
the prefix uniquely identifies one enemy. See WordMatcher.

## Bilingual support
Word lists exist in English and Khmer (words_en.json / words_km.json).
Khmer difficulty tiers are bucketed by grapheme cluster count, not
Java char count — a Khmer visual character can be multiple chars.
Khmer text requires the bundled Noto Sans Khmer font; do not assume
default Swing fonts render it.

## Enemy roster (see bestiary for word-length tiers)
Beisach, Yeak, Ahp, Pret, Stec Kantoab, Naga, Krong Reap

## Main class
`com.guardiansofangkor.Main` — set as `mainClass` in build.gradle.kts,
so `./gradlew run` works.

## Layering rule
Game logic never lives in rendering classes; rendering classes never
contain gameplay logic. GameState/Enemy must not import java.awt or
javax.swing. GamePanel reads state and paints it — it never mutates
what it reads. Entities are composed (one Enemy class configured by an
EnemyType), not subclassed per monster.

## Input capture
Typing goes through TypingInputField's DocumentListener, never a raw
KeyListener — Khmer input-method composition delivers multi-codepoint
document edits, not clean keyTyped chars. KeyboardHandler covers only
non-text keys (pause, clear, quit) via key bindings.

## Shared timing constants
DEFEAT_ANIMATION_TICKS, HIT_FLASH_TICKS and TYPO_FLASH_TICKS live in
GameConfig and are referenced by both engine and renderer. Do not
redeclare them locally — if the cull timer and the fade timer disagree,
sprites pop out mid-fade or linger invisible.

## Play field layout
Grounded enemies march in from the LEFT and RIGHT screen edges along the
temple plaza toward TEMPLE_CENTER_X; reaching BREACH_RADIUS of it costs a
life. Enemies do not walk down the screen — the background art only has
walkable ground in its bottom band.

## Ground behaviour
EnemyType carries a GroundBehavior. GROUNDED (Beisach, Yeak, Pret, Naga,
Krong Reap) anchors the BOTTOM of the trimmed sprite to GROUND_LINE_Y and
never bobs — feet stay planted. FLOATING (Ahp, Stec Kantoab) anchors the
sprite CENTRE at GROUND_LINE_Y minus hoverHeight and bobs on a sine wave.
Never add bobbing to a grounded type; it looks like the ground is moving.

## Sprite sizing
SpriteCache trims each PNG to its opaque bounding box before use. The
delivered art has wildly uneven transparent padding (Yeak ~25% per side,
Krong Reap under 4%), so untrimmed scaling both mis-sizes monsters and
floats grounded ones above the plaza. EnemyType specifies targetHeight
only; width is derived from the trimmed aspect ratio.

## Missing assets are not errors
SpriteCache, WordBank and FontManager all fall back gracefully when a
resource is absent (placeholder shape, built-in word list, default font)
and log one line. Every roster entry is fully configured even without
art, so dropping a PNG into resources/images needs no code change.

## Build phases
Phases 1-4 and 6 are implemented (engine, prefix matching, input, waves,
HUD, save/autosave). Phase 5 (word-projectiles) is next — TargetResolver
already accepts a priority list, so it only needs filling. Phase 9
(Khmer) needs words_km.json and NotoSansKhmer-Regular.ttf added to
resources.