# AGENTS.md

IntelliJ Platform plugin `libgdx-plugin-asset` (group `com.axehigh.libgdx.plugin.assets`).
It makes libGDX texture assets first-class: a `<name>.png` + `<name>.atlas` pair gets an
interactive spritesheet viewer (hover highlights the region under the cursor) and per-region
thumbnails in the `.atlas` text editor's gutter. Read-only — it never rewrites asset files.

Design/plan: `resources/docs/libgdx-asset-plugin-plan.md`.

`README.md` is the stock upstream JetBrains template. When it conflicts with the build files,
trust `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`.

## Build & run

Run everything from the repo root. `gradlew` is executable; if a fresh checkout drops the bit,
`chmod +x gradlew` (or prefix commands with `sh`).

| Purpose | Command |
| --- | --- |
| Compile / assemble | `./gradlew build` |
| Unit tests | `./gradlew test` |
| Single test | `./gradlew test --tests "*.AtlasParserTest"` |
| Launch sandbox IDE with plugin | `./gradlew runIde` |
| Marketplace compatibility check | `./gradlew verifyPlugin` |
| Distributable plugin zip | `./gradlew buildPlugin` |

`.run/` holds the matching configs (Run Tests runs the broader `check`). `runIde`/tests write a
sandbox under `.intellijPlatform/sandbox/`; logs at `.intellijPlatform/sandbox/*/*/log/idea.log`.
Ignored: `.intellijPlatform/`, `build/`, `.gradle/`, `.idea/`.

## JDK

The Gradle **daemon is pinned to JDK 21** by `gradle/gradle-daemon-jvm.properties` (auto-provisioned
through the foojay resolver). Do not delete it: running the daemon on this machine's JDK 25 makes
`instrumentCode` fail with `…/Packages does not exist`. The launcher JVM may be newer — only the
daemon matters. Check with `./gradlew --version` ("Daemon JVM: Compatible with Java 21").

## Toolchain (pinned — do not "upgrade" casually)

- Gradle wrapper 9.7.1, Kotlin 2.3.20, IntelliJ Platform Gradle Plugin 2.19.0.
- Target IDE `2025.3.6.1`; change only the `intellijIdea(...)` call in `build.gradle.kts`.
- Tests use **JUnit 4** (`junit:junit:4.13.2`) plus the IntelliJ Platform test framework. The
  pure parser/hit-test tests are plain JUnit 4 (`@Test`); platform tests extend
  `BasePlatformTestCase`, name methods `testXxx`, and use `myFixture.tempDirFixture` + `getFile`.
- Third-party deps go in `gradle/libs.versions.toml` (`libs.*`); IntelliJ bundled plugins go in the
  `intellijPlatform {}` block.

## Layout

- `src/main/kotlin/com/axehigh/libgdx/plugin/assets/`
  - `atlas/` — `AtlasParser` (libGDX atlas text format; parses by line shape and **never** relies
    on blank-line separators), `AtlasData`/`AtlasRegion` (`toImageRect` maps the atlas `bounds`
    straight into top-left PNG coords; `regionAt` is the hit test), `LibgdxAsset` (content-sniffed
    png+atlas pair resolver).
  - `editor/` — `AtlasImagePanel` (Swing viewer: async load, fit/zoom/pan, hover outline+name label,
    right-click copy of region name/bounds), two `fileEditorProvider`s (`Png…` hides the stock
    ImageEditor; `Atlas…` places the viewer after the text editor so gutter thumbnails stay visible),
    and `AtlasGutterListener` (`RangeHighlighter` + `GutterIconRenderer` per region; hovering a
    region line or its gutter mark opens a larger preview popup).
- `src/main/resources/META-INF/plugin.xml` — manifest. Plugin id
  `com.axehigh.libgdx.plugin.assets.libgdx-plugin-asset` (do not change between versions); register
  extensions under `<extensions defaultExtensionNs="com.intellij">`.
- `resources/assets/origin-game.{atlas,png}` (**repo root, not under `src/`**) is the sample sheet
  (173 regions, 2048²). Parser tests read it via the relative path `resources/assets/...` (Gradle's
  test working dir is the project root), so reuse it instead of inventing fixtures.

## Gotchas

- `.atlas` is a shared extension (Spine and others use it) — always content-sniff and require a
  parsed `bounds:` region; never claim all `.atlas` files.
- The `.png` viewer is gated on a valid sibling `.atlas`, so ordinary PNGs keep the stock editor.
- libGDX `bounds` line up with PNG pixel space at a top-left origin (verified against the sample
  sheet); if that ever looks wrong, re-check with a pixel test rather than re-adding a y-flip. The
  mapping lives in `AtlasRegion.toImageRect` — reuse it rather than re-deriving.
