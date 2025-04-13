package DisplayPack

import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JPanel
import kotlin.math.ceil

/**
 * HorizontalScalePanel draws the world coordinate scale along the bottom.
 */
class HorizontalScalePanel(val display: Display) : JPanel() {

    init {
        // Set preferred height.
        preferredSize = display.horizontalScaleDimension
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D

        // Fill background.
        g2d.color = Color.WHITE
        g2d.fillRect(0, 0, width, height)
        g2d.color = Color.BLACK

        val panelW = display.mapPanel.width
        // Compute visible world coordinate range.
        val visibleWorldMinX = -display.offsetX / display.zoomFactor
        val visibleWorldMaxX = (panelW - display.offsetX) / display.zoomFactor
        val margin = 5
        // Draw horizontal line.
        g2d.drawLine(0, margin, width, margin)

        // Draw tick marks.
        var tickX = ceil(visibleWorldMinX / display.tickSpacingWorld) * display.tickSpacingWorld
        while (tickX <= visibleWorldMaxX) {
            val screenX = (tickX * display.zoomFactor + display.offsetX).toInt()
            g2d.drawLine(screenX, margin - 5, screenX, margin + 5)
            g2d.drawString(tickX.toInt().toString(), screenX - 10, margin + 20)
            tickX += display.tickSpacingWorld
        }
    }
}

/**
 * VerticalScalePanel draws the world coordinate scale along the right side.
 */
class VerticalScalePanel(val display: Display) : JPanel() {

    init {
        preferredSize = display.verticalScaleDimension
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D

        // Fill background.
        g2d.color = Color.WHITE
        g2d.fillRect(0, 0, width, height)
        g2d.color = Color.BLACK

        val panelH = display.mapPanel.height
        val visibleWorldMinY = -display.offsetY / display.zoomFactor
        val visibleWorldMaxY = (panelH - display.offsetY) / display.zoomFactor
        val margin = 5
        g2d.drawLine(margin, 0, margin, height)

        var tickY = ceil(visibleWorldMinY / display.tickSpacingWorld) * display.tickSpacingWorld
        while (tickY <= visibleWorldMaxY) {
            val screenY = (tickY * display.zoomFactor + display.offsetY).toInt()
            g2d.drawLine(margin - 5, screenY, margin + 5, screenY)
            g2d.drawString(tickY.toInt().toString(), margin + 8, screenY + 5)
            tickY += display.tickSpacingWorld
        }
    }
}