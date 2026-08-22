@context.md

<!--
  This file exists only to load context.md.

  Claude Code auto-loads CLAUDE.md and nothing else, so renaming the notes to
  context.md would have made them invisible to it. The @import above pulls
  context.md into the session at launch, which keeps one source of truth: edit
  context.md, never this file.

  A symlink would also work, but needs Administrator or Developer Mode on
  Windows — and this project already has a Windows tester, so the import is the
  portable choice.

  Everything between these comment markers is stripped before it reaches
  Claude's context, so notes for maintainers cost nothing to leave here.

  See also:
    context.md   — architecture, invariants and the reasons behind them
    document.md  — the project journey, written for people
-->
