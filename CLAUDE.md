# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

**thabit** is an Android habit tracker (Kotlin 2.2 / Jetpack Compose Material 3) whose entire UI mimics a code editor and a CI system: your habits are a test suite (`habits.test` — YAML-flavored, deliberately not claiming to *be* YAML), every day is a build that passes or fails (`✓`/`~ unstable`/`✗`, Jenkins semantics), the history is a git log (`habits_history.diff`, one commit per day, `--amend` grace on yesterday only), stats are a `stats.md` with a contribution heatmap and a suite-health table, and settings are a `settings.config`. Third app of the **t-series** after [tweather](https://github.com/fiorenzobrioni/tweather) and [tsteps](https://github.com/fiorenzobrioni/tsteps) — same theme, same components, same philosophy.

Source of truth:

- `VISION.md` — product spec: the series' soul (§1), the CI/test-suite metaphor mapping (§2), every screen/file (§4), metrics (§5), decisions taken against the habit-tracker field — Loop, Streaks, Habitify, HabitNow, Habitica (§6), data strategy (§7), MVP (§8).
- `PLANNING.md` — the phased implementation plan with checkable steps. **Keep it updated as work progresses**, recording every decision and deviation with its reason (the series' rule).
- The sibling repos `../tweather` and `../tsteps` — reference implementations for the editor kit (`ui/components/`), the widget architecture, the diff screens and all decisions already validated on device. tsteps carries the most recent ports, so it is the preferred copy source. Components are **copied and adapted** (package `com.callbackdev.thabit`), never linked.

## Build and commands

Stack: Kotlin 2.2 + Compose (Material 3), Gradle 9.1 / AGP 8.13, version catalog in `gradle/libs.versions.toml`. Package/applicationId: `com.callbackdev.thabit`. minSdk 33, compile/targetSdk 36. **No Retrofit/OkHttp and no INTERNET permission: thabit has no network, by identity.** If a feature needs the network, it is not a thabit feature.

- Build debug APK: `./gradlew :app:assembleDebug` (output: `app/build/outputs/apk/debug/app-debug.apk`)
- Unit tests: `./gradlew :app:testDebugUnitTest` — single class: `--tests "com.callbackdev.thabit.SomeTest"`
- Lint: `./gradlew :app:lintDebug`
- Installable minified build: `./gradlew :app:assembleRelease -PsignReleaseWithDebugKey`
- On this machine there is no system JDK: prepend `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` to gradlew commands.

**Debug signing**: `keystore/debug.keystore` is intentionally committed (alias `thabit-debug`, store/key password `android`) so debug APKs from CI and any machine share one signature. Do not regenerate it. Debug builds carry `applicationIdSuffix ".debug"` and the launcher label `thabit (dev)` (debug res overlay), so they install **side-by-side** with the release-signed app — series decision (Aug 2026), same as snake. The debug-signed *release* build (`-PsignReleaseWithDebugKey`) keeps the plain id: it exists for pre-release smoke tests, not for daily side-by-side testing.

**CI**: `.github/workflows/android-ci.yml` runs on every push — unit tests, lint, then both APKs (tests *before* builds: a red suite never produces an installable artifact). Artifacts: `thabit-debug-apk`, `thabit-release-apk-testing-only`, `thabit-release-mapping`.

**Release signing**: the real keystore lives OUTSIDE the repo (`C:\Fiorenzo\keys\thabit-release.jks`, generated and provisioned before Fase 1 — GitHub Secrets `KEYSTORE_BASE64`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` already on the repo, `THABIT_*` properties filled in the machine's `~/.gradle/gradle.properties`); the `release` signingConfig is created only when the four `THABIT_KEYSTORE*` properties are all set — from `~/.gradle/gradle.properties` locally, from `ORG_GRADLE_PROJECT_*` env vars (GitHub Secrets) in CI — and wins whenever configured. On an unconfigured checkout the release build is unsigned by default; `-PsignReleaseWithDebugKey` signs it with the committed debug key so the minified build is installable for testing (R8 breakage shows up nowhere else) — opt-in so an unconfigured checkout can never produce an installable release by accident. `.github/workflows/release.yml` fires on `v*` tags.

## Design constraints (non-negotiable, inherited from the series)

- **Typography**: JetBrains Mono everywhere, 4px baseline grid, 20px indent per nesting level. The home widget is the single exception (system `monospace` — CVE-2021-0567, launchers silently drop `@font/` in widget layouts).
- **Syntax colors** (Obsidian): keys `#79c0ff`, strings `#a5d6ff`, numbers/booleans `#ffa657`, comments/braces `#8b949e`, diff add `#2ea043`, del `#f85149`. Palette: background `#10141a`, surface `#181c22`, on-surface `#dfe2eb`, borders `#30363d`. Profiles: Obsidian/Dracula/Monokai, dark-only.
- **No drop shadows** — 1px borders + tonal stacking; the FAB's glow is the only exception, and the FAB is rectangular (4px radius, like everything). thabit's FAB is **`+`** (add a test) — one glowing verb per app; the `▶` guided-runner morph is designed and deliberately deferred (VISION §6.9), not an oversight.
- **Controls rendered as text**: checkboxes as `[x]`/`[ ]`/`[·]` (avoid test holding)/`[~]`/`[!]`, removal as `[rm]`, quick increments as `[+1]`, destructive actions as `$` commands with two-tap confirm, inputs as terminal prompts with blinking `_`. No native Material controls.
- **The file must not lie**: pending is pending (never pre-checked), skips are neutral and stated, amended commits carry `# amended`, missing data is missing, and every stat is recomputable from the user's own export (no secret formulas). A day the app never saw is `no run`, never a failure — inventing a red build for a day nobody was there is the same class of lie as a fake zero.
- **Comment channel wears the host file's syntax**: `#` inside YAML, `//` inside the JSON-style settings (thabit's own rule, VISION §1.1).
- **Localization rule**: "code" stays English (YAML/JSON keys, filenames, comments, terminal output, commit/check lines); chrome and data values localize IT/EN. Habit names are user data. The `README.md` day tab is prose: fully localized.
- **The metaphor is a gain, never a toll** (VISION §3.3.7): the editor look asks nothing of the reader, but CI vocabulary can exclude, so **no CI term is ever the only place a fact exists**. Verdicts always ship with their arithmetic (`~ build unstable (4/6)`), and every term currently on screen has a plain-language equivalent one tap away in the `README.md` tab — the app's plain-language layer, together with notifications and accessibility text (the only three localized surfaces; comments stay English). Rows speak words, not glyphs, to a screen reader. No "beginner mode" toggle: that is two apps in one.

## Writing `README.md` (root file only)

**No em dashes (`—`) or en dashes (`–`) in the root `README.md`.** Rewrite the sentence rather than swapping in a hyphen: use a colon when the clause explains, a full stop when the thoughts are separate, parentheses for an aside. Same house style as the siblings, deliberately scoped to that one file — every other file keeps normal punctuation.

## Domain notes

- **Verdicts are computed on read, never persisted**: streaks, health (EMA), build results and records all derive from the `check` rows whenever asked. The midnight rollover worker only notifies and repaints — it never writes data. `--amend` recomputes everything for free.
- **Schedules**: `daily`, weekday sets, quota `n/week` (verdict on the ISO week separator, never on a day), interval `every Nd`. `day_ends` moves the logical day boundary (default 00:00); DST/timezone/day_ends changes are test cases.
- **Skips leave every denominator**; streaks hold across skips; avoid-type tests assert absence, wear `[·]` and pass at commit unless explicitly failed.
- **`no run` days** (no deliberate interaction that logical day, recorded by a `day` presence row — never by a widget repaint or the worker): no commit, blank heatmap cell, out of every denominator, health neutral, but **streaks break** (a streak is a chain of typed passes). Coverage in `stats.md` states how many due days actually ran.
- **History is immutable beyond yesterday**: today and the previous logical day are writable, and that window shrinks to zero as today runs out (it closes at `day_ends`); older days are read-only forever. Coming back after days away, only the last closed day is amendable.
- **Reminders are inexact by design** (`AlarmManager.setWindow`, no exact-alarm permission): a reminder is a nudge, not an alarm clock. Battery is a feature: no polling, no services, no sensors. They are **per-test** alarms, not one chained "next reminder" alarm: inexact alarms are OS-batched so the extra registrations cost nothing, and a self-rearming chain loses every reminder on one missed fire. The series' one-shared-job rule is about periodic polling jobs, not event-driven alarms.
