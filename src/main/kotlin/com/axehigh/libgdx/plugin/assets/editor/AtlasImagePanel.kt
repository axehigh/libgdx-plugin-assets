package com.axehigh.libgdx.plugin.assets.editor

import com.axehigh.libgdx.plugin.assets.atlas.AtlasRegion
import com.axehigh.libgdx.plugin.assets.atlas.LibgdxAsset
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.datatransfer.StringSelection
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities
import javax.swing.ToolTipManager
import kotlin.math.max
import kotlin.math.min

/**
 * Renders a libGDX asset's PNG and, on hover, highlights the atlas region under the cursor.
 *
 * Read-only. Supports fit-to-window, cursor-anchored wheel zoom and drag panning, and reloads when
 * the PNG or atlas changes on disk.
 */
class AtlasImagePanel(
    private val project: Project,
    asset: LibgdxAsset,
) : JPanel(), Disposable {

    private var asset: LibgdxAsset = asset
    private var image: BufferedImage? = null
    private var disposed = false

    private var scale = 1.0
    private var offsetX = 0.0
    private var offsetY = 0.0
    private var fitPending = true
    private var userZoomed = false
    private var hovered: AtlasRegion? = null
    private var dragStart: Point? = null
    private var dragOriginX = 0.0
    private var dragOriginY = 0.0

    init {
        isOpaque = true
        background = UIUtil.getPanelBackground()
        toolTipText = "" // mark the component as having tooltips
        ToolTipManager.sharedInstance().registerComponent(this)

        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) = updateHover(e.point)
            override fun mouseDragged(e: MouseEvent) = panTo(e.point)
        })
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    showContextMenu(e)
                    return
                }
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragStart = e.point
                    dragOriginX = offsetX
                    dragOriginY = offsetY
                }
            }
            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) showContextMenu(e)
                dragStart = null
            }
            override fun mouseExited(e: MouseEvent) {
                if (hovered != null) {
                    hovered = null
                    repaint()
                }
            }
        })
        addMouseWheelListener { e: MouseWheelEvent -> zoomAt(e) }
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                if (!userZoomed) fitPending = true
            }
        })

        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { it.file == asset.png || it.file == asset.atlas }) reload()
            }
        })

        loadImage()
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val region = regionAtScreen(event.x, event.y) ?: return null
        return "${region.name}  ${region.width}x${region.height} @ ${region.x},${region.y}"
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            val img = image
            if (img == null) {
                drawStatus(g2, if (disposed) "" else "Loading ${asset.png.name}…")
                return
            }
            if (fitPending && width > 0 && height > 0) {
                fitToWindow(img)
                fitPending = false
            }
            g2.drawImage(img, offsetX.toInt(), offsetY.toInt(), (img.width * scale).toInt(), (img.height * scale).toInt(), null)
            hovered?.let { drawHighlight(g2, it) }
        } finally {
            g2.dispose()
        }
    }

    private fun drawHighlight(g2: Graphics2D, region: AtlasRegion) {
        val r = region.toImageRect()
        val x = (offsetX + r.x * scale).toInt()
        val y = (offsetY + r.y * scale).toInt()
        val w = max(1, (r.width * scale).toInt())
        val h = max(1, (r.height * scale).toInt())
        g2.color = Color(255, 193, 7, 60)
        g2.fillRect(x, y, w, h)
        g2.color = Color(255, 152, 0)
        g2.stroke = BasicStroke(2f)
        g2.drawRect(x, y, w, h)

        // Name (and bounds) label, kept on-screen and flipped inside the box when there is no room
        // above it.
        val label = "${region.name}  (${region.x},${region.y} ${region.width}x${region.height})"
        val metrics = g2.fontMetrics
        val pad = 3
        val labelW = metrics.stringWidth(label) + pad * 2
        val labelH = metrics.height + pad
        val lx = x.coerceIn(0, max(0, width - labelW))
        val ly = (if (y - labelH - 2 >= 0) y - labelH - 2 else y + 2)
            .coerceIn(0, max(0, height - labelH))
        g2.color = Color(0, 0, 0, 190)
        g2.fillRoundRect(lx, ly, labelW, labelH, 6, 6)
        g2.color = Color.WHITE
        g2.drawString(label, lx + pad, ly + metrics.ascent + pad / 2)
    }

    /** Right-click menu over a region: copy its name or bounds to the clipboard. */
    private fun showContextMenu(e: MouseEvent) {
        val region = regionAtScreen(e.x, e.y) ?: return
        hovered = region
        repaint()
        val menu = JPopupMenu()
        menu.add(JMenuItem("Copy Region Name").apply {
            addActionListener { CopyPasteManager.getInstance().setContents(StringSelection(region.name)) }
        })
        menu.add(JMenuItem("Copy Bounds").apply {
            addActionListener {
                CopyPasteManager.getInstance()
                    .setContents(StringSelection("${region.x},${region.y},${region.width},${region.height}"))
            }
        })
        menu.show(this, e.x, e.y)
    }

    private fun drawStatus(g2: Graphics2D, message: String) {
        if (message.isEmpty()) return
        g2.color = UIUtil.getLabelForeground()
        val metrics = g2.fontMetrics
        g2.drawString(message, (width - metrics.stringWidth(message)) / 2, height / 2)
    }

    private fun fitToWindow(img: BufferedImage) {
        val s = min(width.toDouble() / img.width, height.toDouble() / img.height).coerceAtMost(1.0)
        scale = if (s > 0) s else 1.0
        offsetX = (width - img.width * scale) / 2
        offsetY = (height - img.height * scale) / 2
    }

    private fun updateHover(point: Point) {
        val region = regionAtScreen(point.x, point.y)
        if (region != hovered) {
            hovered = region
            repaint()
        }
    }

    private fun panTo(point: Point) {
        val start = dragStart ?: return
        offsetX = dragOriginX + (point.x - start.x)
        offsetY = dragOriginY + (point.y - start.y)
        repaint()
    }

    private fun zoomAt(e: MouseWheelEvent) {
        val img = image ?: return
        val old = scale
        val factor = if (e.wheelRotation < 0) 1.1 else 1 / 1.1
        scale = (scale * factor).coerceIn(0.05, 16.0)
        val mx = e.x.toDouble()
        val my = e.y.toDouble()
        offsetX = mx - (mx - offsetX) * (scale / old)
        offsetY = my - (my - offsetY) * (scale / old)
        userZoomed = true
        fitPending = false
        // keep the image from being lost off-screen
        offsetX = offsetX.coerceIn(-img.width * scale + 16, width - 16.0)
        offsetY = offsetY.coerceIn(-img.height * scale + 16, height - 16.0)
        repaint()
    }

    private fun regionAtScreen(sx: Int, sy: Int): AtlasRegion? {
        val img = image ?: return null
        if (scale <= 0) return null
        val ix = ((sx - offsetX) / scale).toInt()
        val iy = ((sy - offsetY) / scale).toInt()
        if (ix < 0 || iy < 0 || ix >= img.width || iy >= img.height) return null
        return asset.data.regionAt(ix, iy)
    }

    private fun loadImage() {
        val png = asset.png
        ApplicationManager.getApplication().executeOnPooledThread {
            val loaded = runCatching { png.inputStream.use { ImageIO.read(it) } }.getOrNull()
            ApplicationManager.getApplication().invokeLater {
                if (disposed) return@invokeLater
                image = loaded
                fitPending = true
                userZoomed = false
                repaint()
            }
        }
    }

    private fun reload() {
        ApplicationManager.getApplication().invokeLater {
            if (disposed) return@invokeLater
            LibgdxAsset.forFile(asset.atlas)?.let { asset = it }
            image = null
            hovered = null
            loadImage()
        }
    }

    override fun dispose() {
        disposed = true
        ToolTipManager.sharedInstance().unregisterComponent(this)
    }

    override fun getPreferredSize(): Dimension =
        image?.let { Dimension(it.width, it.height) } ?: Dimension(400, 300)
}
