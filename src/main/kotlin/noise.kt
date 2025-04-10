import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor

class noise (
    var size: Int,
    var seed: Int,
    val octaves: Int = 4,
    val persistence: Float = 0.1f,
    val lacunarity: Float = 8.0f

){
    public fun generateBrownianNoise(scale: Float, frequencyInit: Float, amplitudeInit: Float, mean: Int): Array<IntArray> {
        val noise = Array(size) { IntArray(size) }

        // Fill each pixel with fractal Brownian noise
        for (y in 0 until size) {
            for (x in 0 until size) {
                var amplitude = amplitudeInit
                var frequency = frequencyInit
                var total = 0f

                // Sum multiple “octaves” of noise
                for (octave in 0 until octaves) {
                    // Scale coordinates for this octave
                    val nx = x * scale * frequency
                    val ny = y * scale * frequency

                    // Get noise for this octave and accumulate
                    val perlinValue = perlin2D(nx, ny, seed) // range is typically -1..1
                    total += perlinValue * amplitude

                    amplitude *= persistence
                    frequency *= lacunarity
                }

                // Convert the summed result from about -1..1 to 0..255
                // (you may need to adjust scaling/clamping depending on desired contrast)
                val normalized = (total + 1f) / 2f  // shift [-1..1] to [0..1]
                val color = (normalized * 255f).toInt().coerceIn(0, 255)

                noise[y][x] = color
            }
        }

        // Optionally, add a mean value to the noise
        offsetAdjuster(noise, mean)

        return noise
    }

    private fun offsetAdjuster(Array: Array<IntArray>, mean: Int): Array<IntArray> {
        // 1) Calculer la moyenne actuelle du bruit
        var sum = 0L
        for (y in 0 until size) {
            for (x in 0 until size) {
                sum += Array[y][x]
            }
        }
        val currentMean = sum.toFloat() / (size * size)

        // 2) Calculer la différence par rapport à la moyenne souhaitée
        val offset = mean - currentMean

        // 3) Appliquer ce décalage à tous les pixels et recadrer dans [0..255] si nécessaire
        for (y in 0 until size) {
            for (x in 0 until size) {
                val newValue = Array[y][x] + offset
                Array[y][x] = newValue.toInt().coerceIn(0, 255)
            }
        }
        return Array
    }


    private fun perlin2D(x: Float, y: Float, seed: Int): Float {
        // “Hash” is used to get pseudo-random gradient directions from integer coordinates
        fun hash(i: Int, j: Int): Float {
            // Simple 2D hash that depends on (i,j) and the seed
            var h = i * 374761393 + j * 668265263 + seed * 31
            h = (h xor (h shr 13)) * 1274126177
            return (h xor (h shr 16)).toFloat()
        }

        // Compute lattice coordinates
        val x0 = floor(x).toInt()
        val x1 = x0 + 1
        val y0 = floor(y).toInt()
        val y1 = y0 + 1

        // Local coordinates inside each cell
        val lx = x - x0
        val ly = y - y0

        // Fade function for smoother interpolation
        val sx = fade(lx)
        val sy = fade(ly)

        // Pseudo-random gradients at each corner of the cell
        val g00 = gradient(hash(x0, y0), lx, ly)
        val g10 = gradient(hash(x1, y0), lx - 1, ly)
        val g01 = gradient(hash(x0, y1), lx, ly - 1)
        val g11 = gradient(hash(x1, y1), lx - 1, ly - 1)

        // Interpolate along x, then y
        val nx0 = lerp(g00, g10, sx)
        val nx1 = lerp(g01, g11, sx)
        val nxy = lerp(nx0, nx1, sy)

        return nxy
    }

    /**
     * A simple Perlin-like noise function in 2D. Range is approximately -1..1.
     * Adapted to keep the example self-contained.
     */

    // Smooth interpolation function
    private fun fade(t: Float): Float {
        // 6t^5 - 15t^4 + 10t^3
        return t * t * t * (t * (t * 6f - 15f) + 10f)
    }

    // Dot product of a 2D gradient direction and the distance vector
    private fun gradient(hashVal: Float, x: Float, y: Float): Float {
        // Turn hash value into a gradient direction in 2D
        val angle = (hashVal % 360) * (PI.toFloat() / 180f)
        val gradX = cos(angle)
        val gradY = kotlin.math.sin(angle)
        // Dot product
        return gradX * x + gradY * y
    }

    // Linear interpolation
    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + t * (b - a)
    }
}