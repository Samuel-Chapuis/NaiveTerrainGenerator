import Noise.Utile.computeGradient
import Noise.Utile.domainWarp
import Noise.Utile.perlin2D
import Noise.Utile.voronoi2D

// Refactored Noise class.
class ChunkGenerator(
    var seed: Int = 1,
    var chunkSize: Int = 16,
) {

    fun generateChunkNoise(
        chunkX: Int,
        chunkY: Int,
        seed: Int = this.seed,
        chunkSize: Int = this.chunkSize,
    ): Array<IntArray> {
        val noiseArray = Array(chunkSize) { IntArray(chunkSize) }

        for (y in 0 until chunkSize) {
            for (x in 0 until chunkSize) {

                val Hb = baseNoise(chunkX * chunkSize + x, chunkY * chunkSize + y, seed, chunkSize)

                noiseArray[y][x] = Hb.coerceIn(0, 255)
            }
        }

        return noiseArray
    }


    fun baseNoise(
        worldX: Int,
        worldY: Int,
        seed: Int = this.seed,
        chunkSize: Int = this.chunkSize,
    ): Int {
        val scale = 0.002f
        val warpFactor = 0.5f
        val persistence = 0.6f
        val lacunarity = 2.5f
        val maxValue = 255f
        val minValue = 0f
        val meanTarget = 64f

        val lowFreq = generateNoiseLayer(
            worldX, worldY,
            minValue = minValue, maxValue = maxValue, meanTarget = meanTarget,
            scale = scale * 0.5f,
            octaves = 2, seedOffset = 0,
            warpFactor = warpFactor, persistence = persistence, lacunarity = lacunarity
        )

        val midFreq = generateNoiseLayer(
            worldX, worldY,
            minValue = minValue, maxValue = maxValue, meanTarget = meanTarget,
            scale = scale,
            octaves = 3, seedOffset = 10,
            warpFactor = warpFactor, persistence = persistence, lacunarity = lacunarity
        )

        val highFreq = generateNoiseLayer(
            worldX, worldY,
            minValue = minValue, maxValue = maxValue, meanTarget = meanTarget,
            scale = scale * 2.0f,
            octaves = 4, seedOffset = 20,
            warpFactor = warpFactor, persistence = persistence, lacunarity = lacunarity
        )

        // Ton total est déjà dans une échelle [0, 255]
        val total = lowFreq * 0.5f + midFreq * 0.3f + highFreq * 0.2f

        return total.toInt().coerceIn(0, 255)
    }


    fun generateChunkNoiseAndGradient(
        chunkX: Int,
        chunkY: Int,
        seed: Int = this.seed,
        chunkSize: Int = this.chunkSize,
    ): Pair<Array<IntArray>, Array<Array<Pair<Float, Float>>>> {
        val noiseMap = generateChunkNoise(chunkX, chunkY, seed, chunkSize)
        val gradientMap = computeGradient(noiseMap)
        return Pair(noiseMap, gradientMap)
    }


    private fun generateNoiseLayer(
        worldX: Int,
        worldY: Int,
        noiseType: Int = 0,      // 0 (default) -> perlin | 1 -> voronoi
        minValue: Float = -1f,
        maxValue: Float = 1f,
        meanTarget: Float = 0f,
        scale: Float,
        octaves: Int,
        seedOffset: Int,
        warpFactor: Float = 0.5f,
        persistence: Float = 0.6f,
        lacunarity: Float = 2.5f,
    ): Float {
        var total = 0f
        var amplitude = 1f
        var frequency = 1f
        var amplitudeSum = 0f

        repeat(octaves) {
            val baseX = worldX * scale * frequency
            val baseY = worldY * scale * frequency
            val (warpedX, warpedY) = domainWarp(baseX, baseY, warpFactor, seed * (200 + seedOffset))

            val noise = when (noiseType) {
                1 -> voronoi2D(warpedX, warpedY, seed + seedOffset)
                else -> perlin2D(warpedX, warpedY, seed + seedOffset)
            }

            total += noise * amplitude
            amplitudeSum += amplitude

            amplitude *= persistence
            frequency *= lacunarity
        }

        // Normalisation entre -1 et 1 (approximative si les fonctions retournent entre -1 et 1)
        val normalized = total / amplitudeSum

        // Recentrage autour de la moyenne voulue
        val currentRange = 1f  // plage approximative [-1, 1]
        val scaled = (normalized + 1) / 2  // [0, 1]
        val rescaled = minValue + (maxValue - minValue) * scaled

        // Correction de moyenne (décalage simple)
        val shift = meanTarget - ((minValue + maxValue) / 2)
        return rescaled + shift
    }

}
