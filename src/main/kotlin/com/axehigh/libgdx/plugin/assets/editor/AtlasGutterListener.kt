package com.axehigh.libgdx.plugin.assets.editor

import com.axehigh.libgdx.plugin.assets.atlas.AtlasRegion
import com.axehigh.libgdx.plugin.assets.atlas.LibgdxAsset
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Adds a clickable, thumbnail-shaped gutter mark next to every region name in a libGDX `.atlas`
 * text editor. The mark is a [RangeHighlighter] with a [GutterIconRenderer], so it works on plain
 * text without a custom language or PSI. Marks are rebuilt when the document changes.
 *
 * Hovering a region's line (or its gutter mark) shows a larger preview popup.
 */
class AtlasGutterListener : EditorFactoryListener {

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val file = editor.virtualFile ?: return
        if (!file.extension.equals("atlas", ignoreCase = true)) return
        val asset = LibgdxAsset.forFile(file) ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            val image = runCatching { asset.png.inputStream.use { ImageIO.read(it) } }.getOrNull()
            ApplicationManager.getApplication().invokeLater {
                if (editor.isDisposed || image == null) return@invokeLater
                install(editor, asset, image)
            }
        }
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        event.editor.getUserData(LISTENER_KEY)?.let { Disposer.dispose(it) }
    }

    private fun install(editor: Editor, asset: LibgdxAsset, image: BufferedImage) {
        val document = editor.document

        fun rebuild() {
            editor.getUserData(HIGHLIGHTERS_KEY)?.forEach { editor.markupModel.removeHighlighter(it) }
            val highlighters = asset.regions.mapNotNull { region ->
                if (region.lineNumber !in 0 until document.lineCount) return@mapNotNull null
                val start = document.getLineStartOffset(region.lineNumber)
                val end = document.getLineEndOffset(region.lineNumber)
                editor.markupModel
                    .addRangeHighlighter(start, end, HighlighterLayer.ADDITIONAL_SYNTAX, null, HighlighterTargetArea.LINES_IN_RANGE)
                    .apply { gutterIconRenderer = RegionGutterRenderer(region, RegionIconFactory.create(image, region, GUTTER_SIZE)) }
            }
            editor.putUserData(HIGHLIGHTERS_KEY, highlighters)
        }

        rebuild()

        val disposable = Disposer.newDisposable("libgdx-atlas-gutters")
        editor.putUserData(LISTENER_KEY, disposable)
        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                ApplicationManager.getApplication().invokeLater {
                    if (!editor.isDisposed) rebuild()
                }
            }
        }, disposable)

        val hover = RegionHoverPreview(editor, asset, image)
        editor.addEditorMouseMotionListener(hover, disposable)
        editor.addEditorMouseListener(hover, disposable)
        Disposer.register(disposable, hover)
    }

    private companion object {
        const val GUTTER_SIZE = 16
        val HIGHLIGHTERS_KEY: Key<List<RangeHighlighter>> = Key.create("libgdx.atlas.highlighters")
        val LISTENER_KEY: Key<Disposable> = Key.create("libgdx.atlas.listener")
    }
}

/**
 * Shows a larger preview of a region while the pointer rests on its `.atlas` line or gutter mark.
 *
 * The popup is offset from the cursor so it never sits under the pointer — otherwise the pointer
 * would leave the editor, the popup would close and the cycle would repeat.
 */
private class RegionHoverPreview(
    private val editor: Editor,
    asset: LibgdxAsset,
    private val image: BufferedImage,
) : EditorMouseMotionListener, EditorMouseListener, Disposable {

    private val regionByLine = asset.regions.filter { it.lineNumber >= 0 }.associateBy { it.lineNumber }
    private val previewCache = HashMap<AtlasRegion, Icon>()
    private var popup: JBPopup? = null
    private var shown: AtlasRegion? = null

    override fun mouseMoved(e: EditorMouseEvent) {
        val region = regionUnder(e)
        if (region != null && region == shown && popup?.isVisible == true) return
        hide()
        if (region != null) show(region, e.mouseEvent)
    }

    override fun mouseExited(e: EditorMouseEvent) = hide()

    override fun dispose() = hide()

    private fun regionUnder(e: EditorMouseEvent): AtlasRegion? {
        (e.gutterIconRenderer as? RegionGutterRenderer)?.let { return it.region }
        if (e.area != EditorMouseEventArea.EDITING_AREA) return null
        return regionByLine[e.logicalPosition.line]
    }

    private fun show(region: AtlasRegion, mouseEvent: MouseEvent) {
        val icon = previewCache.getOrPut(region) { RegionIconFactory.create(image, region, PREVIEW_SIZE) }
        val content = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
            add(JLabel(icon), BorderLayout.CENTER)
            add(
                JLabel("${region.name}  ${region.width}x${region.height} @ ${region.x},${region.y}").apply {
                    horizontalAlignment = SwingConstants.CENTER
                },
                BorderLayout.SOUTH,
            )
        }
        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(content, null)
            .setRequestFocus(false)
            .setResizable(false)
            .setMovable(false)
            .setCancelOnClickOutside(true)
            .createPopup()
            .apply {
                showInScreenCoordinates(
                    editor.contentComponent,
                    Point(mouseEvent.xOnScreen + 16, mouseEvent.yOnScreen + 16),
                )
            }
        shown = region
    }

    private fun hide() {
        popup?.let { if (!it.isDisposed) it.cancel() }
        popup = null
        shown = null
    }

    private companion object {
        const val PREVIEW_SIZE = 256
    }
}

/** Gutter mark for one region; hovering shows its name and bounds. */
private class RegionGutterRenderer(
    val region: AtlasRegion,
    private val icon: Icon,
) : GutterIconRenderer() {

    override fun getIcon(): Icon = icon
    override fun getTooltipText(): String = "${region.name}  ${region.width}x${region.height} @ ${region.x},${region.y}"
    override fun getAlignment(): Alignment = Alignment.CENTER
    override fun equals(other: Any?): Boolean = other is RegionGutterRenderer && other.region == region
    override fun hashCode(): Int = region.hashCode()
}

/**
 * Crops [region] out of the sheet and scales it to fit within [maxSize] px, upscaling at most 8x
 * (nearest-neighbour above 1x so pixel art stays crisp, bilinear when downscaling).
 */
internal object RegionIconFactory {

    private const val MAX_UPSCALE = 8.0

    fun create(image: BufferedImage, region: AtlasRegion, maxSize: Int): Icon {
        val rect = region.toImageRect().intersection(Rectangle(0, 0, image.width, image.height))
        if (rect.width <= 0 || rect.height <= 0) return EmptyIcon.ICON_16

        val sub = image.getSubimage(rect.x, rect.y, rect.width, rect.height)
        val ratio = min(maxSize.toDouble() / sub.width, maxSize.toDouble() / sub.height).coerceAtMost(MAX_UPSCALE)
        val width = max(1, (sub.width * ratio).roundToInt())
        val height = max(1, (sub.height * ratio).roundToInt())

        val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = scaled.createGraphics()
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            if (ratio > 1) RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            else RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        g.drawImage(sub, 0, 0, width, height, null)
        g.dispose()
        return ImageIcon(scaled)
    }
}
