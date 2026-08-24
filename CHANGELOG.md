# Changelog

All notable changes to thabit are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The phase-by-phase record, with every decision and the reason behind it, lives in
`PLANNING.md`; this file is the short version.

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
