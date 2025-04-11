import java.awt.AlphaComposite
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
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

// Enum to represent the view mode.
enum class ViewMode {
    GRAYSCALE, COLOR
}

/**
 * A Swing window that displays an infinite grid of chunks with interactive pan/zoom,
 * left-drag selection to generate chunks, and a toggle button to switch between
 * grayscale and color terrain views.
 *
 * Controls:
 * - Left-click without drag: generate a single chunk.
 * - Left-click drag (select box): on release, generate every chunk within the selection.
 * - Right-click drag: pan the view.
 * - Mouse wheel: zoom in/out centered on the mouse pointer.
 * - Button (top): toggles between grayscale view and color terrain view.
 *
 * When a chunk is generated, both a grayscale version and a color version are created.
 * A red grid (50% alpha) is drawn over generated chunks.
 *
 * @param noise     An instance of your Noise class.
 * @param seed      The seed used for noise generation.
 * @param chunkSize The size (in pixels) of each chunk.
 */
class Display(
    private val noise: Noise,
    private val seed: Int,
    private val chunkSize: Int = 16
) : JFrame("Chunk Generator with Toggle View") {

    // Map to store generated chunk images keyed by (chunkX, chunkY).
    // The pair holds (grayscaleImage, coloredImage).
    private val generatedChunks = mutableMapOf<Pair<Int, Int>, Pair<BufferedImage, BufferedImage>>()

    // Pan and zoom parameters.
    private var zoomFactor = 1.0
    private var offsetX = 0.0
    private var offsetY = 0.0
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

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        // Use BorderLayout to place the button on top.
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

                // Derive chunk indices that cover the visible region.
                val startChunkX = floor(worldMinX / chunkSize).toInt()
                val endChunkX = ceil(worldMaxX / chunkSize).toInt() - 1
                val startChunkY = floor(worldMinY / chunkSize).toInt()
                val endChunkY = ceil(worldMaxY / chunkSize).toInt() - 1

                // Loop over the visible region.
                for (cy in startChunkY..endChunkY) {
                    for (cx in startChunkX..endChunkX) {
                        val posX = cx * chunkSize
                        val posY = cy * chunkSize
                        val key = Pair(cx, cy)
                        if (generatedChunks.containsKey(key)) {
                            // Use the image based on the current view mode.
                            val image = if (viewMode == ViewMode.GRAYSCALE) generatedChunks[key]!!.first
                            else generatedChunks[key]!!.second
                            g2d.drawImage(image, posX, posY, null)
                            // Draw red overlay grid with 50% transparency.
                            val oldComposite = g2d.composite
                            g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)
                            g2d.color = Color.RED
                            g2d.drawRect(posX, posY, chunkSize, chunkSize)
                            g2d.composite = oldComposite
                        } else {
                            // Draw placeholder cell for ungenerated chunk.
                            g2d.color = Color.LIGHT_GRAY
                            g2d.fillRect(posX, posY, chunkSize, chunkSize)
                            g2d.color = Color.DARK_GRAY
                            g2d.drawRect(posX, posY, chunkSize, chunkSize)
                        }
                    }
                }

                // If a left-drag selection is in progress, draw the selection rectangle.
                if (isSelecting) {
                    // Convert screen drag coordinates to world coordinates.
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
        // Add mouse listeners for selection, single click generation, and panning.
        drawPanel.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                when (e.button) {
                    MouseEvent.BUTTON1 -> { // Left button: start selection.
                        leftDragStartX = e.x
                        leftDragStartY = e.y
                        leftDragCurrentX = e.x
                        leftDragCurrentY = e.y
                        isSelecting = true
                    }
                    MouseEvent.BUTTON3 -> { // Right button: record starting position for panning.
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
                                // Treat as a simple click.
                                val worldX = (e.x - offsetX) / zoomFactor
                                val worldY = (e.y - offsetY) / zoomFactor
                                val clickedChunkX = floor(worldX / chunkSize).toInt()
                                val clickedChunkY = floor(worldY / chunkSize).toInt()
                                generateChunkIfNeeded(clickedChunkX, clickedChunkY)
                            } else {
                                // Generate all chunks within the selected area.
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
                // Update left-drag selection.
                if ((e.modifiersEx and MouseEvent.BUTTON1_DOWN_MASK) != 0) {
                    leftDragCurrentX = e.x
                    leftDragCurrentY = e.y
                    drawPanel.repaint()
                }
                // Update panning with right-button drag.
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

                // Adjust pan so that zoom is centered on the mouse pointer.
                offsetX = e.x - ((e.x - offsetX) / oldZoom) * zoomFactor
                offsetY = e.y - ((e.y - offsetY) / oldZoom) * zoomFactor

                drawPanel.repaint()
            }
        })

        // Create a toggle button and add it to a top panel.
        val toggleButton = JButton("Switch to Color View")
        toggleButton.addActionListener(object : ActionListener {
            override fun actionPerformed(e: ActionEvent?) {
                // Toggle view mode.
                viewMode = if (viewMode == ViewMode.GRAYSCALE) ViewMode.COLOR else ViewMode.GRAYSCALE
                // Update button text.
                toggleButton.text = if (viewMode == ViewMode.GRAYSCALE) "Switch to Color View"
                else "Switch to Grayscale View"
                // Repaint the drawing panel.
                drawPanel.repaint()
            }
        })
        val topPanel = JPanel()
        topPanel.add(toggleButton)

        // Add the components to the JFrame.
        add(topPanel, BorderLayout.NORTH)
        add(drawPanel, BorderLayout.CENTER)
        pack()
        setLocationRelativeTo(null)
        isVisible = true
    }

    /**
     * Generates the chunk at (chunkX, chunkY) if it hasn't been generated yet.
     * Produces both the grayscale and color versions.
     */
    private fun generateChunkIfNeeded(chunkX: Int, chunkY: Int) {
        val key = Pair(chunkX, chunkY)
        if (!generatedChunks.containsKey(key)) {
            val chunkNoise = noise.generateChunkNoise(
                chunkX = chunkX,
                chunkY = chunkY,
                seed = seed,
                chunkSize = chunkSize
            )
            // Create grayscale image.
            val grayImg = BufferedImage(chunkSize, chunkSize, BufferedImage.TYPE_INT_RGB)
            // Create color image.
            val colorImg = BufferedImage(chunkSize, chunkSize, BufferedImage.TYPE_INT_RGB)
            for (i in 0 until chunkSize) {
                for (j in 0 until chunkSize) {
                    val value = chunkNoise[i][j]
                    // Grayscale: same value for R, G, and B.
                    val grayRGB = Color(value, value, value).rgb
                    grayImg.setRGB(j, i, grayRGB)
                    // Color conversion based on value thresholds.
                    val colorRGB = when (value) {
                        in 0..32 -> Color(0, 0, 150).rgb
                        in 33..64 -> Color(0, 0, 255).rgb
                        in 65..69 -> Color(245, 245, 85).rgb
                        in 70..128 -> Color(85, 220, 85).rgb
                        in 129..192 -> Color(200, 200, 200).rgb
                        in 193..255 -> Color(255, 255, 255).rgb
                        else -> grayRGB
                    }
                    colorImg.setRGB(j, i, colorRGB)
                }
            }
            generatedChunks[key] = Pair(grayImg, colorImg)
        }
    }
}
