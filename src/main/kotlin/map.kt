import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class map (
    var size: Int = 0,

    var height_map: Array<IntArray> = arrayOf(),
    var heat_map: Array<IntArray> = arrayOf()
){
    public fun createImageBW(noise: Array<IntArray>, name: String): Unit {
        val n = noise.size
        val image = BufferedImage(n, n, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until n) {
            for (y in 0 until n) {
                val gray = noise[x][y]
                val rgb = Color(gray, gray, gray).rgb
                image.setRGB(x, y, rgb)
            }
        }

        addGridLines(image)
        saveImage(image, name)
        return
    }

    public fun createImageColorTerrain(noise: Array<IntArray>, name: String): Unit {
        val n = noise.size
        val image = BufferedImage(n, n, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until n) {
            for (y in 0 until n) {
                when(noise[x][y]) {
                    in 0..32 -> image.setRGB(x, y, Color(0, 0, 150).rgb) // Blue for water
                    in 33..64 -> image.setRGB(x, y, Color(0, 0, 255).rgb) // Blue for water
                    in 65..68 -> image.setRGB(x, y, Color(240, 240, 110).rgb) // Green for grass
                    in 65..128 -> image.setRGB(x, y, Color(85, 220, 85).rgb) // Green for grass
                    in 129..192 -> image.setRGB(x, y, Color(200, 200, 200).rgb) // Yellow for sand
                    in 193..255 -> image.setRGB(x, y, Color(255, 255, 255).rgb) // Brown for mountains
                }
            }
        }

        addGridLines(image)
        saveImage(image, name)
        return
    }


    private fun addGridLines(image: BufferedImage, gridSize: Int = 16) {
        val g = image.createGraphics()
        g.color = Color.RED
        g.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.5f) // 50% transparency
        for (i in 0 until image.width step gridSize) {
            g.drawLine(i, 0, i, image.height)
        }
        for (j in 0 until image.height step gridSize) {
            g.drawLine(0, j, image.width, j)
        }
        g.dispose()
    }

    private fun saveImage(image: BufferedImage, name: String): Unit {
        val outputDir = File("out")
        if (!outputDir.exists()) {
            outputDir.mkdirs()  // Create the directory if it doesn't exist
        }
        val outputFile = File(outputDir, "$name.png")
        ImageIO.write(image, "png", outputFile)
        println("Image saved at: ${outputFile.absolutePath}")
    }
}

