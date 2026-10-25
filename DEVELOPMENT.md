# Development

JDK 25 x64, Maven Wrapper, JavaFX 25.0.2, Windows 10/11. All versions are pinned in pom.xml. Core scanner/cleanup/duplicates/storage/settings/automation packages contain no JavaFX imports. UI uses FXML, CSS and a Canvas treemap; pure SquarifiedLayout geometry is unit tested without a toolkit.

```powershell
.\mvnw.cmd verify
.\mvnw.cmd javafx:run
.\mvnw.cmd -Denro.nativeTests=true verify
powershell -File .\scripts\package-windows.ps1 -Type all
powershell -File .\scripts\package-source.ps1
```

Native tests manipulate only generated fixtures. Symlink creation requires Windows Developer Mode or a suitable existing privilege; tests never enable it. Junction and actual recycle/restore tests are opt-in. Do not point destructive tests at personal folders.

Packaged, non-UI release probe:

```powershell
Start-Process .\release\Enro\Enro.exe -ArgumentList '--self-check','C:\absolute\writable\release-check.json' -Wait
```

This does not open a window or clean personal files. It creates a new temporary fixture beside the report, checks named JavaFX modules, native library loading, SQLite, scanner, duplicates, JSON and move/undo, then removes only that generated fixture. The report must say PASS. This is not a substitute for UI or installer acceptance. Generate the original icon resources with `scripts/generate-icon.ps1` if modifying the branding.

For a local isolated app-data folder, set `-Denro.home=<absolute path>` in the JVM. The launcher locks instance.lock; don't open the same DB from two app instances. SQLite WAL allows concurrent readers and commits scan batches every 1000 entries. Database schema is created idempotently; this first schema does not yet include versioned migrations. Do not change it incompatibly without a migration.

Packages:

| Package | Responsibility |
|---|---|
| app | boot, data-directory lock, bounded executor, service wiring |
| core | immutable records and cooperative cancellation |
| scanner | bounded walk, aggregation and partial statuses |
| storage | SQLite repositories, streaming CSV, private diagnostics |
| cleanup | candidate rules, safety policy, one-use confirmed plan |
| duplicates | sample/full hashes, byte verification, File ID grouping |
| recovery | journaled same-volume moves and guarded undo |
| automation | persisted analysis-only in-process scheduler |
| windows | reparse/File ID, Known Folders, FileStore, COM recycle |
| ui | FXML shell controller, page views, Canvas, pure layout |
| settings | validated atomic JSON persistence |

`MainController` contains UI composition, selection, dialogs and job dispatch only. It calls services for file changes. Future UI growth should extract page-specific presenters; no business rule should migrate into a button handler. JSON import does not execute paths or scripts. SQL user values use parameters. CSV protects formula prefixes. Logs omit exception messages and file paths; diagnostics are intentionally small.

Packaging separates JavaFX modules into a jlink runtime, keeps third-party libraries on the classpath and enables only required native access. Memory is capped at 768 MiB in packaged launchers. App images and installers are unsigned until the release owner adds Authenticode. Win10 x64 and install/uninstall acceptance on clean VMs remain a release gate; see TEST_REPORT.md.
