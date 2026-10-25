# Что скачать

Все файлы находятся в [Enro Releases](https://github.com/Hollwestn/Enro/releases/tag/v2.0.0).

| Файл | Назначение |
|---|---|
| Enro-2.0.0.exe | Установщик для Windows x64: обычный вариант |
| Enro-2.0.0.msi | Установка в формате Windows Installer |
| Enro-Portable-2.0.0-win-x64.zip | Вся папка приложения со встроенной Java, без установки |
| Enro-Source-2.0.0.zip | Исходники, документация и Maven Wrapper; для сборки нужен JDK 25 |
| SHA256SUMS.txt | Контрольные суммы четырёх файлов выше |

Для первого знакомства выберите EXE. Для portable распакуйте весь архив и запустите Enro/Enro.exe, сохранив app/ и runtime/ рядом. Portable хранит настройки в %LOCALAPPDATA%\Enro; это не режим хранения профиля на флешке.

Отдельная Java для готового приложения не нужна. Сборки не подписаны; не обходите ограничения своей организации. Проверка SHA-256:

~~~powershell
Get-FileHash .\Enro-2.0.0.exe -Algorithm SHA256
~~~

Сравните результат с SHA256SUMS.txt из того же релиза. GitHub также автоматически создаёт Source code ZIP/TAR.GZ из тега; это исходники, а не готовое приложение.

Поддерживаемая цель — Windows 10/11 x64; текущая фактическая проверка выполнена на Windows 11. Полные сведения: [INSTALLATION.md](../INSTALLATION.md), [START_HERE.md](../START_HERE.md), [TEST_REPORT.md](../TEST_REPORT.md).
