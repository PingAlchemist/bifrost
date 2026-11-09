package br.com.bifrost

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

// ---------- Ícones de habilidades e equipamentos (arte em código, guardada em bitmap) ----------

/** Guarda cada ícone já desenhado em bitmap, no tamanho de pixels da tela. */
class Icons {
    /** Pixels por unidade da tela virtual (GameView.scale). */
    var pxScale = 1f
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val grayPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
            setSaturation(0f)
            postConcat(ColorMatrix().apply { setScale(0.62f, 0.62f, 0.62f, 1f) })
        })
    }
    private val dst = RectF()
    private val cache = object : LinkedHashMap<Long, Bitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap>?) = size > 180
    }

    /** Ícone da habilidade (quadrado com fundo). Bloqueada = cinza. */
    fun skill(c: Canvas, id: Int, x: Float, y: Float, sz: Float, locked: Boolean = false) {
        val b = get(1, id, sz) { cv -> IconArt.skill(cv, id, SKILLS.getOrNull(id)?.cor ?: col(0xFF607D8B)) }
        dst.set(x, y, x + sz, y + sz)
        c.drawBitmap(b, null, dst, if (locked) grayPaint else bmpPaint)
    }

    /** Ícone do equipamento (sem fundo), pela arte do tipo + mapa de origem; brilho extra nos tiers altos. */
    fun gear(c: Canvas, base: ItemBase, tier: Int, x: Float, y: Float, sz: Float) {
        val key = (base.tipo.ordinal * 16 + base.mapa.coerceIn(0, 15)) * 8 + tier.coerceIn(0, 7)
        val b = get(2, key, sz) { cv -> IconArt.gear(cv, base.tipo, base.mapa, tier) }
        dst.set(x, y, x + sz, y + sz)
        c.drawBitmap(b, null, dst, bmpPaint)
    }

    private inline fun get(kind: Int, id: Int, sz: Float, draw: (Canvas) -> Unit): Bitmap {
        val px = (ceil(sz * pxScale / 16f).toInt() * 16).coerceIn(16, 512)
        val key = (kind.toLong() shl 40) or (id.toLong() shl 12) or px.toLong()
        cache[key]?.let { return it }
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        cv.scale(px / 100f, px / 100f)
        draw(cv)
        cache[key] = b
        return b
    }
}

/** Desenho dos ícones num quadro de 100x100 (luz de cima à esquerda, contorno escuro). */
object IconArt {
    private val f = Paint(Paint.ANTI_ALIAS_FLAG)
    private val s = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val p = Path()
    private val r = RectF()
    private lateinit var c: Canvas

    private val OUT = col(0xFF1A1310)
    private const val WHITE = -1

    // ---------- cores ----------
    private fun ch(v: Int, sh: Int) = (v shr sh) and 255
    private fun argb(a: Int, rr: Int, gg: Int, bb: Int) = (a shl 24) or (rr shl 16) or (gg shl 8) or bb
    private fun mix(a: Int, b: Int, t: Float) = argb(
        ch(a, 24) + ((ch(b, 24) - ch(a, 24)) * t).toInt(), ch(a, 16) + ((ch(b, 16) - ch(a, 16)) * t).toInt(),
        ch(a, 8) + ((ch(b, 8) - ch(a, 8)) * t).toInt(), ch(a, 0) + ((ch(b, 0) - ch(a, 0)) * t).toInt()
    )
    private fun lt(v: Int, t: Float) = mix(v, v or 0xFFFFFF, t)
    private fun dk(v: Int, t: Float) = mix(v, v and -0x1000000, t)
    private fun al(v: Int, a: Int) = (v and 0xFFFFFF) or (a shl 24)

    // ---------- primitivas ----------
    /** Preenche o caminho com degradê (claro em cima/esquerda) e contorno escuro. */
    private fun body(base: Int, ow: Float = 3f, path: Path = p) {
        path.computeBounds(r, true)
        f.shader = LinearGradient(r.left, r.top, r.right, r.bottom, lt(base, 0.38f), dk(base, 0.45f), Shader.TileMode.CLAMP)
        c.drawPath(path, f)
        f.shader = null
        if (ow > 0f) { s.strokeWidth = ow; s.color = OUT; c.drawPath(path, s) }
    }

    /** Degradê vertical (mais natural para peças "em pé"). */
    private fun bodyV(base: Int, ow: Float = 3f) {
        p.computeBounds(r, true)
        f.shader = LinearGradient(r.left, r.top, r.left, r.bottom, lt(base, 0.32f), dk(base, 0.42f), Shader.TileMode.CLAMP)
        c.drawPath(p, f)
        f.shader = null
        if (ow > 0f) { s.strokeWidth = ow; s.color = OUT; c.drawPath(p, s) }
    }

    private fun flat(color: Int, ow: Float = 0f) {
        f.color = color; c.drawPath(p, f)
        if (ow > 0f) { s.strokeWidth = ow; s.color = OUT; c.drawPath(p, s) }
    }

