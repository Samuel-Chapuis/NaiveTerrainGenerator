package Noise.Utile

import kotlin.math.floor

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
fun perlin2D(x: Float, y: Float, seed: Int): Float {
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