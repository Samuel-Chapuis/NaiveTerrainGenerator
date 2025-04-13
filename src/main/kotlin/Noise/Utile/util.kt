package Noise.Utile

/**
 * Computes the gradient of a height map (noise map) using finite differences.
 *
 * For every pixel, the gradient is approximated by the partial derivatives in x and y.
 * Central differences are used for inner pixels, with forward/backward differences
 * for edge pixels.
 *
 * @param heightMap A 2D array of noise values (each between 0 and 255).
 * @return A 2D array of Pairs, where each Pair contains (gradientX, gradientY).
 */
fun computeGradient(heightMap: Array<IntArray>): Array<Array<Pair<Float, Float>>> {
    val rows = heightMap.size
    val cols = heightMap[0].size
    val gradientMap = Array(rows) { Array(cols) { Pair(0f, 0f) } }
    for (y in 0 until rows) {
        for (x in 0 until cols) {
            // Compute x-derivative: use forward difference on left edge, backward on right edge, and central otherwise.
            val dx = when (x) {
                0 -> (heightMap[y][x + 1] - heightMap[y][x]).toFloat()
                cols - 1 -> (heightMap[y][x] - heightMap[y][x - 1]).toFloat()
                else -> ((heightMap[y][x + 1] - heightMap[y][x - 1]) / 2f)
            }
            // Compute y-derivative similarly.
            val dy = when (y) {
                0 -> (heightMap[y + 1][x] - heightMap[y][x]).toFloat()
                rows - 1 -> (heightMap[y][x] - heightMap[y - 1][x]).toFloat()
                else -> ((heightMap[y + 1][x] - heightMap[y - 1][x]) / 2f)
            }
            gradientMap[y][x] = Pair(dx, dy)
        }
    }
    return gradientMap
}