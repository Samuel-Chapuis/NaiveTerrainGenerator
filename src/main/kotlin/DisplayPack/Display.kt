package DisplayPack

import Map
import ChunkGenerator
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JPanel
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Display creates the main application frame.
 * It holds shared state (pan, zoom, view mode, etc.) and helper methods (for chunk generation, updating maps, and creating global images).
 * It instantiates the MapPanel, scale panels, and creates the control buttons.
 */
class Display(
    val chunkGenerator: ChunkGenerator,
    val seed: Int,
    val map: Map,
    val chunkSize: Int = 16
) : JFrame("Chunk Generator with Dynamic Global Map, Gradient, and Scale") {

    // Shared state variables.
    var zoomFactor = 1.0
    var offsetX = 0.0
    var offsetY = 0.0
    var viewMode = ViewMode.GRAYSCALE
    var showGrid = true

    // Cache: store generated chunk images keyed by (chunkX, chunkY).
    // The Triple holds: (grayscale image, color image with contour overlay, gradient image)
    val generatedChunks = mutableMapOf<Pair<Int, Int>, Triple<BufferedImage, BufferedImage, BufferedImage>>()

    // Variables for mouse-drag selection.
    var isSelecting = false
    var leftDragStartX = 0
    var leftDragStartY = 0
    var leftDragCurrentX = 0
    var leftDragCurrentY = 0
    var lastDragX = 0
    var lastDragY = 0

    // Dimensions used by panels.
    val panelDimension = Dimension(800, 600)
    val horizontalScaleDimension = Dimension(panelDimension.width, 30)
    val verticalScaleDimension = Dimension(50, panelDimension.height)

    // Tick spacing (world units)
    val tickSpacingWorld = 100f

    // Instances of custom panels.
    val mapPanel: MapPanel = MapPanel(this)

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        layout = BorderLayout()

        // Create scale panels.
        val horizontalScalePanel = HorizontalScalePanel(this)
        val verticalScalePanel = VerticalScalePanel(this)

        // Create control buttons.
        val grayButton = JButton("Grayscale View").apply {
            addActionListener {
                viewMode = ViewMode.GRAYSCALE
                repaintAllPanels()
            }
        }
        val colorButton = JButton("Color View").apply {
            addActionListener {
                viewMode = ViewMode.COLOR
                repaintAllPanels()
            }
        }
        val gradientButton = JButton("Gradient View").apply {
            addActionListener {
                viewMode = ViewMode.GRADIENT
                repaintAllPanels()
            }
        }
        val toggleGridButton = JButton("Toggle Grid").apply {
            addActionListener {
                showGrid = !showGrid
                repaintAllPanels()
            }
        }

        val histButton = JButton("Show Height Distribution").apply {
            addActionListener { showHeightDistribution() }
        }

        val exportButton = JButton("Export Global Map").apply {
            addActionListener {
                // Create the global map image based on the current view mode.
                var globalImage: BufferedImage = when (viewMode) {
                    ViewMode.GRAYSCALE -> createGlobalHeightMapImage()
                    ViewMode.COLOR     -> createGlobalColorMapImage()
                    ViewMode.GRADIENT  -> createGlobalGradientMapImage()
                }
                // In COLOR view, overlay contour lines.
                if (viewMode == ViewMode.COLOR) {
                    globalImage = applyContourLines(globalImage, map.height_map)
                }
                // Save the global image as a JPEG in the "out" folder.
                try {
                    val outputDirectory = File("out")
                    if (!outputDirectory.exists()) {
                        outputDirectory.mkdirs()
                    }
                    val outputFile = File(outputDirectory, "globalMap_${viewMode.name}.jpg")
                    ImageIO.write(globalImage, "jpg", outputFile)
                    println("Export successful: ${outputFile.absolutePath}")
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
        // Top panel holds all control buttons.
        val topPanel = JPanel().apply {
            add(grayButton)
            add(colorButton)
            add(gradientButton)
            add(toggleGridButton)
            add(histButton)
            add(exportButton)
        }

        // Add components to main frame.
        add(topPanel, BorderLayout.NORTH)
        add(mapPanel, BorderLayout.CENTER)
        add(horizontalScalePanel, BorderLayout.SOUTH)
        add(verticalScalePanel, BorderLayout.EAST)

        pack()
        setLocationRelativeTo(null)
        isVisible = true
    }

    // Repaints the map and all panels.
    fun repaintAllPanels() {
        mapPanel.repaint()
        repaint()
    }


    private fun showHeightDistribution() {
        // Count non‑zero points only
        val rows = map.height_map.size
        val cols = map.height_map[0].size
        val counts = DoubleArray(256) { 0.0 }
        var totalPoints = 0

        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val h = map.height_map[y][x].coerceIn(0, 255)
                if (h == 0) continue                // ← skip zeros
                counts[h] += 1.0
                totalPoints++
            }
        }
        if (totalPoints == 0) return             // nothing to show

        // Normalize over non‑zero points
        val proportions = counts.map { it / totalPoints }.toDoubleArray()

        // Build and show the histogram frame as before
        val frame = JFrame("Height Distribution (excluding 0)")
        frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        frame.contentPane.add(HistogramPanel(proportions))
        frame.setSize(800, 400)
        frame.setLocationRelativeTo(this)
        frame.isVisible = true
    }

    class HistogramPanel(data: DoubleArray) : JPanel() {
        private val proportions = data
        private var zoom = 1.0
        private var offsetX = 0.0
        private var offsetY = 0.0
        private var lastDragX = 0
        private var lastDragY = 0

        // Precompute quartiles & mean
        private val cum = DoubleArray(proportions.size).also { arr ->
            proportions.foldIndexed(0.0) { i, acc, v -> arr[i] = acc + v; acc + v }
        }
        private val q1 = cum.indexOfFirst { it >= 0.25 }.coerceAtLeast(0)
        private val median = cum.indexOfFirst { it >= 0.5 }.coerceAtLeast(0)
        private val q3 = cum.indexOfFirst { it >= 0.75 }.coerceAtLeast(0)
        private val mean = proportions.mapIndexed { i, v -> i * v }.sum()

        init {
            // Mouse wheel zoom
            addMouseWheelListener { e ->
                val factor = if (e.wheelRotation < 0) 1.1 else 1/1.1
                val px = e.x.toDouble(); val py = e.y.toDouble()
                offsetX = px - factor * (px - offsetX)
                offsetY = py - factor * (py - offsetY)
                zoom *= factor
                repaint()
            }
            // Mouse press for drag
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    lastDragX = e.x; lastDragY = e.y
                }
            })
            // Mouse drag => pan
            addMouseMotionListener(object : MouseAdapter() {
                override fun mouseDragged(e: MouseEvent) {
                    offsetX += (e.x - lastDragX)
                    offsetY += (e.y - lastDragY)
                    lastDragX = e.x; lastDragY = e.y
                    repaint()
                }
            })
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            val w = width; val h = height
            val marginLeft = 40.0
            val marginBottom = 30.0
            val usableHeight = h - 50.0
            val usableWidth = w - marginLeft - 10.0
            val barWidth = usableWidth / proportions.size

            // Find the maximum proportion to rescale bars
            val maxProp = proportions.maxOrNull()!!.coerceAtLeast(1e-9)

            // Save transform, apply zoom & pan
            val origTx = g2.transform
            g2.translate(offsetX, offsetY)
            g2.scale(zoom, zoom)

            // Draw axes
            g2.color = Color.DARK_GRAY
            g2.drawLine(
                marginLeft.toInt(), 10,
                marginLeft.toInt(), (h - marginBottom).toInt()
            )
            g2.drawLine(
                marginLeft.toInt(), (h - marginBottom).toInt(),
                w - 10, (h - marginBottom).toInt()
            )

            // Draw bars (rescaled)
            proportions.forEachIndexed { i, prop ->
                val norm = prop / maxProp
                val barHeight = (norm * usableHeight).toInt()
                val x = (marginLeft + i * barWidth).toInt()
                val y = (h - marginBottom).toInt() - barHeight
                g2.color = Color.BLUE
                g2.fillRect(x, y, barWidth.toInt(), barHeight)
            }

            // Draw quartile dashed lines
            val dash = floatArrayOf(5f, 5f)
            g2.stroke = BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, dash, 0f)
            g2.color = Color.RED
            listOf(q1, median, q3).forEach { q ->
                val x = (marginLeft + q * barWidth).toInt()
                g2.drawLine(x, 10, x, (h - marginBottom).toInt())
            }

            // Draw mean solid line
            g2.stroke = BasicStroke(2f)
            g2.color = Color.ORANGE
            val meanX = (marginLeft + mean * barWidth).toInt()
            g2.drawLine(meanX, 10, meanX, (h - marginBottom).toInt())

            // Restore transform & stroke
            g2.transform = origTx
            g2.stroke = BasicStroke()

            // --- Draw Y‑axis grid lines and labels ---
            g2.color = Color.LIGHT_GRAY
            for (i in 0..4) {
                val yy = (h - marginBottom - i * usableHeight / 4).toInt()
                g2.drawLine(marginLeft.toInt(), yy, (w - 10), yy)
            }
            g2.color = Color.BLACK
            for (i in 0..4) {
                val yy = (h - marginBottom - i * usableHeight / 4).toInt() + 5
                g2.drawString("${i * 25}%", 5, yy)
            }

            // X‑axis labels every 50 units
            for (i in 0 until proportions.size step 50) {
                val x = (marginLeft + i * barWidth).toInt()
                g2.drawString("$i", x, h - 10)
            }

            // Y‑axis title
            g2.drawString("Relative %", 5, 20)

            // Legend
            g2.color = Color.RED
            g2.drawString("Q1  Median  Q3", (marginLeft + 10).toInt(), 20)
            g2.color = Color.ORANGE
            g2.drawString("Mean", (marginLeft + 150).toInt(), 20)
        }
    }


    /**
     * Helper: returns the RGB for a gray color given a value.
     */
    private fun getGrayColor(value: Int): Int = Color(value, value, value).rgb

    /**
     * Helper: maps a height value to a color.
     * This is the basic mapping used when generating chunk images.
     */
    private fun getColorMap(value: Int): Int = when (value) {
        in 0..32   -> Color(0, 0, 150).rgb
        in 33..64  -> Color(0, 0, 255).rgb
        in 65..128 -> Color(85, 220, 85).rgb
        in 129..192-> Color(200, 200, 200).rgb
        in 193..255-> Color(255, 255, 255).rgb
        else       -> getGrayColor(value)
    }

    /**
     * Generic helper: generates an image given width and height and a lambda to calculate each pixel.
     */
    private fun createImage(width: Int, height: Int, pixelGenerator: (i: Int, j: Int) -> Int): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (i in 0 until height)
            for (j in 0 until width)
                image.setRGB(j, i, pixelGenerator(i, j))
        return image
    }

    /**
     * Creates an image with alpha support.
     *
     * In order to allow transparent overlay, we use BufferedImage.TYPE_INT_ARGB.
     */
    private fun createARGBImage(width: Int, height: Int, pixelGenerator: (i: Int, j: Int) -> Int): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (i in 0 until height) {
            for (j in 0 until width) {
                image.setRGB(j, i, pixelGenerator(i, j))
            }
        }
        return image
    }

    /**
     * Applies transparent contour lines over the provided image.
     *
     * For each pixel, the function checks its left, right, top, and bottom neighbors.
     * If any neighbor’s height is an exact multiple of 10 and the current pixel’s height equals that neighbor’s height plus 1,
     * then the pixel is blended with transparent black (using the given alpha, e.g. 0.5f) rather than replaced outright.
     *
     * @param image   The image to overlay the contour lines.
     * @param heightMap The height map whose dimensions match the image.
     * @param alpha   The blending factor; for example, 0.5f gives 50% opacity for the contour overlay.
     */
    private fun applyContourLines(image: BufferedImage, heightMap: Array<IntArray>, alpha: Float = 0.5f): BufferedImage {
        val rows = heightMap.size
        val cols = heightMap[0].size
        // For each pixel in the height map, check all four neighbors.
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                val current = heightMap[i][j]
                var shouldBlend = false
                // Check left neighbor.
                if (j > 0 && (current - heightMap[i][j - 1] == 1 && heightMap[i][j - 1] % 10 == 0))
                    shouldBlend = true
                // Check right neighbor.
                if (j < cols - 1 && (current - heightMap[i][j + 1] == 1 && heightMap[i][j + 1] % 10 == 0))
                    shouldBlend = true
                // Check top neighbor.
                if (i > 0 && (current - heightMap[i - 1][j] == 1 && heightMap[i - 1][j] % 10 == 0))
                    shouldBlend = true
                // Check bottom neighbor.
                if (i < rows - 1 && (current - heightMap[i + 1][j] == 1 && heightMap[i + 1][j] % 10 == 0))
                    shouldBlend = true

                if (shouldBlend) {
                    // Retrieve the original color.
                    val origRGB = image.getRGB(j, i)
                    val origColor = Color(origRGB, true)
                    // Blend with black: new_color = orig_color * (1 - alpha) + black * alpha.
                    // Since black is (0,0,0), the new value is just orig_color multiplied by (1 - alpha)
                    val newRed = (origColor.red * (1 - alpha)).toInt().coerceIn(0, 255)
                    val newGreen = (origColor.green * (1 - alpha)).toInt().coerceIn(0, 255)
                    val newBlue = (origColor.blue * (1 - alpha)).toInt().coerceIn(0, 255)
                    // Preserve the original alpha.
                    val blended = Color(newRed, newGreen, newBlue, origColor.alpha).rgb
                    image.setRGB(j, i, blended)
                }
            }
        }
        return image
    }


    /**
     * Generates chunk images if they don't already exist.
     * For the color view, after generating the basic image from getColorMap,
     * we overlay contour lines using the chunk's height data.
     */
    fun generateChunkIfNeeded(chunkX: Int, chunkY: Int) {
        val key = Pair(chunkX, chunkY)
        if (!generatedChunks.containsKey(key)) {
            val (chunkNoise, chunkGradient) = chunkGenerator.generateChunkNoiseAndGradient(
                chunkX = chunkX,
                chunkY = chunkY,
                seed = seed,
                chunkSize = chunkSize
            )
            val grayImg = createImage(chunkSize, chunkSize) { i, j -> getGrayColor(chunkNoise[i][j]) }
            // Generate the basic color image.
            val basicColorImg = createARGBImage(chunkSize, chunkSize) { i, j -> getColorMap(chunkNoise[i][j]) }
            val colorImg = applyContourLines(basicColorImg, chunkNoise)
            val gradImg = createImage(chunkSize, chunkSize) { i, j ->
                val (dx, dy) = chunkGradient[i][j]
                val red = (((dx + 1) / 2) * 255).toInt().coerceIn(0, 255)
                val blue = (((dy + 1) / 2) * 255).toInt().coerceIn(0, 255)
                Color(red, 0, blue).rgb
            }
            generatedChunks[key] = Triple(grayImg, colorImg, gradImg)
            updateGlobalMapBounds(chunkX, chunkX, chunkY, chunkY)
            updateMapHeightMap(chunkX, chunkY, chunkNoise)
            updateMapGradientMap(chunkX, chunkY, chunkGradient)
        }
    }

    fun updateMapHeightMap(chunkX: Int, chunkY: Int, chunkNoise: Array<IntArray>) {
        val offsetXInMap = (chunkX - map.minChunkX) * chunkSize
        val offsetYInMap = (chunkY - map.minChunkY) * chunkSize
        for (i in 0 until chunkSize)
            for (j in 0 until chunkSize)
                map.height_map[offsetYInMap + i][offsetXInMap + j] = chunkNoise[i][j]
    }

    fun updateMapGradientMap(chunkX: Int, chunkY: Int, chunkGradient: Array<Array<Pair<Float, Float>>>) {
        val offsetXInMap = (chunkX - map.minChunkX) * chunkSize
        val offsetYInMap = (chunkY - map.minChunkY) * chunkSize
        for (i in 0 until chunkSize)
            for (j in 0 until chunkSize) {
                val (dx, dy) = chunkGradient[i][j]
                val red = (((dx + 1) / 2) * 255).toInt().coerceIn(0, 255)
                val blue = (((dy + 1) / 2) * 255).toInt().coerceIn(0, 255)
                map.gradiant_map[offsetYInMap + i][offsetXInMap + j] = Color(red, 0, blue).rgb
            }
    }

    fun updateGlobalMapBounds(newMinCX: Int, newMaxCX: Int, newMinCY: Int, newMaxCY: Int) {
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

    fun createGlobalHeightMapImage(): BufferedImage {
        if (map.height_map.isEmpty()) throw IllegalStateException("Global height map is empty")
        val rows = map.height_map.size
        val cols = map.height_map[0].size
        return createImage(cols, rows) { i, j -> getGrayColor(map.height_map[i][j]) }
    }

    fun createGlobalGradientMapImage(): BufferedImage {
        if (map.gradiant_map.isEmpty()) throw IllegalStateException("Global gradient map is empty")
        val rows = map.gradiant_map.size
        val cols = map.gradiant_map[0].size
        return createImage(cols, rows) { i, j -> map.gradiant_map[i][j] }
    }

    fun createGlobalColorMapImage(): BufferedImage {
        if (map.height_map.isEmpty()) throw IllegalStateException("Global height map is empty")
        val rows = map.height_map.size
        val cols = map.height_map[0].size
        val basicImage = createARGBImage(cols, rows) { i, j -> getColorMap(map.height_map[i][j]) }
        return applyContourLines(basicImage, map.height_map)
    }

}
