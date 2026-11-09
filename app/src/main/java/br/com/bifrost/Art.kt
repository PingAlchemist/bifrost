package br.com.bifrost

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/** Desenho procedural de personagens, monstros e cenário (sem assets externos). */
class Art {
    val f = Paint(Paint.ANTI_ALIAS_FLAG)
    val s = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val r = RectF()
    private val path = Path()

    private val SKIN = col(0xFFFFE0BD)
    private val DARK = col(0xFF212121)
    private val SHADOW = col(0x40000000)

    /** Animações ambientes (balanço de árvore, brilhos). Desligado = visual antigo. */
    var anim = true

    // ----- contorno: desenha a silhueta em cor escura (SRC_IN) deslocada, antes do sprite normal -----
    private val outlineFilter = PorterDuffColorFilter(col(0xCC1A1410), PorterDuff.Mode.SRC_IN)
    private var outline = false

    fun beginOutline() {
        outline = true
        f.colorFilter = outlineFilter
        s.colorFilter = outlineFilter
    }

    fun endOutline() {
        outline = false
        f.colorFilter = null
        s.colorFilter = null
    }

    val isOutline: Boolean
        get() = outline

    // ---------- primitivas ----------
    fun oval(c: Canvas, l: Float, t: Float, rr: Float, b: Float, color: Int) {
        r.set(l, t, rr, b); f.color = color; c.drawOval(r, f)
    }

    fun circle(c: Canvas, x: Float, y: Float, rad: Float, color: Int) {
        f.color = color; c.drawCircle(x, y, rad, f)
    }

    fun rr(c: Canvas, l: Float, t: Float, rr: Float, b: Float, rad: Float, color: Int) {
        r.set(l, t, rr, b); f.color = color; c.drawRoundRect(r, rad, rad, f)
    }

    fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int) {
        s.strokeWidth = w; s.color = color; c.drawLine(x1, y1, x2, y2, s)
    }

    fun tri(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Int) {
        path.reset(); path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close()
        f.color = color; c.drawPath(path, f)
    }

    fun shadow(c: Canvas, x: Float, y: Float, w: Float) {
        if (outline) return
        oval(c, x - w, y - w * 0.35f, x + w, y + w * 0.35f, SHADOW)
        if (anim) oval(c, x - w * 0.6f, y - w * 0.2f, x + w * 0.6f, y + w * 0.2f, SHADOW)
    }

    fun darker(color: Int, k: Float = 0.75f): Int =
        Color.argb(Color.alpha(color), (Color.red(color) * k).toInt(), (Color.green(color) * k).toInt(), (Color.blue(color) * k).toInt())

    fun mix(a: Int, b: Int, t: Float): Int = Color.argb(
        Color.alpha(a),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )

    fun alpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    private fun crown(c: Canvas, cx: Float, top: Float, w: Float) {
        path.reset()
        path.moveTo(cx - w, top + 8f); path.lineTo(cx - w, top); path.lineTo(cx - w / 2, top + 5f)
        path.lineTo(cx, top - 3f); path.lineTo(cx + w / 2, top + 5f); path.lineTo(cx + w, top); path.lineTo(cx + w, top + 8f)
        path.close()
        f.color = col(0xFFFFC107); c.drawPath(path, f)
        circle(c, cx, top + 4f, 1.8f, col(0xFFE53935))
    }

    // ---------- herói / NPC ----------
    /**
     * hat: 0 nada, 1 elmo, 2 chapéu de mago, 3 capuz de caçador, 4 bandana, 5 touca de curandeira,
     * 6 chapéu de mercador, 7 faixa de ferreiro, 8 barba de mestre, 9 laço
     * weapon: 0 adaga, 1 espada, 2 cajado, 3 arco, -1 nenhuma
     */
    fun hero(
        c: Canvas, x: Float, y: Float, face: Float, body: Int, hair: Int, hat: Int, weapon: Int,
        walk: Float, moving: Boolean, atk: Float, sc: Float = 1f, idleBob: Float = 0f, blink: Boolean = false
    ) {
        shadow(c, x, y, 15f * sc)
        c.save()
        c.translate(x, y)
        c.scale(face * sc, sc)
        val sw = if (moving) sin(walk * 12f) * 4f else 0f
        val bob = if (moving) abs(sin(walk * 12f)) * 1.5f else idleBob
        val legC = col(0xFF4E342E)
        rr(c, -8f + sw, -14f, -2f + sw, 0f, 2f, legC)
        rr(c, 2f - sw, -14f, 8f - sw, 0f, 2f, legC)
        c.translate(0f, -bob)

        // braço de trás
        line(c, -8f, -28f, -12f, -18f + sw * 0.5f, 5f, darker(body))
        // corpo
        rr(c, -11f, -33f, 11f, -11f, 6f, body)
        f.color = darker(body, 0.6f); c.drawRect(-11f, -17f, 11f, -14f, f)
        if (hat == 8) tri(c, -12f, -11f, 12f, -11f, 0f, -4f, body) // túnica longa
        // cabeça
        circle(c, 0f, -45f, 13f, SKIN)
        r.set(-14f, -60f, 14f, -34f); f.color = hair; c.drawArc(r, 180f, 180f, true, f)
        rr(c, -14f, -48f, -8f, -36f, 3f, hair)
        tri(c, -2f, -47f, 6f, -47f, 4f, -52f, hair)
        // olhos
        if (blink) {
            line(c, 2.2f, -44f, 5.8f, -44f, 1.4f, DARK); line(c, 8.2f, -44f, 11.8f, -44f, 1.4f, DARK)
        } else {
            circle(c, 4f, -44f, 2.2f, DARK); circle(c, 10f, -44f, 2.2f, DARK)
            circle(c, 4.6f, -44.8f, 0.8f, Color.WHITE); circle(c, 10.6f, -44.8f, 0.8f, Color.WHITE)
        }
        circle(c, 9f, -39f, 2f, col(0x55FF8A80))

        drawHat(c, hat)

        // arma + mão
        heroWeapon(c, weapon, atk)
        c.restore()
    }

    /** Chapéus/acessórios de cabeça no sistema de coordenadas do herói antigo (centro da cabeça em 0,-45). */
    fun drawHat(c: Canvas, hat: Int) {
        when (hat) {
            1 -> { // elmo
                r.set(-15f, -62f, 15f, -38f); f.color = col(0xFF90A4AE); c.drawArc(r, 180f, 180f, true, f)
                f.color = col(0xFF607D8B); c.drawRect(-15f, -51f, 15f, -48f, f)
                tri(c, -3f, -62f, 3f, -62f, -8f, -70f, col(0xFFE53935))
            }
            2 -> { // chapéu pontudo
                oval(c, -19f, -58f, 19f, -50f, col(0xFF4A148C))
                path.reset(); path.moveTo(-12f, -55f); path.quadTo(2f, -72f, -10f, -86f); path.lineTo(12f, -55f); path.close()
                f.color = col(0xFF6A1B9A); c.drawPath(path, f)
                f.color = col(0xFFFFD54F); c.drawRect(-12f, -58f, 12f, -55f, f)
            }
            3 -> { // capuz + pena
                r.set(-15f, -61f, 15f, -36f); f.color = col(0xFF33691E); c.drawArc(r, 170f, 200f, true, f)
                line(c, -6f, -58f, -18f, -74f, 3f, col(0xFFFF7043))
                line(c, -10f, -62f, -16f, -64f, 2f, col(0xFFFFCCBC))
            }
            4 -> { // bandana
                f.color = col(0xFFC62828); c.drawRect(-14f, -53f, 14f, -48f, f)
                tri(c, -14f, -51f, -22f, -56f, -22f, -46f, col(0xFFC62828))
                rr(c, 2f, -47f, 14f, -41f, 2f, col(0x99212121))
            }
            5 -> { // touca
                rr(c, -11f, -63f, 11f, -54f, 3f, Color.WHITE)
                f.color = col(0xFFE53935); c.drawRect(-1.5f, -62f, 1.5f, -55f, f); c.drawRect(-4f, -60f, 4f, -57f, f)
            }
            6 -> { // chapéu largo
                oval(c, -20f, -58f, 20f, -51f, col(0xFF6D4C41))
                rr(c, -11f, -68f, 11f, -54f, 4f, col(0xFF8D6E63))
            }
            7 -> { // faixa + avental
                f.color = col(0xFFFFA000); c.drawRect(-14f, -54f, 14f, -50f, f)
                rr(c, -8f, -30f, 8f, -10f, 3f, col(0xFF795548))
            }
            8 -> { // barba
                path.reset(); path.moveTo(-6f, -38f); path.lineTo(14f, -38f); path.lineTo(6f, -24f); path.close()
                f.color = col(0xFFECEFF1); c.drawPath(path, f)
            }
            9 -> { // laço
                tri(c, -12f, -60f, -4f, -56f, -12f, -52f, col(0xFFE91E63))
                tri(c, 4f, -56f, -4f, -56f, 4f, -60f, col(0xFFE91E63))
            }
        }
    }

    private fun heroWeapon(c: Canvas, weapon: Int, atk: Float) {
        if (weapon == 3) {
            val pull = atk * 6f
            s.strokeWidth = 3f; s.color = col(0xFF6D4C41)
            r.set(4f, -44f, 22f, -4f); c.drawArc(r, -90f, 180f, false, s)
            line(c, 13f, -44f, 13f - pull, -24f, 1f, col(0xFFFFFFFF))
            line(c, 13f - pull, -24f, 13f, -4f, 1f, col(0xFFFFFFFF))
            circle(c, 14f, -24f, 3.5f, SKIN)
        } else if (weapon >= 0) {
            val ang = when (weapon) {
                2 -> if (atk > 0f) 20f + atk * 50f else 15f
                else -> if (atk > 0f) -40f + (1f - atk) * 160f else 30f
            }
            c.save()
            c.translate(9f, -22f)
            c.rotate(ang)
            when (weapon) {
                0 -> {
                    line(c, 0f, 0f, 0f, -17f, 3f, col(0xFFCFD8DC))
                    line(c, -4f, -3f, 4f, -3f, 2.5f, col(0xFF8D6E63))
                }
                1 -> {
                    line(c, 0f, 2f, 0f, -32f, 4f, col(0xFFE0E0E0))
                    line(c, 0f, -4f, 0f, -30f, 1.2f, col(0xFFFFFFFF))
                    line(c, -6f, -4f, 6f, -4f, 3f, col(0xFFFFC107))
                }
                2 -> {
                    line(c, 0f, 14f, 0f, -34f, 3f, col(0xFF8D6E63))
                    circle(c, 0f, -38f, 7f, col(0x6640C4FF))
                    circle(c, 0f, -38f, 4.5f, col(0xFF80D8FF))
                }
            }
            c.restore()
            circle(c, 9f, -22f, 3.5f, SKIN)
        }
    }

    // ---------- monstros ----------
    fun mob(c: Canvas, m: Mob) {
        val d = m.d
        val sc = d.size / 20f
        val floating = d.shape == Shape.GHOST || d.shape == Shape.INSECT
        shadow(c, m.x, m.y, d.size * (if (floating) 0.6f else 0.9f))
        if (d.boss && !outline) {
            s.strokeWidth = 3f
            s.color = alpha(col(0xFFE040FB), (120 + 80 * sin(m.anim * 4f)).toInt())
            r.set(m.x - d.size * 1.2f, m.y - d.size * 0.45f, m.x + d.size * 1.2f, m.y + d.size * 0.45f)
            c.drawOval(r, s)
        }
        c.save()
        // recuo de ~4-6px para longe do herói ao apanhar, com achatar/esticar
        val recoil = if (anim) m.kbDir * m.kb * 2.4f else 0f
        c.translate(m.x + m.face * m.atkAnim.coerceAtLeast(0f) * 8f + recoil, m.y)
        var sqx = 1f
        var sqy = 1f
        if (anim && m.kb > 0f) {
            sqx = 1f + 0.14f * m.kb
            sqy = 1f - 0.12f * m.kb
        }
        if (m.dead) {
            // morte: achata, gira levemente e pisca antes de virar partículas
            val k = (1f - m.dying / DEATH_TIME).coerceIn(0f, 1f)
            sqx = 1f + 0.45f * k
            sqy = 1f - 0.75f * k
            c.rotate(12f * k * m.face)
            if (((m.dying * 20f).toInt() % 2) == 1 && k > 0.3f) {
                c.restore()
                return
            }
        }
        c.scale(m.face * sc * sqx, sc * sqy)
        var c1 = d.c1
        var c2 = d.c2
        if (m.frozen > 0f) { c1 = mix(c1, col(0xFF81D4FA), 0.6f); c2 = mix(c2, col(0xFF81D4FA), 0.6f) }
        if (m.flash > 0f) { c1 = mix(c1, Color.WHITE, 0.6f); c2 = mix(c2, Color.WHITE, 0.6f) }
        if (m.poison > 0f) c1 = mix(c1, col(0xFF9C27B0), 0.25f)
        val a = if (m.frozen > 0f) 0f else m.anim
        when (d.shape) {
            Shape.BLOB -> blob(c, c1, c2, a, d)
            Shape.QUAD -> quad(c, c1, c2, a, d)
            Shape.MUSHROOM -> mushroom(c, c1, c2, a)
            Shape.INSECT -> insect(c, c1, c2, a)
            Shape.SCORPION -> scorpion(c, c1, c2, a)
            Shape.TREE -> tree(c, c1, c2, a)
            Shape.SNAKE -> snake(c, c1, c2, a)
            Shape.HUMANOID -> humanoid(c, c1, c2, a, d)
            Shape.GHOST -> ghost(c, c1, c2, a)
            Shape.DRAGON -> dragon(c, c1, c2, a, d)
        }
        c.restore()
    }

    private fun blob(c: Canvas, c1: Int, c2: Int, a: Float, d: MonsterDef) {
        val sq = sin(a * 5f) * 0.08f
        val w = 20f * (1 + sq)
        val h = 26f * (1 - sq)
        oval(c, -w, -h, w, 0f, c1)
        oval(c, -w, -h * 0.45f, w, 0f, alpha(darker(c1, 0.85f), 140))
        oval(c, -w * 0.6f, -h * 0.9f, -w * 0.1f, -h * 0.6f, col(0x77FFFFFF))
        circle(c, 5f, -h * 0.55f, 2.8f, DARK); circle(c, 13f, -h * 0.55f, 2.8f, DARK)
        circle(c, 5.8f, -h * 0.58f, 1f, Color.WHITE); circle(c, 13.8f, -h * 0.58f, 1f, Color.WHITE)
        s.strokeWidth = 1.6f; s.color = DARK
        r.set(6f, -h * 0.5f, 12f, -h * 0.34f); c.drawArc(r, 20f, 140f, false, s)
        if (d.has(F_CROWN)) crown(c, 0f, -h - 7f, 10f)
        else circle(c, -2f, -h + 1f, 3f, c2)
    }

    private fun quad(c: Canvas, c1: Int, c2: Int, a: Float, d: MonsterDef) {
        val st = sin(a * 10f) * 3f
        val wolf = d.has(F_WOLF)
        val legC = darker(c1, 0.7f)
        val legH = if (wolf) 10f else 6f
        line(c, -12f + st, -10f, -12f + st, -10f + legH + 1f, 4f, legC)
        line(c, -6f - st, -10f, -6f - st, -10f + legH + 1f, 4f, legC)
        line(c, 6f + st, -10f, 6f + st, -10f + legH + 1f, 4f, legC)
        line(c, 12f - st, -10f, 12f - st, -10f + legH + 1f, 4f, legC)
        val by = if (wolf) 0f else 4f
        if (wolf) tri(c, -16f, -20f, -30f, -28f + st, -18f, -13f, c1)
        else circle(c, -18f, -14f + by, 4.5f, Color.WHITE)
        oval(c, -19f, -26f + by, 13f, -6f + by, c1)
        if (wolf) oval(c, -10f, -16f, 8f, -7f, col(0x33FFFFFF))
        circle(c, 14f, -24f + by, 10f, c1)
        if (wolf) {
            tri(c, 7f, -30f, 10f, -43f, 15f, -31f, darker(c1))
            tri(c, 13f, -31f, 18f, -43f, 21f, -29f, darker(c1))
            oval(c, 16f, -26f, 29f, -17f, mix(c1, Color.WHITE, 0.3f))
            circle(c, 28f, -22f, 2.2f, DARK)
            circle(c, 17f, -27f, 2f, if (d.boss) col(0xFFFF1744) else col(0xFFFFEB3B))
            if (d.boss) line(c, -12f, -24f, 6f, -26f, 3f, c2)
        } else {
            oval(c, 5f, -56f + by, 12f, -28f + by, c1); oval(c, 7f, -52f + by, 10f, -32f + by, c2)
            oval(c, 12f, -54f + by, 19f, -28f + by, c1); oval(c, 14f, -50f + by, 17f, -32f + by, c2)
            circle(c, 18f, -25f + by, 2.2f, DARK)
            circle(c, 23f, -21f + by, 1.6f, col(0xFFF06292))
        }
    }

    private fun mushroom(c: Canvas, c1: Int, c2: Int, a: Float) {
        val hop = abs(sin(a * 4f)) * 5f
        c.translate(0f, -hop)
        rr(c, -9f, -20f, 9f, 0f, 4f, c2)
        r.set(-22f, -42f, 22f, -10f); f.color = c1; c.drawArc(r, 180f, 180f, true, f)
        f.color = darker(c1); c.drawRect(-22f, -27f, 22f, -25f, f)
        circle(c, -10f, -33f, 4f, Color.WHITE); circle(c, 4f, -37f, 3.5f, Color.WHITE); circle(c, 13f, -30f, 3f, Color.WHITE)
        circle(c, 1f, -12f, 1.8f, DARK); circle(c, 6f, -12f, 1.8f, DARK)
        c.translate(0f, hop)
    }

    private fun insect(c: Canvas, c1: Int, c2: Int, a: Float) {
        val h = -14f + sin(a * 8f) * 3f
        val flap = sin(a * 45f) * 5f
        oval(c, -12f, -40f + h - flap, 2f, -22f + h, col(0xAAE3F2FD))
        oval(c, -4f, -38f + h - flap, 10f, -22f + h, col(0xAAE3F2FD))
        oval(c, -22f, -24f + h, -2f, -8f + h, c1)
        line(c, -16f, -23f + h, -16f, -9f + h, 3f, c2)
        line(c, -10f, -24f + h, -10f, -8f + h, 3f, c2)
        tri(c, -21f, -18f + h, -30f, -16f + h, -21f, -13f + h, c2)
        circle(c, 3f, -17f + h, 6f, c2)
        circle(c, 12f, -19f + h, 6f, c1)
        circle(c, 15f, -21f + h, 2.2f, DARK)
        line(c, 13f, -24f + h, 18f, -32f + h, 1.5f, c2)
        line(c, 3f, -12f + h, 0f, -4f + h, 1.5f, c2)
        line(c, 7f, -12f + h, 8f, -4f + h, 1.5f, c2)
    }

    private fun scorpion(c: Canvas, c1: Int, c2: Int, a: Float) {
        val st = sin(a * 10f) * 2f
        for (k in 0..2) {
            val lx = -8f + k * 7f
            line(c, lx, -8f, lx - 5f + st, 0f, 2f, darker(c1))
        }
        oval(c, -15f, -16f, 13f, -2f, c1)
        line(c, 10f, -9f, 20f, -12f, 3f, c1)
        circle(c, 23f, -13f, 5f, c1); tri(c, 23f, -14f, 30f, -18f, 28f, -12f, c1)
        line(c, 10f, -6f, 19f, -4f, 3f, c1)
        circle(c, 21f, -4f, 4f, c1)
        val tw = sin(a * 3f) * 2f
        circle(c, -15f, -11f, 5f, c1)
        circle(c, -21f, -19f, 4.5f, c1)
        circle(c, -20f, -29f + tw, 4f, c1)
        circle(c, -12f, -35f + tw, 3.5f, c1)
        tri(c, -9f, -37f + tw, -1f, -33f + tw, -8f, -31f + tw, c2)
        circle(c, 8f, -13f, 1.8f, DARK); circle(c, 11f, -12f, 1.5f, DARK)
    }

    private fun tree(c: Canvas, c1: Int, c2: Int, a: Float) {
        val sway = sin(a * 2f) * 2f
        line(c, -6f, 0f, -12f, 2f, 3f, c2); line(c, 6f, 0f, 12f, 2f, 3f, c2)
        rr(c, -9f, -34f, 9f, 0f, 3f, c2)
        line(c, -8f, -24f, -21f, -35f + sway, 4f, c2)
        line(c, 8f, -24f, 21f, -37f - sway, 4f, c2)
        circle(c, -21f, -38f + sway, 6f, c1); circle(c, 21f, -40f - sway, 6f, c1)
        circle(c, -11f, -40f, 12f, darker(c1, 0.85f))
        circle(c, 11f, -40f, 12f, darker(c1, 0.85f))
        circle(c, sway, -48f, 16f, c1)
        circle(c, -5f + sway, -54f, 5f, col(0x33FFFFFF))
        circle(c, -1f, -22f, 2.2f, col(0xFFFFEB3B)); circle(c, 5f, -22f, 2.2f, col(0xFFFFEB3B))
        line(c, -1f, -14f, 5f, -14f, 1.5f, DARK)
    }

    private fun snake(c: Canvas, c1: Int, c2: Int, a: Float) {
        for (i in 0..6) {
            val x = -22f + i * 6f
            val y = -5f + sin(a * 6f + i * 0.9f) * 3f
            circle(c, x, y, 3.5f + i * 0.5f, if (i % 2 == 0) c1 else darker(c1, 0.88f))
        }
        val hy = -12f + sin(a * 6f + 7f) * 2f
        oval(c, 12f, hy - 7f, 27f, hy + 5f, c1)
        oval(c, 14f, hy, 26f, hy + 5f, c2)
        circle(c, 20f, hy - 3f, 2f, DARK)
        if (sin(a * 9f) > 0.3f) {
            line(c, 27f, hy, 33f, hy + 1f, 1.5f, col(0xFFE53935))
            line(c, 33f, hy + 1f, 35f, hy - 1f, 1.2f, col(0xFFE53935))
        }
    }

    private fun humanoid(c: Canvas, c1: Int, c2: Int, a: Float, d: MonsterDef) {
        val bulky = d.has(F_BULKY)
        val bones = d.has(F_BONES)
        val bw = if (bulky) 15f else 10f
        val st = sin(a * 6f) * 3f
        val legC = if (bones) c1 else darker(c1, 0.8f)
        if (d.has(F_DRESS)) {
            path.reset(); path.moveTo(-7f, -36f); path.lineTo(7f, -36f); path.lineTo(17f, 0f); path.lineTo(-17f, 0f); path.close()
            f.color = c2; c.drawPath(path, f)
            f.color = darker(c2); c.drawRect(-17f, -3f, 17f, 0f, f)
        } else {
            if (bones) {
                line(c, -4f + st, -16f, -5f + st, 0f, 2.5f, legC); line(c, 4f - st, -16f, 5f - st, 0f, 2.5f, legC)
            } else {
                rr(c, -bw + 2f + st * 0.5f, -16f, -2f + st * 0.5f, 0f, 2f, legC)
                rr(c, 2f - st * 0.5f, -16f, bw - 2f - st * 0.5f, 0f, 2f, legC)
            }
            if (bones) {
                line(c, 0f, -34f, 0f, -16f, 2.5f, c1)
                for (k in 0..2) line(c, -6f, -31f + k * 5f, 6f, -31f + k * 5f, 2f, c1)
            } else if (bulky && !d.has(F_FUR) && !d.has(F_HORNS)) {
                rr(c, -bw, -38f, bw, -14f, 2f, c1)
                rr(c, -bw + 4f, -34f, -2f, -24f, 1f, col(0x33FFFFFF))
            } else {
                rr(c, -bw, -36f, bw, -14f, 6f, c1)
            }
        }
        if (d.has(F_BANDAGE)) {
            for (k in 0..3) line(c, -bw + 1f, -33f + k * 6f, bw - 1f, -30f + k * 6f, 1.5f, c2)
        }
        if (d.has(F_FUR)) {
            for (k in 0..4) tri(c, -bw - 3f, -34f + k * 5f, -bw + 2f, -36f + k * 5f, -bw + 2f, -31f + k * 5f, c1)
        }
        // braços
        val armC = if (d.has(F_DRESS)) c1 else if (bones) c1 else darker(c1, 0.9f)
        val aw = if (bulky) 7f else 4.5f
        line(c, -bw, -32f, -bw - 6f, -19f - st * 0.5f, aw, armC)
        line(c, bw, -32f, bw + 7f, -20f + st * 0.5f, aw, armC)
        if (d.has(F_HORNS)) {
            line(c, bw + 7f, -20f, bw + 9f, -50f, 3.5f, col(0xFFB0BEC5))
            line(c, bw + 3f, -22f, bw + 13f, -22f, 3f, c2)
        }
        if (bones && d.has(F_CROWN)) {
            line(c, bw + 7f, -14f, bw + 7f, -56f, 2.5f, col(0xFF6D4C41))
            circle(c, bw + 7f, -58f, 4.5f, col(0xFF76FF03))
        }
        // cabeça
        val hr = if (bulky) 12f else 10f
        if (bulky && !d.has(F_FUR) && !d.has(F_HORNS)) {
            rr(c, -hr, -58f, hr, -38f, 3f, c1)
            circle(c, 3f, -48f, 2.4f, col(0xFF00E5FF)); circle(c, 8f, -48f, 2.4f, col(0xFF00E5FF))
        } else {
            circle(c, 0f, -47f, hr, if (d.has(F_DRESS) && !bones) col(0xFFE1F5FE) else c1)
            when {
                bones -> {
                    circle(c, 3f, -48f, 2.8f, DARK); circle(c, 9f, -48f, 2.8f, DARK)
                    circle(c, 3f, -48f, 1f, col(0xFFFF1744)); circle(c, 9f, -48f, 1f, col(0xFFFF1744))
                    line(c, 2f, -40f, 9f, -40f, 1.5f, DARK)
                }
                d.has(F_FUR) -> {
                    oval(c, 0f, -53f, 13f, -40f, c2)
                    circle(c, 5f, -48f, 1.8f, DARK); circle(c, 10f, -48f, 1.8f, DARK)
                    line(c, 5f, -43f, 11f, -43f, 1.5f, DARK)
                }
                d.has(F_BANDAGE) -> {
                    for (k in 0..2) line(c, -hr + 1f, -53f + k * 5f, hr - 1f, -51f + k * 5f, 1.5f, c2)
                    circle(c, 6f, -47f, 2f, col(0xFFFFEB3B))
                }
                else -> {
                    circle(c, 4f, -48f, 1.8f, DARK); circle(c, 9f, -48f, 1.8f, DARK)
                }
            }
        }
        if (d.has(F_HORNS)) {
            r.set(-hr - 2f, -61f, hr + 2f, -35f); f.color = darker(c1, 0.8f); c.drawArc(r, 180f, 180f, true, f)
            f.color = DARK; c.drawRect(1f, -50f, hr + 1f, -46f, f)
            circle(c, 5f, -48f, 1.6f, col(0xFFFF1744))
            tri(c, -hr, -52f, -hr - 6f, -66f, -hr + 4f, -56f, c2)
            tri(c, hr, -52f, hr + 6f, -66f, hr - 4f, -56f, c2)
        }
        if (d.has(F_CROWN)) crown(c, 0f, -hr - 55f, 9f)
    }

    private fun ghost(c: Canvas, c1: Int, c2: Int, a: Float) {
        val fl = sin(a * 3f) * 4f - 14f
        val body = alpha(c1, 200)
        path.reset()
        path.moveTo(-16f, -36f + fl)
        r.set(-16f, -52f + fl, 16f, -20f + fl)
        path.arcTo(r, 180f, 180f, false)
        path.lineTo(16f, -10f + fl)
        val wv = sin(a * 6f) * 3f
        path.lineTo(10f, -16f + fl + wv); path.lineTo(5f, -8f + fl); path.lineTo(0f, -16f + fl - wv)
        path.lineTo(-5f, -8f + fl); path.lineTo(-10f, -16f + fl + wv); path.lineTo(-16f, -8f + fl)
        path.close()
        f.color = body; c.drawPath(path, f)
        oval(c, 1f, -43f + fl, 7f, -33f + fl, c2)
        oval(c, 9f, -43f + fl, 15f, -33f + fl, c2)
        oval(c, 5f, -29f + fl, 11f, -23f + fl, alpha(c2, 160))
        circle(c, -8f, -42f + fl, 4f, col(0x44FFFFFF))
    }

    private fun dragon(c: Canvas, c1: Int, c2: Int, a: Float, d: MonsterDef) {
        val flap = sin(a * 6f) * 8f
        if (!d.has(F_NOWINGS)) {
            tri(c, -6f, -28f, -22f, -54f + flap, 4f, -40f + flap, darker(c2, 0.7f))
        }
        tri(c, -14f, -20f, -36f, -8f + sin(a * 3f) * 3f, -14f, -11f, c1)
        val st = sin(a * 8f) * 2f
        rr(c, -12f + st, -14f, -6f + st, 0f, 2f, darker(c1))
        rr(c, 4f - st, -14f, 10f - st, 0f, 2f, darker(c1))
        oval(c, -17f, -31f, 15f, -9f, c1)
        oval(c, -8f, -21f, 12f, -9f, c2)
        for (k in 0..2) tri(c, -12f + k * 7f, -29f, -9f + k * 7f, -36f, -6f + k * 7f, -29f, c2)
        line(c, 10f, -26f, 16f, -34f, 8f, c1)
        circle(c, 17f, -36f, 8.5f, c1)
        oval(c, 18f, -38f, 32f, -29f, c1)
        circle(c, 30f, -35f, 1.4f, DARK)
        circle(c, 19f, -39f, 2.4f, col(0xFFFFEB3B)); circle(c, 19.5f, -39f, 1f, DARK)
        line(c, 12f, -42f, 7f, -50f, 2.5f, c2)
        if (d.has(F_HORNS)) {
            line(c, 16f, -43f, 14f, -56f, 3f, c2)
            line(c, 10f, -40f, 2f, -52f, 3f, c2)
        }
        if (!d.has(F_NOWINGS)) {
            tri(c, 0f, -28f, -12f, -60f + flap, 14f, -42f + flap, c2)
        } else {
            if (sin(a * 2f) > 0.6f) {
                circle(c, 36f, -33f, 3f, col(0xCCFF6D00)); circle(c, 41f, -34f, 2f, col(0xCCFFEB3B))
            }
        }
    }

    // ---------- cenário ----------
    fun deco(c: Canvas, d: Deco, t: Float) {
        val x = d.x
        val y = d.y
        val k = d.s
        val sway = if (anim) sin(t * 1.3f + d.seed * 0.37f) * 2.2f * k else 0f
        when (d.kind) {
            0 -> { // árvore
                shadow(c, x, y, 20f * k)
                rr(c, x - 4f * k, y - 28f * k, x + 4f * k, y, 2f, col(0xFF6D4C41))
                val g = if (d.seed % 2 == 0) col(0xFF5E8F3A) else col(0xFF6E9C45)
                circle(c, x - 11f * k + sway * 0.6f, y - 32f * k, 13f * k, darker(g, 0.85f))
                circle(c, x + 11f * k + sway * 0.6f, y - 32f * k, 13f * k, darker(g, 0.85f))
                circle(c, x + sway, y - 42f * k, 17f * k, g)
                circle(c, x - 5f * k + sway, y - 48f * k, 6f * k, col(0x22FFFFFF))
                if (d.seed % 3 == 0) {
                    circle(c, x - 8f * k + sway, y - 38f * k, 2.2f * k, Style.ROOF_RED)
                    circle(c, x + 7f * k + sway, y - 44f * k, 2.2f * k, Style.ROOF_RED)
                }
            }
            1, 11 -> { // pinheiro
                shadow(c, x, y, 16f * k)
                rr(c, x - 3f * k, y - 12f * k, x + 3f * k, y, 1f, col(0xFF5D4037))
                val g = if (d.kind == 1) col(0xFF1B5E20) else col(0xFF2E5F3A)
                for (i in 0..2) {
                    val ty = y - 10f * k - i * 13f * k
                    val w = (18f - i * 4f) * k
                    val sx = sway * (0.4f + i * 0.3f)
                    tri(c, x - w + sx * 0.5f, ty, x + w + sx * 0.5f, ty, x + sx, ty - 22f * k, if (i % 2 == 0) g else darker(g, 0.9f))
                    if (d.kind == 11) tri(c, x - w * 0.5f + sx * 0.7f, ty - 11f * k, x + w * 0.5f + sx * 0.7f, ty - 11f * k, x + sx, ty - 22f * k, col(0xFFF5F5F5))
                }
            }
            2 -> { // pedra
                shadow(c, x, y, 14f * k)
                oval(c, x - 14f * k, y - 16f * k, x + 14f * k, y + 2f, col(0xFF9E9E9E))
                oval(c, x - 9f * k, y - 14f * k, x + 2f * k, y - 8f * k, col(0x44FFFFFF))
            }
            3 -> { // cacto
                shadow(c, x, y, 10f * k)
                val g = col(0xFF558B2F)
                rr(c, x - 5f * k, y - 38f * k, x + 5f * k, y, 5f * k, g)
                rr(c, x - 15f * k, y - 26f * k, x - 10f * k, y - 12f * k, 3f * k, g)
                line(c, x - 12f * k, y - 13f * k, x - 4f * k, y - 13f * k, 4f * k, g)
                rr(c, x + 10f * k, y - 32f * k, x + 15f * k, y - 18f * k, 3f * k, g)
                line(c, x + 12f * k, y - 19f * k, x + 4f * k, y - 19f * k, 4f * k, g)
                if (d.seed % 3 == 0) circle(c, x, y - 39f * k, 3f * k, col(0xFFF06292))
            }
            4 -> { // cristal
                shadow(c, x, y, 12f * k)
                val glow = (180 + 60 * sin(t * 2f + d.seed)).toInt()
                tri(c, x - 7f * k, y, x + 7f * k, y, x, y - 34f * k, alpha(col(0xFF81D4FA), glow))
                tri(c, x - 2f * k, y, x + 12f * k, y, x + 9f * k, y - 20f * k, alpha(col(0xFF4FC3F7), glow))
                tri(c, x - 12f * k, y, x, y, x - 9f * k, y - 16f * k, alpha(col(0xFFB3E5FC), glow))
            }
            5 -> { // pilar quebrado
                shadow(c, x, y, 14f * k)
                f.color = col(0xFF78748A)
                path.reset(); path.moveTo(x - 9f * k, y); path.lineTo(x - 9f * k, y - 40f * k); path.lineTo(x - 2f * k, y - 46f * k)
                path.lineTo(x + 3f * k, y - 38f * k); path.lineTo(x + 9f * k, y - 43f * k); path.lineTo(x + 9f * k, y); path.close()
                c.drawPath(path, f)
                line(c, x - 3f * k, y - 36f * k, x - 3f * k, y - 2f, 1.5f, col(0x33000000))
                line(c, x + 4f * k, y - 34f * k, x + 4f * k, y - 2f, 1.5f, col(0x33000000))
                rr(c, x - 12f * k, y - 5f * k, x + 12f * k, y + 1f, 1f, col(0xFF6A6678))
            }
            6 -> { // lápide
                shadow(c, x, y, 10f * k)
                r.set(x - 8f * k, y - 26f * k, x + 8f * k, y - 10f * k); f.color = col(0xFF8D8A9A); c.drawArc(r, 180f, 180f, true, f)
                f.color = col(0xFF8D8A9A); c.drawRect(x - 8f * k, y - 18f * k, x + 8f * k, y, f)
                line(c, x, y - 21f * k, x, y - 9f * k, 2f, col(0xFF5E5A6E))
                line(c, x - 4f * k, y - 17f * k, x + 4f * k, y - 17f * k, 2f, col(0xFF5E5A6E))
            }
            7 -> { // árvore morta
                shadow(c, x, y, 12f * k)
                val b = col(0xFF4E342E)
                line(c, x, y, x, y - 34f * k, 5f * k, b)
                line(c, x, y - 22f * k, x - 12f * k, y - 34f * k, 3f * k, b)
                line(c, x, y - 28f * k, x + 11f * k, y - 42f * k, 3f * k, b)
                line(c, x - 12f * k, y - 34f * k, x - 16f * k, y - 33f * k, 2f * k, b)
                line(c, x, y - 34f * k, x - 4f * k, y - 44f * k, 2f * k, b)
            }
            8 -> { // casa
                val wallC = if (d.seed % 2 == 0) Style.CREAM else mix(Style.CREAM, Style.SAND, 0.35f)
                val roofC = if (d.seed % 3 == 0) Style.ROOF_BLUE else if (d.seed % 3 == 1) Style.ROOF_RED else Style.WOOD
                shadow(c, x, y, 75f)
                if (anim) {
                    // sombra projetada para trás-direita + alicerce de pedra: a casa "pousa" no chão
                    oval(c, x - 66f, y - 16f, x + 90f, y + 10f, col(0x30000000))
                    rr(c, x - 64f, y - 10f, x + 64f, y + 3f, 2f, darker(Style.STONE, 0.85f))
                    line(c, x - 64f, y - 10f, x + 64f, y - 10f, 1.5f, mix(Style.STONE, Color.WHITE, 0.3f))
                }
                rr(c, x - 60f, y - 70f, x + 60f, y - (if (anim) 8f else 0f), 2f, wallC)
                tri(c, x - 72f, y - 68f, x + 72f, y - 68f, x, y - 120f, roofC)
                f.color = darker(roofC); c.drawRect(x - 72f, y - 70f, x + 72f, y - 64f, f)
                rr(c, x - 12f, y - 38f, x + 12f, y, 3f, col(0xFF6D4C41))
                circle(c, x + 7f, y - 18f, 1.8f, col(0xFFFFC107))
                rr(c, x - 48f, y - 52f, x - 26f, y - 32f, 2f, col(0xFF90CAF9))
                rr(c, x + 26f, y - 52f, x + 48f, y - 32f, 2f, col(0xFF90CAF9))
                line(c, x - 37f, y - 52f, x - 37f, y - 32f, 2f, wallC)
                line(c, x + 37f, y - 52f, x + 37f, y - 32f, 2f, wallC)
            }
            9 -> { // fonte
                shadow(c, x, y + 14f, 84f)
                oval(c, x - 80f, y - 40f, x + 80f, y + 20f, darker(Style.STONE, 0.8f))
                oval(c, x - 80f, y - 44f, x + 80f, y + 14f, Style.STONE)
                oval(c, x - 70f, y - 38f, x + 70f, y + 8f, Style.WATER)
                if (anim) {
                    val gl = (70 + 50 * sin(t * 2.2f)).toInt()
                    oval(c, x - 58f, y - 32f, x + 58f, y + 2f, alpha(Color.WHITE, gl / 2))
                    for (i in 0..2) { // ondinhas
                        val ph = (t * 0.5f + i / 3f) % 1f
                        s.strokeWidth = 1.5f; s.color = alpha(Color.WHITE, ((1 - ph) * 150).toInt())
                        r.set(x - 14f - ph * 50f, y - 22f - ph * 14f, x + 14f + ph * 50f, y - 8f + ph * 10f)
                        c.drawOval(r, s)
                    }
                }
                oval(c, x - 50f, y - 30f, x + 10f, y - 8f, col(0x33FFFFFF))
                rr(c, x - 8f, y - 60f, x + 8f, y - 14f, 3f, mix(Style.STONE, Color.WHITE, 0.25f))
                oval(c, x - 26f, y - 66f, x + 26f, y - 54f, mix(Style.STONE, Color.WHITE, 0.25f))
                oval(c, x - 20f, y - 65f, x + 20f, y - 57f, Style.WATER)
                val n = if (anim) 14 else 6
                for (i in 0 until n) {
                    val ph = (t * 1.4f + i.toFloat() / n) % 1f
                    val ang = i * (6.283f / n)
                    val dx = cos(ang) * 46f * ph
                    val dy = -62f - 70f * ph + 110f * ph * ph + sin(ang) * 10f * ph
                    circle(c, x + dx, y + dy, if (anim) 2.6f * (1.1f - ph * 0.5f) else 3f, col(0xDDE1F5FE))
                }
                if (anim) circle(c, x, y - 70f, 5f + 2f * sin(t * 6f), col(0x99FFFFFF))
            }
            10 -> { // arbusto
                shadow(c, x, y, 14f * k)
                val g = col(0xFF2E7D32)
                circle(c, x - 8f * k, y - 8f * k, 9f * k, darker(g, 0.9f))
                circle(c, x + 8f * k, y - 8f * k, 9f * k, darker(g, 0.9f))
                circle(c, x, y - 14f * k, 11f * k, g)
                if (d.seed % 4 == 0) {
                    circle(c, x - 4f * k, y - 14f * k, 2f * k, col(0xFFFFEB3B))
                    circle(c, x + 6f * k, y - 10f * k, 2f * k, col(0xFFFFEB3B))
                }
            }
            12 -> { // rocha vulcânica
                shadow(c, x, y, 16f * k)
                f.color = col(0xFF2B1D1A)
                path.reset(); path.moveTo(x - 16f * k, y); path.lineTo(x - 10f * k, y - 22f * k); path.lineTo(x + 2f * k, y - 28f * k)
                path.lineTo(x + 14f * k, y - 14f * k); path.lineTo(x + 16f * k, y); path.close()
                c.drawPath(path, f)
                val glow = (150 + 100 * sin(t * 3f + d.seed)).toInt()
                line(c, x - 6f * k, y - 18f * k, x, y - 8f * k, 2f, alpha(col(0xFFFF6D00), glow))
                line(c, x, y - 8f * k, x + 8f * k, y - 12f * k, 2f, alpha(col(0xFFFF6D00), glow))
            }
            13 -> { // ossos
                line(c, x - 8f * k, y, x + 8f * k, y - 3f * k, 2.5f, col(0xFFEFEBE9))
                circle(c, x - 8f * k, y, 2.2f, col(0xFFEFEBE9)); circle(c, x + 8f * k, y - 3f * k, 2.2f, col(0xFFEFEBE9))
                if (d.seed % 2 == 0) circle(c, x + 14f * k, y - 4f * k, 5f * k, col(0xFFEFEBE9))
            }
            20 -> { // flores
                val cs = intArrayOf(col(0xFFFFEB3B), col(0xFFF48FB1), col(0xFFFFFFFF), col(0xFFE1BEE7))
                for (i in 0..2) {
                    val fx = x + (i - 1) * 7f * k
                    val fy = y + ((d.seed + i * 3) % 5) * 1.5f
                    circle(c, fx, fy, 3f * k, cs[(d.seed + i) % cs.size])
                    circle(c, fx, fy, 1.2f * k, col(0xFFFF8F00))
                }
            }
            21 -> { // tufo de grama
                val g = col(0x5525501A)
                line(c, x, y, x - 4f * k, y - 8f * k, 2f, g)
                line(c, x, y, x, y - 10f * k, 2f, g)
                line(c, x, y, x + 4f * k, y - 8f * k, 2f, g)
            }
            22 -> { // rachadura de lava
                val glow = (120 + 100 * sin(t * 2f + d.seed)).toInt()
                val lc = alpha(col(0xFFFF6D00), glow)
                line(c, x - 20f * k, y, x - 6f * k, y - 5f * k, 3f, lc)
                line(c, x - 6f * k, y - 5f * k, x + 4f * k, y + 3f * k, 3f, lc)
                line(c, x + 4f * k, y + 3f * k, x + 20f * k, y - 2f * k, 3f, lc)
            }
            23 -> oval(c, x - 26f * k, y - 7f * k, x + 26f * k, y + 7f * k, col(0x88FFFFFF))
            24 -> oval(c, x - 40f * k, y - 9f * k, x + 40f * k, y + 9f * k, col(0x22FFFFFF))
            25 -> { // pedras do chão
                oval(c, x - 8f * k, y - 4f * k, x + 4f * k, y + 3f * k, col(0x33000000))
                oval(c, x + 6f * k, y - 2f * k, x + 14f * k, y + 4f * k, col(0x26000000))
            }
            26 -> { // cogumelinhos
                for (i in 0..1) {
                    val sx = x + i * 9f * k
                    rr(c, sx - 1.5f, y - 5f * k, sx + 1.5f, y, 1f, col(0xFFFFF3E0))
                    r.set(sx - 4.5f * k, y - 9f * k, sx + 4.5f * k, y - 3f * k); f.color = col(0xFFE53935); c.drawArc(r, 180f, 180f, true, f)
                }
            }
        }
    }

    fun portal(c: Canvas, x: Float, y: Float, t: Float) {
        if (anim) {
            val pulse = 0.5f + 0.5f * sin(t * 3f)
            oval(c, x - 48f - pulse * 8f, y - 20f - pulse * 3f, x + 48f + pulse * 8f, y + 20f + pulse * 3f, alpha(col(0xFF82B1FF), (40 + 50 * pulse).toInt()))
            r.set(x - 10f, y - 70f, x + 10f, y)
            f.color = alpha(col(0xFFB3E5FF), (30 + 40 * pulse).toInt()); c.drawRoundRect(r, 10f, 10f, f)
        }
        oval(c, x - 34f, y - 14f, x + 34f, y + 14f, col(0x553D5AFE))
        for (i in 0..2) {
            val rad = 14f + i * 9f
            s.strokeWidth = 3f
            s.color = alpha(col(0xFF82B1FF), 230 - i * 50)
            r.set(x - rad, y - rad * 0.42f, x + rad, y + rad * 0.42f)
            val st = (t * (140f + i * 60f)) % 360f
            c.drawArc(r, st, 220f, false, s)
        }
        for (i in 0..5) {
            val ph = (t * 0.8f + i / 6f) % 1f
            val ang = i * 1.047f + t
            circle(c, x + cos(ang) * 20f, y - ph * 55f, 2.5f * (1 - ph), col(0xCCB3E5FF))
        }
    }
}

