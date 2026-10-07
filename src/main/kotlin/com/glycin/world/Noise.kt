package com.glycin.world

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

// Perlin noise in [0, 1]. Close enough to Unity's Mathf.PerlinNoise, but not the exact same numbers.
class Noise(rng: Random) {

    private val perm: IntArray = run {
        val p = (0..255).toMutableList()
        p.shuffle(rng)
        IntArray(512) { p[it and 255] }
    }

    fun sample(x: Double, y: Double): Double {
        val xf = floor(x)
        val yf = floor(y)
        val xi = xf.toInt() and 255
        val yi = yf.toInt() and 255
        val dx = x - xf
        val dy = y - yf
        val u = fade(dx)
        val v = fade(dy)

        val aa = perm[perm[xi] + yi]
        val ab = perm[perm[xi] + yi + 1]
        val ba = perm[perm[xi + 1] + yi]
        val bb = perm[perm[xi + 1] + yi + 1]

        val n = lerp(
            v,
            lerp(u, grad(aa, dx, dy), grad(ba, dx - 1, dy)),
            lerp(u, grad(ab, dx, dy - 1), grad(bb, dx - 1, dy - 1)),
        )
        return (n / MAX_AMPLITUDE * 0.5 + 0.5).coerceIn(0.0, 1.0)
    }

    private fun fade(t: Double): Double = t * t * t * (t * (t * 6 - 15) + 10)

    private fun lerp(t: Double, a: Double, b: Double): Double = a + t * (b - a)

    private fun grad(hash: Int, x: Double, y: Double): Double {
        val h = hash and 15
        return GRAD_X[h] * x + GRAD_Y[h] * y
    }

    companion object {
        // With unit gradients, 2D perlin never goes past +/- sqrt(2)/2.
        private const val MAX_AMPLITUDE = 0.70710678
        private val GRAD_X = DoubleArray(16) { cos(it * PI / 8) }
        private val GRAD_Y = DoubleArray(16) { sin(it * PI / 8) }
    }
}
