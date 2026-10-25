# Pinned dependencies and compatibility

The following exact artifacts resolved from Maven Central and compiled with JDK 25.0.3 on Windows 11 x64. Tests exercise SQLite, Jackson, JNA and SHA-256. JavaFX launch was observed in a real desktop window.

| Component | Version |
|---|---|
| Java | 25 (tested JDK 25.0.3) |
| JavaFX controls/fxml | 25.0.2 |
| Maven Wrapper | 3.3.4, only-script |
| Maven distribution | 3.9.16 |
| sqlite-jdbc | 3.51.2.0 |
| Jackson databind/core | 2.20.1 (annotations transitive 2.20) |
| SLF4J | 2.0.17 |
| Logback classic/core | 1.5.32 |
| JNA/JNA platform | 5.18.1 |
| JUnit Jupiter | 5.13.4 (user-requested JUnit 5 line) |
| Compiler / Surefire | 3.14.1 / 3.5.4 |
| JavaFX Maven plugin | 0.0.8 |
| Jar / dependency plugins | 3.4.2 / 3.9.0 |

JavaFX 25 requires JDK 23+ and 25.0.2 is documented for JDK 25: [OpenJFX 25 highlights](https://openjfx.io/highlights/25/), [Oracle JavaFX 25 release notes](https://docs.oracle.com/en/java/java-components/javafx/25/release-notes/). JUnit 5 is retained explicitly to meet the requested stack. The JavaFX Maven plugin's small launcher integration is older than the runtime; actual JDK 25 compilation and launch are the compatibility evidence rather than an assumption about its version number.

Windows recycle follows Microsoft's [IFileOperation](https://learn.microsoft.com/en-us/windows/win32/api/shobjidl_core/nn-shobjidl_core-ifileoperation) and [operation flags](https://learn.microsoft.com/en-us/windows/win32/api/shobjidl_core/nf-shobjidl_core-ifileoperation-setoperationflags). It is initialized in an STA worker. No Python participates in application functions or packaging.

Versions are pinned and resolved, not represented as the newest available security-reviewed versions. Recheck upstream advisories and license obligations before external distribution. `mvnw.cmd dependency:tree` gives the complete transitive list. See THIRD_PARTY_NOTICES.md.