// =====================================================================
// ANIMAÇÃO POR KEYFRAMES (reaproveitável para herói, NPCs e monstros)
// =====================================================================
object Ease {
    const val LINEAR = 0
    const val IN_OUT = 1
    const val OUT = 2
    const val IN = 3

    fun apply(e: Int, t: Float): Float = when (e) {
        IN_OUT -> t * t * (3f - 2f * t)
        OUT -> 1f - (1f - t) * (1f - t)
        IN -> t * t
        else -> t
    }
}

// Canais da pose (graus para rotações, unidades de mundo para deslocamentos)
const val CH_Y = 0        // bob vertical (+ = para baixo)
const val CH_SX = 1       // squash horizontal
const val CH_SY = 2       // stretch vertical
const val CH_LEAN = 3     // inclinação do tronco (+ = para frente)
const val CH_HEAD = 4     // inclinação da cabeça
const val CH_ARM_F = 5    // braço da frente (+ = para frente)
const val CH_ARM_B = 6    // braço de trás
const val CH_HAND_F = 7   // pulso da frente
const val CH_HAND_B = 8   // pulso de trás
const val CH_LEG_F = 9    // perna da frente (+ = para frente)
const val CH_LEG_B = 10   // perna de trás
const val CH_WEAPON = 11  // arma relativa à mão
const val CH_X = 12       // avanço do corpo
const val CH_SIT = 13     // 0..1 sentado (etapa 3)
const val CH_EYES = 14    // 1 = olhos fechados
const val N_CH = 15

/** Uma curva: tempos normalizados (0..1 da duração do clipe) e valores, com easing entre os quadros. */
class Track(val ch: Int, val t: FloatArray, val v: FloatArray, val ease: Int = Ease.IN_OUT) {
    fun sample(u: Float): Float {
        if (u <= t[0]) return v[0]
        for (i in 0 until t.size - 1) {
            if (u <= t[i + 1]) {
                val k = (u - t[i]) / max(1e-4f, t[i + 1] - t[i])
                return v[i] + (v[i + 1] - v[i]) * Ease.apply(ease, k)
            }
        }
        return v[v.size - 1]
    }
}

class Clip(val name: String, val dur: Float, val loop: Boolean, val tracks: Array<Track>)

object Pose {
    val DEFAULT = FloatArray(N_CH).also {
        it[CH_SX] = 1f; it[CH_SY] = 1f; it[CH_ARM_F] = 8f; it[CH_ARM_B] = -6f
    }
}

/** Toca um clipe com transição suave (crossfade) a partir da pose atual. Sem alocação por frame. */
class Animator {
    val pose = FloatArray(N_CH)
    private val from = FloatArray(N_CH)
    private val tmp = FloatArray(N_CH)
    var clip: Clip? = null
        private set
    var time = 0f
        private set
    private var blend = 1f
    private var fade = 0.15f

    init {
        Pose.DEFAULT.copyInto(pose)
    }

    fun play(c: Clip, restart: Boolean = false, fadeTime: Float = 0.15f) {
        if (c === clip && !restart) return
        pose.copyInto(from)
        clip = c
        time = 0f
        fade = fadeTime
        blend = if (fadeTime <= 0f) 1f else 0f
    }

    val finished: Boolean
        get() {
            val c = clip ?: return true
            return !c.loop && time >= c.dur
        }

    fun update(dt: Float) {
        val c = clip ?: return
        time += dt
        val u = if (c.loop) (time % c.dur) / c.dur else min(1f, time / c.dur)
        Pose.DEFAULT.copyInto(tmp)
        for (tr in c.tracks) tmp[tr.ch] = tr.sample(u)
        blend = min(1f, blend + dt / max(0.001f, fade))
        val k = Ease.apply(Ease.IN_OUT, blend)
        for (i in 0 until N_CH) pose[i] = from[i] + (tmp[i] - from[i]) * k
    }
}

object Clips {
    private fun f(vararg v: Float) = v

