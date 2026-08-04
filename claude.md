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

FILENAMES MUST MATCH `Language.getWordListPath()`. The file was once
named wordBankEng.json while Language asked for words_en.json, so every
run silently used the ~80-word built-in fallback and nobody noticed for
weeks. WordBankTest now asserts `!isUsingFallback()` to catch a repeat.

## Word bank shape
words_en.json holds vocabulary ONCE, in `pools` (tiny 2-3, short 4-5,
medium 6-7, long 8-10, epic 11+) and `bossPools` (novice 5-7, adept
8-10, master 11-13, legend 14+), plus a curated `projectile` pool of
2-3 letter words shared with power-up pickups.

`tricky` and `lore` are the two pools NOT graded by length.

`tricky` holds spellings awkward to type at any size — y-as-vowel
(glyph, lymph, sylph, syzygy), silent letters (psalm, qualm), rare
digraphs and q/z clusters (sphinx, zephyr, quartz, oblique). `lore`
holds the game's Khmer and mythological vocabulary (naga, apsara,
garuda, prasat, baray, reamker). Both are opted into by LATER bands in
`difficulties`, which is what stages the learning curve. Do not "fix"
either to fit a length band; length is not what they are for.

A WORD LIVES IN EXACTLY ONE POOL. This is load-bearing and there is a
test (`poolsDoNotOverlap`) enforcing it. `tricky` used to overlap the
length pools deliberately, and the result was that it gated nothing:
every one of its 51 words was also in `short` or `medium`, so glyph,
myrrh, psalm, lymph, nymph, qualm, sylph and wyrm were all served at
Easy LEVEL ONE while the pool that was supposed to hold them back sat
there doing nothing. An overlap silently defeats the gating, because
the word stays reachable through whichever length pool also has it.

Short is not the same thing as easy. A four-letter word can be the
hardest thing on screen for a beginner, which is the distinction these
two pools exist to express.

VOCABULARY IS ANGKOR-FLAVOURED ON PURPOSE and there are tests asserting
it. Temple and Khmer terms (apsara, prasat, devata, garuda, laterite,
bayon, gopura, bodhisattva), jungle and ruin words, and a seam of rare
evocative ones (petrichor, cenotaph, oubliette, palanquin). Clinical
modern vocabulary — examination, observation, translation — was removed
and is asserted absent. Singular/plural pairs are deduped: keep one.

`bossParagraphs` holds the finale, per tier, as several three-to-five
sentence paragraphs. One is drawn per run so a rerun is not the same
fight. Lower case, letters and spaces only — the finale must not be the
one place in the game that needs a shift key or a comma.

`difficulties` is the tuning table, NOT more word lists: per tier, a
list of level bands saying which pools that stretch of the run may draw
from and which boss rank its bosses use. Bands match by `throughLevel`
in order; the last band also covers everything past it, which is what
keeps Endless from running off the end of its own table. Making an
early level gentler is an edit here, never in Java.

Boss words are RESERVED — never in the regular pools — so a climactic
word can't already have appeared on a Beisach in level two. The rank
climbs with both the tier and the level band, which is what makes an
Easy Naga and a Hard Naga different fights rather than the same fight
with two more letters.

WordPolicy is the resolved answer to "tier X, level Y: what may
appear?". EnemyType still asks for a length window, but the policy is
the ceiling over it: a Pret wants 8-12 letters and Easy's opening band
tops out at 5, so it takes the LONGEST word the band has rather than
reaching outside it. That clamp is the whole fix for "Easy is too hard"
— without it, level one served eight-letter words to beginners.

WordBank talks to the engine through a lower-case String key
(`Difficulty.getWordBankKey()`), not the enum. `engine` already imports
`i18n`; making that mutual to pass one identifier would be a package
cycle.

util/Json is a small hand-rolled parser, added because the old loader
scanned for `"key"` and grabbed the bracketed run after it. That worked
only while every key in the file was unique, which stopped being true
the moment both a pool list and each level band had a `pools` key.

## Power-ups
Collected by TYPING, like everything else: a defeated enemy may drop a
PowerUp carrying a short word, and typing it claims the boon. Not an
inventory with a hotkey — the typing field legitimately consumes every
letter, so any activation key would have to be a modifier chord, which
is a second control scheme bolted onto a game whose whole proposition
is that you only ever type.

