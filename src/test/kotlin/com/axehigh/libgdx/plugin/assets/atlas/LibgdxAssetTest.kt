package com.axehigh.libgdx.plugin.assets.atlas

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LibgdxAssetTest : BasePlatformTestCase() {

    private val atlasText = """
        origin-game.png
        size:8,8
        repeat:none
        coin
        bounds:0,0,4,4
    """.trimIndent()

    fun testResolvesPairFromPng() {
        myFixture.tempDirFixture.createFile("origin-game.atlas", atlasText)
        myFixture.tempDirFixture.createFile("origin-game.png", "png-bytes")

        val png = myFixture.tempDirFixture.getFile("origin-game.png")!!
        val asset = LibgdxAsset.forFile(png)

        assertNotNull(asset)
        assertEquals(1, asset!!.regions.size)
        assertEquals("coin", asset.regions.first().name)
    }

    fun testResolvesPairFromAtlas() {
        myFixture.tempDirFixture.createFile("origin-game.atlas", atlasText)
        myFixture.tempDirFixture.createFile("origin-game.png", "png-bytes")

        val atlas = myFixture.tempDirFixture.getFile("origin-game.atlas")!!
        val asset = LibgdxAsset.forFile(atlas)

        assertNotNull(asset)
        assertEquals("origin-game.png", asset!!.png.name)
    }

    fun testPngWithoutAtlasIsNotAnAsset() {
        myFixture.tempDirFixture.createFile("lonely.png", "png-bytes")
        val png = myFixture.tempDirFixture.getFile("lonely.png")!!

        assertNull(LibgdxAsset.forFile(png))
    }

    fun testAtlasWithoutBoundsIsNotAnAsset() {
        myFixture.tempDirFixture.createFile("origin-game.atlas", "origin-game.png\nsize:8,8\n")
        myFixture.tempDirFixture.createFile("origin-game.png", "png-bytes")

        val atlas = myFixture.tempDirFixture.getFile("origin-game.atlas")!!
        assertNull(LibgdxAsset.forFile(atlas))
    }

    fun testSiblingLookupIsCaseInsensitive() {
        myFixture.tempDirFixture.createFile("Origin-Game.ATLAS", atlasText)
        myFixture.tempDirFixture.createFile("origin-game.png", "png-bytes")

        val png = myFixture.tempDirFixture.getFile("origin-game.png")!!
        assertNotNull(LibgdxAsset.forFile(png))
    }
}
