package Noise.Utile

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Computes basic 2D Voronoi-like noise for a given coordinate.
 *
 * For each cell in a 3×3 grid around the point, a feature point is generated
 * with a pseudo-random offset, and the function returns the distance to the closest
 * feature point normalized to roughly be in the range -1 to 1.
 *
 * @param x The x coordinate.
 * @param y The y coordinate.
 * @param seed The seed for generating the pseudo-random feature points.
 * @return A noise value roughly in the range -1 to 1.
 */
fun voronoi2D(x: Float, y: Float, seed: Int): Float {
    // Fonction de hachage inspirée de l'exemple Perlin afin de générer une valeur pseudo-aléatoire.
    fun hash(i: Int, j: Int, seed: Int): Int {
        var h = i * 374761393 + j * 668265263 + seed * 31
        h = (h xor (h shr 13)) * 1274126177
        return h xor (h shr 16)
    }

    // Renvoie une valeur Float dans l’intervalle [0, 1) basée sur les coordonnées et la seed.
    fun randomFloat(i: Int, j: Int, seed: Int): Float {
        val h = hash(i, j, seed)
        // On utilise les 31 bits de la valeur h en valeur absolue et on normalise par la valeur maximale.
        return ((h and 0x7fffffff).toFloat()) / 2147483647f
    }

    // Détermine la cellule entière contenant le point (x, y).
    val xi = floor(x).toInt()
    val yi = floor(y).toInt()

    // F1 sera la distance minimale (au carré) entre (x,y) et l'ensemble des feature points.
    var f1Squared = Float.MAX_VALUE

    // Itération sur les cellules voisines (3 x 3) autour de la cellule contenant (x, y).
    for (i in (xi - 1)..(xi + 1)) {
        for (j in (yi - 1)..(yi + 1)) {
            // Pour chaque cellule, on génère un point aléatoire :
            // l'offset en x et y est obtenu grâce à randomFloat.
            val offsetX = randomFloat(i, j, seed)
            val offsetY = randomFloat(i, j, seed + 1)
            val featureX = i + offsetX
            val featureY = j + offsetY

            // Calcul de la distance euclidienne au carré entre (x, y) et le point de la cellule.
            val dx = featureX - x
            val dy = featureY - y
            val distSquared = dx * dx + dy * dy

            // On conserve la plus petite distance trouvée.
            if (distSquared < f1Squared) {
                f1Squared = distSquared
            }
        }
    }

    // On prend la racine carrée pour obtenir la distance Euclidienne.
    val f1 = sqrt(f1Squared)

    // Normalisation :
    // La distance f1 varie théoriquement de 0 (point collé à un feature point)
    // à sqrt(2) (point au milieu d'une cellule, au maximum de la distance dans une cellule).
    // On mappe f1 de [0, sqrt(2)] vers [-1, 1] :
    val normalized = 2 * (f1 / sqrt(2f)) - 1

    return normalized
}
