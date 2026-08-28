# thabit

> A habit tracker that thinks it is a test suite.

**thabit** is a minimal Android habit tracker whose entire UI mimics a code editor and a CI system. Your habits are the tests in `habits.test`. Every day is a build: it passes, it fails, or it lands somewhere in between (`~ unstable`, like Jenkins says). The history is a git log with one commit per day. Consistency is a contribution graph. Committing is the whole point.

Third app of the **t-series**, after [tweather](https://github.com/fiorenzobrioni/tweather) and [tsteps](https://github.com/fiorenzobrioni/tsteps): real, single-purpose apps that render their data as syntax-highlighted fake files, with terminal prompts instead of forms and text instead of buttons.

```
# habits.test
# suite 2026-08-20: 3 passed / 2 pending / 1 skipped

- [x] meditate 10 min            # 07:12
- [x] read 20 pages              # 23 pages
- [ ] pushups                    # 12/30    [+1]
- [~] run 5k                     # skip: rest day
- [·] no sugar                   # holds, asserts at commit
- [x] journal                    # 21:40
```

The extension is `.test` and not `.yaml` on purpose: `- [x] meditate 10 min` is not valid YAML, and a file that declares a grammar it does not keep would be the app breaking its own first rule.

## Status

The app is complete and runs on a phone. `$ thabit add` builds your suite as a terminal conversation (one answer is enough, the rest have defaults), `habits.test` runs it a tap at a time, `habits_history.diff` is the git log of your days, `stats.md` draws the heatmap and the suite health, the `README.md` tab says the same things in plain prose, and `settings.config` is a JSON file whose values are the controls, theme included. A fresh install opens on `$ thabit init`, one screen and two answers, and `HELP.md` (the second file behind the Settings tab) explains the app to somebody who does not read `git` for a living. Notifications, the home widget and the export have landed: a reminder per habit answerable from the shade, the day's build result at the boundary, an opt-in evening summary, `thabit --status` on the home screen with a habit ticked off straight from a row, and `$ thabit export` handing the whole history back as JSON or CSV. The domain underneath computes every verdict on read: schedules, the configurable day boundary, streaks, health, coverage, regressions, records. Every row speaks its state out loud to a screen reader, in English or Italian, terminal output and the metrics included. 670+ JVM tests. The spec is in `VISION.md` and the phased plan, with every decision and why it was taken, is in `PLANNING.md`. What is left before the v1.0.0 tag is a pass of edge cases on a real phone.

## What it does

- **One file, one glance**: every habit due today on one line, checked off with one tap.
- **Honest mechanics**: measurable habits are assertions (`pages >= 20`), skips are neutral and never break a streak, and a health score (an exponential moving average) forgives the bad day a raw chain would not.
- **A history that never lies**: days commit at midnight and become read-only. Yesterday only can be amended (you forgot to tick, it happens) and gets marked `# amended`. Two days back is history, forever.
- **CI verdicts**: `build passed`, `build unstable (4/6)`, `build failed`. Facts, not guilt. Weekly quotas (run 3 times a week) pass or fail the week, never a single day.
- **stats.md**: a GitHub style contribution heatmap, per-habit health and streaks, flaky tests called out with their numbers, and regressions (a habit that used to hold and has started breaking). A day you never opened the app is a faint dot, not a blank and not a red square: the grid says it has no record, and the coverage line below says how many such days there were.
- **The app is allowed to not know**: close it for a week and those days come back blank, not as seven red builds it invented. Days the app never saw count nowhere, and `stats.md` reports coverage (how many days actually ran) as its own honest number.
- **Nothing is precomputed**: streaks, health, build results and records are all derived from your checks at the moment you ask. Nothing is frozen at midnight, so fixing yesterday recomputes everything for free.
- **Reminders that stay nudges**: a habit can carry its own reminder, and a yes-or-no habit is ticked off straight from the notification. They are approximate on purpose (the app asks Android for a window, never for an exact alarm), because battery is a feature and a habit reminder is not an alarm clock. The evening summary is opt-in, there is never one nag per habit, and nothing motivational is ever sent.
- **A widget that acts**: `thabit --status` puts today's suite on the home screen, and a yes-or-no habit is ticked off by tapping its row: no app, no unlock past the home screen. It states the date it is showing, so a widget that has not repainted since midnight says so instead of passing yesterday off as today. Habits that need a number, or a decision, open the app on their own row rather than guessing.
- **You do not have to be a developer**: the look is a checklist in a good outfit, and no CI term is ever the only place a fact lives. Every verdict carries the numbers that explain it (`4/6`), and whatever word is on screen is also said in plain language, in your language, in the README tab. The first run asks one question (what is the first habit?) instead of showing you slides, and `HELP.md` is a file you can reopen the day the question actually turns up, not a screen that goes past once. English and Italian, split the way a real terminal splits them: code stays code (the keys, the file names, the `$` commands, the check lines) and anything written to be understood speaks your language, the comment lines included. `git status` on an Italian phone says "Sul branch main" and keeps the word `branch`: so does the history here.
- **No network. No account. No INTERNET permission at all.** Habit data is intimate data: it stays on the device, and `$ thabit export` gives it to you as JSON or CSV whenever you want it (see below).

## Export

`$ thabit export --json` and `$ thabit export --csv`, at the foot of `settings.config`. Files land in your Downloads folder through MediaStore, so **no storage permission is asked for** and the files stay there after you uninstall the app: they are yours, not thabit's. The terminal line reports the name the system actually wrote, which is not always the name that was asked for (a second export the same day gets a `(1)` suffix).

Three tables go out, and the point of all three is that every number the app shows can be worked out again from them:

- **the suite**: every test, archived ones included, with the day it was created and the day it left. `[rm]` takes a test off today's file, never out of the history it earned.
- **the checks**: one row per test per day, exactly as it was written. A skip that covered a week away is one row with an `until` date, not seven invented ones.
- **the days**: when you actually opened the app. This is the unglamorous table that matters: coverage and the `no run` days are counted from it, and without it you could not check the two numbers that say what the app does not know.

JSON is one document and carries the rules in its header: the health half-life and how the average is seeded, what counts as a regression, what counts as flaky, how a weekly quota is graded, how to expand a skip window, and what a `no run` day does to a denominator. They are the same constants the app computes with, so the archive cannot drift from the screens. CSV is three files with clean headers instead: a spreadsheet has nowhere to put a comment, so the sentences live here.

Everything is `Locale.ROOT` and canonical: ISO dates, 24-hour clocks, a full stop for decimals. A file whose meaning changes with the phone's language is not an archive.

## Install

The v1.0.0 tag has not been cut yet. When it is, a signed APK is published as a GitHub Release together with its R8 mapping file, built by CI from the tag. Until then you can build one yourself: `./gradlew :app:assembleDebug` puts a debug APK in `app/build/outputs/apk/debug/`.

Debug builds install side by side with the release app: they carry their own application id and the launcher label `thabit (dev)`, so testing a build never touches the habits you actually keep.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

[GPL-3.0](LICENSE)
