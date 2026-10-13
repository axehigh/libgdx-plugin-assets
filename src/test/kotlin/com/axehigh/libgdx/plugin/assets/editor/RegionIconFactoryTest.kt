package com.axehigh.libgdx.plugin.assets.editor

import com.axehigh.libgdx.plugin.assets.atlas.AtlasParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class RegionIconFactoryTest {

    private val image by lazy { ImageIO.read(File("resources/assets/origin-game.png")) }
    private val regions by lazy { AtlasParser.parse(File("resources/assets/origin-game.atlas").readText())!!.regions }

    private fun region(name: String) = regions.first { it.name == name }

    @Test
    fun gutterIconFitsWithin16px() {
        val icon = RegionIconFactory.create(image, region("Coin_01"), 16)
        assertEquals(16, icon.iconWidth)
        assertEquals(16, icon.iconHeight)
    }

    @Test
    fun previewScalesUpPreservingAspect() {
        // Coin_01 is 128x128 -> 2x -> 256x256.
        val coin = RegionIconFactory.create(image, region("Coin_01"), 256)
        assertEquals(256, coin.iconWidth)
        assertEquals(256, coin.iconHeight)

        // dagger is 64x17, so width is the limiting dimension: 256x68.
        val dagger = RegionIconFactory.create(image, region("dagger"), 256)
        assertEquals(256, dagger.iconWidth)
        assertEquals(68, dagger.iconHeight)
    }
}
