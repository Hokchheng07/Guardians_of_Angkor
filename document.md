# Guardians of Angkor — how it got built

A Java Swing typing-defence game. You defend an Angkorian temple by typing the
words above approaching spirits before they reach it.

This document is the **journey**: what we built, what broke, and why the code
looks the way it does. It is written for people.

For the rules the code must obey, see [`context.md`](context.md). That file is
also what Claude Code loads automatically, via a one-line `CLAUDE.md` import.

---

## The shape of the thing

Seven monsters drawn from Khmer mythology walk in across the temple plaza. Each
carries a word. Type it and an arrow answers it. Let it reach the temple and it
costs a life. Clear fifteen levels — ten on Easy, twenty on Hard — and the
tier's final boss rises: a Naga on Easy, Krong Reap above it.

Three things separate it from a generic typing game, and most of the
interesting engineering follows from them.

**Targeting is prefix-based, not pre-locked.** After each keystroke, every enemy
whose word starts with what you have typed lights up. Typing `ca` highlights
both `can` and `cat`; the target commits only when the prefix is unique. That
one decision drives the input design, the word bank, and a surprising amount of
the difficulty tuning.

**The vocabulary is the game's identity.** The words are Angkorian on purpose —
`apsara`, `prasat`, `garuda`, `laterite` — because they are what the player
actually spends an hour looking at.

**It is bilingual by design.** English ships; Khmer is structured for but not
yet populated. Khmer support is why input goes through a document listener
rather than key events, and why difficulty tiers count grapheme clusters rather
than Java `char`s.

---

## Six problems worth reading about

### 1. The game ran in slow motion on Windows

The most instructive bug in the project. On macOS it played correctly; on
Windows it played *smoothly, uniformly slower* — not stuttering, just wrong.

The loop ran exactly one simulation tick per Swing timer callback and assumed
sixty of those happened per second. Every speed and cooldown in the game is
written in ticks, so on any machine that could not deliver sixty callbacks a
second, the whole game slowed proportionally. Windows rounds timer requests to
its ~15.6 ms scheduler granularity, so a 16 ms request often landed on 31 ms —
half rate, half speed.

The fix was to stop counting callbacks and start reading a clock. The loop now
accumulates real elapsed nanoseconds and runs however many whole ticks have come
due: none on a fast machine, several on a slow one. **A slow machine must lose
frames, never simulation speed.**

Catch-up is capped at five ticks. Uncapped, it is the classic spiral of death —
an overrunning frame asks for more ticks, which makes the next frame overrun
further. The cap doubles as the laptop-lid guard: after a sleep the clock has
jumped by seconds, and the game must not fast-forward through them.

Alongside it we cut the per-frame render cost that pushed Windows over budget in
the first place. Two causes, both invisible on macOS: sprites were being kept as
`getSubimage` views, which Java2D refuses to cache in video memory, so every
blit fell back to a software loop; and PNGs decoded to a pixel format that
didn't match the screen, so every draw paid a conversion. Both are now handled
once at load.

### 2. The pool that gated nothing

Words are held in pools graded by length — `tiny`, `short`, `medium`, `long`,
`epic` — plus a `tricky` pool for spellings that are awkward at any size:
`glyph`, `myrrh`, `psalm`, `sylph`.

Easy was reported as not actually easy, and the reason turned out to be
structural rather than a matter of taste. **Every one of `tricky`'s 51 words
also lived in a length pool.** It had no unique members at all. So including it
in a difficulty band added nothing, and every word it was meant to hold back
stayed reachable through `short` — served at Easy level one, by the very pool
built to prevent that.

Fixing it meant giving `tricky` exclusive ownership of the genuinely awkward
words, and adding a `lore` pool for the Khmer terms, which are unfamiliar but
not hard to type and so needed delaying for a different reason. Neither is
graded by length; both are opted into by later bands.

Easy now gains one new *kind* of difficulty per band: common short English,
then longer common English, then the Khmer world, then the awkward spellings.
A player should be able to name what got harder.

**Short is not the same thing as easy.** A four-letter word can be the hardest
thing on screen for a beginner, and those two pools exist to express that.

### 3. A boss that couldn't be an enemy

The finale asks the player to type a paragraph at a monster that never moves and
cannot be reached. Forcing that into the `Enemy` class would have meant an enemy
ignoring its own movement, hitbox and word — so `BossFight` is a phase, not an
entity.

The fight alternates: type a paragraph, then survive an attack phase, repeat.
Attack phases were originally timed at seven to ten seconds, which meant
ignoring one was survivable — the clock expired and the field was swept. They
are now **objective-based**: a phase sends a quota of attacks and ends only when
the quota is spent *and* the field it filled is clear. Its length belongs to the
player.

That cannot deadlock, and the reason matters: both attack kinds self-resolve. An
untyped summon walks in and breaches; an unanswered bolt lands. Either way it
leaves the field and costs a life, so a player who stops typing loses the run
rather than hanging the phase. There is a sixty-second guard, but it is a *bug*
guard — phases measure six to twenty seconds, and if it ever fires something is
genuinely stuck.