    /** Parado: respiração (squash/stretch ~2%), bob leve e braços acompanhando. */
    val IDLE = Clip("Parado", 2.6f, true, arrayOf(
        Track(CH_SY, f(0f, 0.5f, 1f), f(1f, 1.02f, 1f)),
        Track(CH_SX, f(0f, 0.5f, 1f), f(1f, 0.99f, 1f)),
        Track(CH_Y, f(0f, 0.5f, 1f), f(0f, -0.8f, 0f)),
        Track(CH_ARM_F, f(0f, 0.5f, 1f), f(8f, 11f, 8f)),
        Track(CH_ARM_B, f(0f, 0.5f, 1f), f(-6f, -9f, -6f)),
        Track(CH_HEAD, f(0f, 0.5f, 1f), f(0f, 1.5f, 0f)),
    ))

    /** Provisório até a etapa 2 (andar completo). */
    val WALK_TMP = Clip("Andar (provisório)", 0.56f, true, arrayOf(
        Track(CH_LEG_F, f(0f, 0.5f, 1f), f(25f, -25f, 25f)),
        Track(CH_LEG_B, f(0f, 0.5f, 1f), f(-25f, 25f, -25f)),
        Track(CH_ARM_F, f(0f, 0.5f, 1f), f(-15f, 25f, -15f)),
        Track(CH_ARM_B, f(0f, 0.5f, 1f), f(20f, -20f, 20f)),
        Track(CH_Y, f(0f, 0.25f, 0.5f, 0.75f, 1f), f(0f, -1.5f, 0f, -1.5f, 0f)),
        Track(CH_LEAN, f(0f, 1f), f(4f, 4f)),
    ))
}

const val DIR_FRONT = 0
const val DIR_BACK = 1
const val DIR_SIDE = 2

/**
 * Herói por partes (cutout) com hierarquia: quadril -> tronco -> (cabeça -> elmo -> pluma) e (ombro -> braço -> mão -> arma);
 * quadril -> pernas. Três vistas (frente, costas, lado espelhado), ordem de desenho por direção,
 * sombreamento em 2 tons com luz vinda de cima à esquerda e brilho no metal.
 */
class HeroRig(private val art: Art) {
    private val path = Path()
    private val rect = RectF()
    private val rnd = kotlin.random.Random(5)
    private var blinkT = 3f
    private var blinking = 0f
    private var plume = 0f
    private var plumeV = 0f
    private var ls = 1f // 1 = luz à esquerda no espaço local (inverte quando o boneco é espelhado)
    private val OX = floatArrayOf(-1f, 1f, 0f, 0f, -0.7f, 0.7f, -0.7f, 0.7f)
    private val OY = floatArrayOf(0f, 0f, -1f, 1f, -0.7f, -0.7f, 0.7f, 0.7f)

    private val SKIN = col(0xFFFFE0BD)
    private val SKIN_SH = col(0xFFEDBF95)
    private val DARK = col(0xFF2A211C)
    private val METAL = col(0xFFD8E0E6)
    private val METAL_SH = col(0xFF8F9BA7)
    private val GOLD = col(0xFFF2C14E)
    private val PANTS = col(0xFF5B4636)
    private val PANTS_SH = col(0xFF443428)
    private val BOOT = col(0xFF7A5434)
    private val BOOT_SH = col(0xFF573A23)

    /** Piscar a cada 3–5s e pluma como mola amortecida (movimento secundário, com atraso). */
    fun update(dt: Float, vxLocal: Float, head: Float, time: Float) {
        blinkT -= dt
        if (blinking > 0f) blinking -= dt
        if (blinkT <= 0f) {
            blinking = 0.12f
            blinkT = 3f + rnd.nextFloat() * 2f
        }
        val target = (-vxLocal * 0.035f).coerceIn(-25f, 25f) + head * 2f + sin(time * 1.7f) * 3f
        plumeV += (60f * (target - plume) - 7f * plumeV) * dt
        plume += plumeV * dt
    }

    fun draw(
        c: Canvas, x: Float, y: Float, dir: Int, face: Float, p: FloatArray, body: Int, hair: Int,
        hat: Int, weapon: Int, sc: Float, outlined: Boolean, time: Float
    ) {
        if (outlined) {
            art.beginOutline()
            for (k in 0 until 8) {
                c.save()
                c.translate(OX[k] * Style.OUTLINE * sc, OY[k] * Style.OUTLINE * sc)
                figure(c, x, y, dir, face, p, body, hair, hat, weapon, sc, time)
                c.restore()
            }
            art.endOutline()
        }
        figure(c, x, y, dir, face, p, body, hair, hat, weapon, sc, time)
    }

    private fun figure(
        c: Canvas, x: Float, y: Float, dir: Int, face: Float, p: FloatArray, body: Int, hair: Int,
        hat: Int, weapon: Int, sc: Float, time: Float
    ) {
        art.shadow(c, x, y, 15f * sc)
        c.save()
        c.translate(x, y)
        val fx = if (dir == DIR_SIDE) face else 1f
        ls = fx
        c.scale(fx * sc, sc)
        c.translate(p[CH_X], p[CH_Y])
        c.scale(p[CH_SX], p[CH_SY])
        if (dir == DIR_SIDE) {
            leg(c, -1.6f, p[CH_LEG_B], true, true, 0f)
            leg(c, 1.6f, p[CH_LEG_F], false, true, 0f)
        } else {
            leg(c, -3.7f, 0f, false, false, max(0f, p[CH_LEG_F]) / 25f * 1.5f)
            leg(c, 3.7f, 0f, false, false, max(0f, p[CH_LEG_B]) / 25f * 1.5f)
        }
        c.save()
        c.translate(0f, -12f)
        c.rotate(if (dir == DIR_SIDE) p[CH_LEAN] else 0f)
        val blink = blinking > 0f || p[CH_EYES] > 0.5f
        when (dir) {
            DIR_SIDE -> {
                arm(c, -2.2f, -15f, -p[CH_ARM_B], -p[CH_HAND_B], body, true, -1, 0f, time)
                torso(c, dir, body)
                head(c, dir, p[CH_HEAD], hair, hat, blink)
                arm(c, 2.2f, -15f, -p[CH_ARM_F], -p[CH_HAND_F], body, false, weapon, 58f + p[CH_WEAPON], time)
            }
            DIR_FRONT -> {
                torso(c, dir, body)
                arm(c, 9.6f, -15f, -(6f + (-p[CH_ARM_B] - 6f) * 0.5f), 0f, body, false, -1, 0f, time)
                arm(c, -9.6f, -15f, 6f + (p[CH_ARM_F] - 8f) * 0.5f, 0f, body, false, weapon, 200f + p[CH_WEAPON], time)
                head(c, dir, p[CH_HEAD], hair, hat, blink)
            }
            else -> {
                arm(c, 9.6f, -15f, -(6f + (p[CH_ARM_F] - 8f) * 0.5f), 0f, body, false, weapon, 160f - p[CH_WEAPON], time)
                arm(c, -9.6f, -15f, 6f + (-p[CH_ARM_B] - 6f) * 0.5f, 0f, body, false, -1, 0f, time)
                torso(c, dir, body)
                head(c, dir, p[CH_HEAD], hair, hat, blink)
            }
        }
        c.restore()
        c.restore()
    }

    // ---------- sombreamento 2 tons (luz de cima à esquerda) ----------
    private fun shadeRR(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, base: Int, sh: Int) {
        art.rr(c, l, t, r, b, rad, sh)
        val w = (r - l) * 0.28f
        val bb = b - (b - t) * 0.1f
        if (ls > 0f) art.rr(c, l, t, r - w, bb, rad, base) else art.rr(c, l + w, t, r, bb, rad, base)
    }

    private fun shadeCircle(c: Canvas, x: Float, y: Float, r: Float, base: Int, sh: Int) {
        art.circle(c, x, y, r, sh)
        art.circle(c, x - ls * r * 0.12f, y - r * 0.12f, r * 0.86f, base)
    }

    // ---------- partes ----------
    private fun leg(c: Canvas, px: Float, rot: Float, back: Boolean, side: Boolean, lift: Float) {
        c.save()
        c.translate(px, -12f - lift)
        if (side) c.rotate(-rot)
        val pants = if (back) art.darker(PANTS, 0.85f) else PANTS
        shadeRR(c, -2.7f, -1f, 2.7f, 8.5f, 2f, pants, PANTS_SH)
        val boot = if (back) art.darker(BOOT, 0.85f) else BOOT
        if (side) shadeRR(c, -3.1f, 7.6f, 4.9f, 12.4f, 2.4f, boot, BOOT_SH)
        else shadeRR(c, -3.3f, 7.6f, 3.3f, 12.4f, 2.4f, boot, BOOT_SH)
        c.restore()
    }

    private fun torso(c: Canvas, dir: Int, body: Int) {
        val w = if (dir == DIR_SIDE) 13f else 17f
        val sh = art.darker(body, 0.75f)
        path.reset()
        path.moveTo(-w / 2, -6f); path.lineTo(w / 2, -6f); path.lineTo(w / 2 + 1.5f, 1.5f); path.lineTo(-w / 2 - 1.5f, 1.5f); path.close()
        art.f.color = sh
        c.drawPath(path, art.f)
        shadeRR(c, -w / 2, -18.5f, w / 2, -1f, 5f, body, sh)
        art.rr(c, -w / 2, -5.5f, w / 2, -2.6f, 1f, art.darker(body, 0.45f))
        if (dir != DIR_BACK) {
            val bx = if (dir == DIR_SIDE) 2.5f else 0f
            art.rr(c, bx - 1.7f, -5.9f, bx + 1.7f, -2.2f, 0.6f, GOLD)
        }
        if (dir == DIR_FRONT) art.tri(c, -3.5f, -18.5f, 3.5f, -18.5f, 0f, -14.5f, art.darker(body, 0.6f))
        val lx = if (ls > 0f) -w / 2 + 1.6f else w / 2 - 4.6f
        art.rr(c, lx, -16.8f, lx + 3f, -9f, 1.5f, art.alpha(Color.WHITE, 55))
    }

    private fun arm(
        c: Canvas, sx: Float, sy: Float, ang: Float, handAng: Float, body: Int, back: Boolean,
        weapon: Int, wAng: Float, time: Float
    ) {
        c.save()
        c.translate(sx, sy)
        c.rotate(ang)
        val sleeve = if (back) art.darker(body, 0.72f) else body
        shadeRR(c, -2.6f, -1.8f, 2.6f, 8.8f, 2.6f, sleeve, art.darker(sleeve, 0.75f))
        c.translate(0f, 9.2f)
        c.rotate(handAng)
        if (weapon >= 0) {
            c.save()
            c.rotate(wAng)
            weaponShape(c, weapon, time)
            c.restore()
        }
        shadeCircle(c, 0f, 0f, 3.2f, if (back) art.darker(SKIN, 0.9f) else SKIN, SKIN_SH)
        c.restore()
    }

    /** Arma no sistema da mão: lâmina ao longo de -y. */
    private fun weaponShape(c: Canvas, w: Int, time: Float) {
        when (w) {
            0, 1 -> {
                val len = if (w == 1) 30f else 16f
                val bw = if (w == 1) 2.6f else 1.9f
                art.rr(c, -1.3f, -1f, 1.3f, 5f, 0.8f, col(0xFF5D4037))
                art.circle(c, 0f, 5.8f, 1.7f, GOLD)
                val gw = if (w == 1) 5.5f else 4f
                art.rr(c, -gw, -2.7f, gw, -0.7f, 0.8f, GOLD)
                path.reset()
                path.moveTo(-bw, -2.7f); path.lineTo(bw, -2.7f); path.lineTo(bw * 0.8f, -len + 3f)
                path.lineTo(0f, -len); path.lineTo(-bw * 0.8f, -len + 3f); path.close()
                art.f.color = METAL_SH
                c.drawPath(path, art.f)
                path.reset()
                path.moveTo(0f, -2.7f); path.lineTo(-ls * bw, -2.7f); path.lineTo(-ls * bw * 0.8f, -len + 3f)
                path.lineTo(0f, -len); path.close()
                art.f.color = METAL
                c.drawPath(path, art.f)
                art.line(c, -ls * bw * 0.45f, -4f, -ls * bw * 0.3f, -len + 4f, 0.8f, art.alpha(Color.WHITE, 220))
                if (!art.isOutline) {
                    val tw = 0.5f + 0.5f * sin(time * 3f)
                    val s = 1.2f + tw * 2.6f
                    val gx = -ls * bw * 0.35f
                    val gy = -len + 6f
                    art.line(c, gx - s, gy, gx + s, gy, 0.8f, art.alpha(Color.WHITE, (120 + tw * 130).toInt()))
                    art.line(c, gx, gy - s, gx, gy + s, 0.8f, art.alpha(Color.WHITE, (120 + tw * 130).toInt()))
                }
            }
            2 -> {
                art.line(c, 0f, 12f, 0f, -32f, 2.8f, col(0xFF6D4C41))
                art.line(c, -0.6f * ls, 10f, -0.6f * ls, -30f, 0.8f, col(0xFFA1887F))
                if (!art.isOutline) art.circle(c, 0f, -36f, 6.5f + sin(time * 4f), col(0x5540C4FF))
                art.circle(c, 0f, -36f, 4.2f, col(0xFF4FC3F7))
                art.circle(c, -1.4f * ls, -37.4f, 1.3f, Color.WHITE)
            }
            3 -> {
                art.s.strokeWidth = 2.4f; art.s.color = col(0xFF6D4C41)
                rect.set(-4f, -20f, 8f, 20f)
                c.drawArc(rect, -90f, 180f, false, art.s)
                art.line(c, 2f, -20f, 2f, 20f, 0.7f, Color.WHITE)
            }
        }
    }

    private fun eye(c: Canvas, cx: Float, cy: Float, blink: Boolean) {
        if (blink) art.line(c, cx - 1.8f, cy, cx + 1.8f, cy, 1.1f, DARK)
        else {
            art.oval(c, cx - 1.7f, cy - 2.6f, cx + 1.7f, cy + 2.6f, DARK)
            art.circle(c, cx - 0.6f, cy - 1.2f, 0.75f, Color.WHITE)
        }
    }

    private fun head(c: Canvas, dir: Int, rot: Float, hair: Int, hat: Int, blink: Boolean) {
        c.save()
        c.translate(0f, -18.5f)
        c.rotate(rot)
        val hs = art.darker(hair, 0.75f)
        if (dir == DIR_BACK) {
            shadeCircle(c, 0f, -12f, 13f, hair, hs)
            art.rr(c, -9f, -6f, 9f, 0.5f, 3f, hs)
        } else {
            if (dir == DIR_SIDE) shadeRR(c, -13.8f, -18f, -5f, -3.5f, 3.5f, hair, hs)
            else {
                art.rr(c, -14f, -17f, -9.5f, -4f, 3f, hs)
                art.rr(c, 9.5f, -17f, 14f, -4f, 3f, hs)
            }
            shadeCircle(c, 0f, -12f, 13f, SKIN, SKIN_SH)
            rect.set(-13.3f, -25.5f, 13.3f, -10.5f)
            art.f.color = hair
            c.drawArc(rect, 180f, 180f, true, art.f)
            val blush = art.alpha(col(0xFFFF8A80), 90)
            if (dir == DIR_SIDE) {
                art.tri(c, 4f, -18.2f, 13f, -18.2f, 11f, -13.8f, hair)
                eye(c, 4.8f, -11.2f, blink)
                eye(c, 10f, -11.2f, blink)
                art.line(c, 7.6f, -5.8f, 9.6f, -6f, 1f, art.darker(SKIN_SH, 0.8f))
                art.oval(c, 9.4f, -9f, 12.4f, -7f, blush)
            } else {
                art.tri(c, -6f, -18.2f, 2f, -18.2f, -3f, -14.2f, hair)
                art.tri(c, 1f, -18.2f, 9f, -18.2f, 6f, -14.6f, hair)
                eye(c, -4.6f, -11.2f, blink)
                eye(c, 4.6f, -11.2f, blink)
                art.line(c, -1.2f, -5.8f, 1.2f, -5.8f, 1f, art.darker(SKIN_SH, 0.8f))
                art.oval(c, -9.5f, -8.8f, -6.5f, -6.8f, blush)
                art.oval(c, 6.5f, -8.8f, 9.5f, -6.8f, blush)
            }
        }
        when (hat) {
            0 -> {}
            1, 10 -> helmet(c, dir, hat == 1)
            else -> {
                c.save()
                c.translate(0f, 33f)
                art.drawHat(c, hat)
                c.restore()
            }
        }
        c.restore()
    }