PowerUp implements WordTarget and is fed to TargetResolver BETWEEN
projectiles and enemies. Tiers are ordered by time budget, shortest
first: a bolt lands in a second, a drop fades in seven, an enemy takes
as long as it walks. That ordering is also what makes grabbing one a
real decision — it breaks off the word you were part-way through.

Five boons, all with placeholder glyphs until art arrives (SpriteCache
falls back exactly as it does for the unfinished enemy roster):
Time Freeze, Slow Tide, Purge, Mend, Naga Shield.

PowerUpState owns only the DURABLE half — timed effects and banked
shield charges. Purge and Mend act on the field and the life count,
which are GameState's, and putting them in the state holder too would
leave two objects able to decide what a Purge does.

Freeze and Slow work by ONE timeScale read once per tick and passed to
Enemy.update(scale) / Projectile.update(scale). Reading it per entity
would let a boon expire mid-frame and advance the back half of the
field further than the front. Only the threatening half of a tick
scales — hit flashes, stagger and death fades stay on real time, or a
Time Freeze looks like a hang rather than like enemies stopping.
Enemy's attack timers are DOUBLE, not int, or a 0.45 scale rounds to
0 or 1 and a slowed Yeak throws at full speed.

Drop rate is LOW (12-30% per ELIGIBLE kill, ceiling 30%). The first pass
dropped a boon from a third of Easy's kills, which made them ordinary —
a reward the player stops noticing has stopped being one. It rises as
lives run out (PowerUpDrops), capped. That mercy
curve is the difficulty valve that needs no curve retuning: a run going
badly quietly gets more to reach for, a run going well never notices.
Mend is withheld at full health and the ward at full charges rather
than rolled and wasted — a drop that does nothing teaches the player to
ignore drops.

Only Yeak, Pret and Naga drop (EnemyType.dropsBoons). Flyers and the
common Beisach never do, so a boon is payment for a slow, long-word kill
rather than loot from the trash mob. Rates are per ELIGIBLE kill and
about a third of spawns qualify — Easy's 0.30 is nearer one boon in ten
kills overall.

Breaches and landed bolts both route through
GameState.absorbOrLoseLife, so a shield can never be honoured for one
and forgotten for the other.

## Damage
Lives are counted in HALVES (GameState.halfLives), not as a fraction —
an integer count means a flyer's half-hit and a walker's full hit can
never disagree by a rounding error about whether the run is over.
Grounded breach costs 2, flying breach 1, any landed projectile 1. The
HUD keeps three lotus buds and clips the gold to the left half of one
to show a half; six small pips would be exact but would need counting.

## Enemy roster (see bestiary for word-length tiers)
Beisach, Yeak, Ahp, Pret, Kmaoch, Naga, Krong Reap

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

## Tick rate is a promise, not a hope
Every speed, duration and cooldown in the game is expressed in TICKS and
assumes exactly TARGET_FPS of them per second. GameLoop is what makes
that true: it accumulates REAL nanoseconds and runs however many whole
ticks have come due — none on a fast machine, several on a slow one.

This is not premature engineering. The loop used to run exactly one tick
per Swing timer callback, and the result was that the entire game played
in smooth uniform SLOW MOTION on Windows while being correct on macOS.
Windows rounds Swing timer requests to its ~15.6ms scheduler
granularity, so a 16ms request can land on 31ms — half rate, half speed.
A slow machine must lose FRAMES, never simulation speed.

Catch-up is capped (MAX_CATCHUP_TICKS 5). Uncapped it is the spiral of
death — an overrunning frame asks for more ticks, which makes the next
frame overrun further. The cap doubles as the laptop-lid guard: after a
sleep or a breakpoint the clock has jumped by seconds and the game must
not fast-forward through them.

Never reintroduce per-callback stepping, and do not add
`System.nanoTime()` reads inside entity update methods either — the
fixed timestep is what keeps the simulation deterministic and the tests
meaningful.

## Play field layout
Enemies materialise in a smoke puff and converge on TEMPLE_CENTER_X /
GROUND_LINE_Y. Reaching BREACH_RADIUS of that point costs a life.
BREACH_RADIUS is 58 and was 105 — at 105 the box was wider than Preah
Ream is drawn, so lives were lost while the monster was visibly still a
stride away and it felt stolen. It can be lowered freely but NOT raised
without re-checking DEPTH_FULL_SIZE_AT: the breach has to happen after
enemies reach full size or they are culled mid-growth.