    private fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int) {
        s.strokeWidth = w; s.color = color; c.drawLine(x1, y1, x2, y2, s)
    }

    private fun circ(x: Float, y: Float, rad: Float, color: Int) { f.color = color; c.drawCircle(x, y, rad, f) }

    private fun ring(x: Float, y: Float, rad: Float, w: Float, color: Int) {
        s.strokeWidth = w; s.color = color; c.drawCircle(x, y, rad, s)
    }

    /** Brilho na peça: traço branco translúcido. */
    private fun hl(x1: Float, y1: Float, x2: Float, y2: Float, w: Float = 2.5f, a: Int = 150) = line(x1, y1, x2, y2, w, al(WHITE, a))

    /** Gema facetada com brilho. */
    private fun gem(x: Float, y: Float, rad: Float, color: Int) {
        f.shader = RadialGradient(x - rad * 0.3f, y - rad * 0.35f, rad * 1.3f, lt(color, 0.7f), dk(color, 0.45f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, rad, f); f.shader = null
        ring(x, y, rad, 2.2f, OUT)
        circ(x - rad * 0.35f, y - rad * 0.38f, rad * 0.28f, al(WHITE, 220))
    }

    /** Orbe mágico com halo. */
    private fun orb(x: Float, y: Float, rad: Float, color: Int) {
        f.shader = RadialGradient(x, y, rad * 2.1f, al(color, 150), al(color, 0), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, rad * 2.1f, f); f.shader = null
        gem(x, y, rad, color)
    }

    /** Estrela de 4 pontas (faísca). */
    private fun spark(x: Float, y: Float, rad: Float, color: Int) {
        p.reset()
        p.moveTo(x, y - rad); p.quadTo(x, y, x + rad, y); p.quadTo(x, y, x, y + rad)
        p.quadTo(x, y, x - rad, y); p.quadTo(x, y, x, y - rad); p.close()
        f.color = color; c.drawPath(p, f)
    }

    /** Explosão em estrela (n pontas). */
    private fun burst(x: Float, y: Float, ro: Float, ri: Float, n: Int, rot: Float) {
        p.reset()
        for (i in 0 until n * 2) {
            val a = (rot + i * 180f / n) * 0.0174533f
            val rr = if (i % 2 == 0) ro else ri
            val px = x + cos(a) * rr
            val py = y + sin(a) * rr
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        p.close()
    }

    /** Folha (ponta para cima no quadro local, girada por ang). */
    private fun leaf(x: Float, y: Float, len: Float, w: Float, ang: Float, color: Int) {
        c.save(); c.translate(x, y); c.rotate(ang)
        p.reset(); p.moveTo(0f, 0f); p.quadTo(w, -len * 0.5f, 0f, -len); p.quadTo(-w, -len * 0.5f, 0f, 0f); p.close()
        body(color, 2f)
        line(0f, -len * 0.1f, 0f, -len * 0.85f, 1.3f, al(dk(color, 0.5f), 200))
        c.restore()
    }

    /** Flecha de (x1,y1) a (x2,y2): haste, ponta e penas. */
    private fun arrow(x1: Float, y1: Float, x2: Float, y2: Float, shaft: Int, head: Int, fletch: Int, sc: Float = 1f) {
        val dx = x2 - x1; val dy = y2 - y1
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        val ang = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        c.save(); c.translate(x1, y1); c.rotate(ang)
        line(0f, 0f, len - 10f * sc, 0f, 6.5f * sc, OUT)
        line(0f, 0f, len - 10f * sc, 0f, 3.5f * sc, shaft)
        // penas
        p.reset(); p.moveTo(-2f * sc, 0f); p.lineTo(-10f * sc, -6.5f * sc); p.lineTo(4f * sc, -6.5f * sc); p.lineTo(10f * sc, 0f)
        p.lineTo(4f * sc, 6.5f * sc); p.lineTo(-10f * sc, 6.5f * sc); p.close()
        body(fletch, 2f)
        // ponta
        p.reset(); p.moveTo(len + 2f * sc, 0f); p.lineTo(len - 12f * sc, -7f * sc); p.lineTo(len - 9f * sc, 0f); p.lineTo(len - 12f * sc, 7f * sc); p.close()
        body(head, 2.2f)
        c.restore()
    }

    // ================================================================
    // HABILIDADES
    // ================================================================

    fun skill(cv: Canvas, id: Int, cor: Int) {
        c = cv
        // fundo: degradê radial na cor da habilidade, moldura escura e borda clara
        r.set(3f, 3f, 97f, 97f)
        f.shader = RadialGradient(42f, 34f, 78f, lt(cor, 0.25f), dk(cor, 0.72f), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 18f, 18f, f); f.shader = null
        // vinheta de luz central
        f.shader = RadialGradient(50f, 50f, 40f, al(lt(cor, 0.6f), 90), al(cor, 0), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 18f, 18f, f); f.shader = null
        when (id) {
            0 -> sPrimeirosSocorros()
            1 -> sGolpeBrutal()
            2 -> sSismico()
            3 -> sFuria()
            4 -> sSetaFogo()
            5 -> sTempestade()
            6 -> sNova()
            7 -> sDisparoDuplo()
            8 -> sChuva()
            9 -> sOlhoAguia()
            10 -> sVenenoso()
            11 -> sLaminas()
            12 -> sSombra()
            else -> spark(50f, 50f, 30f, WHITE)
        }
        // brilho de vidro em cima
        p.reset(); p.moveTo(8f, 30f); p.quadTo(10f, 8f, 30f, 7f); p.lineTo(70f, 7f); p.quadTo(92f, 8f, 92f, 26f)
        p.quadTo(50f, 18f, 8f, 30f); p.close()
        f.color = al(WHITE, 42); c.drawPath(p, f)
        // moldura
        r.set(3f, 3f, 97f, 97f)
        s.strokeWidth = 5f; s.color = dk(cor, 0.78f); c.drawRoundRect(r, 18f, 18f, s)
        r.set(6.5f, 6.5f, 93.5f, 93.5f)
        s.strokeWidth = 1.6f; s.color = al(lt(cor, 0.6f), 150); c.drawRoundRect(r, 15f, 15f, s)
    }

    private fun sPrimeirosSocorros() {
        // coração com cruz e brilhos de cura
        p.reset()
        p.moveTo(50f, 84f)
        p.cubicTo(18f, 62f, 12f, 40f, 24f, 28f)
        p.cubicTo(34f, 18f, 46f, 22f, 50f, 33f)
        p.cubicTo(54f, 22f, 66f, 18f, 76f, 28f)
        p.cubicTo(88f, 40f, 82f, 62f, 50f, 84f)
        p.close()
        body(col(0xFFE53950), 3.2f)
        hl(26f, 38f, 32f, 29f, 3f, 170)
        // cruz
        p.reset()
        p.addRect(44f, 36f, 56f, 70f, Path.Direction.CW); p.addRect(33f, 47f, 67f, 59f, Path.Direction.CW)
        flat(col(0xFFFFFFFF))
        s.strokeWidth = 1.5f; s.color = al(col(0xFF8E0016), 160)
        c.drawRect(44f, 36f, 56f, 70f, s); c.drawRect(33f, 47f, 67f, 59f, s)
        f.color = WHITE; c.drawRect(44.8f, 47.8f, 55.2f, 58.2f, f)
        spark(82f, 20f, 8f, col(0xFFF1FFD6)); spark(17f, 74f, 6f, col(0xFFF1FFD6)); spark(84f, 70f, 4.5f, col(0xFFF1FFD6))
    }

    private fun sGolpeBrutal() {
        // explosão de impacto atrás da espada
        burst(60f, 40f, 34f, 15f, 9, -10f); f.color = al(col(0xFFFFD54F), 230); c.drawPath(p, f)
        burst(60f, 40f, 20f, 9f, 9, 10f); f.color = col(0xFFFFFDE7); c.drawPath(p, f)
        // espada descendo na diagonal
        c.save(); c.rotate(45f, 50f, 50f)
        p.reset(); p.moveTo(50f, 2f); p.lineTo(58f, 12f); p.lineTo(58f, 62f); p.lineTo(42f, 62f); p.lineTo(42f, 12f); p.close()
        body(col(0xFFCFD8DC), 3f)
        line(50f, 10f, 50f, 58f, 2f, al(col(0xFF607D8B), 200))
        hl(45f, 14f, 45f, 58f, 2f, 190)
        p.reset(); p.addRoundRect(RectF(30f, 61f, 70f, 69f), 3f, 3f, Path.Direction.CW); body(col(0xFFFFC107), 2.6f)
        p.reset(); p.addRoundRect(RectF(45.5f, 68f, 54.5f, 88f), 2f, 2f, Path.Direction.CW); body(col(0xFF6D4C41), 2.4f)
        for (k in 0..3) line(46f, 71f + k * 4.5f, 54f, 73f + k * 4.5f, 1.4f, al(OUT, 140))
        p.reset(); p.addCircle(50f, 92f, 5f, Path.Direction.CW); body(col(0xFFFFC107), 2.2f)
        c.restore()
        // linhas de golpe
        line(14f, 30f, 30f, 24f, 3f, al(WHITE, 200)); line(10f, 44f, 26f, 40f, 3f, al(WHITE, 160))
    }

    private fun sSismico() {
        // ondas de choque
        r.set(10f, 52f, 90f, 86f); s.strokeWidth = 3f; s.color = al(col(0xFFFFE0B2), 150); c.drawArc(r, 180f, 180f, false, s)
        r.set(20f, 58f, 80f, 82f); s.color = al(col(0xFFFFE0B2), 210); c.drawArc(r, 180f, 180f, false, s)
        // chão rachado
        p.reset(); p.moveTo(6f, 72f); p.lineTo(30f, 66f); p.lineTo(44f, 70f); p.lineTo(56f, 64f); p.lineTo(72f, 69f); p.lineTo(94f, 64f)
        p.lineTo(94f, 94f); p.lineTo(6f, 94f); p.close()
        body(col(0xFF795548), 3f)
        s.strokeWidth = 2.6f; s.color = OUT
        p.reset(); p.moveTo(50f, 66f); p.lineTo(46f, 76f); p.lineTo(52f, 82f); p.lineTo(47f, 93f); c.drawPath(p, s)
        p.reset(); p.moveTo(47f, 77f); p.lineTo(34f, 82f); p.lineTo(28f, 91f); c.drawPath(p, s)
        p.reset(); p.moveTo(52f, 82f); p.lineTo(66f, 85f); p.lineTo(74f, 80f); c.drawPath(p, s)
        // pedras voando
        fun rock(x: Float, y: Float, sz: Float) {
            p.reset(); p.moveTo(x - sz, y); p.lineTo(x - sz * 0.4f, y - sz); p.lineTo(x + sz * 0.7f, y - sz * 0.7f)
            p.lineTo(x + sz, y + sz * 0.3f); p.lineTo(x + sz * 0.1f, y + sz); p.close()
            body(col(0xFFA1887F), 2.4f)
        }
        rock(28f, 40f, 9f); rock(70f, 34f, 10f); rock(50f, 22f, 7f); rock(84f, 52f, 5f); rock(14f, 56f, 5f)
        // punho batendo (seta para baixo)
        p.reset(); p.moveTo(50f, 62f); p.lineTo(40f, 48f); p.lineTo(46f, 48f); p.lineTo(46f, 36f); p.lineTo(54f, 36f); p.lineTo(54f, 48f)
        p.lineTo(60f, 48f); p.close()
        body(col(0xFFFFCC80), 2.6f)
    }

    private fun sFuria() {
        // chama em três camadas
        fun flame(sc: Float, color: Int, ow: Float) {
            p.reset()
            p.moveTo(50f, 92f)
            p.cubicTo(50f - 34f * sc, 90f, 50f - 36f * sc, 62f, 50f - 22f * sc, 46f)
            p.cubicTo(50f - 20f * sc, 58f, 50f - 12f * sc, 60f, 50f - 10f * sc, 56f)
            p.cubicTo(50f - 18f * sc, 36f, 50f - 4f * sc, 22f, 50f + 2f * sc, 8f + (1 - sc) * 40f)
            p.cubicTo(50f + 6f * sc, 26f, 50f + 26f * sc, 36f, 50f + 18f * sc, 58f)
            p.cubicTo(50f + 26f * sc, 54f, 50f + 28f * sc, 46f, 50f + 26f * sc, 40f)
            p.cubicTo(50f + 42f * sc, 58f, 50f + 34f * sc, 90f, 50f, 92f)
            p.close()
            if (ow > 0f) body(color, ow) else bodyV(color, 0f)
        }
        flame(1f, col(0xFFE64A19), 3f)
        flame(0.68f, col(0xFFFF9800), 0f)
        flame(0.38f, col(0xFFFFEB3B), 0f)
        // olhos de raiva
        p.reset(); p.moveTo(36f, 64f); p.lineTo(47f, 69f); p.lineTo(45f, 74f); p.lineTo(37f, 71f); p.close(); flat(col(0xFF3E1A00))
        p.reset(); p.moveTo(64f, 64f); p.lineTo(53f, 69f); p.lineTo(55f, 74f); p.lineTo(63f, 71f); p.close(); flat(col(0xFF3E1A00))
        spark(20f, 22f, 5f, col(0xFFFFF3E0)); spark(82f, 26f, 4f, col(0xFFFFF3E0))
    }

    private fun sSetaFogo() {
        // rastro de fogo
        c.save(); c.rotate(-40f, 50f, 50f)
        p.reset(); p.moveTo(8f, 50f); p.cubicTo(24f, 30f, 44f, 36f, 62f, 40f); p.lineTo(62f, 60f); p.cubicTo(44f, 64f, 24f, 70f, 8f, 50f); p.close()
        f.shader = LinearGradient(8f, 50f, 62f, 50f, al(col(0xFFFFEB3B), 0), col(0xFFFF6F00), Shader.TileMode.CLAMP)
        c.drawPath(p, f); f.shader = null
        p.reset(); p.moveTo(22f, 50f); p.cubicTo(34f, 40f, 48f, 43f, 62f, 45f); p.lineTo(62f, 55f); p.cubicTo(48f, 57f, 34f, 60f, 22f, 50f); p.close()
        f.shader = LinearGradient(22f, 50f, 62f, 50f, al(col(0xFFFFFDE7), 0), col(0xFFFFEB3B), Shader.TileMode.CLAMP)
        c.drawPath(p, f); f.shader = null
        // bola de fogo na ponta
        f.shader = RadialGradient(70f, 50f, 22f, col(0xFFFFFDE7), al(col(0xFFFF6F00), 0), Shader.TileMode.CLAMP)
        c.drawCircle(70f, 50f, 22f, f); f.shader = null
        burst(70f, 50f, 17f, 9f, 7, 0f); body(col(0xFFFF8F00), 2.4f)
        circ(71f, 50f, 7f, col(0xFFFFF59D))
        c.restore()
        arrow(16f, 84f, 74f, 26f, col(0xFF8D6E63), col(0xFFFFCA28), col(0xFFE53935), 1f)
    }

    private fun sTempestade() {
        // nuvem escura
        p.reset()
        p.addCircle(30f, 34f, 14f, Path.Direction.CW); p.addCircle(48f, 26f, 18f, Path.Direction.CW)
        p.addCircle(68f, 32f, 15f, Path.Direction.CW); p.addRoundRect(RectF(16f, 32f, 84f, 48f), 8f, 8f, Path.Direction.CW)
        p.op(Path(), Path.Op.UNION)
        body(col(0xFF546E7A), 3f)
        hl(36f, 20f, 48f, 13f, 2.5f, 120)
        // raio principal
        p.reset(); p.moveTo(54f, 40f); p.lineTo(38f, 66f); p.lineTo(50f, 66f); p.lineTo(40f, 94f); p.lineTo(68f, 58f); p.lineTo(55f, 58f); p.lineTo(64f, 40f); p.close()
        f.shader = RadialGradient(52f, 66f, 40f, al(col(0xFFFFF59D), 160), al(col(0xFFFFF59D), 0), Shader.TileMode.CLAMP)
        c.drawCircle(52f, 66f, 40f, f); f.shader = null
        body(col(0xFFFFEB3B), 2.8f)
        // raios menores
        s.strokeWidth = 3f; s.color = col(0xFFFFF9C4)
        p.reset(); p.moveTo(26f, 50f); p.lineTo(20f, 62f); p.lineTo(26f, 63f); p.lineTo(18f, 78f); c.drawPath(p, s)
        p.reset(); p.moveTo(76f, 50f); p.lineTo(82f, 60f); p.lineTo(77f, 62f); p.lineTo(84f, 74f); c.drawPath(p, s)
    }

    private fun sNova() {
        // anel de gelo
        f.shader = RadialGradient(50f, 50f, 44f, al(col(0xFFE1F5FE), 0), al(col(0xFFE1F5FE), 170), Shader.TileMode.CLAMP)
        c.drawCircle(50f, 50f, 44f, f); f.shader = null
        ring(50f, 50f, 40f, 2f, al(WHITE, 160))
        // floco de neve de 6 braços
        for (k in 0 until 6) {
            c.save(); c.rotate(k * 60f, 50f, 50f)
            line(50f, 50f, 50f, 14f, 8f, OUT)
            line(50f, 32f, 41f, 24f, 6f, OUT); line(50f, 32f, 59f, 24f, 6f, OUT)
            line(50f, 22f, 44f, 16f, 5f, OUT); line(50f, 22f, 56f, 16f, 5f, OUT)
            c.restore()
        }
        for (k in 0 until 6) {
            c.save(); c.rotate(k * 60f, 50f, 50f)
            line(50f, 50f, 50f, 14f, 4f, col(0xFFE1F5FE))
            line(50f, 32f, 41f, 24f, 2.6f, col(0xFFE1F5FE)); line(50f, 32f, 59f, 24f, 2.6f, col(0xFFE1F5FE))
            line(50f, 22f, 44f, 16f, 2f, col(0xFFE1F5FE)); line(50f, 22f, 56f, 16f, 2f, col(0xFFE1F5FE))
            c.restore()
        }
        // cristal central
        p.reset(); p.moveTo(50f, 38f); p.lineTo(60f, 44f); p.lineTo(60f, 56f); p.lineTo(50f, 62f); p.lineTo(40f, 56f); p.lineTo(40f, 44f); p.close()
        body(col(0xFF81D4FA), 2.4f)
        hl(44f, 45f, 50f, 41f, 2f, 220)
        spark(80f, 18f, 6f, WHITE); spark(18f, 80f, 5f, WHITE)
    }

    private fun sDisparoDuplo() {
        line(10f, 66f, 30f, 46f, 2.5f, al(WHITE, 140)); line(30f, 84f, 50f, 64f, 2.5f, al(WHITE, 140))
        arrow(14f, 56f, 70f, 14f, col(0xFFD7CCC8), col(0xFFE0E0E0), col(0xFFAED581), 1f)
        arrow(30f, 86f, 88f, 40f, col(0xFFD7CCC8), col(0xFFE0E0E0), col(0xFFAED581), 1f)
        spark(80f, 14f, 6f, WHITE); spark(92f, 52f, 4f, WHITE)
    }

    private fun sChuva() {
        // arco de nuvem de flechas caindo
        val xs = floatArrayOf(22f, 40f, 58f, 76f, 31f, 67f)
        val ys = floatArrayOf(60f, 72f, 66f, 58f, 86f, 88f)
        for (i in xs.indices) {
            val x = xs[i]; val y = ys[i]
            line(x - 4f, y - 48f, x - 2f, y - 34f, 2f, al(WHITE, 100))
            arrow(x - 7f, y - 40f, x, y, col(0xFFD7CCC8), col(0xFFE0E0E0), col(0xFF9CCC65), 0.75f)
        }
        // chão com impactos
        r.set(10f, 86f, 90f, 96f); f.color = al(OUT, 70); c.drawOval(r, f)
    }

    private fun sOlhoAguia() {
        // asas estilizadas
        for (sg in intArrayOf(-1, 1)) {
            c.save(); if (sg < 0) c.scale(-1f, 1f, 50f, 50f)
            p.reset(); p.moveTo(70f, 46f); p.cubicTo(84f, 30f, 92f, 26f, 96f, 22f); p.cubicTo(94f, 34f, 92f, 40f, 86f, 46f)
            p.cubicTo(92f, 46f, 94f, 48f, 94f, 50f); p.cubicTo(86f, 56f, 80f, 58f, 70f, 56f); p.close()
            body(col(0xFFFFE082), 2.4f)
            line(76f, 48f, 90f, 32f, 1.4f, al(OUT, 130)); line(76f, 52f, 90f, 47f, 1.4f, al(OUT, 130))
            c.restore()
        }
        // olho
        p.reset(); p.moveTo(14f, 52f); p.quadTo(50f, 18f, 86f, 52f); p.quadTo(50f, 84f, 14f, 52f); p.close()
        body(col(0xFFFFFFFF), 3.2f)
        f.shader = RadialGradient(50f, 50f, 18f, col(0xFFFFE082), col(0xFFE65100), Shader.TileMode.CLAMP)
        c.drawCircle(50f, 51f, 17f, f); f.shader = null
        ring(50f, 51f, 17f, 2.4f, OUT)
        circ(50f, 51f, 8f, col(0xFF1A1310))
        circ(45f, 46f, 4f, al(WHITE, 230)); circ(55f, 56f, 1.8f, al(WHITE, 180))
        // mira
        s.strokeWidth = 2f; s.color = al(col(0xFFFFF8E1), 210)
        c.drawLine(50f, 8f, 50f, 22f, s); c.drawLine(50f, 80f, 50f, 94f, s)
    }

    private fun sVenenoso() {
        // nuvem de veneno
        f.shader = RadialGradient(50f, 60f, 44f, al(col(0xFFB2FF59), 120), al(col(0xFFB2FF59), 0), Shader.TileMode.CLAMP)
        c.drawCircle(50f, 60f, 44f, f); f.shader = null
        // adaga
        c.save(); c.rotate(-35f, 50f, 50f)
        p.reset(); p.moveTo(50f, 6f); p.cubicTo(58f, 20f, 58f, 40f, 57f, 56f); p.lineTo(43f, 56f); p.cubicTo(42f, 40f, 42f, 20f, 50f, 6f); p.close()
        body(col(0xFFB0BEC5), 3f)
        // lâmina envenenada (verde escorrendo)
        p.reset(); p.moveTo(50f, 10f); p.cubicTo(55f, 22f, 56f, 32f, 56f, 40f); p.quadTo(53f, 46f, 50f, 40f); p.quadTo(46f, 34f, 45f, 30f); p.cubicTo(45f, 22f, 47f, 16f, 50f, 10f); p.close()
        flat(al(col(0xFF76FF03), 210))
        hl(46f, 22f, 46f, 50f, 1.8f, 160)
        p.reset(); p.moveTo(30f, 56f); p.quadTo(50f, 62f, 70f, 56f); p.lineTo(70f, 63f); p.quadTo(50f, 68f, 30f, 63f); p.close(); body(col(0xFF558B2F), 2.4f)
        p.reset(); p.addRoundRect(RectF(45f, 63f, 55f, 84f), 3f, 3f, Path.Direction.CW); body(col(0xFF4E342E), 2.4f)
        p.reset(); p.addCircle(50f, 89f, 5.5f, Path.Direction.CW); body(col(0xFF7CB342), 2.2f)
        c.restore()
        // gotas
        fun drop(x: Float, y: Float, sz: Float) {
            p.reset(); p.moveTo(x, y - sz * 1.6f); p.quadTo(x + sz, y - sz * 0.2f, x + sz, y + sz * 0.2f)
            p.quadTo(x + sz, y + sz, x, y + sz); p.quadTo(x - sz, y + sz, x - sz, y + sz * 0.2f); p.quadTo(x - sz, y - sz * 0.2f, x, y - sz * 1.6f); p.close()
            body(col(0xFF76FF03), 2f)
        }
        drop(70f, 62f, 5f); drop(80f, 80f, 4f); drop(62f, 84f, 3.5f)
        // bolhas
        ring(24f, 76f, 5f, 2f, al(col(0xFFCCFF90), 220)); ring(16f, 62f, 3f, 1.6f, al(col(0xFFCCFF90), 200))
    }

    private fun sLaminas() {
        // rastro circular
        r.set(12f, 12f, 88f, 88f)
        s.strokeWidth = 5f; s.color = al(WHITE, 70); c.drawArc(r, 200f, 250f, false, s)
        r.set(20f, 20f, 80f, 80f)
        s.strokeWidth = 3f; s.color = al(WHITE, 110); c.drawArc(r, 20f, 250f, false, s)
        // três lâminas curvas
        for (k in 0 until 3) {
            c.save(); c.rotate(k * 120f + 15f, 50f, 50f)
            p.reset(); p.moveTo(50f, 50f); p.cubicTo(44f, 34f, 50f, 16f, 66f, 8f); p.cubicTo(60f, 22f, 60f, 36f, 60f, 48f); p.close()
            body(col(0xFFECEFF1), 2.6f)
            hl(52f, 36f, 58f, 18f, 1.8f, 200)
            c.restore()
        }
        p.reset(); p.addCircle(50f, 50f, 11f, Path.Direction.CW); body(col(0xFF78909C), 2.6f)
        gem(50f, 50f, 5f, col(0xFF7E57C2))
    }

    private fun sSombra() {
        // linhas de velocidade
        for (k in 0..4) line(8f, 26f + k * 12f, 26f + (k % 2) * 10f, 26f + k * 12f, 3f, al(WHITE, 90 + k * 20))
        // pós-imagens
        fun runner(dx: Float, color: Int, ow: Float) {
            c.save(); c.translate(dx, 0f)
            p.reset()
            p.addCircle(60f, 22f, 8f, Path.Direction.CW)
            p.moveTo(54f, 32f); p.lineTo(66f, 32f); p.lineTo(70f, 52f); p.lineTo(84f, 46f); p.lineTo(86f, 52f); p.lineTo(68f, 60f)
            p.lineTo(64f, 56f); p.lineTo(76f, 74f); p.lineTo(84f, 90f); p.lineTo(76f, 92f); p.lineTo(66f, 76f); p.lineTo(56f, 64f)
            p.lineTo(48f, 78f); p.lineTo(34f, 84f); p.lineTo(32f, 78f); p.lineTo(44f, 70f); p.lineTo(50f, 52f); p.lineTo(40f, 54f)
            p.lineTo(32f, 46f); p.lineTo(36f, 42f); p.lineTo(46f, 46f); p.close()
            if (ow > 0f) body(color, ow) else flat(color)
            c.restore()
        }
        runner(-24f, al(col(0xFFB39DDB), 60), 0f)
        runner(-12f, al(col(0xFFB39DDB), 110), 0f)
        runner(0f, col(0xFF311B92), 2.8f)
        // cachecol esvoaçante e olho brilhante
        p.reset(); p.moveTo(56f, 30f); p.cubicTo(44f, 26f, 34f, 34f, 20f, 28f); p.lineTo(24f, 34f); p.cubicTo(36f, 40f, 46f, 34f, 58f, 35f); p.close()
        body(col(0xFFE53935), 2f)
        circ(63f, 21f, 2.2f, col(0xFFFFEB3B))
    }

    // ================================================================
    // EQUIPAMENTOS
    // ================================================================

    /** Materiais por mapa de origem: metal, couro/pano/madeira, detalhe e gema (0 = sem gema). */
    private class Mat(val metal: Int, val soft: Int, val trim: Int, val gem: Int)

    private val MATS = arrayOf(
        Mat(col(0xFFC98A4B), col(0xFFC9B48A), col(0xFF8D5A2B), 0),                    // 1 bronze/pano
        Mat(col(0xFFA7B0B8), col(0xFF8B5A3C), col(0xFF5D3A22), 0),                    // 2 ferro/couro
        Mat(col(0xFFB6C8A0), col(0xFF5FA04E), col(0xFF7A5230), col(0xFF9CFF6E)),      // 3 bosque
        Mat(col(0xFFF0C04A), col(0xFFE6D3A3), col(0xFFB8860B), col(0xFFFF6D2E)),      // 4 deserto/solar
        Mat(col(0xFFBFE6FF), col(0xFF8FB8D8), col(0xFF5C8DB8), col(0xFF7FE8FF)),      // 5 glacial
        Mat(col(0xFF5E5872), col(0xFF3C3550), col(0xFF8E5BD6), col(0xFFC27BFF)),      // 6 sombrio
        Mat(col(0xFFB8392C), col(0xFF7E1F18), col(0xFFE0A030), col(0xFFFF4A3A)),      // 7 dracônico
    )

    fun gear(cv: Canvas, tipo: EqType, mapa: Int, tier: Int) {
        c = cv
        val v = mapa.coerceIn(1, 7)
        val m = MATS[v - 1]
        // aura nos tiers altos
        if (tier >= 3) {
            val auraC = when (tier) { 3 -> col(0xFF42A5F5); 4 -> col(0xFFAB47BC); else -> col(0xFFFF9800) }
            f.shader = RadialGradient(50f, 50f, 50f, al(auraC, if (tier >= 4) 120 else 80), al(auraC, 0), Shader.TileMode.CLAMP)
            c.drawCircle(50f, 50f, 50f, f); f.shader = null
        }
        when (tipo) {
            EqType.ARMA_FIS -> weapon(v, m)
            EqType.ARMA_MAG -> staff(v, m)
            EqType.ELMO -> helm(v, m)
            EqType.ARMADURA -> armor(v, m)
            EqType.ESCUDO -> shield(v, m)
            EqType.BOTAS -> boots(v, m)
            EqType.ACESSORIO -> accessory(v, m)
        }
        if (tier >= 3) spark(80f, 18f, 9f, al(WHITE, 240))
        if (tier >= 4) { spark(18f, 76f, 6.5f, al(WHITE, 230)); spark(86f, 64f, 4.5f, al(WHITE, 210)) }
    }

    // ---------- armas físicas (desenhadas em pé e giradas 45°) ----------
    private fun weapon(v: Int, m: Mat) {
        c.save(); c.scale(1.18f, 1.18f, 50f, 50f); c.rotate(45f, 50f, 50f)
        when (v) {
            1 -> { // lâmina curta de bronze
                p.reset(); p.moveTo(50f, 12f); p.lineTo(57f, 22f); p.lineTo(57f, 62f); p.lineTo(43f, 62f); p.lineTo(43f, 22f); p.close()
                body(m.metal, 3f); line(50f, 20f, 50f, 58f, 1.6f, al(dk(m.metal, 0.5f), 200)); hl(45.5f, 23f, 45.5f, 58f)
                guardBar(32f, 68f, 61f, m.trim); grip(68f, 86f, m.soft); pommel(90f, m.metal, 0)
            }
            2 -> { // machado de lenhador
                p.reset(); p.addRoundRect(RectF(46f, 10f, 54f, 96f), 4f, 4f, Path.Direction.CW); body(m.trim, 2.8f)
                for (k in 0..2) line(46f, 74f + k * 6f, 54f, 77f + k * 6f, 1.6f, al(OUT, 150))
                p.reset(); p.moveTo(52f, 16f); p.lineTo(64f, 14f); p.cubicTo(80f, 8f, 88f, 18f, 88f, 30f); p.cubicTo(88f, 44f, 80f, 52f, 64f, 46f)
                p.lineTo(52f, 44f); p.close(); body(m.metal, 3f)
                line(84f, 16f, 86f, 42f, 2.4f, al(WHITE, 180))
                p.reset(); p.moveTo(48f, 20f); p.lineTo(36f, 22f); p.lineTo(34f, 36f); p.lineTo(48f, 38f); p.close(); body(dk(m.metal, 0.15f), 2.6f)
                circ(50f, 30f, 2.4f, OUT)
            }
            3 -> { // espada do bosque com guarda de folhas
                p.reset(); p.moveTo(50f, 4f); p.lineTo(58f, 16f); p.lineTo(58f, 62f); p.lineTo(42f, 62f); p.lineTo(42f, 16f); p.close()
                body(col(0xFFD8E4C8), 3f); line(50f, 14f, 50f, 58f, 2.2f, al(col(0xFF558B2F), 200)); hl(45f, 17f, 45f, 58f)
                leaf(50f, 64f, 24f, 8f, -70f, m.soft); leaf(50f, 64f, 24f, 8f, 70f, m.soft)
                guardBar(40f, 60f, 60f, m.trim); grip(66f, 86f, m.trim); pommel(91f, m.metal, m.gem)
            }
            4 -> { // cimitarra curva
                p.reset(); p.moveTo(43f, 62f); p.lineTo(43f, 40f); p.cubicTo(43f, 24f, 52f, 10f, 70f, 4f)
                p.cubicTo(64f, 18f, 62f, 30f, 60f, 44f); p.cubicTo(59f, 52f, 57f, 58f, 57f, 62f); p.close()
                body(col(0xFFECEFF1), 3f); hl(47f, 54f, 49f, 26f)
                p.reset(); p.moveTo(30f, 62f); p.quadTo(50f, 70f, 70f, 62f); p.lineTo(68f, 70f); p.quadTo(50f, 76f, 32f, 70f); p.close(); body(m.metal, 2.6f)
                grip(72f, 88f, m.trim); pommel(92f, m.metal, m.gem); gem(50f, 67f, 4f, m.gem)
            }
            5 -> { // lâmina glacial de cristal
                p.reset(); p.moveTo(50f, 2f); p.lineTo(60f, 14f); p.lineTo(56f, 22f); p.lineTo(62f, 32f); p.lineTo(57f, 40f); p.lineTo(61f, 50f)
                p.lineTo(57f, 62f); p.lineTo(43f, 62f); p.lineTo(39f, 50f); p.lineTo(43f, 40f); p.lineTo(38f, 32f); p.lineTo(44f, 22f); p.lineTo(40f, 14f); p.close()
                f.shader = RadialGradient(50f, 32f, 34f, al(col(0xFF80DEEA), 150), al(col(0xFF80DEEA), 0), Shader.TileMode.CLAMP)
                c.drawCircle(50f, 32f, 34f, f); f.shader = null
                body(m.metal, 3f); line(50f, 8f, 50f, 58f, 1.8f, al(WHITE, 220)); line(44f, 30f, 50f, 36f, 1.4f, al(WHITE, 150))
                p.reset(); p.moveTo(30f, 60f); p.lineTo(40f, 64f); p.lineTo(50f, 60f); p.lineTo(60f, 64f); p.lineTo(70f, 60f); p.lineTo(66f, 70f); p.lineTo(34f, 70f); p.close()
                body(m.soft, 2.6f); grip(70f, 88f, m.trim); pommel(92f, m.soft, m.gem)
            }
            6 -> { // espada sombria serrilhada com runa
                p.reset(); p.moveTo(50f, 2f); p.lineTo(62f, 14f); p.lineTo(59f, 20f); p.lineTo(63f, 26f); p.lineTo(60f, 32f); p.lineTo(64f, 38f)
                p.lineTo(61f, 44f); p.lineTo(64f, 50f); p.lineTo(60f, 62f); p.lineTo(40f, 62f); p.lineTo(38f, 14f); p.close()
                body(m.metal, 3f)
                line(50f, 12f, 50f, 56f, 4f, al(m.gem, 90)); line(50f, 14f, 50f, 54f, 1.8f, lt(m.gem, 0.4f))
                hl(42f, 16f, 42f, 58f, 2f, 110)
                p.reset(); p.moveTo(50f, 62f); p.cubicTo(40f, 58f, 30f, 54f, 24f, 58f); p.lineTo(30f, 62f); p.lineTo(26f, 68f); p.cubicTo(36f, 66f, 44f, 70f, 50f, 70f)
                p.cubicTo(56f, 70f, 64f, 66f, 74f, 68f); p.lineTo(70f, 62f); p.lineTo(76f, 58f); p.cubicTo(70f, 54f, 60f, 58f, 50f, 62f); p.close()
                body(dk(m.metal, 0.3f), 2.6f); gem(50f, 65f, 4.2f, m.gem)
                grip(70f, 88f, m.soft); pommel(92f, m.metal, m.gem)
            }
            else -> { // garra dracônica
                for (k in -1..1) {
                    val dx = k * 12f
                    p.reset(); p.moveTo(44f + dx, 66f); p.cubicTo(42f + dx, 40f, 48f + dx, 22f, 64f + dx * 0.6f, 6f + kotlin.math.abs(k) * 10f)
                    p.cubicTo(58f + dx, 28f, 56f + dx, 46f, 56f + dx, 66f); p.close()
                    body(col(0xFFF5E6C8), 2.6f); hl(47f + dx, 56f, 49f + dx, 34f, 1.8f, 170)
                }
                p.reset(); p.addRoundRect(RectF(26f, 62f, 74f, 80f), 8f, 8f, Path.Direction.CW); body(m.metal, 3f)
                for (k in 0..3) { r.set(30f + k * 10f, 64f, 40f + k * 10f, 74f); s.strokeWidth = 1.5f; s.color = al(OUT, 150); c.drawArc(r, 0f, 180f, false, s) }
                line(28f, 78f, 72f, 78f, 3f, m.trim); gem(50f, 71f, 4.5f, m.gem)
                p.reset(); p.addRoundRect(RectF(44f, 80f, 56f, 96f), 3f, 3f, Path.Direction.CW); body(m.soft, 2.4f)
            }
        }
        c.restore()
    }

    private fun guardBar(l: Float, rgt: Float, y: Float, color: Int) {
        p.reset(); p.addRoundRect(RectF(l, y, rgt, y + 7f), 3f, 3f, Path.Direction.CW); body(color, 2.6f)
    }

    private fun grip(top: Float, bot: Float, color: Int) {
        p.reset(); p.addRoundRect(RectF(45.5f, top, 54.5f, bot), 2f, 2f, Path.Direction.CW); body(color, 2.4f)
        var y = top + 3f
        while (y < bot - 2f) { line(46f, y, 54f, y + 2f, 1.3f, al(OUT, 130)); y += 4.5f }
    }

    private fun pommel(y: Float, color: Int, gemC: Int) {
        p.reset(); p.addCircle(50f, y, 5.5f, Path.Direction.CW); body(color, 2.2f)
        if (gemC != 0) gem(50f, y, 3f, gemC)
    }

    // ---------- armas mágicas (cajado na diagonal) ----------
    private fun staff(v: Int, m: Mat) {
        c.save(); c.scale(1.12f, 1.12f, 50f, 54f); c.rotate(40f, 50f, 50f)
        val shaft = when (v) { 1, 2, 3 -> m.trim; 4 -> m.metal; 5 -> col(0xFFCFD8DC); 6 -> m.metal; else -> m.metal }
        val top = when (v) { 1 -> 32f; else -> 26f }
        p.reset(); p.addRoundRect(RectF(46.5f, top, 53.5f, 97f), 3.5f, 3.5f, Path.Direction.CW); bodyV(shaft, 2.6f)
        hl(48.5f, top + 6f, 48.5f, 92f, 1.6f, 110)
        when (v) {
            1 -> { // varinha com estrela
                f.shader = RadialGradient(50f, 20f, 20f, al(col(0xFFFFF59D), 170), al(col(0xFFFFF59D), 0), Shader.TileMode.CLAMP)
                c.drawCircle(50f, 20f, 20f, f); f.shader = null
                burst(50f, 20f, 14f, 6f, 5, -90f); body(col(0xFFFFD54F), 2.4f)
                circ(48f, 18f, 2.2f, al(WHITE, 220))
                line(46.5f, 60f, 53.5f, 60f, 3f, m.metal); line(46.5f, 84f, 53.5f, 84f, 3f, m.metal)
            }
            2 -> { // salgueiro: gancho em espiral e folhas
                s.strokeWidth = 9f; s.color = OUT
                p.reset(); p.moveTo(50f, 30f); p.cubicTo(50f, 10f, 72f, 6f, 72f, 20f); p.cubicTo(72f, 30f, 60f, 30f, 60f, 22f); c.drawPath(p, s)
                s.strokeWidth = 5.5f; s.color = m.trim; c.drawPath(p, s)
                leaf(52f, 40f, 18f, 6f, -60f, col(0xFF7CB342)); leaf(48f, 46f, 16f, 6f, 70f, col(0xFF9CCC65)); leaf(70f, 26f, 14f, 5f, 150f, col(0xFF7CB342))
                circ(62f, 20f, 3.5f, col(0xFFFFF59D))
            }
            3 -> { // cetro silvestre: garras de madeira segurando orbe verde
                orb(50f, 18f, 10f, m.gem)
                s.strokeWidth = 7.5f; s.color = OUT
                p.reset(); p.moveTo(50f, 32f); p.cubicTo(36f, 28f, 36f, 14f, 44f, 6f); p.moveTo(50f, 32f); p.cubicTo(64f, 28f, 64f, 14f, 56f, 6f); c.drawPath(p, s)
                s.strokeWidth = 4.5f; s.color = m.trim; c.drawPath(p, s)
                leaf(42f, 34f, 16f, 6f, -80f, m.soft); leaf(58f, 34f, 16f, 6f, 80f, m.soft)
            }
            4 -> { // cajado solar: disco de sol com raios
                for (k in 0 until 12) {
                    c.save(); c.rotate(k * 30f, 50f, 20f)
                    p.reset(); p.moveTo(46f, 8f); p.lineTo(50f, -2f); p.lineTo(54f, 8f); p.close(); body(m.metal, 1.8f)
                    c.restore()
                }
                p.reset(); p.addCircle(50f, 20f, 13f, Path.Direction.CW); body(m.metal, 2.8f)
                ring(50f, 20f, 9.5f, 1.6f, al(dk(m.metal, 0.5f), 200))
                gem(50f, 20f, 6.5f, m.gem)
                line(46.5f, 40f, 53.5f, 40f, 3.5f, m.trim); line(46.5f, 46f, 53.5f, 46f, 3.5f, m.trim)
            }
            5 -> { // cetro glacial: cristal hexagonal
                f.shader = RadialGradient(50f, 16f, 26f, al(m.gem, 150), al(m.gem, 0), Shader.TileMode.CLAMP)
                c.drawCircle(50f, 16f, 26f, f); f.shader = null
                p.reset(); p.moveTo(50f, -2f); p.lineTo(58f, 8f); p.lineTo(58f, 26f); p.lineTo(50f, 32f); p.lineTo(42f, 26f); p.lineTo(42f, 8f); p.close()
                body(m.metal, 2.6f); line(50f, 0f, 50f, 30f, 1.4f, al(WHITE, 200)); hl(44.5f, 10f, 44.5f, 24f, 2f, 220)
                p.reset(); p.moveTo(40f, 30f); p.lineTo(32f, 14f); p.lineTo(38f, 12f); p.lineTo(44f, 26f); p.close(); body(m.soft, 2f)
                p.reset(); p.moveTo(60f, 30f); p.lineTo(68f, 14f); p.lineTo(62f, 12f); p.lineTo(56f, 26f); p.close(); body(m.soft, 2f)
                p.reset(); p.addRoundRect(RectF(41f, 28f, 59f, 35f), 3f, 3f, Path.Direction.CW); body(m.trim, 2.2f)
            }
            6 -> { // cajado espectral: lua crescente com orbe
                p.reset(); r.set(30f, 0f, 70f, 40f); p.addCircle(50f, 20f, 20f, Path.Direction.CW)
                val cut = Path(); cut.addCircle(50f, 12f, 18f, Path.Direction.CW)
                p.op(cut, Path.Op.DIFFERENCE); body(m.metal, 2.6f)
                orb(50f, 16f, 8f, m.gem)
                s.strokeWidth = 2f; s.color = al(lt(m.gem, 0.4f), 170)
                p.reset(); p.moveTo(40f, 10f); p.cubicTo(30f, 4f, 36f, -4f, 28f, -6f); c.drawPath(p, s)
                p.reset(); p.moveTo(60f, 8f); p.cubicTo(70f, 2f, 64f, -4f, 72f, -8f); c.drawPath(p, s)
                line(46.5f, 46f, 53.5f, 46f, 3f, m.trim)
            }
            else -> { // cetro do dragão: garra dourada segurando orbe vermelho
                orb(50f, 16f, 10f, m.gem)
                for (k in intArrayOf(-1, 0, 1)) {
                    c.save(); c.rotate(k * 52f, 50f, 30f)
                    p.reset(); p.moveTo(46f, 32f); p.cubicTo(44f, 22f, 46f, 10f, 50f, 3f); p.cubicTo(52f, 12f, 54f, 22f, 54f, 32f); p.close()
                    body(m.trim, 2.2f); c.restore()
                }
                p.reset(); p.addRoundRect(RectF(42f, 28f, 58f, 38f), 4f, 4f, Path.Direction.CW); body(m.metal, 2.4f)
                gem(50f, 33f, 2.6f, m.trim)
            }
        }
        c.restore()
    }

    // ---------- elmos ----------
    private fun helm(v: Int, m: Mat) {
        when (v) {
            1 -> { // capuz de pano
                p.reset(); p.moveTo(50f, 8f); p.cubicTo(76f, 14f, 86f, 44f, 84f, 84f); p.lineTo(16f, 84f); p.cubicTo(14f, 44f, 24f, 14f, 50f, 8f); p.close()
                body(m.soft, 3f)
                p.reset(); p.moveTo(50f, 30f); p.cubicTo(68f, 32f, 70f, 56f, 66f, 78f); p.lineTo(34f, 78f); p.cubicTo(30f, 56f, 32f, 32f, 50f, 30f); p.close()
                f.shader = LinearGradient(0f, 30f, 0f, 78f, col(0xFF2B1D14), col(0xFF4A3526), Shader.TileMode.CLAMP); c.drawPath(p, f); f.shader = null
                s.strokeWidth = 2.4f; s.color = OUT; c.drawPath(p, s)
                line(22f, 84f, 78f, 84f, 3f, m.trim)
                hl(28f, 30f, 38f, 18f, 2.5f, 130)
                for (k in 0..3) line(24f + k * 3f, 50f + k * 8f, 28f + k * 3f, 50f + k * 8f, 1.4f, al(OUT, 120))
            }
            2 -> { // chapéu de couro com aba
                r.set(8f, 58f, 92f, 82f); p.reset(); p.addOval(r, Path.Direction.CW); body(dk(m.soft, 0.1f), 3f)
                p.reset(); p.moveTo(24f, 68f); p.cubicTo(22f, 30f, 34f, 18f, 50f, 18f); p.cubicTo(66f, 18f, 78f, 30f, 76f, 68f); p.quadTo(50f, 76f, 24f, 68f); p.close()
                body(m.soft, 3f)
                p.reset(); p.moveTo(25f, 56f); p.quadTo(50f, 64f, 75f, 56f); p.lineTo(76f, 66f); p.quadTo(50f, 74f, 24f, 66f); p.close(); body(m.trim, 2.4f)
                s.strokeWidth = 1.4f; s.color = al(lt(m.soft, 0.5f), 200); s.pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 3f), 0f)
                p.reset(); p.moveTo(50f, 20f); p.lineTo(50f, 56f); c.drawPath(p, s); s.pathEffect = null
                hl(32f, 40f, 38f, 28f, 2.5f, 140)
            }
            4 -> { // turbante
                p.reset(); p.moveTo(14f, 70f); p.cubicTo(8f, 30f, 30f, 14f, 50f, 14f); p.cubicTo(70f, 14f, 92f, 30f, 86f, 70f); p.quadTo(50f, 82f, 14f, 70f); p.close()
                body(m.soft, 3f)
                s.strokeWidth = 2.2f; s.color = al(dk(m.soft, 0.45f), 210)
                for (k in 0..3) { r.set(14f + k * 2f, 26f + k * 11f, 86f - k * 2f, 70f + k * 4f); c.drawArc(r, 200f + k * 4f, 140f - k * 8f, false, s) }
                hl(26f, 40f, 34f, 26f, 2.5f, 150)
                // joia e pluma
                p.reset(); p.moveTo(50f, 34f); p.cubicTo(56f, 20f, 70f, 8f, 66f, 2f); p.cubicTo(58f, 8f, 48f, 20f, 46f, 34f); p.close(); body(col(0xFFE53935), 2f)
                p.reset(); p.addCircle(50f, 44f, 9f, Path.Direction.CW); body(m.metal, 2.4f)
                gem(50f, 44f, 5.5f, m.gem)
                p.reset(); p.moveTo(46f, 52f); p.lineTo(50f, 60f); p.lineTo(54f, 52f); p.close(); body(m.metal, 1.8f)
            }
            else -> { // elmos de metal (bosque, gelo, sombrio, dracônico)
                // enfeites atrás do elmo
                when (v) {
                    3 -> { leaf(28f, 40f, 26f, 8f, -50f, m.soft); leaf(72f, 40f, 26f, 8f, 50f, m.soft); leaf(50f, 20f, 22f, 7f, 0f, lt(m.soft, 0.15f)) }
                    5 -> for (k in -2..2) {
                        val x = 50f + k * 12f; val h = 22f - kotlin.math.abs(k) * 5f
                        p.reset(); p.moveTo(x - 6f, 26f); p.lineTo(x, 26f - h); p.lineTo(x + 6f, 26f); p.close(); body(m.gem, 2f)
                    }
                    6 -> for (sg in intArrayOf(-1, 1)) {
                        c.save(); if (sg < 0) c.scale(-1f, 1f, 50f, 50f)
                        p.reset(); p.moveTo(68f, 36f); p.cubicTo(84f, 30f, 90f, 18f, 86f, 4f); p.cubicTo(96f, 18f, 92f, 38f, 74f, 48f); p.close()
                        body(col(0xFFD7CCC8), 2.4f); c.restore()
                    }
                    else -> for (sg in intArrayOf(-1, 1)) {
                        c.save(); if (sg < 0) c.scale(-1f, 1f, 50f, 50f)
                        p.reset(); p.moveTo(74f, 50f); p.lineTo(98f, 20f); p.lineTo(92f, 40f); p.lineTo(98f, 44f); p.lineTo(88f, 54f); p.lineTo(94f, 60f); p.lineTo(76f, 64f); p.close()
                        body(m.trim, 2.4f); c.restore()
                    }
                }
                // cúpula
                p.reset(); p.moveTo(20f, 80f); p.lineTo(20f, 50f); p.cubicTo(20f, 26f, 34f, 16f, 50f, 16f); p.cubicTo(66f, 16f, 80f, 26f, 80f, 50f); p.lineTo(80f, 80f)
                p.lineTo(62f, 86f); p.lineTo(60f, 62f); p.lineTo(40f, 62f); p.lineTo(38f, 86f); p.close()
                body(m.metal, 3f)
                // visor/abertura
                p.reset(); p.moveTo(30f, 50f); p.lineTo(70f, 50f); p.lineTo(66f, 58f); p.lineTo(56f, 58f); p.lineTo(54f, 78f); p.lineTo(46f, 78f); p.lineTo(44f, 58f); p.lineTo(34f, 58f); p.close()
                flat(col(0xFF15100E), 2f)
                if (v == 6) { line(34f, 54f, 46f, 54f, 3f, lt(m.gem, 0.3f)); line(54f, 54f, 66f, 54f, 3f, lt(m.gem, 0.3f)) }
                // aro e crista
                line(21f, 46f, 79f, 46f, 3.5f, if (v == 7) m.trim else dk(m.metal, 0.25f))
                line(50f, 18f, 50f, 44f, 4f, if (v == 7) m.trim else dk(m.metal, 0.2f))
                hl(28f, 44f, 34f, 28f, 3f, 170)
                if (m.gem != 0 && v != 5) gem(50f, 38f, 4.5f, m.gem)
                if (v == 5) { gem(50f, 38f, 4.5f, m.gem); hl(64f, 30f, 70f, 40f, 2f, 200) }
            }
        }
    }

    // ---------- armaduras ----------
    private fun armor(v: Int, m: Mat) {
        val cloth = v <= 3
        val baseC = when (v) { 1 -> m.soft; 2 -> m.soft; 3 -> m.soft; else -> m.metal }
        // tronco
        p.reset()
        p.moveTo(34f, 14f); p.lineTo(66f, 14f); p.lineTo(84f, 24f); p.lineTo(80f, 44f); p.lineTo(72f, 42f); p.lineTo(72f, 86f)
        p.quadTo(50f, 92f, 28f, 86f); p.lineTo(28f, 42f); p.lineTo(20f, 44f); p.lineTo(16f, 24f); p.close()
        body(baseC, 3f)
        // gola em V
        p.reset(); p.moveTo(38f, 14f); p.lineTo(50f, 32f); p.lineTo(62f, 14f); p.close(); flat(dk(baseC, 0.55f), 2.4f)
        when (v) {
            1 -> { // túnica: cinto de corda
                line(28f, 64f, 72f, 64f, 5f, OUT); line(28f, 64f, 72f, 64f, 3f, m.trim)
                line(54f, 64f, 58f, 78f, 2.4f, m.trim); line(54f, 64f, 50f, 78f, 2.4f, m.trim)
                hl(32f, 40f, 32f, 60f, 2.5f, 110)
            }
            2 -> { // colete de couro com cadarço
                s.strokeWidth = 1.8f; s.color = col(0xFFEFD9B4)
                for (k in 0..3) { val y = 38f + k * 10f; c.drawLine(45f, y, 55f, y + 6f, s); c.drawLine(55f, y, 45f, y + 6f, s) }
                line(50f, 32f, 50f, 88f, 2f, al(OUT, 170))
                s.strokeWidth = 1.3f; s.color = al(lt(m.soft, 0.5f), 200); s.pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 3f), 0f)
                p.reset(); p.moveTo(31f, 44f); p.lineTo(31f, 84f); p.moveTo(69f, 44f); p.lineTo(69f, 84f); c.drawPath(p, s); s.pathEffect = null
                line(28f, 74f, 72f, 74f, 5f, m.trim); p.reset(); p.addRect(46f, 71f, 54f, 77f, Path.Direction.CW); body(col(0xFFBDBDBD), 1.6f)
            }
            3 -> { // gibão do bosque: ombreiras de folhas
                line(28f, 66f, 72f, 66f, 6f, m.trim); gem(50f, 66f, 4f, m.gem)
                leaf(24f, 30f, 22f, 8f, -70f, lt(m.soft, 0.1f)); leaf(22f, 34f, 18f, 7f, -110f, dk(m.soft, 0.1f))
                leaf(76f, 30f, 22f, 8f, 70f, lt(m.soft, 0.1f)); leaf(78f, 34f, 18f, 7f, 110f, dk(m.soft, 0.1f))
                line(40f, 40f, 40f, 60f, 1.6f, al(OUT, 120)); line(60f, 40f, 60f, 60f, 1.6f, al(OUT, 120))
            }
            else -> {
                // placas de metal
                s.strokeWidth = 2f; s.color = al(dk(m.metal, 0.6f), 200)
                r.set(30f, 34f, 70f, 74f); c.drawArc(r, 20f, 140f, false, s)
                line(50f, 32f, 50f, 70f, 2f, al(dk(m.metal, 0.6f), 180))
                // faixas de saiote
                for (k in 0..2) { p.reset(); p.addRoundRect(RectF(30f + k * 14f, 76f, 42f + k * 14f, 94f), 3f, 3f, Path.Direction.CW); body(if (v == 4) m.soft else dk(m.metal, 0.2f), 2.2f) }
                // ombreiras
                for (sg in intArrayOf(-1, 1)) {
                    c.save(); if (sg < 0) c.scale(-1f, 1f, 50f, 50f)
                    p.reset(); p.moveTo(64f, 16f); p.cubicTo(80f, 12f, 92f, 20f, 90f, 36f); p.lineTo(70f, 34f); p.close(); body(dk(m.metal, 0.1f), 2.6f)
                    when (v) {
                        5 -> { p.reset(); p.moveTo(76f, 16f); p.lineTo(82f, 2f); p.lineTo(86f, 20f); p.close(); body(m.gem, 1.8f) }
                        6 -> { p.reset(); p.moveTo(74f, 16f); p.lineTo(88f, 0f); p.lineTo(84f, 20f); p.close(); body(col(0xFFD7CCC8), 1.8f) }
                        7 -> line(66f, 18f, 88f, 34f, 2.6f, m.trim)
                    }
                    c.restore()
                }
                when (v) {
                    4 -> { // sol no peito
                        for (k in 0 until 8) { c.save(); c.rotate(k * 45f, 50f, 50f); line(50f, 36f, 50f, 40f, 3f, m.trim); c.restore() }
                        gem(50f, 50f, 7f, m.gem)
                    }
                    5 -> { hl(34f, 40f, 40f, 64f, 3f, 200); gem(50f, 46f, 5f, m.gem) }
                    6 -> { // runa
                        s.strokeWidth = 4f; s.color = al(m.gem, 90)
                        p.reset(); p.moveTo(44f, 40f); p.lineTo(56f, 52f); p.lineTo(44f, 64f); p.moveTo(50f, 38f); p.lineTo(50f, 66f); c.drawPath(p, s)
                        s.strokeWidth = 1.8f; s.color = lt(m.gem, 0.4f); c.drawPath(p, s)
                    }
                    else -> { // escamas
                        s.strokeWidth = 1.5f; s.color = al(OUT, 140)
                        for (row in 0..3) for (k in 0..3) {
                            val x = 32f + k * 10f + (row % 2) * 5f; val y = 40f + row * 8f
                            if (x < 70f) { r.set(x, y - 4f, x + 10f, y + 6f); c.drawArc(r, 0f, 180f, false, s) }
                        }
                        line(28f, 72f, 72f, 72f, 3.5f, m.trim); gem(50f, 42f, 5f, m.gem)
                    }
                }
                hl(31f, 26f, 31f, 44f, 2.5f, 160)
            }
        }
        if (cloth) hl(22f, 26f, 30f, 18f, 2.5f, 150)
    }

    // ---------- escudos ----------
    private fun heater() {
        p.reset(); p.moveTo(16f, 12f); p.lineTo(84f, 12f); p.lineTo(84f, 42f); p.cubicTo(84f, 66f, 66f, 82f, 50f, 94f)
        p.cubicTo(34f, 82f, 16f, 66f, 16f, 42f); p.close()
    }

    private fun shield(v: Int, m: Mat) {
        when (v) {
            1, 2 -> { // broquel redondo
                p.reset(); p.addCircle(50f, 52f, 40f, Path.Direction.CW); body(m.metal, 3f)
                p.reset(); p.addCircle(50f, 52f, 33f, Path.Direction.CW); body(if (v == 1) m.trim else m.soft, 2.4f)
                if (v == 1) for (k in -2..2) line(50f + k * 11f, 22f + kotlin.math.abs(k) * 3f, 50f + k * 11f, 82f - kotlin.math.abs(k) * 3f, 1.6f, al(OUT, 140))
                else { s.strokeWidth = 1.5f; s.color = col(0xFFEFD9B4); s.pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 3f), 0f); c.drawCircle(50f, 52f, 28f, s); s.pathEffect = null }
                for (k in 0 until 8) { val a = k * 0.785f; circ(50f + cos(a) * 36.5f, 52f + sin(a) * 36.5f, 1.8f, lt(m.metal, 0.5f)) }
                p.reset(); p.addCircle(50f, 52f, 10f, Path.Direction.CW); body(col(0xFFB0BEC5), 2.4f)
                circ(47f, 49f, 3f, al(WHITE, 200))
                hl(22f, 40f, 30f, 26f, 2.5f, 150)
            }
            else -> {
                heater()
                val face = when (v) { 3 -> m.trim; else -> m.metal }
                body(face, 3f)
                // borda interna
                val q = Path(); q.moveTo(22f, 18f); q.lineTo(78f, 18f); q.lineTo(78f, 42f); q.cubicTo(78f, 62f, 63f, 76f, 50f, 86f); q.cubicTo(37f, 76f, 22f, 62f, 22f, 42f); q.close()
                s.strokeWidth = 2f; s.color = al(if (v == 7) m.trim else lt(face, 0.5f), 220); c.drawPath(q, s)
                when (v) {
                    3 -> { // casca + folha
                        for (k in 0..4) { s.strokeWidth = 1.5f; s.color = al(OUT, 120); c.drawLine(28f + k * 11f, 20f, 26f + k * 12f, 70f - kotlin.math.abs(k - 2) * 8f, s) }
                        leaf(50f, 72f, 40f, 14f, 0f, m.soft)
                    }
                    4 -> { // escaravelho
                        p.reset(); p.addOval(RectF(38f, 30f, 62f, 70f), Path.Direction.CW); body(col(0xFF00897B), 2.4f)
                        line(50f, 36f, 50f, 70f, 1.6f, OUT)
                        p.reset(); p.addCircle(50f, 30f, 7f, Path.Direction.CW); body(col(0xFF26A69A), 2f)
                        s.strokeWidth = 2.4f; s.color = OUT
                        for (sg in intArrayOf(-1, 1)) for (k in 0..2) c.drawLine(50f + sg * 11f, 42f + k * 9f, 50f + sg * 20f, 38f + k * 11f, s)
                        p.reset(); p.moveTo(38f, 46f); p.cubicTo(22f, 38f, 22f, 24f, 30f, 20f); p.cubicTo(32f, 30f, 36f, 36f, 40f, 40f); p.close(); body(m.metal, 1.8f)
                        p.reset(); p.moveTo(62f, 46f); p.cubicTo(78f, 38f, 78f, 24f, 70f, 20f); p.cubicTo(68f, 30f, 64f, 36f, 60f, 40f); p.close(); body(m.metal, 1.8f)
                        gem(50f, 22f, 4f, m.gem)
                    }
                    5 -> { // floco de gelo
                        for (k in 0 until 3) { c.save(); c.rotate(k * 60f, 50f, 46f); line(50f, 30f, 50f, 62f, 5f, OUT); line(50f, 30f, 50f, 62f, 2.6f, WHITE); c.restore() }
                        gem(50f, 46f, 5f, m.gem); hl(24f, 22f, 26f, 44f, 3f, 200)
                    }
                    6 -> { // olho sombrio e espinhos
                        for (x in floatArrayOf(16f, 84f)) { p.reset(); p.moveTo(x, 14f); p.lineTo(x + (if (x < 50f) -8f else 8f), 2f); p.lineTo(x + (if (x < 50f) 6f else -6f), 12f); p.close(); body(col(0xFFD7CCC8), 1.8f) }
                        p.reset(); p.moveTo(32f, 46f); p.quadTo(50f, 30f, 68f, 46f); p.quadTo(50f, 62f, 32f, 46f); p.close(); flat(col(0xFF120E18), 2f)
                        orb(50f, 46f, 6f, m.gem)
                    }
                    else -> { // escamas de dragão
                        s.strokeWidth = 1.6f; s.color = al(OUT, 150)
                        for (row in 0..5) for (k in 0..5) {
                            val x = 22f + k * 10f + (row % 2) * 5f; val y = 22f + row * 9f
                            if (x < 76f && y < 80f - kotlin.math.abs(x - 45f) * 0.6f) { r.set(x, y - 4f, x + 10f, y + 6f); c.drawArc(r, 0f, 180f, false, s) }
                        }
                        p.reset(); p.moveTo(36f, 44f); p.quadTo(50f, 34f, 64f, 44f); p.quadTo(50f, 54f, 36f, 44f); p.close(); body(m.trim, 2.2f)
                        p.reset(); p.moveTo(50f, 36f); p.quadTo(54f, 44f, 50f, 52f); p.quadTo(46f, 44f, 50f, 36f); p.close(); flat(OUT)
                    }
                }
                hl(22f, 20f, 22f, 44f, 3f, 150)
            }
        }
    }

    // ---------- botas (perfil, bico para a direita) ----------
    private fun boots(v: Int, m: Mat) {
        if (v == 1) { // sandálias
            p.reset(); p.moveTo(10f, 80f); p.lineTo(86f, 80f); p.quadTo(94f, 80f, 92f, 88f); p.lineTo(12f, 88f); p.quadTo(6f, 86f, 10f, 80f); p.close(); body(m.trim, 2.6f)
            p.reset(); p.moveTo(22f, 80f); p.cubicTo(20f, 60f, 30f, 44f, 34f, 30f); p.lineTo(44f, 30f); p.cubicTo(44f, 46f, 48f, 62f, 84f, 74f); p.lineTo(84f, 80f); p.close()
            f.color = al(col(0xFFFFE0B2), 120); c.drawPath(p, f)
            line(30f, 46f, 44f, 46f, 4f, m.soft); line(28f, 60f, 54f, 66f, 4f, m.soft); line(60f, 70f, 80f, 76f, 4f, m.soft)
            line(30f, 46f, 44f, 46f, 1f, OUT)
            return
        }
        // cano e pé
        val bootC = when (v) { 2 -> m.soft; 3 -> m.trim; 4 -> m.soft; 5 -> m.soft; else -> m.metal }
        p.reset()
        p.moveTo(22f, 12f); p.lineTo(54f, 12f); p.lineTo(54f, 54f); p.cubicTo(60f, 60f, 76f, 62f, 86f, 66f)
        p.cubicTo(94f, 70f, 94f, 84f, 86f, 86f); p.lineTo(20f, 86f); p.cubicTo(18f, 70f, 22f, 50f, 22f, 12f); p.close()
        body(bootC, 3f)
        // sola
        p.reset(); p.moveTo(18f, 84f); p.lineTo(90f, 84f); p.quadTo(94f, 92f, 86f, 94f); p.lineTo(20f, 94f); p.quadTo(14f, 90f, 18f, 84f); p.close()
        body(dk(bootC, 0.55f), 2.4f)
        hl(28f, 18f, 28f, 70f, 3f, 130)
        when (v) {
            2 -> { // cadarço e dobra
                s.strokeWidth = 1.8f; s.color = col(0xFFEFD9B4)
                for (k in 0..3) { val y = 30f + k * 7f; c.drawLine(44f, y, 54f, y + 4f, s) }
                p.reset(); p.addRoundRect(RectF(18f, 10f, 58f, 22f), 4f, 4f, Path.Direction.CW); body(m.trim, 2.4f)
            }
            3 -> { // cano dobrado com folhas
                p.reset(); p.moveTo(18f, 12f); p.lineTo(58f, 12f); p.lineTo(54f, 28f); p.lineTo(44f, 22f); p.lineTo(34f, 30f); p.lineTo(26f, 22f); p.lineTo(18f, 28f); p.close(); body(m.soft, 2.4f)
                leaf(56f, 18f, 18f, 6f, 60f, lt(m.soft, 0.1f)); gem(36f, 50f, 3.5f, m.gem)
                line(24f, 66f, 54f, 66f, 3f, al(OUT, 160))
            }
            4 -> { // panos enrolados, tornozeleira dourada e bico curvo
                s.strokeWidth = 1.8f; s.color = al(dk(m.soft, 0.5f), 200)
                for (k in 0..4) c.drawLine(22f, 20f + k * 8f, 54f, 16f + k * 8f, s)
                line(22f, 56f, 56f, 56f, 6f, OUT); line(22f, 56f, 56f, 56f, 4f, m.metal); gem(38f, 56f, 3.5f, m.gem)
                s.strokeWidth = 7f; s.color = OUT; p.reset(); p.moveTo(84f, 70f); p.quadTo(98f, 66f, 94f, 54f); c.drawPath(p, s)
                s.strokeWidth = 4f; s.color = m.soft; c.drawPath(p, s)
            }
            5 -> { // pele: cano felpudo
                p.reset()
                p.moveTo(14f, 30f); for (k in 0..6) { val x = 14f + k * 7f; p.quadTo(x + 3.5f, 36f, x + 7f, 30f) }
                p.lineTo(62f, 8f); for (k in 6 downTo 0) { val x = 14f + k * 7f; p.quadTo(x + 3.5f, 2f, x, 8f) }
                p.close(); body(col(0xFFF5F5F5), 2.4f)
                line(30f, 14f, 32f, 26f, 1.4f, al(OUT, 90)); line(44f, 14f, 46f, 26f, 1.4f, al(OUT, 90))
                gem(38f, 48f, 3.5f, m.gem)
            }
            6 -> { // grevas sombrias
                for (k in 0..2) line(23f, 30f + k * 14f, 53f, 30f + k * 14f, 2f, al(dk(m.metal, 0.7f), 200))
                p.reset(); p.moveTo(54f, 26f); p.lineTo(70f, 18f); p.lineTo(58f, 38f); p.close(); body(col(0xFFD7CCC8), 2f)
                p.reset(); p.addRoundRect(RectF(16f, 8f, 60f, 18f), 4f, 4f, Path.Direction.CW); body(m.soft, 2.4f)
                gem(38f, 44f, 4f, m.gem)
            }
            else -> { // dracônicas: escamas e garra
                s.strokeWidth = 1.5f; s.color = al(OUT, 150)
                for (row in 0..4) for (k in 0..2) { val x = 24f + k * 10f + (row % 2) * 5f; val y = 20f + row * 9f; if (x < 52f) { r.set(x, y - 4f, x + 10f, y + 6f); c.drawArc(r, 0f, 180f, false, s) } }
                for (k in 0..1) { p.reset(); p.moveTo(84f + k * 0f, 72f + k * 8f); p.lineTo(98f, 74f + k * 8f); p.lineTo(86f, 80f + k * 8f); p.close(); body(m.trim, 1.8f) }
                p.reset(); p.addRoundRect(RectF(16f, 8f, 60f, 18f), 4f, 4f, Path.Direction.CW); body(m.trim, 2.4f)
                gem(38f, 13f, 3.5f, m.gem)
            }
        }
    }

    // ---------- acessórios ----------
    private fun accessory(v: Int, m: Mat) {
        when (v) {
            1, 3, 5, 7 -> { // anéis
                val band = when (v) { 1 -> m.metal; 3 -> col(0xFFE0B04A); 5 -> col(0xFFCFD8DC); else -> m.trim }
                r.set(18f, 38f, 82f, 92f)
                s.strokeWidth = 14f; s.color = OUT; c.drawOval(r, s)
                s.strokeWidth = 10f
                s.shader = LinearGradient(18f, 38f, 82f, 92f, lt(band, 0.45f), dk(band, 0.45f), Shader.TileMode.CLAMP); c.drawOval(r, s); s.shader = null
                r.set(20f, 40f, 80f, 90f); s.strokeWidth = 2f; s.color = al(WHITE, 140); c.drawArc(r, 160f, 80f, false, s)
                when (v) {
                    1 -> { p.reset(); p.addRoundRect(RectF(38f, 26f, 62f, 44f), 6f, 6f, Path.Direction.CW); body(m.metal, 2.6f); line(42f, 35f, 58f, 35f, 1.6f, al(OUT, 140)) }
                    3 -> { // âmbar com folhas
                        leaf(40f, 36f, 18f, 6f, -60f, col(0xFF7CB342)); leaf(60f, 36f, 18f, 6f, 60f, col(0xFF7CB342))
                        p.reset(); p.addOval(RectF(36f, 14f, 64f, 46f), Path.Direction.CW); f.shader = RadialGradient(46f, 24f, 22f, col(0xFFFFE082), col(0xFFE65100), Shader.TileMode.CLAMP)
                        c.drawPath(p, f); f.shader = null; s.strokeWidth = 2.6f; s.color = OUT; c.drawPath(p, s)
                        circ(44f, 22f, 3.5f, al(WHITE, 200)); circ(52f, 34f, 2f, al(col(0xFF3E2723), 160))
                    }
                    5 -> { // cristal de gelo
                        f.shader = RadialGradient(50f, 26f, 26f, al(m.gem, 150), al(m.gem, 0), Shader.TileMode.CLAMP); c.drawCircle(50f, 26f, 26f, f); f.shader = null
                        p.reset(); p.moveTo(50f, 4f); p.lineTo(62f, 22f); p.lineTo(50f, 44f); p.lineTo(38f, 22f); p.close(); body(m.metal, 2.6f)
                        line(50f, 6f, 50f, 42f, 1.4f, al(WHITE, 200)); line(38f, 22f, 62f, 22f, 1.4f, al(m.trim, 200))
                    }
                    else -> { // dragão enrolado com rubi
                        for (sg in intArrayOf(-1, 1)) {
                            c.save(); if (sg < 0) c.scale(-1f, 1f, 50f, 50f)
                            p.reset(); p.moveTo(56f, 40f); p.cubicTo(66f, 30f, 76f, 34f, 80f, 26f); p.lineTo(78f, 38f); p.cubicTo(72f, 44f, 64f, 44f, 58f, 46f); p.close(); body(m.trim, 2f)
                            c.restore()
                        }
                        p.reset(); p.addCircle(50f, 32f, 13f, Path.Direction.CW); body(m.trim, 2.6f)
                        gem(50f, 32f, 8.5f, m.gem)
                    }
                }
            }
            else -> { // amuletos (pena, sol, espectral): cordão + pingente
                s.strokeWidth = 5f; s.color = OUT
                r.set(14f, -10f, 86f, 52f); c.drawArc(r, 15f, 150f, false, s)
                s.strokeWidth = 3f; s.color = if (v == 2) col(0xFF8D6E63) else lt(m.metal, 0.2f); c.drawArc(r, 15f, 150f, false, s)
                if (v != 2) { // elos de corrente
                    for (k in 0..6) { val a = (20f + k * 22f) * 0.0174533f; circ(50f + cos(a) * 36f, 21f + sin(a) * 31f, 2f, lt(m.metal, 0.5f)) }
                }
                when (v) {
                    2 -> { // pena
                        circ(50f, 52f, 4f, col(0xFF8D6E63))
                        c.save(); c.rotate(18f, 50f, 54f)
                        p.reset(); p.moveTo(50f, 54f); p.cubicTo(66f, 62f, 66f, 84f, 52f, 98f); p.cubicTo(38f, 86f, 36f, 64f, 50f, 54f); p.close(); body(col(0xFFE8EAF6), 2.4f)
                        line(50f, 56f, 52f, 96f, 1.6f, col(0xFF795548))
                        for (k in 0..4) { line(51f, 64f + k * 6f, 60f, 60f + k * 6f, 1.2f, al(OUT, 100)); line(51f, 64f + k * 6f, 42f, 60f + k * 6f, 1.2f, al(OUT, 100)) }
                        p.reset(); p.moveTo(50f, 82f); p.cubicTo(58f, 86f, 58f, 92f, 52f, 98f); p.cubicTo(46f, 92f, 44f, 86f, 50f, 82f); p.close(); flat(col(0xFF5C6BC0))
                        c.restore()
                        circ(40f, 52f, 3f, col(0xFFFFCA28)); circ(60f, 52f, 3f, col(0xFF4FC3F7))
                    }
                    4 -> { // sol
                        for (k in 0 until 12) {
                            c.save(); c.rotate(k * 30f, 50f, 70f)
                            p.reset(); p.moveTo(45f, 54f); p.lineTo(50f, 44f); p.lineTo(55f, 54f); p.close(); body(m.metal, 1.8f)
                            c.restore()
                        }
                        p.reset(); p.addCircle(50f, 70f, 17f, Path.Direction.CW); body(m.metal, 2.8f)
                        ring(50f, 70f, 12.5f, 1.6f, al(dk(m.metal, 0.5f), 200))
                        gem(50f, 70f, 8f, m.gem)
                    }
                    else -> { // espectral: moldura crescente com orbe
                        p.reset(); p.addCircle(50f, 70f, 20f, Path.Direction.CW)
                        val cut = Path(); cut.addCircle(50f, 64f, 17f, Path.Direction.CW); p.op(cut, Path.Op.DIFFERENCE)
                        body(m.metal, 2.6f)
                        orb(50f, 66f, 10f, m.gem)
                        s.strokeWidth = 2f; s.color = al(lt(m.gem, 0.4f), 170)
                        p.reset(); p.moveTo(34f, 84f); p.cubicTo(28f, 90f, 34f, 96f, 26f, 98f); c.drawPath(p, s)
                        p.reset(); p.moveTo(66f, 84f); p.cubicTo(72f, 90f, 66f, 96f, 74f, 98f); c.drawPath(p, s)
                        circ(50f, 48f, 4f, lt(m.metal, 0.3f))
                    }
                }
            }
        }
    }
}
