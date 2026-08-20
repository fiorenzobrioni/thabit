# VISION.md — thabit

> **A habit tracker that thinks it is a test suite.**
> Your habits are the tests. Every day is a build. Committing is the whole point.

thabit is the third app of the **t-series** (after [tweather](https://github.com/fiorenzobrioni/tweather) and [tsteps](https://github.com/fiorenzobrioni/tsteps)): real, single-purpose Android apps whose entire UI mimics a code editor and a terminal. This document fixes the soul of the series as inherited (§1), explains how the metaphor deepens in the habit domain (§2), and specifies the product (§3 onward).

---

## 1. The soul inherited from the series

These are the rules tweather discovered and tsteps confirmed — the parts that *are* the series. They are not styling suggestions; they are the identity.

### 1.1 The editor is the interface, not a skin

Every screen is a **fake file** behind a bottom tab bar. Data is rendered as syntax-highlighted source (YAML, diff, markdown, config) with a line-number gutter, editor tabs, and a terminal status bar. There are no cards, no lists-with-icons, no Material widgets wearing a dark theme. If a piece of UI cannot be expressed as something a code editor or a terminal would show, it does not ship.

Corollaries proved on device by the first two apps:

- **Controls are text.** Checkboxes are `[x]`/`[ ]`. Removal is `[rm]`. Destructive actions are `$` shell commands with a two-tap confirm. Inputs are terminal prompts with a blinking `_` cursor. Booleans are tappable `true`/`false` values.
- **The comment channel.** Comments are the loading, error and hint channel. A failure reads like a compiler message, not a toast. New in thabit: the comment channel wears **the host file's own syntax** — `#` inside YAML, `//` inside the JSON-style settings — because a YAML file with `//` comments would be the file lying about what it is.
- **Icons are emoji inside the text** (`🧪`, `📖`, `🔥`), never image assets in the body.
- **The file must not lie.** Estimates say so, missing data is missing (never a fake zero), stale is labeled stale, and a state that has not happened yet is `pending`, not pre-checked.

### 1.2 Design system: Obsidian Syntax (non-negotiable)

Full token set in tweather's `obsidian_syntax/DESIGN.md`; the Compose implementation is ported from tsteps into `ui/theme/`.

- **JetBrains Mono everywhere.** 4px baseline grid, 20px indent per nesting level. Single sanctioned exception: home-widget layouts use system `monospace` (CVE-2021-0567 — launchers silently drop `@font/` in widget contexts).
- **Syntax colors** (Obsidian profile): keys `#79c0ff`, strings `#a5d6ff`, numbers/booleans `#ffa657`, comments/punctuation `#8b949e`, diff add `#2ea043`, diff del `#f85149`.
- **Core palette**: background `#10141a`, surface container `#181c22`, on-surface `#dfe2eb`, borders `#30363d`.
- **No drop shadows.** Depth = 1px borders + tonal stacking. The FAB's glow is the only shadow-like effect, and the FAB is rectangular — nothing in an editor is round. 4px corner radius on every element.
- **Theme profiles**: Obsidian (default), Dracula, Monokai — switchable at runtime, dark-only. No light theme exists in the series.

### 1.3 The localization rule

English and Italian via the system per-app language picker (minSdk 33). The split is semantic: **code stays English** — YAML/JSON keys, filenames, comments, terminal output, commit hashes and check lines. **Chrome and data values are localized** — navigation labels, accessibility text, day names, notification titles. The `README.md` fake file is prose, so it localizes fully, headings included. **Habit names are user data**: they are written by the user in whatever language the user writes them.

### 1.4 Engineering ethos

- Single module, no DI framework (hand-rolled `ServiceLocator`), Kotlin 2.2 + Compose M3, version catalog.
- DataStore for settings/state, Room for history, one shared WorkManager periodic job — never one job per feature.
- JVM-only test suite (Robolectric for Compose), runs in CI **before** any APK is built: a red suite must never produce an installable artifact. (In this app that sentence is also the product.)
- Committed shared debug keystore; release build minified by R8, unsigned by default, debug-signable only via explicit flag.
- `PLANNING.md` is a phased log with checkable steps where every decision and deviation is recorded **with its reason**. Battery cost is a design input, not an afterthought.

---

## 2. The metaphor upgrade: from repository to CI

tweather wore the editor as a skin; tsteps made the repository real (day = commit, steps = added lines). thabit closes the loop with the part of the developer's world the first two only borrowed glyphs from: **continuous integration**. A habit tracker *is* a CI system for a person — a suite of checks that runs every day, builds that pass or fail, flaky tests, coverage, and the discipline of committing daily.

| dev concept | thabit meaning |
| --- | --- |
| test case | **a habit** — one thing you intend to do |
| the test suite | **your set of habits**, versioned like code |
| a build / test run | **a day** — the suite runs from wake to midnight |
| commit | **the completed day**, committed at midnight |
| ✓ / ~ / ✗ build result | **the day's outcome**: passed / unstable / failed (Jenkins semantics) |
| assertion | **the habit's criterion**: `assert pages >= 20` — measurable habits are literally asserts |
| `@Ignore` / skipped test | **a justified skip** (`[~]` sick day, travel) — never breaks a streak, never counted against you |
| adding / removing a test | **creating / archiving a habit** — visible as `+`/`-` lines in that day's diff |
| flaky test | **an inconsistent habit** — low pass rate, reported factually |
| test health | **habit strength** — an exponential moving average that forgives a bad day |
| cron / scheduled job | **a reminder** — the test's scheduled run |
| `git commit --amend` | **the one-day grace period** — yesterday can be fixed, history before it cannot |
| contribution graph | **the heatmap** — intensity = fraction of the suite that passed |
| `git tag` | **records** (longest streak, perfect week) pinned to their commit |
| coverage report | **completion rates** in stats.md |

And the double meaning of **commit** carries over from tsteps and lands harder: this app is *only* about committing — there is no sensor doing the work for you. A streak is a commit streak you typed yourself.

One rule keeps this healthy, inherited verbatim: **the metaphor serves the data, never the reverse.** No CI concept gets a screen unless it answers a real user question ("what do I still have to do today?", "am I consistent?", "which habit is failing me?").

---

## 3. Product identity

### 3.1 What thabit is

A minimal Android habit tracker. It answers, at a glance and honestly:

- What is due today, and what have I already done?
- Did I hold the line this week — on each habit, and overall?
- Which habits are solid, and which are flaky?

**Product promise:** *the whole state of your habits in one file, and a history that never lies.* No account, no wearable, and — like tsteps — **no network**: the app does not request the INTERNET permission at all. Habit data is among the most intimate data there is; in thabit it never leaves the device except when the user explicitly exports it.

### 3.2 What thabit is not

Not a todo list (a task is done once and dies; a test runs forever). Not a gamified pet or an RPG — no XP, no coins, no avatar to disappoint. Not a journal, not a mood tracker, not a goal/project planner, not a coach, not a social network. Every future feature request is tested against this list.

### 3.3 Product principles

1. **Minimal by default.** Every feature justifies its presence; if it serves few users and complicates the main file, it is optional or deferred.
2. **One glance, one tap.** The most frequent interaction — checking a habit off — is a single tap on a checkbox in the main file, or on the widget without opening the app at all.
3. **Forgiving mechanics, honest reporting.** Streaks exist but health (an EMA) is the primary strength signal: one missed day dents it, it does not zero it. Skips are first-class and neutral. The reporting never softens a number — the *mechanics* are humane, the *file* is honest.
4. **No guilt engine.** `✗ suite unstable (4/6)` states a fact; the app never says "you're slipping!". Failed tests are rendered exactly as CI renders them: information for the next run. No red badges screaming on the launcher icon, no shame notifications, evening digest strictly opt-in.
5. **History is immutable — with one honest exception.** Committed days are read-only, like any git history. The exception is `--amend`: **yesterday** stays editable, because "I did it and forgot to tick it" is the most common habit-tracker lie and the cure is a grace window, not a falsified history. Amended commits are labeled `# amended`. Two days back is history, forever.
6. **Local first.** Room + DataStore. Export is a file the user owns. No cloud, no sync, no account — by identity, not by roadmap.

---

## 4. The files (screens)

Four tabs, like the siblings: **Editor / Log / Stats / Settings**. Each opens a file.

### 4.1 `habits_test.yaml` — today (main screen)

The suite, mid-run. One line per test due today; the checkbox is the tap target. Comments (`#` — it is YAML) carry the live detail: pass time, counter progress, skip reason, quota state.

```yaml
# habits_test.yaml
# suite 2026-08-20 — 3 passed · 2 pending · 1 skipped

- [x] meditate 10 min            # 07:12
- [x] read 20 pages 📖           # 23 pages
- [ ] pushups                    # 12/30    [+1]
- [~] run 5k                     # skip: rest day
- [ ] no sugar                   # holds — asserts at commit
- [x] journal                    # 21:40

# 2 weekly tests not due today — [show]
```

- **Boolean test**: tap `[ ]` → `[x]` (with the pass time stamped in the comment); tap again to undo a mistap — today is the working tree, it is *supposed* to change.
- **Measurable test** (an assert): tapping the checkbox opens the in-place terminal prompt (`> pages: _`, `[esc]` to cancel); the box turns `[x]` when the assert holds. Counters with small steps get a `[+1]` text control at line end (water glasses, pushup sets) so the common case is one tap.
- **Avoid test** (`no sugar`): asserts *absence* — it stays `[ ]` with `# holds — asserts at commit` and passes at commit unless the user taps it to mark it `[!]` failed (optional note). The file never pre-checks something that has not finished being true.
- **Skip**: from the test's in-place expansion — `[~ skip]` with an optional note prompt. Skips are neutral everywhere: streaks hold, rates exclude them.
- **Tapping the test name expands it in place** (the series' session-detail pattern): the full spec as indented YAML — `when:` (schedule), `assert:` (if measurable), `remind:`, current streak, `health: ▓▓▓▓▓▓▓▓░░ 82%` — plus the text-rendered actions `[~ skip]`, `[edit]`, `[rm]` (archive, two-tap).
- Tests scheduled but **not due today** (weekday-bound, quota already met) collapse into one comment line with `[show]`; quota tests still due show their week state (`# 2/3 this week`).
- Empty suite (first launch): the file is honest — `# no tests in the suite yet` — and the FAB explains itself with a hint comment.
- **FAB** (glowing, rectangular): **`+`** — adds a test (§4.5). Each t-series app gets exactly one glowing verb: tweather refreshes, tsteps starts a walk, thabit grows the suite.
- **Second editor tab: `README.md`** — the day as localized prose (markdown source), series pattern: `## Today` (what ran and what's left, in neutral words), `## Status` (the build badge, streaks worth mentioning), `## Week` (compact 7-day table, today bold and live). Fully localized, headings included.
- Status bar: `⎇ main | 6 tests` left, `3/6 passed` live on the right.

### 4.2 `habits_history.diff` — the log

The CI dashboard as a git log. One commit per day; today sits on top as uncommitted changes.

```diff
# On branch main — changes not yet committed (today)
#   3 passed · 2 pending · 1 skipped

commit 9e31c7a  (tag: perfect-week)
Date:   Tue Aug 19

    suite: 6/6 passed
    ✓ build passed (6/6)

+ [x] meditate 10 min        07:03
+ [x] read 20 pages          31 pages
+ [x] pushups                30/30
...

commit 4b02d1f  # amended
Date:   Mon Aug 18

    suite: 4/6 passed · 1 skipped
    ~ build unstable (4/5 · 1 skipped)

--- week 34 · 89% passed (+6% vs week 33) ---
```

- Collapsed to commit lines by default; a day expands into its run: one line per test, `+` green for passed, `-` red for failed, `~` grey for skipped (with its note). Adding or archiving a habit shows in that day's diff as the suite itself changing: `+ test added: "journal"` / `- test archived: "cold shower"`.
- **Build result, Jenkins semantics**: `✓ build passed` (everything due passed), `~ build unstable (4/6)` in orange (something passed, something failed), `✗ build failed` red (nothing passed). Skips leave the denominator. A day with nothing due runs no check.
- **Quota checks live on the week**: a `3/week` test never fails a single day — its verdict belongs to the ISO week separator: `--- week 34 · quota: run 5k 2/3 ✗ ---`. The week as a diff, now with its own CI line.
- **`--amend`**: yesterday's expanded run stays tappable — same interactions as today. On first edit the commit gains a permanent `# amended` marker. Older days are read-only, forever.
- Records are `tag:` refs on their commit (`tag: perfect-week`, `tag: longest-streak`).
- Status bar: `⎇ main | N commits` left, `HEAD → <hash>` right.

### 4.3 `stats.md` — coverage report

The screen the CI metaphor unlocks: consistency as a contribution graph plus a per-test health table no mainstream tracker renders this honestly.

```markdown
# stats.md

## contributions (last 12 weeks)

Mon  ■ ■ ▪ ■ □ ■ ■ ■ ▪ ■ ■ ■
Wed  ■ ▪ ■ ■ ■ □ ■ ▪ ■ ■ □ ■
Fri  □ ■ ■ ▪ ■ ■ ■ ■ ▪ ■ ■ ▪
Sun  ■ ■ □ ■ ▪ ■ □ ■ ■ ▪ ■ ■
         jun         jul         aug

## suite health

| test             | health | streak | 30d    |
| ---------------- | ------ | ------ | ------ |
| meditate 10 min  | 92%    | 18     | 28/30  |
| read 20 pages    | 87%    | 6      | 25/30  |
| pushups          | 41%    | 0      | 11/30  |

## flaky tests

pushups — 37% pass rate over 30 days
# a flaky test wants a smaller assert or a different schedule

## tags

| tag            | value               | date       |
| -------------- | ------------------- | ---------- |
| longest-streak | meditate · 43 days  | 2026-07-30 |
| perfect-week   | week 33 · 100%      | 2026-08-16 |
```

- Heatmap intensity = **fraction of the due suite that passed** that day, relative buckets like tsteps (quartiles of the user's own non-zero days); skipped tests leave the denominator; future cells blank, today live.
- **Health** is Loop-style: an exponential moving average over each test's due days — a pass strengthens, a fail decays, a skip is neutral. It is the primary signal *because* it forgives: a 90-day habit does not die of one bad Tuesday. Streak is still shown — some people run on chains — but health leads the table.
- **Flaky tests**: pass rate below threshold over 30 days, stated with its number and one fixed factual hint. It appears only when there is enough data, and never more than the facts.
- Tag rows link to their commit in the Log (the tsteps `LogFocus` pattern).
- Status bar: `⎇ main | ro` — a stats file is computed, not edited.

### 4.4 `settings.config` — the settings

The series' JSON-with-comments format, verbatim from tsteps: booleans flip on tap, cycles cycle, free values open terminal prompts, reset is `$ git restore settings.config` with two-tap confirm.

```json
{
  "suite": {
    "day_ends": "00:00",         // the nightly build; "03:00" if your day ends late
    "week_starts": "monday"
  },
  "editor": {
    "line_numbers": true,
    "word_wrap": false
  },
  "theme": {
    "active_profile": "obsidian" // obsidian | dracula | monokai
  },
  "notifications": {
    "daily_commit": true,        // the build result, silent, at commit
    "pending_digest": false,     // evening reminder of pending tests — opt-in
    "digest_hour": "20:00"
  }
}

$ thabit export --json
$ thabit export --csv
$ git restore settings.config
```

- **`day_ends`** moves the commit boundary: night owls' midnights are 02:00. All verdicts, streaks and the heatmap respect it; DST and timezone changes are test cases, as always in this series.
- Per-test settings (schedule, reminder, target) live **on the test**, in its expansion and wizard — the suite is configured in the suite file, not in a settings mirror.

### 4.5 `$ thabit add` — the wizard (FAB)

Not a form: a **terminal session**. The FAB opens a transcript that asks one thing at a time, each answer a prompt or a cycling token — the same machinery as the series' inputs, composed into a conversation:

```
$ thabit add
> name: read 20 pages_
> type: [boolean] counter avoid      # tap to cycle
> assert: pages >= [20]
> when: [daily] mon..fri 3/week every 2d
> remind: [off] 07:00
> emoji: 📖 [skip]

✓ test added to the suite
```

`[edit]` on an existing test reopens the same transcript prefilled (`$ thabit edit "read 20 pages"`). Archiving is `[rm]` with two-tap confirm: the test leaves the suite as a `-` line in today's diff, **history preserved** — its past runs stay in every committed day and in stats' historical windows.

### 4.6 `thabit --status` — the widget

A terminal window on the home screen, inheriting the tweather/tsteps widget architecture whole (RemoteViews sizes-map, one line per tier step, measured breakpoints, configurable opacity, system monospace). Content — and this widget **acts**, not just shows:

```
$ thabit --status                🧪
suite:  ▓▓▓▓▓░░░░░ 3/6
[x] meditate          [x] read
[ ] pushups           [~] run 5k
[ ] no sugar          [x] journal
# 2 pending — tap to pass
```

- **Boolean tests check off from the widget** — the single most valuable feature a habit widget can have (Loop proved it). Measurable tests open the app on their prompt.
- Tiers cut from the bottom; the smallest tier is the suite bar alone.
- No polling: repaints on check-off, at commit, and on settings changes. If the day rolled over and nothing repainted yet, the widget shows the date it is rendering — never yesterday's suite posing as today's.

---

## 5. Metrics

| metric | placement | notes |
| --- | --- | --- |
| today's run (per-test state) | main file, widget | the working tree; pending is pending, never pre-checked |
| build result ✓/~/✗ | log, notification | Jenkins semantics; skips leave the denominator; no tests due → no check |
| streak (per test) | expansion, stats | consecutive *due* passes; skips hold it; computed on read, never persisted |
| health (per test) | expansion, stats | Loop-style EMA over due days; the forgiving, primary strength signal |
| pass rate 7/30d | stats | per test and suite-wide; denominators exclude skips |
| heatmap | stats | 12 weeks, intensity = due-suite pass fraction, relative buckets |
| quota state | main file, week separators | `2/3 this week`; verdict on the ISO week, never on a day |
| flaky tests | stats | pass rate under threshold, factual, only with enough data |
| records | stats, log | `tag:` refs — longest-streak, perfect-week |

Deliberately absent: points, levels, coins, avatars, "momentum scores" with secret formulas, mood tracking, motivational quotes, and any number the user cannot recompute from their own exported data.

---

## 6. Decisions, and what the field taught us

A survey of the habit-tracker landscape (Loop Habit Tracker, Streaks, Habitify, HabitNow, Habitica) was done before this document; what was adopted, adapted or rejected is recorded here so the reasoning survives.

1. **From Loop — adopted, it is the gold standard of honest trackers**: the strength EMA (our *health*, reframed as test health); flexible schedules (`3/week`, `every 2d`, weekday sets); measurable habits (reframed as *asserts* — the metaphor improves them); skips as first-class; check-off from the widget; full data export. Loop is open-source and account-free like us; thabit's answer to "why not just Loop?" is the same as tsteps' answer to pedometers: the file, the honesty rules, and the series.
2. **From Streaks — adopted**: negative habits (our *avoid* tests, reframed as asserting absence). **Rejected**: timed habits — thabit is not a stopwatch. *Parked with a note*: a `$ thabit run "meditate"` live timer process would fit the series' running-process pattern (tsteps' `track`); it waits for real demand, not for symmetry.
3. **From HabitNow/Habitify — adopted**: the custom day boundary (`day_ends` — the single most requested feature in the category's reviews). *Deferred*: time-of-day grouping (morning/evening sections in the suite file) — designed to render as YAML section comments, shipped only if the flat file proves too long in practice. **Rejected**: mood logging, notes-as-journal.
4. **From Habitica — rejected wholesale**: gamification is the anti-identity. No rewards, no punishments, no theatrics. The dopamine in thabit is a checkbox turning `[x]` and a green cell in the graph — the same dopamine git gives you.
5. **Amend over mutable history** (ours): every tracker allows backfilling forever, which makes every streak suspect. thabit's one-day `--amend` grace window is the honest middle: forgetting is human, rewriting last month is fiction.
6. **No per-habit colors** (ours): the palette is the palette. Identity comes from the optional emoji on the test line — icons are emoji in this series.
7. **Reminders are inexact by design** (ours): scheduled with `AlarmManager.setWindow` (~10 min), no exact-alarm permission, documented in the file (`# reminders are approximate — battery first`). A habit reminder is a nudge, not an alarm clock.
8. **Verdicts are computed on read, never persisted**: a day's build result derives from its stored checks whenever asked. There is no midnight mutation to get wrong, no streak counter to corrupt, and `--amend` recomputes everything for free. The midnight worker only notifies and repaints — it never writes data.

---

## 7. Data & scheduling strategy

- **Storage**: Room — `habit` (name, type, assert target/unit/step, schedule, reminder, emoji, position, `createdAt`, `archivedAt`) and `check` (habit id, local date, state pass/fail/skip, value, note, stamped time). One row per test per day, written by taps; everything else derives. Pruned never — a decade of checks is trivially small.
- **The commit is a boundary, not a write**: at `day_ends` the day becomes read-only (minus the amend window) by *definition*; nothing is copied, frozen or summarized. `DaySummary`-style snapshots are unnecessary because checks are already immutable facts.
- **Rollover worker** (one shared periodic job, series rule): fires at the `day_ends` boundary to post the `daily_commit` notification and repaint the widget; a safety-net pass on app open covers phones asleep at midnight. DST, timezone changes and `day_ends` edits are test cases.
- **Reminders**: per-test inexact alarms (`setWindow`), rescheduled on boot and on schedule edits; notification carries a **pass action** for boolean tests — the habit checks off from the shade. `pending_digest` (opt-in) is a single evening summary, never one nag per test.
- **No network. No INTERNET permission.** The CI badge for this in the README stays the series' proudest line.
- **Export** (`$ thabit export --json|--csv` in settings): the full suite and every check, canonical values, `Locale.ROOT`, MediaStore into Downloads with the tsteps pending→publish pattern. The user's history belongs to the user.
- **Health formula**: exponential moving average with the half-life chosen once, documented in the export and in this file when fixed (Fase 2) — no secret numbers: the user must be able to recompute every stat from their export.

---

## 8. MVP

1. Suite CRUD via `$ thabit add`/`edit`/`[rm]` — boolean, counter (assert), avoid; daily/weekday/quota/interval schedules
2. `habits_test.yaml` — live run, one-tap pass, `[+1]`, prompts, skips, in-place expansion
3. `habits_history.diff` — day commits with build results, week separators with quota checks, `--amend` on yesterday
4. `stats.md` — heatmap, suite health table, streaks, flaky tests, tags
5. `settings.config` — day_ends, week start, editor, theme profiles, notifications, reset
6. `README.md` day tab
7. Notifications: per-test reminders with pass action, `daily_commit`, opt-in `pending_digest`
8. Widget `thabit --status` with widget check-off
9. IT/EN localization, per-app language
10. Export JSON/CSV

Deferred: time-of-day groups, `$ thabit run` timer process, Wear OS (probably never). Rejected: gamification, cloud/accounts, mood, per-habit colors.

## 9. Success criteria

The first release succeeds if a user can install it, add three habits in under a minute through the wizard, and then: check today off in one glance and three taps (two of them on the widget); trust that a `[x]` was typed by them and nobody else; find any past day and see exactly what ran; watch a bad week dent their health without erasing their history; and never once meet a guilt mechanic they did not opt into. The deeper test, inherited twice over: **does adding a feature make the app better, or just bigger?**

> **North star:** a suite you wrote yourself, a build every day, a history that never lies — and the quiet satisfaction of a green checkmark you earned.
