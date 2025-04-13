import java.awt.Color
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities

fun main() {
    val map = Map()

    val seed = 3
    val size = 1000   // Size value used by your Noise class.
    val ChunkGenerator = Chunk(size, seed)
    SwingUtilities.invokeLater {
        Display(ChunkGenerator, seed, map)
    }
}