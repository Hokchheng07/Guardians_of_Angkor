# Khmer fonts

Two files are expected here. Neither is committed — they are downloaded per
checkout because of font licensing, and because binaries bloat the repo.

| Slot | File | Source |
|---|---|---|
| Primary | `Suwannaphum-Regular.ttf` | https://fonts.google.com/specimen/Suwannaphum |
| Backup | `KantumruyPro-Regular.ttf` | https://fonts.google.com/specimen/Kantumruy+Pro |

Both are Open Font License, so they are safe to redistribute with the game.

## Installing

1. Open each link, click **Get font** then **Download all**.
2. Unzip and copy the two `*-Regular.ttf` files into this folder.
3. Run the game. `FontManager` logs which face it picked at startup.

## What happens without them

Nothing breaks. `FontManager` walks a chain:

1. Suwannaphum (primary)
2. Kantumruy Pro (backup)
3. Any Khmer face already installed on the machine
4. Plain sans-serif

Only step 4 fails to render Khmer, and it renders English normally, so the game
is fully playable in English on a fresh clone. The loader also accepts a few
alternative filenames (`Suwannaphum.ttf`, `KamtumruyPro-Regular.ttf`,
`NotoSansKhmer-Regular.ttf`, `KhmerOS.ttf`) so nobody has to rename a download.

## Why the font must be bundled at all

Swing does not substitute fonts for missing glyphs the way a browser does. If
the running face has no Khmer coverage, Khmer text draws as empty boxes rather
than falling back automatically — hence `Font.createFont` plus an explicit
`registerFont` call.
