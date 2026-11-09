package br.com.bifrost

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

class Renderer(private val g: Game, private val res: Resources) {
    val VH = 720f
    var vw = 1280f
    var camX = 0f
    var camY = 0f
    var zoom = Style.ZOOM_DEFAULT

    /** Áreas seguras (notch / barras), em unidades virtuais. */
    var safeL = 0f
    var safeR = 0f
    var safeT = 0f
    var safeB = 0f

    private val art = Art()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val textOut = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = Color.BLACK
    }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fadePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 140 }
    private val rect = RectF()
    private val srcR = Rect()
    private val dstR = RectF()
    private val path = Path()

    // ---------- y-sort sem alocação ----------
    private val items = arrayOfNulls<Any>(512)
    private val ys = FloatArray(512)
    private var nItems = 0
    private val playerToken = Any()
    private val OUT_DX = floatArrayOf(-Style.OUTLINE, Style.OUTLINE, 0f, 0f)
    private val OUT_DY = floatArrayOf(0f, 0f, -Style.OUTLINE, Style.OUTLINE)

    // ---------- chão em cache ----------
    private var groundBmp: Bitmap? = null
    private var groundMap = -1
    private var groundSc = 1f

    // ---------- botões (pool, sem alocar por frame no HUD) ----------
    private class Btn {
        var l = 0f; var t = 0f; var r = 0f; var b = 0f
        var act: (() -> Unit)? = null
    }

    private val btnPool = Array(128) { Btn() }
    private var nBtn = 0
    private var cardPage = 0
    private var itensTab = 0
    private var bagPage = 0
    private var selItem: GearItem? = null
    private var resetArm = 0f

    private val actMenu: Array<() -> Unit> = arrayOf(
        { g.panel = Panel.STATUS },
        { g.panel = Panel.SKILLS },
        { cardPage = 0; bagPage = 0; selItem = null; g.panel = Panel.ITENS },
        { if (g.map.id != 0 && !g.dead) g.changeMap(0, 2) },
    )
    private val actFx: () -> Unit = {
        g.p.fx = !g.p.fx
        g.addLog(if (g.p.fx) "Efeitos novos: LIGADOS" else "Efeitos novos: DESLIGADOS (visual antigo)", C_GRAY)
    }
    private val actAuto: () -> Unit = {
        g.p.autoMode = !g.p.autoMode
        g.addLog(if (g.p.autoMode) "Auto-ataque ligado." else "Auto-ataque desligado.", C_GRAY)
    }
    private val actHp: () -> Unit = { g.drinkHp(true) }
    private val actSp: () -> Unit = { g.drinkSp(true) }
    private val actSkill: Array<() -> Unit> = Array(4) { k ->
        { val ids = g.p.job.skills; if (k < ids.size) g.useSkill(ids[k]) }
    }
    private val actNone: () -> Unit = {}
    private val actBackdrop: () -> Unit = { if (g.panel != Panel.MORTE) g.panel = Panel.NONE }

    // ---------- cache de textos do HUD (só recria quando o valor muda) ----------
    private var cLv = -1; private var sLv = ""
    private var cJlv = -1; private var cJob: Job? = null; private var sJlv = ""
    private var cZeny = -1L; private var sZeny = ""
    private var cHp = -1; private var cMhp = -1; private var sHp = ""
    private var cSp = -1; private var cMsp = -1; private var sSp = ""
    private var cBe = -1; private var sBe = ""
    private var cJe = -1; private var sJe = ""
    private var cMap = -1; private var sTitle = ""
    private var cBoss = -2; private var sBoss = ""
    private var cHpPot = -1; private var sHpPot = ""
    private var cSpPot = -1; private var sSpPot = ""
    private val cSlot = IntArray(4) { -1 }
    private val cSlotSp = IntArray(4) { -1 }
    private val sSlot = Array(4) { "" }

    // barras de XP animadas
    private var dispBe = 0f
    private var dispJe = 0f
    private var seenLv = -1
    private var seenJlv = -1
    private var wrapBe = false
    private var wrapJe = false

    fun worldX(sx: Float) = (sx - vw / 2) / zoom + camX
    fun worldY(sy: Float) = (sy - VH / 2) / zoom + camY

    fun tap(x: Float, y: Float): Boolean {
        for (i in nBtn - 1 downTo 0) {
            val b = btnPool[i]
            if (x >= b.l && x <= b.r && y >= b.t && y <= b.b) {
                b.act?.invoke()
                return true
            }
        }
        return false
    }

    private fun addBtn(l: Float, t: Float, r: Float, b: Float, act: () -> Unit) {
        if (nBtn >= btnPool.size) return
        val x = btnPool[nBtn++]
        x.l = l; x.t = t; x.r = r; x.b = b; x.act = act
    }

    fun draw(c: Canvas, w: Float, dt: Float) {
        vw = w
        frameDt = dt
        nBtn = 0
        if (resetArm > 0f) resetArm -= dt
        if (g.selecting) {
            if (g.title) drawTitle(c) else drawSelect(c)
            return
        }
        drawWorld(c)
        drawHud(c, dt)
        if (g.panel != Panel.NONE) drawPanel(c)
        if (testMode) drawTest(c, dt)
    }

    /** Tamanho de texto no mundo: compensa o zoom para não ficar gigante. */
    private fun ws(s: Float) = s * 1.1f / zoom

    // =====================================================================
    // MUNDO
    // =====================================================================
    private fun drawWorld(c: Canvas) {
        val m = g.map
        val fx = g.p.fx
        zoom = g.p.zoom
        art.anim = fx
        val hw = vw / 2f / zoom
        val hh = VH / 2f / zoom
        // câmera com seguimento suave e zona morta pequena (sem trancos ao trocar de alvo)
        val tcx = if (m.w <= hw * 2) m.w / 2 else g.px.coerceIn(hw, m.w - hw)
        val tcy = if (m.h <= hh * 2) m.h / 2 else g.py.coerceIn(hh, m.h - hh)
        if (camMap != m.id || abs(tcx - camX) > 500f || abs(tcy - camY) > 500f) {
            camX = tcx; camY = tcy; camMap = m.id
        } else {
            val dz = 12f
            val k = min(1f, frameDt * 6f)
            val ddx = tcx - camX
            val ddy = tcy - camY
            if (abs(ddx) > dz) camX += (ddx - sign(ddx) * dz) * k
            if (abs(ddy) > dz) camY += (ddy - sign(ddy) * dz) * k
        }
        if (m.w > hw * 2) camX = camX.coerceIn(hw, m.w - hw)
        if (m.h > hh * 2) camY = camY.coerceIn(hh, m.h - hh)
        // tremida leve (~2px) só em crítico
        var shx = 0f
        var shy = 0f
        if (g.shakeT > 0f) {
            shx = (kotlin.random.Random.nextFloat() - 0.5f) * 2.8f / zoom
            shy = (kotlin.random.Random.nextFloat() - 0.5f) * 2.8f / zoom
        }
        c.drawColor(col(0xFF1A1A22))
        c.save()
        c.translate(vw / 2, VH / 2)
        c.scale(zoom, zoom)
        c.translate(-camX - shx, -camY - shy)
        val l = camX - hw
        val t = camY - hh
        val r = camX + hw
        val b = camY + hh

        val forest = m.id == 1 && Forest.loaded
        if (m.id == 0 && Town.loaded) {
            // vila: a pintura inteira é o chão
            drawRegion(c, Town.ground, l, t, r, b)
        } else if (forest) {
            // chão (com sombras) + borda de floresta de cima (o personagem sempre fica na frente dela)
            drawRegion(c, Forest.ground, l, t, r, b)
            drawRegion(c, Forest.top, l, t, r, b)
        } else if (fx) drawGroundCached(c, m, l, t, r, b) else drawGroundSimple(c, m, l, t, r, b)
        if (fx && m.theme == 6) {
            for (i in g.decos.indices) {
                val d = g.decos[i]
                if (d.kind == 22 && d.x > l - 40 && d.x < r + 40 && d.y > t - 40 && d.y < b + 40) art.deco(c, d, g.time)
            }
        }

        for (i in g.portals.indices) {
            val p = g.portals[i]
            art.portal(c, p.x, p.y, g.time)
            outlined(c, MAPS[p.dest].nome, p.x, p.y - 74f, ws(17f), col(0xFFB3E5FC))
        }
        if (g.hasDest && g.target == null) {
            stroke.strokeWidth = 2f; stroke.color = col(0x99FFFFFF)
            val pr = 8f + 4f * sin(g.time * 8f)
            rect.set(g.destX - pr * 1.6f, g.destY - pr * 0.6f, g.destX + pr * 1.6f, g.destY + pr * 0.6f)
            c.drawOval(rect, stroke)
        }
        g.target?.let { tg ->
            stroke.strokeWidth = 2.5f; stroke.color = col(0xCCFF5252)
            rect.set(tg.x - tg.d.size * 1.1f, tg.y - tg.d.size * 0.4f, tg.x + tg.d.size * 1.1f, tg.y + tg.d.size * 0.4f)
            c.drawOval(rect, stroke)
        }

        // ----- y-sort: quem está mais abaixo é desenhado na frente -----
        nItems = 0
        for (i in g.decos.indices) {
            val d = g.decos[i]
            if (d.tall && d.x > l - 160 && d.x < r + 160 && d.y > t - 20 && d.y < b + 160) push(d, d.y)
        }
        if (forest) {
            // objetos do meio (árvores, arbustos, pedras) ordenados pela base junto com herói e monstros
            for (i in 0 until Forest.N) {
                val ox = Forest.OX[i]
                val bw = Forest.objBmp[i]?.width ?: 0
                if (ox + bw < l - 20 || ox > r + 20 || Forest.AY[i] < t - 20 || Forest.OY[i] > b + 20) continue
                push(Forest.objs[i], Forest.AY[i])
            }
        }
        for (i in g.mobs.indices) push(g.mobs[i], g.mobs[i].y)
        for (i in g.npcs.indices) push(g.npcs[i], g.npcs[i].y)
        if (!g.dead) push(playerToken, g.py)
        for (i in 1 until nItems) {
            val o = items[i]
            val y = ys[i]
            var j = i - 1
            while (j >= 0 && ys[j] > y) {
                items[j + 1] = items[j]; ys[j + 1] = ys[j]; j--
            }
            items[j + 1] = o; ys[j + 1] = y
        }
        for (i in 0 until nItems) {
            when (val o = items[i]) {
                is Deco -> art.deco(c, o, g.time)
                is Forest.Obj -> Forest.objBmp[o.i]?.let {
                    // herói atrás da copa: a árvore fica semitransparente para ele não sumir
                    val ox = Forest.OX[o.i]
                    val oy = Forest.OY[o.i]
                    val behind = !g.dead && g.py < Forest.AY[o.i] && g.px > ox + 4f && g.px < ox + it.width - 4f &&
                            g.py > oy + 20f && Forest.TYPE[o.i] == Forest.T_TREE
                    c.drawBitmap(it, ox, oy, if (behind) fadePaint else bmpPaint)
                }
                is Mob -> drawMob(c, o, fx)
                is Npc -> drawNpc(c, o, i, fx)
                else -> drawPlayer(c, fx)
            }
            items[i] = null
        }
        deadT = if (g.dead) deadT + frameDt else 0f
        if (g.dead && fx && hasSprite(g.p.job)) {
            spriteHero.skin = skinOf(g.p.job)
            spriteHero.drawDead(c, g.px, g.py, Style.CHAR_SCALE, deadT)
        } else if (g.dead) {
            art.shadow(c, g.px, g.py, 15f)
            art.rr(c, g.px - 22f, g.py - 12f, g.px + 22f, g.py, 5f, g.p.job.cor)
            art.circle(c, g.px + 22f, g.py - 8f, 11f, col(0xFFFFE0BD))
        }

        if (fx) drawClouds(c, m, l, t, r, b)
        // borda de floresta de baixo POR CIMA dos personagens (ele passa atrás das copas)
        if (forest) drawRegion(c, Forest.bottom, l, t, r, b)
        drawMobOverlay(c)
        if (fx) drawDrops(c)
        drawFx(c)
        if (fx) drawParticles(c)
        for (i in g.texts.indices) {
            val tx = g.texts[i]
            val age = tx.max - tx.life
            val a = (tx.life / tx.max * (if (tx.kind == TXT_DMG) 2.5f else 1.5f)).coerceIn(0f, 1f)
            val base = when (tx.kind) {
                TXT_DMG -> if (tx.big) 36f else 27f
                TXT_HERO -> 17f
                TXT_BIG -> 32f
                else -> if (tx.big) 32f else 21f
            }
            var scl = 1f
            var cor = tx.cor
            var alpha = a
            if (tx.kind == TXT_DMG) {
                // nasce pequeno, cresce até 1,4x em 200ms, assenta no normal; legível ~1s, depois cinza e desbota
                scl = when {
                    age < 0.2f -> 0.6f + 0.8f * Ease.apply(Ease.OUT, age / 0.2f)
                    age < 0.35f -> 1.4f - 0.4f * Ease.apply(Ease.IN_OUT, (age - 0.2f) / 0.15f)
                    else -> 1f
                }
                if (age > 1.0f) {
                    val g2 = ((age - 1.0f) / max(0.01f, tx.max - 1.0f)).coerceIn(0f, 1f)
                    cor = art.mix(tx.cor, col(0xFF9E9E9E), g2)
                    alpha = 1f - g2
                } else alpha = 1f
                scl *= tx.sz
            }
            outlined(c, tx.s, tx.x, tx.y, ws(base * scl), art.alpha(cor, (alpha * 255).toInt()))
        }
        c.restore()
    }

    /** Desenha só a parte visível de um bitmap do tamanho do mapa (1 px = 1 unidade de mundo). */
    private fun drawRegion(c: Canvas, bmp: Bitmap?, l: Float, t: Float, r: Float, b: Float) {
        if (bmp == null) return
        srcR.set(max(0f, l).toInt(), max(0f, t).toInt(), min(bmp.width.toFloat(), r + 2f).toInt(), min(bmp.height.toFloat(), b + 2f).toInt())
        if (srcR.width() <= 0 || srcR.height() <= 0) return
        dstR.set(srcR)
        c.drawBitmap(bmp, srcR, dstR, bmpPaint)
    }

    private fun push(o: Any, y: Float) {
        if (nItems >= items.size) return
        items[nItems] = o
        ys[nItems] = y
        nItems++
    }

    // ---------- chão antigo (efeitos desligados) ----------
    private fun drawGroundSimple(c: Canvas, m: MapDef, l: Float, t: Float, r: Float, b: Float) {
        fill.color = m.g1
        c.drawRect(0f, 0f, m.w, m.h, fill)
        fill.color = m.g2
        val ts = 80f
        var iy = floor(max(0f, t) / ts).toInt()
        while (iy * ts < min(b, m.h)) {
            var ix = floor(max(0f, l) / ts).toInt()
            while (ix * ts < min(r, m.w)) {
                if ((ix + iy) % 2 == 0) c.drawRect(ix * ts, iy * ts, min(ix * ts + ts, m.w), min(iy * ts + ts, m.h), fill)
                ix++
            }
            iy++
        }
        for (i in g.decos.indices) {
            val d = g.decos[i]
            if (!d.tall && d.x > l - 60 && d.x < r + 60 && d.y > t - 60 && d.y < b + 60) art.deco(c, d, g.time)
        }
    }

    // ---------- chão novo: gerado uma vez por mapa e guardado em bitmap ----------
    private fun drawGroundCached(c: Canvas, m: MapDef, l: Float, t: Float, r: Float, b: Float) {
        if (groundMap != m.id || groundBmp == null) buildGround(m)
        val bmp = groundBmp ?: return drawGroundSimple(c, m, l, t, r, b)
        val sc = groundSc
        srcR.set(
            (max(0f, l) * sc).toInt(), (max(0f, t) * sc).toInt(),
            min(bmp.width.toFloat(), r * sc + 2).toInt(), min(bmp.height.toFloat(), b * sc + 2).toInt()
        )
        dstR.set(srcR.left / sc, srcR.top / sc, srcR.right / sc, srcR.bottom / sc)
        c.drawBitmap(bmp, srcR, dstR, bmpPaint)
    }

    private fun buildGround(m: MapDef) {
        groundBmp?.recycle()
        groundBmp = null
        val sc = if (m.w * m.h <= 2_000_000f) 1.5f else 1f
        val bmp = try {
            Bitmap.createBitmap((m.w * sc).toInt(), (m.h * sc).toInt(), Bitmap.Config.RGB_565)
        } catch (e: OutOfMemoryError) {
            null
        } ?: return
        val c = Canvas(bmp)
        c.scale(sc, sc)
        val rnd = Random(m.id * 131 + 17)
        art.anim = true
        c.drawColor(m.g1)
        noise(c, rnd, 0f, 0f, m.w, m.h, m.g1)
        if (m.theme == 1 || m.theme == 2) dirtRoad(c, rnd, m)
        details(c, rnd, 0f, 0f, m.w, m.h, m.theme, 2600f)
        for (i in g.decos.indices) {
            val d = g.decos[i]
            if (!d.tall && d.kind != 22) art.deco(c, d, 0f)
        }
        groundBmp = bmp
        groundMap = m.id
        groundSc = sc
    }

    /** "Noise" barato: manchas suaves claras/escuras em duas escalas. */
    private fun noise(c: Canvas, rnd: Random, x0: Float, y0: Float, x1: Float, y1: Float, base: Int) {
        val w = x1 - x0
        val h = y1 - y0
        val light = art.mix(base, Color.WHITE, 0.22f)
        val dark = art.darker(base, 0.82f)
        repeat((w * h / 14000f).toInt()) {
            fill.color = art.alpha(if (rnd.nextBoolean()) light else dark, 16 + rnd.nextInt(22))
            c.drawCircle(x0 + rnd.nextFloat() * w, y0 + rnd.nextFloat() * h, 50f + rnd.nextFloat() * 120f, fill)
        }
        repeat((w * h / 2500f).toInt()) {
            fill.color = art.alpha(if (rnd.nextBoolean()) light else dark, 14 + rnd.nextInt(22))
            c.drawCircle(x0 + rnd.nextFloat() * w, y0 + rnd.nextFloat() * h, 6f + rnd.nextFloat() * 18f, fill)
        }
    }

    /** Pedrinhas, tufos de grama e flores espalhados (sem padrão repetido). */
    private fun details(c: Canvas, rnd: Random, x0: Float, y0: Float, x1: Float, y1: Float, theme: Int, density: Float) {
        val w = x1 - x0
        val h = y1 - y0
        val base = when (theme) { 0 -> Style.SAND; 1, 2 -> Style.GRASS; else -> g.map.g1 }
        val dark = art.darker(base, 0.7f)
        val flowers = intArrayOf(col(0xFFFFF59D), col(0xFFF8BBD0), Color.WHITE, col(0xFFD1C4E9))
        repeat((w * h / density).toInt()) {
            val x = x0 + rnd.nextFloat() * w
            val y = y0 + rnd.nextFloat() * h
            val roll = rnd.nextFloat()
            when (theme) {
                1, 2 -> when {
                    roll < 0.55f -> { // tufo
                        stroke.strokeWidth = 1.6f; stroke.color = art.alpha(dark, 170)
                        c.drawLine(x, y, x - 3f, y - 7f, stroke); c.drawLine(x, y, x, y - 9f, stroke); c.drawLine(x, y, x + 3f, y - 7f, stroke)
                    }
                    roll < 0.8f -> pebble(c, x, y, rnd)
                    else -> {
                        fill.color = flowers[rnd.nextInt(flowers.size)]
                        c.drawCircle(x, y, 2.2f, fill)
                        c.drawCircle(x + 5f, y + 2f, 1.8f, fill)
                    }
                }
                3 -> if (roll < 0.5f) {
                    stroke.strokeWidth = 1.5f; stroke.color = col(0x30FFFFFF)
                    rect.set(x - 18f, y - 4f, x + 18f, y + 4f); c.drawArc(rect, 200f, 140f, false, stroke)
                } else pebble(c, x, y, rnd)
                4 -> {
                    fill.color = if (roll < 0.6f) col(0x88FFFFFF) else col(0x2290CAF9)
                    c.drawCircle(x, y, if (roll < 0.6f) 1.6f else 8f, fill)
                }
                5 -> if (roll < 0.4f) {
                    stroke.strokeWidth = 1.4f; stroke.color = col(0x40000000)
                    c.drawLine(x, y, x + 9f, y + 3f, stroke); c.drawLine(x + 9f, y + 3f, x + 14f, y - 2f, stroke)
                } else if (roll < 0.7f) pebble(c, x, y, rnd) else {
                    fill.color = col(0x2E6E8B40); c.drawCircle(x, y, 5f, fill)
                }
                else -> {
                    fill.color = if (roll < 0.5f) col(0x33000000) else col(0x22FF7043)
                    c.drawCircle(x, y, 1.5f + rnd.nextFloat() * 2.5f, fill)
                }
            }
        }
    }

    private fun pebble(c: Canvas, x: Float, y: Float, rnd: Random) {
        val s = 2f + rnd.nextFloat() * 3f
        fill.color = col(0x55000000)
        rect.set(x - s, y - s * 0.5f + 1f, x + s, y + s * 0.6f + 1f); c.drawOval(rect, fill)
        fill.color = art.mix(Style.STONE, Color.WHITE, rnd.nextFloat() * 0.3f)
        rect.set(x - s, y - s * 0.6f, x + s, y + s * 0.5f); c.drawOval(rect, fill)
    }

    /** Estrada de terra serpenteando entre os portais oeste e leste. */
    private fun dirtRoad(c: Canvas, rnd: Random, m: MapDef) {
        path.reset()
        val cy = m.h / 2
        path.moveTo(0f, cy)
        var x = 0f
        while (x < m.w) {
            val nx = x + 300f
            path.quadTo(x + 150f, cy + (rnd.nextFloat() - 0.5f) * 220f, nx, cy + (rnd.nextFloat() - 0.5f) * 80f)
            x = nx
        }
        stroke.strokeWidth = 74f; stroke.color = art.alpha(art.mix(m.g1, Style.SAND_DARK, 0.55f), 210)
        c.drawPath(path, stroke)
        stroke.strokeWidth = 56f; stroke.color = art.alpha(art.mix(m.g1, Style.SAND, 0.6f), 230)
        c.drawPath(path, stroke)
    }

    // ---------- sombras de nuvens ----------
    private fun drawClouds(c: Canvas, m: MapDef, l: Float, t: Float, r: Float, b: Float) {
        fill.color = col(0x0B000000)
        for (i in 0 until 5) {
            val span = m.w + 1000f
            val x = ((i * 977f + g.time * (9f + i * 3f)) % span) - 500f
            val y = (i * 389f) % m.h + 80f
            if (x + 300f < l || x - 300f > r || y + 150f < t || y - 150f > b) continue
            rect.set(x - 220f, y - 60f, x + 220f, y + 60f); c.drawOval(rect, fill)
            rect.set(x - 110f, y - 100f, x + 150f, y + 30f); c.drawOval(rect, fill)
            rect.set(x - 250f, y - 20f, x + 40f, y + 80f); c.drawOval(rect, fill)
        }
    }

    private fun drawPlayer(c: Canvas, fx: Boolean) {
        val p = g.p
        if (g.lvlFx > 0f) {
            val k = g.lvlFx
            if (fx) {
                // coluna de luz dourada
                val a = (k / 1.6f).coerceIn(0f, 1f)
                for (i in 0 until 6) {
                    val w = 34f - i * 5f
                    fill.color = art.alpha(if (i < 3) C_GOLD else Color.WHITE, (a * 34).toInt())
                    rect.set(g.px - w, g.py - 240f, g.px + w, g.py); c.drawRoundRect(rect, w, w, fill)
                }
                art.oval(c, g.px - 44f, g.py - 14f, g.px + 44f, g.py + 14f, art.alpha(C_GOLD, (a * 120).toInt()))
            } else {
                for (i in 0..2) {
                    val rr = 20f + ((1.6f - k) * 60f + i * 18f) % 60f
                    stroke.strokeWidth = 3f; stroke.color = art.alpha(col(0xFFFFD54F), (k * 140).toInt())
                    rect.set(g.px - rr, g.py - rr * 0.4f - i * 12f, g.px + rr, g.py + rr * 0.4f - i * 12f)
                    c.drawOval(rect, stroke)
                }
            }
        }
        if (p.buff[3] > 0f || p.buff[9] > 0f || p.buff[12] > 0f) {
            val cor = if (p.buff[3] > 0f) col(0x55FF7043) else if (p.buff[9] > 0f) col(0x55FFCA28) else col(0x557E57C2)
            art.oval(c, g.px - 22f, g.py - 70f, g.px + 22f, g.py + 6f, cor)
        }
        val weapon = if (p.job == Job.APRENDIZ || p.job == Job.LADINO) 0 else p.job.weaponCat
        if (fx) {
            if (hasSprite(p.job)) drawSpriteHero(c) else drawHeroRig(c)
            return
        }
        art.hero(c, g.px, g.py, g.face, p.job.cor, p.job.cabelo, p.job.hat, weapon, g.walk, g.moving, g.atkAnim, Style.CHAR_SCALE, 0f)
        if (p.job == Job.LADINO) {
            art.line(c, g.px - g.face * 10f, g.py - 20f, g.px - g.face * 18f, g.py - 30f, 3f, col(0xFFCFD8DC))
        }
    }

    // ---------- herói por partes (novo) ----------
    private val heroRig = HeroRig(art)
    private val heroAnim = Animator()
    private val heroPose = FloatArray(N_CH)
    private var heroDir = DIR_SIDE
    private var lastPx = Float.NaN
    private var lastPy = 0f
    private var frameDt = 0.016f

    private fun hatFor(j: Job) = when (j) {
        Job.APRENDIZ -> 10
        Job.GUERREIRO -> 1
        else -> j.hat
    }

    private fun weaponFor(j: Job) = if (j == Job.APRENDIZ || j == Job.LADINO) 0 else j.weaponCat

    /** Direção pela movimentação (ou pelo alvo quando parado): lado, frente ou costas. */
    private fun chooseDir(dx: Float, dy: Float) {
        heroDir = if (abs(dx) >= abs(dy) * 0.75f) DIR_SIDE else if (dy > 0f) DIR_FRONT else DIR_BACK
    }

    private fun drawHeroRig(c: Canvas) {
        val dt = frameDt
        if (lastPx.isNaN() || dist(lastPx, lastPy, g.px, g.py) > 200f) {
            lastPx = g.px; lastPy = g.py
        }
        val vx = (g.px - lastPx) / max(dt, 1e-3f)
        val vy = (g.py - lastPy) / max(dt, 1e-3f)
        lastPx = g.px; lastPy = g.py
        if (g.moving && vx * vx + vy * vy > 225f) chooseDir(vx, vy)
        else {
            val t = g.target
            if (t != null) chooseDir(t.x - g.px, t.y - g.py)
        }
        heroAnim.play(if (g.moving) Clips.WALK_TMP else Clips.IDLE)
        heroAnim.update(dt)
        heroAnim.pose.copyInto(heroPose)
        if (g.atkAnim > 0f) { // golpe provisório: a etapa 3 traz antecipação, rastro e hit stop
            heroPose[CH_ARM_F] = -50f + (1f - g.atkAnim) * 140f
            heroPose[CH_WEAPON] = 20f
        }
        val p = g.p
        heroRig.update(dt, vx * g.face, heroPose[CH_HEAD], g.time)
        heroRig.draw(c, g.px, g.py, heroDir, g.face, heroPose, p.job.cor, p.job.cabelo, hatFor(p.job), weaponFor(p.job),
            Style.CHAR_SCALE, true, g.time)
    }

    // ---------- guerreiro em sprites (8 direções) ----------
    private val spriteHero = SpriteHero(res, art)
    private var spDir = SpriteHero.S
    private var deadT = 0f

    /** Classes com visual em sprites (as outras usam o boneco desenhado em código). */
    private fun hasSprite(job: Job) = job == Job.APRENDIZ || job == Job.GUERREIRO || job == Job.MAGO || job == Job.CACADOR

    private fun skinOf(job: Job) = when (job) {
        Job.APRENDIZ -> SpriteHero.APRENDIZ
        Job.MAGO -> SpriteHero.MAGO
        Job.CACADOR -> SpriteHero.CACADOR
        else -> SpriteHero.GUERREIRO
    }
    private var prevAtkSeq = -1
    private var prevCastSeq = -1
    private var prevHp = -1
    private var heroSpeed = 0f
    private var camMap = -1
    // direção com histerese e virada gradual
    private var turnTarget = SpriteHero.S
    private var turnStepT = 0f
    private var dirPendT = 0f

    /** Diferença angular (0..180) entre o vetor e o centro do setor da direção atual. */
    private fun sectorDelta(dx: Float, dy: Float, dirIdx: Int): Float {
        val va = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val ca = 90f - 45f * dirIdx
        var d = abs(va - ca) % 360f
        if (d > 180f) d = 360f - d
        return d
    }

    private fun drawSpriteHero(c: Canvas) {
        val dt = frameDt
        if (lastPx.isNaN() || dist(lastPx, lastPy, g.px, g.py) > 200f) {
            lastPx = g.px; lastPy = g.py
        }
        val vx = (g.px - lastPx) / max(dt, 1e-3f)
        val vy = (g.py - lastPy) / max(dt, 1e-3f)
        lastPx = g.px; lastPy = g.py
        // direção desejada: no ataque, para o alvo; andando, pelo movimento; parado com alvo, para o alvo
        var wx = 0f
        var wy = 0f
        val t = g.target
        // posicionando/atacando: vira de lado para o alvo (o golpe é feito de lado, com o braço da espada)
        if (g.hstate == HS_ATK) { wx = if (g.atkDirX >= 0f) 1f else -1f; wy = 0f }
        else if (g.skillT > 0f) { wx = if (g.castDirX >= 0f) 1f else -1f; wy = 0f }
        else if (g.hstate == HS_POS && t != null) { wx = if (t.x >= g.px) 1f else -1f; wy = 0f }
        else if (g.moving && vx * vx + vy * vy > 225f) { wx = vx; wy = vy }
        else if (t != null) { wx = t.x - g.px; wy = t.y - g.py }
        if (wx != 0f || wy != 0f) {
            val want = SpriteHero.dirOf(wx, wy)
            if (want != turnTarget) {
                // histerese: só troca se passar ~15° do limite do setor ou após 150ms
                dirPendT += dt
                if (sectorDelta(wx, wy, turnTarget) > 22.5f + 15f || dirPendT >= 0.15f) {
                    turnTarget = want
                    dirPendT = 0f
                }
            } else dirPendT = 0f
        }
        // virada suave: um setor (45°) a cada 40ms em direção ao alvo da virada
        turnStepT -= dt
        if (spDir != turnTarget && turnStepT <= 0f) {
            val diff = (turnTarget - spDir + 8) % 8
            spDir = if (diff <= 4) (spDir + 1) % 8 else (spDir + 7) % 8
            turnStepT = 0.04f
        }
        spriteHero.dir = spDir
        spriteHero.skin = skinOf(g.p.job)
        // gatilhos visuais a partir de eventos do jogo (sem mexer na lógica)
        val p = g.p
        if (prevHp < 0) prevHp = p.hp
        if (prevAtkSeq < 0) { prevAtkSeq = g.atkSeq; prevCastSeq = g.castSeq }
        if (g.atkSeq != prevAtkSeq) {
            prevAtkSeq = g.atkSeq
            spriteHero.attack(g.atkScale, g.atkDirX, g.atkDirY, g.atkDist)
        }
        if (g.castSeq != prevCastSeq) {
            prevCastSeq = g.castSeq
            spriteHero.skill(g.castId, g.castDirX, g.castDirY)
        }
        if (p.hp < prevHp && !g.dead) spriteHero.hurt()
        prevHp = p.hp
        // velocidade de deslocamento suavizada: o ciclo de passos acompanha (pés não patinam)
        val rawSpeed = if (g.moving) kotlin.math.sqrt(vx * vx + vy * vy) else 0f
        heroSpeed += (rawSpeed - heroSpeed) * min(1f, dt * 10f)
        spriteHero.update(dt, g.moving, heroSpeed, g.px, g.py, Style.CHAR_SCALE)
        spriteHero.draw(c, g.px, g.py, Style.CHAR_SCALE, g.time)
    }

    // ---------- modo de teste de animação (cicla as 8 direções e todas as animações) ----------
    private val testNames = arrayOf("Parado", "Andar", "Ataque", "Tomar dano", "Conjurar")
    /** Depois das animações básicas, as habilidades da classe (cada uma com animação própria). */
    private fun testSkills() = when (g.p.job) {
        Job.GUERREIRO -> intArrayOf(1, 2, 3)
        Job.MAGO -> intArrayOf(4, 5, 6)
        Job.CACADOR -> intArrayOf(7, 8, 9)
        else -> IntArray(0)
    }
    private fun testCount() = testNames.size + testSkills().size
    private fun testName(i: Int) = if (i < testNames.size) testNames[i] else SKILLS[testSkills()[i - testNames.size]].nome
    private var testMode = false
    private var testIdx = 0
    private var testDir = 0
    private var testDirT = 0f
    private var testTrig = 0f
    private val testSprite = SpriteHero(res, art)
    private val actSair: () -> Unit = { g.onSwitchChar?.invoke() }
    private val actTest: () -> Unit = { testMode = !testMode; testIdx = 0; testDir = 0; testDirT = 0f }
    private val actTestNext: () -> Unit = { testIdx = (testIdx + 1) % testCount(); testTrig = 0f }
    private val actTestPrev: () -> Unit = { testIdx = (testIdx + testCount() - 1) % testCount(); testTrig = 0f }
    private val actTestDir: () -> Unit = { testDir = (testDir + 1) % 8; testDirT = 0f }
    private var testSlow = false
    private val actTestSlow: () -> Unit = { testSlow = !testSlow }
    /** Escala de tempo do jogo inteiro (combate lento para gravar). Lida pelo GameView. */
    var timeScale = 1f
        private set
    private val actSlowCombat: () -> Unit = {
        timeScale = if (timeScale < 1f) 1f else 0.25f
        testMode = false
    }
    private val actSlowOff: () -> Unit = { timeScale = 1f }

    private fun drawTest(c: Canvas, dt: Float) {
        fill.color = col(0xAA000000)
        c.drawRect(0f, 0f, vw, VH, fill)
        addBtn(0f, 0f, vw, VH, actNone)
        val w = 600f
        val h = 560f
        val l = safeL + (vw - safeL - safeR - w) / 2
        val t = (VH - h) / 2
        if (testIdx >= testCount()) testIdx = 0
        window(c, l, t, w, h, "Teste de animação  ·  ${testIdx + 1}/${testCount()}" + if (testSlow) "  ·  0,25x" else "")
        addBtn(l, t, l + w, t + h, actTestNext)
        val tdt = if (testSlow) dt * 0.25f else dt
        // direção gira sozinha (mais devagar no andar, para ver vários ciclos)
        testDirT += tdt
        if (testDirT > (if (testIdx == 1) 2.4f else 1.2f)) { testDirT = 0f; testDir = (testDir + 1) % 8 }
        testSprite.dir = testDir
        testSprite.skin = skinOf(g.p.job)
        // animações de um disparo se repetem
        testTrig -= tdt
        if (testTrig <= 0f) {
            when (testIdx) {
                2 -> { testSprite.attack(); testTrig = 0.9f }
                3 -> { testSprite.hurt(); testTrig = 0.8f }
                4 -> { testSprite.cast(); testTrig = 1.2f }
                in 0..1 -> testTrig = 1f
                else -> {
                    val id = testSkills()[testIdx - testNames.size]
                    testSprite.skill(id, if (SpriteHero.FX[testDir] < 0f) -1f else 1f, 0f)
                    testTrig = (SpriteClips.SKILL[id]?.dur ?: 1f) + 0.5f
                }
            }
        }
        val cx = l + w / 2
        val gy = t + 430f
        art.oval(c, cx - 160f, gy - 32f, cx + 160f, gy + 32f, Style.GRASS)
        art.oval(c, cx - 130f, gy - 24f, cx + 130f, gy + 24f, art.mix(Style.GRASS, Color.WHITE, 0.12f))
        // andar contínuo na velocidade real do herói (a escala do teste não muda a cadência)
        testSprite.update(tdt, testIdx == 1, g.p.speed, cx, gy, 4.2f)
        testSprite.draw(c, cx, gy, 4.2f, g.time)
        label(c, "${testName(testIdx)}  —  ${SpriteHero.NAMES[testDir]}", cx, t + h - 78f, 19f, INK, Paint.Align.CENTER)
        label(c, "Toque no quadro: próxima animação  ·  a direção gira sozinha", cx, t + h - 58f, 13f, INK2, Paint.Align.CENTER)
        button(c, l + 16f, t + h - 50f, 60f, 40f, "◀", true, Style.HUD_BTN, 18f, actTestPrev)
        button(c, l + 82f, t + h - 50f, 104f, 40f, "Direção", true, Style.HUD_BTN, 15f, actTestDir)
        button(c, cx - 50f, t + h - 46f, 100f, 36f, "Fechar", true, BTN_RED, 15f, actTest)
        button(c, l + w - 196f, t + h - 50f, 104f, 40f, if (testSlow) "1x" else "0,25x", true,
            if (testSlow) BTN_GOLD else Style.HUD_BTN, 15f, actTestSlow)
        // combate lento: fecha o teste e deixa o JOGO inteiro em 0,25x para gravar o combate
        button(c, l + w - 196f, t + 44f, 180f, 36f, if (timeScale < 1f) "Combate 1x" else "Combate lento 0,25x", true,
            if (timeScale < 1f) BTN_GOLD else Style.HUD_BTN, 14f, actSlowCombat)
        button(c, l + w - 76f, t + h - 50f, 60f, 40f, "▶", true, Style.HUD_BTN, 18f, actTestNext)
    }

    private fun drawNpc(c: Canvas, n: Npc, idx: Int, fx: Boolean) {
        val ph = n.x * 0.013f
        val bob = if (fx) sin(g.time * 2f + ph) * 1.5f else 0f
        val blink = fx && ((g.time + ph * 3f) % 3.4f) < 0.13f
        if (fx) {
            art.beginOutline()
            for (k in 0 until 4) {
                c.save(); c.translate(OUT_DX[k], OUT_DY[k])
                art.hero(c, n.x, n.y, 1f, n.cor, n.cabelo, n.hat, -1, 0f, false, 0f, Style.NPC_SCALE, bob, blink)
                c.restore()
            }
            art.endOutline()
        }
        art.hero(c, n.x, n.y, 1f, n.cor, n.cabelo, n.hat, -1, 0f, false, 0f, Style.NPC_SCALE, bob, blink)
        outlined(c, n.nome, n.x, n.y - 80f, ws(17f), col(0xFFFFF59D))
        val f = if (fx) sin(g.time * 3f + idx) * 3f else sin(g.time * 3f) * 3f
        if (fx) art.circle(c, n.x, n.y - 104f + f, 9f, col(0x55FFC107))
        outlined(c, "!", n.x, n.y - 96f + f, ws(24f), col(0xFFFFC107))
    }

    private fun drawMob(c: Canvas, m: Mob, fx: Boolean) {
        if (fx) {
            art.beginOutline()
            for (k in 0 until 4) {
                c.save(); c.translate(OUT_DX[k], OUT_DY[k])
                art.mob(c, m)
                c.restore()
            }
            art.endOutline()
        }
        art.mob(c, m)
    }

    /** Barra de HP e nome dos monstros numa camada acima do cenário (árvores não encobrem). */
    private fun drawMobOverlay(c: Canvas) {
        for (i in g.mobs.indices) mobOverlay(c, g.mobs[i])
    }

    private fun mobOverlay(c: Canvas, m: Mob) {
        if (m.dead) return
        val d = m.d
        val top = m.y - d.size * 2.6f - 10f
        if (m === g.target || d.boss || m.hp < d.hp) {
            val bw = max(40f, d.size * 2f)
            rect.set(m.x - bw / 2 - 1, top - 1, m.x + bw / 2 + 1, top + 6)
            fill.color = col(0xCC000000); c.drawRect(rect, fill)
            fill.color = if (d.boss) col(0xFFE040FB) else col(0xFFFF5252)
            c.drawRect(m.x - bw / 2, top, m.x - bw / 2 + bw * (m.hp.toFloat() / d.hp).coerceIn(0f, 1f), top + 5, fill)
        }
        if (m === g.target || d.boss) {
            // nome abaixo do monstro para não cobrir o herói nem os números de dano
            outlined(c, d.label, m.x, m.y + ws(if (d.boss) 24f else 21f), ws(if (d.boss) 20f else 17f),
                if (d.boss) col(0xFFEA80FC) else if (d.lv > g.p.lv + 8) col(0xFFFF8A80) else Color.WHITE)
        }
    }

    private fun drawDrops(c: Canvas) {
        for (d in g.drops) {
            if (!d.on) continue
            val sh = (1f - d.z / 120f).coerceIn(0.3f, 1f)
            art.oval(c, d.x - 6f * sh, d.y - 2f * sh, d.x + 6f * sh, d.y + 2f * sh, col(0x50000000))
            val y = d.y - d.z - 7f
            when (d.kind) {
                0 -> {
                    art.circle(c, d.x, y, 6.5f, col(0xFFB8860B))
                    art.circle(c, d.x, y, 5f, C_GOLD)
                    art.circle(c, d.x - 1.5f, y - 1.5f, 1.8f, col(0xCCFFFFFF))
                }
                1 -> {
                    art.circle(c, d.x, y + 1f, 6f, art.darker(d.cor, 0.8f))
                    art.circle(c, d.x, y, 5f, d.cor)
                    art.line(c, d.x - 3f, y - 5f, d.x + 3f, y - 5f, 2f, col(0xFF6D4C41))
                }
                3 -> {
                    val tier = d.amount.toInt()
                    if (tier >= 3) {
                        // feixe de luz: mais alto e mais forte no tier 4
                        val pulse = sin(g.time * 6f) * 0.5f + 0.5f
                        val hgt = if (tier >= 4) 150f else 95f
                        val wd = if (tier >= 4) 9f else 6f
                        for (k in 0..2) {
                            val ww = wd * (1.6f - k * 0.5f)
                            fill.color = art.alpha(d.cor, ((if (tier >= 4) 60 else 40) + k * 30 + pulse * 30).toInt())
                            rect.set(d.x - ww, y - hgt, d.x + ww, y + 2f)
                            c.drawRoundRect(rect, ww, ww, fill)
                        }
                    }
                    fill.color = art.alpha(d.cor, 90)
                    c.drawCircle(d.x, y, 9f + (if (tier >= 3) 3f else 0f), fill)
                    art.rr(c, d.x - 5.5f, y - 5.5f, d.x + 5.5f, y + 5.5f, 1.5f, col(0xFF1C232C))
                    stroke.strokeWidth = 1.6f; stroke.color = d.cor
                    rect.set(d.x - 5.5f, y - 5.5f, d.x + 5.5f, y + 5.5f); c.drawRoundRect(rect, 1.5f, 1.5f, stroke)
                    c.drawLine(d.x - 2.5f, y + 2.5f, d.x + 2.5f, y - 2.5f, stroke)
                    if (d.t < 1.2f) outlined(c, d.label, d.x, y - 14f, ws(11f), d.cor)
                }
                else -> {
                    art.rr(c, d.x - 5f, y - 7f, d.x + 5f, y + 7f, 1.5f, col(0xFFFFF8E1))
                    stroke.strokeWidth = 1.5f; stroke.color = col(0xFFB8860B)
                    rect.set(d.x - 5f, y - 7f, d.x + 5f, y + 7f); c.drawRoundRect(rect, 1.5f, 1.5f, stroke)
                }
            }
            // brilho cintilante
            val tw = (sin(g.time * 9f + d.x) * 0.5f + 0.5f)
            val s = 3f + tw * 4f
            stroke.strokeWidth = 1.4f; stroke.color = art.alpha(Color.WHITE, (90 + tw * 160).toInt())
            c.drawLine(d.x + 5f - s, y - 6f, d.x + 5f + s, y - 6f, stroke)
            c.drawLine(d.x + 5f, y - 6f - s, d.x + 5f, y - 6f + s, stroke)
        }
    }

    private fun drawParticles(c: Canvas) {
        val ps = g.parts
        for (i in 0 until ps.cap) {
            if (ps.life[i] <= 0f) continue
            val a = (ps.life[i] / ps.max[i]).coerceIn(0f, 1f)
            fill.color = art.alpha(ps.cor[i], (a * 255).toInt())
            c.drawCircle(ps.x[i], ps.y[i], ps.size[i] * (0.4f + 0.6f * a), fill)
        }
    }

    private fun drawFx(c: Canvas) {
        for (i in g.fx.indices) {
            val f = g.fx[i]
            val k = 1f - f.life / f.max
            val a = ((1f - k) * 255).toInt()
            when (f.kind) {
                FX_RING -> {
                    stroke.strokeWidth = 5f * (1 - k) + 1f
                    stroke.color = art.alpha(f.cor, a)
                    val rr = f.r * (0.3f + 0.7f * k)
                    rect.set(f.x - rr, f.y - rr * 0.45f, f.x + rr, f.y + rr * 0.45f)
                    c.drawOval(rect, stroke)
                    fill.color = art.alpha(f.cor, a / 4)
                    c.drawOval(rect, fill)
                }
                FX_SHOT -> {
                    val x = f.x + (f.x2 - f.x) * k
                    val y = f.y + (f.y2 - f.y) * k
                    if (f.r > 0f) {
                        art.circle(c, x, y, 9f, art.alpha(f.cor, 140))
                        art.circle(c, x, y, 5f, col(0xFFFFF59D))
                    } else {
                        val dx = f.x2 - f.x
                        val dy = f.y2 - f.y
                        val len = max(1f, kotlin.math.sqrt(dx * dx + dy * dy))
                        art.line(c, x - dx / len * 16f, y - dy / len * 16f, x, y, 3f, f.cor)
                    }
                }
                FX_BOLT -> {
                    var x0 = f.x
                    var y0 = f.y - 260f
                    stroke.strokeWidth = 4f * (1 - k) + 1f
                    stroke.color = art.alpha(f.cor, a)
                    for (s in 1..6) {
                        val x1 = f.x + if (s == 6) 0f else ((s * 37 + f.x.toInt()) % 30 - 15).toFloat()
                        val y1 = f.y - 260f + s * 260f / 6f
                        c.drawLine(x0, y0, x1, y1, stroke)
                        x0 = x1; y0 = y1
                    }
                    art.circle(c, f.x, f.y, 14f * (1 - k), art.alpha(Color.WHITE, a))
                }
                FX_SLASH -> {
                    stroke.strokeWidth = 5f * (1 - k) + 1f
                    stroke.color = art.alpha(f.cor, a)
                    rect.set(f.x - f.r, f.y - f.r, f.x + f.r, f.y + f.r)
                    c.drawArc(rect, -120f + k * 180f, 110f, false, stroke)
                }
                FX_FLASH -> {
                    // explosão branca de impacto: núcleo que estoura e raios curtos
                    val e = Ease.apply(Ease.OUT, k)
                    val rr = f.r * (0.35f + 0.65f * e)
                    art.circle(c, f.x, f.y, rr, art.alpha(Color.WHITE, (a * 0.55f).toInt()))
                    art.circle(c, f.x, f.y, rr * 0.55f, art.alpha(col(0xFFFFFDE7), a))
                    stroke.strokeWidth = 3f * (1f - k) + 1f
                    stroke.color = art.alpha(Color.WHITE, a)
                    for (s in 0 until 8) {
                        val ang = s * 0.785f + 0.3f
                        val r0 = rr * 0.6f
                        val r1 = rr * (1.1f + 0.3f * (s % 2))
                        c.drawLine(f.x + cos(ang) * r0, f.y + sin(ang) * r0 * 0.8f, f.x + cos(ang) * r1, f.y + sin(ang) * r1 * 0.8f, stroke)
                    }
                }
                FX_FIREBOLT -> {
                    // bola de fogo com rastro: posição acelera um pouco no fim
                    val e = k * k * 0.3f + k * 0.7f
                    val x = f.x + (f.x2 - f.x) * e
                    val y = f.y + (f.y2 - f.y) * e
                    val dx = f.x2 - f.x
                    val dy = f.y2 - f.y
                    val len = max(1f, kotlin.math.sqrt(dx * dx + dy * dy))
                    for (s in 5 downTo 1) {
                        val bx = x - dx / len * s * 9f
                        val by = y - dy / len * s * 9f + sin(g.time * 30f + s) * 2f
                        art.circle(c, bx, by, f.r * (1f - s * 0.14f), art.alpha(if (s > 2) col(0xFFE64A19) else f.cor, 150 - s * 22))
                    }
                    art.circle(c, x, y, f.r * 1.3f, art.alpha(f.cor, 110))
                    art.circle(c, x, y, f.r, col(0xFFFF9800))
                    art.circle(c, x, y, f.r * 0.55f, col(0xFFFFF59D))
                }
                FX_QUAKE -> {
                    // rachaduras que crescem do centro e somem; anel de poeira
                    val grow = min(1f, k * 4f)
                    stroke.strokeWidth = 3.5f * (1f - k) + 1.5f
                    stroke.color = art.alpha(f.cor, a)
                    for (s in 0 until 9) {
                        var ang = s * 0.698f + (f.x.toInt() % 7) * 0.1f
                        var x0 = f.x
                        var y0 = f.y
                        val seg = f.r * grow / 3f
                        for (j in 0 until 3) {
                            ang += (((s * 7 + j * 13) % 9) - 4) * 0.09f
                            val x1 = x0 + cos(ang) * seg
                            val y1 = y0 + sin(ang) * seg * 0.45f
                            c.drawLine(x0, y0, x1, y1, stroke)
                            x0 = x1; y0 = y1
                        }
                    }
                    val rr = f.r * (0.4f + 0.8f * k)
                    fill.color = art.alpha(col(0xFFBCAAA4), (a * 0.35f).toInt())
                    rect.set(f.x - rr, f.y - rr * 0.4f, f.x + rr, f.y + rr * 0.4f)
                    c.drawOval(rect, fill)
                }
                FX_ICE -> {
                    // espinhos de gelo brotam rápido em anel e afundam no fim
                    val h = if (k < 0.15f) k / 0.15f else if (k > 0.7f) (1f - k) / 0.3f else 1f
                    for (s in 0 until 12) {
                        val ang = s * 0.5236f + 0.2f
                        val rr = f.r * (0.55f + 0.35f * ((s * 37) % 10) / 10f)
                        val bx = f.x + cos(ang) * rr
                        val by = f.y + sin(ang) * rr * 0.45f
                        val sh = (26f + (s % 3) * 10f) * h
                        val w = 7f + (s % 2) * 3f
                        art.tri(c, bx - w, by, bx + w, by, bx + (s % 3 - 1) * 3f, by - sh, art.alpha(f.cor, min(255, a + 60)))
                        art.tri(c, bx - w * 0.2f, by, bx + w, by, bx + (s % 3 - 1) * 3f, by - sh, art.alpha(Color.WHITE, a / 2))
                    }
                }
                FX_AURA -> {
                    // chamas em volta do herói (acompanham a posição atual dele)
                    val ax = g.px
                    val ay = g.py
                    for (s in 0 until 10) {
                        val ang = s * 0.628f + g.time * 2f
                        val ox = cos(ang) * f.r
                        val front = sin(ang) > 0f
                        val fl = 30f + 14f * sin(g.time * 18f + s * 1.7f)
                        val cor = if (s % 2 == 0) f.cor else col(0xFFFFCA28)
                        val alpha = (a * (if (front) 0.8f else 0.45f)).toInt()
                        art.tri(c, ax + ox - 7f, ay - 4f, ax + ox + 7f, ay - 4f, ax + ox + sin(g.time * 9f + s) * 4f, ay - 4f - fl * (1f - k * 0.5f),
                            art.alpha(cor, alpha))
                    }
                    fill.color = art.alpha(f.cor, a / 5)
                    rect.set(ax - f.r * 1.4f, ay - 80f, ax + f.r * 1.4f, ay + 6f)
                    c.drawOval(rect, fill)
                }
                FX_SKYBOLT -> {
                    // raio grosso do céu com galhos, núcleo branco
                    val top = f.y - 420f
                    for (pass in 0..1) {
                        stroke.strokeWidth = if (pass == 0) 9f * (1 - k) + 2f else 3f * (1 - k) + 1f
                        stroke.color = if (pass == 0) art.alpha(f.cor, a) else art.alpha(Color.WHITE, a)
                        var x0 = f.x + 30f
                        var y0 = top
                        for (s in 1..8) {
                            val x1 = f.x + if (s == 8) 0f else ((s * 53 + f.x.toInt() * 3) % 50 - 25).toFloat()
                            val y1 = top + s * 420f / 8f
                            c.drawLine(x0, y0, x1, y1, stroke)
                            if (pass == 0 && s % 3 == 1) c.drawLine(x1, y1, x1 + 22f * (if (s % 2 == 0) 1f else -1f), y1 + 30f, stroke)
                            x0 = x1; y0 = y1
                        }
                    }
                    fill.color = art.alpha(f.cor, a / 3)
                    rect.set(f.x - 40f, f.y - 14f, f.x + 40f, f.y + 14f)
                    c.drawOval(rect, fill)
                }
                FX_RAIN -> {
                    for (s in 0 until 14) {
                        val ang = s * 2.39f
                        val rad = f.r * ((s * 53) % 100) / 100f
                        val ax = f.x + cos(ang) * rad
                        val ay = f.y + sin(ang) * rad * 0.5f
                        val fall = ((k * 2f + s * 0.13f) % 1f)
                        art.line(c, ax, ay - 120f + fall * 120f - 18f, ax, ay - 120f + fall * 120f, 2.5f, art.alpha(f.cor, a))
                    }
                    stroke.strokeWidth = 2f; stroke.color = art.alpha(f.cor, a / 2)
                    rect.set(f.x - f.r, f.y - f.r * 0.45f, f.x + f.r, f.y + f.r * 0.45f)
                    c.drawOval(rect, stroke)
                }
            }
        }
    }

    // =====================================================================
    // HUD
    // =====================================================================
    private fun refreshHudStrings() {
        val p = g.p
        if (p.lv != cLv) { cLv = p.lv; sLv = "Base Lv ${p.lv}" }
        if (p.jlv != cJlv || p.job != cJob) { cJlv = p.jlv; cJob = p.job; sJlv = "${p.job.nome} · Job Lv ${p.jlv}" }
        if (p.zeny != cZeny) { cZeny = p.zeny; sZeny = "Zeny ${fmt(p.zeny)}" }
        if (p.hp != cHp || p.maxHp != cMhp) { cHp = p.hp; cMhp = p.maxHp; sHp = "HP ${p.hp} / ${p.maxHp}" }
        if (p.sp != cSp || p.maxSp != cMsp) { cSp = p.sp; cMsp = p.maxSp; sSp = "SP ${p.sp} / ${p.maxSp}" }
        val be = if (p.lv >= 99) 1000 else (p.exp * 1000 / expNeed(p.lv)).toInt()
        if (be != cBe) { cBe = be; sBe = "${be / 10}.${be % 10}%" }
        val je = if (p.jlv >= p.job.jobMax) 1000 else (p.jexp * 1000 / jobNeed(p.jlv, p.job)).toInt()
        if (je != cJe) { cJe = je; sJe = "${je / 10}.${je % 10}%" }
        if (g.map.id != cMap) { cMap = g.map.id; sTitle = "${g.map.nome}  ·  ${g.map.faixa}"; cBoss = -2 }
        if (g.map.boss >= 0) {
            var alive = false
            for (i in g.mobs.indices) if (g.mobs[i].d.boss) { alive = true; break }
            val key = if (alive) -1 else g.bossT[g.map.id].toInt().coerceAtLeast(0)
            if (key != cBoss) {
                cBoss = key
                sBoss = if (alive) "⚠ ${MONSTERS[g.map.boss].nome} está no mapa!"
                else "Chefe ${MONSTERS[g.map.boss].nome} em ${key / 60}:${(key % 60).toString().padStart(2, '0')}"
            }
        }
        val hpPots = p.potions[0] + p.potions[1] + p.potions[2]
        if (hpPots != cHpPot) { cHpPot = hpPots; sHpPot = "x$hpPots" }
        if (p.potions[3] != cSpPot) { cSpPot = p.potions[3]; sSpPot = "x${p.potions[3]}" }
    }

    private fun drawHud(c: Canvas, dt: Float) {
        val p = g.p
        val fx = p.fx
        refreshHudStrings()
        val L = safeL + 10f
        val R = vw - safeR - 10f
        val T = safeT + 8f
        val B = VH - safeB - 8f

        // ---------- janela de status ----------
        window(c, L, T, 340f, 134f, null)
        // nome do personagem (encolhe se for comprido); a classe fica na linha do Job Lv
        var ns = 19f
        text.textSize = ns
        while (text.measureText(p.nome) > 170f && ns > 12f) { ns -= 1f; text.textSize = ns }
        label(c, p.nome, L + 12f, T + 26f, ns, Style.HUD_INK)
        label(c, sLv, L + 190f, T + 26f, 16f, Style.HUD_INK)
        label(c, sJlv, L + 12f, T + 48f, 15f, Style.HUD_INK2)
        label(c, sZeny, L + 190f, T + 48f, 15f, col(0xFF8D6E00))
        bar(c, L + 12f, T + 56f, 316f, 16f, p.hp.toFloat() / p.maxHp, if (p.hp < p.maxHp / 4) col(0xFFE53935) else col(0xFF43A047))
        label(c, sHp, L + 170f, T + 69f, 13f, Color.WHITE, Paint.Align.CENTER)
        bar(c, L + 12f, T + 76f, 316f, 14f, p.sp.toFloat() / p.maxSp, col(0xFF1E88E5))
        label(c, sSp, L + 170f, T + 88f, 12f, Color.WHITE, Paint.Align.CENTER)
        // XP com animação suave
        val be = cBe / 1000f
        val je = cJe / 1000f
        if (seenLv < 0) { seenLv = p.lv; seenJlv = p.jlv; dispBe = be; dispJe = je }
        if (p.lv != seenLv) { if (p.lv > seenLv && fx) wrapBe = true; seenLv = p.lv }
        if (p.jlv != seenJlv) { if (p.jlv > seenJlv && fx) wrapJe = true; seenJlv = p.jlv }
        if (!fx) { dispBe = be; dispJe = je; wrapBe = false; wrapJe = false } else {
            val k = min(1f, dt * 5f)
            if (wrapBe) { dispBe += (1.02f - dispBe) * k; if (dispBe >= 1f) { dispBe = 0f; wrapBe = false } } else dispBe += (be - dispBe) * k
            if (wrapJe) { dispJe += (1.02f - dispJe) * k; if (dispJe >= 1f) { dispJe = 0f; wrapJe = false } } else dispJe += (je - dispJe) * k
        }
        label(c, "Base", L + 12f, T + 107f, 12f, Style.HUD_INK2)
        bar(c, L + 46f, T + 98f, 120f, 10f, dispBe, col(0xFFFFB300))
        label(c, "Job", L + 174f, T + 107f, 12f, Style.HUD_INK2)
        bar(c, L + 202f, T + 98f, 126f, 10f, dispJe, col(0xFF00ACC1))
        label(c, sBe, L + 106f, T + 124f, 12f, Style.HUD_INK2, Paint.Align.CENTER)
        label(c, sJe, L + 265f, T + 124f, 12f, Style.HUD_INK2, Paint.Align.CENTER)

        // ---------- botões do menu (topo direito) ----------
        val bw = 88f
        val gap = 6f
        val menuLeft = R - 4 * (bw + gap) + gap - (54f + gap) - (64f + gap)
        // Sair: salva e volta para a seleção de personagem
        val sairX = menuLeft - 64f - gap
        button(c, sairX, T, 64f, 42f, "Sair", true, BTN_RED, 15f, actSair)
        button(c, menuLeft, T, 64f, 42f, "Anim", true, if (testMode) BTN_GOLD else Style.HUD_BTN, 15f, actTest)
        if (timeScale < 1f) button(c, sairX - 92f, T, 86f, 42f, "0,25x ✕", true, BTN_GOLD, 15f, actSlowOff)
        button(c, menuLeft + 64f + gap, T, 54f, 42f, "FX", true, if (fx) BTN_GREEN else col(0xFF757575), 16f, actFx)
        val labels = MENU
        for (i in labels.indices) {
            val x = menuLeft + 64f + gap + 54f + gap + i * (bw + gap)
            button(c, x, T, bw, 42f, labels[i], i != 3 || g.map.id != 0, if (i == 3) BTN_GOLD else Style.HUD_BTN, 17f, actMenu[i])
            val badge = (i == 0 && p.statPts > 0) || (i == 1 && p.skillPts > 0 && hasSkillToRaise())
            if (badge) art.circle(c, x + bw - 6f, T + 4f, 7f, col(0xFFFF1744))
        }

        // ---------- título do mapa numa faixa própria (entre a janela e os botões) ----------
        val gapL = L + 350f
        val tcx = (gapL + sairX - 10f) / 2
        val maxW = sairX - 10f - gapL
        var ts = 20f
        text.textSize = ts
        var tw = text.measureText(sTitle)
        while (tw > maxW - 30f && ts > 13f) { ts -= 1f; text.textSize = ts; tw = text.measureText(sTitle) }
        val hasBoss = g.map.boss >= 0
        rect.set(tcx - tw / 2 - 16f, T, tcx + tw / 2 + 16f, T + (if (hasBoss) 52f else 34f))
        fill.color = col(0x8A1A2230); c.drawRoundRect(rect, 10f, 10f, fill)
        label(c, sTitle, tcx, T + 24f, ts, col(0xFFFFF59D), Paint.Align.CENTER)
        if (hasBoss) {
            text.textSize = 13f
            label(c, sBoss, tcx, T + 44f, 13f, if (cBoss == -1) col(0xFFEA80FC) else col(0xFFCFD8DC), Paint.Align.CENTER)
        }

        drawMinimap(c, R, T + 50f)

        // ---------- log ----------
        var ly = B - 128f
        for (i in g.log.indices.reversed()) {
            val ln = g.log[i]
            val a = if (ln.t < 10f) 255 else (255 - (ln.t - 10f) * 80).toInt()
            if (a <= 0) continue
            outlined(c, ln.s, L + 6f, ly, 15f, art.alpha(ln.cor, a), Paint.Align.LEFT)
            ly -= 21f
        }

        // ---------- AUTO ----------
        val ax = L + 58f
        val ay = B - 56f
        art.circle(c, ax, ay, 56f, col(0x88000000))
        art.circle(c, ax, ay, 50f, if (p.autoMode) col(0xFF2E7D32) else col(0xFF616161))
        if (p.autoMode) {
            stroke.strokeWidth = 4f; stroke.color = col(0xFFB9F6CA)
            rect.set(ax - 50f, ay - 50f, ax + 50f, ay + 50f)
            c.drawArc(rect, (g.time * 200f) % 360f, 90f, false, stroke)
        }
        label(c, "AUTO", ax, ay - 2f, 21f, Color.WHITE, Paint.Align.CENTER)
        label(c, if (p.autoMode) "LIGADO" else "DESLIG.", ax, ay + 20f, 12f, Color.WHITE, Paint.Align.CENTER)
        addBtn(ax - 56f, ay - 56f, ax + 56f, ay + 56f, actAuto)

        // ---------- poções (ícone desenhado + contador legível) ----------
        potButton(c, L + 152f, B - 44f, col(0xFFE53935), cHpPot, sHpPot, if (p.autoPot) "Poção · auto" else "Poção HP", actHp)
        potButton(c, L + 232f, B - 44f, col(0xFF1E88E5), cSpPot, sSpPot, "Mana", actSp)

        // ---------- barra de skills com fundo opaco ----------
        val ids = p.job.skills
        val sz = 84f
        val sg = 8f
        val n = 4
        val barW = n * sz + (n - 1) * sg + 16f
        rect.set(R - barW, B - sz - 16f, R, B)
        fill.color = Style.HUD_SLOT; c.drawRoundRect(rect, Style.HUD_RADIUS + 4f, Style.HUD_RADIUS + 4f, fill)
        stroke.strokeWidth = 1.5f; stroke.color = Style.HUD_BORDER; c.drawRoundRect(rect, Style.HUD_RADIUS + 4f, Style.HUD_RADIUS + 4f, stroke)
        addBtn(rect.left, rect.top, rect.right, rect.bottom, actNone)
        for (k in 0 until n) {
            val x = R - barW + 8f + k * (sz + sg)
            val y = B - sz - 8f
            rect.set(x, y, x + sz, y + sz)
            fill.color = col(0xFF3A4556); c.drawRoundRect(rect, 10f, 10f, fill)
            if (k >= ids.size) {
                label(c, "—", x + sz / 2, y + sz / 2 + 6f, 18f, col(0x88FFFFFF), Paint.Align.CENTER)
                continue
            }
            val id = ids[k]
            val s = SKILLS[id]
            val lv = p.skills[id]
            val spc = s.sp(max(1, lv))
            if (cSlot[k] != lv * 1000 + id || cSlotSp[k] != spc) {
                cSlot[k] = lv * 1000 + id; cSlotSp[k] = spc
                sSlot[k] = if (lv > 0) "Nv$lv · ${spc}SP" else "bloqueada"
            }
            art.circle(c, x + sz / 2, y + sz / 2 - 6f, 27f, if (lv > 0) s.cor else col(0xFF616161))
            art.circle(c, x + sz / 2 - 8f, y + sz / 2 - 16f, 8f, col(0x33FFFFFF))
            label(c, s.sigla, x + sz / 2, y + sz / 2 + 1f, 19f, Color.WHITE, Paint.Align.CENTER)
            label(c, sSlot[k], x + sz / 2, y + sz - 6f, 11f, Color.WHITE, Paint.Align.CENTER)
            if (lv > 0 && g.cd[id] > 0f) {
                fill.color = col(0x99000000)
                rect.set(x + sz / 2 - 27f, y + sz / 2 - 33f, x + sz / 2 + 27f, y + sz / 2 + 21f)
                c.drawArc(rect, -90f, 360f * (g.cd[id] / s.cd).coerceIn(0f, 1f), true, fill)
            }
            if (lv > 0 && p.sp < s.sp(lv)) {
                fill.color = col(0x660D47A1); rect.set(x, y, x + sz, y + sz); c.drawRoundRect(rect, 10f, 10f, fill)
            }
            if (lv > 0 && p.autoSkill[id]) art.circle(c, x + sz - 9f, y + 9f, 5.5f, col(0xFF69F0AE))
            if (s.kind == SK.BUFF && p.buff[id] > 0f) art.circle(c, x + 10f, y + 10f, 5.5f, col(0xFFFFF59D))
            if (lv > 0) addBtn(x, y, x + sz, y + sz, actSkill[k])
        }

        // ---------- alerta de HP baixo ----------
        if (p.hp in 1 until p.maxHp / 4) {
            val a = (60 + 50 * sin(g.time * 6f)).toInt()
            stroke.strokeWidth = 24f; stroke.color = art.alpha(col(0xFFD50000), a)
            c.drawRect(0f, 0f, vw, VH, stroke)
        }
    }

    private val MENU = arrayOf("Status", "Skills", "Itens", "Vila")

    private fun hasSkillToRaise(): Boolean {
        val ids = g.p.job.skills
        for (id in ids) if (g.p.skills[id] < SKILLS[id].maxLv) return true
        return false
    }

    /** Botão de poção: frasco desenhado, contador em pílula e legenda. */
    private fun potButton(c: Canvas, x: Float, y: Float, cor: Int, n: Int, count: String, caption: String, act: () -> Unit) {
        val r = 32f
        val has = n > 0
        art.circle(c, x, y, r + 4f, col(0x88000000))
        art.circle(c, x, y, r, if (has) col(0xFF37474F) else col(0xFF3A3A3A))
        // frasco
        val liquid = if (has) cor else col(0xFF757575)
        art.circle(c, x, y + 5f, 13f, col(0xCCECEFF1))
        art.circle(c, x, y + 5f, 11f, liquid)
        art.circle(c, x - 4f, y + 1f, 3.5f, col(0x66FFFFFF))
        art.rr(c, x - 4.5f, y - 15f, x + 4.5f, y - 6f, 1.5f, col(0xCCECEFF1))
        art.rr(c, x - 5.5f, y - 19f, x + 5.5f, y - 14f, 2f, col(0xFF8D6E63))
        // contador
        text.textSize = 13f
        val tw = text.measureText(count)
        rect.set(x - tw / 2 - 7f, y + r - 10f, x + tw / 2 + 7f, y + r + 8f)
        fill.color = if (has) col(0xFF263238) else col(0xFFB71C1C); c.drawRoundRect(rect, 9f, 9f, fill)
        stroke.strokeWidth = 1f; stroke.color = col(0x88FFFFFF); c.drawRoundRect(rect, 9f, 9f, stroke)
        label(c, count, x, y + r + 4f, 13f, Color.WHITE, Paint.Align.CENTER)
        outlined(c, caption, x, y - r - 8f, 12f, Color.WHITE, Paint.Align.CENTER)
        addBtn(x - r - 6f, y - r - 6f, x + r + 6f, y + r + 10f, act)
    }

    private fun drawMinimap(c: Canvas, R: Float, y0: Float) {
        val m = g.map
        val mw = 150f
        val mh = mw * m.h / m.w
        val x0 = R - mw
        rect.set(x0, y0, x0 + mw, y0 + mh)
        fill.color = col(0xAA000000); c.drawRoundRect(rect, 5f, 5f, fill)
        stroke.strokeWidth = 1.5f; stroke.color = Style.HUD_BORDER; c.drawRoundRect(rect, 5f, 5f, stroke)
        fill.color = art.alpha(m.g1, 120); c.drawRect(x0 + 3, y0 + 3, x0 + mw - 3, y0 + mh - 3, fill)
        val k = (mw - 6f) / m.w
        for (i in g.portals.indices) { val p = g.portals[i]; art.circle(c, x0 + 3 + p.x * k, y0 + 3 + p.y * k, 3.5f, col(0xFF82B1FF)) }
        for (i in g.npcs.indices) { val n = g.npcs[i]; art.circle(c, x0 + 3 + n.x * k, y0 + 3 + n.y * k, 2.5f, col(0xFFFFEB3B)) }
        for (i in g.mobs.indices) {
            val mo = g.mobs[i]
            if (mo.d.boss) art.circle(c, x0 + 3 + mo.x * k, y0 + 3 + mo.y * k, 4.5f, col(0xFFE040FB))
            else art.circle(c, x0 + 3 + mo.x * k, y0 + 3 + mo.y * k, 2f, if (mo.aggro) col(0xFFFF5252) else col(0xFFFFAB91))
        }
        art.circle(c, x0 + 3 + g.px * k, y0 + 3 + g.py * k, 3.5f, Color.WHITE)
        val hw = vw / 2 / zoom
        val hh = VH / 2 / zoom
        stroke.strokeWidth = 1f; stroke.color = col(0x88FFFFFF)
        c.drawRect(x0 + 3 + max(0f, camX - hw) * k, y0 + 3 + max(0f, camY - hh) * k,
            x0 + 3 + min(m.w, camX + hw) * k, y0 + 3 + min(m.h, camY + hh) * k, stroke)
    }

    // =====================================================================
    // JANELAS
    // =====================================================================
    private val BTN_GREEN = col(0xFF3F8B4A)
    private val BTN_RED = col(0xFFB5443A)
    private val BTN_GOLD = col(0xFFB08A2E)
    private val INK = Style.HUD_INK
    private val INK2 = Style.HUD_INK2

    private fun drawPanel(c: Canvas) {
        fill.color = col(0x88000000)
        c.drawRect(0f, 0f, vw, VH, fill)
        addBtn(0f, 0f, vw, VH, actBackdrop)
        val closable = g.panel != Panel.MORTE
        val availW = vw - safeL - safeR - 40f
        val pw = min(availW, 920f)
        val ph = when (g.panel) {
            Panel.MORTE -> 260f
            Panel.OFFLINE -> min(620f, VH - safeT - safeB - 40f)
            else -> min(620f, VH - safeT - safeB - 40f)
        }
        val pl = safeL + (vw - safeL - safeR - pw) / 2
        val pt = safeT + (VH - safeT - safeB - ph) / 2
        addBtn(pl, pt, pl + pw, pt + ph, actNone)
        val title = when (g.panel) {
            Panel.STATUS -> "Status — ${g.p.job.nome}"
            Panel.SKILLS -> "Habilidades — ${g.p.job.nome}"
            Panel.ITENS -> "Itens e Equipamentos"
            Panel.LOJA -> "Mercador"
            Panel.FERREIRO -> "Ferreiro"
            Panel.MESTRE -> "Mestre de Classes"
            Panel.TELEPORTE -> "Teleportadora"
            Panel.OFFLINE -> "Enquanto você estava fora..."
            Panel.MORTE -> "Você foi derrotado"
            Panel.NONE -> ""
        }
        window(c, pl, pt, pw, ph, title)
        if (closable) button(c, pl + pw - 40f, pt + 5f, 32f, 26f, "X", true, BTN_RED, 14f) { g.panel = Panel.NONE }
        when (g.panel) {
            Panel.STATUS -> panelStatus(c, pl, pt, pw, ph)
            Panel.SKILLS -> panelSkills(c, pl, pt, pw)
            Panel.ITENS -> panelItens(c, pl, pt, pw, ph)
            Panel.LOJA -> panelLoja(c, pl, pt, pw)
            Panel.FERREIRO -> panelFerreiro(c, pl, pt, pw)
            Panel.MESTRE -> panelMestre(c, pl, pt, pw, ph)
            Panel.TELEPORTE -> panelTeleporte(c, pl, pt, pw)
            Panel.OFFLINE -> panelOffline(c, pl, pt, pw, ph)
            Panel.MORTE -> panelMorte(c, pl, pt, pw)
            Panel.NONE -> {}
        }
    }

    private fun panelStatus(c: Canvas, pl: Float, pt: Float, pw: Float, ph: Float) {
        val p = g.p
        val x = pl + 26f
        label(c, "Pontos de atributo: ${p.statPts}", x, pt + 70f, 19f, if (p.statPts > 0) col(0xFFC62828) else INK)
        for (i in 0 until 6) {
            val y = pt + 86f + i * 64f
            label(c, STAT_NOMES[i], x, y + 26f, 22f, INK)
            label(c, "${p.stats[i]}", x + 70f, y + 26f, 22f, INK)
            val extra = p.tot[i] - p.stats[i]
            if (extra > 0) label(c, "+$extra", x + 110f, y + 26f, 17f, col(0xFF2E7D32))
            label(c, STAT_DESC[i], x, y + 47f, 13f, INK2)
            val cost = statCost(p.stats[i])
            label(c, "custo $cost", x + 160f, y + 26f, 13f, INK2)
            val can = p.stats[i] < 99 && p.statPts >= cost
            button(c, x + 240f, y + 6f, 56f, 40f, "+1", can, BTN_GREEN, 17f) { p.addStat(i, 1) }
            button(c, x + 302f, y + 6f, 56f, 40f, "+5", can, BTN_GREEN, 17f) { p.addStat(i, 5) }
        }
        val rx = pl + pw / 2 + 40f
        val lines = listOf(
            "ATK" to "${p.atk.toInt()}",
            "MATK" to "${p.matk.toInt()}",
            "DEF" to "${p.def.toInt()}",
            "Precisão" to "${p.hit.toInt()}",
            "Esquiva" to "${p.flee.toInt()}",
            "Crítico" to "%.1f%%".format(p.crit * 100),
            "Ataques/s" to "%.2f".format(1f / p.delay),
            "HP / SP" to "${p.maxHp} / ${p.maxSp}",
            "EXP base" to if (p.lv >= 99) "MÁX" else "${fmt(p.exp)} / ${fmt(expNeed(p.lv))}",
            "EXP classe" to if (p.jlv >= p.job.jobMax) "MÁX" else "${fmt(p.jexp)} / ${fmt(jobNeed(p.jlv, p.job))}",
            "Abates" to fmt(p.kills),
            "Caça offline" to MAPS[p.farmMap].nome,
        )
        lines.forEachIndexed { i, (k, v) ->
            val y = pt + 70f + i * 30f
            label(c, k, rx, y, 16f, INK2)
            label(c, v, rx + 140f, y, 17f, INK)
        }
        val by = pt + ph - 56f
        val by2 = by - 50f
        val by3 = by2 - 50f
        button(c, rx, by3, 130f, 42f, "Som: " + if (p.som) "ON" else "OFF", true, if (p.som) BTN_GREEN else Style.HUD_BTN, 15f) {
            p.som = !p.som
        }
        button(c, rx + 138f, by3, 130f, 42f, "Música: " + if (p.musica) "ON" else "OFF", true, if (p.musica) BTN_GREEN else Style.HUD_BTN, 15f) {
            p.musica = !p.musica
            g.onMusicSetting?.invoke()
        }
        button(c, rx, by2, 130f, 42f, "Zoom ${"%.1f".format(p.zoom)}x", true, Style.HUD_BTN, 15f) {
            val zs = Style.ZOOMS
            var i = 0
            while (i < zs.size && zs[i] <= p.zoom + 0.01f) i++
            p.zoom = if (i >= zs.size) zs[0] else zs[i]
        }
        button(c, rx + 138f, by2, 130f, 42f, "Vibração: " + if (p.vibra) "ON" else "OFF", true, if (p.vibra) BTN_GREEN else Style.HUD_BTN, 14f) {
            p.vibra = !p.vibra
        }
        button(c, rx + 276f, by2, 120f, 42f, "Efeitos: " + if (p.fx) "ON" else "OFF", true, if (p.fx) BTN_GREEN else Style.HUD_BTN, 14f, actFx)
        button(c, rx, by, 180f, 42f, "Auto-poção: " + if (p.autoPot) "ON" else "OFF", true, if (p.autoPot) BTN_GREEN else Style.HUD_BTN, 15f) {
            p.autoPot = !p.autoPot
        }
        button(c, x, by, 220f, 42f, "Trocar personagem", true, BTN_GOLD, 15f) {
            g.panel = Panel.NONE
            g.onSwitchChar?.invoke()
        }
        button(c, rx + 188f, by, 208f, 42f, if (resetArm > 0f) "Toque de novo p/ apagar" else "Recomeçar do zero", true, BTN_RED, 14f) {
            if (resetArm > 0f) {
                resetArm = 0f
                g.resetAll()
            } else resetArm = 3f
        }
    }

    private fun panelSkills(c: Canvas, pl: Float, pt: Float, pw: Float) {
        val p = g.p
        label(c, "Pontos de habilidade: ${p.skillPts}", pl + 26f, pt + 70f, 19f, if (p.skillPts > 0) col(0xFFC62828) else INK)
        label(c, "Bolinha verde = usada no AUTO", pl + pw - 26f, pt + 70f, 14f, INK2, Paint.Align.RIGHT)
        p.job.skills.forEachIndexed { k, id ->
            val s = SKILLS[id]
            val lv = p.skills[id]
            val y = pt + 86f + k * 116f
            rect.set(pl + 16f, y, pl + pw - 16f, y + 106f)
            fill.color = col(0x14000000); c.drawRoundRect(rect, 8f, 8f, fill)
            art.circle(c, pl + 66f, y + 53f, 34f, if (lv > 0) s.cor else col(0xFF9E9E9E))
            label(c, s.sigla, pl + 66f, y + 61f, 22f, Color.WHITE, Paint.Align.CENTER)
            label(c, s.nome, pl + 116f, y + 30f, 20f, INK)
            label(c, "Nv $lv/${s.maxLv}   ·   SP ${s.sp(max(1, lv))}   ·   recarga ${"%.1f".format(s.cd)}s", pl + 116f + text.measureText(s.nome) + 18f, y + 30f, 14f, INK2)
            label(c, (if (lv > 0) "Atual: " else "Nv 1: ") + s.desc(max(1, lv)), pl + 116f, y + 58f, 15f, INK)
            if (lv in 1 until s.maxLv) label(c, "Próx.: " + s.desc(lv + 1), pl + 116f, y + 84f, 14f, INK2)
            val canUp = p.skillPts > 0 && lv < s.maxLv
            button(c, pl + pw - 250f, y + 30f, 90f, 46f, "+1", canUp, BTN_GREEN, 18f) {
                if (p.skillPts > 0 && p.skills[id] < s.maxLv) { p.skillPts--; p.skills[id]++ }
            }
            button(c, pl + pw - 150f, y + 30f, 118f, 46f, "Auto: " + if (p.autoSkill[id]) "ON" else "OFF", true,
                if (p.autoSkill[id]) BTN_GREEN else Style.HUD_BTN, 16f) { p.autoSkill[id] = !p.autoSkill[id] }
        }
        if (p.job == Job.APRENDIZ) {
            val y = pt + 86f + 130f
            label(c, "Aprendizes só têm Primeiros Socorros.", pl + 30f, y + 20f, 17f, INK)
            label(c, "No Job Lv 10, fale com o Mestre de Classes na Vila para virar", pl + 30f, y + 50f, 16f, INK2)
            label(c, "Guerreiro, Mago, Caçador ou Ladino e ganhar 3 novas habilidades.", pl + 30f, y + 74f, 16f, INK2)
            label(c, "Pontos não usados passam para a próxima classe.", pl + 30f, y + 98f, 16f, INK2)
        }
    }

    private fun panelItens(c: Canvas, pl: Float, pt: Float, pw: Float, ph: Float) {
        button(c, pl + 16f, pt + 44f, 200f, 36f, "Equipamentos", true, if (itensTab == 0) BTN_GOLD else Style.HUD_BTN, 16f) { itensTab = 0 }
        button(c, pl + 224f, pt + 44f, 220f, 36f, "Consumíveis e cartas", true, if (itensTab == 1) BTN_GOLD else Style.HUD_BTN, 16f) { itensTab = 1 }
        if (itensTab == 0) panelEquip(c, pl, pt + 88f, pw, ph - 88f) else panelItensGeral(c, pl, pt + 40f, pw, ph - 40f)
    }

    private val SLOT_BG = col(0xFF2E3846)
    private val TIP_BG = col(0xF21E2530)
    private val C_UP = col(0xFF66BB6A)
    private val C_DOWN = col(0xFFEF5350)

    /** Aba de equipamentos com tier: espaços equipados, bolsa paginada e detalhes do item escolhido. */
    private fun panelEquip(c: Canvas, pl: Float, top: Float, pw: Float, h: Float) {
        val p = g.p
        if (selItem != null && selItem !in p.bag && p.gear.none { it === selItem }) selItem = null
        // ----- espaços equipados -----
        val sx = pl + 16f
        for (s in 0 until N_SLOTS) {
            val y = top + s * 54f
            val it = p.gear[s]
            rect.set(sx, y, sx + 250f, y + 48f)
            fill.color = SLOT_BG; c.drawRoundRect(rect, 6f, 6f, fill)
            if (it != null && it === selItem) { stroke.strokeWidth = 2.5f; stroke.color = col(0xFFFFD54F); c.drawRoundRect(rect, 6f, 6f, stroke) }
            itemBox(c, sx + 4f, y + 4f, 40f, it, it?.tipo ?: slotType(s))
            label(c, SLOT_NOMES[s], sx + 52f, y + 18f, 12f, col(0xFFB0BEC5))
            val nome = when {
                it != null -> it.nome
                s == 0 -> "${p.weaponName()} (loja)"
                s == 2 -> "${p.armorName()} (loja)"
                else -> "vazio"
            }
            label(c, fit(nome, 190f, 15f), sx + 52f, y + 38f, 15f, if (it != null) Equip.tierCor(it.tier) else col(0xFF8A96A6))
            if (it != null) addBtn(sx, y, sx + 250f, y + 48f) { selItem = it }
        }
        // ----- teste: gerar item de qualquer tier, simular drops, abates/hora -----
        val dy = top + N_SLOTS * 54f + 12f
        label(c, "Teste: gerar item", sx, dy + 4f, 13f, INK2)
        for (t in 1..4) button(c, sx + (t - 1) * 63f, dy + 12f, 58f, 34f, "T$t", true, tierBtn(t), 15f) { g.debugItem(t) }
        button(c, sx, dy + 54f, 250f, 34f, "Simular 10.000 abates", g.map.id != 0, Style.HUD_BTN, 15f) { g.simulateGear() }
        g.simGear?.let { r ->
            label(c, "T1 ${r[0]} · T2 ${r[1]} · T3 ${r[2]} · T4 ${r[3]}", sx, dy + 108f, 14f, INK)
        }
        label(c, "Abates/hora (sessão): ${fmt(g.killsPerHour())}", sx, dy + 130f, 13f, INK2)

        // ----- bolsa -----
        val gx = pl + 286f
        val cell = 56f
        val gap = 6f
        val cols = 5
        val rows = max(1, ((h - 120f) / (cell + gap)).toInt())
        val per = cols * rows
        val pages = max(1, (p.bag.size + per - 1) / per)
        bagPage = bagPage.coerceIn(0, pages - 1)
        label(c, "Bolsa: ${p.bag.size}/${Equip.limiteBolsa}", gx, top + 2f, 14f, if (p.bag.size >= Equip.limiteBolsa) col(0xFFC62828) else INK2)
        // venda automática por tier (liga/desliga)
        val vy = top + 10f + rows * (cell + gap) + 2f
        label(c, "Venda auto:", gx, vy + 22f, 13f, INK2)
        for (t in 1..4) {
            val on = p.autoSell[t - 1]
            button(c, gx + 82f + (t - 1) * 56f, vy, 50f, 32f, "T$t", true, if (on) tierBtn(t) else col(0xFFB0B8C2), 14f) { p.autoSell[t - 1] = !p.autoSell[t - 1] }
            if (on) label(c, "✓", gx + 82f + (t - 1) * 56f + 44f, vy + 10f, 11f, Color.WHITE, Paint.Align.RIGHT)
        }
        for (k in 0 until per) {
            val i = bagPage * per + k
            val x = gx + (k % cols) * (cell + gap)
            val y = top + 10f + (k / cols) * (cell + gap)
            val it = p.bag.getOrNull(i)
            itemBox(c, x, y, cell, it, it?.tipo)
            if (it != null) {
                if (it === selItem) { rect.set(x - 2f, y - 2f, x + cell + 2f, y + cell + 2f); stroke.strokeWidth = 2.5f; stroke.color = col(0xFFFFD54F); c.drawRoundRect(rect, 7f, 7f, stroke) }
                if (p.lv < it.base.nivel || !it.tipo.serve(p.job)) { fill.color = col(0x66B71C1C); rect.set(x, y, x + cell, y + cell); c.drawRoundRect(rect, 6f, 6f, fill) }
                addBtn(x, y, x + cell, y + cell) { selItem = it }
            }
        }
        if (pages > 1) {
            val by = top + h - 50f
            button(c, gx, by, 60f, 40f, "<", bagPage > 0, Style.HUD_BTN, 18f) { bagPage-- }
            label(c, "${bagPage + 1}/$pages", gx + 152f, by + 27f, 16f, INK, Paint.Align.CENTER)
            button(c, gx + 244f, by, 60f, 40f, ">", bagPage < pages - 1, Style.HUD_BTN, 18f) { bagPage++ }
        }

        // ----- detalhes do item -----
        val tx = gx + cols * (cell + gap) + 10f
        val tw = pl + pw - 16f - tx
        rect.set(tx, top, tx + tw, top + h - 12f)
        fill.color = TIP_BG; c.drawRoundRect(rect, 8f, 8f, fill)
        val it = selItem
        if (it == null) {
            label(c, "Toque em um item para ver", tx + 14f, top + 30f, 14f, col(0xFFB0BEC5))
            label(c, "atributos e comparação.", tx + 14f, top + 50f, 14f, col(0xFFB0BEC5))
            return
        }
        itemTooltip(c, it, tx + 14f, top + 28f, tw - 28f, top + h - 12f)
    }

    private fun tierBtn(t: Int) = when (t) { 1 -> col(0xFF78909C); 2 -> col(0xFF388E3C); 3 -> col(0xFF1E88E5); 4 -> col(0xFF8E24AA); else -> col(0xFFEF6C00) }

    private fun slotType(s: Int): EqType? = when (s) {
        0 -> if (g.p.job.magic) EqType.ARMA_MAG else EqType.ARMA_FIS
        else -> EqType.entries.firstOrNull { it.slot == s }
    }

    /** Detalhes: atributos base, encantamentos e diferença para o que está equipado (verde melhor, vermelho pior). */
    private fun itemTooltip(c: Canvas, it: GearItem, x: Float, y0: Float, w: Float, bottom: Float) {
        val p = g.p
        val tc = Equip.tierCor(it.tier)
        var y = y0
        for (ln in wrap(it.nome, w, 19f)) { label(c, ln, x, y, 19f, tc); y += 23f }
        label(c, "Tier ${it.tier} · ${Equip.tierNome(it.tier)}", x, y, 14f, tc); y += 20f
        val okLv = p.lv >= it.base.nivel
        val okJob = it.tipo.serve(p.job)
        label(c, "${it.tipo.nome} · requer Nv ${it.base.nivel}", x, y, 14f, if (okLv && okJob) col(0xFFCFD8DC) else C_DOWN); y += 18f
        if (!okJob) { label(c, "${p.job.nome} não pode usar", x, y, 13f, C_DOWN); y += 18f }
        y += 6f
        for (k in it.base.base.indices) if (it.base.base[k] != 0) {
            label(c, St.entries[k].texto(it.base.base[k]), x, y, 15f, Color.WHITE); y += 20f
        }
        y += 4f
        label(c, "Encantamentos", x, y, 13f, col(0xFF90A4AE)); y += 19f
        for (i in it.ench.indices) { label(c, "✦ " + St.entries[it.ench[i]].texto(it.vals[i]), x, y, 15f, tc); y += 20f }
        val equipped = p.gear[it.tipo.slot] === it
        y += 8f
        if (!equipped && okJob) {
            label(c, "Comparado ao equipado", x, y, 13f, col(0xFF90A4AE)); y += 19f
            val a = it.stats()
            val b = p.slotStats(it.tipo.slot)
            var any = false
            for (k in a.indices) {
                val d = a[k] - b[k]
                if (d == 0) continue
                any = true
                if (y < bottom - 112f) { label(c, St.entries[k].texto(d), x, y, 14f, if (d > 0) C_UP else C_DOWN); y += 18f }
            }
            if (!any) label(c, "Igual ao atual", x, y, 14f, col(0xFFCFD8DC))
        } else if (equipped) label(c, "Equipado", x, y, 14f, C_UP)
        val by = bottom - 54f
        if (equipped) button(c, x, by, w, 42f, "Desequipar", true, Style.HUD_BTN, 17f) { p.unequip(it.tipo.slot) }
        else {
            button(c, x, by, w, 42f, "Equipar", okLv && okJob, BTN_GOLD, 17f) { g.equipItem(it) }
            button(c, x, by - 48f, w, 40f, "Vender (+${fmt(Equip.sellValue(it))} z)", true, BTN_GREEN, 15f) { g.sellItem(it) }
        }
    }

    /** Quadrado do item: fundo escuro, brilho e borda na cor do tier e ícone do tipo. */
    private fun itemBox(c: Canvas, x: Float, y: Float, sz: Float, it: GearItem?, tipo: EqType?) {
        rect.set(x, y, x + sz, y + sz)
        fill.color = col(0xFF1C232C); c.drawRoundRect(rect, 6f, 6f, fill)
        val cor = if (it != null) Equip.tierCor(it.tier) else col(0xFF55606E)
        if (it != null && it.tier >= 2) {
            fill.color = (cor and 0x00FFFFFF) or 0x33000000
            c.drawCircle(x + sz / 2, y + sz / 2, sz * 0.42f, fill)
        }
        rect.set(x, y, x + sz, y + sz)
        stroke.strokeWidth = if (it != null) 2f else 1f; stroke.color = cor
        c.drawRoundRect(rect, 6f, 6f, stroke)
        if (tipo != null) itemIcon(c, tipo, x + sz / 2, y + sz / 2, sz * 0.32f, cor)
    }

    private fun itemIcon(c: Canvas, t: EqType, cx: Float, cy: Float, r: Float, cor: Int) {
        stroke.color = cor; stroke.strokeWidth = max(2f, r * 0.16f)
        path.reset()
        when (t) {
            EqType.ARMA_FIS -> {
                c.drawLine(cx - r * 0.8f, cy + r * 0.8f, cx + r * 0.9f, cy - r * 0.9f, stroke)
                c.drawLine(cx - r * 0.75f, cy + r * 0.15f, cx - r * 0.15f, cy + r * 0.75f, stroke)
            }
            EqType.ARMA_MAG -> {
                c.drawLine(cx - r * 0.7f, cy + r, cx + r * 0.35f, cy - r * 0.35f, stroke)
                c.drawCircle(cx + r * 0.55f, cy - r * 0.55f, r * 0.32f, stroke)
            }
            EqType.ELMO -> {
                rect.set(cx - r * 0.85f, cy - r * 0.8f, cx + r * 0.85f, cy + r * 0.9f)
                c.drawArc(rect, 180f, 180f, false, stroke)
                c.drawLine(cx - r * 0.95f, cy + r * 0.05f, cx + r * 0.95f, cy + r * 0.05f, stroke)
                c.drawLine(cx, cy - r * 0.8f, cx, cy + r * 0.6f, stroke)
            }
            EqType.ARMADURA -> {
                path.moveTo(cx - r * 0.4f, cy - r * 0.85f); path.lineTo(cx - r, cy - r * 0.5f); path.lineTo(cx - r * 0.7f, cy - r * 0.1f)
                path.lineTo(cx - r * 0.6f, cy + r * 0.9f); path.lineTo(cx + r * 0.6f, cy + r * 0.9f); path.lineTo(cx + r * 0.7f, cy - r * 0.1f)
                path.lineTo(cx + r, cy - r * 0.5f); path.lineTo(cx + r * 0.4f, cy - r * 0.85f); path.close()
                c.drawPath(path, stroke)
            }
            EqType.ESCUDO -> {
                path.moveTo(cx - r * 0.8f, cy - r * 0.8f); path.lineTo(cx + r * 0.8f, cy - r * 0.8f)
                path.quadTo(cx + r * 0.8f, cy + r * 0.5f, cx, cy + r); path.quadTo(cx - r * 0.8f, cy + r * 0.5f, cx - r * 0.8f, cy - r * 0.8f)
                c.drawPath(path, stroke)
            }
            EqType.BOTAS -> {
                path.moveTo(cx - r * 0.5f, cy - r); path.lineTo(cx - r * 0.5f, cy + r * 0.8f); path.lineTo(cx + r * 0.9f, cy + r * 0.8f)
                path.lineTo(cx + r * 0.9f, cy + r * 0.35f); path.lineTo(cx + r * 0.2f, cy + r * 0.1f); path.lineTo(cx + r * 0.2f, cy - r)
                c.drawPath(path, stroke)
            }
            EqType.ACESSORIO -> {
                c.drawCircle(cx, cy + r * 0.2f, r * 0.65f, stroke)
                fill.color = cor; c.drawCircle(cx, cy - r * 0.55f, r * 0.25f, fill)
            }
        }
    }

    /** Corta o texto com reticências se passar da largura. */
    private fun fit(s: String, maxW: Float, size: Float): String {
        text.textSize = size
        if (text.measureText(s) <= maxW) return s
        var t = s
        while (t.length > 1 && text.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    private fun panelItensGeral(c: Canvas, pl: Float, pt: Float, pw: Float, ph: Float) {
        val p = g.p
        val x = pl + 26f
        var y = pt + 70f
        label(c, "Equipamentos", x, y, 19f, INK); y += 30f
        val atkNome = if (p.job.magic) "MATK" else "ATK"
        label(c, "Arma: +${p.wRef} ${p.weaponName()}", x, y, 17f, INK); y += 22f
        label(c, "$atkNome ${(WEAPON_ATK[p.wTier] * (1 + p.wRef * 0.1f)).toInt()}   ·   " +
                (if (p.wCard >= 0) "${MONSTERS[p.wCard].cardNome} (${MONSTERS[p.wCard].card.texto(MONSTERS[p.wCard].cardV)})" else "slot de carta vazio"),
            x, y, 14f, INK2); y += 30f
        label(c, "Armadura: +${p.aRef} ${p.armorName()}", x, y, 17f, INK); y += 22f
        label(c, "DEF ${(ARMOR_DEF[p.aTier] * (1 + p.aRef * 0.1f)).toInt()}   ·   " +
                (if (p.aCard >= 0) "${MONSTERS[p.aCard].cardNome} (${MONSTERS[p.aCard].card.texto(MONSTERS[p.aCard].cardV)})" else "slot de carta vazio"),
            x, y, 14f, INK2); y += 40f
        label(c, "Consumíveis", x, y, 19f, INK); y += 12f
        for (i in POTIONS.indices) {
            val po = POTIONS[i]
            art.circle(c, x + 14f, y + 22f, 10f, po.cor)
            label(c, "${po.nome}  x${p.potions[i]}", x + 34f, y + 28f, 16f, INK)
            label(c, if (po.hp > 0) "+${po.hp} HP" else "+${po.sp} SP", x + 250f, y + 28f, 14f, INK2)
            button(c, x + 330f, y + 4f, 80f, 38f, "Usar", p.potions[i] > 0, Style.HUD_BTN, 15f) {
                if (i == 3) g.drinkSp(true) else if (p.potions[i] > 0 && p.hp < p.maxHp) {
                    p.potions[i]--; p.hp = min(p.maxHp, p.hp + po.hp)
                }
            }
            y += 46f
        }
        y += 14f
        label(c, "Espólios: ${p.loot.sum()} itens (vale ${fmt(g.lootValue())} zeny)", x, y, 16f, INK); y += 22f
        label(c, "Venda no Mercador da Vila.", x, y, 14f, INK2)

        val cx = pl + pw / 2 + 20f
        label(c, "Cartas", cx, pt + 70f, 19f, INK)
        val owned = MONSTERS.indices.filter { p.cards[it] > 0 }
        if (owned.isEmpty()) {
            label(c, "Nenhuma carta ainda.", cx, pt + 104f, 15f, INK2)
            label(c, "Monstros têm 0,5% de chance de dropar", cx, pt + 128f, 14f, INK2)
            label(c, "sua carta (chefes: 10%).", cx, pt + 148f, 14f, INK2)
        }
        val perPage = 7
        val pages = max(1, (owned.size + perPage - 1) / perPage)
        cardPage = cardPage.coerceIn(0, pages - 1)
        owned.drop(cardPage * perPage).take(perPage).forEachIndexed { k, id ->
            val m = MONSTERS[id]
            val yy = pt + 86f + k * 60f
            rect.set(cx, yy, cx + 36f, yy + 48f)
            fill.color = col(0xFFFFF8E1); c.drawRoundRect(rect, 4f, 4f, fill)
            stroke.strokeWidth = 2f; stroke.color = col(0xFFB8860B); c.drawRoundRect(rect, 4f, 4f, stroke)
            art.circle(c, cx + 18f, yy + 22f, 10f, m.c1)
            label(c, "${m.cardNome} x${p.cards[id]}", cx + 46f, yy + 20f, 15f, INK)
            label(c, "${m.card.texto(m.cardV)}  ·  ${if (m.card.arma) "arma" else "armadura"}", cx + 46f, yy + 42f, 13f, INK2)
            button(c, pl + pw - 116f, yy + 6f, 92f, 38f, "Equipar", true, BTN_GOLD, 15f) { g.insertCard(id) }
        }
        if (pages > 1) {
            val by = pt + ph - 54f
            button(c, cx, by, 60f, 40f, "<", cardPage > 0, Style.HUD_BTN, 18f) { cardPage-- }
            label(c, "${cardPage + 1}/$pages", cx + 100f, by + 27f, 16f, INK, Paint.Align.CENTER)
            button(c, cx + 140f, by, 60f, 40f, ">", cardPage < pages - 1, Style.HUD_BTN, 18f) { cardPage++ }
        }
    }

    private fun panelLoja(c: Canvas, pl: Float, pt: Float, pw: Float) {
        val p = g.p
        val x = pl + 26f
        label(c, "Zeny: ${fmt(p.zeny)}", pl + pw - 60f, pt + 70f, 18f, col(0xFF8D6E00), Paint.Align.RIGHT)
        label(c, "Poções", x, pt + 70f, 19f, INK)
        for (i in POTIONS.indices) {
            val po = POTIONS[i]
            val y = pt + 84f + i * 52f
            art.circle(c, x + 14f, y + 22f, 11f, po.cor)
            label(c, po.nome, x + 36f, y + 28f, 17f, INK)
            label(c, (if (po.hp > 0) "+${po.hp} HP" else "+${po.sp} SP") + "  ·  ${po.preco}z  ·  tem ${p.potions[i]}", x + 200f, y + 28f, 14f, INK2)
            var bx = pl + pw - 290f
            for (n in intArrayOf(1, 10, 50)) {
                button(c, bx, y + 2f, 84f, 40f, "x$n", p.zeny >= po.preco.toLong() * n, Style.HUD_BTN, 15f) { g.buyPotion(i, n) }
                bx += 92f
            }
        }
        var y = pt + 84f + 4 * 52f + 18f
        label(c, "Equipamentos", x, y, 19f, INK)
        y += 10f
        val wn = p.wTier + 1
        val atkNome = if (p.job.magic) "MATK" else "ATK"
        if (wn <= 6) {
            label(c, WEAPON_NAMES[p.job.weaponCat][wn], x, y + 28f, 17f, INK)
            label(c, "$atkNome ${WEAPON_ATK[wn]} (atual ${WEAPON_ATK[p.wTier]})  ·  requer Nv ${EQ_LV[wn]}  ·  ${fmt(WEAPON_PRICE[wn].toLong())}z", x, y + 50f, 14f,
                if (p.lv >= EQ_LV[wn]) INK2 else col(0xFFC62828))
            button(c, pl + pw - 290f, y + 6f, 260f, 44f, "Comprar arma", p.lv >= EQ_LV[wn] && p.zeny >= WEAPON_PRICE[wn], BTN_GOLD, 16f) { g.buyWeapon() }
        } else label(c, "Sua arma já é a melhor da loja.", x, y + 28f, 16f, INK2)
        y += 64f
        val an = p.aTier + 1
        if (an <= 6) {
            label(c, ARMOR_NAMES[an], x, y + 28f, 17f, INK)
            label(c, "DEF ${ARMOR_DEF[an]} (atual ${ARMOR_DEF[p.aTier]})  ·  requer Nv ${EQ_LV[an]}  ·  ${fmt(ARMOR_PRICE[an].toLong())}z", x, y + 50f, 14f,
                if (p.lv >= EQ_LV[an]) INK2 else col(0xFFC62828))
            button(c, pl + pw - 290f, y + 6f, 260f, 44f, "Comprar armadura", p.lv >= EQ_LV[an] && p.zeny >= ARMOR_PRICE[an], BTN_GOLD, 16f) { g.buyArmor() }
        } else label(c, "Sua armadura já é a melhor da loja.", x, y + 28f, 16f, INK2)
        y += 64f
        label(c, "Comprar um novo equipamento zera o refino; a carta volta para o inventário.", x, y + 18f, 13f, INK2)
        val lv = g.lootValue()
        button(c, x, y + 30f, 360f, 44f, "Vender espólios (+${fmt(lv)}z)", lv > 0, BTN_GREEN, 16f) { g.sellLoot() }
    }

    private fun panelFerreiro(c: Canvas, pl: Float, pt: Float, pw: Float) {
        val p = g.p
        val x = pl + 26f
        label(c, "Zeny: ${fmt(p.zeny)}", pl + pw - 60f, pt + 70f, 18f, col(0xFF8D6E00), Paint.Align.RIGHT)
        label(c, "\"Traga-me seu equipamento e zeny. Não garanto nada acima de +4!\"", x, pt + 70f, 15f, INK2)
        for (k in 0..1) {
            val weapon = k == 0
            val tier = if (weapon) p.wTier else p.aTier
            val ref = if (weapon) p.wRef else p.aRef
            val nome = if (weapon) p.weaponName() else p.armorName()
            val y = pt + 100f + k * 170f
            rect.set(pl + 16f, y, pl + pw - 16f, y + 150f)
            fill.color = col(0x14000000); c.drawRoundRect(rect, 8f, 8f, fill)
            label(c, (if (weapon) "Arma: " else "Armadura: ") + "+$ref $nome", x + 10f, y + 36f, 20f, INK)
            val base = if (weapon) WEAPON_ATK[tier] else ARMOR_DEF[tier]
            label(c, (if (weapon) (if (p.job.magic) "MATK " else "ATK ") else "DEF ") + "${(base * (1 + ref * 0.1f)).toInt()}  (+10% por nível de refino)", x + 10f, y + 64f, 15f, INK2)
            if (ref < 10) {
                val cost = refineCost(tier, ref)
                val ch = REFINE_CHANCE[ref]
                label(c, "Refinar para +${ref + 1}:  $ch% de sucesso  ·  ${fmt(cost.toLong())}z", x + 10f, y + 100f, 17f,
                    if (ch >= 100) col(0xFF2E7D32) else if (ch >= 40) col(0xFFEF6C00) else col(0xFFC62828))
                label(c, "Falha: o item perde 1 nível de refino.", x + 10f, y + 126f, 13f, INK2)
                button(c, pl + pw - 220f, y + 50f, 180f, 54f, "Refinar", p.zeny >= cost, BTN_GOLD, 19f) { g.refine(weapon) }
            } else label(c, "Refino máximo (+10)!", x + 10f, y + 100f, 17f, col(0xFF8D6E00))
        }
        if (g.refineMsg.isNotEmpty()) label(c, g.refineMsg, pl + pw / 2, pt + 480f, 20f,
            if (g.refineMsg.startsWith("Sucesso")) col(0xFF2E7D32) else col(0xFFC62828), Paint.Align.CENTER)
    }

    private fun panelMestre(c: Canvas, pl: Float, pt: Float, pw: Float, ph: Float) {
        val p = g.p
        val x = pl + 26f
        if (p.job == Job.APRENDIZ) {
            label(c, if (p.jlv < 10) "\"Volte quando atingir Job Lv 10.\"  (atual: ${p.jlv})" else "\"Você está pronto! Escolha seu caminho:\"", x, pt + 70f, 17f, INK)
            val jobs = arrayOf(Job.GUERREIRO, Job.MAGO, Job.CACADOR, Job.LADINO)
            val cw = (pw - 32f - 3 * 12f) / 4
            jobs.forEachIndexed { i, j ->
                val cx = pl + 16f + i * (cw + 12f)
                val cy = pt + 86f
                rect.set(cx, cy, cx + cw, cy + 390f)
                fill.color = col(0x14000000); c.drawRoundRect(rect, 8f, 8f, fill)
                val wpn = if (j == Job.LADINO) 0 else j.weaponCat
                art.hero(c, cx + cw / 2, cy + 110f, 1f, j.cor, j.cabelo, j.hat, wpn, 0f, false, 0f, 1.5f)
                label(c, j.nome, cx + cw / 2, cy + 146f, 20f, INK, Paint.Align.CENTER)
                var yy = cy + 172f
                for (ln in wrap(j.desc, cw - 20f, 13f)) {
                    label(c, ln, cx + 10f, yy, 13f, INK2); yy += 18f
                }
                yy += 6f
                for (sid in j.skills) {
                    if (sid == 0) continue
                    label(c, "• " + SKILLS[sid].nome, cx + 10f, yy, 14f, INK); yy += 20f
                }
                button(c, cx + 10f, cy + 330f, cw - 20f, 46f, "Escolher", p.jlv >= 10, BTN_GOLD, 17f) { g.changeJob(j) }
            }
        } else {
            label(c, "\"Você já trilha o caminho do ${p.job.nome}. Continue treinando!\"", x, pt + 70f, 17f, INK)
            label(c, "Job Lv ${p.jlv}/${p.job.jobMax}. A cada nível de classe você ganha 1 ponto de habilidade", x, pt + 102f, 15f, INK2)
            label(c, "e bônus de atributos da classe (veja o +verde na tela de Status).", x, pt + 124f, 15f, INK2)
        }
        val by = pt + ph - 56f
        label(c, "Redistribuir (grátis):", x, by + 28f, 16f, INK)
        button(c, x + 190f, by, 200f, 42f, "Atributos", true, Style.HUD_BTN, 16f) { p.resetStats(); g.addLog("Atributos redistribuídos.", C_GRAY) }
        button(c, x + 400f, by, 200f, 42f, "Habilidades", true, Style.HUD_BTN, 16f) { p.resetSkills(); g.addLog("Habilidades redistribuídas.", C_GRAY) }
    }

    private fun panelTeleporte(c: Canvas, pl: Float, pt: Float, pw: Float) {
        val p = g.p
        label(c, "\"Para onde deseja ir? Só levo a lugares que você já visitou.\"", pl + 26f, pt + 70f, 15f, INK2)
        for (i in 1 until MAPS.size) {
            val m = MAPS[i]
            val y = pt + 82f + (i - 1) * 68f
            val ok = i <= p.maxMap
            rect.set(pl + 16f, y, pl + pw - 16f, y + 60f)
            fill.color = col(0x14000000); c.drawRoundRect(rect, 8f, 8f, fill)
            art.circle(c, pl + 52f, y + 30f, 19f, m.g1)
            label(c, if (ok) m.nome else "???", pl + 86f, y + 28f, 19f, if (ok) INK else INK2)
            val chefe = if (ok && m.boss >= 0) "  ·  chefe: ${MONSTERS[m.boss].nome}" else ""
            label(c, m.faixa + chefe, pl + 86f, y + 50f, 14f, INK2)
            if (i == p.farmMap) label(c, "mapa de caça offline", pl + pw - 170f, y + 36f, 13f, col(0xFF2E7D32), Paint.Align.RIGHT)
            button(c, pl + pw - 150f, y + 9f, 118f, 42f, "Ir", ok, Style.HUD_BTN, 17f) {
                g.panel = Panel.NONE
                g.changeMap(i, 0)
            }
        }
    }

    private fun panelOffline(c: Canvas, pl: Float, pt: Float, pw: Float, ph: Float) {
        val r = g.report ?: run { g.panel = Panel.NONE; return }
        val x = pl + 40f
        var y = pt + 80f
        val h = r.secs / 3600
        val mi = (r.secs % 3600) / 60
        label(c, "Tempo fora: ${h}h ${mi}min" + if (r.secs >= 12 * 3600) " (máximo 12h)" else "", x, y, 18f, INK); y += 30f
        label(c, "O bot caçou em: ${r.mapa}", x, y, 18f, INK); y += 40f
        val rows = listOf(
            "Monstros derrotados" to fmt(r.kills),
            "EXP base" to "+${fmt(r.exp)}",
            "EXP de classe" to "+${fmt(r.jexp)}",
            "Níveis ganhos" to "Base +${r.lvBase}  ·  Classe +${r.lvJob}",
            "Zeny" to "+${fmt(r.zeny)}",
            "Espólios" to "+${r.loot}",
            "Poções" to "usou ${r.potsUsadas}, achou ${r.potsGanhas}",
        )
        for ((k, v) in rows) {
            label(c, k, x, y, 17f, INK2)
            label(c, v, x + 240f, y, 18f, INK)
            y += 30f
        }
        if (r.gear.sum() > 0) {
            label(c, "Equipamentos", x, y, 17f, INK2)
            var ex = x + 240f
            for (t in 1..4) {
                val sTxt = "T$t ${r.gear[t - 1]}   "
                label(c, sTxt, ex, y, 18f, tierBtn(t)); text.textSize = 18f; ex += text.measureText(sTxt)
            }
            y += 26f
            if (r.gearVenda > 0) { label(c, "Venda automática: +${fmt(r.gearVenda)} zeny", x + 240f, y, 14f, INK2); y += 22f }
            if (r.gearRaros.isNotEmpty()) {
                for (ln in wrap("✦ " + r.gearRaros.joinToString(", "), pw - 80f, 15f).take(2)) { label(c, ln, x, y, 15f, col(0xFF6A1B9A)); y += 22f }
            }
            y += 4f
        }
        if (r.cards.isNotEmpty()) {
            label(c, "★ " + r.cards.joinToString(", "), x, y, 17f, col(0xFF8D6E00)); y += 30f
        }
        if (r.nota.isNotEmpty()) {
            for (ln in wrap(r.nota, pw - 80f, 15f)) {
                label(c, ln, x, y, 15f, col(0xFFC62828)); y += 22f
            }
        }
        button(c, pl + pw / 2 - 100f, pt + ph - 62f, 200f, 46f, "Coletar!", true, BTN_GREEN, 19f) {
            g.panel = Panel.NONE
            g.report = null
        }
    }

    private fun panelMorte(c: Canvas, pl: Float, pt: Float, pw: Float) {
        label(c, "Os monstros foram mais fortes desta vez...", pl + pw / 2, pt + 90f, 18f, INK, Paint.Align.CENTER)
        val msg = if (g.lastLoss > 0) "Perdeu ${fmt(g.lastLoss)} de EXP base (1%)." else "Aprendizes não perdem EXP ao morrer."
        label(c, msg, pl + pw / 2, pt + 122f, 16f, if (g.lastLoss > 0) col(0xFFC62828) else col(0xFF2E7D32), Paint.Align.CENTER)
        button(c, pl + pw / 2 - 130f, pt + 166f, 260f, 52f, "Retornar à Vila", true, BTN_GOLD, 19f) { g.respawn() }
    }

    // =====================================================================
    // TELA INICIAL
    // =====================================================================
    private var titleT = 0f
    private val actTitle: () -> Unit = { g.title = false }
    /** Cores da ponte (de fora para dentro). */
    private val BRIDGE = intArrayOf(
        0xFFE57373.toInt(), 0xFFFFB74D.toInt(), 0xFFFFF176.toInt(), 0xFF81C784.toInt(),
        0xFF64B5F6.toInt(), 0xFF9575CD.toInt()
    )

    /** Céu noturno, ponte arco-íris cintilando, logo, Aprendiz e Guerreiro no morro; toque em qualquer lugar entra. */
    private fun drawTitle(c: Canvas) {
        titleT += frameDt
        val t = titleT
        // céu em faixas
        val sky = intArrayOf(0xFF0A0F1F.toInt(), 0xFF0F1830.toInt(), 0xFF152242.toInt(), 0xFF1C2D55.toInt(), 0xFF243866.toInt())
        val bh = VH * 0.8f / sky.size
        for (i in sky.indices) { fill.color = sky[i]; c.drawRect(0f, i * bh, vw, (i + 1) * bh + 1f, fill) }
        // estrelas piscando (posições fixas)
        for (i in 0 until 80) {
            val sx = ((i * 7919) % 1000) / 1000f * vw
            val sy = ((i * 104729 + 17) % 1000) / 1000f * VH * 0.62f
            val a = 0.35f + 0.65f * abs(sin(t * (0.8f + (i % 5) * 0.3f) + i))
            fill.color = art.alpha(Color.WHITE, (a * 210).toInt())
            c.drawCircle(sx, sy, 1f + (i % 3) * 0.7f, fill)
        }
        val cx = vw / 2
        val ground = VH * 0.80f
        // ponte arco-íris: arcos concêntricos com um brilho que corre por ela
        val rad = min(vw * 0.46f, VH * 0.62f)
        val band = 9f
        stroke.strokeWidth = band + 1f
        for (k in BRIDGE.indices) {
            val r = rad - k * band
            val glow = 150 + (60 * (0.5f + 0.5f * sin(t * 2f - k * 0.6f))).toInt()
            stroke.color = art.alpha(BRIDGE[k], glow)
            rect.set(cx - r, ground - r, cx + r, ground + r)
            c.drawArc(rect, 180f, 180f, false, stroke)
        }
        val sweep = 180f + ((t * 50f) % 220f) - 20f
        stroke.strokeWidth = band * BRIDGE.size
        stroke.color = art.alpha(Color.WHITE, 60)
        val rm = rad - band * (BRIDGE.size - 1) / 2f
        rect.set(cx - rm, ground - rm, cx + rm, ground + rm)
        c.drawArc(rect, sweep, 14f, false, stroke)
        // morros e chão
        art.oval(c, -vw * 0.2f, ground - 60f, vw * 0.55f, ground + 120f, col(0xFF1E3B2A))
        art.oval(c, vw * 0.45f, ground - 46f, vw * 1.2f, ground + 120f, col(0xFF1E3B2A))
        fill.color = col(0xFF173022); c.drawRect(0f, ground, vw, VH, fill)
        art.oval(c, cx - 200f, ground - 34f, cx + 200f, ground + 30f, col(0xFF25492F))
        // heróis
        val hh = VH * 0.24f
        frontHero(c, Job.APRENDIZ, cx - 70f, ground + 4f + sin(t * 2.2f) * 1.2f, hh)
        frontHero(c, Job.GUERREIRO, cx + 70f, ground + 4f + sin(t * 2.2f + 1.3f) * 1.2f, hh)
        // logo
        val ly = safeT + VH * 0.2f
        outlined(c, "BIFROST", cx, ly, 78f, col(0xFFFFF59D))
        label(c, "A ponte entre os mundos", cx, ly + 40f, 19f, col(0xFFB0BEC5), Paint.Align.CENTER)
        // toque para começar
        val a = 0.45f + 0.55f * (0.5f + 0.5f * sin(t * 3.2f))
        outlined(c, "Toque para começar", cx, VH - safeB - 34f, 24f, art.alpha(Color.WHITE, (a * 255).toInt()))
        label(c, "v$VERSAO", vw - safeR - 16f, VH - safeB - 14f, 15f, col(0xFF90A4AE), Paint.Align.RIGHT)
        addBtn(0f, 0f, vw, VH, actTitle)
    }

    // =====================================================================
    // SELEÇÃO DE PERSONAGEM
    // =====================================================================
    /** Sprite de frente nos slots e na tela inicial: 0 = Aprendiz, 1 = Guerreiro, 2 = Mago. */
    private val slotBmp = arrayOfNulls<Bitmap>(4)
    private val slotStaff = arrayOfNulls<Bitmap>(4)
    private val actSlot: Array<() -> Unit> = Array(SLOTS) { i ->
        {
            // tocar de novo no slot já escolhido: joga (ou cria, se vazio)
            if (g.selSlot == i) {
                if (g.slots[i] != null) g.onPlay?.invoke(i) else g.onCreateChar?.invoke(i)
            }
            g.selSlot = i
        }
    }

    /** Ao entrar com outro personagem: esquece barras/animações do anterior. */
    fun resetChar() {
        seenLv = -1
        prevHp = -1
        lastPx = Float.NaN
        camMap = -1
        resetArm = 0f
        testMode = false
    }

    private fun drawSelect(c: Canvas) {
        fill.color = col(0xFF1A2230); c.drawRect(0f, 0f, vw, VH, fill)
        fill.color = col(0xFF243047); c.drawRect(0f, VH * 0.55f, vw, VH, fill)
        val L = safeL + 20f
        val R = vw - safeR - 20f
        val T = safeT + 12f
        val B = VH - safeB - 12f
        val cx = (L + R) / 2
        outlined(c, "Bifrost", cx, T + 46f, 40f, col(0xFFFFF59D))
        label(c, "v$VERSAO", R, T + 24f, 15f, col(0xFF90A4AE), Paint.Align.RIGHT)
        label(c, "Selecione seu personagem", cx, T + 76f, 17f, col(0xFFCFD8DC), Paint.Align.CENTER)
        button(c, L, T + 8f, 110f, 42f, "‹ Início", true, Style.HUD_BTN, 17f) { g.title = true }

        val gap = 24f
        val cw = min(320f, (R - L - 2 * gap) / SLOTS)
        val ch = min(400f, B - T - 96f - 76f)
        val x0 = cx - (SLOTS * cw + (SLOTS - 1) * gap) / 2
        val y0 = T + 96f
        for (i in 0 until SLOTS) {
            val x = x0 + i * (cw + gap)
            val sp = g.slots[i]
            window(c, x, y0, cw, ch, "Slot ${i + 1}")
            if (i == g.selSlot) {
                rect.set(x - 3f, y0 - 3f, x + cw + 3f, y0 + ch + 3f)
                stroke.strokeWidth = 3f; stroke.color = col(0xFFFFD54F)
                c.drawRoundRect(rect, Style.HUD_RADIUS + 2f, Style.HUD_RADIUS + 2f, stroke)
            }
            addBtn(x, y0, x + cw, y0 + ch, actSlot[i])
            val mx = x + cw / 2
            if (sp == null) {
                art.circle(c, mx, y0 + ch / 2 - 16f, 38f, col(0x225874A0))
                label(c, "+", mx, y0 + ch / 2 - 1f, 44f, Style.HUD_BTN, Paint.Align.CENTER)
                label(c, "Criar personagem", mx, y0 + ch / 2 + 56f, 17f, INK2, Paint.Align.CENTER)
                continue
            }
            val feet = y0 + 36f + (ch - 36f) * 0.58f
            slotHero(c, sp, mx, feet, (ch - 36f) * 0.48f)
            label(c, sp.nome, mx, feet + 36f, 20f, INK, Paint.Align.CENTER)
            label(c, sp.job.nome, mx, feet + 60f, 16f, INK2, Paint.Align.CENTER)
            label(c, "Base Lv ${sp.lv}  ·  Job Lv ${sp.jlv}", mx, feet + 86f, 15f, INK, Paint.Align.CENTER)
            label(c, MAPS[sp.map.coerceIn(0, MAPS.size - 1)].nome, mx, feet + 110f, 14f, INK2, Paint.Align.CENTER)
        }

        // botões
        val has = g.slots[g.selSlot] != null
        val bw = 190f
        val bg = 16f
        val bx = cx - (3 * bw + 2 * bg) / 2
        val by = y0 + ch + 20f
        button(c, bx, by, bw, 52f, "Jogar", has, BTN_GREEN, 20f) { g.onPlay?.invoke(g.selSlot) }
        button(c, bx + bw + bg, by, bw, 52f, "Criar", !has, Style.HUD_BTN, 20f) { g.onCreateChar?.invoke(g.selSlot) }
        button(c, bx + 2 * (bw + bg), by, bw, 52f, "Apagar", has, BTN_RED, 20f) { g.onDeleteChar?.invoke(g.selSlot) }
    }

    /** Boneco parado de frente no slot: sprite (Aprendiz/Guerreiro) ou boneco desenhado. */
    private fun slotHero(c: Canvas, sp: Player, x: Float, feet: Float, h: Float) = frontHero(c, sp.job, x, feet, h)

    private fun frontHero(c: Canvas, job: Job, x: Float, feet: Float, h: Float) {
        if (hasSprite(job)) {
            val i = when (job) { Job.APRENDIZ -> 0; Job.MAGO -> 2; Job.CACADOR -> 3; else -> 1 }
            val b = slotBmp[i] ?: BitmapFactory.decodeResource(res, skinOf(job).ids[SpriteHero.S],
                BitmapFactory.Options().apply { inScaled = false })?.also { slotBmp[i] = it }
            if (b != null) {
                art.oval(c, x - h * 0.22f, feet - h * 0.05f, x + h * 0.22f, feet + h * 0.05f, col(0x44000000))
                val k = h / b.height
                dstR.set(x - b.width * k / 2, feet - b.height * k, x + b.width * k / 2, feet)
                val sk = skinOf(job)
                if (sk.staffId != 0) {
                    // cajado/arco atrás do corpo, na mão da vista de frente
                    val st = slotStaff[i] ?: BitmapFactory.decodeResource(res, sk.staffId,
                        BitmapFactory.Options().apply { inScaled = false })?.also { slotStaff[i] = it }
                    if (st != null) {
                        c.save()
                        c.translate(dstR.left + sk.handX[0] * k, dstR.top + sk.handY[0] * k)
                        c.rotate(sk.staffTilt[0])
                        c.scale(k, k)
                        c.drawBitmap(st, -sk.gripX, -sk.gripY, bmpPaint)
                        sk.stringPts?.let { sp ->
                            stroke.strokeWidth = 3f; stroke.color = col(0xFFEDE6D6)
                            c.drawLine(sp[0] - sk.gripX, sp[1] - sk.gripY, sp[2] - sk.gripX, sp[3] - sk.gripY, stroke)
                        }
                        c.restore()
                    }
                }
                c.drawBitmap(b, null, dstR, bmpPaint)
                return
            }
        }
        art.hero(c, x, feet, 1f, job.cor, job.cabelo, job.hat, weaponFor(job), 0f, false, 0f, h / 66f, 0f)
    }

    // =====================================================================
    // WIDGETS (estilo janela clássica: borda fina, cantos levemente arredondados, título azulado)
    // =====================================================================
    private fun window(c: Canvas, l: Float, t: Float, w: Float, h: Float, title: String?) {
        val rad = Style.HUD_RADIUS
        rect.set(l + 2f, t + 3f, l + w + 2f, t + h + 3f)
        fill.color = col(0x44000000); c.drawRoundRect(rect, rad, rad, fill)
        rect.set(l, t, l + w, t + h)
        fill.color = Style.HUD_BG; c.drawRoundRect(rect, rad, rad, fill)
        if (title != null) {
            rect.set(l, t, l + w, t + 36f)
            fill.color = Style.HUD_TITLE_BOT; c.drawRoundRect(rect, rad, rad, fill)
            c.drawRect(l, t + rad, l + w, t + 36f, fill)
            fill.color = Style.HUD_TITLE_TOP; c.drawRect(l + 1f, t + rad, l + w - 1f, t + 16f, fill)
            rect.set(l + 1f, t + 1f, l + w - 1f, t + 16f); c.drawRoundRect(rect, rad, rad, fill)
            stroke.strokeWidth = 1f; stroke.color = col(0x55FFFFFF); c.drawLine(l + 4f, t + 1.5f, l + w - 4f, t + 1.5f, stroke)
            label(c, title, l + 14f, t + 25f, 17f, Color.WHITE)
        }
        rect.set(l, t, l + w, t + h)
        stroke.strokeWidth = 1.5f; stroke.color = Style.HUD_BORDER; c.drawRoundRect(rect, rad, rad, stroke)
    }

    private fun button(
        c: Canvas, l: Float, t: Float, w: Float, h: Float, txt: String, enabled: Boolean = true,
        cor: Int = Style.HUD_BTN, size: Float = 18f, act: () -> Unit
    ) {
        rect.set(l, t, l + w, t + h)
        fill.color = if (enabled) cor else col(0xFFAAB0B8)
        c.drawRoundRect(rect, Style.HUD_RADIUS, Style.HUD_RADIUS, fill)
        fill.color = col(0x26FFFFFF)
        rect.set(l + 2f, t + 2f, l + w - 2f, t + h / 2)
        c.drawRoundRect(rect, Style.HUD_RADIUS - 2f, Style.HUD_RADIUS - 2f, fill)
        rect.set(l, t, l + w, t + h)
        stroke.strokeWidth = 1f; stroke.color = col(0x77000000); c.drawRoundRect(rect, Style.HUD_RADIUS, Style.HUD_RADIUS, stroke)
        label(c, txt, l + w / 2, t + h / 2 + size * 0.36f, size, if (enabled) Color.WHITE else col(0xFFEEEEEE), Paint.Align.CENTER)
        if (enabled) addBtn(l, t, l + w, t + h, act)
    }

    private fun bar(c: Canvas, l: Float, t: Float, w: Float, h: Float, frac: Float, cor: Int) {
        rect.set(l, t, l + w, t + h)
        fill.color = col(0xFF37474F); c.drawRoundRect(rect, 3f, 3f, fill)
        val fw = w * frac.coerceIn(0f, 1f)
        if (fw > 1f) {
            rect.set(l, t, l + fw, t + h)
            fill.color = cor; c.drawRoundRect(rect, 3f, 3f, fill)
            rect.set(l + 1f, t + 1f, l + fw - 1f, t + h / 2)
            fill.color = col(0x33FFFFFF); c.drawRoundRect(rect, 2f, 2f, fill)
        }
    }

    private fun label(c: Canvas, s: String, x: Float, y: Float, size: Float, cor: Int, align: Paint.Align = Paint.Align.LEFT) {
        text.textSize = size; text.color = cor; text.textAlign = align
        c.drawText(s, x, y, text)
        text.textAlign = Paint.Align.LEFT
    }

    private fun outlined(c: Canvas, s: String, x: Float, y: Float, size: Float, cor: Int, align: Paint.Align = Paint.Align.CENTER) {
        textOut.textSize = size; textOut.strokeWidth = size / 5f; textOut.textAlign = align
        textOut.alpha = Color.alpha(cor)
        c.drawText(s, x, y, textOut)
        text.textSize = size; text.color = cor; text.textAlign = align
        c.drawText(s, x, y, text)
        text.textAlign = Paint.Align.LEFT
    }

    private fun wrap(s: String, maxW: Float, size: Float): List<String> {
        text.textSize = size
        val out = ArrayList<String>()
        var cur = ""
        for (w in s.split(" ")) {
            val t = if (cur.isEmpty()) w else "$cur $w"
            if (text.measureText(t) > maxW && cur.isNotEmpty()) {
                out.add(cur); cur = w
            } else cur = t
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }

    private fun fmt(v: Long): String = "%,d".format(v).replace(',', '.')
}
