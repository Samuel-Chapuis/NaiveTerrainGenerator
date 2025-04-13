package DisplayPack

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import javax.swing.JPanel
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * MapPanel is responsible for drawing the map (chunks), selection rectangle, and handling mouse events.
 * It accesses the current state (offset, zoom, view mode, grid toggle, etc.) from its parent Display.
 */
class MapPanel(val display: Display) : JPanel() {

    init {
        preferredSize = display.panelDimension

        // Mouse listeners for panning, chunk generation, and selection.
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                when (e.button) {
                    MouseEvent.BUTTON1 -> {
                        display.leftDragStartX = e.x
                        display.leftDragStartY = e.y
                        display.leftDragCurrentX = e.x
                        display.leftDragCurrentY = e.y
                        display.isSelecting = true
                    }
                    MouseEvent.BUTTON3 -> {
                        display.lastDragX = e.x
                        display.lastDragY = e.y
                    }
                }
            }
            override fun mouseReleased(e: MouseEvent) {
                when (e.button) {
                    MouseEvent.BUTTON1 -> {
                        if (display.isSelecting) {
                            display.leftDragCurrentX = e.x
                            display.leftDragCurrentY = e.y
                            val dragDistX = kotlin.math.abs(display.leftDragCurrentX - display.leftDragStartX)
                            val dragDistY = kotlin.math.abs(display.leftDragCurrentY - display.leftDragStartY)
                            if (dragDistX < 5 && dragDistY < 5) {
                                val worldX = (e.x - display.offsetX) / display.zoomFactor
                                val worldY = (e.y - display.offsetY) / display.zoomFactor
                                val chunkX = floor(worldX / display.chunkSize).toInt()
                                val chunkY = floor(worldY / display.chunkSize).toInt()
                                display.generateChunkIfNeeded(chunkX, chunkY)
                            } else {
                                val startWorldX = (display.leftDragStartX - display.offsetX) / display.zoomFactor
                                val startWorldY = (display.leftDragStartY - display.offsetY) / display.zoomFactor
                                val endWorldX = (e.x - display.offsetX) / display.zoomFactor
                                val endWorldY = (e.y - display.offsetY) / display.zoomFactor
                                val chunkStartX = floor(min(startWorldX, endWorldX) / display.chunkSize).toInt()
                                val chunkEndX = ceil(max(startWorldX, endWorldX) / display.chunkSize).toInt() - 1
                                val chunkStartY = floor(min(startWorldY, endWorldY) / display.chunkSize).toInt()
                                val chunkEndY = ceil(max(startWorldY, endWorldY) / display.chunkSize).toInt() - 1
                                for (cy in chunkStartY..chunkEndY) {
                                    for (cx in chunkStartX..chunkEndX) {
                                        display.generateChunkIfNeeded(cx, cy)
                                    }
                                }
                            }
                            display.isSelecting = false
                            display.repaintAllPanels()
                        }
                    }
                }
            }
        })
        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                if ((e.modifiersEx and MouseEvent.BUTTON1_DOWN_MASK) != 0) {
                    display.leftDragCurrentX = e.x
                    display.leftDragCurrentY = e.y
                    display.repaintAllPanels()
                }
                if ((e.modifiersEx and MouseEvent.BUTTON3_DOWN_MASK) != 0) {
                    val dx = e.x - display.lastDragX
                    val dy = e.y - display.lastDragY
                    display.offsetX += dx
                    display.offsetY += dy
                    display.lastDragX = e.x
                    display.lastDragY = e.y
                    display.repaintAllPanels()
                }
            }
        })
        addMouseWheelListener(object : MouseWheelListener {
            override fun mouseWheelMoved(e: MouseWheelEvent) {
                val oldZoom = display.zoomFactor
                val zoomChange = if (e.wheelRotation < 0) 1.1 else 0.9
                display.zoomFactor *= zoomChange
                display.offsetX = e.x - ((e.x - display.offsetX) / oldZoom) * display.zoomFactor
                display.offsetY = e.y - ((e.y - display.offsetY) / oldZoom) * display.zoomFactor
                display.repaintAllPanels()
            }
        })
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        // Save original transform.
        val originalTransform: AffineTransform = g2d.transform

        // Apply pan/zoom transformations.
        g2d.translate(display.offsetX, display.offsetY)
        g2d.scale(display.zoomFactor, display.zoomFactor)

        // Determine visible region.
        val panelWidth = width
        val panelHeight = height
        val worldMinX = -display.offsetX / display.zoomFactor
        val worldMinY = -display.offsetY / display.zoomFactor
        val worldMaxX = (panelWidth - display.offsetX) / display.zoomFactor
        val worldMaxY = (panelHeight - display.offsetY) / display.zoomFactor

        // Calculate visible chunk indices.
        val visStartChunkX = floor(worldMinX / display.chunkSize).toInt()
        val visEndChunkX = ceil(worldMaxX / display.chunkSize).toInt() - 1
        val visStartChunkY = floor(worldMinY / display.chunkSize).toInt()
        val visEndChunkY = ceil(worldMaxY / display.chunkSize).toInt() - 1

        // Make sure the global map bounds are updated.
        display.updateGlobalMapBounds(visStartChunkX, visEndChunkX, visStartChunkY, visEndChunkY)

        // Draw each visible chunk.
        for (cy in visStartChunkY..visEndChunkY) {
            for (cx in visStartChunkX..visEndChunkX) {
                val posX = cx * display.chunkSize
                val posY = cy * display.chunkSize
                val key = Pair(cx, cy)
                if (display.generatedChunks.containsKey(key)) {
                    // Select image based on current view mode.
                    val image: BufferedImage = when (display.viewMode) {
                        ViewMode.GRAYSCALE -> display.generatedChunks[key]!!.first
                        ViewMode.COLOR     -> display.generatedChunks[key]!!.second
                        ViewMode.GRADIENT  -> display.generatedChunks[key]!!.third
                    }
                    g2d.drawImage(image, posX, posY, null)
                    if (display.showGrid) {
                        val oldComposite = g2d.composite
                        g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)
                        g2d.color = Color.RED
                        g2d.drawRect(posX, posY, display.chunkSize, display.chunkSize)
                        g2d.composite = oldComposite
                    }
                } else {
                    if (display.showGrid) {
                        g2d.color = Color.LIGHT_GRAY
                        g2d.fillRect(posX, posY, display.chunkSize, display.chunkSize)
                        g2d.color = Color.DARK_GRAY
                        g2d.drawRect(posX, posY, display.chunkSize, display.chunkSize)
                    } else {
                        g2d.color = Color.LIGHT_GRAY
                        g2d.fillRect(posX, posY, display.chunkSize, display.chunkSize)
                    }
                }
            }
        }

        // Draw the selection rectangle, if any.
        if (display.isSelecting) {
            val startWorldX = (display.leftDragStartX - display.offsetX) / display.zoomFactor
            val startWorldY = (display.leftDragStartY - display.offsetY) / display.zoomFactor
            val currentWorldX = (display.leftDragCurrentX - display.offsetX) / display.zoomFactor
            val currentWorldY = (display.leftDragCurrentY - display.offsetY) / display.zoomFactor
            val rectX = min(startWorldX, currentWorldX)
            val rectY = min(startWorldY, currentWorldY)
            val rectWidth = max(startWorldX, currentWorldX) - rectX
            val rectHeight = max(startWorldY, currentWorldY) - rectY
            val oldComposite = g2d.composite
            g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f)
            g2d.color = Color.BLUE
            g2d.fillRect(rectX.toInt(), rectY.toInt(), rectWidth.toInt(), rectHeight.toInt())
            g2d.composite = oldComposite
            g2d.drawRect(rectX.toInt(), rectY.toInt(), rectWidth.toInt(), rectHeight.toInt())
        }
        // Restore the original transform.
        g2d.transform = originalTransform
    }
}