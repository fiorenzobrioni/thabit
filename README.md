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

Under construction. The spec is complete (see `VISION.md`) and the plan is phased and public (see `PLANNING.md`). The editor kit is ported and the domain engine is in: schedules (daily, weekday sets, weekly quotas, intervals), the configurable day boundary, build verdicts, streaks, health, coverage, regressions and records, all computed on read and covered by 200+ JVM tests. The screens land phase by phase.

## What it will do

- **One file, one glance**: every habit due today on one line, checked off with one tap.
- **Honest mechanics**: measurable habits are assertions (`pages >= 20`), skips are neutral and never break a streak, and a health score (an exponential moving average) forgives the bad day a raw chain would not.
- **A history that never lies**: days commit at midnight and become read-only. Yesterday only can be amended (you forgot to tick, it happens) and gets marked `# amended`. Two days back is history, forever.
- **CI verdicts**: `build passed`, `build unstable (4/6)`, `build failed`. Facts, not guilt. Weekly quotas (run 3 times a week) pass or fail the week, never a single day.
- **stats.md**: a GitHub style contribution heatmap, per-habit health and streaks, flaky tests called out with their numbers, and regressions (a habit that used to hold and has started breaking).
- **The app is allowed to not know**: close it for a week and those days come back blank, not as seven red builds it invented. Days the app never saw count nowhere, and `stats.md` reports coverage (how many days actually ran) as its own honest number.
- **Nothing is precomputed**: streaks, health, build results and records are all derived from your checks at the moment you ask. Nothing is frozen at midnight, so fixing yesterday recomputes everything for free.
- **A widget that acts**: check habits off from the home screen without opening the app.
- **You do not have to be a developer**: the look is a checklist in a good outfit, and no CI term is ever the only place a fact lives. Every verdict carries the numbers that explain it (`4/6`), and whatever word is on screen is also said in plain language, in your language, in the README tab.
- **No network. No account. No INTERNET permission at all.** Habit data is intimate data: it stays on the device, and `$ thabit export` gives it to you as JSON or CSV whenever you want it.

## Install

Not yet released. Once v1.0.0 is out, signed APKs will be published as GitHub Releases.

## License

[GPL-3.0](LICENSE)
