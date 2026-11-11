package com.example.shapelab

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

val PALETTE = intArrayOf(
    0xFF3DDC84.toInt(), 0xFF4FC3F7.toInt(), 0xFFFF7043.toInt(), 0xFFFFD54F.toInt(),
    0xFFBA68C8.toInt(), 0xFFEF5350.toInt(), 0xFFF5F5F5.toInt(), 0xFF90A4AE.toInt()
)

val ANIM_NAMES = listOf(
    "Yo'q", "Aylanish Y", "Aylanish X", "Sakrash",
    "Orbita", "Kattalashish", "Tebranish", "Siljish"
)

class Obj(var shape: Int) {
    var name = ""
    var px = 0f
    var py = 0.5f
    var pz = 0f
    var rx = 0f
    var ry = 0f
    var rz = 0f
    var sx = 1f
    var sy = 1f
    var sz = 1f
    var color = PALETTE[0]
    var anim = 0
    var speed = 1f
    var amp = 1f
}

class Pose {
    var x = 0f
    var y = 0f
    var z = 0f
    var rx = 0f
    var ry = 0f
    var rz = 0f
    var sx = 1f
    var sy = 1f
    var sz = 1f
}

fun pose(o: Obj, t: Float, p: Pose) {
    p.x = o.px
    p.y = o.py
    p.z = o.pz
    p.rx = o.rx
    p.ry = o.ry
    p.rz = o.rz
    p.sx = o.sx
    p.sy = o.sy
    p.sz = o.sz
    val s = o.speed
    val a = o.amp
    when (o.anim) {
        1 -> p.ry += t * 60f * s
        2 -> p.rx += t * 60f * s
        3 -> p.y += abs(sin(t * s * 2.5f)) * a
        4 -> {
            val ang = t * s
            val r = a * 1.2f
            p.x += r * (cos(ang) - 1f)
            p.z += r * sin(ang)
        }
        5 -> {
            val k = 1f + 0.35f * a * sin(t * s * 3f)
            p.sx *= k
            p.sy *= k
            p.sz *= k
        }
        6 -> p.rz += sin(t * s * 2f) * 30f * a
        7 -> p.x += sin(t * s * 1.5f) * a * 1.5f
    }
}

class Scene {
    val lock = Any()
    val objs = ArrayList<Obj>()
    var selected = -1
    var playing = true
    var time = 0f

    fun sel(): Obj? = if (selected >= 0 && selected < objs.size) objs[selected] else null

    fun add(shape: Int): Obj {
        val i = objs.size
        val o = Obj(shape)
        o.name = Shapes.names[shape]
        o.px = ((i % 5) - 2) * 1.3f
        o.pz = -(i / 5) * 1.3f
        o.color = PALETTE[i % PALETTE.size]
        objs.add(o)
        selected = objs.size - 1
        return o
    }

    fun removeSelected() {
        if (selected >= 0 && selected < objs.size) {
            objs.removeAt(selected)
            selected = if (objs.isEmpty()) -1 else minOf(selected, objs.size - 1)
        }
    }

    fun duplicateSelected() {
        val s = sel() ?: return
        val o = Obj(s.shape)
        o.name = s.name
        o.px = s.px + 0.6f
        o.py = s.py
        o.pz = s.pz + 0.6f
        o.rx = s.rx
        o.ry = s.ry
        o.rz = s.rz
        o.sx = s.sx
        o.sy = s.sy
        o.sz = s.sz
        o.color = s.color
        o.anim = s.anim
        o.speed = s.speed
        o.amp = s.amp
        objs.add(o)
        selected = objs.size - 1
    }
}