The boss does NOT stand on GROUND_LINE_Y. BOSS_BASE_Y is 110px above it
so the serpent rears up behind the temple rather than sharing the plaza
with the hero — at ground level Preah Ream stood squarely in front of
it. VERSE_PANEL_BOTTOM_Y is derived from the hero's height for the same
reason: he is drawn in the foreground, and a panel reaching any lower
put him in the middle of the sentence. Spawns
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

## Concurrent enemy cap
Difficulty.getMaxConcurrentEnemies (Easy 4, Medium 6, Hard 8) limits how
many enemies are ALIVE at once. WaveManager holds the queue when the
plaza is full — the spawn cooldown does not tick, so clearing one enemy
never owes the player a backlog of everything that would have spawned
while they were busy.

This is a READING limit, not a screen-space one. Every live enemy is a
word to scan and choose between, and past about six the player stops
reading and starts guessing — prefix matching turns ambiguous at the
same time, so the target they hit stops being the target they meant.

It fixes a real gap. enemyCount plateaus but spawnIntervalTicks keeps
shrinking to its floor, so every level past the plateau turned "more
enemies" into "all the enemies at once": Medium's last wave put sixteen
monsters up in nine seconds, and Hard's final six levels were identical
to each other. The cap converts that into a queue that refills as fast
as the player clears it. Count ACTIVE enemies only — a defeated one
lingers through its death fade and would block a slot for a second.

DO NOT conflate the two ceilings. enemyCount's cap (40) is a runaway
guard for Endless and sets a level's LENGTH; the concurrent cap sets its
DENSITY. They were the same number (20) once and that is exactly what
made the late game flat.

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
never bobs — feet stay planted. FLOATING (Ahp, Kmaoch) anchors the
sprite CENTRE at GROUND_LINE_Y minus hoverHeight and bobs on a sine wave.
Never add bobbing to a grounded type; it looks like the ground is moving.

## Sprite sizing
SpriteCache trims each PNG to its opaque bounding box before use. The
delivered art has wildly uneven transparent padding (Yeak ~25% per side,
Krong Reap under 4%), so untrimmed scaling both mis-sizes monsters and
floats grounded ones above the plaza. EnemyType specifies targetHeight
only; width is derived from the trimmed aspect ratio.

Every loaded image is then converted ONCE to a display-compatible copy
at working size (toWorkingCopy / toBackdrop). Both halves matter and
both cost frame time on Windows but not macOS:

- trim() ends in getSubimage, which returns a VIEW onto the parent
  raster. Java2D refuses to treat a view as a managed image, so it can
  never live in video memory and every blit of it is a software loop.
  The copy is what makes it cacheable. Do not hand a raw getSubimage
  result to the renderer.
- ImageIO decodes to whatever the PNG declares, usually TYPE_4BYTE_ABGR
  or TYPE_CUSTOM. Neither matches the screen, so each draw pays a
  per-pixel conversion. TYPE_INT_ARGB_PRE is what the pipeline wants.

Sources are up to 1216x1200 and monsters are drawn around 200px, so they
are also downscaled on load to the tallest size they are ever drawn at
(never upscaled). The 1672x941 backdrops are pre-scaled to the exact
1280x720 window and kept OPAQUE — nothing sits behind them, so the alpha
blend is pure waste. Backdrops then blit 1:1, which is the fast path.

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

## Interface font chains
Three chains beyond Khmer, all in FontManager, all optional and all
degrading to platform serifs (resources/fonts/README.md):

- displayFont — Cinzel Decorative. The ANGKOR wordmark, the modal
  headline, the stat numbers.
- uiSerifFont — plain Cinzel. Button labels, stat labels, tracked caps.
- bodyFont — EB Garamond italic. Subtitles, captions, footnotes.

Cinzel and Cinzel Decorative are SEPARATE chains on purpose. Decorative's
swash capitals carry a 49px wordmark and turn a 14px tracked button label
into a smear, so anything small prefers the plain cut. Do not collapse
them. Unlike Khmer these are styling choices rather than glyph coverage,
so the uncovered fallback is not tofu, just plainer.

## Front-end visual design
MenuRenderer and HUDRenderer.drawGameOver are ports of the Figma design
(Main Menu Screen Illustration). Its responsive clamps are RESOLVED at
the fixed 1280x720 rather than reimplemented — clamp(28px,3.5vw,52px)
is written as 45, because a clamp that can only produce one value is
that value with arithmetic in the way. If the window ever becomes
resizable these become functions again.

