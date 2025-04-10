import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO


// Function to convert the noise table to a BufferedImage (grayscale)
fun createImage(noise: Array<IntArray>): BufferedImage {
    val n = noise.size
    val image = BufferedImage(n, n, BufferedImage.TYPE_BYTE_GRAY)
    for (x in 0 until n) {
        for (y in 0 until n) {
            val gray = noise[x][y]
            val rgb = Color(gray, gray, gray).rgb
            image.setRGB(x, y, rgb)
        }
    }
    return image
}


fun main() {
    // Parameters
    val size = 7500
    val seed = 3


    // Class instantiation
    val map = map(size)
    val noise_generator = noise(size, seed)

    // Generate noise for the height map
    val scale = 0.003f
    val mean = 64
    val frequency = 0.2f
    val amplitude = 2.5f
    val noise = noise_generator.generateBrownianNoise(scale, frequency, amplitude, mean)
    map.height_map = noise


    // Create and save the image
    map.createImageBW(map.height_map, "height_map_BW")
    map.createImageColorTerrain(map.height_map, "height_map_color_terrain")

}
