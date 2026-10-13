# AGENTS.md

IntelliJ Platform plugin (`libgdx-plugin-asset`, group `com.axehigh.libgdx.plugin.assets`)
for working with libGDX assets. It is a fresh scaffold from the JetBrains IDE Plugin
wizard: `src/main/kotlin` and `src/test` do not exist yet, and `plugin.xml` still has
placeholder vendor/description text.

`README.md` is the stock upstream JetBrains template. When it conflicts with
`build.gradle.kts`, `settings.gradle.kts`, or `gradle.properties`, trust the build files.

## Build & run

Run everything from the repo root. `gradlew` is **not executable** in this checkout:
`chmod +x gradlew` once, or prefix commands with `sh` (e.g. `sh gradlew test`).

| Purpose | Command |
| --- | --- |
| Compile / assemble | `./gradlew build` |
| Unit tests | `./gradlew test` |
| Single test | `./gradlew test --tests "com.axehigh.libgdx.plugin.assets.SomeTest"` |
| Launch sandbox IDE with plugin | `./gradlew runIde` |
| Marketplace compatibility check | `./gradlew verifyPlugin` |
| Distributable plugin zip | `./gradlew buildPlugin` |

`.run/` holds the matching Run/Debug configs (Run Tests runs the broader `check`).
`runIde`/tests write a sandbox under `.intellijPlatform/sandbox/`; IDE logs are at
`.intellijPlatform/sandbox/*/*/log/idea.log`. `.intellijPlatform/`, `build/`, `.gradle/`,
and `.idea/` are gitignored.

## Toolchain (pinned — do not "upgrade" casually)

- Gradle wrapper 9.7.1, Kotlin 2.3.20, IntelliJ Platform Gradle Plugin 2.19.0.
- Compiled against IntelliJ IDEA `2025.3.6.1`; change only the `intellijIdea(...)` call in
  the `intellijPlatform {}` block of `build.gradle.kts`.
- Tests use **JUnit 4** (`junit:junit:4.13.2`), not JUnit 5.
- Add third-party dependencies to `gradle/libs.versions.toml` and reference them as
  `libs.*`. Add IntelliJ bundled/optional plugin dependencies inside `intellijPlatform {}`
  (see the commented `bundledPlugin(...)` example).

## Layout

- `src/main/kotlin/` — Kotlin sources (create the directory). `src/main/java/` must be
  created before adding Java sources.
- `src/main/resources/META-INF/plugin.xml` — the plugin manifest. Plugin id is
  `com.axehigh.libgdx.plugin.assets.libgdx-plugin-asset` (do not change between versions).
  Register extensions under `<extensions defaultExtensionNs="com.intellij">` and set
  `<idea-version>`/`since-build` here for compatibility.
- `resources/assets/origin-game.{atlas,png}` (**repo root, not under `src/`**) is a sample
  libGDX TextureAtlas in text format (`filename`, `size`, `repeat`, then `region` +
  `bounds:x,y,w,h`). Reuse it as a test fixture instead of inventing sample data.
