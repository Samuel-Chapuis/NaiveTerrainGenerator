package DisplayPack

import Map

import ChunkGenerator
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
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
 * It instantiates the MapPanel and the scale panels, and it also creates the control buttons.
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
        val showGlobalButton = JButton("Show Global Map").apply {
            addActionListener {
                // Create the global map image.
                val globalImage = when (viewMode) {
                    ViewMode.GRAYSCALE -> createGlobalHeightMapImage()
                    ViewMode.COLOR     -> createGlobalColorMapImage()
                    ViewMode.GRADIENT  -> createGlobalGradientMapImage()
                }
                // Save the global image as a JPEG in the "/out" folder.
                try {
                    val outputDirectory = File("out")
                    if (!outputDirectory.exists()) {
                        outputDirectory.mkdirs()
                    }
                    val outputFile = File(outputDirectory, "globalMap_${viewMode.name}.jpg")
                    ImageIO.write(globalImage, "jpg", outputFile)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
                // Open a new frame to display the global image.
                val mapFrame = JFrame("Global Map - ${viewMode.name}")
                mapFrame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
                val panel = object : JPanel() {
                    override fun paintComponent(g: java.awt.Graphics) {
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
        }
        // Top panel holds all control buttons.
        val topPanel = JPanel().apply {
            add(grayButton)
            add(colorButton)
            add(gradientButton)
            add(toggleGridButton)
            add(showGlobalButton)
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

    // --- Helper functions, similar to your previous implementation ---

    fun generateChunkIfNeeded(chunkX: Int, chunkY: Int) {
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
        val image = BufferedImage(cols, rows, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val value = map.height_map[y][x]
                image.setRGB(x, y, Color(value, value, value).rgb)
            }
        }
        return image
    }

    fun createGlobalGradientMapImage(): BufferedImage {
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

    fun createGlobalColorMapImage(): BufferedImage {
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