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

English and Italian via the system per-app language picker (minSdk 33). The split is semantic, and since Aug 2026 it is drawn by **register**, not by the punctuation around the string (series decision, recorded in full in tweather's `PLANNING.md` Fase 18, here in `PLANNING.md` Fase 15):

- **Code is always English.** YAML/JSON keys, filenames, `$` commands, terminal output, commit hashes, check lines and verdicts (`✓ pass`, `~ unstable`, `✗ fail`), log levels (`ERROR:`, `WARN:`), one-word markers (`# amended`), file banners (`# habits.test`), licenses. Test: if translating it would break a lookup, a filename, a copy-paste or the alignment with a key printed elsewhere, it is code.
- **Data localizes**: navigation labels, day names, notification titles, units. **Habit names are user data** — written by the user in whatever language the user writes them.
- **Prose localizes wherever it appears** — the `README.md` day tab (fully, headings included), `HELP.md`, `$ thabit init`, notifications, accessibility text, **and the comment lines that are sentences** (`# tap the command to confirm`, `# a reminder is a nudge — it can arrive a few minutes late`, `# empty to turn it off`). The marker and the position on screen do not change: **the syntax is the fiction, the language is the reader's**, and the comment channel still wears the host file's syntax (§1.1).

The precedent is the metaphor's own tooling. Under `LANG=it_IT`, `git status` prints "Sul branch main" and keeps `branch`, `commit`, `HEAD`; `gcc` localizes its diagnostics the same way. A file whose keywords are English and whose margin is in the reader's language is exactly what a localized toolchain looks like — the earlier wording (an English comment channel, always) mistook the channel for the register and left the app more English than git itself.

thabit is where that cost the most, because its comments are barely comments: `# tap the command to confirm`, `# how often?`, `# still editable until 23:59`, `# nothing due today` are the app addressing the reader, in the only channel a `.test` file has. **It does not discharge §3.3.7, though, and must not be read as having paid that debt**: the plain-language path is still the `README.md` tab, the notifications and the accessibility text, and it still has to carry the whole meaning by itself. A translated comment is politeness, not a gloss — `# 4/6 passati` explains `~ build unstable` no better than the English line did.

Two consequences worth stating. A line often holds both registers: keep the tokens and translate around them (`# ancora modificabile fino alle 23:59`). And the rule applies **completely or not at all** — kept four times out of five it reads as a half-finished translation rather than as a design, which is why implementing it is a closed phase and never an opportunistic touch-up.

*Implemented in Fase 15, and implemented the way the document layer already worked: the prose a document decides on travels as a **string id**, and the renderer speaks it. `SettingsDocument` still says which note each key carries, `SuiteDocument` still decides which lines an empty suite gets — they just name them instead of spelling them, so they stay pure values and the screens stay the only place that reads a locale.*

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
| coverage of the pipeline | **days that actually ran**, not the pass rate |
| a build that never started | **a day with no run** — unknown, never counted as a failure |
| a regression | **a solid habit that has started breaking** |
| a branch | **a suite profile** (`main`, `vacation`) — deferred, §6.10 |
| `--watch` | **a timed test**, parked (§6.2) |

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
4. **No guilt engine.** `~ build unstable (4/6)` states a fact; the app never says "you're slipping!". Failed tests are rendered exactly as CI renders them: information for the next run. No red badges screaming on the launcher icon, no shame notifications, evening digest strictly opt-in.
5. **History is immutable — with one honest exception.** Committed days are read-only, like any git history. The exception is `--amend`: **yesterday** stays editable, because "I did it and forgot to tick it" is the most common habit-tracker lie and the cure is a grace window, not a falsified history. Amended commits are labeled `# amended`. The window is defined precisely: "yesterday" is the previous **logical** day, and it stays editable until the current logical day ends at `day_ends` — the grace shrinks from a full day to zero as today passes. Coming back after several days away, only the most recent closed day is amendable; the days before it are `no run` and stay that way. Two days back is history, forever.
6. **Local first.** Room + DataStore. Export is a file the user owns. No cloud, no sync, no account — by identity, not by roadmap.
7. **The metaphor is a gain for whoever gets the joke, never a toll for whoever does not.** The editor *look* asks nothing of the reader: monospace, a gutter and `[x]` boxes are a checklist in a good outfit, and the daily gesture is the same tap every tracker has. What can exclude is the *vocabulary* — `unstable`, `flaky`, `coverage`, `regression`, `assert`, `boolean`, and above all `commit`. So the rule is verifiable instead of well-meant: **no CI term is ever the only place a fact exists.** A verdict always ships with the arithmetic that explains it (`~ build unstable (4/6)` — the numbers are the gloss), and every term the app is currently rendering has a plain-language equivalent one tap away, in the reader's own language, in the `README.md` tab. Since §1.3's register rule the comment channel speaks the reader's language too, and that changes nothing here: a translated comment is politeness, not a gloss, so this principle is discharged by the arithmetic and by the `README.md` tab, or it is not discharged at all. Deliberately rejected: a "beginner mode", or a switch that renames the terms. That is two apps in one, which principle 1 forbids, and it would make the metaphor optional — when the whole point is that it costs nothing to whoever ignores it.
8. **The app is allowed to not know.** A day the app never saw is `no run`, not a failure: recording a fail for a day nobody was there would be inventing data, which §1.1 forbids. Unknown days are absent from every denominator and neutral for health — but they do break streaks, because a streak is a chain of passes somebody typed and an unknown day is not a pass. Coverage — how many days actually ran — is reported as its own number instead (§4.3). In the heatmap they are a dim `·`, like every other day already behind the reader (Fase 16a): the dot is the graph paper, not a verdict, and a grid with holes where the app has nothing to say is a grid nobody can read. What this principle forbids is colouring an unknown day like a failure, and `□` — the day ran and passed none of it — stays a different glyph.

---

## 4. The files (screens)

Four tabs, like the siblings: **Editor / Log / Stats / Settings**. Each opens a file.

### 4.1 `habits.test` — today (main screen)

The suite, mid-run. One line per test due today; the checkbox is the tap target. Comments (`#`) carry the live detail: pass time, counter progress, skip reason, quota state.

The extension is `.test`, not `.yaml` — deliberately. `- [x] meditate 10 min` is not valid YAML (a plain scalar cannot follow a flow sequence), and in a series whose first commandment is "the file must not lie", a file that declares a grammar and then violates it would be the one place the series breaks its own rule. `.test` is the runner's own format, the way `.diff` is a format rather than a language: YAML-flavored, `#` as its comment channel (§1.1), and no claim it cannot keep.

```
# habits.test
# suite 2026-08-20 — 3 passed · 2 pending · 1 skipped

- [x] meditate 10 min            # 07:12
- [x] read 20 pages 📖           # 23 pages
- [ ] pushups                    # 12/30    [+1]
- [~] run 5k                     # skip: rest day
- [·] no sugar                   # holds — asserts at commit
- [x] journal                    # 21:40

# 2 weekly tests not due today — [show]
```

- **Boolean test**: tap `[ ]` → `[x]` (with the pass time stamped in the comment); tap again to undo a mistap — today is the working tree, it is *supposed* to change.
- **Measurable test** (an assert): tapping the checkbox opens the in-place terminal prompt (`> pages: _`, `[esc]` to cancel); the box turns `[x]` when the assert holds. Counters with small steps get a `[+1]` text control at line end (water glasses, pushup sets) so the common case is one tap.
- **Avoid test** (`no sugar`): asserts *absence* — it wears `[·]` ("holding") with `# holds — asserts at commit` and passes at commit unless the user taps it to mark it `[!]` failed (optional note). The file never pre-checks something that has not finished being true — and `[·]` exists because `[ ]` would make "holding" and "pending" the same glyph, which matters most on the widget, where there is no comment channel to disambiguate.
- **Skip**: from the test's in-place expansion — `[~ skip]` with an optional note prompt and an optional `until:` date, so a week of travel is one interaction, not one skip per test per day. Skips are neutral everywhere: streaks hold, rates exclude them. (The full answer to the week away is the vacation branch, deferred — §6.10.)
- **Tapping the test name expands it in place** (the series' session-detail pattern): the full spec as indented YAML — `when:` (schedule), `assert:` (if measurable), `remind:`, current streak, `health: ▓▓▓▓▓▓▓▓░░ 82%` — plus the text-rendered actions `[~ skip]`, `[edit]`, `[rm]` (archive, two-tap).
- Tests scheduled but **not due today** (weekday-bound, quota already met) collapse into one comment line with `[show]`; quota tests still due show their week state (`# 2/3 this week`).
- When the logical date diverges from the wall-clock date (a `day_ends` after midnight), the header states it: `# logical day 2026-08-20 — ends 03:00`. At 01:00 the phone says August 21 while the suite is still the 20th's; undeclared, that honesty would look like a bug.
- Empty suite (first launch): the file is honest — `# no tests in the suite yet` — and the FAB explains itself with a hint comment. First launch is the one moment when the screen is *only* metaphor, with no checklist on it yet to carry the meaning, so the empty file also points at the tab that speaks plainly: `# the README tab says what a test is here`.
- **FAB** (glowing, rectangular): **`+`** — adds a test (§4.5). Each t-series app gets exactly one glowing verb: tweather refreshes, tsteps starts a walk, thabit grows the suite. The objection is on record — `+` is a setup verb, heavy in week one and rare after, while the siblings' glow verbs are daily — and so is the designed answer: the `$ thabit run` guided runner with the FAB morphing to `▶` (§6.9). It stays deferred because the file itself already is the runner.
- **Second editor tab: `README.md`** — the day as localized prose (markdown source), series pattern: `## Today` (what ran and what's left, in neutral words), `## Status` (the build badge, streaks worth mentioning), `## Week` (compact 7-day table, today bold and live). Fully localized, headings included. It is also **the app's plain-language layer** (§3.3.7), which is what a README has always been for: whatever term the other files are showing right now, this tab says it in a sentence — `~ build unstable` becomes "four of six passed", a flaky test becomes a habit you skip often, `[·]` becomes nothing to do here unless you break it. Two constraints keep it from rotting into a manual: the gloss lives inside the prose that is already there (no glossary section), and only terms **currently on screen** get one — a word the app is not showing needs no explanation.
- **Rows speak words, not glyphs.** A screen reader must never say "left bracket dot right bracket": every row's accessibility text is a localized sentence (*passed at 07:12* / *still to do* / *holding — it fails only if you break it* / *skipped, rest day* / *failed*). The glyph is the look, the words are the meaning — and this is the only place `[·]` can be explained to somebody who has never seen it anywhere else.
- Status bar: `⎇ main | 6 tests` left, `3/6 passed` live on the right. The git decorations (`⎇ main`, `HEAD → 9e31c7a`, `ro`) are signature, not information: they never carry a fact that is not already stated in plain form somewhere on the screen, which is why they can stay.

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
- **Build result, Jenkins semantics**: `✓ build passed` (everything due passed), `~ build unstable (4/6)` in orange (something passed, something failed), `✗ build failed` red (nothing passed). Skips leave the denominator. A day with nothing due runs no check. A day with tests due but **no run** (§3.3.8) produces no commit at all — Jenkins does not paint red the days nobody pushed; the week separator still renders, and a week with zero runs makes no quota claim.
- **Quota checks live on the week**: a `3/week` test never fails a single day — its verdict belongs to the ISO week separator: `--- week 34 · quota: run 5k 2/3 ✗ ---`. The week as a diff, now with its own CI line.
- **`--amend`**: yesterday's expanded run stays tappable — same interactions as today. On first edit the commit gains a permanent `# amended` marker. Older days are read-only, forever.
- The amend window is **stated, not discovered**: yesterday's commit carries `# still editable until 00:00` (localized clock). The best mechanism in the app is invisible otherwise — nobody taps a row hoping it might be editable — and note that `--amend` is never a control the user has to recognize: the control is the tappable row, the git name is only how this document and the `# amended` marker refer to it.
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

## coverage

24 of 30 days ran · 6 days no run          ▓▓▓▓▓▓▓▓░░ 80%
# a day with no run is not a failed build — it is a build that never started

## suite health

| test             | health | streak | 30d    |
| ---------------- | ------ | ------ | ------ |
| meditate 10 min  | 92%    | 18     | 22/24  |
| read 20 pages    | 87%    | 6      | 21/24  |
| journal          | 71%    | 0      | 17/24  |
| pushups          | 41%    | 0      | 9/24   |

## flaky tests

pushups — 37% pass rate over 30 days
# a flaky test wants a smaller assert or a different schedule

## regressions

journal — 41 days green, 3 of the last 5 red
# a regression is a habit that used to hold; it is reported once, without advice

## tags

| tag            | value               | date       |
| -------------- | ------------------- | ---------- |
| longest-streak | meditate · 43 days  | 2026-07-30 |
| perfect-week   | week 33 · 100%      | 2026-08-16 |
```

- Heatmap intensity = **fraction of the due suite that passed** that day, relative buckets like tsteps (quartiles of the user's own non-zero days); skipped tests leave the denominator; the future is blank and today is live; every day behind the reader with nothing to colour is a dim `·`. Row and month labels follow the reader's language, lowercase and three letters.
- **Health** is Loop-style: an exponential moving average over each test's due days — a pass strengthens, a fail decays, a skip is neutral. It is the primary signal *because* it forgives: a 90-day habit does not die of one bad Tuesday. Streak is still shown — some people run on chains — but health leads the table.
- **Flaky tests**: pass rate below threshold over 30 days, stated with its number and one fixed factual hint. It appears only when there is enough data, and never more than the facts.
- **Coverage** finally makes the section's title literal: due days that ran over due days in the window. It is the honest counterweight to `no run`: never opening the app stops the red, but the coverage bar says so in plain numbers. It is also why the `30d` column above reads `22/24` and not `22/30` — the *window* is thirty days, the *denominator* is the due days that ran inside it, and the six unknown days are stated once, in coverage, instead of being quietly counted as failures four times over.
- **Regressions** are distinct from flaky: flaky never was solid, a regression *was* and is breaking. The detection rule is fixed in Fase 2 next to the health half-life and documented in the export — recomputable, reported once, no advice attached.
- Every windowed rate starts at the test's `createdAt`: a habit added yesterday is `1/1`, not `1/30` — a fake 3% pass rate on day two is exactly the lie §1.1 forbids.
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

- **Notifications speak the reader's language** (series practice — tsteps localizes titles and bodies): `daily_commit` is the one place a verdict appears with no file around it and no arithmetic beside it, so it never ships the bare token. The glyph and the numbers stay, the sentence is prose: `~` + *four of six tests passed*, never a naked `build unstable`.
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

The transcript's labels are the app's **first sixty seconds**, so they are held to §3.3.7 more strictly than anywhere else: each step shows the plain form of what it is asking beside the token it is asking for (`pages >= [20]` is readable without knowing the word `assert`; `[boolean]` cycles next to `yes/no` in prose). The metaphor's own vocabulary lives in the test's expansion and in the file, where the user is no longer a newcomer.

Everything except `name:` has a default (`boolean`, `daily`, no reminder, no emoji): after the name, `[done]` adds the test as-is and `[more]` walks the remaining prompts. "Three habits in under a minute" (§9) is a promise the wizard has to keep — eighteen mandatory answers in sixty seconds was never going to be true.

`[edit]` on an existing test reopens the same transcript prefilled (`$ thabit edit "read 20 pages"`). Archiving is `[rm]` with two-tap confirm: the test leaves the suite as a `-` line in today's diff, **history preserved** — its past runs stay in every committed day and in stats' historical windows.

### 4.6 `thabit --status` — the widget

A terminal window on the home screen, inheriting the tweather/tsteps widget architecture whole (RemoteViews sizes-map, one line per tier step, measured breakpoints, configurable opacity, system monospace). Content — and this widget **acts**, not just shows:

```
$ thabit --status                🧪
suite:  ▓▓▓▓▓░░░░░ 3/6
[x] meditate          [x] read
[ ] pushups           [~] run 5k
[·] no sugar          [x] journal
# 2 pending — tap to pass
```

- **Boolean tests check off from the widget** — the single most valuable feature a habit widget can have (Loop proved it). Measurable tests open the app on their prompt.
- Avoid tests wear `[·]` here too — there is no comment channel on the widget, so the glyph alone must distinguish "pending" from "holding".
- A widget render is **not** presence: automatic repaints never write the `day` row (§7) — only a deliberate tap does. An untouched widget testifies to nothing.
- Tiers cut from the bottom; the smallest tier is the suite bar alone.
- No polling: repaints on check-off, at commit, and on settings changes. If the day rolled over and nothing repainted yet, the widget shows the date it is rendering — never yesterday's suite posing as today's.

---

## 5. Metrics

| metric | placement | notes |
| --- | --- | --- |
| today's run (per-test state) | main file, widget | the working tree; pending is pending, never pre-checked |
| build result ✓/~/✗ | log, notification | Jenkins semantics; skips leave the denominator; no tests due → no check |
| streak (per test) | expansion, stats | consecutive *due* passes; skips hold it, a `no run` day breaks it; computed on read, never persisted |
| health (per test) | expansion, stats | Loop-style EMA over due days; the forgiving, primary strength signal; `no run` days neutral |
| pass rate 7/30d | stats | per test and suite-wide; denominators exclude skips and `no run` days; windows clamp to `createdAt` |
| coverage | stats | due days that ran over due days in the window; the `no run` count stated beside it |
| heatmap | stats | 12 weeks, intensity = due-suite pass fraction, relative buckets; every past day at least a dim `·`, only the future blank |
| regressions | stats | a solid test now breaking; rule fixed with the health half-life, recomputable from export, reported once |
| quota state | main file, week separators | `2/3 this week`; verdict on the ISO week, never on a day |
| flaky tests | stats | pass rate under threshold, factual, only with enough data |
| records | stats, log | `tag:` refs — longest-streak, perfect-week; perfect-week requires every due day to have actually run |

Deliberately absent: points, levels, coins, avatars, "momentum scores" with secret formulas, mood tracking, motivational quotes, and any number the user cannot recompute from their own exported data.

---

## 6. Decisions, and what the field taught us

A survey of the habit-tracker landscape (Loop Habit Tracker, Streaks, Habitify, HabitNow, Habitica) was done before this document; what was adopted, adapted or rejected is recorded here so the reasoning survives.

1. **From Loop — adopted, it is the gold standard of honest trackers**: the strength EMA (our *health*, reframed as test health); flexible schedules (`3/week`, `every 2d`, weekday sets); measurable habits (reframed as *asserts* — the metaphor improves them); skips as first-class; check-off from the widget; full data export. Loop is open-source and account-free like us; thabit's answer to "why not just Loop?" is the same as tsteps' answer to pedometers: the file, the honesty rules, and the series.
2. **From Streaks — adopted**: negative habits (our *avoid* tests, reframed as asserting absence). **Rejected**: timed habits — thabit is not a stopwatch. *Parked with a note*: a `$ thabit watch "meditate"` live timer process would fit the series' running-process pattern (tsteps' `track`); it waits for real demand, not for symmetry. Named `watch` — watch mode — because `run` belongs to the guided runner (§6.9).
3. **From HabitNow/Habitify — adopted**: the custom day boundary (`day_ends` — the single most requested feature in the category's reviews). *Deferred*: time-of-day grouping (morning/evening sections in the suite file) — designed to render as YAML section comments, shipped only if the flat file proves too long in practice. **Rejected**: mood logging, notes-as-journal.
4. **From Habitica — rejected wholesale**: gamification is the anti-identity. No rewards, no punishments, no theatrics. The dopamine in thabit is a checkbox turning `[x]` and a green cell in the graph — the same dopamine git gives you.
5. **Amend over mutable history** (ours): every tracker allows backfilling forever, which makes every streak suspect. thabit's one-day `--amend` grace window is the honest middle: forgetting is human, rewriting last month is fiction.
6. **No per-habit colors** (ours): the palette is the palette. Identity comes from the optional emoji on the test line — icons are emoji in this series.
7. **Reminders are inexact by design** (ours): scheduled with `AlarmManager.setWindow` (~10 min), no exact-alarm permission, documented in the file (`# reminders are approximate — battery first`). A habit reminder is a nudge, not an alarm clock.
8. **Verdicts are computed on read, never persisted**: a day's build result derives from its stored checks whenever asked. There is no midnight mutation to get wrong, no streak counter to corrupt, and `--amend` recomputes everything for free. The midnight worker only notifies and repaints — it never writes data.
9. **`$ thabit run` — the guided runner** (ours, deferred): the FAB critique is on record — `+` is a setup verb, heavy in week one and rare after, while thabit's daily verb is *running the suite*. The designed answer: `$ thabit run` walks today's pending tests one at a time with fixed-position controls (`[pass] [+1] [fail] [~ skip] [next]`) and closes with the classic `Tests: 5 passed, 1 skipped, 0 failed, 6 total` summary; the FAB is `+` while the suite is empty and becomes `▶` once the first test exists — exactly what a CI shows before and after the first pipeline — and `+` retires to a `# + add test` line at the end of the file, next to the other text controls. Deferred, not shipped: the main file already *is* the runner (one glance, one tap — §3.3.2) and the widget already covers booleans, so a third surface for the same checks must be demanded by real usage, not by symmetry — the same rule that parked the timer in §6.2. **Evaluated in Fase 12 (Aug 2026) and left deferred**: the field round asked for nothing, and opening it anyway would be symmetry deciding where the rule says practice decides. The design above stands as written, for the day practice does ask.
10. **Suite profiles as branches** (ours, deferred): the week away is the category's most requested scenario after `day_ends`. `no run` (§3.3.8) already absorbs the fully-offline case — a closed app writes nothing and invents nothing — so what remains is the *partial* vacation: still meditating, not lifting. The in-metaphor answer is `git checkout vacation` — a reduced suite on its own branch, the status bar `⎇` becoming the switcher exactly as tweather made `⎇ <city>` tappable. MVP ships the cheap half: `[~ skip]` takes an optional `until:` date. The branch lands only when the suite model has proved stable on device. **Reviewed in Fase 12 (Aug 2026) and left deferred**: one clean field round is the absence of a contradiction, not a demonstration of stability, and `[~ skip] until:` already covers the partial vacation at a cost a branch does not come near.
11. **No sensor may pass a test for you** (ours — a refusal, recorded so it is not re-proposed): tsteps writes steps to Health Connect, so `it("walks 8000 steps")` could go green untouched — and thabit still says no. The point of this app is that every `[x]` was typed by a person; a suite that passes itself is a dashboard, and tsteps is already the app for data a sensor owns.

---

## 7. Data & scheduling strategy

- **Storage**: Room — `habit` (name, type, assert target/unit/step, schedule, reminder, emoji, position, `createdAt`, `archivedAt`), `check` (habit id, local date, state pass/fail/skip, value, note, stamped time, optional skip `until` date) and `day` (presence — see below). One row per test per day, written by taps; everything else derives. Pruned never — a decade of checks is trivially small.
- **The commit is a boundary, not a write**: at `day_ends` the day becomes read-only (minus the amend window) by *definition*; nothing is copied, frozen or summarized. `DaySummary`-style snapshots are unnecessary because checks are already immutable facts.
- **Presence, not verdicts**: a `day` row (logical date, `first_seen`) is written by the first *deliberate* interaction of each logical day — opening the app, tapping the widget, tapping a notification action. Automatic events never write it: a widget repaint or the rollover worker stamping presence would invent a user who was not there. It is evidence, not a verdict — §6.8 holds, the boundary still writes nothing — and its absence is what makes `no run` sayable at all.
- **Rollover worker** (one shared periodic job, series rule): fires at the `day_ends` boundary to post the `daily_commit` notification and repaint the widget; a safety-net pass on app open covers phones asleep at midnight. DST, timezone changes and `day_ends` edits are test cases.
- **Reminders**: per-test inexact alarms (`setWindow`), rescheduled on boot and on schedule edits; notification carries a **pass action** for boolean tests — the habit checks off from the shade. `pending_digest` (opt-in) is a single evening summary, never one nag per test. A single re-armed "next reminder" alarm was considered and rejected: inexact alarms are batched by the OS, so fifteen registrations cost what one does, and a chain that must re-arm itself after every fire stalls entirely on one missed fire — independently registered alarms degrade one test at a time.
- **No network. No INTERNET permission.** The CI badge for this in the README stays the series' proudest line.
- **Export** (`$ thabit export --json|--csv` in settings): the full suite, every check **and every `day` presence row**, canonical values, `Locale.ROOT`, MediaStore into Downloads with the tsteps pending→publish pattern. Presence ships because coverage and `no run` must be recomputable too — a stat the user cannot verify is a secret formula. The user's history belongs to the user.
- **Health formula** (fixed in Fase 2): an exponential moving average over a test's **graded units** — its due days, or its ISO weeks for a quota test — with a **half-life of 14 graded units** (`alpha = 1 - 2^(-1/14)` ≈ 0.0483). The average is **seeded with the first graded outcome** rather than with zero, so a test passed three times out of three reads 100% instead of a false 13%; skips and `no run` days are simply not in the sequence, so they move nothing; a test with nothing graded is *unknown*, not 0%. Counting in graded units rather than in calendar days means a `mon,wed,fri` test decays per run exactly like a daily one — sparse scheduling is not a penalty. Half-life, seeding and exclusions are repeated verbatim in the export header: no secret numbers, the user must be able to recompute every stat from their own file.
- **Regression rule** (fixed in Fase 2, beside the half-life for the same reason): at least **3 of the last 5** graded units failed, after a green run of at least **14** units — the same horizon as the health half-life, which is where the app is willing to call a habit established. **Flaky rule**: a 30-day pass rate below **60%** over at least **8** graded units, excluding whatever already qualifies as a regression, since a test cannot both have been solid and never have been.

---

## 8. MVP

1. Suite CRUD via `$ thabit add`/`edit`/`[rm]` — boolean, counter (assert), avoid; daily/weekday/quota/interval schedules
2. `habits.test` — live run, one-tap pass, `[+1]`, prompts, skips (with `until:`), in-place expansion
3. `habits_history.diff` — day commits with build results, week separators with quota checks, `--amend` on yesterday; no-run days produce no commit
4. `stats.md` — heatmap, coverage, suite health table, streaks, flaky tests, regressions, tags
5. `settings.config` — day_ends, week start, editor, theme profiles, notifications, reset
6. `README.md` day tab
7. Notifications: per-test reminders with pass action, `daily_commit`, opt-in `pending_digest`
8. Widget `thabit --status` with widget check-off
9. IT/EN localization, per-app language
10. Export JSON/CSV

Deferred: the `$ thabit run` guided runner with the FAB `▶` morph (§6.9), suite profiles as branches (§6.10), the `$ thabit watch` timer process (§6.2), time-of-day groups, Wear OS (probably never). Rejected: gamification, cloud/accounts, mood, per-habit colors, sensors that pass tests (§6.11).

## 9. Success criteria

The first release succeeds if a user can install it, add three habits in under a minute through the wizard, and then: check today off in one glance and three taps (two of them on the widget); trust that a `[x]` was typed by them and nobody else; find any past day and see exactly what ran; watch a bad week dent their health without erasing their history; come back from a week away and find blank days rather than seven failures the app made up; and never once meet a guilt mechanic they did not opt into. It fails, equally, if a user who has never used git cannot run that whole loop — install, add a habit, tell whether today went well, fix yesterday — without ever needing to learn what a commit is. The deeper test, inherited twice over: **does adding a feature make the app better, or just bigger?**

> **North star:** a suite you wrote yourself, a build every day, a history that never lies — and the quiet satisfaction of a green checkmark you earned.