The design's SVG components are ported to Java2D in renderer/Ornament
(drawNagaDivider, drawLotusFlame, drawStoneTexture, drawGoldRule,
drawGoldSeam, drawCornerBracket, drawDotRow) rather than shipped as
images. They are small flat vector marks, so a Path2D is sharper and
cheaper at the sizes used — and it recolours with the palette, which is
what lets the end-of-run card draw itself in gold or in danger red from
one code path.

Palette carries the design's five golds and three stones as named
tokens. Everything else is expressed in terms of those; do not
reintroduce hex literals in renderers.

Two deliberate departures from the design, both because the design is a
mouse-driven web mock and this is a keyboard-driven Swing game:

- The design marks NEW GAME as a permanent primary (gold pill) and
  animates hover. There is no cursor here, so the pill follows the
  SELECTED entry instead. Showing what the arrow keys are on matters
  more than marking one entry special — without it the menu cannot be
  navigated. A selected but LOCKED entry deliberately does not get the
  pill: the pill means "press this", and offering it for something that
  will refuse is a lie the player only discovers by pressing.
- The modal's three mouse buttons become two key-hinted ones (TAB then
  ENTER, ESC), matching the controls that actually exist. Drawing a
  mouse affordance for something the mouse cannot do is worse than
  drawing no button.

The modal's backdrop blur is dropped for a darker scrim. A full-screen
convolve every frame is exactly the per-frame cost the render pass was
rebuilt to avoid, and the blur's job — pushing the play field back — is
one a scrim does for free.

The card is 620x552 at y=66 and its height is still a CONSTANT, not
measured. There is a layout arithmetic check in the header comment path:
content runs 94 to ~574 against a card bottom of 618. Adding a stat row
or a third line of prose overruns it silently, so re-check the column if
you change the content.

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

Easy is not merely slower — the word bank's Easy bands hold back the
long vocabulary, and WaveWeights delays the heavier types by two
levels, because a beginner's problem is finding the letters rather than
the clock and meeting five monsters in six levels is a lot to learn at
once.

Easy has FOUR bands where the other tiers have three, and the extra one
is the point: each step adds exactly one new kind of difficulty rather
than several at once. L1-3 common short English, L4-7 common longer
English, L8-11 the Khmer vocabulary arrives, L12-15 the awkward
spellings arrive. A player should be able to name what got harder. Per-type speed multipliers are deliberately
tier-INDEPENDENT: the tier scales the base speed, so the relationship
between light and heavy types survives at every tier.

EVERY BUILT TIER IS OPEN FROM THE FIRST LAUNCH. There is no unlock
ladder — MenuState.isEnabled(Difficulty) checks isImplemented() and
nothing else. DifficultyProgress still tracks what has been cleared and
still has isUnlocked(), but it GATES NOTHING; it is a record for the
save file and the end card. Do not reintroduce it as a gate without
deciding that deliberately.

EASY, MEDIUM and HARD ALL END ON LEVEL 15. A tier changes how hard the
same run is, not how long it is, and a player moving up from Easy
should recognise the shape of what they are attempting. Endless is the
exception by definition. Each tier still names its own finale
(finalBossType / finalBossChainLength): Easy a 3-word Naga, Medium and
Hard Krong Reap. Boss VOCABULARY is not set here — it comes from the
ranked boss pools the tier's band points at.

getFinalLevel() and getFinalBossLevel() are the same number today and
are separate methods anyway, because they answer different questions:
when the boss arrives, and when the game stops.

## The finale
Clearing level 15's wave does NOT win the run — it summons the boss.
BossFight is a phase, not an Enemy: it stands at TEMPLE_CENTER_X, never
moves, cannot be reached, and is beaten by typing a paragraph. Forcing
that into Enemy would mean an enemy ignoring its own movement, hitbox
and word, which is not an enemy any more.

The paragraph arrives ONE SENTENCE AT A TIME. Thirty words at once
reads as a punishment; a verse at a time has a rhythm and each cleared
one is a visible beat. A mistype RESETS THE CURRENT VERSE only —
harsher than the rest of the game on purpose, since the finale is where
accuracy is meant to matter, but never touching a verse already
cleared.

