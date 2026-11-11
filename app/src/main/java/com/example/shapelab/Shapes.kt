package com.example.shapelab

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val HP = (PI / 2.0).toFloat()
private val TWO = (2.0 * PI).toFloat()

class MB {
    private var a = FloatArray(8192)
    private var n = 0
    var cx = 0f
    var cy = 0f
    var cz = 0f

    private fun put(v: Float) {
        if (n == a.size) a = a.copyOf(n * 2)
        a[n] = v
        n++
    }

    private fun vert(p: FloatArray, nx: Float, ny: Float, nz: Float) {
        put(p[0])
        put(p[1])
        put(p[2])
        put(nx)
        put(ny)
        put(nz)
    }

    // Tekis uchburchak: normal markazdan tashqariga qaratiladi
    fun tri(p: FloatArray, q: FloatArray, r: FloatArray) {
        val ux = q[0] - p[0]
        val uy = q[1] - p[1]
        val uz = q[2] - p[2]
        val vx = r[0] - p[0]
        val vy = r[1] - p[1]
        val vz = r[2] - p[2]
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val len = sqrt(nx * nx + ny * ny + nz * nz)
        if (len < 1e-9f) return
        nx /= len
        ny /= len
        nz /= len
        val mx = (p[0] + q[0] + r[0]) / 3f - cx
        val my = (p[1] + q[1] + r[1]) / 3f - cy
        val mz = (p[2] + q[2] + r[2]) / 3f - cz
        if (nx * mx + ny * my + nz * mz < 0f) {
            nx = -nx
            ny = -ny
            nz = -nz
        }
        vert(p, nx, ny, nz)
        vert(q, nx, ny, nz)
        vert(r, nx, ny, nz)
    }

    fun quad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray) {
        tri(a, b, c)
        tri(a, c, d)
    }

    // Silliq uchburchak: har bir uchun normal beriladi
    fun triS(
        p: FloatArray, np: FloatArray,
        q: FloatArray, nq: FloatArray,
        r: FloatArray, nr: FloatArray
    ) {
        vert(p, np[0], np[1], np[2])
        vert(q, nq[0], nq[1], nq[2])
        vert(r, nr[0], nr[1], nr[2])
    }

    fun quadS(
        a: FloatArray, na: FloatArray,
        b: FloatArray, nb: FloatArray,
        c: FloatArray, nc: FloatArray,
        d: FloatArray, nd: FloatArray
    ) {
        triS(a, na, b, nb, c, nc)
        triS(a, na, c, nc, d, nd)
    }

    fun build(): FloatArray = a.copyOf(n)
}

private fun v3(x: Float, y: Float, z: Float): FloatArray = floatArrayOf(x, y, z)

private fun pt(r: Float, y: Float): FloatArray = floatArrayOf(r, y)

private fun box(mb: MB, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float) {
    mb.cx = (x0 + x1) / 2f
    mb.cy = (y0 + y1) / 2f
    mb.cz = (z0 + z1) / 2f
    val p = arrayOf(
        v3(x0, y0, z0), v3(x1, y0, z0), v3(x1, y1, z0), v3(x0, y1, z0),
        v3(x0, y0, z1), v3(x1, y0, z1), v3(x1, y1, z1), v3(x0, y1, z1)
    )
    mb.quad(p[0], p[1], p[2], p[3])
    mb.quad(p[4], p[5], p[6], p[7])
    mb.quad(p[0], p[1], p[5], p[4])
    mb.quad(p[3], p[2], p[6], p[7])
    mb.quad(p[0], p[3], p[7], p[4])
    mb.quad(p[1], p[2], p[6], p[5])
}

