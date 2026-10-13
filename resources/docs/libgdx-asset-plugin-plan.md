# libGDX Asset Plugin — Implementation Plan

## Goal

A **libGDX asset** is a `<name>.png` + `<name>.atlas` pair in the same directory.
This IntelliJ Platform plugin makes such pairs first-class:

- Opening **either** file of a pair gives an interactive spritesheet viewer.
- Hovering the image highlights the atlas region under the cursor and shows a
  tooltip with its name and bounds.
- The `.atlas` **text** editor shows a per-region thumbnail in the gutter.
- Read-only: the plugin never rewrites `.png` or `.atlas` files.

## Locked decisions

| Question | Decision |
| --- | --- |
| Viewer surface | Both `.png` and `.atlas` open the interactive viewer |
| Hover feedback | Outline highlight + tooltip (name, x/y/w/h) |
| Atlas previews | Gutter thumbnails per region name |
| Editing | Read-only |

### Reconciling "both open the viewer" with "gutter thumbnails"

A viewer that *replaced* the `.atlas` text editor would hide the gutter
thumbnails, so:

- `.png` → the viewer replaces the stock ImageEditor (`HIDE_DEFAULT_EDITOR`),
  but only when a sibling `.atlas` parses; ordinary PNGs keep the stock editor.
- `.atlas` → the text editor stays (gutter thumbnails work) and the viewer is
  added as a **second, right-hand tab** (`PLACE_RIGHT`).

## Architecture

Package root: `com.axehigh.libgdx.plugin.assets`.

- **`atlas/AtlasParser`** — parses the libGDX TextureAtlas text format by *line
  shape*: a line containing `:` is `key:value`; a bare line is a region name.
  It must **not** rely on blank-line separators (the sample file has none).
  Reads the header (`size`) and each region's `bounds:x,y,w,h`, recording the
  region's source line number. Unknown keys (`rotate`, `index`, `offsets`,
  `orig`, `split`) are ignored for now.
- **`atlas/AtlasData`, `atlas/AtlasRegion`** — immutable model.
  `AtlasRegion.toImageRect()` maps the atlas `bounds` straight onto top-left
  image/PNG coordinates (verified against the sample PNG: a y-flip put the box and
  the gutter crops over transparent pixels). `AtlasData.regionAt(x, y)`
  is the hit test (reverse iteration so later regions win overlaps).
- **`atlas/LibgdxAsset`** — pure resolver from a `VirtualFile` to its
  png+atlas pair (`parent.findChild`, with a case-insensitive fallback). Returns
  `null` when unpaired or when the `.atlas` does not parse to any bounded
  region (content sniff — Spine atlases use `xy:`/`size:` and are never claimed).
- **`editor/AtlasImagePanel`** — Swing viewer: asynchronous PNG load,
  fit-to-window, wheel zoom (cursor-anchored), drag pan, nearest-neighbour
  scaling for crisp sprites, hover outline + name/bounds label + tooltip,
  right-click menu to copy the region name or bounds, live reload on VFS change.
- **`editor/LibgdxAssetFileEditorProvider`** / **`LibgdxAssetFileEditor`** —
  the `FileEditor` plumbing; per-file policy (`HIDE_DEFAULT_EDITOR` for `.png`,
  `PLACE_AFTER_DEFAULT_EDITOR` for `.atlas` so the text editor stays visible).
- **`editor/AtlasGutterListener`** + **`RegionGutterRenderer`** + **`RegionHoverPreview`** —
  an `EditorFactoryListener` that adds a `RangeHighlighter` per region-name line
  with a `GutterIconRenderer` painting a crop of the sibling PNG, and (via
  `EditorMouseMotionListener`) a larger preview popup when the pointer rests on a
  region line or its gutter mark. Document-based (works on plain text, no PSI/lexer).
  `RegionIconFactory` crops and scales regions (16px gutter icons, up to 256px
  previews, nearest-neighbour upscaling capped at 8x).

### Registration (`plugin.xml`)

```xml
<fileEditorProvider implementation="...editor.LibgdxAssetFileEditorProvider"/>
<editorFactoryListener implementation="...editor.AtlasGutterListener"/>
```

## Phases (tracer bullets)

- **P0 — skeleton.** Create `src/main/kotlin` (`com.axehigh.libgdx.plugin.assets`),
  `src/test`, register the extension points.
- **P1 — vertical slice.** Parse the atlas → resolve the pair → viewer with
  fit/zoom/pan → hover outline + tooltip. Parser, resolver, and hit-test tests green.
- **P2 — UX.** Reload on file change, click-to-focus, region info, error states
  (missing PNG, malformed atlas).
- **P3 — atlas gutter thumbnails.** Then, optionally, inline thumbnails.
- **P4 — polish.** `.atlas` file type/icon, settings toggle, docs.

## Tests

JUnit 4 + the IntelliJ Platform test framework (already configured).

- `AtlasParserTest` (pure JUnit): parse `resources/assets/origin-game.atlas`,
  assert 173 regions, header `size:2048,2048`, and exact bounds for a couple of
  regions (`Coin_01`, `dagger` 64×17); plus a pixel test proving `bounds` map
  straight onto the PNG (top-left origin) and a `regionAt` hit test.
- `LibgdxAssetTest extends BasePlatformTestCase`: pair hit/miss using real local
  VirtualFiles — png+atlas resolves, atlas-without-png is null, a non-libGDX
  `.atlas` is rejected, sibling lookup is case-insensitive.

## Risks / notes

- The `.atlas` extension is shared (Spine, others) → always content-sniff.
- The built-in image editor already offers zoom; `HIDE_DEFAULT_EDITOR` is gated
  on a valid pair so plain PNGs are unaffected.
- 2048² sheet with ~173 regions → hit tests index by rectangle; image loads
  off the EDT.
- Gutter thumbnails are document-based highlighters, so they work on plain text
  without a custom language, but must be rebuilt when the document changes.
