package com.axehigh.libgdx.plugin.assets.atlas

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

/**
 * A resolved libGDX asset: a `.png` and its sibling `.atlas`, together with the parsed atlas data.
 *
 * Resolution is content-sniffed: a `.png` is only an asset when a sibling `.atlas` parses to at
 * least one bounded region, and a `.atlas` is only an asset when the same holds and a sibling
 * `.png` exists. This keeps unrelated `.atlas` files (for example Spine atlases) out of scope.
 */
class LibgdxAsset private constructor(
    val png: VirtualFile,
    val atlas: VirtualFile,
    val data: AtlasData,
) {
    val regions: List<AtlasRegion> get() = data.regions

    companion object {
        private const val PNG_EXT = "png"
        private const val ATLAS_EXT = "atlas"

        /** Resolves [file] to its asset pair, or null if it is not part of a libGDX asset. */
        fun forFile(file: VirtualFile): LibgdxAsset? = when (file.extension?.lowercase()) {
            PNG_EXT -> resolvePng(file)
            ATLAS_EXT -> resolveAtlas(file)
            else -> null
        }

        private fun resolvePng(png: VirtualFile): LibgdxAsset? {
            val atlas = sibling(png, ATLAS_EXT) ?: return null
            val data = parse(atlas) ?: return null
            return LibgdxAsset(png, atlas, data)
        }

        private fun resolveAtlas(atlas: VirtualFile): LibgdxAsset? {
            val data = parse(atlas) ?: return null
            val png = sibling(atlas, PNG_EXT) ?: return null
            return LibgdxAsset(png, atlas, data)
        }

        /** Finds the file with the same base name and [extension] in the same directory. */
        private fun sibling(file: VirtualFile, extension: String): VirtualFile? {
            val parent = file.parent ?: return null
            val base = file.nameWithoutExtension
            parent.findChild("$base.$extension")?.let { return it }
            // Fall back to a case-insensitive match (e.g. origin-game.PNG).
            return parent.children.firstOrNull {
                it.nameWithoutExtension.equals(base, ignoreCase = true) &&
                    it.extension.equals(extension, ignoreCase = true)
            }
        }

        private fun parse(atlas: VirtualFile): AtlasData? =
            runCatching { AtlasParser.parse(VfsUtilCore.loadText(atlas)) }.getOrNull()
    }
}
