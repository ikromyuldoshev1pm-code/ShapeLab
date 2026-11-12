package com.example.shapelab

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class Cam {
    var yaw = 35f
    var pitch = 25f
    var dist = 9f
    var tx = 0f
    var ty = 0.5f
    var tz = 0f
}

private val VS = """
    attribute vec3 aPos;
    attribute vec3 aNorm;
    uniform mat4 uMvp;
    uniform mat4 uModel;
    varying vec3 vN;
    void main() {
        gl_Position = uMvp * vec4(aPos, 1.0);
        vN = mat3(uModel) * aNorm;
    }
""".trimIndent()

private val FS = """
    precision mediump float;
    uniform vec4 uColor;
    uniform vec3 uLight;
    uniform float uMode;
    varying vec3 vN;
    void main() {
        if (uMode > 0.5 && uMode < 1.5) {
            gl_FragColor = uColor;
        } else {
            vec3 n = normalize(vN);
            float d = max(dot(n, normalize(uLight)), 0.0);
            float d2 = max(dot(n, normalize(vec3(-0.6, -0.3, -0.5))), 0.0) * 0.25;
            vec3 c = uColor.rgb * (0.28 + 0.72 * d + d2);
            if (uMode > 1.5) {
                c = mix(c, vec3(1.0, 1.0, 0.45), 0.28);
            }
            gl_FragColor = vec4(c, 1.0);
        }
    }
""".trimIndent()

class SceneRenderer(private val scene: Scene, private val cam: Cam) : GLSurfaceView.Renderer {

    private var prog = 0
    private var aPos = 0
    private var aNorm = 0
    private var uMvp = 0
    private var uModel = 0
    private var uColor = 0
    private var uLight = 0
    private var uMode = 0

    private val meshes = HashMap<Int, FloatBuffer>()
    private val meshCount = HashMap<Int, Int>()
    private var grid: FloatBuffer? = null
    private var gridCount = 0
    private var xAxis: FloatBuffer? = null
    private var zAxis: FloatBuffer? = null

    val proj = FloatArray(16)
    val view = FloatArray(16)
    val vp = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val pose = Pose()
    private var lastNs = 0L
    var vw = 1
    var vh = 1

    private fun fb(a: FloatArray): FloatBuffer {
        val b = ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        b.put(a)
        b.position(0)
        return b
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        return s
    }

