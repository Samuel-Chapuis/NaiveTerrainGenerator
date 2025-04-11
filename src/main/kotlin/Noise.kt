import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

class Noise (
    var size: Int,
    var seed: Int,
    val octaves: Int = 4,
    // Adjust default persistence & lacunarity for smoother shapes
    val persistence: Float = 0.4f,
    val lacunarity: Float = 2.5f
) {
    fun generateChunkNoise(
        chunkX: Int,
        chunkY: Int,
        seed: Int,
        chunkSize: Int = 16,
        scale: Float = 0.05f,
        octaves: Int = 4,
        persistence: Float = 0.4f,
        lacunarity: Float = 2.5f,
        mean: Int = 64
    ): Array<IntArray> {

        val noise = Array(chunkSize) { IntArray(chunkSize) }
        // For fractal noise, we vary amplitude/frequency each octave:
        // e.g., frequency *= lacunarity, amplitude *= persistence

        // Loop over each pixel in the chunk
        for (y in 0 until chunkSize) {
            for (x in 0 until chunkSize) {
                var total = 0f
                var amplitude = 1f
                var frequency = 1f

                // Derive global coordinates
                val worldX = chunkX * chunkSize + x
                val worldY = chunkY * chunkSize + y

                // Summation of multiple octaves
                repeat(octaves) {
                    val nx = worldX * scale * frequency
                    val ny = worldY * scale * frequency

                    val perlinVal = perlin2D(nx, ny, seed) // ~ -1..1
                    total += perlinVal * amplitude

                    amplitude *= persistence
                    frequency *= lacunarity
                }

                // Convert from about -1..1 to 0..255
                val normalized = (total + 1f) / 2f
                val colorValue = (normalized * 255f).toInt().coerceIn(0,255)
                noise[y][x] = colorValue
            }
        }

        offsetAdjuster(noise, 64) // Adjust mean to 128 (mid-gray)
        return noise
    }


    private fun offsetAdjuster(array: Array<IntArray>, mean: Int): Array<IntArray> {
        // Use the actual dimensions of the array rather than the Noise class property.
        val height = array.size
        val width = array[0].size

        // 1) Calculate current mean
        var sum = 0L
        for (y in 0 until height) {
            for (x in 0 until width) {
                sum += array[y][x]
            }
        }
        val currentMean = sum.toFloat() / (height * width)

        // 2) Offset to match desired mean
        val offset = mean - currentMean

        // 3) Apply offset to all pixels & clamp
        for (y in 0 until height) {
            for (x in 0 until width) {
                val newValue = array[y][x] + offset
                array[y][x] = newValue.toInt().coerceIn(0, 255)
            }
        }
        return array
    }


    /**
     * (Optional) Binary threshold to create distinct “water” vs “land” zones.
     * Adjust threshold as you like, or remove if you want continuous shades.
     */
    private fun threshold(array: Array<IntArray>, cutoff: Int) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                array[y][x] = if (array[y][x] < cutoff) 0 else 255
            }
        }
    }

    /**
     * Basic 2D Perlin-like noise, returning a float in -1..1.
     */
    private fun perlin2D(x: Float, y: Float, seed: Int): Float {
        // “Hash” is used to get pseudo-random gradient directions from integer coords
        fun hash(i: Int, j: Int): Float {
            var h = i * 374761393 + j * 668265263 + seed * 31
            h = (h xor (h shr 13)) * 1274126177
            return (h xor (h shr 16)).toFloat()
        }

        // Lattice coordinates
        val x0 = floor(x).toInt()
        val x1 = x0 + 1
        val y0 = floor(y).toInt()
        val y1 = y0 + 1

        // Local coordinates inside each cell
        val lx = x - x0
        val ly = y - y0

        // Fade for smooth interpolation
        val sx = fade(lx)
        val sy = fade(ly)

        // Pseudo-random gradients at corners
        val g00 = gradient(hash(x0, y0), lx, ly)
        val g10 = gradient(hash(x1, y0), lx - 1, ly)
        val g01 = gradient(hash(x0, y1), lx, ly - 1)
        val g11 = gradient(hash(x1, y1), lx - 1, ly - 1)

        // Interpolate along x, then y
        val nx0 = lerp(g00, g10, sx)
        val nx1 = lerp(g01, g11, sx)
        return lerp(nx0, nx1, sy)
    }

    // Smooth interpolation function: 6t^5 - 15t^4 + 10t^3
    private fun fade(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)

    // Turn our hash into a gradient direction in 2D, do dot product with (x, y)
    private fun gradient(hashVal: Float, x: Float, y: Float): Float {
        val angle = (hashVal % 360) * (PI.toFloat() / 180f)
        val gradX = cos(angle)
        val gradY = kotlin.math.sin(angle)
        return gradX * x + gradY * y
    }

    // Linear interpolation
    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + t * (b - a)
    }
}
