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
ApproachPath describes routes as a horizontal RUN plus a vertical RISE,
not as an angle. This is not incidental — walkers and flyers genuinely
cannot share one geometry:

- The walkable plaza starts at PLAZA_TOP_Y (540, MEASURED off the
  background art where the flagstones begin) and the ground line is 640,
  so there are only ~100px of usable depth. A true 45-degree walk needs
  equal run and rise, which over 100px means spawning basically on top of
  the breach point. Anything longer puts a GROUNDED enemy's feet in the
  sky above the temple. This was a real bug — Yeak was the most visible
  because he is the tallest.
- So GROUND_DIAGONAL is a SHALLOW drift (~9-10 degrees): long horizontal
  run, rise capped at GROUND_RISE_MAX. AIR_DIAGONAL keeps the true 45
  degrees, because flyers legitimately belong in the sky.

PLAZA_TOP_Y DOES NOT SURVIVE AN ART CHANGE. If the background is
replaced, re-measure where the paving starts and update it, or walkers
will float. It has already moved once (585 -> 540) when the background
was swapped.
- FLANK routes have zero rise and stay at full size the whole way; they
  enter on the near plane.

Use path.isFortyFiveDegrees() when you mean "the airborne descent", not
isDescending() — the ground drift descends too.

Depth scaling floor is PER ROUTE (ApproachPath.depthScaleMin), not
global: GROUND_DEPTH_SCALE_MIN 0.82 vs DEPTH_SCALE_MIN 0.55. A walker
that shrinks to 55% while descending only fifty pixels reads as
deflating, not as perspective. Full size is reached at
DEPTH_FULL_SIZE_AT, before the breach point — scaling to 1.0 only at the
temple centre would mean monsters got culled before ever being drawn at
100%.

Airborne spawn runs are capped by ApproachPath.maxRunFor(targetY,
headroom): a tall monster or high-hovering flyer would otherwise spawn
with its word plate behind the HUD bar, unreadable and untypeable. The
RUN is shortened rather than the position clamped, so 45 degrees
survives. HUD_SAFETY_MARGIN exists because without it the cap is exact —
the plate lands ON the bar and a pixel of rounding decides readability.
There are tests asserting every spawn clears HUD_BAR_HEIGHT and that no
grounded enemy ever leaves the plaza.

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

## Failure containment
Anything Swing calls repeatedly is wrapped in util/CrashGuard. This is
not defensive noise — it fixes a specific, very bad failure mode: an
exception escaping a Swing Timer tick or a paintComponent is logged and
then the callback FIRES AGAIN, so one bad frame becomes sixty stack
traces a second while the window sits there open and frozen, telling the
player nothing.

CrashGuard catches Throwable (not just Exception), logs the first 3 with
traces then goes quiet, and counts CONSECUTIVE failures so a transient
glitch is absorbed but a persistent one is reported as hopeless.

Guarded entry points: GameLoop.tick, GamePanel.paintComponent,
GamePanel.tick, TypingInputField.paintComponent, the buffer-changed
callback, and every KeyboardHandler action. GameLoop stops itself after
HOPELESS_AFTER_TICKS consecutive failures, fires onFatalError ONCE
(fatalReported guards re-entry), and Main saves progress then shows a
dialog. Do not "simplify" these away.

## Missing assets are not errors
SpriteCache, WordBank and FontManager all fall back gracefully when a
resource is absent (placeholder shape, built-in word list, default font)
and log one line. Every roster entry is fully configured even without
art, so dropping a PNG into resources/images needs no code change.

## Attack animation
Yeak is the only type that throws (throwIntervalTicks > 0; others are 0
and enable with a one-number change). The throw is a phase machine —
WINDUP / RELEASE / RECOVER in AttackPhase.

GamePanel.drawThrowingSprite builds the pose from ONE static image by
cutting the sprite at WAIST_RATIO and drawing the halves with different
transforms: legs planted with a slight brace, torso pivoting about the
waist. Each half is isolated by clipping AFTER its transform is applied,
so the clip travels with the pixels it selects; the torso clip runs
SEAM_OVERLAP past the cut so rotation cannot open a gap. A whole-body
rotation was tried first and reads as toppling, not winding up — that
version survives only as the placeholder fallback when there is no
sprite to cut. Enemies stop walking while an attack phase is active.

## Mini-boss word chains
Enemy carries a LIST of words, not one. Ordinary types have a list of
one; NAGA has 2-3 (randomised per spawn from getMaxChainLength). The
chain lives in Enemy rather than a Naga subclass so update, render and
matching all stay on one path.

Clearing a non-final word calls advanceChain(), which sets a stagger:
the enemy holds position so the player gets a beat to read the next
word. It scores but does NOT increment enemiesDefeated or
resolvedThisLevel, because it has not actually been resolved.

WaveManager dedupes against getAllWords(), not getWord() — otherwise a
Naga's unrevealed second word can collide with a live enemy's.

## One-shot flags
Projectile.hasJustLanded() and Enemy.isProjectileDue() are true for
exactly ONE tick and are cleared at the top of update(). They must stay
that way: a sticky landed flag charges the player a life on every tick
of the fade-out and drains a whole run from a single missed bolt.