    private fun link(): Int {
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, VS))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, FS))
        GLES20.glLinkProgram(p)
        return p
    }

    private fun buildGrid() {
        val g = ArrayList<Float>()
        fun line(x0: Float, z0: Float, x1: Float, z1: Float) {
            g.add(x0); g.add(0f); g.add(z0); g.add(0f); g.add(1f); g.add(0f)
            g.add(x1); g.add(0f); g.add(z1); g.add(0f); g.add(1f); g.add(0f)
        }
        for (i in -6..6) {
            if (i != 0) {
                line(i.toFloat(), -6f, i.toFloat(), 6f)
                line(-6f, i.toFloat(), 6f, i.toFloat())
            }
        }
        grid = fb(g.toFloatArray())
        gridCount = g.size / 6
        xAxis = fb(floatArrayOf(-6f, 0f, 0f, 0f, 1f, 0f, 6f, 0f, 0f, 0f, 1f, 0f))
        zAxis = fb(floatArrayOf(0f, 0f, -6f, 0f, 1f, 0f, 0f, 0f, 6f, 0f, 1f, 0f))
    }

    private fun mesh(id: Int): FloatBuffer {
        var b = meshes[id]
        if (b == null) {
            val d = Shapes.build(id)
            b = fb(d)
            meshes[id] = b
            meshCount[id] = d.size / 6
        }
        return b
    }

    private fun drawBuf(buf: FloatBuffer, count: Int, mode: Int) {
        buf.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, buf)
        GLES20.glEnableVertexAttribArray(aPos)
        buf.position(3)
        GLES20.glVertexAttribPointer(aNorm, 3, GLES20.GL_FLOAT, false, 24, buf)
        GLES20.glEnableVertexAttribArray(aNorm)
        GLES20.glDrawArrays(mode, 0, count)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.07f, 0.09f, 0.12f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        prog = link()
        aPos = GLES20.glGetAttribLocation(prog, "aPos")
        aNorm = GLES20.glGetAttribLocation(prog, "aNorm")
        uMvp = GLES20.glGetUniformLocation(prog, "uMvp")
        uModel = GLES20.glGetUniformLocation(prog, "uModel")
        uColor = GLES20.glGetUniformLocation(prog, "uColor")
        uLight = GLES20.glGetUniformLocation(prog, "uLight")
        uMode = GLES20.glGetUniformLocation(prog, "uMode")
        buildGrid()
        lastNs = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        vw = max(w, 1)
        vh = max(h, 1)
        GLES20.glViewport(0, 0, w, h)
        Matrix.perspectiveM(proj, 0, 45f, vw.toFloat() / vh.toFloat(), 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        var dt = if (lastNs == 0L) 0f else (now - lastNs) / 1000000000f
        lastNs = now
        if (dt > 0.1f) dt = 0.1f

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(prog)

        val yr = Math.toRadians(cam.yaw.toDouble()).toFloat()
        val pr = Math.toRadians(cam.pitch.toDouble()).toFloat()
        val ex = cam.tx + cam.dist * cos(pr) * sin(yr)
        val ey = cam.ty + cam.dist * sin(pr)
        val ez = cam.tz + cam.dist * cos(pr) * cos(yr)
        Matrix.setLookAtM(view, 0, ex, ey, ez, cam.tx, cam.ty, cam.tz, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        GLES20.glUniform3f(uLight, 0.5f, 1.0f, 0.7f)

        // To'r va o'qlar
        Matrix.setIdentityM(model, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, vp, 0)
        GLES20.glUniform1f(uMode, 1f)
        GLES20.glUniform4f(uColor, 0.22f, 0.27f, 0.33f, 1f)
        val g = grid
        if (g != null) drawBuf(g, gridCount, GLES20.GL_LINES)
        GLES20.glUniform4f(uColor, 0.9f, 0.3f, 0.3f, 1f)
        val xa = xAxis
        if (xa != null) drawBuf(xa, 2, GLES20.GL_LINES)
        GLES20.glUniform4f(uColor, 0.3f, 0.5f, 0.95f, 1f)
        val za = zAxis
        if (za != null) drawBuf(za, 2, GLES20.GL_LINES)

        synchronized(scene.lock) {
            if (scene.playing) scene.time += dt
            for (i in scene.objs.indices) {
                val o = scene.objs[i]
                pose(o, scene.time, pose)
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, pose.x, pose.y, pose.z)
                Matrix.rotateM(model, 0, pose.rz, 0f, 0f, 1f)
                Matrix.rotateM(model, 0, pose.ry, 0f, 1f, 0f)
                Matrix.rotateM(model, 0, pose.rx, 1f, 0f, 0f)
                Matrix.scaleM(model, 0, pose.sx, pose.sy, pose.sz)
                Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
                GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)
                GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
                val c = o.color
                GLES20.glUniform4f(
                    uColor,
                    ((c shr 16) and 255) / 255f,
                    ((c shr 8) and 255) / 255f,
                    (c and 255) / 255f,
                    1f
                )
                GLES20.glUniform1f(uMode, if (i == scene.selected) 2f else 0f)
                val b = mesh(o.shape)
                drawBuf(b, meshCount[o.shape] ?: 0, GLES20.GL_TRIANGLES)
            }
        }
    }

    // Ekrandagi nuqtadan nur o'tkazib, qaysi shakl bosilganini topadi
    fun pick(sx: Float, sy: Float): Int {
        val inv = FloatArray(16)
        if (!Matrix.invertM(inv, 0, vp, 0)) return -1
        val nx = 2f * sx / vw - 1f
        val ny = 1f - 2f * sy / vh
        val a = floatArrayOf(nx, ny, -1f, 1f)
        val b = floatArrayOf(nx, ny, 1f, 1f)
        val a4 = FloatArray(4)
        val b4 = FloatArray(4)
        Matrix.multiplyMV(a4, 0, inv, 0, a, 0)
        Matrix.multiplyMV(b4, 0, inv, 0, b, 0)
        if (abs(a4[3]) < 1e-9f || abs(b4[3]) < 1e-9f) return -1
        val ox = a4[0] / a4[3]
        val oy = a4[1] / a4[3]
        val oz = a4[2] / a4[3]
        var dx = b4[0] / b4[3] - ox
        var dy = b4[1] / b4[3] - oy
        var dz = b4[2] / b4[3] - oz
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1e-9f) return -1
        dx /= len
        dy /= len
        dz /= len

        val pp = Pose()
        var best = -1
        var bestT = 1e9f
        synchronized(scene.lock) {
            for (i in scene.objs.indices) {
                val o = scene.objs[i]
                pose(o, scene.time, pp)
                val r = 0.62f * max(pp.sx, max(pp.sy, pp.sz))
                val cx = ox - pp.x
                val cy = oy - pp.y
                val cz = oz - pp.z
                val bb = cx * dx + cy * dy + cz * dz
                val cc = cx * cx + cy * cy + cz * cz - r * r
                val disc = bb * bb - cc
                if (disc >= 0f) {
                    val sq = sqrt(disc)
                    var t = -bb - sq
                    if (t < 0f) t = -bb + sq
                    if (t > 0f && t < bestT) {
                        bestT = t
                        best = i
                    }
                }
            }
        }
        return best
    }
}