Two smaller decisions inside it are worth recording. Verse words confirm on an
explicit **space**, not the instant the last letter lands: prose is typed
word-then-space, and a field that clears itself out from under the space you
were already about to type leaves that keystroke landing on an empty buffer.
And the boss now **harasses** during the paragraph, because a paragraph nothing
could interrupt was a safe window, and a safe window is a window with no
decisions in it.

### 4. Twenty monsters at once

Reported as "after round 10 it just keeps spawning." The cause was two curves
where only one stopped: the per-level enemy count plateaued, but the spawn
interval kept shrinking. So past the plateau the only thing still escalating was
the *rate*, which does not add enemies to a level — it converts them into
enemies on screen simultaneously. Medium's last wave arrived in 8.5 seconds.
Hard's final six levels were identical to one another.

There was no concurrent cap anywhere.

There is now: four on Easy, six on Medium, eight on Hard. It is a **reading**
limit, not a screen-space one. Every live enemy is a word to scan and choose
between, and past about six the player stops reading and starts guessing —
prefix matching turns ambiguous at the same moment, so the target you hit stops
being the target you meant.

The two ceilings — how many a level *sends* versus how many may be *alive* —
had been conflated into one number, and separating them is what let late levels
be genuinely longer instead of merely more frantic.

### 5. The word that stuck to a dead monster

Type at an enemy, have it reach the temple mid-word, and the letters stayed in
the input field. The next keystroke was then measured against a buffer the
engine had already discarded: a red flash and an accuracy penalty for a monster
that was taken away from you.

There *was* a guard, and it had two holes. It only fired when the breaching
enemy was the **locked** target — but the lock is null for as long as a prefix
still matches more than one enemy, which is exactly when a breach surprises you.
And even when it fired, it cleared the engine's buffer without telling the text
field, which lives in Swing and is only ever cleared from the keystroke
callback.

The fix is a single end-of-tick check: if what you have typed no longer matches
anything alive, drop it and signal the UI once. A buffer that still matches
something live is deliberately kept — if another enemy shares the prefix your
keystrokes are still good, and taking them would be its own small theft.

The first attempt at this fix *failed*, and instructively. The old
per-removal-site resets were left in place alongside the new check; they fired
first, emptied the buffer, and the new check then found nothing stale and stayed
silent. The bug survived its own fix. Now exactly one place may clear the
buffer.

### 6. Art that looked like a rendering bug

Three monster sprites arrived as cut-outs saved onto white rather than with
transparency. The failure mode is quiet and very confusing: the trim step finds
no transparent margin, keeps the full square canvas, derives a 1:1 aspect ratio
from it, and the monster draws squashed inside an opaque white box, floating
above the plaza because the canvas bottom rather than its feet is anchored to
the ground line. Nothing throws.

They were keyed by flood-filling from the border only — never a global colour
match, which would punch holes through light pixels inside the sprite — with a
saturation guard so pale highlights on the art are not mistaken for background.

A related quality bug was self-inflicted. Every sprite was being cached at the
boss height of 380 px so that any type *could* be a boss, then bilinearly
reduced to its real size every frame. Bilinear samples a 2×2 neighbourhood, so
reductions past 2× undersample — which reads as a soft, shimmering sprite rather
than a small one. Only the types a tier actually ends on now get that headroom.

---

## Recurring lessons

**A superseded mechanism is worse than no mechanism.** The `tricky` pool that
gated nothing; the buffer resets that pre-empted the check meant to replace
them. In both cases the dead code did not merely sit there — it actively
defeated the thing that replaced it. Both were found by tests written to
describe the intent rather than the implementation.

**Short is not easy; small is not simple.** The two difficulty bugs and the two
art bugs all came from a proxy standing in for the thing that actually mattered:
word length for typing difficulty, level count for level density, canvas size
for sprite size.

**Data beats code for tuning.** Difficulty bands, vocabulary and boss paragraphs
all live in `words_en.json`. Making an early level gentler is an edit there,
never a recompile.

**The build is the only real verification.** Static checks and simulations
narrow things down, but several rounds of failures came from tests that modelled
the engine one step off — riding out a phase that no longer expires, counting a
spawn that belongs to the next level. Running the tests is what caught them.

---

## Layout

```
src/main/java/com/guardiansofangkor/
  engine/     GameState, GameLoop, WaveManager, BossFight, difficulty tuning
  entities/   Enemy, Projectile, PowerUp, Player — no AWT imports, ever
  matching/   prefix matching and target resolution
  renderer/   everything that paints; reads state, never mutates it
  i18n/       word bank, fonts, language
  util/       config constants, crash containment, JSON
src/main/resources/
  words/      words_en.json — vocabulary, difficulty bands, boss paragraphs
  images/     sprites and backdrops
  fonts/      optional; see the README there
```

The layering rule is strict and worth stating once: **game logic never lives in
rendering classes, and rendering classes never contain gameplay logic.**
`GameState` and `Enemy` must not import `java.awt` or `javax.swing`.

## Running it

```
./gradlew build     # compile and run the test suite
./gradlew run       # play
```

Fonts are optional and not committed — the interface degrades to platform
serifs without them. See `src/main/resources/fonts/README.md`.
