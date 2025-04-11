import java.awt.Color
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities

fun main() {
    val seed = 3
    val size = 1000   // Size value used by your Noise class.
    val noiseGenerator = Noise(size, seed)
    SwingUtilities.invokeLater {
        Display(noiseGenerator, seed)
    }
}