A verse is typed ONE WORD AT A TIME, buffer clearing between words like
it does after a kill. That is load-bearing, not cosmetic: venom bolts
carry words too, and a verse typed as one continuous string leaves no
moment mid-verse at which a bolt's word could be started. Word-at-a-time
keeps the buffer a partial word always, so both can be weighed against
the same keystrokes.

A word only advances on an explicit SPACE, not the instant its last
letter lands (BossFight.submit checks for `word + " "` before it checks
completion). Auto-advancing on the last letter was tried first and it
broke the player's own typing rhythm: prose is typed word-then-space, and
a field that clears itself out from under the space the player was
already about to type leaves that keystroke landing on an empty buffer
instead, reading as a stray first letter rather than a confirmation.
GameState.handleBossInput mirrors this: the verse only counts as
COMPLETED on `currentWord() + " "`, never on the bare word, so a fully
typed but unconfirmed word still shows as PROGRESS (want.startsWith(want)
is trivially true) rather than jumping ahead on its own.

The boss also HARASSES during the paragraph — roughly one bolt per
paragraph, 9-16s apart (BossFight.updateHarassment). The fight used to
strictly alternate, which made the verse a safe window, and a safe
window is a window with no decisions in it.

Harassment is a BUDGET FOR THE FIGHT, not a rate. At a fixed rate a
slow reader would earn MORE interruptions — punished twice for the same
slowness. Taking longer spaces them out instead.

SLOWNESS IS DELIBERATELY NOT PUNISHED FURTHER, in either half of the
fight. A phase already charges for it: unanswered summons breach and
unanswered bolts land, so a slow player is losing lives the whole time.
Adding escalation on top would charge twice for one mistake, and the
player it hurts most is the one already losing. Do not add a stall
timer, a quota escalation or verse regression without deciding that
deliberately.

Boss phases are OBJECTIVE-BASED, not timed. A phase sends a quota of
attacks (BossFight.attacksFor: kind × tier × phases elapsed) and ends
only when the quota is spent AND the field it filled is clear. Its
length therefore belongs to the player, not to a countdown.

This cannot softlock, and the reason matters: both attack kinds
SELF-RESOLVE. An untyped summon walks in and breaches; an unanswered
bolt lands. Either way it leaves the field and costs a life, so a
player who stops typing loses the run rather than hanging the phase.
Never add a boss attack that can sit on the field indefinitely without
also giving it a resolution path.

BossFight knows what it scheduled; only GameState owns the entity
lists. So GameState.updateBoss calls boss.reportField(census) and THEN
boss.update() — both in that method, in that order, so the dependency
is visible. The census counts ACTIVE entities only: a defeated summon
lingers through its death fade, and counting it would hold every phase
open an extra second per kill.

PHASE_STUCK_TICKS (60s) is a BUG GUARD, not pacing. Phases measure
6-20s; if it ever fires, something is stuck and it logs loudly.

venomIntervalTicks is SPAWN SPACING (2-3s), not a phase clock. It was
5-10s when it decided how often a bolt appeared across a seven-second
phase. With a quota of 3-8 that would be half a minute of standing
about between bolts.

Venom (Projectile.Kind.VENOM, purple) is deflected by typing its word.
It flies SLOWLY (5.5s, scaled by the tier) and comes every 5-10 seconds
at random — a fixed cadence becomes a rhythm players stop reacting to.
Its words come from the `action` pool — IMPERATIVES, not nouns
(repel, shield, sever). A bolt gives about five seconds mid-fight, so
the word must read as an order at a glance; an unfamiliar noun is a
fine enemy word and a bad instruction. That pool is disjoint from every
other list in the file: no enemy pool, no thrown-bolt word, no boss
word, and nothing appearing in any boss paragraph. Asserted, not
maintained by eye — the paragraph rule alone rejects ward, stop, hold
and wall, all of which look like obvious action words until you notice
the finale already says them. Words are also excluded against
BossFight.remainingWords() as defence in depth. Finishing a verse still fires a counter-volley clearing the
sky, which is what makes the paragraph a defence and not just a score.
A venom word never contains a space, so the confirm-with-space check
above can never collide with a bolt.

