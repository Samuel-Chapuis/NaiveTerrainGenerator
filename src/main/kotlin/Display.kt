import java.awt.AlphaComposite
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import java.awt.image.BufferedImage
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// Extend view modes to include GRADIENT.
enum class ViewMode {
    GRAYSCALE, COLOR, GRADIENT
}

/**
 * Display class that shows an infinite grid of chunks with interactive panning, zooming,
 * and a set of buttons to select one of three view modes: Grayscale, Color, or Gradient.
 *
 * When a chunk is generated (via click or drag), its noise data (height map and gradient)
 * are produced via noise.generateChunkNoiseAndGradient and three images are created for local display.
 * In addition, the global Map's height_map and gradiant_map are updated dynamically.
 *
 * The "Show Global Map" button displays a new window that shows the global map
 * corresponding to the current view mode.
 *
 * @param noise     An instance of your Noise class.
 * @param seed      The seed used for noise generation.
 * @param map       The Map instance to be updated. (Map must have fields:
 *                  minChunkX, maxChunkX, minChunkY, maxChunkY, height_map, gradiant_map.)
 * @param chunkSize The size (in pixels) of each chunk.
 */
class Display(
    private val chunkGenerator: ChunkGenerator,
    private val seed: Int,
    private val map: Map,
    private val chunkSize: Int = 16
) : JFrame("Chunk Generator with Dynamic Global Map and Gradient View") {

    // Cache: store generated chunk images keyed by (chunkX, chunkY).
    // Each entry holds a Triple: (grayscale image, color image, gradient image).
    private val generatedChunks = mutableMapOf<Pair<Int, Int>, Triple<BufferedImage, BufferedImage, BufferedImage>>()

    // Pan/zoom parameters.
    private var zoomFactor = 1.0
    private var offsetX = 0.0
    private var offsetY = 0.0
    private var lastDragX = 0
    private var lastDragY = 0

    // Left-drag selection variables.
    private var isSelecting = false
    private var leftDragStartX = 0
    private var leftDragStartY = 0
    private var leftDragCurrentX = 0
    private var leftDragCurrentY = 0

    // Current view mode.
    private var viewMode = ViewMode.GRAYSCALE

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        layout = BorderLayout()

        // Create the drawing panel.
        val drawPanel = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2d = g as Graphics2D

                // Apply pan and zoom transformations.
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

                // Loop over visible chunks.
                for (cy in visStartChunkY..visEndChunkY) {
                    for (cx in visStartChunkX..visEndChunkX) {
                        val posX = cx * chunkSize
                        val posY = cy * chunkSize
                        val key = Pair(cx, cy)
                        if (generatedChunks.containsKey(key)) {
                            // Choose image according to current view mode.
                            val image = when(viewMode) {
                                ViewMode.GRAYSCALE -> generatedChunks[key]!!.first
                                ViewMode.COLOR     -> generatedChunks[key]!!.second
                                ViewMode.GRADIENT  -> generatedChunks[key]!!.third
                            }
                            g2d.drawImage(image, posX, posY, null)
                            // Draw a semi-transparent red grid.
                            val oldComposite = g2d.composite
                            g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)
                            g2d.color = Color.RED
                            g2d.drawRect(posX, posY, chunkSize, chunkSize)
                            g2d.composite = oldComposite
                        } else {
                            // Draw placeholder cell.
                            g2d.color = Color.LIGHT_GRAY
                            g2d.fillRect(posX, posY, chunkSize, chunkSize)
                            g2d.color = Color.DARK_GRAY
                            g2d.drawRect(posX, posY, chunkSize, chunkSize)
                        }
                    }
                }

                // Draw the selection rectangle if active.
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

        // Mouse listeners for generation (click/drag) and panning.
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
                                // Single-click: generate one chunk.
                                val worldX = (e.x - offsetX) / zoomFactor
                                val worldY = (e.y - offsetY) / zoomFactor
                                val chunkX = floor(worldX / chunkSize).toInt()
                                val chunkY = floor(worldY / chunkSize).toInt()
                                generateChunkIfNeeded(chunkX, chunkY)
                            } else {
                                // Drag selection: determine bounds and generate all chunks in area.
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
                            drawPanel.repaint()
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
                    drawPanel.repaint()
                }
                if ((e.modifiersEx and MouseEvent.BUTTON3_DOWN_MASK) != 0) {
                    val dx = e.x - lastDragX
                    val dy = e.y - lastDragY
                    offsetX += dx
                    offsetY += dy
                    lastDragX = e.x
                    lastDragY = e.y
                    drawPanel.repaint()
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
                drawPanel.repaint()
            }
        })

        // Create buttons for view mode selection.
        val grayButton = JButton("Grayscale View")
        grayButton.addActionListener {
            viewMode = ViewMode.GRAYSCALE
            drawPanel.repaint()
        }
        val colorButton = JButton("Color View")
        colorButton.addActionListener {
            viewMode = ViewMode.COLOR
            drawPanel.repaint()
        }
        val gradientButton = JButton("Gradient View")
        gradientButton.addActionListener {
            viewMode = ViewMode.GRADIENT
            drawPanel.repaint()
        }
        // The Show Global Map button now shows the global map in the mode matching the current view.
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
        // Top panel holds the view buttons and the global map button.
        val topPanel = JPanel().apply {
            add(grayButton)
            add(colorButton)
            add(gradientButton)
            add(showGlobalButton)
        }

        add(topPanel, BorderLayout.NORTH)
        add(drawPanel, BorderLayout.CENTER)
        pack()
        setLocationRelativeTo(null)
        isVisible = true
    }

    /**
     * Generates a chunk at (chunkX, chunkY) if not already generated.
     * Uses noise.generateChunkNoiseAndGradient to get both the height map and gradient map.
     * Produces three images for display (grayscale, color, gradient),
     * and updates the global maps.
     */
    private fun generateChunkIfNeeded(chunkX: Int, chunkY: Int) {
        val key = Pair(chunkX, chunkY)
        if (!generatedChunks.containsKey(key)) {
            // Obtain both noise and gradient using the Noise class.
            val (chunkNoise, chunkGradient) = chunkGenerator.generateChunkNoiseAndGradient(
                chunkX = chunkX,
                chunkY = chunkY,
                seed = seed,
                chunkSize = chunkSize
            )
            // Create three images for display.
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
                    // For gradient image, map dx to red and dy to blue.
                    val (dx, dy) = chunkGradient[i][j]
                    val red = (((dx + 1) / 2) * 255).toInt().coerceIn(0,255)
                    val blue = (((dy + 1) / 2) * 255).toInt().coerceIn(0,255)
                    // Set green to 0.
                    gradImg.setRGB(j, i, Color(red, 0, blue).rgb)
                }
            }
            generatedChunks[key] = Triple(grayImg, colorImg, gradImg)
            // Update the global maps.
            updateGlobalMapBounds(chunkX, chunkX, chunkY, chunkY)
            updateMapHeightMap(chunkX, chunkY, chunkNoise)
            updateMapGradientMap(chunkX, chunkY, chunkGradient)
        }
    }

    /**
     * Copies the chunk noise data into the appropriate region of Map.height_map.
     */
    private fun updateMapHeightMap(chunkX: Int, chunkY: Int, chunkNoise: Array<IntArray>) {
        val offsetXInMap = (chunkX - map.minChunkX) * chunkSize
        val offsetYInMap = (chunkY - map.minChunkY) * chunkSize
        for (i in 0 until chunkSize) {
            for (j in 0 until chunkSize) {
                map.height_map[offsetYInMap + i][offsetXInMap + j] = chunkNoise[i][j]
            }
        }
    }

    /**
     * Copies the chunk gradient data into the appropriate region of Map.gradiant_map.
     * The gradient is converted to a color with the red channel representing dx and the blue channel representing dy.
     */
    private fun updateMapGradientMap(chunkX: Int, chunkY: Int, chunkGradient: Array<Array<Pair<Float, Float>>>) {
        val offsetXInMap = (chunkX - map.minChunkX) * chunkSize
        val offsetYInMap = (chunkY - map.minChunkY) * chunkSize
        for (i in 0 until chunkSize) {
            for (j in 0 until chunkSize) {
                val (dx, dy) = chunkGradient[i][j]
                val red = (((dx + 1) / 2) * 255).toInt().coerceIn(0,255)
                val blue = (((dy + 1) / 2) * 255).toInt().coerceIn(0,255)
                map.gradiant_map[offsetYInMap + i][offsetXInMap + j] = Color(red, 0, blue).rgb
            }
        }
    }

    /**
     * Updates the global map boundaries to include chunks from newMinCX to newMaxCX and newMinCY to newMaxCY.
     * Reallocates map.height_map and map.gradiant_map if bounds change and copies over existing data.
     */
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

    /**
     * Creates a BufferedImage from the global height_map (grayscale).
     */
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

    /**
     * Creates a BufferedImage from the global gradiant_map.
     */
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

    /**
     * Creates a BufferedImage representing the global map in color view.
     *
     * This function converts the global height_map values into color using the same thresholds
     * as used in chunk generation.
     */
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
