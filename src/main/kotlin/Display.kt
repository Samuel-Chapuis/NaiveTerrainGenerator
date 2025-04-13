import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JPanel
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// Extend view modes to include GRADIENT.
enum class ViewMode {
    GRAYSCALE, COLOR, GRADIENT
}

/**
 * Display class that shows an infinite grid of chunks with interactive panning, zooming, and view modes.
 * It now separates the drawing of the coordinate scales into their own panels so that the scales
 * are displayed on the side (right and bottom) rather than over the map.
 */
class Display(
    private val chunkGenerator: ChunkGenerator,
    private val seed: Int,
    private val map: Map,
    private val chunkSize: Int = 16
) : JFrame("Chunk Generator with Dynamic Global Map, Gradient, and Scale") {

    // Cache: store generated chunk images keyed by (chunkX, chunkY).
    private val generatedChunks = mutableMapOf<Pair<Int, Int>, Triple<BufferedImage, BufferedImage, BufferedImage>>()

    // Pan/zoom parameters.
    var zoomFactor = 1.0
    var offsetX = 0.0
    var offsetY = 0.0
    private var lastDragX = 0
    private var lastDragY = 0

    // Variables for left-drag selection.
    private var isSelecting = false
    private var leftDragStartX = 0
    private var leftDragStartY = 0
    private var leftDragCurrentX = 0
    private var leftDragCurrentY = 0

    // Current view mode.
    private var viewMode = ViewMode.GRAYSCALE

    // New flag to control grid visibility.
    private var showGrid = true

    // Reference to the drawing (map) panel.
    private val drawPanel: JPanel

    // Tick settings for scales.
    private val tickSpacingWorld = 100f  // world units

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        layout = BorderLayout()

        // Create the drawing panel for the map.
        drawPanel = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2d = g as Graphics2D

                // Apply pan/zoom transformations.
                g2d.translate(offsetX, offsetY)
                g2d.scale(zoomFactor, zoomFactor)

                // Determine visible region in world coordinates.
                val panelWidth = this.width
                val panelHeight = this.height
                val worldMinX = -offsetX / zoomFactor
                val worldMinY = -offsetY / zoomFactor
                val worldMaxX = (panelWidth - offsetX) / zoomFactor
                val worldMaxY = (panelHeight - offsetY) / zoomFactor

                // Derive the visible chunk indices.
                val visStartChunkX = floor(worldMinX / chunkSize).toInt()
                val visEndChunkX = ceil(worldMaxX / chunkSize).toInt() - 1
                val visStartChunkY = floor(worldMinY / chunkSize).toInt()
                val visEndChunkY = ceil(worldMaxY / chunkSize).toInt() - 1

                // Ensure the global maps cover at least the visible region.
                updateGlobalMapBounds(visStartChunkX, visEndChunkX, visStartChunkY, visEndChunkY)

                // Draw the visible chunks.
                for (cy in visStartChunkY..visEndChunkY) {
                    for (cx in visStartChunkX..visEndChunkX) {
                        val posX = cx * chunkSize
                        val posY = cy * chunkSize
                        val key = Pair(cx, cy)
                        if (generatedChunks.containsKey(key)) {
                            val image = when (viewMode) {
                                ViewMode.GRAYSCALE -> generatedChunks[key]!!.first
                                ViewMode.COLOR     -> generatedChunks[key]!!.second
                                ViewMode.GRADIENT  -> generatedChunks[key]!!.third
                            }
                            g2d.drawImage(image, posX, posY, null)

                            if (showGrid) {
                                val oldComposite = g2d.composite
                                g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)
                                g2d.color = Color.RED
                                g2d.drawRect(posX, posY, chunkSize, chunkSize)
                                g2d.composite = oldComposite
                            }
                        } else {
                            if (showGrid) {
                                g2d.color = Color.LIGHT_GRAY
                                g2d.fillRect(posX, posY, chunkSize, chunkSize)
                                g2d.color = Color.DARK_GRAY
                                g2d.drawRect(posX, posY, chunkSize, chunkSize)
                            } else {
                                // When grid is hidden, fill the area with a background color.
                                g2d.color = Color.LIGHT_GRAY
                                g2d.fillRect(posX, posY, chunkSize, chunkSize)
                            }
                        }
                    }
                }

                // Draw selection rectangle (in world coordinates).
                if (isSelecting) {
                    val startWorldX = (leftDragStartX - offsetX) / zoomFactor
                    val startWorldY = (leftDragStartY - offsetY) / zoomFactor
                    val currentWorldX = (leftDragCurrentX - offsetX) / zoomFactor
                    val currentWorldY = (leftDragCurrentY - offsetY) / zoomFactor
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
            }
        }
        drawPanel.preferredSize = Dimension(800, 600)

        // Create a dedicated horizontal scale panel (for the bottom).
        val horizontalScalePanel = object : JPanel() {
            init {
                preferredSize = Dimension(drawPanel.preferredSize.width, 30)
            }
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2d = g as Graphics2D
                g2d.color = Color.WHITE
                g2d.fillRect(0, 0, width, height)
                g2d.color = Color.BLACK

                val panelW = drawPanel.width
                val visibleWorldMinX = -offsetX / zoomFactor
                val visibleWorldMaxX = (panelW - offsetX) / zoomFactor

                val margin = 5
                g2d.drawLine(0, margin, width, margin)

                var tickX = ceil(visibleWorldMinX / tickSpacingWorld) * tickSpacingWorld
                while (tickX <= visibleWorldMaxX) {
                    val screenX = (tickX * zoomFactor + offsetX).toInt()
                    g2d.drawLine(screenX, margin - 5, screenX, margin + 5)
                    g2d.drawString(tickX.toInt().toString(), screenX - 10, margin + 20)
                    tickX += tickSpacingWorld
                }
            }
        }

        // Create a dedicated vertical scale panel (for the right side).
        val verticalScalePanel = object : JPanel() {
            init {
                preferredSize = Dimension(50, drawPanel.preferredSize.height)
            }
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2d = g as Graphics2D
                g2d.color = Color.WHITE
                g2d.fillRect(0, 0, width, height)
                g2d.color = Color.BLACK

                val panelH = drawPanel.height
                val visibleWorldMinY = -offsetY / zoomFactor
                val visibleWorldMaxY = (panelH - offsetY) / zoomFactor

                val margin = 5
                g2d.drawLine(margin, 0, margin, height)

                var tickY = ceil(visibleWorldMinY / tickSpacingWorld) * tickSpacingWorld
                while (tickY <= visibleWorldMaxY) {
                    val screenY = (tickY * zoomFactor + offsetY).toInt()
                    g2d.drawLine(margin - 5, screenY, margin + 5, screenY)
                    g2d.drawString(tickY.toInt().toString(), margin + 8, screenY + 5)
                    tickY += tickSpacingWorld
                }
            }
        }

        // Add mouse listeners to drawPanel (for panning, chunk generation, etc.).
        drawPanel.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                when (e.button) {
                    MouseEvent.BUTTON1 -> {
                        leftDragStartX = e.x
                        leftDragStartY = e.y
                        leftDragCurrentX = e.x
                        leftDragCurrentY = e.y
                        isSelecting = true
                    }
                    MouseEvent.BUTTON3 -> {
                        lastDragX = e.x
                        lastDragY = e.y
                    }
                }
            }
            override fun mouseReleased(e: MouseEvent) {
                when (e.button) {
                    MouseEvent.BUTTON1 -> {
                        if (isSelecting) {
                            leftDragCurrentX = e.x
                            leftDragCurrentY = e.y
                            val dragDistX = kotlin.math.abs(leftDragCurrentX - leftDragStartX)
                            val dragDistY = kotlin.math.abs(leftDragCurrentY - leftDragStartY)
                            if (dragDistX < 5 && dragDistY < 5) {
                                val worldX = (e.x - offsetX) / zoomFactor
                                val worldY = (e.y - offsetY) / zoomFactor
                                val chunkX = floor(worldX / chunkSize).toInt()
                                val chunkY = floor(worldY / chunkSize).toInt()
                                generateChunkIfNeeded(chunkX, chunkY)
                            } else {
                                val startWorldX = (leftDragStartX - offsetX) / zoomFactor
                                val startWorldY = (leftDragStartY - offsetY) / zoomFactor
                                val endWorldX = (e.x - offsetX) / zoomFactor
                                val endWorldY = (e.y - offsetY) / zoomFactor
                                val chunkStartX = floor(min(startWorldX, endWorldX) / chunkSize).toInt()
                                val chunkEndX = ceil(max(startWorldX, endWorldX) / chunkSize).toInt() - 1
                                val chunkStartY = floor(min(startWorldY, endWorldY) / chunkSize).toInt()
                                val chunkEndY = ceil(max(startWorldY, endWorldY) / chunkSize).toInt() - 1
                                for (cy in chunkStartY..chunkEndY) {
                                    for (cx in chunkStartX..chunkEndX) {
                                        generateChunkIfNeeded(cx, cy)
                                    }
                                }
                            }
                            isSelecting = false
                            repaintAllPanels()
                        }
                    }
                }
            }
        })
        drawPanel.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                if ((e.modifiersEx and MouseEvent.BUTTON1_DOWN_MASK) != 0) {
                    leftDragCurrentX = e.x
                    leftDragCurrentY = e.y
                    repaintAllPanels()
                }
                if ((e.modifiersEx and MouseEvent.BUTTON3_DOWN_MASK) != 0) {
                    val dx = e.x - lastDragX
                    val dy = e.y - lastDragY
                    offsetX += dx
                    offsetY += dy
                    lastDragX = e.x
                    lastDragY = e.y
                    repaintAllPanels()
                }
            }
        })
        drawPanel.addMouseWheelListener(object : MouseWheelListener {
            override fun mouseWheelMoved(e: MouseWheelEvent) {
                val oldZoom = zoomFactor
                val zoomChange = if (e.wheelRotation < 0) 1.1 else 0.9
                zoomFactor *= zoomChange
                offsetX = e.x - ((e.x - offsetX) / oldZoom) * zoomFactor
                offsetY = e.y - ((e.y - offsetY) / oldZoom) * zoomFactor
                repaintAllPanels()
            }
        })

        // Create buttons for view mode selection.
        val grayButton = JButton("Grayscale View")
        grayButton.addActionListener {
            viewMode = ViewMode.GRAYSCALE
            repaintAllPanels()
        }
        val colorButton = JButton("Color View")
        colorButton.addActionListener {
            viewMode = ViewMode.COLOR
            repaintAllPanels()
        }
        val gradientButton = JButton("Gradient View")
        gradientButton.addActionListener {
            viewMode = ViewMode.GRADIENT
            repaintAllPanels()
        }
        // New button to toggle grid visibility.
        val toggleGridButton = JButton("Toggle Grid")
        toggleGridButton.addActionListener {
            showGrid = !showGrid
            repaintAllPanels()
        }
        // Button to show the global map in the current view mode.
        val showGlobalButton = JButton("Show Global Map")
        showGlobalButton.addActionListener {
            val globalImage = when(viewMode) {
                ViewMode.GRAYSCALE -> createGlobalHeightMapImage()
                ViewMode.COLOR     -> createGlobalColorMapImage()
                ViewMode.GRADIENT  -> createGlobalGradientMapImage()
            }
            val mapFrame = JFrame("Global Map - ${viewMode.name}")
            mapFrame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            val panel = object : JPanel() {
                override fun paintComponent(g: Graphics) {
                    super.paintComponent(g)
                    g.drawImage(globalImage, 0, 0, null)
                }
            }
            panel.preferredSize = Dimension(globalImage.width, globalImage.height)
            mapFrame.add(panel)
            mapFrame.pack()
            mapFrame.setLocationRelativeTo(null)
            mapFrame.isVisible = true
        }
        // Arrange buttons in the top panel.
        val topPanel = JPanel().apply {
            add(grayButton)
            add(colorButton)
            add(gradientButton)
            add(toggleGridButton)   // <-- new grid toggle button added here.
            add(showGlobalButton)
        }

        // Set up the layout: top for buttons, center for map, south for horizontal scale, east for vertical scale.
        add(topPanel, BorderLayout.NORTH)
        add(drawPanel, BorderLayout.CENTER)
        add(horizontalScalePanel, BorderLayout.SOUTH)
        add(verticalScalePanel, BorderLayout.EAST)
        pack()
        setLocationRelativeTo(null)
        isVisible = true
    }

    // Helper to repaint all panels.
    private fun repaintAllPanels() {
        drawPanel.repaint()
        this.repaint()
    }

    // --- (Keep the rest of your helper functions unchanged) ---

    private fun generateChunkIfNeeded(chunkX: Int, chunkY: Int) {
        val key = Pair(chunkX, chunkY)
        if (!generatedChunks.containsKey(key)) {
            val (chunkNoise, chunkGradient) = chunkGenerator.generateChunkNoiseAndGradient(
                chunkX = chunkX,
                chunkY = chunkY,
                seed = seed,
                chunkSize = chunkSize
            )
            val grayImg = BufferedImage(chunkSize, chunkSize, BufferedImage.TYPE_INT_RGB)
            val colorImg = BufferedImage(chunkSize, chunkSize, BufferedImage.TYPE_INT_RGB)
            val gradImg = BufferedImage(chunkSize, chunkSize, BufferedImage.TYPE_INT_RGB)
            for (i in 0 until chunkSize) {
                for (j in 0 until chunkSize) {
                    val value = chunkNoise[i][j]
                    val grayRGB = Color(value, value, value).rgb
                    grayImg.setRGB(j, i, grayRGB)
                    val colorRGB = when (value) {
                        in 0..32   -> Color(0, 0, 150).rgb
                        in 33..64  -> Color(0, 0, 255).rgb
                        in 65..128 -> Color(85, 220, 85).rgb
                        in 129..192-> Color(200, 200, 200).rgb
                        in 193..255-> Color(255, 255, 255).rgb
                        else       -> grayRGB
                    }
                    colorImg.setRGB(j, i, colorRGB)
                    val (dx, dy) = chunkGradient[i][j]
                    val red = (((dx + 1) / 2) * 255).toInt().coerceIn(0, 255)
                    val blue = (((dy + 1) / 2) * 255).toInt().coerceIn(0, 255)
                    gradImg.setRGB(j, i, Color(red, 0, blue).rgb)
                }
            }
            generatedChunks[key] = Triple(grayImg, colorImg, gradImg)
            updateGlobalMapBounds(chunkX, chunkX, chunkY, chunkY)
            updateMapHeightMap(chunkX, chunkY, chunkNoise)
            updateMapGradientMap(chunkX, chunkY, chunkGradient)
        }
    }

    private fun updateMapHeightMap(chunkX: Int, chunkY: Int, chunkNoise: Array<IntArray>) {
        val offsetXInMap = (chunkX - map.minChunkX) * chunkSize
        val offsetYInMap = (chunkY - map.minChunkY) * chunkSize
        for (i in 0 until chunkSize) {
            for (j in 0 until chunkSize) {
                map.height_map[offsetYInMap + i][offsetXInMap + j] = chunkNoise[i][j]
            }
        }
    }

    private fun updateMapGradientMap(chunkX: Int, chunkY: Int, chunkGradient: Array<Array<Pair<Float, Float>>>) {
        val offsetXInMap = (chunkX - map.minChunkX) * chunkSize
        val offsetYInMap = (chunkY - map.minChunkY) * chunkSize
        for (i in 0 until chunkSize) {
            for (j in 0 until chunkSize) {
                val (dx, dy) = chunkGradient[i][j]
                val red = (((dx + 1) / 2) * 255).toInt().coerceIn(0, 255)
                val blue = (((dy + 1) / 2) * 255).toInt().coerceIn(0, 255)
                map.gradiant_map[offsetYInMap + i][offsetXInMap + j] = Color(red, 0, blue).rgb
            }
        }
    }

    private fun updateGlobalMapBounds(newMinCX: Int, newMaxCX: Int, newMinCY: Int, newMaxCY: Int) {
        if (map.height_map.isEmpty()) {
            map.minChunkX = newMinCX
            map.maxChunkX = newMaxCX
            map.minChunkY = newMinCY
            map.maxChunkY = newMaxCY
            val width = (map.maxChunkX - map.minChunkX + 1) * chunkSize
            val height = (map.maxChunkY - map.minChunkY + 1) * chunkSize
            map.height_map = Array(height) { IntArray(width) { 0 } }
            map.gradiant_map = Array(height) { IntArray(width) { 0 } }
            return
        }
        val curMinCX = map.minChunkX
        val curMaxCX = map.maxChunkX
        val curMinCY = map.minChunkY
        val curMaxCY = map.maxChunkY
        val updatedMinCX = min(curMinCX, newMinCX)
        val updatedMaxCX = max(curMaxCX, newMaxCX)
        val updatedMinCY = min(curMinCY, newMinCY)
        val updatedMaxCY = max(curMaxCY, newMaxCY)
        if (updatedMinCX != curMinCX || updatedMaxCX != curMaxCX ||
            updatedMinCY != curMinCY || updatedMaxCY != curMaxCY) {
            val newWidth = (updatedMaxCX - updatedMinCX + 1) * chunkSize
            val newHeight = (updatedMaxCY - updatedMinCY + 1) * chunkSize
            val newHeightMap = Array(newHeight) { IntArray(newWidth) { 0 } }
            val newGradientMap = Array(newHeight) { IntArray(newWidth) { 0 } }
            val offsetXCopy = (curMinCX - updatedMinCX) * chunkSize
            val offsetYCopy = (curMinCY - updatedMinCY) * chunkSize
            for (y in map.height_map.indices) {
                for (x in map.height_map[y].indices) {
                    newHeightMap[y + offsetYCopy][x + offsetXCopy] = map.height_map[y][x]
                    newGradientMap[y + offsetYCopy][x + offsetXCopy] = map.gradiant_map[y][x]
                }
            }
            map.minChunkX = updatedMinCX
            map.maxChunkX = updatedMaxCX
            map.minChunkY = updatedMinCY
            map.maxChunkY = updatedMaxCY
            map.height_map = newHeightMap
            map.gradiant_map = newGradientMap
        }
    }

    private fun createGlobalHeightMapImage(): BufferedImage {
        if (map.height_map.isEmpty()) throw IllegalStateException("Global height map is empty")
        val rows = map.height_map.size
        val cols = map.height_map[0].size
        val image = BufferedImage(cols, rows, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val value = map.height_map[y][x]
                image.setRGB(x, y, Color(value, value, value).rgb)
            }
        }
        return image
    }

    private fun createGlobalGradientMapImage(): BufferedImage {
        if (map.gradiant_map.isEmpty()) throw IllegalStateException("Global gradient map is empty")
        val rows = map.gradiant_map.size
        val cols = map.gradiant_map[0].size
        val image = BufferedImage(cols, rows, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                image.setRGB(x, y, map.gradiant_map[y][x])
            }
        }
        return image
    }

    private fun createGlobalColorMapImage(): BufferedImage {
        if (map.height_map.isEmpty()) throw IllegalStateException("Global height map is empty")
        val rows = map.height_map.size
        val cols = map.height_map[0].size
        val image = BufferedImage(cols, rows, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val value = map.height_map[y][x]
                val colorRGB = when (value) {
                    in 0..32   -> Color(0, 0, 150).rgb
                    in 33..64  -> Color(0, 0, 255).rgb
                    in 65..128 -> Color(85, 220, 85).rgb
                    in 129..192-> Color(200, 200, 200).rgb
                    in 193..255-> Color(255, 255, 255).rgb
                    else       -> Color(value, value, value).rgb
                }
                image.setRGB(x, y, colorRGB)
            }
        }
        return image
    }
}