class SceneView(ctx: Context, val scene: Scene) : GLSurfaceView(ctx) {

    val cam = Cam()
    private val rend = SceneRenderer(scene, cam)
    var onPick: ((Int) -> Unit)? = null

    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downT = 0L
    private var moved = false
    private var multi = false
    private var lastDist = 0f
    private var lastMx = 0f
    private var lastMy = 0f

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(rend)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setPlaying(p: Boolean) {
        renderMode = if (p) RENDERMODE_CONTINUOUSLY else RENDERMODE_WHEN_DIRTY
        requestRender()
    }

    private fun fingerDist(e: MotionEvent): Float {
        val dx = e.getX(0) - e.getX(1)
        val dy = e.getY(0) - e.getY(1)
        return sqrt(dx * dx + dy * dy)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                lastX = e.x
                lastY = e.y
                downT = e.eventTime
                moved = false
                multi = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (e.pointerCount >= 2) {
                    multi = true
                    lastDist = fingerDist(e)
                    lastMx = (e.getX(0) + e.getX(1)) / 2f
                    lastMy = (e.getY(0) + e.getY(1)) / 2f
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount >= 2) {
                    val d = fingerDist(e)
                    val mx = (e.getX(0) + e.getX(1)) / 2f
                    val my = (e.getY(0) + e.getY(1)) / 2f
                    if (lastDist > 1f && d > 1f) {
                        cam.dist = (cam.dist * lastDist / d).coerceIn(2f, 40f)
                    }
                    val yr = Math.toRadians(cam.yaw.toDouble()).toFloat()
                    val pr = Math.toRadians(cam.pitch.toDouble()).toFloat()
                    val rx = cos(yr)
                    val rz = -sin(yr)
                    val ux = -sin(yr) * sin(pr)
                    val uy = cos(pr)
                    val uz = -cos(yr) * sin(pr)
                    val k = cam.dist * 0.0016f
                    val dx = mx - lastMx
                    val dy = my - lastMy
                    cam.tx += (-rx * dx + ux * dy) * k
                    cam.ty += (uy * dy) * k
                    cam.tz += (-rz * dx + uz * dy) * k
                    lastDist = d
                    lastMx = mx
                    lastMy = my
                    moved = true
                } else if (!multi) {
                    val dx = e.x - lastX
                    val dy = e.y - lastY
                    if (abs(e.x - downX) > 12f || abs(e.y - downY) > 12f) moved = true
                    cam.yaw -= dx * 0.35f
                    cam.pitch = (cam.pitch + dy * 0.35f).coerceIn(-85f, 85f)
                    lastX = e.x
                    lastY = e.y
                } else {
                    lastX = e.x
                    lastY = e.y
                }
                requestRender()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (e.pointerCount == 2) {
                    val idx = if (e.actionIndex == 0) 1 else 0
                    lastX = e.getX(idx)
                    lastY = e.getY(idx)
                    multi = false
                    moved = true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!moved && !multi && e.eventTime - downT < 350L) {
                    val i = rend.pick(e.x, e.y)
                    onPick?.invoke(i)
                }
                requestRender()
            }
        }
        return true
    }
}