BRIEFING is a PHASE, not a banner over a live fight. Between ARRIVING
and FIGHTING the boss stands risen and the game is held for
BRIEFING_TICKS (5s) while BossRenderer.drawBriefing puts the rules on
screen. It has to be a phase because the overlay covers the verse
panel: leaving the fight running underneath would ask the player to
type a sentence they cannot see and spit at them for the privilege.
Nothing is typeable (isActive() and isFighting() are both false, so the
existing "boss is not fighting" gate in handleInput covers it) and no
venom flies. Its phaseTicks are NOT scaled by timeScale — a Time Freeze
carried through the boss door must not stretch it, and freezing an
already-held screen reads as a hang.

GameState does not advance elapsedTicks while isBriefing(), for the
same reason IntroSequence does not: five seconds of a screen that
forbids typing must not be charged against the player's WPM.

It exists because the finale changes three rules at once — words
confirm on space, orbs are answered by typing them, a slip costs the
verse — and none are guessable. The arrival name card announces the
boss's name, which is the one thing already visible. Without this the
first mistake is the tutorial.

The overlay text says the verse resets, NOT the paragraph, because that
is what resetVerse() does and the banner has to be true.

GameState.handleBossInput does NOT use TargetResolver — it checks exact
matches first (bolts, then the verse word followed by a space), then
prefixes. Checking completions before prefixes is what stops a verse
word that is a strict prefix of a live bolt's word ("the" while "temple"
is in the air) from being impossible to finish. Same rule the matcher
already applies within a tier; it just has to be applied across two
lists here.

Because the resolver is bypassed, its buffer goes stale for the fight —
GameState.getTypedBuffer() is what the renderer must read, not
resolver.getValidBuffer().

For the same reason the resolver's HIGHLIGHT list goes stale, and
handleBossInput must publish its candidates back through
TargetResolver.noteExternalCandidates(). Without it getHighlighted() is
empty for the whole fight and every renderer asking "is this target
lit?" is told no — summoned monsters never turned gold as they were
typed. That was open-coded once in the projectile path before being
fixed here; do not reintroduce a per-entity-type workaround.

Venom words are excluded against the verse by PREFIX, not just exact
match, now that a bolt can arrive mid-verse. `sea` and `seal` cannot
both be finished and the player cannot tell which their keystrokes are
going to — that is undecidable rather than merely ambiguous, so the
pair is excluded at spawn instead. Worst case across the vocabulary
bans one word.

Power-ups: arriving CLEARS every drop on the ground and suppresses new
ones for the fight. Boons already running are left alone — those were
earned, and cancelling them at the door would feel like a cheat.

GameState.victory is tracked separately from gameOver rather than
inferred from "finished with lives left" — different events, different
screens, and the inference breaks the moment anything else can end a
run. HUDRenderer checks isVictory() FIRST. Victory waits for the death
animation; cutting it off to show a scoreboard throws away the moment
the whole run was for.

## Level hints
LevelPreview derives everything — the finale from Difficulty, arrivals
from WaveWeights.newlyUnlockedAt. A hardcoded "level 3 is Yeak" was
correct at Medium and a lie on every other tier, because the tier
shifts the unlocks. When a mini-boss level and an arrival collide the
banner says BOTH rather than silently dropping one.

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
projectiles, save/autosave, player, game over), plus power-ups, the
banded word bank and the level-15 finale. Phase 9 (Khmer) needs
words_km.json and NotoSansKhmer-Regular.ttf added to resources —
words_km.json must use the same pools / bossPools / difficulties shape
as words_en.json.

## Art still outstanding
Everything below draws a placeholder and needs no code change when the
PNG lands in src/main/resources/images:
pret_transparent.png, and the five power-up icons
(powerup_time_freeze.png, powerup_slow_tide.png, powerup_purge.png,
powerup_mend.png, powerup_naga_shield.png).

Six of the seven monsters now have real art. EnemyType names the file
directly (Beisach.png, Kmaoch.png, Naga.png alongside the older
*_transparent.png ones), so the filename convention is not enforced —
only that EnemyType and the file agree.

SPRITES MUST BE RGBA WITH A TRANSPARENT BACKGROUND, not a cut-out saved
on white. Beisach.png and Kmaoch.png arrived as RGB with no alpha, and
the failure is quiet and confusing: SpriteCache.trim finds no
transparent margin, keeps the whole square canvas, and the aspect ratio
it derives is then 1:1 — so the monster draws squashed, inside an opaque
white box, floating above the plaza because the canvas bottom rather
than its feet is anchored to the ground line. Nothing throws. If a new
sprite looks like that, check the alpha channel before reading any
code.