package com.axehigh.libgdx.plugin.assets.atlas

/**
 * Parser for the libGDX TextureAtlas text format.
 *
 * The format is detected by line shape rather than by blank-line separators (the sample atlas in
 * this repo contains none): the first content line is the texture file name, a line containing `:`
 * is a `key:value` pair, and a bare line starts a new region. Only `size` and `bounds` are read;
 * other keys (`rotate`, `index`, `offsets`, `orig`, `split`, `filter`, `repeat`, `pma`) are ignored.
 */
object AtlasParser {

    /** Returns the parsed atlas, or null when the text does not contain any bounded region. */
    fun parse(text: String): AtlasData? {
        var textureName: String? = null
        var textureWidth = 0
        var textureHeight = 0
        var sawHeader = false

        var currentName: String? = null
        var currentLine = -1
        var currentBounds: IntArray? = null
        val regions = ArrayList<AtlasRegion>()

        fun flushRegion() {
            val name = currentName
            val bounds = currentBounds
            if (name != null && bounds != null && bounds.size == 4) {
                regions.add(AtlasRegion(name, bounds[0], bounds[1], bounds[2], bounds[3], currentLine))
            }
            currentName = null
            currentLine = -1
            currentBounds = null
        }

        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed

            if (!sawHeader) {
                textureName = line
                sawHeader = true
                return@forEachIndexed
            }

            val colon = line.indexOf(':')
            if (colon < 0) {
                flushRegion()
                currentName = line
                currentLine = index
                return@forEachIndexed
            }

            val key = line.substring(0, colon).trim()
            val value = line.substring(colon + 1).trim()
            when (key) {
                "size" -> ints(value)?.let {
                    if (it.size >= 2) {
                        textureWidth = it[0]
                        textureHeight = it[1]
                    }
                }
                "bounds" -> currentBounds = ints(value)
            }
        }
        flushRegion()

        if (regions.isEmpty()) return null
        return AtlasData(textureName, textureWidth, textureHeight, regions)
    }

    /** Splits a comma-separated int list, returning null if any element is not a valid integer. */
    private fun ints(value: String): IntArray? {
        val parts = value.split(',')
        val out = IntArray(parts.size)
        for (i in parts.indices) {
            out[i] = parts[i].trim().toIntOrNull() ?: return null
        }
        return out
    }
}
