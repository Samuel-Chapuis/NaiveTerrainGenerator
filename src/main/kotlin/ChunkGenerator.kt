import Noise.Utile.computeGradient
import Noise.Utile.domainWarp
import Noise.Utile.perlin2D

// Refactored Noise class.
class ChunkGenerator(
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
                    val (warpedX, warpedY) = domainWarp(baseX, baseY, warpFactor, seed*200)

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

}
