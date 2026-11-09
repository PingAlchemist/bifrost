package br.com.bifrost

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import kotlin.math.max

@SuppressLint("ViewConstructor")
class GameView(ctx: Context, private val g: Game) : View(ctx) {
    private val r = Renderer(g, ctx.resources)
    private var scale = 1f
    private var vw = 1280f
    private var lastNs = 0L
    private var holdId = -1
    private var holdX = 0f
    private var holdY = 0f

    // insets em pixels reais (notch / barras do sistema)
    private var insL = 0
    private var insR = 0
    private var insT = 0
    private var insB = 0

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        scale = h / r.VH
        vw = w / scale
        updateSafe()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        var l = 0
        var t = 0
        var rr = 0
        var b = 0
        if (Build.VERSION.SDK_INT >= 28) {
            insets.displayCutout?.let {
                l = it.safeInsetLeft; t = it.safeInsetTop; rr = it.safeInsetRight; b = it.safeInsetBottom
            }
        }
        if (Build.VERSION.SDK_INT >= 30) {
            val sb = insets.getInsets(WindowInsets.Type.systemBars())
            l = max(l, sb.left); t = max(t, sb.top); rr = max(rr, sb.right); b = max(b, sb.bottom)
        }
        insL = l; insT = t; insR = rr; insB = b
        updateSafe()
        return super.onApplyWindowInsets(insets)
    }

    private fun updateSafe() {
        if (scale <= 0f) return
        r.safeL = insL / scale
        r.safeR = insR / scale
        r.safeT = insT / scale
        r.safeB = insB / scale
    }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 0.016f else ((now - lastNs) / 1e9f).coerceIn(0f, 0.05f)
        lastNs = now
        if (holdId >= 0) {
            if (g.panel == Panel.NONE && !g.dead && !g.selecting) g.moveTo(r.worldX(holdX), r.worldY(holdY))
            else holdId = -1
        }
        // combate lento (modo de teste): o jogo inteiro roda na escala de tempo escolhida
        val sdt = dt * r.timeScale
        // na seleção de personagem o jogo fica parado
        if (!g.selecting) g.update(sdt)
        c.save()
        c.scale(scale, scale)
        r.draw(c, vw, sdt)
        c.restore()
        postInvalidateOnAnimation()
    }

    /** Troca de personagem: limpa o estado visual do anterior. */
    fun resetChar() {
        r.resetChar()
        holdId = -1
    }

    fun resume() {
        lastNs = 0L
        holdId = -1
        requestApplyInsets()
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                down(e.getPointerId(i), e.getX(i) / scale, e.getY(i) / scale)
            }
            MotionEvent.ACTION_MOVE -> if (holdId >= 0) {
                val i = e.findPointerIndex(holdId)
                if (i >= 0) {
                    holdX = e.getX(i) / scale
                    holdY = e.getY(i) / scale
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex) == holdId) holdId = -1
            }
            MotionEvent.ACTION_CANCEL -> holdId = -1
        }
        return true
    }

    private fun down(id: Int, x: Float, y: Float) {
        if (r.tap(x, y)) return
        if (g.selecting) return
        if (g.panel != Panel.NONE || g.dead || holdId >= 0) return
        if (!g.tapWorld(r.worldX(x), r.worldY(y))) {
            holdId = id
            holdX = x
            holdY = y
        }
    }
}
