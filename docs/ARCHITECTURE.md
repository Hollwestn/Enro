# Enro architecture

Java 25 / JavaFX 25.0.2, Maven, SQLite, Jackson, SLF4J/Logback and JNA. The core has no JavaFX dependency. A single deployable Maven application uses packages as boundaries; a JPMS descriptor is deliberately omitted because SQLite/JNA have native loading requirements. jpackage bundles a Java runtime. No service or elevation is installed.

## Data flow

FXML shell → UI controller → bounded executor → scanner/duplicate/cleanup/organizer services → repositories → SQLite. UI mutations return through Platform.runLater. Background scans emit throttled, indeterminate progress (there is no truthful percentage before enumeration). Closing the application cancels workers.

## Scanner

NIO walkFileTree without FOLLOW_LINKS. Refuse redirected roots/ancestors, skip symbolic links, junctions and reparse points. Persist entries in batches, retain only the directory stack and current batch, aggregate sizes at postVisitDirectory. Configurable entry/depth limits, exclusions, cancellation and per-entry error records. Trees and tables use bounded SQL queries. Map pages contain at most 350 direct records. A Canvas renders the selected folder page, without one scene node per file. Values are logical bytes per path, not physical allocation. Hard links are identified for duplicate search; directory totals can include each hard-link path. Partial results have explicit status.

## Cleanup model

Discovery never authorizes mutation. Immutable snapshots record normalized path, file identity, size, mtime and creation time. Explicit selected candidates create a preview. User confirmation yields a one-use plan; execution rechecks identity and every ancestor against reparse points and protected locations. Only regular files in explicit permitted roots can be processed. HIGH/UNKNOWN are refused. User-temp candidates are MEDIUM unless a specific trusted producer rule exists; an age or extension never makes a file LOW. Downloads are manual review only. Browser profiles, Windows, Program Files, ProgramData, recovery stores and application data are protected (the exact user Temp folder is the narrow exception).

Recycle Bin is the only supported file disposal mode in this release. No permanent deletion, direct system cleanup, browser cache rules or automatic deletion are exposed. Windows Shell uses the undo/recycle flag; unsupported targets fail rather than falling back to deletion. The journal describes recycle operations as recoverable through Windows, not guaranteed disk savings. Organizer moves are same-volume atomic renames, journaled before execution, and can be reversed with identity verification. Safety checks narrow but cannot eliminate namespace races against a malicious process running with the same user privileges; Enro is not a security boundary.

## Persistence

SQLite WAL, busy timeout, foreign keys. Tables: scans, entries (parent/path/logical bytes/file identity/times/category), scan_errors, operations (intent/result/method/recovery), schedules (interval/root/next run/enabled), task_runs. Queries are indexed by scan, parent, size and identity. Settings are validated JSON written by atomic replace. Logs omit file paths by default; explicit CSV/diagnostic exports warn about personal paths.

## UI

FXML defines one permanent sidebar shell. enro.css supplies light/dark appearance and six live accent themes. JavaFX controls define consistent data views. Dashboard uses actual FileStore values and journal data. Analyzer offers a directory tree drilldown via Canvas squarified layout, tooltips/context menus, search/filter, breadcrumb/up and CSV export. Shared paginated file views power large/old files. Duplicate results use streaming SHA-256, sample hashes and byte comparison with a protected keeper. Cleanup preview is opt-in. Recovery, organizer, schedules and settings use real repositories.

## Delivery plan and acceptance gates

1. Maven/architecture: resolve dependencies and compile.
2. Scanner: fixture counts, exclusions, links, limits, cancellation and persistence tests.
3. Dashboard/FXML: load actual drives, launch JavaFX.
4. Analyzer/treemap: layout area tests, persisted directory browsing and export.
5. Duplicates: sample/full/byte checks, identities, hard-link and keeper tests.
6. Cleanup: preview/confirmation, changed-file/protected-path/link rejection tests.
7. Recovery: durable intent/result journal and reversible move tests.
8. Large/old/downloads: SQL filters, rename preview and atomic move checks.
9. Automation/settings: persisted analysis-only schedules, JSON validation tests.
10. Hardening: limits, bounded tasks, cancellation, diagnostic privacy.
11. Verification: actual Maven and Windows UI smoke results in TEST_REPORT.md.
12. Release: jpackage portable app and per-user installer, documentation and checksums.

Gates are recorded with evidence in TEST_REPORT.md. Unverified behavior is never marked passed.
