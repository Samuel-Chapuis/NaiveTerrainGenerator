package Noise.Utile

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
fun domainWarp(x: Float, y: Float, warpFactor: Float, seed: Int=1): Pair<Float, Float> {
    val (dx, dy) = computeDisplacement(x, y, warpFactor, seed)
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
fun computeDisplacement(x: Float, y: Float, warpFactor: Float, seed: Int): Pair<Float, Float> {
    val dx = perlin2D(x, y, seed + 100) * warpFactor
    val dy = perlin2D(x, y, seed + 200) * warpFactor
    return Pair(dx, dy)
}