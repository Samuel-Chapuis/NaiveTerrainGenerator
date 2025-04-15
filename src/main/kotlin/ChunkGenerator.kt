import Noise.Utile.computeGradient
import Noise.Utile.domainWarp
import Noise.Utile.perlin2D

// Refactored Noise class.
class ChunkGenerator(
    var seed: Int = 1,
    var chunkSize: Int = 16,
    var scale: Float = 0.002f,
    var octaves: Int = 4,
    var persistence: Float = 0.6f,
    var lacunarity: Float = 2.5f,
    var warpFactor: Float = 0.5f,
    var mean: Int = 64,
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
        seed: Int = this.seed,
        chunkSize: Int = this.chunkSize,
        scale: Float = this.scale,
        octaves: Int = this.octaves,
        persistence: Float = this.persistence,
        lacunarity: Float = this.lacunarity,
        mean: Int = this.mean,
    ): Array<IntArray> {
        val noiseArray = Array(chunkSize) { IntArray(chunkSize) }

        for (y in 0 until chunkSize) {
            for (x in 0 until chunkSize) {
                val worldX = chunkX * chunkSize + x
                val worldY = chunkY * chunkSize + y

                val lowFreq = generateNoiseLayer(worldX, worldY, scale * 0.5f, 2, 0)
                val midFreq = generateNoiseLayer(worldX, worldY, scale, 3, 10)
                val highFreq = generateNoiseLayer(worldX, worldY, scale * 2.0f, 4, 20)

                val total = lowFreq * 0.5f + midFreq * 0.3f + highFreq * 0.2f
                val normalized = ((total + 1) / 2 * 255).toInt()
                val adjusted = normalized + (64 - 128)
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
        seed: Int = this.seed,
        chunkSize: Int = this.chunkSize,
        scale: Float = this.scale,
        octaves: Int = this.octaves,
        persistence: Float = this.persistence,
        lacunarity: Float = this.lacunarity,
        mean: Int = this.mean,
    ): Pair<Array<IntArray>, Array<Array<Pair<Float, Float>>>> {
        val noiseMap = generateChunkNoise(chunkX, chunkY, seed, chunkSize, scale, octaves, persistence, lacunarity, mean)
        val gradientMap = computeGradient(noiseMap)
        return Pair(noiseMap, gradientMap)
    }


    private fun generateNoiseLayer(
        worldX: Int, worldY: Int, scale: Float, octaves: Int, seedOffset: Int
    ): Float {
        var total = 0f
        var amplitude = 1f
        var frequency = 1f
        repeat(octaves) {
            val baseX = worldX * scale * frequency
            val baseY = worldY * scale * frequency
            val (warpedX, warpedY) = domainWarp(baseX, baseY, warpFactor, seed * (200 + seedOffset))
            total += perlin2D(warpedX, warpedY, seed + seedOffset) * amplitude

            amplitude *= persistence
            frequency *= lacunarity
        }
        return total
    }
}