// Aylanish sirti: har bir chiziq (r, y) nuqtalar ro'yxati
private fun lathe(mb: MB, strips: List<List<FloatArray>>, segs: Int, flat: Boolean, rot: Float) {
    var minY = 1e9f
    var maxY = -1e9f
    for (s in strips) {
        for (p in s) {
            minY = min(minY, p[1])
            maxY = max(maxY, p[1])
        }
    }
    mb.cx = 0f
    mb.cy = (minY + maxY) / 2f
    mb.cz = 0f

    for (s in strips) {
        val m = s.size
        val nr = FloatArray(m)
        val ny = FloatArray(m)
        for (i in 0 until m) {
            val p0 = s[max(i - 1, 0)]
            val p1 = s[min(i + 1, m - 1)]
            val tr = p1[0] - p0[0]
            val ty = p1[1] - p0[1]
            val len = sqrt(tr * tr + ty * ty)
            if (len < 1e-6f) {
                nr[i] = 1f
                ny[i] = 0f
            } else {
                nr[i] = ty / len
                ny[i] = -tr / len
            }
        }
        for (k in 0 until segs) {
            val a0 = rot + TWO * k / segs
            val a1 = rot + TWO * (k + 1) / segs
            val c0 = cos(a0)
            val s0 = sin(a0)
            val c1 = cos(a1)
            val s1 = sin(a1)
            for (i in 0 until m - 1) {
                val pa = s[i]
                val pb = s[i + 1]
                val A = v3(pa[0] * c0, pa[1], pa[0] * s0)
                val B = v3(pa[0] * c1, pa[1], pa[0] * s1)
                val C = v3(pb[0] * c1, pb[1], pb[0] * s1)
                val D = v3(pb[0] * c0, pb[1], pb[0] * s0)
                if (flat) {
                    mb.quad(A, B, C, D)
                } else {
                    val nA = v3(nr[i] * c0, ny[i], nr[i] * s0)
                    val nB = v3(nr[i] * c1, ny[i], nr[i] * s1)
                    val nC = v3(nr[i + 1] * c1, ny[i + 1], nr[i + 1] * s1)
                    val nD = v3(nr[i + 1] * c0, ny[i + 1], nr[i + 1] * s0)
                    mb.quadS(A, nA, B, nB, C, nC, D, nD)
                }
            }
        }
    }
}

private fun arc(cr: Float, cy: Float, rad: Float, a0: Float, a1: Float, n: Int): List<FloatArray> {
    val l = ArrayList<FloatArray>()
    for (i in 0..n) {
        val a = a0 + (a1 - a0) * i / n
        l.add(pt(cr + rad * cos(a), cy + rad * sin(a)))
    }
    return l
}

private fun cylStrips(r: Float, h: Float): List<List<FloatArray>> = listOf(
    listOf(pt(0f, -h), pt(r, -h)),
    listOf(pt(r, -h), pt(r, h)),
    listOf(pt(r, h), pt(0f, h))
)

private fun torus(
    mb: MB, big: Float, small: Float, ns: Int, nr: Int,
    arcLen: Float, vertical: Boolean, yOff: Float
) {
    fun pos(u: Float, v: Float): FloatArray {
        val rr = big + small * cos(v)
        return if (vertical) {
            v3(rr * cos(u), rr * sin(u) + yOff, small * sin(v))
        } else {
            v3(rr * cos(u), small * sin(v) + yOff, rr * sin(u))
        }
    }
    fun nrm(u: Float, v: Float): FloatArray {
        return if (vertical) {
            v3(cos(v) * cos(u), cos(v) * sin(u), sin(v))
        } else {
            v3(cos(v) * cos(u), sin(v), cos(v) * sin(u))
        }
    }
    for (i in 0 until ns) {
        val u0 = arcLen * i / ns
        val u1 = arcLen * (i + 1) / ns
        for (j in 0 until nr) {
            val v0 = TWO * j / nr
            val v1 = TWO * (j + 1) / nr
            mb.quadS(
                pos(u0, v0), nrm(u0, v0),
                pos(u1, v0), nrm(u1, v0),
                pos(u1, v1), nrm(u1, v1),
                pos(u0, v1), nrm(u0, v1)
            )
        }
    }
}

