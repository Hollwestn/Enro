# Статус этапов master prompt

Это сводка реализации и доказательств, а не отметка полного прохождения коммерческой приёмки.

| Этап | Сделано | Оставшаяся проверка / ограничение |
|---|---|---|
| 1. Архитектура/Maven | Пакетные границы, закреплённые зависимости, Wrapper, успешная Java 25 сборка | Нет блокера сборки |
| 2. Сканер | SQLite, обход NIO, размеры, отмена, исключения, длинные пути, ACL, junction | Symlink test пропущен из-за прав; крупные нагрузочные диски не проверены |
| 3. Dashboard | Реальные FileStore показатели, история, графики; окно визуально проверено | Проверка других разрешений/DPI/экранов |
| 4. Analyzer/treemap | Canvas squarified geometry, drilldown/up, hover/select, фильтры/страницы, CSV, ошибки | UI списка/карты и переход в каталог проверены; другие DPI требуют приёмки |
| 5. Duplicates | Size/sample/SHA-256/byte pipeline, hard links, keeper, лимиты | UI групп и предпросмотра проверен; миллионы кандидатов не проверены |
| 6. Cleanup preview | Immutable preview, пустой выбор, подтверждение, guards, реальная Корзина | Полная/отключённая Корзина, UI-confirmation acceptance, residual namespace race |
| 7. Recovery | Durable intent/result, interrupted states, Windows Bin, guarded move undo | Приёмка GUI восстановления и power-loss сценарии |
| 8. Large/Old/Downloads | SQL filters, metadata/image preview, batch rename/move preview и журнал | Приёмка полного сценария Downloads GUI; операции только внутри Downloads на одном томе |
| 9. Automation/settings | Daily/weekly, startup, history, due-run test, atomic JSON, темы/язык | Темы и языки проверены в UI; длительный soak test не выполнен, приложение должно быть открыто |
| 10. Hardening | Ограниченные workers/queues/pages, stream hashing, no link following, protected paths, native ID | Нагрузочные RSS/скорость, hostile-process TOCTOU не устранён полностью |
| 11. Tests | 49 тестов: 48 PASS, 1 SKIPPED; Windows ACL/junction/recycle/restore; packaged self-check PASS | Указанные TEST_REPORT.md системные и UI сценарии |
| 12. Release/docs | EXE, MSI, portable, source ZIP, встроенный runtime, SHA-256, русское START_HERE и полный набор руководств | Clean-VM Win10/Win11 install/uninstall, подпись и внешняя release/security acceptance |

Рабочая локальная сборка подготовлена. Полностью закрытым весь master prompt до оставшейся приёмки не объявляется. Подробности — ../TEST_REPORT.md и ../SAFETY_GUIDE.md.