    private fun helmet(c: Canvas, dir: Int, big: Boolean) {
        val px = if (dir == DIR_SIDE) -2.5f else 0f
        // pluma (atrás da cúpula), presa no topo do elmo
        c.save()
        c.translate(px, -26.5f)
        c.rotate(if (dir == DIR_SIDE) plume else plume * 0.5f)
        if (big) c.scale(1.15f, 1.15f)
        val pc = if (big) col(0xFFE53935) else col(0xFF42A5F5)
        val psh = art.darker(pc, 0.7f)
        path.reset()
        if (dir == DIR_SIDE) {
            path.moveTo(0f, 0f)
            path.cubicTo(-1f, -9f, -11f, -14f, -19f, -8.5f)
            path.cubicTo(-13f, -9.5f, -5f, -5f, 0f, 0f)
            path.close()
            art.f.color = pc; c.drawPath(path, art.f)
            path.reset()
            path.moveTo(0f, 0f)
            path.cubicTo(-5f, -5f, -12f, -8f, -19f, -8.5f)
            path.cubicTo(-12f, -6.5f, -5f, -3.5f, 0f, 0f)
            path.close()
            art.f.color = psh; c.drawPath(path, art.f)
        } else {
            path.moveTo(0f, 0f)
            path.cubicTo(-6f, -5f, -5f, -15f, 0f, -19.5f)
            path.cubicTo(5f, -15f, 6f, -5f, 0f, 0f)
            path.close()
            art.f.color = pc; c.drawPath(path, art.f)
            path.reset()
            path.moveTo(0f, 0f)
            path.cubicTo(2f, -6f, 2.5f, -14f, 0f, -19.5f)
            path.cubicTo(5f, -15f, 6f, -5f, 0f, 0f)
            path.close()
            art.f.color = psh; c.drawPath(path, art.f)
        }
        c.restore()
        // cúpula em 2 tons
        rect.set(-14.6f, -27.8f, 14.6f, -4f)
        art.f.color = METAL_SH
        c.drawArc(rect, 180f, 180f, true, art.f)
        rect.set(-14.6f + (if (ls > 0f) 0f else 2.4f), -27.8f, 14.6f - (if (ls > 0f) 2.4f else 0f), -6.5f)
        art.f.color = METAL
        c.drawArc(rect, 180f, 180f, true, art.f)
        // faixa e rebites
        art.rr(c, -15.2f, -17.2f, 15.2f, -13.6f, 1.6f, METAL_SH)
        art.rr(c, -15.2f, -17.2f, 15.2f, -15.8f, 1f, art.mix(METAL, Color.WHITE, 0.25f))
        val rv = if (dir == DIR_SIDE) 2 else 3
        for (i in -rv..rv) if (i != 0 || dir != DIR_SIDE) art.circle(c, i * 4.6f, -15.4f, 0.75f, art.darker(METAL_SH, 0.8f))
        if (big) {
            if (dir == DIR_SIDE) art.rr(c, 3f, -15f, 9f, -7.5f, 2f, METAL_SH)
            else if (dir == DIR_FRONT) {
                art.rr(c, -14.5f, -15f, -10.5f, -7f, 1.5f, METAL_SH)
                art.rr(c, 10.5f, -15f, 14.5f, -7f, 1.5f, METAL_SH)
            }
        }
        // brilho do metal (cima-esquerda)
        art.s.strokeWidth = 1.8f
        art.s.color = art.alpha(Color.WHITE, 210)
        rect.set(-11f, -25f, 11f, -8f)
        c.drawArc(rect, if (ls > 0f) 200f else 302f, 38f, false, art.s)
        art.circle(c, -ls * 6.5f, -22.5f, 1.2f, Color.WHITE)
        art.circle(c, px, -27.6f, 2.1f, GOLD)
    }
}

// =====================================================================
// GUERREIRO EM SPRITES (8 direções) com animação por transformações do sprite inteiro
// =====================================================================
object SpriteClips {
    private fun f(vararg v: Float) = v

    /** Respiração (~2%) e bob leve. */
    val IDLE = Clip("Parado", 2.6f, true, arrayOf(
        Track(CH_SY, f(0f, 0.5f, 1f), f(1f, 1.02f, 1f)),
        Track(CH_SX, f(0f, 0.5f, 1f), f(1f, 0.99f, 1f)),
        Track(CH_Y, f(0f, 0.5f, 1f), f(0f, -0.8f, 0f)),
    ))

    /** Quique 2x por ciclo, inclinação ±4° alternando e achatada ao pisar (0 e 0.5 = pisadas). */
    val WALK = Clip("Andar", 0.6f, true, arrayOf(
        Track(CH_Y, f(0f, 0.25f, 0.5f, 0.75f, 1f), f(0f, -3.2f, 0f, -3.2f, 0f)),
        Track(CH_SY, f(0f, 0.07f, 0.25f, 0.5f, 0.57f, 0.75f, 1f), f(0.95f, 1f, 1.025f, 0.95f, 1f, 1.025f, 0.95f)),
        Track(CH_SX, f(0f, 0.07f, 0.25f, 0.5f, 0.57f, 0.75f, 1f), f(1.04f, 1f, 0.99f, 1.04f, 1f, 0.99f, 1.04f)),
        Track(CH_HEAD, f(0f, 0.5f, 1f), f(-4f, 4f, -4f)),
    ))

    /**
     * Preparação 0–120ms (recua para longe do alvo e achata), impacto 120–200ms (avança na direção do alvo esticando),
     * recuperação 200–350ms voltando ao idle. O hit stop de 60ms entra no quadro de impacto (0,2s).
     */
    val ATTACK = Clip("Ataque", 1.0f, false, arrayOf(
        // 0–0,40 preparação (inclina para trás, ergue a espada atrás da cabeça) · 0,40–0,50 golpe ·
        // 0,50–0,80 segura a pose agachada com a espada estendida · 0,80–1,0 volta ao idle
        Track(CH_HAND_F, f(0f, 0.40f, 0.50f, 0.80f, 1f), f(0f, 150f, -80f, -72f, 0f)),   // braço da espada (graus)
        Track(CH_LEG_F, f(0f, 0.40f, 0.50f, 0.80f, 1f), f(0f, 6f, -24f, -22f, 0f)),
        Track(CH_LEG_B, f(0f, 0.40f, 0.50f, 0.80f, 1f), f(0f, -6f, 18f, 16f, 0f)),
        Track(CH_SIT, f(0f, 0.40f, 0.50f, 0.80f, 1f), f(0f, 2f, 7f, 6f, 0f)),            // agachar (px do sprite)
        Track(CH_X, f(0f, 0.40f, 0.50f, 0.80f, 1f), f(0f, -3f, 10f, 9f, 0f)),
        Track(CH_LEAN, f(0f, 0.40f, 0.50f, 0.80f, 1f), f(0f, -10f, 12f, 10f, 0f)),
        Track(CH_SX, f(0f, 0.40f, 0.50f, 0.80f, 1f), f(1f, 1.03f, 1.06f, 1.04f, 1f)),
        Track(CH_SY, f(0f, 0.40f, 0.50f, 0.80f, 1f), f(1f, 0.97f, 0.93f, 0.95f, 1f)),
    ))

    /**
     * Mago, ataque básico (mesma linha do tempo do ATTACK: impacto em 0,5): ergue o cajado (0–0,40) e aponta para o
     * alvo (0,40–0,50; o projétil sai aqui), segura e volta. CH_WEAPON gira o cajado na mão (cajado = braço + arma).
     */
    val ATTACK_STAFF = Clip("Ataque (cajado)", 1.0f, false, arrayOf(
        Track(CH_HAND_F, f(0f, 0.40f, 0.50f, 0.80f, 1f), f(0f, -120f, -75f, -70f, 0f)),
        Track(CH_WEAPON, f(0f, 0.40f, 0.50f, 0.80f, 1f), f(0f, 150f, 165f, 160f, 0f)),
        Track(CH_LEAN, f(0f, 0.40f, 0.50f, 0.80f, 1f), f(0f, -6f, 8f, 6f, 0f)),
        Track(CH_X, f(0f, 0.40f, 0.50f, 0.80f, 1f), f(0f, -2f, 6f, 5f, 0f)),
        Track(CH_SIT, f(0f, 0.40f, 0.50f, 0.80f, 1f), f(0f, 1f, 4f, 3f, 0f)),
    ))

    // ---------- habilidades (impacto/soltura no tempo indicado; Game aplica o dano no mesmo instante) ----------

    /** Golpe Brutal (0,9s, impacto 0,42s): pulinho erguendo a espada bem alto e pancada de cima para baixo. */
    val GOLPE_BRUTAL = Clip("Golpe Brutal", 0.9f, false, arrayOf(
        Track(CH_HAND_F, f(0f, 0.36f, 0.47f, 0.75f, 1f), f(0f, 175f, -95f, -88f, 0f)),
        Track(CH_Y, f(0f, 0.36f, 0.47f, 1f), f(0f, -10f, 0f, 0f)),
        Track(CH_SIT, f(0f, 0.36f, 0.47f, 0.75f, 1f), f(0f, 1f, 10f, 8f, 0f)),
        Track(CH_LEAN, f(0f, 0.36f, 0.47f, 0.75f, 1f), f(0f, -14f, 16f, 12f, 0f)),
        Track(CH_X, f(0f, 0.36f, 0.47f, 0.75f, 1f), f(0f, -4f, 14f, 12f, 0f)),
        Track(CH_LEG_F, f(0f, 0.36f, 0.47f, 0.75f, 1f), f(0f, 8f, -28f, -25f, 0f)),
        Track(CH_LEG_B, f(0f, 0.36f, 0.47f, 0.75f, 1f), f(0f, -8f, 20f, 18f, 0f)),
        Track(CH_SX, f(0f, 0.36f, 0.47f, 0.75f, 1f), f(1f, 1.02f, 1.08f, 1.05f, 1f)),
        Track(CH_SY, f(0f, 0.36f, 0.47f, 0.75f, 1f), f(1f, 0.98f, 0.9f, 0.94f, 1f)),
    ))

    /** Impacto Sísmico (0,8s, impacto 0,48s): agacha, salta com a espada no alto e cai cravando no chão. */
    val SISMICO = Clip("Impacto Sísmico", 0.8f, false, arrayOf(
        Track(CH_SIT, f(0f, 0.22f, 0.5f, 0.6f, 0.8f, 1f), f(0f, 8f, 0f, 12f, 10f, 0f)),
        Track(CH_Y, f(0f, 0.22f, 0.5f, 0.6f, 1f), f(0f, 0f, -34f, 0f, 0f)),
        Track(CH_HAND_F, f(0f, 0.22f, 0.5f, 0.6f, 0.8f, 1f), f(0f, 20f, 170f, -100f, -95f, 0f)),
        Track(CH_LEG_F, f(0f, 0.22f, 0.5f, 0.6f, 0.8f, 1f), f(0f, 0f, 18f, -20f, -18f, 0f)),
        Track(CH_LEG_B, f(0f, 0.22f, 0.5f, 0.6f, 0.8f, 1f), f(0f, 0f, -14f, 16f, 14f, 0f)),
        Track(CH_SY, f(0f, 0.22f, 0.5f, 0.6f, 0.8f, 1f), f(1f, 0.92f, 1.08f, 0.88f, 0.92f, 1f)),
        Track(CH_SX, f(0f, 0.22f, 0.5f, 0.6f, 0.8f, 1f), f(1f, 1.06f, 0.95f, 1.1f, 1.06f, 1f)),
    ))

    /** Fúria (0,7s, explosão 0,30s): encolhe concentrando e se ergue com a espada para o alto. */
    val FURIA = Clip("Fúria", 0.7f, false, arrayOf(
        Track(CH_SIT, f(0f, 0.3f, 0.43f, 1f), f(0f, 7f, 0f, 0f)),
        Track(CH_SY, f(0f, 0.3f, 0.43f, 0.7f, 1f), f(1f, 0.9f, 1.1f, 1.04f, 1f)),
        Track(CH_SX, f(0f, 0.3f, 0.43f, 0.7f, 1f), f(1f, 1.06f, 0.96f, 1f, 1f)),
        Track(CH_HAND_F, f(0f, 0.3f, 0.43f, 0.7f, 1f), f(0f, -30f, 160f, 150f, 0f)),
        Track(CH_HEAD, f(0f, 0.3f, 0.43f, 0.7f, 1f), f(0f, 6f, -10f, -8f, 0f)),
        Track(CH_Y, f(0f, 0.3f, 0.43f, 0.7f, 1f), f(0f, 0f, -6f, -3f, 0f)),
    ))

    /** Seta de Fogo (0,7s, disparo 0,36s): ergue o cajado carregando fogo e aponta para o alvo. */
    val SETA_FOGO = Clip("Seta de Fogo", 0.7f, false, arrayOf(
        Track(CH_HAND_F, f(0f, 0.43f, 0.52f, 0.8f, 1f), f(0f, -150f, -80f, -78f, 0f)),
        Track(CH_WEAPON, f(0f, 0.43f, 0.52f, 0.8f, 1f), f(0f, 150f, 170f, 168f, 0f)),
        Track(CH_LEAN, f(0f, 0.43f, 0.52f, 0.8f, 1f), f(0f, -8f, 10f, 8f, 0f)),
        Track(CH_X, f(0f, 0.43f, 0.52f, 0.8f, 1f), f(0f, -3f, 8f, 7f, 0f)),
        Track(CH_SY, f(0f, 0.43f, 0.52f, 0.8f, 1f), f(1f, 1.03f, 0.96f, 0.97f, 1f)),
    ))

    /** Tempestade de Raios (1,0s, raios de 0,35s a 0,8s): cajado erguido acima da cabeça, olhando para o céu. */
    val TEMPESTADE = Clip("Tempestade de Raios", 1.0f, false, arrayOf(
        Track(CH_HAND_F, f(0f, 0.3f, 0.8f, 1f), f(0f, -170f, -170f, 0f)),
        Track(CH_WEAPON, f(0f, 0.3f, 0.8f, 1f), f(0f, 170f, 170f, 0f)),
        Track(CH_Y, f(0f, 0.3f, 0.8f, 1f), f(0f, -6f, -6f, 0f)),
        Track(CH_SY, f(0f, 0.3f, 0.8f, 1f), f(1f, 1.04f, 1.04f, 1f)),
        Track(CH_HEAD, f(0f, 0.3f, 0.8f, 1f), f(0f, -6f, -6f, 0f)),
    ))

    /** Nova Gélida (0,8s, impacto 0,40s): ergue o cajado e crava a ponta no chão à frente. */
    val NOVA = Clip("Nova Gélida", 0.8f, false, arrayOf(
        Track(CH_HAND_F, f(0f, 0.38f, 0.5f, 0.75f, 1f), f(0f, -160f, -60f, -60f, 0f)),
        Track(CH_WEAPON, f(0f, 0.38f, 0.5f, 0.75f, 1f), f(0f, 160f, 60f, 60f, 0f)),
        Track(CH_SIT, f(0f, 0.38f, 0.5f, 0.75f, 1f), f(0f, 0f, 10f, 8f, 0f)),
        Track(CH_Y, f(0f, 0.38f, 0.5f, 1f), f(0f, -8f, 0f, 0f)),
        Track(CH_LEAN, f(0f, 0.38f, 0.5f, 0.75f, 1f), f(0f, -6f, 10f, 8f, 0f)),
    ))

    /** Clipe de cada habilidade (null = conjurar genérico) e o instante em que ela solta/acerta (s). */
    val SKILL = arrayOfNulls<Clip>(13).also {
        it[1] = GOLPE_BRUTAL; it[2] = SISMICO; it[3] = FURIA; it[4] = SETA_FOGO; it[5] = TEMPESTADE; it[6] = NOVA
    }
    val SKILL_HIT = floatArrayOf(0f, 0.42f, 0.48f, 0.30f, 0.36f, 0.35f, 0.40f, 0f, 0f, 0f, 0f, 0f, 0f)
    /** Brilho na ponta do cajado durante a carga (por habilidade; 0 = sem brilho). */
    val SKILL_GLOW = intArrayOf(0, 0, 0, 0, col(0xFFFF7043), col(0xFFFFEB3B), col(0xFF80DEEA), 0, 0, 0, 0, 0, 0)

    /** Recuo para trás com leve achatada (o flash e a tremida são procedurais). */
    val HURT = Clip("Dano", 0.32f, false, arrayOf(
        Track(CH_X, f(0f, 0.2f, 1f), f(0f, -7f, 0f)),
        Track(CH_LEAN, f(0f, 0.2f, 1f), f(0f, -6f, 0f)),
        Track(CH_SX, f(0f, 0.2f, 1f), f(1f, 1.05f, 1f)),
        Track(CH_SY, f(0f, 0.2f, 1f), f(1f, 0.95f, 1f)),
    ))

    /** Sobe levemente enquanto o círculo brilha no chão. */
    val CAST = Clip("Conjurar", 0.8f, false, arrayOf(
        Track(CH_Y, f(0f, 0.3f, 0.75f, 1f), f(0f, -7f, -7f, 0f)),
        Track(CH_SY, f(0f, 0.3f, 0.75f, 1f), f(1f, 1.04f, 1.04f, 1f)),
        Track(CH_SX, f(0f, 0.3f, 0.75f, 1f), f(1f, 0.98f, 0.98f, 1f)),
    ))
}

