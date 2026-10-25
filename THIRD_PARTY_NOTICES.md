# Third-party notices

Enro bundles a Java runtime and the libraries listed below. Their license and copyright notices remain in the distributed JARs/runtime. This file is an index, not a replacement for the upstream legal files. No license for the original Enro source is imposed without the project owner's choice.

| Component | Upstream / license |
|---|---|
| JDK runtime used in this local build | Oracle JDK 25.0.3, Oracle No-Fee Terms and Conditions (NFTC); full terms and notices are included in runtime/legal/java.base/LICENSE and the other runtime/legal module folders. |
| OpenJFX | https://openjfx.io/ — GPL v2 with Classpath Exception; module legal resources included in runtime |
| sqlite-jdbc | https://github.com/xerial/sqlite-jdbc — Apache License 2.0; SQLite upstream is public domain |
| Jackson | https://github.com/FasterXML/jackson — Apache License 2.0 |
| SLF4J | https://www.slf4j.org/ — MIT |
| Logback | https://logback.qos.ch/ — EPL 1.0 / LGPL 2.1 dual licensing |
| JNA | https://github.com/java-native-access/jna — LGPL 2.1 or Apache License 2.0 |
| Maven Wrapper | https://maven.apache.org/wrapper/ — Apache License 2.0 |
| JUnit 5 (test only) | https://junit.org/junit5/ — EPL 2.0 |

Maven plugins and JUnit are build/test dependencies rather than shipped app runtime. No third-party artwork or reference-image assets are included. The interface is independently implemented in FXML/CSS/Canvas.