private fun spring(mb: MB) {
    val turns = 4
    val ns = 24 * turns
    val nr = 8
    val bigR = 0.3f
    val tubeR = 0.07f
    val height = 1.0f
    val total = TWO * turns
    val pts = Array(ns + 1) { Array(nr) { FloatArray(3) } }
    val nrm = Array(ns + 1) { Array(nr) { FloatArray(3) } }
    for (i in 0..ns) {
        val t = total * i / ns
        val ct = cos(t)
        val st = sin(t)
        val px = bigR * ct
        val py = height * (i.toFloat() / ns) - height / 2f
        val pz = bigR * st
        var tx = -bigR * st
        var ty = height / total
        var tz = bigR * ct
        val tl = sqrt(tx * tx + ty * ty + tz * tz)
        tx /= tl
        ty /= tl
        tz /= tl
        var nx = -ct
        var ny = 0f
        var nz = -st
        val d = nx * tx + ny * ty + nz * tz
        nx -= d * tx
        ny -= d * ty
        nz -= d * tz
        val nl = sqrt(nx * nx + ny * ny + nz * nz)
        nx /= nl
        ny /= nl
        nz /= nl
        val bx = ty * nz - tz * ny
        val by = tz * nx - tx * nz
        val bz = tx * ny - ty * nx
        for (j in 0 until nr) {
            val v = TWO * j / nr
            val cv = cos(v)
            val sv = sin(v)
            val ox = cv * nx + sv * bx
            val oy = cv * ny + sv * by
            val oz = cv * nz + sv * bz
            pts[i][j] = floatArrayOf(px + tubeR * ox, py + tubeR * oy, pz + tubeR * oz)
            nrm[i][j] = floatArrayOf(ox, oy, oz)
        }
    }
    for (i in 0 until ns) {
        for (j in 0 until nr) {
            val j2 = (j + 1) % nr
            mb.quadS(
                pts[i][j], nrm[i][j],
                pts[i + 1][j], nrm[i + 1][j],
                pts[i + 1][j2], nrm[i + 1][j2],
                pts[i][j2], nrm[i][j2]
            )
        }
    }
}

private fun starPrism(mb: MB) {
    val n = 10
    val pts = ArrayList<FloatArray>()
    for (i in 0 until n) {
        val ang = HP + TWO * i / n
        val rad = if (i % 2 == 0) 0.5f else 0.2f
        pts.add(floatArrayOf(rad * cos(ang), rad * sin(ang)))
    }
    val z = 0.12f
    mb.cx = 0f
    mb.cy = 0f
    mb.cz = 0f
    val front = v3(0f, 0f, z)
    val back = v3(0f, 0f, -z)
    for (i in 0 until n) {
        val a = pts[i]
        val b = pts[(i + 1) % n]
        val af = v3(a[0], a[1], z)
        val bf = v3(b[0], b[1], z)
        val ab = v3(a[0], a[1], -z)
        val bb = v3(b[0], b[1], -z)
        mb.tri(front, af, bf)
        mb.tri(back, ab, bb)
        mb.quad(af, bf, bb, ab)
    }
}

private fun poly(mb: MB, v: Array<FloatArray>, faces: Array<IntArray>) {
    mb.cx = 0f
    mb.cy = 0f
    mb.cz = 0f
    for (f in faces) {
        mb.tri(v[f[0]], v[f[1]], v[f[2]])
    }
}

object Shapes {

    val names = listOf(
        "Kub", "Shar", "Silindr", "Konus", "Tekislik",
        "Torus", "Piramida", "Uchburchakli prizma", "Oltiburchakli prizma", "Yarim shar",
        "Kapsula", "Disk", "Quvur", "Tetraedr", "Oktaedr",
        "Ikosaedr", "Kesik konus", "Prujina", "Zina", "Yulduz",
        "Krest", "Kamon", "Burchak", "Vaza", "Strelka"
    )

