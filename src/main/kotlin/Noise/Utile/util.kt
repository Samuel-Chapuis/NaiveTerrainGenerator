package Noise.Utile

/**
 * Fade function for smooth interpolation.
 *
 * Implements the 6t^5 - 15t^4 + 10t^3 function.
 *
 * @param t A value typically in [0, 1].
 * @return The smoothly interpolated value.
 */
fun fade(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)


/*
 * Linearly interpolates between values a and b using factor t.
 *
 * @param a The start value.
 * @param b The end value.
 * @param t The interpolation parameter (0 ≤ t ≤ 1).
 * @return The interpolated value.
 */
fun lerp(a: Float, b: Float, t: Float): Float = a + t * (b - a)