/**
 * Navegação: A* numa grade de 10px sobre um mapa (w x h); depois corta caminho onde a linha reta é livre.
 * free(x,y) diz se o herói cabe ali (já com a folga desejada).
 */
class NavGrid(private val W: Int, private val H: Int, private val free: (Float, Float) -> Boolean) {
    private companion object { const val NC = 10 }
    private val GW = W / NC + 1
    private val GH = H / NC + 1
    private val freeCell = BooleanArray(GW * GH)
    private val gCost = FloatArray(GW * GH)
    private val came = IntArray(GW * GH)
    private val seen = IntArray(GW * GH)
    private var stamp = 0
    private val hk = IntArray(GW * GH * 4)
    private val hp = FloatArray(GW * GH * 4)
    private var hn = 0
    private val cells = IntArray(GW * GH)
    private val NBX = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
    private val NBY = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

    /** Monta a grade (chamar depois que o mapa e a colisão estiverem carregados). */
    fun build() {
        for (cy in 0 until GH) for (cx in 0 until GW) {
            val x = cx * NC + NC / 2f
            val y = cy * NC + NC / 2f
            freeCell[cy * GW + cx] = x < W && y < H && free(x, y)
        }
    }

    private fun cellFree(x: Float, y: Float): Boolean {
        val cx = (x / NC).toInt()
        val cy = (y / NC).toInt()
        return cx in 0 until GW && cy in 0 until GH && freeCell[cy * GW + cx]
    }

    /** Linha reta livre? Ignora os primeiros 12px (o herói pode estar encostado num obstáculo). */
    fun lineClear(x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
        val d = kotlin.math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))
        var s = 12f
        while (s < d) {
            val k = s / d
            if (!cellFree(x0 + (x1 - x0) * k, y0 + (y1 - y0) * k)) return false
            s += 5f
        }
        return true
    }

    /** Célula livre mais próxima (até 6 células de distância), -1 se não houver. */
    private fun nearestFree(x: Float, y: Float): Int {
        val cx = (x / NC).toInt().coerceIn(0, GW - 1)
        val cy = (y / NC).toInt().coerceIn(0, GH - 1)
        for (r in 0..6) for (dy in -r..r) for (dx in -r..r) {
            if (max(abs(dx), abs(dy)) != r) continue
            val nx = cx + dx
            val ny = cy + dy
            if (nx in 0 until GW && ny in 0 until GH && freeCell[ny * GW + nx]) return ny * GW + nx
        }
        return -1
    }

    private fun push(c: Int, f: Float) {
        var i = hn++
        while (i > 0) {
            val p = (i - 1) / 2
            if (hp[p] <= f) break
            hk[i] = hk[p]; hp[i] = hp[p]; i = p
        }
        hk[i] = c; hp[i] = f
    }

    private fun pop(): Int {
        val top = hk[0]
        hn--
        val c = hk[hn]
        val f = hp[hn]
        var i = 0
        while (true) {
            var m = i * 2 + 1
            if (m >= hn) break
            if (m + 1 < hn && hp[m + 1] < hp[m]) m++
            if (hp[m] >= f) break
            hk[i] = hk[m]; hp[i] = hp[m]; i = m
        }
        hk[i] = c; hp[i] = f
        return top
    }

    /**
     * Caminho de (sx,sy) até (tx,ty) contornando obstáculos: grava os pontos (x,y) em out e devolve quantos são;
     * 0 = sem saída. O último ponto é o próprio destino (ou o ponto livre mais perto dele).
     * Destino isolado (monstro entre troncos): vai até o ponto alcançável mais perto dele e marca [partial].
     */
    fun path(sx: Float, sy: Float, tx: Float, ty: Float, out: FloatArray): Int {
        partial = false
        val s = nearestFree(sx, sy)
        var g = nearestFree(tx, ty)
        if (s < 0) return 0
        stamp++
        hn = 0
        gCost[s] = 0f; came[s] = -1; seen[s] = stamp
        val gx = if (g >= 0) g % GW else (tx / NC).toInt().coerceIn(0, GW - 1)
        val gy = if (g >= 0) g / GW else (ty / NC).toInt().coerceIn(0, GH - 1)
        var best = s
        var bestH = Float.MAX_VALUE
        push(s, 0f)
        var found = false
        while (hn > 0 && hn < hk.size - 8) {
            val c = pop()
            if (c == g) { found = true; break }
            if (seen[c] == -stamp) continue // já fechada
            seen[c] = -stamp
            val cx = c % GW
            val cy = c / GW
            val hx = abs(cx - gx).toFloat()
            val hy = abs(cy - gy).toFloat()
            val h = max(hx, hy) + 0.414f * min(hx, hy)
            if (h < bestH) { bestH = h; best = c }
            for (k in 0 until 8) {
                val nx = cx + NBX[k]
                val ny = cy + NBY[k]
                if (nx !in 0 until GW || ny !in 0 until GH) continue
                val n = ny * GW + nx
                if (!freeCell[n] || seen[n] == -stamp) continue
                // diagonal só se as duas laterais estiverem livres (não corta quina de tronco)
                if (k >= 4 && (!freeCell[cy * GW + nx] || !freeCell[ny * GW + cx])) continue
                val ng = gCost[c] + (if (k >= 4) 1.414f else 1f)
                if (seen[n] == stamp && ng >= gCost[n]) continue
                seen[n] = stamp; gCost[n] = ng; came[n] = c
                val ddx = abs(nx - gx).toFloat()
                val ddy = abs(ny - gy).toFloat()
                push(n, ng + max(ddx, ddy) + 0.414f * min(ddx, ddy))
            }
        }
        if (!found) {
            if (best == s) return 0
            g = best
            partial = true
        }
        var nc = 0
        var c = g
        while (c >= 0 && nc < cells.size) { cells[nc++] = c; c = came[c] }
        // cells vai do destino até a origem; corta caminho: do ponto atual, pula para a célula mais distante visível
        val last = if (!partial && cellFree(tx, ty)) 1 else 0
        val maxPts = out.size / 2
        var n = 0
        var curX = sx
        var curY = sy
        var i = nc - 1
        while (i > 0 && n < maxPts - 1) {
            var j = 0
            while (j < i) {
                val px = (cells[j] % GW) * NC + NC / 2f
                val py = (cells[j] / GW) * NC + NC / 2f
                if (lineClear(curX, curY, px, py)) break
                j++
            }
            if (j >= i) j = i - 1
            curX = (cells[j] % GW) * NC + NC / 2f
            curY = (cells[j] / GW) * NC + NC / 2f
            out[n * 2] = curX; out[n * 2 + 1] = curY; n++
            i = j
        }
        if (last == 1 && n < maxPts) { out[n * 2] = tx; out[n * 2 + 1] = ty; n++ }
        if (n == 0) { out[0] = (g % GW) * NC + NC / 2f; out[1] = (g / GW) * NC + NC / 2f; n = 1 } // já na célula do destino
        return n
    }

    /** O último path() não chega ao destino, só ao ponto livre mais perto dele. */
    var partial = false
        private set

}

/**
 * Vila Aurora: a pintura art/vila/Vila Fantasia e a Fonte Azul.png (1536x1024) é o chão, em escala 1:1.
 * Casas, fonte, postes, bancos, barris e floreiras bloqueiam a área inteira do desenho (o herói nunca fica "dentro"
 * de um objeto, então não precisa de recorte nem de y-sorting). Muros com aberturas a oeste e a leste.
 * Medidas em pixels da pintura (conferência: art/debug/vila_colisao.png).
 */
object Town {
    const val W = 1536
    const val H = 1024
    var ground: Bitmap? = null
    var loaded = false
        private set

    // bloqueios retangulares: x0, y0, x1, y1
    private val RECTS = floatArrayOf(
        // casas (de cima: 1-4; de baixo: 5-8)
        172f, 118f, 314f, 262f, 508f, 84f, 648f, 226f, 851f, 80f, 994f, 226f, 1199f, 108f, 1350f, 262f,
        168f, 726f, 310f, 872f, 496f, 772f, 646f, 918f, 826f, 776f, 982f, 922f, 1180f, 716f, 1334f, 878f,
        // postes (praça: 4; laterais: 2)
        558f, 330f, 584f, 414f, 925f, 328f, 952f, 414f, 554f, 548f, 582f, 642f, 928f, 548f, 954f, 642f,
        194f, 372f, 220f, 470f, 1293f, 372f, 1319f, 470f,
        // bancos
        486f, 404f, 547f, 456f, 961f, 402f, 1023f, 456f, 479f, 584f, 541f, 638f, 967f, 584f, 1029f, 638f,
        // barris
        411f, 170f, 466f, 216f, 1049f, 166f, 1105f, 213f, 147f, 660f, 199f, 709f, 1333f, 640f, 1391f, 696f,
        // floreiras
        422f, 249f, 491f, 299f, 242f, 370f, 323f, 421f, 1029f, 249f, 1096f, 299f, 1189f, 370f, 1273f, 421f,
        242f, 606f, 326f, 659f, 1189f, 606f, 1275f, 659f, 356f, 860f, 423f, 911f, 1056f, 856f, 1131f, 906f,
        // pilares dos portões
        80f, 400f, 124f, 474f, 80f, 532f, 124f, 600f, 1412f, 400f, 1456f, 474f, 1412f, 532f, 1456f, 600f,
    )
    // fonte: elipse (centro, raios)
    private const val FX = 755f
    private const val FY = 497f
    private const val FRX = 120f
    private const val FRY = 88f
    // limites internos dos muros e o corredor dos portões
    private const val IN_L = 124f
    private const val IN_R = 1412f
    private const val IN_T = 106f
    private const val IN_B = 916f
    private const val GATE_T = 478f
    private const val GATE_B = 532f

    /** Portais (do lado de fora dos portões) e pontos de chegada. */
    const val PORTAL_W_X = 52f
    const val PORTAL_E_X = 1484f
    const val GATE_Y = 505f
    const val ENTRY_W_X = 230f
    const val ENTRY_E_X = 1306f
    const val SPAWN_X = 768f
    const val SPAWN_Y = 642f

    fun load(res: Resources) {
        if (loaded) return
        val opaque = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.RGB_565 }
        ground = BitmapFactory.decodeResource(res, R.drawable.vila_chao, opaque)
        loaded = true
        nav.build()
    }

    fun walkable(x: Float, y: Float): Boolean {
        if (x < IN_L || x > IN_R) {
            // fora dos muros só o corredor dos portões
            if (y < GATE_T || y > GATE_B || x < 20f || x > W - 20f) return false
        } else if (y < IN_T || y > IN_B) return false
        var i = 0
        while (i < RECTS.size) {
            if (x > RECTS[i] && x < RECTS[i + 2] && y > RECTS[i + 1] && y < RECTS[i + 3]) return false
            i += 4
        }
        val dx = (x - FX) / FRX
        val dy = (y - FY) / FRY
        return dx * dx + dy * dy >= 1f
    }

    fun open(x: Float, y: Float, r: Float): Boolean =
        walkable(x, y) && walkable(x - r, y) && walkable(x + r, y) && walkable(x, y - r * 0.6f) && walkable(x, y + r * 0.6f)

    val nav = NavGrid(W, H) { x, y -> open(x, y, 6f) }
}

/** Visual em sprites: PNGs e medidas geradas por art/tools (SpriteTool, SideLayers, FrontLayers). */
class SpriteSkin(
    val ids: IntArray,
    /** Lado: corpo, braço+espada, perna da frente, perna de trás. */
    val sideIds: IntArray,
    /** Frente, 3/4 frente, 3/4 costas, costas: corpo sem pernas, perna esquerda, perna direita. */
    val frontIds: IntArray,
    val anchorX: Float, val baseY: Float,
    val hipFX: Float, val hipFY: Float, val hipBX: Float, val hipBY: Float, val shoulderX: Float, val shoulderY: Float,
    /** Por vista (0 = frente, 1 = 3/4 frente, 2 = 3/4 costas, 3 = costas): quadril e centro de cada perna. */
    val fHipY: FloatArray, val fLegL: FloatArray, val fLegR: FloatArray,
    /** Arma separada desenhada na mão (0 = nenhuma, a arma já está no sprite). */
    val staffId: Int = 0,
    /** Por vista base (0 frente, 1 3/4 frente, 2 lado, 3 3/4 costas, 4 costas): mão que segura e inclinação (graus). */
    val handX: FloatArray = FloatArray(5), val handY: FloatArray = FloatArray(5), val staffTilt: FloatArray = FloatArray(5),
    /** Ponto do PNG da arma que fica na mão. */
    val gripX: Float = 0f, val gripY: Float = 0f,
)