    fun build(id: Int): FloatArray {
        val mb = MB()
        when (id) {
            0 -> box(mb, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f)
            1 -> lathe(mb, listOf(arc(0f, 0f, 0.5f, -HP, HP, 14)), 28, false, 0f)
            2 -> lathe(mb, cylStrips(0.5f, 0.5f), 32, false, 0f)
            3 -> lathe(
                mb,
                listOf(
                    listOf(pt(0f, -0.5f), pt(0.5f, -0.5f)),
                    listOf(pt(0.5f, -0.5f), pt(0f, 0.5f))
                ),
                32, false, 0f
            )
            4 -> box(mb, -0.5f, -0.02f, -0.5f, 0.5f, 0.02f, 0.5f)
            5 -> torus(mb, 0.36f, 0.14f, 36, 16, TWO, false, 0f)
            6 -> lathe(
                mb,
                listOf(
                    listOf(pt(0f, -0.5f), pt(0.7f, -0.5f)),
                    listOf(pt(0.7f, -0.5f), pt(0f, 0.5f))
                ),
                4, true, HP / 2f
            )
            7 -> lathe(mb, cylStrips(0.55f, 0.4f), 3, true, HP)
            8 -> lathe(mb, cylStrips(0.5f, 0.4f), 6, true, 0f)
            9 -> lathe(
                mb,
                listOf(
                    listOf(pt(0f, -0.25f), pt(0.5f, -0.25f)),
                    arc(0f, -0.25f, 0.5f, 0f, HP, 10)
                ),
                28, false, 0f
            )
            10 -> {
                val s = ArrayList<FloatArray>()
                s.addAll(arc(0f, -0.2f, 0.3f, -HP, 0f, 8))
                s.addAll(arc(0f, 0.2f, 0.3f, 0f, HP, 8))
                lathe(mb, listOf(s), 28, false, 0f)
            }
            11 -> lathe(mb, cylStrips(0.5f, 0.04f), 40, false, 0f)
            12 -> lathe(
                mb,
                listOf(
                    listOf(pt(0.5f, -0.5f), pt(0.5f, 0.5f)),
                    listOf(pt(0.5f, 0.5f), pt(0.3f, 0.5f)),
                    listOf(pt(0.3f, 0.5f), pt(0.3f, -0.5f)),
                    listOf(pt(0.3f, -0.5f), pt(0.5f, -0.5f))
                ),
                36, false, 0f
            )
            13 -> {
                val s = 0.38f
                val v = arrayOf(
                    floatArrayOf(s, s, s), floatArrayOf(s, -s, -s),
                    floatArrayOf(-s, s, -s), floatArrayOf(-s, -s, s)
                )
                val f = arrayOf(
                    intArrayOf(0, 1, 2), intArrayOf(0, 3, 1),
                    intArrayOf(0, 2, 3), intArrayOf(1, 3, 2)
                )
                poly(mb, v, f)
            }
            14 -> {
                val r = 0.6f
                val v = arrayOf(
                    floatArrayOf(r, 0f, 0f), floatArrayOf(-r, 0f, 0f),
                    floatArrayOf(0f, r, 0f), floatArrayOf(0f, -r, 0f),
                    floatArrayOf(0f, 0f, r), floatArrayOf(0f, 0f, -r)
                )
                val f = arrayOf(
                    intArrayOf(0, 2, 4), intArrayOf(2, 1, 4),
                    intArrayOf(1, 3, 4), intArrayOf(3, 0, 4),
                    intArrayOf(0, 5, 2), intArrayOf(2, 5, 1),
                    intArrayOf(1, 5, 3), intArrayOf(3, 5, 0)
                )
                poly(mb, v, f)
            }
            15 -> {
                val g = 1.618034f
                val k = 0.289f
                val v = arrayOf(
                    floatArrayOf(-1f * k, g * k, 0f), floatArrayOf(1f * k, g * k, 0f),
                    floatArrayOf(-1f * k, -g * k, 0f), floatArrayOf(1f * k, -g * k, 0f),
                    floatArrayOf(0f, -1f * k, g * k), floatArrayOf(0f, 1f * k, g * k),
                    floatArrayOf(0f, -1f * k, -g * k), floatArrayOf(0f, 1f * k, -g * k),
                    floatArrayOf(g * k, 0f, -1f * k), floatArrayOf(g * k, 0f, 1f * k),
                    floatArrayOf(-g * k, 0f, -1f * k), floatArrayOf(-g * k, 0f, 1f * k)
                )
                val f = arrayOf(
                    intArrayOf(0, 11, 5), intArrayOf(0, 5, 1), intArrayOf(0, 1, 7),
                    intArrayOf(0, 7, 10), intArrayOf(0, 10, 11),
                    intArrayOf(1, 5, 9), intArrayOf(5, 11, 4), intArrayOf(11, 10, 2),
                    intArrayOf(10, 7, 6), intArrayOf(7, 1, 8),
                    intArrayOf(3, 9, 4), intArrayOf(3, 4, 2), intArrayOf(3, 2, 6),
                    intArrayOf(3, 6, 8), intArrayOf(3, 8, 9),
                    intArrayOf(4, 9, 5), intArrayOf(2, 4, 11), intArrayOf(6, 2, 10),
                    intArrayOf(8, 6, 7), intArrayOf(9, 8, 1)
                )
                poly(mb, v, f)
            }
            16 -> lathe(
                mb,
                listOf(
                    listOf(pt(0f, -0.5f), pt(0.5f, -0.5f)),
                    listOf(pt(0.5f, -0.5f), pt(0.25f, 0.5f)),
                    listOf(pt(0.25f, 0.5f), pt(0f, 0.5f))
                ),
                32, false, 0f
            )
            17 -> spring(mb)
            18 -> {
                for (i in 0 until 4) {
                    val x0 = -0.5f + 0.25f * i
                    box(mb, x0, -0.5f, -0.4f, x0 + 0.25f, -0.5f + 0.25f * (i + 1), 0.4f)
                }
            }
            19 -> starPrism(mb)
            20 -> {
                box(mb, -0.5f, -0.17f, -0.1f, 0.5f, 0.17f, 0.1f)
                box(mb, -0.17f, -0.5f, -0.1f, 0.17f, 0.5f, 0.1f)
            }
            21 -> torus(mb, 0.35f, 0.1f, 28, 12, (PI).toFloat(), true, -0.17f)
            22 -> {
                box(mb, -0.5f, -0.5f, -0.25f, 0.5f, -0.2f, 0.25f)
                box(mb, -0.5f, -0.2f, -0.25f, -0.2f, 0.5f, 0.25f)
            }
            23 -> lathe(
                mb,
                listOf(
                    listOf(pt(0f, -0.5f), pt(0.25f, -0.5f)),
                    listOf(
                        pt(0.25f, -0.5f), pt(0.33f, -0.38f), pt(0.43f, -0.1f),
                        pt(0.39f, 0.12f), pt(0.22f, 0.3f), pt(0.16f, 0.42f), pt(0.2f, 0.5f)
                    ),
                    listOf(pt(0.2f, 0.5f), pt(0.12f, 0.5f)),
                    listOf(pt(0.12f, 0.5f), pt(0.12f, 0.3f)),
                    listOf(pt(0.12f, 0.3f), pt(0f, 0.3f))
                ),
                32, false, 0f
            )
            24 -> lathe(
                mb,
                listOf(
                    listOf(pt(0f, -0.5f), pt(0.1f, -0.5f)),
                    listOf(pt(0.1f, -0.5f), pt(0.1f, 0.2f)),
                    listOf(pt(0.1f, 0.2f), pt(0.25f, 0.2f)),
                    listOf(pt(0.25f, 0.2f), pt(0f, 0.5f))
                ),
                24, false, 0f
            )
            else -> box(mb, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f)
        }
        return mb.build()
    }
}
