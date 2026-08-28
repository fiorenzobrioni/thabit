# Changelog

All notable changes to thabit are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The phase-by-phase record, with every decision and the reason behind it, lives in
`PLANNING.md`; this file is the short version.

## [Unreleased]

### Added

- **`$ thabit init`** — the first run as a terminal session rather than a
  carousel: one screen, two answers (write the first habit, which opens the
  wizard, or skip). thabit has no mandatory permission to ask for, so the one
  thing it cannot start without is a suite. An install that already holds a test
  or a check is never asked, and the shell draws nothing until that check has
  landed, so a returning reader never sees a flash of setup.
- **`HELP.md`** — the second file behind the Settings tab bar: the app explained
  to somebody who does not read `git` for a living. The four tabs, the borrowed
  words (`test`, `suite`, `assert`, the boxes, `commit`, `--amend`, `diff`,
  `branch`), where the numbers come from, and one paragraph on why it looks like
  this. It does not restate the `README.md` tab's glossary; one line hands the
  reader there, where those words stand next to the numbers they are about.
- **A one-shot pointer** — `# new here? open HELP.md` at the head of
  `habits.test`, spent by the tap or by opening the file any other way. A `#`
  and not a `//`, because the comment channel wears the host file's syntax.

Both new surfaces are localized, like the `README.md` tab: they are the ones
addressed to somebody who cannot read the app yet.

### Changed

- **The comment channel now speaks the reader's language** when what it is
  saying is a sentence. The rule that used to read "comments stay English"
  mistook the punctuation for the register: under a `#` sat both `# 07:12`, which
  is a readout, and `# tap the command to confirm`, which exists only to be
  understood. Now the register decides. Code is still English everywhere — keys,
  file names, `$` commands, check lines and verdicts, asserts, hashes, log
  levels, `# amended`, `// active` — and so are the row comments of
  `habits.test`, because they are live detail and one translated row would leave
  the column bilingual. What moved is the prose: the empty suite, the not-due
  summary, the two-tap confirms, every note in `settings.config`, the wizard's
  prompts and the plain meaning beside each token it offers, the hints in
  `stats.md` that explain `coverage`, `flaky` and `regression`, the notification
  footers, the widget's affordance, and the terminal output that says why a tap
  was refused. The marker never moves and neither does the level: an error reads
  `// ERROR: manca il permesso per le notifiche — tocca per concederlo`.
- **`habits_history.diff` opens the way `git` does in Italian** — `# Sul branch
  main — modifiche non ancora committate (oggi)`, with `branch`, `main` and
  `commit` exactly where they were. That is not a loosening of the metaphor: it
  is what the tool the metaphor is borrowed from actually prints under
  `LANG=it_IT`.

None of this discharges the plain-language promise: a translated comment is
politeness, not a gloss. The `README.md` tab, the notifications and the
accessibility text still carry the whole meaning on their own.

- **The heatmap draws the whole window.** Every day behind the reader now carries
  at least a dim `·`; before, anything the app had nothing to say about was blank,
  so a young suite was three squares floating in a void with no grid to place them
  against. The dot is the graph paper, not a verdict about that day: a mark every
  past cell carries cannot read as an accusation, and it is not a fourth intensity
  either — `·` means there is no level here, `□` still means the day ran and passed
  none of it. Only the future is blank.
- **The grid speaks the reader's language.** Its row and month labels were a
  hardcoded English, so an Italian phone read `Mon`/`jun` while every other date in
  the app was already localized. They are now lowercase three-letter names in the
  reader's own language, like the graph this borrows from.

### Fixed

- **The `+` button is one thing to a screen reader.** It declared its own
  description without making itself a merge boundary, so TalkBack announced it as
  part of whatever larger block happened to sit above it, and a test looking for
  it passed or failed depending on what else was on screen.

## [1.0.0] - 2026-08-24

The first release. thabit is an offline Android habit tracker whose interface is
a code editor and whose vocabulary is a CI system: habits are tests in
`habits.test`, a day is a build, the history is a git log, and the stats are a
coverage report.

### Added

- **`habits.test`** — today's suite as a YAML-flavored file. One line per test
  due today, checked off with a tap on its box; a tap on the name expands the
  test's spec (`when:`, `assert:`, `remind:`, `streak:`, `health:`) with
  `[~ skip]`, `[edit]` and `[rm]`. Counters answer a terminal prompt in place and
  offer `[+N]` when that is genuinely a shortcut; avoid tests assert absence and
  pass at commit. A test not due today is a commented line, not a hidden one, and
  it opens like every other.
- **`habits_history.diff`** — one commit per day, with the day's build result
  (`✓ build passed`, `~ build unstable (4/6)`, `✗ build failed`) and its
  arithmetic beside it. Week separators carry the quota verdicts. Yesterday, and
  only yesterday, can be amended; the commit says `# amended` when it was.
- **`stats.md`** — a contribution heatmap, a coverage report, a suite-health
  table, flaky tests, regressions, and records as tag rows that jump to the
  commit that earned them. Every rule that produces a number is printed in the
  file itself.
- **`settings.config`** — the settings as a JSON file whose values are the
  controls: the day boundary (`day_ends`), week start, editor options, theme
  profiles (Obsidian, Dracula, Monokai), notification switches and the export.
- **`README.md` tab** — the same day in plain prose, fully localized, including a
  glossary of every CI word the app uses.
- **`$ thabit add` / `edit`** — the wizard as a terminal session rather than a
  form: one answer is enough, everything else has a default.
- **Notifications** — a per-test reminder answerable from the shade, the day's
  build result at the boundary, and an opt-in evening summary. Reminders are
  inexact by design (`setWindow`, no exact-alarm permission).
- **Home widget `thabit --status`** — today's suite on the home screen, with a
  yes-or-no habit ticked off straight from its row. It states the date it is
  rendering, so a widget that has not repainted since midnight says so.
- **`$ thabit export --json | --csv`** — the suite, every check, and every day of
  presence, written to Downloads through MediaStore with no storage permission.
  The JSON header carries the rules the app computes with, so the archive cannot
  drift from the screens.
- **Italian and English**, per-app language. Code stays English (keys, filenames,
  comments, terminal output, commit lines); chrome, data values, notifications
  and everything a screen reader says are localized.

### Design rules held throughout

- **The file must not lie.** Pending is pending, skips are neutral and stated,
  amended commits say so, and a day the app never saw is `no run` — no commit, a
  blank cell, out of every denominator. Inventing a red build for a day nobody
  was there is the same class of lie as a fake zero.
- **Verdicts are computed on read, never persisted.** Streaks, health, build
  results and records all derive from the stored checks whenever asked, so
  amending yesterday recomputes everything for free and there is no midnight
  mutation to get wrong.
- **No CI term is ever the only place a fact exists.** Every verdict ships with
  its arithmetic, every term on screen has a plain sentence in the `README.md`
  tab, and every row speaks words rather than glyphs to a screen reader.
- **No network, and no `INTERNET` permission at all.** Habit data stays on the
  device. No accounts, no sync, no analytics, no gamification.

[1.0.0]: https://github.com/fiorenzobrioni/thabit/releases/tag/v1.0.0
