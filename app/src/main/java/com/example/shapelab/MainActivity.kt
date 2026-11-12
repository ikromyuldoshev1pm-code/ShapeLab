package com.example.shapelab

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {

    private val scene = Scene()
    private lateinit var sv: SceneView
    private lateinit var panel: ScrollView
    private lateinit var selTitle: TextView
    private lateinit var playBtn: TextView
    private lateinit var toggleBtn: TextView
    private val binds = ArrayList<() -> Unit>()
    private val animBtns = ArrayList<TextView>()
    private var panelVisible = true
    private var panelH = 0

    private val green = Color.rgb(140, 255, 160)
    private val neon = Color.rgb(40, 255, 100)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun chip(filled: Boolean): GradientDrawable {
        val g = GradientDrawable()
        g.cornerRadius = dp(14).toFloat()
        g.setColor(if (filled) Color.argb(120, 40, 255, 100) else Color.argb(150, 8, 14, 18))
        g.setStroke(dp(1) + 1, neon)
        return g
    }

    private fun button(text: String, onClick: () -> Unit): TextView {
        val b = TextView(this)
        b.text = text
        b.textSize = 12f
        b.gravity = Gravity.CENTER
        b.setTextColor(green)
        b.setPadding(dp(6), dp(10), dp(6), dp(10))
        b.background = chip(false)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun label(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 14f
        t.setTextColor(neon)
        t.setPadding(0, dp(12), 0, dp(4))
        return t
    }

    private fun slider(
        name: String, min: Float, max: Float,
        get: (Obj) -> Float, set: (Obj, Float) -> Unit
    ): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val tv = TextView(this)
        tv.textSize = 13f
        tv.setTextColor(green)
        val sb = SeekBar(this)
        sb.max = 1000
        var busy = false
        val refresh = {
            val o = scene.sel()
            if (o != null) {
                busy = true
                val v = get(o)
                sb.progress = ((v - min) / (max - min) * 1000f).toInt().coerceIn(0, 1000)
                tv.text = name + ": " + String.format(Locale.US, "%.2f", v)
                busy = false
            }
        }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (busy || !fromUser) return
                val v = min + (max - min) * p / 1000f
                synchronized(scene.lock) {
                    val o = scene.sel()
                    if (o != null) set(o, v)
                }
                tv.text = name + ": " + String.format(Locale.US, "%.2f", v)
                sv.requestRender()
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        binds.add(refresh)
        box.addView(tv)
        box.addView(sb)
        return box
    }

    private fun refreshAll() {
        val o = scene.sel()
        selTitle.text = if (o != null) "Tanlangan: " + o.name else "Obyekt tanlanmagan"
        for (b in binds) b()
        for (i in animBtns.indices) {
            animBtns[i].background = chip(o != null && o.anim == i)
        }
    }

    private fun addShapeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Shakl tanlang")
            .setItems(Shapes.names.toTypedArray()) { _, which ->
                synchronized(scene.lock) { scene.add(which) }
                refreshAll()
                sv.requestRender()
            }
            .show()
    }

    private fun listDialog() {
        val names = ArrayList<String>()
        for (i in scene.objs.indices) names.add((i + 1).toString() + ". " + scene.objs[i].name)
        if (names.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Obyektlar")
            .setItems(names.toTypedArray()) { _, which ->
                scene.selected = which
                refreshAll()
                sv.requestRender()
            }
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val mp = ViewGroup.LayoutParams.MATCH_PARENT
        val wc = ViewGroup.LayoutParams.WRAP_CONTENT

        sv = SceneView(this, scene)
        val root = FrameLayout(this)
        root.addView(sv, FrameLayout.LayoutParams(mp, mp))

        // Boshlang'ich shakl
        val first = scene.add(0)
        first.anim = 1

        sv.onPick = { i ->
            scene.selected = i
            refreshAll()
            sv.requestRender()
        }

        // Yuqori panel
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.setPadding(dp(6), dp(34), dp(6), 0)

        fun addTop(b: TextView) {
            val lp = LinearLayout.LayoutParams(0, wc, 1f)
            lp.setMargins(dp(3), 0, dp(3), 0)
            top.addView(b, lp)
        }

        addTop(button("+ Shakl") { addShapeDialog() })
        playBtn = button("⏸ To'xtat") {
            scene.playing = !scene.playing
            playBtn.text = if (scene.playing) "⏸ To'xtat" else "▶ Ijro"
            sv.setPlaying(scene.playing)
        }
        addTop(playBtn)
        addTop(button("Nusxa") {
            synchronized(scene.lock) { scene.duplicateSelected() }
            refreshAll()
            sv.requestRender()
        })
        addTop(button("O'chirish") {
            synchronized(scene.lock) { scene.removeSelected() }
            refreshAll()
            sv.requestRender()
        })
        addTop(button("Obyektlar") { listDialog() })
        root.addView(top, FrameLayout.LayoutParams(mp, wc, Gravity.TOP))

        // Pastki sozlamalar paneli
        panel = ScrollView(this)
        panel.setBackgroundColor(Color.argb(235, 11, 15, 20))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(14), dp(10), dp(14), dp(24))

        selTitle = TextView(this)
        selTitle.textSize = 16f
        selTitle.setTextColor(green)
        col.addView(selTitle)

        col.addView(label("Joylashuv"))
        col.addView(slider("X", -6f, 6f, { it.px }, { o, v -> o.px = v }))
        col.addView(slider("Y", -2f, 6f, { it.py }, { o, v -> o.py = v }))
        col.addView(slider("Z", -6f, 6f, { it.pz }, { o, v -> o.pz = v }))

        col.addView(label("Burilish (daraja)"))
        col.addView(slider("X", 0f, 360f, { it.rx }, { o, v -> o.rx = v }))
        col.addView(slider("Y", 0f, 360f, { it.ry }, { o, v -> o.ry = v }))
        col.addView(slider("Z", 0f, 360f, { it.rz }, { o, v -> o.rz = v }))

        col.addView(label("O'lcham"))
        col.addView(slider("X", 0.1f, 4f, { it.sx }, { o, v -> o.sx = v }))
        col.addView(slider("Y", 0.1f, 4f, { it.sy }, { o, v -> o.sy = v }))
        col.addView(slider("Z", 0.1f, 4f, { it.sz }, { o, v -> o.sz = v }))

        col.addView(label("Rang"))
        val colors = LinearLayout(this)
        colors.orientation = LinearLayout.HORIZONTAL
        for (c in PALETTE) {
            val sw = View(this)
            val g = GradientDrawable()
            g.setColor(c)
            g.cornerRadius = dp(8).toFloat()
            g.setStroke(dp(1), Color.WHITE)
            sw.background = g
            sw.setOnClickListener {
                synchronized(scene.lock) {
                    val o = scene.sel()
                    if (o != null) o.color = c
                }
                sv.requestRender()
            }
            val lp = LinearLayout.LayoutParams(0, dp(36), 1f)
            lp.setMargins(dp(3), 0, dp(3), 0)
            colors.addView(sw, lp)
        }
        col.addView(colors)

        col.addView(label("Animatsiya"))
        var row: LinearLayout? = null
        for (i in ANIM_NAMES.indices) {
            if (i % 4 == 0) {
                row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                col.addView(row)
            }
            val b = TextView(this)
            b.text = ANIM_NAMES[i]
            b.textSize = 12f
            b.gravity = Gravity.CENTER
            b.setTextColor(green)
            b.setPadding(dp(4), dp(10), dp(4), dp(10))
            b.background = chip(false)
            b.setOnClickListener {
                synchronized(scene.lock) {
                    val o = scene.sel()
                    if (o != null) o.anim = i
                }
                refreshAll()
                sv.requestRender()
            }
            animBtns.add(b)
            val lp = LinearLayout.LayoutParams(0, wc, 1f)
            lp.setMargins(dp(3), dp(3), dp(3), dp(3))
            row!!.addView(b, lp)
        }
        col.addView(slider("Tezlik", 0.1f, 4f, { it.speed }, { o, v -> o.speed = v }))
        col.addView(slider("Kuch", 0.2f, 3f, { it.amp }, { o, v -> o.amp = v }))

        panel.addView(col)
        panelH = (resources.displayMetrics.heightPixels * 0.42f).toInt()
        root.addView(panel, FrameLayout.LayoutParams(mp, panelH, Gravity.BOTTOM))

        toggleBtn = button("▼ Yopish") {
            panelVisible = !panelVisible
            panel.visibility = if (panelVisible) View.VISIBLE else View.GONE
            toggleBtn.text = if (panelVisible) "▼ Yopish" else "▲ Sozlamalar"
            val lp = toggleBtn.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = if (panelVisible) panelH else dp(16)
            toggleBtn.layoutParams = lp
        }
        val tlp = FrameLayout.LayoutParams(wc, wc, Gravity.BOTTOM or Gravity.END)
        tlp.bottomMargin = panelH
        tlp.rightMargin = dp(8)
        root.addView(toggleBtn, tlp)

        setContentView(root)
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        sv.onResume()
    }

    override fun onPause() {
        sv.onPause()
        super.onPause()
    }
}
