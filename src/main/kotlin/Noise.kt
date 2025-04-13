import Noise.Utile.computeGradient

import kotlin.math.*

// Refactored Noise class.
class Chunk(
    var size: Int,             // Overall noise size (can be used in offsets if needed).
    var seed: Int,             // Seed to ensure reproducibility.
    val octaves: Int = 4,      // Number of octaves to combine.
    val persistence: Float = 0.4f,  // Amplitude reduction per octave.
    val lacunarity: Float = 2.5f    // Frequency increase per octave.
) {
    /**
     * Generates a chunk of noise as a 2D array of grayscale values (0–255).
     *
     * The algorithm uses domain warping. For each pixel:
     * 1. Computes its global coordinate.
     * 2. For each octave: scales the coordinate,
     *    applies domain warping (via a displacement vector computed from noise),
     *    samples the Perlin-like noise at the warped position,
     *    and accumulates the value weighted by the current amplitude.
     * 3. Normalizes the final value (roughly from -1..1) into the range 0–255,
     *    then adjusts it by the desired mean.
     *
     * @param chunkX Chunk index in the x direction.
     * @param chunkY Chunk index in the y direction.
     * @param seed The seed used for noise generation.
     * @param chunkSize The number of pixels per side for the chunk.
     * @param scale The base scale (frequency) factor.
     * @param octaves Number of octaves for fractal noise.
     * @param persistence Amplitude reduction factor per octave.
     * @param lacunarity Frequency increase factor per octave.
     * @param mean Desired mean brightness (adjusts the overall brightness).
     * @param warpFactor The magnitude of the domain warping.
     * @return A 2D array of noise values (each between 0 and 255).
     */
    fun generateChunkNoise(
        chunkX: Int,
        chunkY: Int,
        seed: Int,
        chunkSize: Int = 16,
        scale: Float = 0.05f,
        octaves: Int = this.octaves,
        persistence: Float = this.persistence,
        lacunarity: Float = this.lacunarity,
        mean: Int = 64,
        warpFactor: Float = 0.5f
    ): Array<IntArray> {
        val noiseArray = Array(chunkSize) { IntArray(chunkSize) }

        for (y in 0 until chunkSize) {
            for (x in 0 until chunkSize) {
                var total = 0f
                var amplitude = 1f
                var frequency = 1f
                // Compute the global coordinate of the current pixel.
                val worldX = chunkX * chunkSize + x
                val worldY = chunkY * chunkSize + y

                // Accumulate contributions from multiple octaves.
                repeat(octaves) {
                    // Scale coordinate for current octave.
                    val baseX = worldX * scale * frequency
                    val baseY = worldY * scale * frequency

                    // Domain warp: compute a displacement (dx, dy) and offset the coordinate.
                    val (warpedX, warpedY) = domainWarp(baseX, baseY, warpFactor)

                    // Sample our Perlin-like noise at the warped coordinate.
                    val noiseValue = perlin2D(warpedX, warpedY, seed)
                    total += noiseValue * amplitude

                    amplitude *= persistence
                    frequency *= lacunarity
                }
                // Normalize (assumes noise is roughly in -1..1) to 0..255.
                val normalized = ((total + 1) / 2 * 255).toInt()
                // Adjust the brightness around a target mean (here 128 is the mid-point of 0..255).
                val adjusted = normalized + (mean - 128)
                noiseArray[y][x] = adjusted.coerceIn(0, 255)
            }
        }
        return noiseArray
    }

    /**
     * Convenience function that generates a noise chunk and computes its gradient.
     *
     * @return A Pair where first is the noise map (height map) and second is the gradient map.
     */
    fun generateChunkNoiseAndGradient(
        chunkX: Int,
        chunkY: Int,
        seed: Int,
        chunkSize: Int = 16,
        scale: Float = 0.05f,
        octaves: Int = this.octaves,
        persistence: Float = this.persistence,
        lacunarity: Float = this.lacunarity,
        mean: Int = 64,
        warpFactor: Float = 0.5f
    ): Pair<Array<IntArray>, Array<Array<Pair<Float, Float>>>> {
        val noiseMap = generateChunkNoise(chunkX, chunkY, seed, chunkSize, scale, octaves, persistence, lacunarity, mean, warpFactor)
        val gradientMap = computeGradient(noiseMap)
        return Pair(noiseMap, gradientMap)
    }

    /**
     * Applies domain warping to a coordinate.
     *
     * Computes a displacement vector (dx, dy) using noise (with different seed offsets)
     * and adds it to the original coordinate.
     *
     * @param x The original x coordinate.
     * @param y The original y coordinate.
     * @param warpFactor Scales the displacement magnitude.
     * @return A Pair containing the warped (x, y) coordinates.
     */
    private fun domainWarp(x: Float, y: Float, warpFactor: Float): Pair<Float, Float> {
        val (dx, dy) = computeDisplacement(x, y, warpFactor)
        return Pair(x + dx, y + dy)
    }

    /**
     * Computes a displacement vector for domain warping.
     *
     * Uses two calls to perlin2D with different seed offsets to generate the x
     * and y displacement components.
     *
     * @param x The input x coordinate.
     * @param y The input y coordinate.
     * @param warpFactor Scales the magnitude of the displacement.
     * @return A Pair (dx, dy) representing the displacement vector.
     */
    private fun computeDisplacement(x: Float, y: Float, warpFactor: Float): Pair<Float, Float> {
        val dx = perlin2D(x, y, seed + 100) * warpFactor
        val dy = perlin2D(x, y, seed + 200) * warpFactor
        return Pair(dx, dy)
    }

    /**
     * Computes basic 2D Perlin-like noise for a given coordinate.
     *
     * Steps:
     * 1. Finds the integer lattice cell containing (x, y).
     * 2. Determines the local coordinates within that cell.
     * 3. Computes pseudo-random gradients at each of the cell's four corners.
     * 4. Calculates the dot product between these gradients and the displacement vectors.
     * 5. Uses a fade function to smoothly interpolate between the dot products.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param seed The seed for generating the pseudo-random gradients.
     * @return A noise value roughly in the range -1 to 1.
     */
    private fun perlin2D(x: Float, y: Float, seed: Int): Float {
        // Hash function to compute a pseudo-random value from integer coordinates.
        fun hash(i: Int, j: Int): Float {
            var h = i * 374761393 + j * 668265263 + seed * 31
            h = (h xor (h shr 13)) * 1274126177
            return (h xor (h shr 16)).toFloat()
        }

        // Determine lattice cell boundaries.
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val x1 = x0 + 1
        val y1 = y0 + 1

        // Relative (local) coordinates in the cell.
        val lx = x - x0
        val ly = y - y0

        // Apply the fade function for smooth interpolation.
        val sx = fade(lx)
        val sy = fade(ly)

        // Compute dot products for the four corners.
        val g00 = pseudoRandomGradient(hash(x0, y0), lx, ly)
        val g10 = pseudoRandomGradient(hash(x1, y0), lx - 1, ly)
        val g01 = pseudoRandomGradient(hash(x0, y1), lx, ly - 1)
        val g11 = pseudoRandomGradient(hash(x1, y1), lx - 1, ly - 1)

        // Interpolate across x, then between the two results along y.
        val nx0 = lerp(g00, g10, sx)
        val nx1 = lerp(g01, g11, sx)
        return lerp(nx0, nx1, sy)
    }

    /**
     * Fade function for smooth interpolation.
     *
     * Implements the 6t^5 - 15t^4 + 10t^3 function.
     *
     * @param t A value typically in [0, 1].
     * @return The smoothly interpolated value.
     */
    private fun fade(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)

    /**
     * Computes the dot product between a pseudo-random gradient (from a hash)
     * and a displacement vector (x, y) from a lattice point.
     *
     * The hash-derived value is converted into an angle (in radians),
     * then a unit vector is computed and the dot product with (x, y) is returned.
     *
     * @param hashVal The value from the hash function.
     * @param x The x component of the displacement.
     * @param y The y component of the displacement.
     * @return The dot product.
     */
    private fun pseudoRandomGradient(hashVal: Float, x: Float, y: Float): Float {
        val angle = (hashVal % 360) * (PI.toFloat() / 180f)
        val gradX = cos(angle)
        val gradY = sin(angle)
        return gradX * x + gradY * y
    }

    /**
     * Linearly interpolates between values a and b using factor t.
     *
     * @param a The start value.
     * @param b The end value.
     * @param t The interpolation parameter (0 ≤ t ≤ 1).
     * @return The interpolated value.
     */
    private fun lerp(a: Float, b: Float, t: Float): Float = a + t * (b - a)
}
