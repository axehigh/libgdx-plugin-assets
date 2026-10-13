package com.axehigh.libgdx.plugin.assets.atlas

import java.awt.Rectangle

/**
 * A single region of a libGDX texture atlas.
 *
 * [x], [y], [width] and [height] are exactly as written in the atlas `bounds` value. libGDX texture
 * coordinates use a **top-left** origin (the same convention as `TextureRegion`), so these map
 * directly onto PNG pixel space.
 */
data class AtlasRegion(
    val name: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    /** Zero-based line in the atlas file where the region name appears, or -1 if unknown. */
    val lineNumber: Int = -1,
) {
    /** Bounds as a rectangle in PNG/image pixel space (top-left origin). */
    fun toImageRect(): Rectangle = Rectangle(x, y, width, height)
}

/** Parsed contents of a libGDX `.atlas` file. */
data class AtlasData(
    val textureName: String?,
    val textureWidth: Int,
    val textureHeight: Int,
    val regions: List<AtlasRegion>,
) {
    /**
     * Returns the region covering the given point in PNG/image pixel space (top-left origin), or
     * null. Regions are scanned in reverse so that later entries win where bounds overlap.
     */
    fun regionAt(x: Int, y: Int): AtlasRegion? =
        regions.asReversed().firstOrNull { it.toImageRect().contains(x.toDouble(), y.toDouble()) }
}
