package com.axehigh.libgdx.plugin.assets.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class AtlasParserTest {

    private val sample: String by lazy { File("resources/assets/origin-game.atlas").readText() }

    @Test
    fun parsesHeaderAndAllRegions() {
        val data = AtlasParser.parse(sample)!!
        assertEquals(2048, data.textureWidth)
        assertEquals(2048, data.textureHeight)
        assertEquals(173, data.regions.size)
    }

    @Test
    fun parsesKnownBounds() {
        val data = AtlasParser.parse(sample)!!
        val coin = data.regions.first { it.name == "Coin_01" }
        assertEquals(1042, coin.x)
        assertEquals(220, coin.y)
        assertEquals(128, coin.width)
        assertEquals(128, coin.height)

        val dagger = data.regions.first { it.name == "dagger" }
        assertEquals(260, dagger.x)
        assertEquals(535, dagger.y)
        assertEquals(64, dagger.width)
        assertEquals(17, dagger.height)
    }

    @Test
    fun recordsRegionSourceLine() {
        val data = AtlasParser.parse(sample)!!
        // Lines 0-2 are the filename, size and repeat; the first region name is on line 3.
        assertEquals(3, data.regions.first().lineNumber)
    }

    @Test
    fun rejectsTextWithoutBoundedRegions() {
        assertNull(AtlasParser.parse("just some text\nno regions here"))
        assertNull(AtlasParser.parse(""))
    }

    @Test
    fun hitTestUsesTopLeftBounds() {
        val data = AtlasParser.parse(sample)!!
        // Coin_01 bounds are 1042,220,128,128 with a top-left origin -> the sprite sits at y 220..348.
        assertEquals("Coin_01", data.regionAt(1100, 250)?.name)
        // Under a y-flip this point would have hit Coin_01; with the correct origin it is goblin_hurt1.
        assertEquals("goblin_hurt1", data.regionAt(1100, 1750)?.name)
        assertNull(data.regionAt(0, 0))
    }

    /**
     * Guards the coordinate convention: the atlas `bounds` must line up with where the sprite's
     * pixels actually are in the PNG. A vertical flip (bottom-left origin) would sample transparent
     * gutter instead.
     */
    @Test
    fun boundsAlignWithActualSpritePixels() {
        val image = ImageIO.read(File("resources/assets/origin-game.png"))
        val data = AtlasParser.parse(sample)!!
        for (name in listOf("Coin_01", "Chest_01_Locked", "Key_02")) {
            val region = data.regions.first { it.name == name }
            val direct = meanAlpha(image, region.toImageRect())
            val flipped = meanAlpha(
                image,
                Rectangle(region.x, image.height - region.y - region.height, region.width, region.height),
            )
            assertTrue(
                "$name: direct=$direct flipped=$flipped",
                direct > 0.3 && direct > flipped * 2,
            )
        }
    }

    private fun meanAlpha(image: BufferedImage, r: Rectangle): Double {
        val rect = r.intersection(Rectangle(0, 0, image.width, image.height))
        if (rect.width <= 0 || rect.height <= 0) return 0.0
        var sum = 0L
        for (y in rect.y until rect.y + rect.height) {
            for (x in rect.x until rect.x + rect.width) {
                sum += (image.getRGB(x, y) ushr 24) and 0xff
            }
        }
        return sum.toDouble() / (rect.width.toLong() * rect.height)
    }
}
