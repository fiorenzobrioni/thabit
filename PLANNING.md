# PLANNING.md — Piano di realizzazione thabit

Piano di sviluppo per **thabit**, habit tracker Android (Kotlin + Jetpack Compose) con UI in stile code editor + CI/test suite (tema "Obsidian Syntax", ereditato dalla serie). Ogni passo è smarcabile: `[ ]` da fare → `[x]` completato.

Riferimenti: `VISION.md` (identità, metafora CI, schermate, metriche, decisioni prese studiando il campo — Loop, Streaks, Habitify, HabitNow, Habitica), i repo gemelli [tweather](https://github.com/fiorenzobrioni/tweather) e [tsteps](https://github.com/fiorenzobrioni/tsteps) come sorgente dei componenti UI e delle decisioni già validate su device (design system in `obsidian_syntax/DESIGN.md` di tweather; il porting più recente dei componenti è quello di tsteps, che è quindi la sorgente preferita per la copia).

Regola del piano (di serie): ogni decisione e ogni deviazione dalla vision va registrata qui **con la motivazione**, nella fase in cui è stata presa.

---

## Fase 0 — Setup progetto

- [x] Repository git con remote https://github.com/fiorenzobrioni/thabit.git, licenza GPL-3.0, `.gitignore` Android/Kotlin (copiato dalla serie)
- [x] Progetto Android (scheletro Compose), package/applicationId `com.callbackdev.thabit`
- [x] Gradle (Kotlin DSL) identico ai gemelli: Gradle 9.1, AGP 8.13, Kotlin 2.2.20, Compose BOM 2025.08, Material 3, minSdk 33, target/compileSdk 36, version catalog `gradle/libs.versions.toml`
- [x] Dipendenze base = quelle di tsteps **meno Health Connect e meno tutto ciò che è rete** (thabit non ha né sensori esterni né INTERNET — punto di identità, VISION §3.1). Room/WorkManager/DataStore/Navigation già nel catalogo, attivate dalle fasi che le usano
- [x] Font **JetBrains Mono** (400/500/600/700) copiati in `res/font`
- [x] Tema portato da tsteps in `ui/theme/` (Color, Theme, Type, Shape, Depth, SyntaxColors): 3 profili Obsidian/Dracula/Monokai, dark-only, rename `TstepsTheme` → `ThabitTheme`
- [x] Keystore debug condiviso committato `keystore/debug.keystore` (alias `thabit-debug`, password `android`) — stessa filosofia di serie: APK debug di CI e macchine diverse si aggiornano senza reinstallare
- [x] CI GitHub Actions `.github/workflows/android-ci.yml` (copiata da tsteps): test e lint **prima** delle build, artifact APK debug + release minificata debug-signed su flag + mapping R8; `release.yml` su tag `v*` pronto per la Fase 13
- [x] Icona launcher: parentesi graffe e tick nello stesso trattamento di serie (fill `#2B4D73` / stroke `#79C0FF`), soggetto centrale = **checkbox `[x]`** (il gesto dell'app); icona status bar monocromatica `ic_stat_thabit` pronta per la fase notifiche
- [x] `MainActivity` minima: splash screen (brand mark), edge-to-edge con barre forzate scure, placeholder `SkeletonScreen` che disegna un `habits_test.yaml` statico con gutter e colori sintassi — il primo build ha già la faccia di thabit
- [x] Primo unit test (`ThemeProfileTest`) per esercitare la toolchain JVM; `robolectric.properties` (sdk 35, graphics NATIVE) copiato per i test Compose futuri
- [x] Verifica: `testDebugUnitTest` + `assembleDebug` + `lintDebug` + `assembleRelease -PsignReleaseWithDebugKey` verdi in locale
- [x] Primo commit e push (`796e8a4`); CI verde sul remote al **secondo** run (`fd2314d`): il primo è morto su `./gradlew: Permission denied` — la copia dei file da tsteps via `cp` su Windows non porta il bit eseguibile dentro git; fixato con `git update-index --chmod=+x gradlew`. Gotcha registrato per le prossime app della serie

## Fase 1 — Editor kit (import da tsteps)

I componenti riusabili sono già a tema e già testati: si importano, non si reinventano. Adattare package e ripulire dai riferimenti di dominio.

- [x] `CodeBlockContainer`, `CodeCanvas` (gutter, indent 20px, `EditorOptions`), `EditorTab` + `EditorNavBar`, `TerminalStatusBar`, `TerminalInput`, `GlowFab` (glifo di default **`+`** con contentDescription "Add" — l'unico verbo col glow di thabit è aggiungere un test alla suite); preview ripulite dai riferimenti al dominio passi
- [x] `JsonSyntax` + `MarkdownSyntax` importati (settings.config e stats.md/README li usano)
- [x] **`YamlSyntax` nuovo**: `CheckboxState` (`[x]` diff-verde / `[ ]` **neutro: nessuno span, eredita l'on-surface del canvas** / `[~]` grigio commento / `[!]` diff-rosso), `checkboxToken` nudo per le righe widget future, `yamlTestLine` (`- ` grigio, nome del test senza span — è dato utente, emoji incluse — e commento `#` in coda dimmed come gli hint dei gemelli), `yamlStringLine`/`yamlNumberLine` per le spec espanse. Il canale commenti veste la sintassi del file ospite (VISION §1.1). 10 test dedicati (`YamlSyntaxTest`) sul modello di `JsonSyntaxTest`
- [x] `MarkdownTable` importato ora (puro, serve le Fasi 7-8); **`UnitFormat` NON importato — deviazione registrata**: è dominio passi (km/mi, velocità, pace) quasi per intero; thabit scriverà la propria formattazione numeri/percentuali/orari nella fase che ne avrà bisogno — non si copia ciò che non serve
- [x] Test Compose importati e adattati (37 totali, tutti verdi); nav bar con route pulite `editor`/`log`/`stats`/`settings`. **Glifo dell'editor: `checklist`** — il file principale è una lista di checkbox, il `{}` dei gemelli prometterebbe JSON; Log/Stats/Settings mantengono `commit`/`insights`/`code` di serie. Label IT: "Stats" resta inglese (decisione tsteps ereditata: "Statistiche" non sta nella label-sm)
- [x] Shell `ThabitApp` provvisoria: bottom bar + placeholder onesti per tab (`# <file> — not yet written`, `//` per settings.config — regola del file ospite), senza Navigation Compose (il NavHost per-tab è della Fase 4). **Deviazione migliorativa registrata**: la tab editor non è un placeholder ma la suite statica di esempio renderizzata con CodeCanvas+YamlSyntax — esercita il tokenizer nuovo su device da subito; lo `SkeletonScreen` disegnato a mano della Fase 0 è superato e rimosso

## Fase 2 — Dominio: suite, run, verdetti

Il cuore. Nessuna UI: tutto puro e testabile su JVM.

- [ ] Modello `Habit` (nome, tipo `boolean|counter|avoid`, assert `target/unit/step`, schedule, reminder, emoji, posizione, `createdAt`/`archivedAt`) e `Check` (habitId, data locale, stato `pass|fail|skip`, valore, nota, orario). Room `ThabitDatabase` v1: tabelle `habit` e `check`, una riga per test per giorno, scritta dai tap — **i verdetti non si persistono mai** (VISION §6.8: calcolo in lettura, niente stato da corrompere, `--amend` gratis)
- [ ] **`Schedule` puro**: `daily`, giorni della settimana (`mon,wed,fri`), quota `n/week` (ISO), intervallo `every Nd` (ancorato a `createdAt`); `isDue(date)` e `dueCount(window)` con test su bordi settimana, anni bisestili, cambio schedule a metà settimana (la quota si valuta sullo schedule vigente a fine settimana — registrare)
- [ ] **`DayBoundary`**: il giorno logico con `day_ends` configurabile (default 00:00) — mapping istante→data logica, test su DST 23h/25h (Europe/Rome), cambio timezone, cambio di `day_ends` a giornata in corso (i check già scritti restano sulla loro data logica: il passato non si rietichetta)
- [ ] **`Verdicts` puro**: stato del run di un giorno (per-test: pass/fail/skip/pending; un pending di un giorno chiuso legge fail per definizione) e build result con semantica Jenkins — `PASSED` (tutti i dovuti passati), `UNSTABLE` (misto), `FAILED` (nessun passato), `NO_RUN` (niente in scadenza); skip fuori dal denominatore
- [ ] **`Streaks` puro per test**: passaggi consecutivi sulle sole occorrenze dovute, skip neutri, calcolato in lettura
- [ ] **`Health` puro** (EMA alla Loop): pass rafforza, fail decade, skip neutro; half-life fissata qui e **documentata in VISION §7 e nell'export** (niente numeri segreti: l'utente deve poter ricalcolare tutto dal proprio export)
- [ ] `Records` puri: `longest-streak` (test + giorni), `perfect-week` (ISO week al 100%); `FlakyTests` (pass rate 30d sotto soglia, con soglia-minima-dati)
- [ ] `CommitHash` (SHA-1 di "thabit:<data>" troncato a 7 hex, pattern tsteps), `SettingsStore` minimale (day_ends, week_start, tema, editor), `ServiceLocator` a mano con `overrideForTests`
- [ ] **Rollover worker**: allo scoccare di `day_ends` notifica `daily_commit` e ridisegna il widget — **non scrive mai dati** (il commit è una definizione, non una scrittura); safety-net all'apertura app; test DST/timezone/edit di day_ends
- [ ] Test completi su JVM per tutto il sopra

## Fase 3 — Schermata principale (`habits_test.yaml`)

- [ ] Rendering live con i componenti Fase 1: header `# suite <data> — N passed · M pending · K skipped`, una riga per test dovuto oggi (checkbox + nome + emoji + commento `#` col dettaglio vivo: orario del pass, progresso counter, motivo skip, stato quota)
- [ ] **Tap sul checkbox**: boolean `[ ]`→`[x]` con orario stampato nel commento, tap di nuovo = undo (oggi è working tree); counter → prompt in place (`> pages: _`, `[esc]`), box `[x]` quando l'assert regge; `[+1]` a fine riga per i counter con step piccoli; avoid = `[ ]` con `# holds — asserts at commit`, tap → `[!]` failed con nota opzionale
- [ ] **Espansione in place** al tap sul nome (pattern sessioni tsteps): spec YAML indentata (`when:`, `assert:`, `remind:`), streak corrente, `health: ▓▓░░ NN%`, azioni testo `[~ skip]` (con prompt nota opzionale), `[edit]`, `[rm]` (archivio, two-tap)
- [ ] Test non dovuti oggi collassati in un commento con `[show]`; quota ancora aperte visibili con `# 2/3 this week`
- [ ] Empty state onesto (`# no tests in the suite yet`) con hint verso il FAB; FAB `+` col glow che apre il wizard (Fase 5 — fino ad allora, commento transiente "coming soon" come fece tsteps col suo FAB)
- [ ] Status bar: `⎇ main | N tests` a sinistra, `X/N passed` vivo a destra
- [ ] Test: rendering riga per riga, transizioni di stato, undo, counter con assert, avoid, prompt, espansione, empty state

## Fase 4 — Navigazione e Impostazioni (`settings.config`)

- [ ] Navigation Compose con le 4 route (pattern di serie: stack per-tab con `saveState`/`restoreState`)
- [ ] `settings.config` formato serie (JSON con commenti `//`): sezioni `suite` (`day_ends` a cicli o prompt orario, `week_starts`), `editor` (line_numbers/word_wrap → `LocalEditorOptions`), `theme` (profili con `// active`), `notifications` (placeholder onesto fino alla Fase 9), `about`
- [ ] `// Last modified:` alla prima modifica; `$ git restore settings.config` two-tap — **non tocca la suite né i check**: il reset della config non deve mai perdere un dato dell'utente
- [ ] Tema runtime; test su flip, cicli, prompt, reset, day_ends che rietichetta solo il futuro

## Fase 5 — Il wizard (`$ thabit add` / `edit` / `[rm]`)

La suite si costruisce qui: è la fase che rende l'app usabile davvero.

- [ ] **Wizard come sessione terminale** (VISION §4.5): transcript che chiede una cosa alla volta — `> name:` (prompt), `> type:` (token che cicla `[boolean] counter avoid`), `> assert:` (solo counter: target+unit), `> when:` (cicla i 4 schemi + prompt per il custom), `> remind:` (off/orario), `> emoji:` (opzionale, `[skip]`) — chiusura `✓ test added to the suite`. Riusa `TerminalInput` e i token ciclanti di settings: macchinario esistente, composto in conversazione
- [ ] `[edit]` riapre il transcript prefillato; cambi di schedule/assert valgono da oggi (la storia resta con le regole del suo tempo — registrare)
- [ ] `[rm]` two-tap = **archivio, mai delete**: `archivedAt` valorizzato, il test esce dalla suite come riga `-` nel diff di oggi, i run passati restano in ogni giorno committato e nelle statistiche storiche
- [ ] Ordinamento: i test nuovi in coda; riordino manuale rimandato (VISION lo tiene minimale) — se servirà, sarà un verbo testuale, non drag-and-drop
- [ ] Test: wizard end-to-end per i tre tipi, validazioni, edit prefillato, archivio con storia intatta

## Fase 6 — Storico (`habits_history.diff`)

- [ ] Oggi in testa come `# On branch main / # Changes not yet committed (today)` col riassunto del run; un commit per giorno: hash stabile, `Date:` localizzata, messaggio `suite: N/M passed · K skipped`, build result con semantica Jenkins e colori (`✓` verde / `~ unstable` arancio / `✗` rosso), assente nei giorni senza test dovuti
- [ ] Espansione al tap: una riga per test — `+` verde (pass, con orario/valore), `-` rossa (fail), `~` grigia (skip con nota); i cambi di suite come righe di diff (`+ test added: "journal"` / `- test archived: …`)
- [ ] **Separatori settimana ISO** con pass rate e delta (`--- week 34 · 89% passed (+6% vs week 33) ---`) e **lì le verdette quota**: `quota: run 5k 2/3 ✗` — una quota non fallisce mai un giorno, fallisce (o passa) la settimana
- [ ] **`--amend`**: il run di ieri espanso resta interattivo (stessi tap di oggi); al primo edit il commit guadagna il marcatore permanente `# amended`; da due giorni indietro è storia read-only per sempre
- [ ] Tag sui record (`tag: perfect-week`, `tag: longest-streak`) in arancio; status bar `⎇ main | N commits` / `HEAD → <hash>`; empty state onesto
- [ ] Test: documento riga per riga, tre verdetti, espansioni, quota su settimana, amend con marcatore, immutabilità oltre ieri, tag

## Fase 7 — README.md del giorno (seconda tab dell'editor)

- [ ] `WorkspaceStore` portato (DataStore `workspace`, tab persistita, scroll separati)
- [ ] Prosa **completamente localizzata**: titolo = data estesa; `## Oggi` (cosa è passato e cosa resta, parole neutre); `## Stato` (build badge del giorno, streak degne di nota, health in parole solo se dice qualcosa); `## Settimana` (tabella 7 giorni via `MarkdownTable`, oggi in grassetto e vivo, giorni senza test dovuti come `—`); footer `*Calcolato sul dispositivo · N giorni committati*`
- [ ] Test: localizzazione, omissioni oneste, tabella rettangolare, oggi vivo

## Fase 8 — Stats (`stats.md`)

- [ ] **Heatmap contributi 12 settimane** (riuso del dominio `Heatmap` di tsteps): intensità = frazione della suite dovuta passata quel giorno, bucket relativi ai quartili dell'utente, skip fuori denominatore, futuri vuoti, oggi vivo
- [ ] Tabella `## suite health`: per test — health %, streak corrente, pass 30d; ordinata per health discendente
- [ ] `## flaky tests`: pass rate 30d sotto soglia, col numero e un solo hint fisso e fattuale; sezione presente solo con dati sufficienti — mai un dito puntato, solo un report
- [ ] `## tags`: `longest-streak`, `perfect-week` — righe cliccabili verso il commit nel Log (pattern `LogFocus`)
- [ ] Rendering markdown evidenziato, heatmap colorata a mano, footer onesto, status bar `⎇ main | ro`; empty state con griglia comunque visibile
- [ ] Test: geometria heatmap con denominatore-dovuti, tabelle, flaky con soglia dati, tag cliccabili, empty state

## Fase 9 — Notifiche

- [ ] Permission `POST_NOTIFICATIONS` runtime col pattern completo di serie nella sezione `notifications` (toggle gated, status line, detour impostazioni di sistema)
- [ ] **Reminder per test** (`remind:` dal wizard): sveglie **inesatte** via `AlarmManager.setWindow` (~10 min, niente permission exact-alarm — VISION §6.7: un reminder è un nudge, non una sveglia; dichiarato nel file), riarmate al boot e a ogni edit; notifica con **azione `pass`** per i boolean — il test si smarca dalla tendina; i counter aprono l'app sul prompt
- [ ] `daily_commit` (default on, canale LOW silenzioso): il build result del giorno chiuso, compatta una riga / espansa stile `git log` (pattern due-forme di tsteps)
- [ ] `pending_digest` (**default off, opt-in** — VISION §3.3.4): un solo riepilogo serale all'ora configurata (`✗ 3 tests still pending`), mai un nag per test
- [ ] Canali separati, tutte disattivabili, linguaggio neutro sempre; niente notifiche motivazionali, mai
- [ ] Test: contenuti puri delle due forme, azione pass end-to-end, digest solo opt-in, riarmo sveglie

## Fase 10 — Widget (`thabit --status`)

Architettura widget di serie ereditata intera (sizes-map, un gradino per riga, breakpoint misurati, `monospace` di sistema, opacità configurabile, zero polling).

- [ ] Content builder puro: prompt `$ thabit --status 🧪`, barra suite `▓░ N/M`, i test dovuti oggi in colonne compatte `[x] nome`, commento `# N pending — tap to pass`; tier che tagliano dal fondo, il minimo è la sola barra suite
- [ ] **Check-off dal widget**: le righe boolean portano PendingIntent propri (action+requestCode distinti — lezione filterEquals di tsteps) e smarcano senza aprire l'app; counter/avoid aprono l'app sul punto giusto
- [ ] Ridisegni su: tap, rollover, modifiche suite/settings; il widget che non ha ancora ridisegnato mostra la data che sta renderizzando — mai la suite di ieri spacciata per oggi
- [ ] Test: transcript e budget per tier, binary search sui gradini (pattern tsteps), colori via span attraverso l'IPC, intent per riga

## Fase 11 — Export dati

- [ ] `$ thabit export --json` e `--csv` in fondo a settings.config (pattern tsteps Fase 13 per intero: MediaStore pending→publish, zero permission storage, nome riportato = quello vero, esiti `// wrote …` / `// nothing to export yet` / `// ERROR:`)
- [ ] JSON un documento (suite + tutti i check, un record per riga); CSV due file (`thabit-suite-*.csv` + `thabit-checks-*.csv`); `Locale.ROOT`, valori canonici, half-life dell'health dichiarata nell'header — tutto ricalcolabile
- [ ] Onestà del formato: skip con nota, `# amended` come flag dato, test archiviati inclusi con `archived_at` (la storia è dell'utente)
- [ ] Formato documentato nel README (sezione Export); test su documenti e sink

## Fase 12 — Rifiniture dal campo

Fase cuscinetto deliberata: la serie ha imparato che il device del committente riscrive sempre qualcosa (status bar affamate, controlli che non si spiegano, notifiche da ridisegnare). Qui si raccolgono le tornate di feedback prima del restyling finale, ciascuna registrata con la motivazione.

- [ ] Tornata/e di feedback su device del committente
- [ ] Valutare qui, con dati d'uso reali: gruppi per momento della giornata (sezioni YAML), riordino manuale, `$ thabit run` (timer come processo) — si aprono solo se la pratica li chiede (VISION §6)

## Fase 13 — Restyling pre-v1 e Release

- [ ] Passata di coerenza design su tutte le schermate: vincoli, spazi, scala font di sistema, TalkBack
- [ ] Edge case checklist su device: DST e cambio timezone, cambio `day_ends` a giornata in corso, riavvio (riarmo sveglie), giorno vuoto, suite vuota, amend al limite della finestra, archivio con storia lunga, battery saver
- [x] Keystore di release reale — **anticipato a prima della Fase 1** (ago 2026, in coda al giro che ha messo in riga tutte le app della serie): chiave RSA 4096 trentennale (alias `thabit`) generata dal committente con `keytool`, fuori repo in `C:\Fiorenzo\keys\` con backup nel password manager; 4 proprietà `THABIT_*` compilate in `~/.gradle/gradle.properties`, 4 GitHub Secrets caricati, `release.yml` su tag `v*` già nel repo dalla Fase 0. Verificato in locale: `assembleRelease` produce `app-release.apk` firmato, certificato controllato con apksigner (`CN=callbackdev`, fingerprint distinto dalle altre 4 app); il percorso `-PsignReleaseWithDebugKey` è quello già esercitato dalla CI verde. Il keystore di debug committato è nuovo e unico (alias `thabit-debug`, fingerprint diverso dai gemelli)
- [ ] Versione 1.0.0, `CHANGELOG.md`, screenshot reali in `docs/screenshots/`, README definitivo, tag e prima GitHub Release verificata

---

## Note trasversali

- **Vincoli di design non negoziabili** (vedi `CLAUDE.md` e VISION §1.2): solo JetBrains Mono (eccetto widget), griglia 4px, indent 20px, niente ombre (bordi 1px + glow del FAB), raggio 4px ovunque, controlli renderizzati come testo, emoji come icone nel testo.
- **Regola l10n**: il "codice" resta inglese (chiavi YAML/JSON, filenames, commenti, output terminale, hash, check line); chrome e valori-dato localizzati IT/EN; i nomi dei test sono dati dell'utente. `README.md` (la tab) è prosa: localizzata per intero.
- **Niente rete**: se una feature futura chiede la permission INTERNET, non è una feature di thabit.
- **Verdetti mai persistiti**: build result, streak, health e record si calcolano in lettura dai check. Il worker di mezzanotte notifica e ridisegna, non scrive.
- **Ordine**: Fasi 1–2 sono il fondamento (la 2 in parallelo alla 1). La 3 dipende da 1–2; la 5 rende l'app usabile e sblocca il valore di 6–10; il widget (10) dopo che dominio e settings sono stabili.
- **Import dalla serie**: i componenti si copiano adattando il package, mai linkando i repo; ogni divergenza che emerge (bug fixati qui, migliorie) va valutata per il backport a tweather/tsteps.
- Aggiornare questo file smarcando i passi completati e annotando le deviazioni dalla VISION con la motivazione.