class SpriteHero(private val res: Resources, private val art: Art) {
    companion object {
        const val S = 0
        const val SE = 1
        const val E = 2
        const val NE = 3
        const val N = 4
        const val NW = 5
        const val W = 6
        const val SW = 7
        val NAMES = arrayOf("frente", "3/4 frente direita", "lado direito", "3/4 costas direita", "costas",
            "3/4 costas esquerda", "lado esquerdo", "3/4 frente esquerda")
        /** Guerreiro: folha art/source/guerreiro_sheet.png (sprite 248x420). */
        val GUERREIRO = SpriteSkin(
            intArrayOf(
                R.drawable.guerreiro_s, R.drawable.guerreiro_se, R.drawable.guerreiro_e, R.drawable.guerreiro_ne,
                R.drawable.guerreiro_n, R.drawable.guerreiro_nw, R.drawable.guerreiro_w, R.drawable.guerreiro_sw
            ),
            intArrayOf(R.drawable.lado_corpo, R.drawable.lado_braco, R.drawable.lado_perna_f, R.drawable.lado_perna_t),
            intArrayOf(
                R.drawable.frente_s_corpo, R.drawable.frente_s_perna_e, R.drawable.frente_s_perna_d,
                R.drawable.frente_se_corpo, R.drawable.frente_se_perna_e, R.drawable.frente_se_perna_d,
                R.drawable.frente_ne_corpo, R.drawable.frente_ne_perna_e, R.drawable.frente_ne_perna_d,
                R.drawable.frente_n_corpo, R.drawable.frente_n_perna_e, R.drawable.frente_n_perna_d
            ),
            anchorX = 124f, baseY = 418f,
            hipFX = 112f, hipFY = 318f, hipBX = 146f, hipBY = 318f, shoulderX = 124f, shoulderY = 252f,
            fHipY = floatArrayOf(329f, 328f, 330f, 333f),
            fLegL = floatArrayOf(93f, 103f, 92f, 90f),
            fLegR = floatArrayOf(161f, 165f, 157f, 158f),
        )
        /** Mago: folha art/source/mago_sheet.png (sprite 266x420) + cajado (mago_cajado.png) na mão. */
        val MAGO = SpriteSkin(
            intArrayOf(
                R.drawable.mago_s, R.drawable.mago_se, R.drawable.mago_e, R.drawable.mago_ne,
                R.drawable.mago_n, R.drawable.mago_nw, R.drawable.mago_w, R.drawable.mago_sw
            ),
            intArrayOf(R.drawable.mago_lado_corpo, R.drawable.mago_lado_braco, R.drawable.mago_lado_perna_f, R.drawable.mago_lado_perna_t),
            intArrayOf(
                R.drawable.mago_frente_s_corpo, R.drawable.mago_frente_s_perna_e, R.drawable.mago_frente_s_perna_d,
                R.drawable.mago_frente_se_corpo, R.drawable.mago_frente_se_perna_e, R.drawable.mago_frente_se_perna_d,
                R.drawable.mago_frente_ne_corpo, R.drawable.mago_frente_ne_perna_e, R.drawable.mago_frente_ne_perna_d,
                R.drawable.mago_frente_n_corpo, R.drawable.mago_frente_n_perna_e, R.drawable.mago_frente_n_perna_d
            ),
            anchorX = 133f, baseY = 418f,
            hipFX = 140f, hipFY = 346f, hipBX = 140f, hipBY = 346f, shoulderX = 130f, shoulderY = 240f,
            fHipY = floatArrayOf(379f, 377f, 378f, 378f),
            fLegL = floatArrayOf(101f, 113f, 114f, 103f),
            fLegR = floatArrayOf(168f, 178f, 175f, 168f),
            staffId = R.drawable.mago_cajado,
            handX = floatArrayOf(69f, 86f, 138f, 210f, 201f),
            handY = floatArrayOf(300f, 300f, 305f, 305f, 300f),
            staffTilt = floatArrayOf(-8f, -8f, 12f, 8f, 8f),
            gripX = 36f, gripY = 95f,
        )
        /** Aprendiz: folha art/source/aprendiz_sheet.png (sprite 262x420); de lado a perna de trás é cópia da da frente. */
        val APRENDIZ = SpriteSkin(
            intArrayOf(
                R.drawable.aprendiz_s, R.drawable.aprendiz_se, R.drawable.aprendiz_e, R.drawable.aprendiz_ne,
                R.drawable.aprendiz_n, R.drawable.aprendiz_nw, R.drawable.aprendiz_w, R.drawable.aprendiz_sw
            ),
            intArrayOf(
                R.drawable.aprendiz_lado_corpo, R.drawable.aprendiz_lado_braco,
                R.drawable.aprendiz_lado_perna_f, R.drawable.aprendiz_lado_perna_t
            ),
            intArrayOf(
                R.drawable.aprendiz_frente_s_corpo, R.drawable.aprendiz_frente_s_perna_e, R.drawable.aprendiz_frente_s_perna_d,
                R.drawable.aprendiz_frente_se_corpo, R.drawable.aprendiz_frente_se_perna_e, R.drawable.aprendiz_frente_se_perna_d,
                R.drawable.aprendiz_frente_ne_corpo, R.drawable.aprendiz_frente_ne_perna_e, R.drawable.aprendiz_frente_ne_perna_d,
                R.drawable.aprendiz_frente_n_corpo, R.drawable.aprendiz_frente_n_perna_e, R.drawable.aprendiz_frente_n_perna_d
            ),
            anchorX = 131f, baseY = 417f,
            hipFX = 124f, hipFY = 312f, hipBX = 124f, hipBY = 312f, shoulderX = 132f, shoulderY = 238f,
            fHipY = floatArrayOf(329f, 325f, 310f, 315f),
            fLegL = floatArrayOf(93f, 108f, 97f, 96f),
            fLegR = floatArrayOf(173f, 180f, 163f, 165f),
        )
        /** direção -> vista (-1 = lado) e se é espelhada */
        private val VIEW_OF = intArrayOf(0, 1, -1, 2, 3, 2, -1, 1)
        private val MIRROR_OF = booleanArrayOf(false, false, false, false, false, true, true, true)
        /** direção -> vista base (0 S, 1 SE, 2 E, 3 NE, 4 N) desenhada espelhada nas direções da esquerda */
        private val BASE_OF = intArrayOf(0, 1, 2, 3, 4, 3, 2, 1)
        /** vista do andar de frente (0 frente, 1 3/4 frente, 2 3/4 costas, 3 costas) -> vista base */
        private val FRONT_BASE = intArrayOf(0, 1, 3, 4)
        const val F_LIFT = 14f      // px do sprite (~3-4px na tela)
        const val F_SHORT = 0.10f  // encurta para 0,92 no pico
        const val F_ADV = 6f        // 3/4: a perna levantada avança (~1-2px na tela)
        const val F_SWAY = 2.4f     // balanço lateral do corpo (~1px na tela)
        const val F_RISE = 3.5f     // corpo mais baixo no contato do pé
        /** Vetor "para frente" de cada direção na tela (y para baixo). */
        val FX = floatArrayOf(0f, 0.707f, 1f, 0.707f, 0f, -0.707f, -1f, -0.707f)
        val FY = floatArrayOf(1f, 0.707f, 0f, -0.707f, -1f, -0.707f, 0f, 0.707f)
        private val SECTOR = intArrayOf(E, SE, S, SW, W, NW, N, NE)

        // altura do conteúdo dos PNGs (igual nos visuais); âncora e pivôs ficam em SpriteSkin
        const val CONTENT_H = 412f
        const val TARGET_H = 72f

        // ciclo de andar
        const val STRIDE = 38f      // unidades de mundo percorridas por ciclo (2 passos): fase segue a velocidade
        const val LEG_SWING = 33f   // graus
        const val ARM_SWING = 8f    // braço da espada
        const val FOOT_LIFT = 11f   // px do sprite (~2-3px na tela)
        const val BODY_DROP = 5f    // px do sprite no contato do pé (~1.5px na tela)
        const val BOB = 0.6f        // unidades de mundo (demais direções)
        const val TILT = 1.5f       // graus
        private const val TWO_PI = 6.2831855f

        fun dirOf(dx: Float, dy: Float): Int {
            val a = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            val idx = (((a + 360f + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)
            return SECTOR[idx]
        }
    }

    /** Visual atual; trocar recarrega os PNGs no próximo desenho. */
    var skin = GUERREIRO
    private var loaded: SpriteSkin? = null
    private val bmps = arrayOfNulls<Bitmap>(8)
    private var staffBmp: Bitmap? = null
    private val side = arrayOfNulls<Bitmap>(4)
    private val front = arrayOfNulls<Bitmap>(12)
    private var lastSinSign = 1f
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val flashPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = LightingColorFilter(0, 0xFFFFFF) }
    /** Perna de trás escurecida até 15% conforme o peso do andar (8 níveis pré-criados, sem alocar por frame). */
    private val darkPaints = Array(8) { i ->
        Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            val v = (255 * (1f - 0.15f * i / 7f)).toInt()
            colorFilter = LightingColorFilter(Color.rgb(v, v, v), 0)
        }
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val anim = Animator()
    private val dust = Particles(48)
    private val rnd = kotlin.random.Random(11)
    var dir = S
    private var oneShot = 0 // 0 nenhum, 1 ataque, 2 dano, 3 conjurar, 4 habilidade (pose de lado)
    /** Brilho na ponta do cajado: cor e instante (tempo do clipe) em que a carga solta. */
    private var glowCol = 0
    private var glowEnd = 0f
    private var hitStop = 0f
    private var hitDone = false
    private var flash = 0f
    private var hurtT = 0f
    private var castT = 0f
    private var phase = 0f
    private var walkW = 0f
    private var lastCosSign = 1f

    private fun load() {
        if (loaded === skin) return
        loaded = skin
        val o = BitmapFactory.Options().apply { inScaled = false }
        for (i in 0 until 8) bmps[i] = BitmapFactory.decodeResource(res, skin.ids[i], o)
        for (i in 0 until 4) side[i] = BitmapFactory.decodeResource(res, skin.sideIds[i], o)
        for (i in 0 until 12) front[i] = BitmapFactory.decodeResource(res, skin.frontIds[i], o)
        staffBmp = if (skin.staffId != 0) BitmapFactory.decodeResource(res, skin.staffId, o) else null
    }

    private var atkScale = 1f
    private var atkDx = 1f
    private var atkDy = 0f
    private var atkDist = 40f

    /**
     * scale: compressão da linha do tempo (golpes rápidos); dx,dy: direção normalizada até o alvo; dist: distância até ele.
     * O impacto do desenho coincide com o impacto do jogo (0,2s × scale).
     */
    fun attack(scale: Float = 1f, dx: Float = FX[dir], dy: Float = FY[dir], dist: Float = 40f) {
        val staffSkin = skin.staffId != 0
        anim.play(if (staffSkin) SpriteClips.ATTACK_STAFF else SpriteClips.ATTACK, true, 0.04f)
        glowCol = if (staffSkin) col(0xFF90CAF9) else 0
        glowEnd = ATK_IMPACT
        oneShot = 1
        hitDone = false
        atkScale = scale.coerceIn(0.3f, 1f)
        val len = max(0.001f, kotlin.math.sqrt(dx * dx + dy * dy))
        atkDx = dx / len
        atkDy = dy / len
        atkDist = dist
    }

    fun hurt() {
        flash = 0.15f
        hurtT = 0.3f
        if (oneShot != 1) {
            anim.play(SpriteClips.HURT, true, 0.04f)
            oneShot = 2
        }
    }

    fun cast() {
        anim.play(SpriteClips.CAST, true, 0.06f)
        oneShot = 3
        castT = SpriteClips.CAST.dur
    }

    /** Habilidade com animação própria (de lado, virado para dx); as sem clipe usam o conjurar genérico. */
    fun skill(id: Int, dx: Float, dy: Float) {
        val clip = SpriteClips.SKILL.getOrNull(id) ?: return cast()
        anim.play(clip, true, 0.05f)
        oneShot = 4
        val len = max(0.001f, kotlin.math.sqrt(dx * dx + dy * dy))
        atkDx = dx / len
        atkDy = dy / len
        glowCol = if (skin.staffId != 0) SpriteClips.SKILL_GLOW[id] else 0
        glowEnd = SpriteClips.SKILL_HIT[id]
        // magias: círculo mágico nos pés durante a carga
        if (glowCol != 0) castT = SpriteClips.SKILL_HIT[id] + 0.25f
    }

    /**
     * speed: velocidade de deslocamento (unidades/s) — a fase do passo avança na mesma proporção.
     * x,y: posição dos pés (no mesmo espaço em que será desenhado) para a poeira.
     */
    fun update(dt: Float, moving: Boolean, speed: Float, x: Float, y: Float, sc: Float) {
        if (flash > 0f) flash -= dt
        if (hurtT > 0f) hurtT -= dt
        if (castT > 0f) castT -= dt
        var adt = dt
        if (hitStop > 0f) {
            hitStop -= dt
            adt = 0f
        }
        if (oneShot != 0 && anim.finished) oneShot = 0
        if (oneShot == 0) anim.play(SpriteClips.IDLE, false, 0.2f)
        anim.update(if (oneShot == 1) adt / atkScale else adt)
        if (oneShot == 1 && !hitDone && anim.time >= ATK_IMPACT) {
            hitDone = true
            hitStop = HIT_STOP
        }
        // peso do andar: entra e sai suave (ao parar, volta à pose parada)
        val target = if (moving && oneShot == 0) 1f else 0f
        walkW += (target - walkW) * min(1f, dt * 9f)
        if (walkW < 0.004f) walkW = 0f
        if (moving) {
            phase += speed / STRIDE * TWO_PI * dt
            if (phase > TWO_PI) phase -= TWO_PI
        }
        // contato do pé com o chão: de lado = perna no extremo (cos troca de sinal);
        // frente/costas = pé que estava levantado volta ao chão (sen troca de sinal). Poeira só nesse instante.
        val cs = if (cos(phase) >= 0f) 1f else -1f
        val ss = if (sin(phase) >= 0f) 1f else -1f
        val sideView = VIEW_OF[dir] < 0
        val contact = if (sideView) cs != lastCosSign else ss != lastSinSign
        lastCosSign = cs
        lastSinSign = ss
        if (contact) {
            if (walkW > 0.5f) {
                val front = sin(phase) > 0f
                val fx = FX[dir]
                val footX = x + fx * (if (front) 6f else -6f) * sc
                for (k in 0 until 3) {
                    dust.spawn(footX + (rnd.nextFloat() - 0.5f) * 8f * sc, y - 0.5f * sc,
                        (rnd.nextFloat() - 0.5f) * 36f * sc - fx * 10f * sc, -(12f + rnd.nextFloat() * 20f) * sc,
                        0.35f + rnd.nextFloat() * 0.2f, (1.8f + rnd.nextFloat() * 1.8f) * sc, 40f * sc, col(0xB3D2C2A0))
                }
            }
        }
        dust.update(dt)
    }

    fun draw(c: Canvas, x: Float, y: Float, sc: Float, time: Float) {
        load()
        val p = anim.pose
        val d = dir
        val fx = FX[d]
        val fy = FY[d]
        val w = walkW
        val s = sin(phase)
        art.shadow(c, x, y, 16f * sc)
        if (castT > 0f) castCircle(c, x, y, sc, time)
        for (i in 0 until dust.cap) {
            if (dust.life[i] <= 0f) continue
            val a = (dust.life[i] / dust.max[i]).coerceIn(0f, 1f)
            fill.color = art.alpha(dust.cor[i], (a * 170).toInt())
            c.drawCircle(dust.x[i], dust.y[i], dust.size[i] * (1.6f - 0.6f * a), fill)
        }
        val k = sc * TARGET_H / CONTENT_H
        // no ataque, recuo/avanço seguem a direção real até o alvo; nos demais, a direção do sprite
        val mx = if (oneShot == 1) atkDx else fx
        val my = if (oneShot == 1) atkDy else fy
        var ox = p[CH_X] * mx * sc
        var oy = (p[CH_Y] + p[CH_X] * my * 0.5f) * sc
        if (hurtT > 0f) ox += sin(hurtT * 90f) * 2f * (hurtT / 0.3f) * sc
        val lean = if (abs(mx) > 0.3f) p[CH_LEAN] * sign(mx) else 0f
        // ataque: sempre de lado (esquerda/direita pelo alvo), com o braço da espada animado
        val atkRig = oneShot == 1 || oneShot == 4
        val sideRig = !atkRig && w > 0.01f && oneShot == 0 && (d == E || d == W)
        val frontRig = !atkRig && w > 0.01f && oneShot == 0 && !sideRig
        var tilt = p[CH_HEAD] + lean
        var sx = p[CH_SX]
        var sy = p[CH_SY]
        if (w > 0f && !sideRig && !frontRig) {
            // balanço baixo sincronizado com os passos: mais baixo quando o pé toca o chão
            oy += BOB * sc * w * abs(s)
            tilt += TILT * w * s
            val s4 = s * s * s * s
            sy *= 1f - 0.015f * w * s4
            sx *= 1f + 0.01f * w * s4
        }
        c.save()
        c.translate(x + ox, y + oy)
        c.rotate(tilt)
        if (atkRig) {
            c.scale(sx * k * (if (atkDx < 0f) -1f else 1f), sy * k)
            c.translate(-skin.anchorX, -skin.baseY)
            drawSidePose(c, p[CH_LEG_F], p[CH_LEG_B], 0f, 0f, p[CH_HAND_F], p[CH_SIT], 0, p[CH_WEAPON], glow())
        } else if (sideRig) {
            c.scale(sx * k * (if (d == W) -1f else 1f), sy * k)
            c.translate(-skin.anchorX, -skin.baseY + BODY_DROP * w * abs(s))
            drawSideRig(c, s, cos(phase), w)
        } else if (frontRig) {
            c.scale(sx * k * (if (MIRROR_OF[d]) -1f else 1f), sy * k)
            c.translate(-skin.anchorX, -skin.baseY)
            drawFrontRig(c, VIEW_OF[d], s, w)
        } else if (staffBmp != null && (d == E || d == W)) {
            // de lado parado: mesma montagem do andar (cajado entre o corpo e o braço)
            c.scale(sx * k * (if (d == W) -1f else 1f), sy * k)
            c.translate(-skin.anchorX, -skin.baseY)
            drawSidePose(c, 0f, 0f, 0f, 0f, 0f, 0f, 0)
        } else if (staffBmp != null) {
            val v = BASE_OF[d]
            val bmp = bmps[v]
            if (bmp != null) {
                c.scale(sx * k * (if (d >= NW) -1f else 1f), sy * k)
                c.translate(-skin.anchorX, -skin.baseY)
                staff(c, v, 0f, 0f) // atrás do corpo: o punho fica por cima
                c.drawBitmap(bmp, 0f, 0f, paint)
                if (flash > 0f) {
                    flashPaint.alpha = (flash / 0.15f * 210).toInt().coerceIn(0, 255)
                    c.drawBitmap(bmp, 0f, 0f, flashPaint)
                }
            }
        } else {
            val bmp = bmps[d]
            if (bmp != null) {
                c.scale(sx * k, sy * k)
                c.drawBitmap(bmp, -skin.anchorX, -skin.baseY, paint)
                if (flash > 0f) {
                    flashPaint.alpha = (flash / 0.15f * 210).toInt().coerceIn(0, 255)
                    c.drawBitmap(bmp, -skin.anchorX, -skin.baseY, flashPaint)
                }
            }
        }
        c.restore()
        if (oneShot == 1 && staffBmp == null) trail(c, x + ox, y + oy, sc)
    }

    /**
     * Morte com o próprio sprite: pisca branco, cai de costas girando nos pés (0,4s), quica de leve e fica deitado.
     * t: segundos desde a morte.
     */
    fun drawDead(c: Canvas, x: Float, y: Float, sc: Float, t: Float) {
        load()
        val right = FX[dir] >= 0f
        val bmp = bmps[if (right) E else W] ?: return
        val fall = Ease.apply(Ease.IN, (t / 0.4f).coerceIn(0f, 1f))
        val bounce = if (t in 0.4f..0.6f) sin((t - 0.4f) / 0.2f * kotlin.math.PI.toFloat()) * 6f else 0f
        val ang = (fall * 88f - bounce) * (if (right) -1f else 1f)
        val k = sc * TARGET_H / CONTENT_H
        art.shadow(c, x, y, 16f * sc * (1f + 0.6f * fall))
        c.save()
        c.translate(x, y - 6f * sc * fall)
        c.rotate(ang)
        c.scale(k, k)
        c.drawBitmap(bmp, -skin.anchorX, -skin.baseY, paint)
        if (t < 0.15f) {
            flashPaint.alpha = ((1f - t / 0.15f) * 210).toInt().coerceIn(0, 255)
            c.drawBitmap(bmp, -skin.anchorX, -skin.baseY, flashPaint)
        }
        c.restore()
    }

    /** Lado: perna de trás, perna da frente, corpo, braço+espada — cada parte girando no seu pivô. */
    private fun drawSideRig(c: Canvas, s: Float, cphase: Float, w: Float) {
        val body = side[0] ?: return
        val arm = side[1] ?: return
        val legF = side[2] ?: return
        val legB = side[3] ?: return
        // perna da frente vai para frente quando s>0 (rotação anti-horária = negativa); a de trás em fase oposta
        val angF = -LEG_SWING * s * w
        val angB = LEG_SWING * s * w
        // o pé levanta ao passar pela vertical indo para frente
        val liftF = FOOT_LIFT * max(0f, cphase) * w
        val liftB = FOOT_LIFT * max(0f, -cphase) * w
        // braço da espada ao contrário da perna da frente
        drawSidePose(c, angF, angB, liftF, liftB, ARM_SWING * s * w, 0f, (w * 7f + 0.5f).toInt())
    }

    /** Pose de lado por partes: pernas (ângulo/elevação), braço da espada (ângulo) e agachamento do corpo (px). */
    /** Intensidade do brilho do cajado: cresce até a soltura e some em 0,2s. */
    private fun glow(): Float {
        if (glowCol == 0 || (oneShot != 1 && oneShot != 4)) return 0f
        val t = anim.time
        return if (t < glowEnd) t / max(0.01f, glowEnd) else max(0f, 1f - (t - glowEnd) / 0.2f)
    }

    private fun drawSidePose(
        c: Canvas, angF: Float, angB: Float, liftF: Float, liftB: Float, armAng: Float, crouch: Float, darkLv: Int,
        weaponAng: Float = 0f, glowK: Float = 0f
    ) {
        val body = side[0] ?: return
        val arm = side[1] ?: return
        val legF = side[2] ?: return
        val legB = side[3] ?: return
        part(c, legB, skin.hipBX, skin.hipBY, angB, liftB, darkPaints[darkLv.coerceIn(0, 7)])
        part(c, legF, skin.hipFX, skin.hipFY, angF, liftF, paint)
        c.save()
        c.translate(0f, crouch)
        c.drawBitmap(body, 0f, 0f, paint)
        if (staffBmp != null) {
            c.save()
            c.rotate(armAng, skin.shoulderX, skin.shoulderY)
            c.rotate(weaponAng, skin.handX[2], skin.handY[2])
            staff(c, 2, 0f, 0f, glowK)
            c.restore()
        }
        part(c, arm, skin.shoulderX, skin.shoulderY, armAng, 0f, paint)
        c.restore()
    }

    /**
     * Frente / 3/4 / costas: pernas atrás do corpo. As pernas se alternam: uma levanta e encurta (0,92 no pico)
     * enquanto a outra fica apoiada; no 3/4 a perna levantada avança. Corpo balança 1px para o lado da perna apoiada
     * e fica mais baixo no contato do pé.
     */
    private fun drawFrontRig(c: Canvas, v: Int, s: Float, w: Float) {
        val body = front[v * 3] ?: return
        val legL = front[v * 3 + 1] ?: return
        val legR = front[v * 3 + 2] ?: return
        val liftL = max(0f, s) * w
        val liftR = max(0f, -s) * w
        val adv = if (v == 1 || v == 2) F_ADV else 0f
        frontLeg(c, legL, skin.fLegL[v], skin.fHipY[v], liftL, adv)
        frontLeg(c, legR, skin.fLegR[v], skin.fHipY[v], liftR, adv)
        staff(c, FRONT_BASE[v], F_SWAY * s * w, -F_RISE * abs(s) * w)
        c.drawBitmap(body, F_SWAY * s * w, -F_RISE * abs(s) * w, paint)
    }

    private fun frontLeg(c: Canvas, b: Bitmap, cx: Float, hipY: Float, lift: Float, adv: Float) {
        c.save()
        c.translate(adv * lift, -F_LIFT * lift)
        c.scale(1f, 1f - F_SHORT * lift, cx, hipY)
        c.drawBitmap(b, 0f, 0f, paint)
        c.restore()
    }

    /** Arma separada (cajado) na mão da vista base v, em espaço do sprite; dx/dy acompanham o corpo. */
    private fun staff(c: Canvas, v: Int, dx: Float, dy: Float, glowK: Float = 0f) {
        val b = staffBmp ?: return
        c.save()
        c.translate(skin.handX[v] + dx, skin.handY[v] + dy)
        c.rotate(skin.staffTilt[v])
        c.drawBitmap(b, -skin.gripX, -skin.gripY, paint)
        if (glowK > 0f) {
            // carga na pedra do cajado: halo colorido pulsando + núcleo branco
            val tx = b.width / 2f - skin.gripX
            val ty = b.height * 0.1f - skin.gripY
            val pulse = 1f + 0.12f * sin(anim.time * 40f)
            fill.color = art.alpha(glowCol, (glowK * 120).toInt())
            c.drawCircle(tx, ty, 46f * glowK * pulse, fill)
            fill.color = art.alpha(glowCol, (glowK * 200).toInt())
            c.drawCircle(tx, ty, 26f * glowK * pulse, fill)
            fill.color = art.alpha(Color.WHITE, (glowK * 230).toInt())
            c.drawCircle(tx, ty, 12f * glowK, fill)
        }
        c.restore()
    }

    private fun part(c: Canvas, b: Bitmap, px: Float, py: Float, ang: Float, lift: Float, pt: Paint) {
        c.save()
        c.translate(0f, -lift)
        c.rotate(ang, px, py)
        c.drawBitmap(b, 0f, 0f, pt)
        c.restore()
    }

    /**
     * Arco do golpe orientado para o alvo: centrado no peito do herói, com raio até o alvo, varrendo da espada
     * por cima do alvo durante a investida (tempo do clipe 0,12–0,20s) e sumindo na recuperação.
     */
    private fun trail(c: Canvas, x: Float, y: Float, sc: Float) {
        // nasce na espada erguida (atrás/acima da cabeça), varre de cima até o alvo em ~100ms e fica ~300ms desbotando
        val t = anim.time
        if (t < ATK_WIND - 0.01f || t > 0.80f) return
        val k = Ease.apply(Ease.OUT, ((t - ATK_WIND) / (ATK_IMPACT - ATK_WIND)).coerceIn(0f, 1f))
        val fade = if (t > ATK_IMPACT) (1f - (t - ATK_IMPACT) / 0.30f).coerceIn(0f, 1f) else 1f
        val side = if (atkDx >= 0f) 1f else -1f
        val cx = x
        val cy = y - 34f * sc
        // raio até o alvo + um pouco além, para cobrir o monstro
        val r = (atkDist + 16f).coerceIn(34f, 80f) * sc
        rect.set(cx - r, cy - r * 0.75f, cx + r, cy + r * 0.75f)
        // de cima/atrás (-110°) até a frente-baixo (+35°), espelhado para a esquerda
        val start = if (side > 0f) -110f else -70f
        val sweep = 145f * k * side
        val widths = floatArrayOf(18f, 11f, 5f)
        val cols = intArrayOf(col(0xFF81D4FA), col(0xFFE1F5FE), Color.WHITE)
        val alphas = intArrayOf(70, 150, 240)
        for (i in 0..2) {
            stroke.strokeWidth = widths[i] * sc * (0.6f + 0.4f * fade)
            stroke.color = art.alpha(cols[i], (alphas[i] * fade).toInt().coerceIn(0, 255))
            c.drawArc(rect, start + sweep * 0.12f * i, sweep * (1f - 0.12f * i), false, stroke)
        }
    }

    /** Círculo brilhante no chão ao conjurar. */
    private fun castCircle(c: Canvas, x: Float, y: Float, sc: Float, time: Float) {
        val k = castT / SpriteClips.CAST.dur
        val a = (if (k > 0.8f) (1f - k) / 0.2f else k / 0.8f).coerceIn(0f, 1f)
        val r = 26f * sc
        rect.set(x - r, y - r * 0.42f, x + r, y + r * 0.42f)
        fill.color = art.alpha(col(0xFF80D8FF), (a * 60).toInt())
        c.drawOval(rect, fill)
        stroke.strokeWidth = 2.2f * sc
        stroke.color = art.alpha(col(0xFFE1F5FE), (a * 230).toInt())
        c.drawOval(rect, stroke)
        stroke.strokeWidth = 1.6f * sc
        stroke.color = art.alpha(col(0xFFFFE082), (a * 220).toInt())
        val rot = (time * 120f) % 360f
        for (i in 0 until 4) c.drawArc(rect, rot + i * 90f, 50f, false, stroke)
        rect.inset(7f * sc, 3f * sc)
        stroke.color = art.alpha(col(0xFF80D8FF), (a * 200).toInt())
        c.drawOval(rect, stroke)
    }
}

// =====================================================================
// FLORESTA (substitui o Campo de Aprendizes): camadas extraídas por art/tools/ForestExtract.java
// 1 px da imagem = 1 unidade de mundo (o guerreiro ~72 fica com ~metade da altura de uma árvore média)
// =====================================================================
object Forest {
    const val W = 1672
    const val H = 941
    const val T_TREE = 0
    const val T_BUSH = 1
    const val T_ROCK = 2

