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
Enemies materialise in a smoke puff and converge on TEMPLE_CENTER_X /
GROUND_LINE_Y. Reaching BREACH_RADIUS of that point costs a life. Spawns
are deliberately ON-SCREEN — the poof is what makes that read as
materialising rather than popping in. Preah Ream stands at
TEMPLE_CENTER_X in the foreground, back to the viewer.

## Approach routes
ApproachPath gives each enemy one of two shapes, picked from the family
matching its GroundBehavior:
- FLANK: purely horizontal, spawns level with its target, always drawn
  at full size (it enters on the near plane).
- DIAGONAL: exact 45 degrees, spawns back up the causeway, scales up
  with depth.
Ground types get GROUND_FLANK / GROUND_DIAGONAL, flyers get the AIR_
variants at hover altitude. Never give a flyer a ground route or the
hover height is ignored.

Depth scaling runs from DEPTH_SCALE_MIN to full size at
DEPTH_FULL_SIZE_AT. Full size is reached BEFORE the breach point on
purpose — scaling to 1.0 only at the temple centre would mean monsters
got culled before ever being drawn at 100%.

Diagonal spawn runs are capped per-type by
ApproachPath.maxRunFor(targetY, headroom): a tall monster or a
high-hovering flyer on a long run would otherwise spawn with its word
plate behind the HUD bar, making it unreadable and untypeable. The run is
shortened rather than the position clamped, so the 45 degrees survives.
There is a test asserting every spawn clears HUD_BAR_HEIGHT.

## Difficulty
All level scaling lives in engine/DifficultyCurve. Every curve is
monotonic AND capped — an uncapped curve eventually produces an
unplayable level. Light types (Ahp, Beisach) carry a large
levelSpeedGain so they get frantic; heavies (Pret, bosses) barely
accelerate, because their threat is word length, not pace. There is a
test asserting a Pret never outruns an Ahp at any level.

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

## Attack animation
Yeak is the only type that throws (throwIntervalTicks > 0; others are 0
and enable with a one-number change). The throw is a phase machine —
WINDUP / RELEASE / RECOVER in AttackPhase — and the renderer maps each
phase to a lean angle and lunge offset applied around the FEET. This
builds a convincing throw from a single static image via anticipation
and follow-through. When real pose art arrives, the sprite swap plugs
into the same phases and the timing does not change. Enemies stop
walking while an attack phase is active, or the throw reads as a stumble.

## One-shot flags
Projectile.hasJustLanded() and Enemy.isProjectileDue() are true for
exactly ONE tick and are cleared at the top of update(). They must stay
that way: a sticky landed flag charges the player a life on every tick
of the fade-out and drains a whole run from a single missed bolt.

## Controls
Type to attack. Tab arms restart and Enter within RESTART_ARMED_TICKS
confirms (so one stray Tab cannot wipe a run). Escape quits. Ctrl+P
pauses — pause cannot use a bare letter key, since the typing field
consumes those. TypingInputField must keep
setFocusTraversalKeysEnabled(false) or Tab moves focus instead of
reaching the key binding.

## Input field
TypingInputField is custom-painted (setOpaque(false), paintComponent
draws the stone frame and glass plate, then calls super for the text). It
is not a plain JTextField — do not set a background colour on it, tint
the glass gradient instead. Its tick() drives both the typo flash decay
and the idle glow pulse, so the loop must keep calling it.

## UI colour
All chrome colour lives in renderer/Palette — stone-dark (#1E1914) and
temple gold (#D4AF37 / #F7D16E). The top HUD bar and the bottom typing
bar both source from it deliberately: they are one frame around the play
area, and if their colours drift the screen stops reading as a single
interface. Do not hardcode chrome colours in HUDRenderer, GamePanel or
TypingInputField.

Stat hierarchy on the HUD bar is intentional, not decorative. LEVEL and
SCORE share the display font in gold; WPM/ACCURACY/SLAIN/BEST drop a
full size tier to off-white with labels at 70% alpha. They are not five
equal peers — only two things on that bar are meant to be read rather
than glanced at.

## Level progress bar
GameState.getLevelProgress() counts enemies RESOLVED this level —
defeated OR leaked — over DifficultyCurve.enemyCount(level). Counting
only kills would leave the bar permanently short of full after any
breach, which reads as a bug rather than as feedback.

## Target lock chip
GamePanel draws a chip above the typing bar showing the locked enemy and
its remaining letters. This exists because prefix matching is ambiguous
by design: several enemies light up at once and the moment the target
narrows to one is otherwise invisible. GamePanel.tick() eases its fade
and must be called from the game loop, not from paintComponent.

## Build phases
Phases 1-6 are implemented (engine, prefix matching, input, waves, HUD,
projectiles, save/autosave, player, game over). Phase 9 (Khmer) needs
words_km.json and NotoSansKhmer-Regular.ttf added to resources.