## Controls
Type to attack. Tab arms restart and Enter within RESTART_ARMED_TICKS
confirms (so one stray Tab cannot wipe a run). Escape quits. Cmd+P on
macOS / Ctrl+P elsewhere pauses — pause cannot use a bare letter key,
since the typing field legitimately consumes every letter.
TypingInputField must keep setFocusTraversalKeysEnabled(false) or Tab
moves focus instead of reaching the key binding.

util/Platform holds the modifier detection. It is in util, not input,
because HUDRenderer also needs it to label the pause overlay — putting
it in input would make renderer depend on input while input already
depends on renderer for Palette, creating a package cycle.

## Pausing
Pause SKIPS the simulation (GameState.paused short-circuits update); it
does NOT stop the GameLoop. The loop must keep ticking so the renderer
still paints the overlay — stopping the timer freezes the last frame
with no explanation, which is indistinguishable from a hang. A finished
run refuses to pause, since the overlay would cover the restart prompt.

## Khmer fonts
FontManager walks a chain: Suwannaphum (primary) then Kantumruy Pro
(backup) then any system Khmer face then sans-serif. Font files are NOT
committed — see resources/fonts/README.md. Swing does not substitute
fonts for missing glyphs the way a browser does, so the face must be
loaded with Font.createFont and registerFont or Khmer draws as tofu.
Several alternative filenames are accepted so nobody has to rename a
download.

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

## Front end
Two screens share one window via CardLayout in Main: MenuPanel and the
game. Only one animates at a time — MenuPanel has its own Timer, the
game has GameLoop, and switching stops the other.

MenuState is pure logic (no Swing) so the whole flow is unit-testable.
It returns an Outcome the caller acts on rather than calling back into
the UI itself.

KeyboardHandler bindings are WHEN_IN_FOCUSED_WINDOW, which fire even
when their component is not showing. keys.setActiveWhen(gameRoot::
isShowing) is what stops Escape quitting the game from inside the menu.
Do not remove that gate.

MenuPanel uses its own focusable KeyListener rather than window
bindings, for the same reason in reverse.

Locked entries (Options, Bestiary, and every difficulty except Easy)
stay REACHABLE by the highlight instead of being skipped. A cursor that
jumps past items the player can see reads as broken; landing on one and
being told it is not ready does not.

## Difficulty tiers
MEDIUM is the REFERENCE TUNING. Every number in DifficultyCurve is
written at Medium's values, and all of Medium's scales are 1.0 — so the
single-argument curve methods describe Medium exactly, and the
Difficulty overloads apply a tier's deviation on top. There are tests
asserting the two agree for Medium. Never retune a curve without
checking what it does to the other tiers.

EASY and MEDIUM are playable. HARD and ENDLESS are listed but
implemented=false; MenuState refuses START_RUN for them. Their
multipliers are already recorded so the intended balance is not lost.

Easy is not merely slower — it also shortens words
(getWordMinShift/getWordMaxShift), because a beginner's problem is
finding the letters rather than the clock. Per-type speed multipliers are
deliberately tier-INDEPENDENT: the tier scales the base speed, so the
relationship between light and heavy types survives at every tier.

Each tier names its own finale (finalBossType / finalBossLevel /
finalBossChainLength). Easy ends with a 3-word Naga at level 10; Medium
with Krong Reap at 15. getBossWordLengthBonus stacks on top of the tier
shift, so Easy's boss still demands the hardest typing in an Easy run.
LevelPreview is tier-aware for the same reason — announcing the wrong
finale is worse than announcing nothing.

Difficulty is settable on GameState/WaveManager but ONLY between runs, via
GameState.restartWith(). The tier decides speeds, word lengths and which
monster ends the game, so changing it mid-level would finish a level
under different rules than it started.

## Opening sequence
IntroSequence gates the start of play: a loading bar, then 3-2-1, then a
DEFEND flash — about four seconds total. GameState.update() returns early
while it is active, so nothing walks and elapsedTicks stays at zero (the
countdown must not count against the player's WPM). The loop keeps
ticking so the renderer can draw it, exactly as pause works. restart()
replays it, so a player is never dropped back into a wave already in
motion. Tests that exercise the simulation call skipIntro() first.

## Menu press delay
MenuState.activate() does NOT act. It starts a short depress and returns
PENDING; the caller watches pollReady() for the real outcome, which
arrives once the button has finished sinking. Screen changes are applied
in pollReady, not activate, so the button the player pressed is still the
one on screen while it depresses. A second press while one is running is
dropped, and reset() discards a press in flight.

## Shared ornament
renderer/Ornament holds the lotus-bud prang path used BOTH as HUD life
pips and as the menu's title divider. It was duplicated once; keep it in
one place or the two silhouettes drift apart.

## Build phases
Phases 1-6 are implemented (engine, prefix matching, input, waves, HUD,
projectiles, save/autosave, player, game over). Phase 9 (Khmer) needs
words_km.json and NotoSansKhmer-Regular.ttf added to resources.