    // objetos do meio (art/floresta/objetos.txt): tipo, canto sup. esq., largura, ponto de apoio (base)
    val TYPE = intArrayOf(0, 0, 2, 1, 1, 1, 2, 0, 2, 1, 0, 0, 1, 1, 2, 0, 2, 1, 2, 0, 2, 2, 0, 1, 1, 2, 2)
    val OX = floatArrayOf(139f, 427f, 353f, 376f, 96f, 624f, 590f, 1081f, 997f, 1237f, 1468f, 1085f, 880f, 1000f, 1487f, 34f, 33f, 423f, 529f, 653f, 600f, 747f, 1476f, 1401f, 1604f, 177f, 985f)
    val OY = floatArrayOf(177f, 263f, 287f, 374f, 326f, 263f, 294f, 166f, 272f, 275f, 232f, 352f, 390f, 370f, 476f, 516f, 489f, 554f, 530f, 549f, 666f, 504f, 582f, 567f, 568f, 699f, 137f)
    val OW = floatArrayOf(169f, 91f, 94f, 83f, 82f, 48f, 43f, 168f, 109f, 48f, 134f, 135f, 84f, 49f, 46f, 149f, 50f, 87f, 86f, 178f, 101f, 43f, 169f, 83f, 66f, 58f, 41f)
    val AX = floatArrayOf(230f, 454f, 403f, 409f, 129f, 643f, 620f, 1165f, 1063f, 1263f, 1537f, 1158f, 932f, 1022f, 1502f, 107f, 63f, 476f, 573f, 747f, 653f, 769f, 1586f, 1451f, 1635f, 216f, 1001f)
    val AY = floatArrayOf(351f, 398f, 352f, 438f, 384f, 298f, 323f, 302f, 327f, 310f, 380f, 502f, 449f, 405f, 516f, 668f, 518f, 615f, 573f, 735f, 720f, 530f, 732f, 627f, 610f, 732f, 162f)
    private val IDS = intArrayOf(
        R.drawable.floresta_obj_00, R.drawable.floresta_obj_01, R.drawable.floresta_obj_02, R.drawable.floresta_obj_03,
        R.drawable.floresta_obj_04, R.drawable.floresta_obj_05, R.drawable.floresta_obj_06, R.drawable.floresta_obj_07,
        R.drawable.floresta_obj_08, R.drawable.floresta_obj_09, R.drawable.floresta_obj_10, R.drawable.floresta_obj_11,
        R.drawable.floresta_obj_12, R.drawable.floresta_obj_13, R.drawable.floresta_obj_14, R.drawable.floresta_obj_15,
        R.drawable.floresta_obj_16, R.drawable.floresta_obj_17, R.drawable.floresta_obj_18, R.drawable.floresta_obj_19,
        R.drawable.floresta_obj_20, R.drawable.floresta_obj_21, R.drawable.floresta_obj_22, R.drawable.floresta_obj_23,
        R.drawable.floresta_obj_24, R.drawable.floresta_obj_25, R.drawable.floresta_obj_26
    )
    val N = TYPE.size

    /** Referência de cada objeto para o y-sorting (criada uma vez, sem alocar por frame). */
    class Obj(val i: Int)

    val objs = Array(N) { Obj(it) }
    val objBmp = arrayOfNulls<Bitmap>(N)
    var ground: Bitmap? = null
    var top: Bitmap? = null
    var bottom: Bitmap? = null
    var loaded = false
        private set

    // colisão das faixas: por coluna, até onde vai a floresta de cima e onde começa a de baixo
    private val topEdge = IntArray(W)
    private val botEdge = IntArray(W) { H }

    /** Entradas/saídas (posição no mundo). */
    const val ENTRY_LEFT_X = 110f
    const val ENTRY_LEFT_Y = 466f
    const val CENTER_X = 760f
    const val CENTER_Y = 470f
    const val PORTAL_LEFT_X = 40f
    const val PORTAL_LEFT_Y = 466f

    fun load(res: Resources) {
        if (loaded) return
        val opaque = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.RGB_565 }
        val alpha = BitmapFactory.Options().apply { inScaled = false }
        ground = BitmapFactory.decodeResource(res, R.drawable.floresta_chao, opaque)
        top = BitmapFactory.decodeResource(res, R.drawable.floresta_topo, alpha)
        bottom = BitmapFactory.decodeResource(res, R.drawable.floresta_base, alpha)
        for (i in 0 until N) objBmp[i] = BitmapFactory.decodeResource(res, IDS[i], alpha)
        val px = IntArray(W * H)
        top?.let { b ->
            b.getPixels(px, 0, W, 0, 0, W, H)
            for (x in 0 until W) {
                var e = 0
                for (y in 0 until H / 2) if ((px[y * W + x] ushr 24) > 128) e = y
                topEdge[x] = e
            }
        }
        bottom?.let { b ->
            b.getPixels(px, 0, W, 0, 0, W, H)
            for (x in 0 until W) {
                var e = H
                for (y in H - 1 downTo H / 2) if ((px[y * W + x] ushr 24) > 128) e = y
                botEdge[x] = e
            }
        }
        // suaviza: cada coluna usa o pior caso das vizinhas (sem frestas de 1px)
        val t2 = topEdge.copyOf()
        val b2 = botEdge.copyOf()
        for (x in 0 until W) {
            var t = 0
            var b = H
            for (k in -4..4) {
                val xx = (x + k).coerceIn(0, W - 1)
                t = max(t, t2[xx]); b = min(b, b2[xx])
            }
            topEdge[x] = t; botEdge[x] = b
        }
        loaded = true
        nav.build()
    }

    /**
     * Pode pisar aqui? Floresta de cima e de baixo bloqueiam (embaixo para na borda: as copas são desenhadas por cima
     * dos personagens e escondiam herói e monstros); árvores e pedras bloqueiam numa elipse na base; arbustos são passáveis.
     */
    fun walkable(x: Float, y: Float): Boolean {
        if (!loaded) return true
        val xi = x.toInt().coerceIn(0, W - 1)
        if (y < topEdge[xi] + 8f) return false
        if (y > botEdge[xi] - 6f) return false
        for (i in 0 until N) {
            val t = TYPE[i]
            if (t == T_BUSH) continue
            val rx = if (t == T_TREE) 16f else min(OW[i] * 0.33f, 30f)
            val ry = if (t == T_TREE) 8f else 9f
            val dx = (x - AX[i]) / rx
            val dy = (y - AY[i] + 3f) / ry
            if (dx * dx + dy * dy < 1f) return false
        }
        return true
    }

    /** Pisável com folga r em volta (monstros nascem e passeiam longe de troncos, pedras e da borda da mata). */
    fun open(x: Float, y: Float, r: Float): Boolean =
        walkable(x, y) && walkable(x - r, y) && walkable(x + r, y) && walkable(x, y - r * 0.6f) && walkable(x, y + r * 0.6f)

    /** Navegação do herói (A*); célula livre = pisável com 6px de folga. */
    val nav = NavGrid(W, H) { x, y -> open(x, y, 6f) }
    fun lineClear(x0: Float, y0: Float, x1: Float, y1: Float) = !loaded || nav.lineClear(x0, y0, x1, y1)
    fun path(sx: Float, sy: Float, tx: Float, ty: Float, out: FloatArray) = if (loaded) nav.path(sx, sy, tx, ty, out) else 0

    /** 0 = nenhuma, 2 = estrada da direita (em breve), 3 = trilha de terra do topo (em breve). A esquerda é um portal. */
    fun exitAt(x: Float, y: Float): Int {
        if (x > W - 46f && y > 400f && y < 545f) return 2
        if (y < 128f && x > 740f && x < 990f) return 3
        return 0
    }
}
