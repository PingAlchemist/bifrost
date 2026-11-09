package br.com.bifrost

import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.random.Random

/** Quantidade de slots de personagem na tela de seleção. */
const val SLOTS = 3

enum class Panel { NONE, STATUS, SKILLS, ITENS, LOJA, FERREIRO, MESTRE, TELEPORTE, OFFLINE, MORTE }

class Mob(val d: MonsterDef, var x: Float, var y: Float) {
    var hp = d.hp
    var atkT = 0.8f
    var atkAnim = 0f
    var aggro = false
    var wx = x
    var wy = y
    var wt = 0f
    var frozen = 0f
    var poison = 0f
    var poisonDps = 0f
    var poisonTick = 0f
    var flash = 0f
    var kb = 0f
    var kbDir = 1f
    var anim = Random.nextFloat() * 10f
    var dead = false
    /** Animação de morte (achata, pisca, vira partículas) antes de sair da lista. */
    var dying = 0f
    var burst = false
    var face = 1f
}

const val TXT_NORMAL = 0
const val TXT_DMG = 1    // números de dano: "pop", sobem ~30px e somem em ~0,6s
const val TXT_HERO = 2   // +EXP / +Zeny: menores, em fila saindo do herói
const val TXT_BIG = 3    // LEVEL UP, JOB UP, CARTA

class FText(var x: Float, var y: Float, val s: String, val cor: Int, var life: Float, val big: Boolean, val kind: Int = TXT_NORMAL, val sz: Float = 1f) {
    val max = life
}

// estados do herói (nunca dois ao mesmo tempo)
const val HS_IDLE = 0
const val HS_WALK = 1
const val HS_POS = 2      // chegou no alcance: desacelera, para e vira para o alvo
const val HS_ATK = 3      // preparação -> impacto -> recuperação
const val HS_POST = 4     // pausa após abate (moedas caem) antes do próximo alvo

// linha do tempo do ataque (s); comprimida quando a velocidade de ataque é maior
const val ATK_WIND = 0.40f   // preparação: inclina para trás e ergue a espada
const val ATK_IMPACT = 0.50f // golpe ~100ms; dano aplicado aqui
const val ATK_END = 1.0f     // segura a pose agachada ~300ms e volta ao idle
const val HIT_STOP = 0.06f
const val POST_KILL = 0.8f
const val DEATH_TIME = 0.4f

const val FX_RING = 0
const val FX_SHOT = 1
const val FX_BOLT = 2
const val FX_SLASH = 3
const val FX_RAIN = 4
const val FX_FLASH = 5 // explosão branca de impacto sobre o monstro

class Fx(val kind: Int, val x: Float, val y: Float, val x2: Float, val y2: Float, val r: Float, val cor: Int, var life: Float) {
    val max = life
}

class LogLine(val s: String, val cor: Int, var t: Float)
class Npc(val nome: String, val x: Float, val y: Float, val tipo: Panel?, val cor: Int, val cabelo: Int, val hat: Int)
class Deco(val x: Float, val y: Float, val kind: Int, val s: Float, val tall: Boolean, val seed: Int)
class Portal(val x: Float, val y: Float, val dest: Int, val entry: Int)

/** Item caído no chão (pool fixo): quica, brilha e voa até o herói. */
class Drop {
    var on = false
    var x = 0f
    var y = 0f
    var z = 0f
    var vx = 0f
    var vy = 0f
    var vz = 0f
    var t = 0f
    var kind = 0 // 0 zeny, 1 item, 2 carta
    var amount = 0L
    var label = ""
    var cor = 0
}

/** Partículas com pool em arrays (sem alocação por frame). Buffer circular. */
class Particles(val cap: Int) {
    val x = FloatArray(cap)
    val y = FloatArray(cap)
    val vx = FloatArray(cap)
    val vy = FloatArray(cap)
    val life = FloatArray(cap)
    val max = FloatArray(cap)
    val size = FloatArray(cap)
    val grav = FloatArray(cap)
    val cor = IntArray(cap)
    private var next = 0

    fun spawn(px: Float, py: Float, pvx: Float, pvy: Float, l: Float, s: Float, g: Float, c: Int) {
        val i = next
        next = (next + 1) % cap
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy
        life[i] = l; max[i] = l; size[i] = s; grav[i] = g; cor[i] = c
    }

    fun update(dt: Float) {
        for (i in 0 until cap) {
            if (life[i] <= 0f) continue
            life[i] -= dt
            vy[i] += grav[i] * dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
        }
    }

    fun clear() = life.fill(0f)
}

class OfflineReport(
    val secs: Long, val mapa: String, val kills: Long, val exp: Long, val jexp: Long, val zeny: Long,
    val loot: Int, val potsUsadas: Int, val potsGanhas: Int, val cards: List<String>,
    val lvBase: Int, val lvJob: Int, val nota: String
)

fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    return sqrt(dx * dx + dy * dy)
}

val C_WHITE = col(0xFFFFFFFF)
val C_YELLOW = col(0xFFFFEB3B)
val C_RED = col(0xFFFF5252)
val C_GREEN = col(0xFF69F0AE)
val C_BLUE = col(0xFF64B5F6)
val C_GOLD = col(0xFFFFD54F)
val C_PURPLE = col(0xFFCE93D8)
val C_GRAY = col(0xFFB0BEC5)
val C_ORANGE = col(0xFFFFAB40)
val C_MISS = col(0xFF82B1FF)

class Game {
    val p = Player()
    var map = MAPS[0]
    val mobs = ArrayList<Mob>()
    val npcs = ArrayList<Npc>()
    val portals = ArrayList<Portal>()
    var decos: List<Deco> = emptyList()
    var px = 800f
    var py = 680f
    var destX = 0f
    var destY = 0f
    var hasDest = false
    var target: Mob? = null
    var npcTarget: Npc? = null
    var pendingSkill = -1
    var face = 1f
    var walk = 0f
    var moving = false
    var atkAnim = 0f
    private var atkT = 0f

    // ----- fluxo de combate/movimento -----
    var hstate = HS_IDLE
        private set
    var curSpeed = 0f
        private set
    private var exitMsgT = 0f
    private var lastDx = 1f
    private var lastDy = 0f
    private var posT = 0f
    private var atkPhaseT = 0f
    private var swingDone = false
    private var impactDone = false
    private var atkMob: Mob? = null
    private var killedInAttack = false
    var postKillT = 0f
        private set
    /** Sinais para a animação (o desenho reage sem mexer na lógica). */
    var atkSeq = 0
        private set
    var castSeq = 0
        private set
    var atkScale = 1f
        private set
    var atkDirX = 1f
        private set
    var atkDirY = 0f
        private set
    var atkDist = 40f
        private set
    var hitStopT = 0f
        private set
    private var hitStopMob: Mob? = null
    /** Tremida de câmera (só no crítico). */
    var shakeT = 0f
        private set
    // fila de textos do herói (+EXP, +Zeny...) e LEVEL UP sozinho
    private val heroQueue = ArrayList<FText>()
    private var heroQueueT = 0f
    private var lvlTextT = 0f

    private var castLock = 0f
    private var regenT = 0f
    private var potT = 0f
    private var manualT = 0f
    private var spawnT = 0f
    private var saveT = 0f
    val cd = FloatArray(SKILLS.size)
    val bossT = FloatArray(MAPS.size) { 150f }
    val texts = ArrayList<FText>()
    val fx = ArrayList<Fx>()
    val log = ArrayList<LogLine>()
    val drops = Array(48) { Drop() }
    val parts = Particles(360)
    var panel = Panel.NONE
    var dead = false
    var time = 0f
    var report: OfflineReport? = null
    var lastLoss = 0L
    var lvlFx = 0f
    var refineMsg = ""
    var onSave: (() -> Unit)? = null
    var onVibrate: ((Long) -> Unit)? = null
    /** Efeito sonoro (id em Audio.S_*, velocidade/tom). */
    var sfx: ((Int, Float) -> Unit)? = null
    /** Troca de mapa (para a música de fundo). */
    var onMapChange: ((Int) -> Unit)? = null
    /** Liga/desliga música alterado no Status. */
    var onMusicSetting: (() -> Unit)? = null
    private var started = false

    // ----- seleção de personagem -----
    /** Tela de seleção ativa: o jogo fica parado e o Renderer desenha os slots. */
    var selecting = true
    /** Resumo de cada slot (null = vazio), lido dos saves. */
    val slots = arrayOfNulls<Player>(SLOTS)
    var selSlot = 0
    var onPlay: ((Int) -> Unit)? = null
    var onCreateChar: ((Int) -> Unit)? = null
    var onDeleteChar: ((Int) -> Unit)? = null
    var onSwitchChar: (() -> Unit)? = null
    val rnd = Random(System.nanoTime())

    private fun snd(id: Int, rate: Float = 1f) {
        if (p.som) sfx?.invoke(id, rate)
    }

    /** Ordem de prioridade do auto por classe (cura > buff > área > dano), calculada uma vez. */
    private val autoOrders = Array(Job.entries.size) { j ->
        Job.entries[j].skills.sortedBy { SKILLS[it].kind.ordinal }.toIntArray()
    }

    fun start() {
        p.recalc()
        if (p.hp <= 0) p.hp = p.maxHp
        if (p.sp <= 0) p.sp = p.maxSp
        changeMap(p.map, 2)
        started = true
        addLog("Bem-vindo a Bifrost! Toque no chão para andar e nos monstros para atacar.", C_GOLD)
        if (p.statPts > 0) addLog("Abra o Status e distribua seus ${p.statPts} pontos de atributo!", C_GOLD)
    }

    // ================= LOOP =================
    fun update(dt: Float) {
        time += dt
        for (i in p.buff.indices) if (p.buff[i] > 0f) p.buff[i] -= dt
        p.recalc()
        atkT -= dt; castLock -= dt; potT -= dt; manualT -= dt
        atkAnim = max(0f, atkAnim - dt * 4f)
        lvlFx = max(0f, lvlFx - dt)
        if (hitStopT > 0f) hitStopT -= dt
        if (shakeT > 0f) shakeT -= dt
        if (postKillT > 0f) postKillT -= dt
        // textos do herói saem em fila (150ms), e esperam o LEVEL UP terminar
        if (lvlTextT > 0f) lvlTextT -= dt
        heroQueueT -= dt
        if (heroQueue.isNotEmpty() && heroQueueT <= 0f && lvlTextT <= 0f) {
            val t = heroQueue.removeAt(0)
            t.x = px
            t.y = py - 92f
            texts.add(t)
            heroQueueT = 0.15f
        }
        for (i in cd.indices) if (cd[i] > 0f) cd[i] -= dt
        regen(dt)
        if (!dead) {
            updatePlayer(dt)
            if (p.autoMode && map.id != 0) autoLogic()
            if (p.autoPot && map.id != 0) autoPotion()
        }
        updateMobs(dt)
        for (i in mobs.size - 1 downTo 0) if (mobs[i].dead && mobs[i].dying <= 0f) mobs.removeAt(i)
        spawning(dt)
        updateDrops(dt)
        if (lvlFx > 0f && p.fx) levelParticles()
        parts.update(dt)

        for (i in texts.size - 1 downTo 0) {
            val t = texts[i]
            t.life -= dt
            t.y -= dt * when (t.kind) {
                TXT_DMG -> 18f
                TXT_HERO -> 70f
                TXT_BIG -> 22f
                else -> if (t.big) 25f else 40f
            }
            if (t.life <= 0f) texts.removeAt(i)
        }
        for (i in fx.size - 1 downTo 0) {
            val f = fx[i]
            f.life -= dt
            if (f.life <= 0f) fx.removeAt(i)
        }
        for (i in log.indices) log[i].t += dt

        saveT += dt
        if (saveT > 20f) {
            saveT = 0f
            onSave?.invoke()
        }
    }

    private fun vibrate(ms: Long) {
        if (p.vibra) onVibrate?.invoke(ms)
    }

    private fun regen(dt: Float) {
        regenT += dt
        if (regenT < 3f) return
        regenT = 0f
        if (dead) return
        val mult = if (map.id == 0) 4f else 1f
        p.hp = min(p.maxHp, p.hp + max(1, ((p.maxHp * 0.012f + p.tot[VIT] * 0.2f) * mult).toInt()))
        p.sp = min(p.maxSp, p.sp + max(1, ((p.maxSp * 0.015f + p.tot[INT] * 0.25f) * mult).toInt()))
    }

    /**
     * Máquina de estados do herói: PARADO -> ANDANDO -> POSICIONANDO -> ATACANDO -> (PAUSA_PÓS_ABATE).
     * Só ataca parado e dentro do alcance; durante o ataque não anda.
     */
    private fun updatePlayer(dt: Float) {
        target?.let { if (it.dead) { target = null; pendingSkill = -1 } }
        if (hstate == HS_ATK) {
            updateAttack(dt)
            glideStop(dt)
            moving = curSpeed > 6f
            return
        }
        val n = npcTarget
        val t = target
        if (n != null) {
            val d = dist(px, py, n.x, n.y + 12f)
            if (d < 60f) {
                npcTarget = null
                glideStop(dt)
                interact(n)
            } else moveToward(n.x, n.y + 12f, dt, d - 50f)
        } else if (t != null && !p.job.ranged && pendingSkill < 0) {
            // corpo a corpo: vai até uma posição AO LADO do alvo (mesma altura), do lado em que já está,
            // para o golpe de lado acertar o monstro de frente
            var side = if (px <= t.x) -1f else 1f
            val gap = t.d.size + 26f
            if (t.x + side * gap < 40f || t.x + side * gap > map.w - 40f) side = -side
            val sx = t.x + side * gap
            val sy = t.y
            val dSlot = dist(px, py, sx, sy)
            if (dSlot > (if (hstate == HS_POS) 30f else 8f)) {
                moveToward(sx, sy, dt, dSlot)
                hstate = HS_WALK
                posT = 0.12f
            } else {
                glideStop(dt)
                face = if (t.x >= px) 1f else -1f
                hstate = HS_POS
                if (curSpeed < 8f) posT -= dt
                if (posT <= 0f && atkT <= 0f) startAttack(t)
            }
        } else if (t != null) {
            val range = if (pendingSkill >= 0) max(SKILLS[pendingSkill].range, 60f) else p.job.range
            val d = dist(px, py, t.x, t.y) - t.d.size
            if (d > range) {
                moveToward(t.x, t.y, dt, d - range)
                hstate = HS_WALK
                posT = 0.12f
            } else {
                // chegou: desacelera, para e vira suavemente (~120ms) antes de atacar
                glideStop(dt)
                face = if (t.x >= px) 1f else -1f
                hstate = HS_POS
                if (curSpeed < 8f) posT -= dt
                if (posT <= 0f) {
                    if (pendingSkill >= 0) {
                        val s = pendingSkill
                        pendingSkill = -1
                        if (canCast(s, true)) cast(s, t)
                    } else if (atkT <= 0f) startAttack(t)
                }
            }
        } else if (hasDest) {
            val d = dist(px, py, destX, destY)
            if (d < 4f) {
                hasDest = false
                glideStop(dt)
            } else moveToward(destX, destY, dt, d)
        } else glideStop(dt)
        moving = curSpeed > 6f
        if (moving) walk += dt
        if (hstate != HS_POS && hstate != HS_ATK) hstate = if (moving) HS_WALK else if (postKillT > 0f) HS_POST else HS_IDLE
        // floresta: estrada da direita e trilha do topo ainda não levam a lugar nenhum
        if (exitMsgT > 0f) exitMsgT -= dt
        if (map.id == 1) {
            val ex = Forest.exitAt(px, py)
            if (ex != 0) {
                if (exitMsgT <= 0f) {
                    addLog(if (ex == 2) "Estrada para o leste: em breve!" else "Trilha da floresta: em breve!", C_GOLD)
                    texts.add(FText(px, py - 95f, "Em breve!", C_GOLD, 1.2f, false))
                    exitMsgT = 3f
                }
                // volta alguns passos para dentro do mapa
                hasDest = false
                curSpeed = 0f
                if (ex == 2) tryMove(px - 30f, py) else tryMove(px, py + 30f)
            }
        }
        for (i in portals.indices) {
            val pt = portals[i]
            if (dist(px, py, pt.x, pt.y) < 40f) {
                changeMap(pt.dest, pt.entry)
                return
            }
        }
    }

    /** Velocidade de caminhada (mais baixa que antes, constante). */
    private fun walkSpeed() = p.speed * 0.72f

    /** Anda com aceleração (~150ms) e desacelera ao se aproximar do destino (remain = distância que falta). */
    private fun moveToward(tx: Float, ty: Float, dt: Float, remain: Float) {
        val dx = tx - px
        val dy = ty - py
        val d = sqrt(dx * dx + dy * dy)
        if (d < 1f) return
        val maxV = walkSpeed()
        val acc = maxV / 0.15f
        val brake = sqrt(2f * acc * max(0f, remain))
        val tv = min(maxV, max(brake, 25f))
        curSpeed = if (curSpeed < tv) min(tv, curSpeed + acc * dt) else max(tv, curSpeed - acc * dt)
        val step = min(d, curSpeed * dt)
        lastDx = dx / d
        lastDy = dy / d
        if (!tryMove(px + lastDx * step, py + lastDy * step)) {
            // bloqueado (tronco, pedra, borda): tenta contornar girando a direção, começando pelo lado que funcionou antes
            var ok = false
            for (k in STEER.indices) {
                val a = STEER[k] * steerSide
                val cs = kotlin.math.cos(a)
                val sn = kotlin.math.sin(a)
                val rx = lastDx * cs - lastDy * sn
                val ry = lastDx * sn + lastDy * cs
                if (tryMove(px + rx * step, py + ry * step)) {
                    ok = true
                    break
                }
                val b = -a
                val cb = kotlin.math.cos(b)
                val sb = kotlin.math.sin(b)
                if (tryMove(px + (lastDx * cb - lastDy * sb) * step, py + (lastDx * sb + lastDy * cb) * step)) {
                    steerSide = -steerSide
                    ok = true
                    break
                }
            }
            if (!ok) curSpeed *= 0.5f
        }
        if (abs(dx) > 2f) face = sign(dx)
    }

    private val STEER = floatArrayOf(0.6f, 1.2f, 1.8f)
    private var steerSide = 1f

    /** Sem destino: desliza desacelerando (~150ms) em vez de parar de uma vez. */
    private fun glideStop(dt: Float) {
        if (curSpeed <= 0f) return
        val acc = walkSpeed() / 0.15f
        curSpeed = max(0f, curSpeed - acc * dt)
        if (!tryMove(px + lastDx * curSpeed * dt, py + lastDy * curSpeed * dt)) curSpeed = 0f
    }

    /** Pode pisar? (só a floresta tem colisão por enquanto) */
    fun walkable(x: Float, y: Float): Boolean = map.id != 1 || Forest.walkable(x, y)

    /** Move o herói respeitando a colisão; se bloquear, tenta deslizar só em X ou só em Y. */
    private fun tryMove(nx0: Float, ny0: Float): Boolean {
        val nx = nx0.coerceIn(30f, map.w - 30f)
        val ny = ny0.coerceIn(60f, map.h - 20f)
        if (walkable(nx, ny)) { px = nx; py = ny; return true }
        if (walkable(nx, py)) { px = nx; return true }
        if (walkable(px, ny)) { py = ny; return true }
        return false
    }

    /** Começa um golpe: a cadência (golpes/s) continua sendo p.delay, de início a início. */
    private fun startAttack(t: Mob) {
        hstate = HS_ATK
        atkPhaseT = 0f
        swingDone = false
        impactDone = false
        killedInAttack = false
        atkMob = t
        atkT = p.delay
        // golpes mais rápidos que a sequência completa: encurta todas as fases na mesma proporção (nenhuma some)
        atkScale = ((p.delay - HIT_STOP) / ATK_END).coerceIn(0.15f, 1f)
        val dx = t.x - px
        val dy = t.y - py
        val dd = max(1f, sqrt(dx * dx + dy * dy))
        atkDirX = dx / dd
        atkDirY = dy / dd
        atkDist = dd
        face = if (dx >= 0f) 1f else -1f
        atkAnim = 1f
        atkSeq++
    }

    /** Preparação -> impacto (dano aplicado aqui) -> hit stop -> recuperação. */
    private fun updateAttack(dt: Float) {
        if (hitStopT > 0f) return
        atkPhaseT += dt
        val m = atkMob
        if (!swingDone && atkPhaseT >= ATK_WIND * atkScale) {
            swingDone = true
            // som do golpe no início da investida; o ritmo acompanha a velocidade de ataque (AGI/DES)
            snd(if (p.job.magic) Audio.S_MAGIC else if (p.job.ranged) Audio.S_ARROW else Audio.S_SWING, 0.9f + rnd.nextFloat() * 0.2f)
        }
        if (!impactDone && atkPhaseT >= ATK_IMPACT * atkScale) {
            impactDone = true
            if (m != null && !m.dead) {
                resolveAttack(m)
                fx.add(Fx(FX_FLASH, m.x, m.y - m.d.size, 0f, 0f, m.d.size * 1.8f + 14f, C_WHITE, 0.25f))
                hitStopT = HIT_STOP
                hitStopMob = m
                if (m.dead) killedInAttack = true
            }
        }
        if (atkPhaseT >= ATK_END * atkScale) {
            atkMob = null
            hstate = if (killedInAttack || target == null) HS_POST else HS_POS
            posT = 0f
        }
    }

    // ================= COMBATE =================
    fun hitChance(m: MonsterDef) = (0.8f + (p.hit - m.flee) / 100f).coerceIn(0.05f, 1f)
    fun reduce(d: Float, def: Int, magic: Boolean): Float {
        val e = if (magic) def * 0.5f else def.toFloat()
        return d * (1f - e / (e + 150f))
    }

    private fun vari() = 0.9f + rnd.nextFloat() * 0.2f
    fun basicDmg() = if (p.job.magic) p.matk * 0.75f else p.atk

    /** Resultado do golpe no quadro de impacto (mesmas fórmulas de antes: acerto, crítico, dano). */
    private fun resolveAttack(m: Mob) {
        if (p.job.ranged) {
            fx.add(Fx(FX_SHOT, px + face * 12f, py - 28f, m.x, m.y - m.d.size, 0f,
                if (p.job.magic) col(0xFFB388FF) else col(0xFFFFF8E1), 0.16f))
        } else {
            fx.add(Fx(FX_SLASH, m.x, m.y - m.d.size, 0f, 0f, m.d.size + 8f, col(0xCCFFFFFF), 0.15f))
        }
        if (p.job.magic) {
            dmgMob(m, reduce(p.matk * 0.75f * vari(), m.d.def, true), false)
            return
        }
        if (rnd.nextFloat() > hitChance(m.d)) {
            texts.add(FText(m.x + rnd.nextInt(-8, 8), dmgTextY(m), "Miss", C_MISS, 1.1f, false, TXT_DMG, 0.9f))
            snd(Audio.S_MISS)
            return
        }
        val crit = rnd.nextFloat() < p.crit
        val dmg = if (crit) p.atk * 1.4f * vari() else reduce(p.atk * vari(), m.d.def, false)
        dmgMob(m, dmg, crit)
    }

    fun dmgMob(m: Mob, dmg: Float, crit: Boolean, cor: Int = if (crit) C_YELLOW else C_WHITE) {
        if (m.dead) return
        val v = max(1, dmg.toInt())
        m.hp -= v
        m.aggro = true
        m.flash = 0.08f
        m.kb = 1f
        m.kbDir = if (m.x >= px) 1f else -1f
        if (cor != C_PURPLE) snd(if (crit) Audio.S_CRIT else Audio.hitFor(m.d), Audio.pitchFor(m.d) * (0.94f + rnd.nextFloat() * 0.12f))
        // golpes mais fortes (fração maior do HP do monstro) geram número maior
        val sz = (0.9f + 0.8f * min(1f, v * 2f / m.d.hp)) * (if (crit) 1.2f else 1f)
        texts.add(FText(m.x + rnd.nextInt(-8, 8), dmgTextY(m), if (crit) "$v!" else "$v", cor, 1.4f, crit, TXT_DMG, sz))
        if (crit) {
            shakeT = 0.14f
            vibrate(25)
            if (p.fx) for (k in 0 until 8) {
                parts.spawn(m.x, m.y - m.d.size, rnd.nextInt(-160, 160).toFloat(), rnd.nextInt(-220, -40).toFloat(),
                    0.45f, 2.5f, 500f, C_YELLOW)
            }
        }
        if (m.hp <= 0) kill(m)
    }

    /** Números de dano: acima da cabeça do monstro, acima da barra de HP, com leve variação. */
    private fun dmgTextY(m: Mob) = m.y - m.d.size * 2.6f - 24f + rnd.nextInt(-4, 4)

    /** Texto do herói em fila (+EXP, +Zeny, JOB UP...), sem sobrepor. */
    fun heroText(s: String, cor: Int, big: Boolean = false) {
        heroQueue.add(FText(px, py - 92f, s, cor, if (big) 1.6f else 0.9f, big, if (big) TXT_BIG else TXT_HERO))
    }

    private fun spawnDrop(x: Float, y: Float, kind: Int, amount: Long, label: String, cor: Int) {
        for (d in drops) {
            if (d.on) continue
            d.on = true
            d.x = x; d.y = y; d.z = 4f; d.t = 0f
            d.vz = 210f + rnd.nextFloat() * 90f
            d.vx = rnd.nextInt(-70, 70).toFloat()
            d.vy = rnd.nextInt(-30, 30).toFloat()
            d.kind = kind; d.amount = amount; d.label = label; d.cor = cor
            return
        }
    }

    private fun updateDrops(dt: Float) {
        for (d in drops) {
            if (!d.on) continue
            d.t += dt
            if (d.t < 1.0f) {
                d.x += d.vx * dt; d.y += d.vy * dt
                d.vz -= 900f * dt; d.z += d.vz * dt
                if (d.z < 0f) {
                    d.z = 0f; d.vz = -d.vz * 0.45f; d.vx *= 0.5f; d.vy *= 0.5f
                }
            } else {
                val ty = py - 18f
                val dd = dist(d.x, d.y, px, ty)
                val sp = 450f + (d.t - 1.0f) * 1000f
                if (dd < sp * dt + 12f) {
                    d.on = false
                    snd(when (d.kind) { 0 -> Audio.S_COIN; 1 -> Audio.S_POP; else -> Audio.S_CARD }, if (d.kind == 1) 1.5f else 1f)
                    val txt = when (d.kind) {
                        0 -> "+${d.amount} Zeny"
                        1 -> if (d.amount > 1) "+${d.amount} ${d.label}" else "+${d.label}"
                        else -> "★ ${d.label}"
                    }
                    heroText(txt, if (d.kind == 0) C_GOLD else d.cor, d.kind == 2)
                    // brilho ao coletar
                    if (p.fx) for (k in 0 until 8) parts.spawn(px + rnd.nextInt(-6, 6), py - 22f, rnd.nextInt(-70, 70).toFloat(), rnd.nextInt(-110, -20).toFloat(), 0.4f, 2.2f, 120f, if (k % 2 == 0) C_GOLD else C_WHITE)
                } else {
                    d.x += (px - d.x) / dd * sp * dt
                    d.y += (ty - d.y) / dd * sp * dt
                    d.z = max(0f, d.z - dt * 60f)
                }
            }
        }
    }

    private fun levelParticles() {
        for (k in 0 until 3) {
            parts.spawn(px + rnd.nextInt(-24, 24), py - rnd.nextInt(0, 12), rnd.nextInt(-15, 15).toFloat(),
                -(90f + rnd.nextFloat() * 90f), 0.9f + rnd.nextFloat() * 0.5f, 1.5f + rnd.nextFloat() * 2.5f, -40f,
                if (k == 0) C_WHITE else C_GOLD)
        }
    }

    private fun kill(m: Mob) {
        m.dead = true
        m.dying = DEATH_TIME
        p.kills++
        if (target === m) target = null
        // pausa após o abate: moedas caem e o herói fica em idle antes do próximo alvo
        postKillT = POST_KILL
        val d = m.d
        val mul = (1f + p.expPct / 100f) * EXP_RATE
        val e = (d.exp * mul).toLong()
        val j = (d.jexp * mul).toLong()
        heroText("+$e EXP", col(0xFF80D8FF))
        snd(Audio.S_POP, Audio.pitchFor(d))
        val z = (d.zeny * (0.8f + rnd.nextFloat() * 0.4f)).toLong()
        p.zeny += z
        if (!p.fx) snd(Audio.S_COIN)
        if (p.fx && z > 0) spawnDrop(m.x, m.y, 0, z, "", C_GOLD)
        if (rnd.nextFloat() < (if (d.boss) 1f else 0.35f)) {
            val n = if (d.boss) 5 else 1
            p.loot[d.id] += n
            if (d.boss) addLog("Obteve 5x ${d.loot}", C_WHITE)
            if (p.fx) spawnDrop(m.x, m.y, 1, n.toLong(), d.loot, col(0xFFFFE0B2))
        }
        if (rnd.nextFloat() < (if (d.boss) 0.6f else 0.05f)) {
            val t = potionTierFor(d.lv)
            val n = if (d.boss) 5 else 1
            p.potions[t] += n
            addLog("Obteve ${n}x ${POTIONS[t].nome}", C_WHITE)
            if (p.fx) spawnDrop(m.x, m.y, 1, n.toLong(), POTIONS[t].nome, POTIONS[t].cor)
        }
        if (rnd.nextFloat() < (if (d.boss) 0.10f else 0.005f)) {
            p.cards[d.id]++
            addLog("★ Obteve ${d.cardNome}! (${d.card.texto(d.cardV)})", C_GOLD)
            heroText("★ CARTA! ★", C_GOLD, true)
            if (!p.fx) snd(Audio.S_CARD)
            if (p.fx) spawnDrop(m.x, m.y, 2, 1, d.cardNome, C_GOLD)
        }
        if (d.boss) {
            bossT[map.id] = 480f
            addLog("${d.nome} foi derrotado!", C_GOLD)
        }
        gainExp(e, j)
    }

    fun gainExp(e: Long, j: Long, quiet: Boolean = false): IntArray {
        var gb = 0
        var gj = 0
        if (p.lv < 99) {
            p.exp += e
            while (p.lv < 99 && p.exp >= expNeed(p.lv)) {
                p.exp -= expNeed(p.lv)
                p.lv++
                gb++
                p.statPts += p.lv / 5 + 3
            }
            if (p.lv >= 99) p.exp = 0
        }
        if (p.jlv < p.job.jobMax) {
            p.jexp += j
            while (p.jlv < p.job.jobMax && p.jexp >= jobNeed(p.jlv, p.job)) {
                p.jexp -= jobNeed(p.jlv, p.job)
                p.jlv++
                gj++
                p.skillPts++
            }
            if (p.jlv >= p.job.jobMax) p.jexp = 0
        }
        if (gb > 0) {
            p.recalc()
            p.hp = p.maxHp
            p.sp = p.maxSp
            if (!quiet) {
                lvlFx = 1.6f
                vibrate(120)
                snd(Audio.S_LEVELUP)
                // LEVEL UP sozinho, centralizado acima do herói; a fila de textos espera
                texts.add(FText(px, py - 100f, "LEVEL UP!", C_GOLD, 1.6f, true, TXT_BIG))
                lvlTextT = 1.3f
                addLog("Nível base ${p.lv}! Você tem ${p.statPts} pontos de atributo.", C_GOLD)
            }
        }
        if (gj > 0 && !quiet) {
            if (gb == 0) snd(Audio.S_JOBUP)
            heroText("JOB UP!", col(0xFF80D8FF), true)
            addLog("Nível de classe ${p.jlv}! +1 ponto de habilidade.", col(0xFF80D8FF))
            if (p.job == Job.APRENDIZ && p.jlv >= 10) addLog("Fale com o Mestre de Classes na Vila para escolher sua classe!", C_GOLD)
        }
        return intArrayOf(gb, gj)
    }

    private fun updateMobs(dt: Float) {
        for (i in mobs.indices) {
            val m = mobs[i]
            if (m.dead) {
                // morte: 350ms de animação (achata e pisca) e no fim vira partículas; só então sai da lista
                if (m.dying > 0f) {
                    m.dying -= dt
                    if (m.flash > 0f) m.flash -= dt
                    if (!m.burst && m.dying < 0.1f) {
                        m.burst = true
                        for (k in 0 until 14) {
                            parts.spawn(m.x + rnd.nextInt(-8, 8), m.y - m.d.size * 0.8f, rnd.nextInt(-120, 120).toFloat(),
                                rnd.nextInt(-170, -30).toFloat(), 0.45f + rnd.nextFloat() * 0.25f, 2f + rnd.nextFloat() * 2.5f, 260f,
                                if (k % 3 == 0) C_WHITE else m.d.c1)
                        }
                    }
                }
                continue
            }
            // hit stop: o alvo congela junto com o herói
            if (hitStopT > 0f && m === hitStopMob) continue
            m.anim += dt
            if (m.flash > 0f) m.flash -= dt
            if (m.kb > 0f) m.kb = max(0f, m.kb - dt * 6f)
            if (m.atkAnim > 0f) m.atkAnim -= dt * 4f
            if (m.poison > 0f) {
                m.poison -= dt
                m.poisonTick -= dt
                if (m.poisonTick <= 0f) {
                    m.poisonTick = 1f
                    dmgMob(m, m.poisonDps, false, C_PURPLE)
                }
                if (m.dead) continue
            }
            if (m.frozen > 0f) {
                m.frozen -= dt
                continue
            }
            m.atkT -= dt
            val d = dist(m.x, m.y, px, py)
            if (!dead && (m.aggro || (m.d.aggressive && d < 260f))) {
                m.aggro = true
                if (d > 900f) {
                    m.aggro = false
                    continue
                }
                m.face = if (px >= m.x) 1f else -1f
                val reach = m.d.size + 24f
                if (d > reach) mobMove(m, px, py, m.d.speed, dt)
                else if (m.atkT <= 0f) {
                    mobAttack(m)
                    m.atkT = if (m.d.boss) 1.2f else 1.6f
                }
            } else {
                if (dead) m.aggro = false
                m.wt -= dt
                if (m.wt <= 0f) {
                    m.wt = 2f + rnd.nextFloat() * 4f
                    m.wx = (m.x + rnd.nextInt(-200, 200)).coerceIn(320f, map.w - 320f)
                    m.wy = (m.y + rnd.nextInt(-200, 200)).coerceIn(150f, map.h - 80f)
                }
                if (dist(m.x, m.y, m.wx, m.wy) > 5f) mobMove(m, m.wx, m.wy, m.d.speed * 0.45f, dt)
            }
        }
        // separação simples
        for (i in mobs.indices) {
            val a = mobs[i]
            if (a.dead) continue
            for (k in i + 1 until mobs.size) {
                val b = mobs[k]
                if (b.dead) continue
                val dx = b.x - a.x
                val dy = b.y - a.y
                val dd = sqrt(dx * dx + dy * dy)
                val mn = (a.d.size + b.d.size) * 0.7f
                if (dd < mn && dd > 0.01f) {
                    val push = (mn - dd) * 0.25f
                    a.x -= dx / dd * push; a.y -= dy / dd * push
                    b.x += dx / dd * push; b.y += dy / dd * push
                }
            }
        }
    }

    private fun mobMove(m: Mob, tx: Float, ty: Float, speed: Float, dt: Float) {
        val dx = tx - m.x
        val dy = ty - m.y
        val d = sqrt(dx * dx + dy * dy)
        if (d < 1f) return
        val step = min(d, speed * dt)
        val nx = m.x + dx / d * step
        val ny = m.y + dy / d * step
        if (walkable(nx, ny)) { m.x = nx; m.y = ny }
        else if (walkable(nx, m.y)) m.x = nx
        else if (walkable(m.x, ny)) m.y = ny
        else m.wt = 0f // bloqueado: escolhe outro destino
        if (abs(dx) > 1f) m.face = sign(dx)
    }

    private fun mobAttack(m: Mob) {
        m.atkAnim = 1f
        val dodge = (0.05f + (p.flee - m.d.hit) / 100f).coerceIn(0.05f, 0.9f)
        if (rnd.nextFloat() < dodge) {
            texts.add(FText(px + rnd.nextInt(-8, 8), py - 74f, "Miss", C_MISS, 0.6f, false, TXT_DMG))
            snd(Audio.S_MISS, 1.3f)
            return
        }
        val dmg = max(1f, m.d.atk * vari() * (1f - p.def / (p.def + 120f)) - p.tot[VIT] * 0.3f).toInt()
        p.hp -= dmg
        snd(Audio.S_HURT, 0.95f + rnd.nextFloat() * 0.1f)
        texts.add(FText(px + rnd.nextInt(-8, 8), py - 74f, "$dmg", C_RED, 0.6f, false, TXT_DMG))
        if (p.hp <= 0) {
            p.hp = 0
            die()
        }
    }

    private fun die() {
        dead = true
        target = null; pendingSkill = -1; hasDest = false; npcTarget = null
        // Aprendizes não perdem EXP ao morrer
        lastLoss = if (p.lv < 99 && p.job != Job.APRENDIZ) (expNeed(p.lv) * 0.01).toLong() else 0L
        p.exp = max(0L, p.exp - lastLoss)
        panel = Panel.MORTE
        snd(Audio.S_DEATH)
        addLog("Você foi derrotado...", C_RED)
    }

    fun respawn() {
        dead = false
        p.recalc()
        p.hp = p.maxHp
        p.sp = p.maxSp
        panel = Panel.NONE
        changeMap(0, 2)
    }

    // ================= SPAWN =================
    private fun spawning(dt: Float) {
        if (map.id == 0) return
        spawnT -= dt
        var normal = 0
        var hasBoss = false
        for (i in mobs.indices) {
            if (mobs[i].dead) continue
            if (mobs[i].d.boss) hasBoss = true else normal++
        }
        if (normal < map.maxMobs && spawnT <= 0f) {
            spawnMob(MONSTERS[map.mobs[rnd.nextInt(map.mobs.size)]])
            spawnT = 2.5f
        }
        if (map.boss >= 0 && !hasBoss) {
            bossT[map.id] -= dt
            if (bossT[map.id] <= 0f) {
                val b = spawnMob(MONSTERS[map.boss])
                bossT[map.id] = 480f
                addLog("⚠ ${b.d.nome} apareceu no mapa!", C_PURPLE)
            }
        }
    }

    private fun spawnMob(d: MonsterDef): Mob {
        var x: Float
        var y: Float
        var tries = 0
        val mx = if (map.id == 1) 200f else 340f
        do {
            x = mx + rnd.nextFloat() * (map.w - 2 * mx)
            y = 160f + rnd.nextFloat() * (map.h - 240f)
            tries++
        } while ((dist(x, y, px, py) < (if (map.id == 1) 380f else 520f) || !walkable(x, y)) && tries < 60)
        val m = Mob(d, x, y)
        mobs.add(m)
        return m
    }

    // ================= AUTO =================
    private fun autoLogic() {
        if (manualT > 0f || npcTarget != null) return
        if (hasDest && target == null) return
        // o próximo alvo só é escolhido depois do golpe terminar e da pausa pós-abate
        if (hstate == HS_ATK || postKillT > 0f) return
        if (target == null && pendingSkill < 0) target = pickTarget()
        autoSkills()
    }

    private fun pickTarget(): Mob? {
        var best: Mob? = null
        var bd = Float.MAX_VALUE
        for (i in mobs.indices) {
            val m = mobs[i]
            if (m.dead) continue
            // não puxa monstros passivos muito acima do nível (quem já está atacando é prioridade)
            if (!m.aggro && m.d.lv > p.lv + 6) continue
            var d = dist(px, py, m.x, m.y)
            if (m.aggro) d *= 0.4f
            if (d < bd) {
                bd = d
                best = m
            }
        }
        return best
    }

    private fun autoSkills() {
        if (castLock > 0f) return
        val ids = autoOrders[p.job.ordinal]
        val t = target
        for (id in ids) {
            if (!p.autoSkill[id] || !canCast(id, false)) continue
            val s = SKILLS[id]
            val ok = when (s.kind) {
                SK.CURA -> p.hp < p.maxHp * 0.55f
                SK.BUFF -> p.buff[id] <= 0f && t != null && dist(px, py, t.x, t.y) < 400f
                SK.AREA_SELF -> countNear(px, py, s.radius) >= 2 ||
                        (t != null && t.d.boss && dist(px, py, t.x, t.y) - t.d.size < s.radius)
                SK.AREA_ALVO -> t != null && inRange(t, s.range)
                SK.DANO -> t != null && inRange(t, s.range) && t.hp > basicDmg() * 1.2f
            }
            if (ok) {
                cast(id, if (s.kind == SK.DANO || s.kind == SK.AREA_ALVO) t else null)
                return
            }
        }
    }

    private fun autoPotion() {
        if (p.hp < p.maxHp * 0.5f) drinkHp(false)
        else if (p.autoMode && p.sp < p.maxSp * 0.2f) drinkSp(false)
    }

    private fun countNear(x: Float, y: Float, r: Float): Int {
        var n = 0
        for (i in mobs.indices) {
            val m = mobs[i]
            if (!m.dead && dist(x, y, m.x, m.y) - m.d.size <= r) n++
        }
        return n
    }

    fun inRange(m: Mob, r: Float) = dist(px, py, m.x, m.y) - m.d.size <= max(r, 60f)

    private fun nearestMob(r: Float): Mob? {
        var best: Mob? = null
        var bd = r
        for (i in mobs.indices) {
            val m = mobs[i]
            if (m.dead) continue
            val d = dist(px, py, m.x, m.y)
            if (d < bd) { bd = d; best = m }
        }
        return best
    }

    // ================= HABILIDADES =================
    fun canCast(id: Int, ignoreLock: Boolean): Boolean {
        val lv = p.skills[id]
        return lv > 0 && cd[id] <= 0f && (ignoreLock || castLock <= 0f) && p.sp >= SKILLS[id].sp(lv) && !dead
    }

    fun useSkill(id: Int) {
        val lv = p.skills[id]
        val s = SKILLS[id]
        if (lv <= 0 || dead) return
        if (cd[id] > 0f) return
        if (p.sp < s.sp(lv)) {
            texts.add(FText(px, py - 85f, "SP insuficiente", C_BLUE, 1f, false))
            return
        }
        when (s.kind) {
            SK.CURA, SK.BUFF -> cast(id, null)
            SK.AREA_SELF -> {
                if (map.id == 0) {
                    texts.add(FText(px, py - 85f, "Não há monstros aqui", C_GRAY, 1f, false)); return
                }
                cast(id, null)
            }
            else -> {
                val t = target ?: nearestMob(700f)
                if (t == null) {
                    texts.add(FText(px, py - 85f, "Sem alvo", C_GRAY, 1f, false)); return
                }
                target = t; npcTarget = null; hasDest = false
                if (inRange(t, s.range)) cast(id, t) else pendingSkill = id
            }
        }
    }

    private fun cast(id: Int, t: Mob?) {
        val s = SKILLS[id]
        val lv = p.skills[id]
        val l = lv.toFloat()
        p.sp -= s.sp(lv)
        cd[id] = s.cd
        castLock = 0.4f
        atkAnim = 1f
        castSeq++
        atkT = max(atkT, 0.3f)
        texts.add(FText(px, py - 80f, "${s.nome}!!", s.cor, 1.0f, false))
        snd(if (id == 0) Audio.S_HEAL else Audio.S_SKILL)
        when (id) {
            0 -> {
                val h = (10 * lv + p.maxHp * 0.03f * lv).toInt()
                healP(h)
                fx.add(Fx(FX_RING, px, py - 25f, 0f, 0f, 45f, col(0xCC69F0AE), 0.5f))
            }
            1 -> t?.let {
                fx.add(Fx(FX_SLASH, it.x, it.y - it.d.size, 0f, 0f, it.d.size + 18f, s.cor, 0.25f))
                dmgMob(it, reduce(p.atk * (1.5f + 0.3f * l) * vari(), it.d.def, false), false, C_ORANGE)
            }
            2 -> {
                fx.add(Fx(FX_RING, px, py, 0f, 0f, s.radius, col(0xCCBCAAA4), 0.4f))
                areaDmg(px, py, s.radius, 1f + 0.2f * l, false)
            }
            3, 9, 12 -> {
                p.buff[id] = s.dur
                fx.add(Fx(FX_RING, px, py - 25f, 0f, 0f, 55f, s.cor, 0.6f))
            }
            4 -> t?.let {
                fx.add(Fx(FX_SHOT, px, py - 30f, it.x, it.y - it.d.size, 1f, s.cor, 0.2f))
                fx.add(Fx(FX_RING, it.x, it.y - it.d.size, 0f, 0f, 30f, s.cor, 0.35f))
                dmgMob(it, reduce(p.matk * (1.2f + 0.35f * l) * vari(), it.d.def, true), false, C_ORANGE)
            }
            5 -> t?.let {
                for (k in 0 until 4) {
                    val ox = rnd.nextInt(-60, 60).toFloat()
                    val oy = rnd.nextInt(-40, 40).toFloat()
                    fx.add(Fx(FX_BOLT, it.x + ox, it.y + oy, it.x + ox, it.y + oy, 0f, s.cor, 0.3f + k * 0.05f))
                }
                fx.add(Fx(FX_RING, it.x, it.y, 0f, 0f, s.radius, col(0x88FFEB3B), 0.4f))
                areaDmg(it.x, it.y, s.radius, 1f + 0.25f * l, true)
            }
            6 -> {
                fx.add(Fx(FX_RING, px, py, 0f, 0f, s.radius, col(0xDD81D4FA), 0.5f))
                val fr = 1.5f + 0.25f * l
                for (i in mobs.indices) {
                    val m = mobs[i]
                    if (m.dead || dist(px, py, m.x, m.y) - m.d.size > s.radius) continue
                    dmgMob(m, reduce(p.matk * (0.8f + 0.15f * l) * vari(), m.d.def, true), false, C_BLUE)
                    if (!m.dead) m.frozen = if (m.d.boss) fr * 0.3f else fr
                }
            }
            7 -> t?.let {
                fx.add(Fx(FX_SHOT, px, py - 30f, it.x, it.y - it.d.size, 0f, s.cor, 0.14f))
                fx.add(Fx(FX_SHOT, px, py - 22f, it.x, it.y - it.d.size + 8f, 0f, s.cor, 0.2f))
                repeat(2) { _ -> dmgMob(it, reduce(p.atk * (0.9f + 0.15f * l) * vari(), it.d.def, false), false, C_GREEN) }
            }
            8 -> t?.let {
                fx.add(Fx(FX_RAIN, it.x, it.y, 0f, 0f, s.radius, s.cor, 0.5f))
                areaDmg(it.x, it.y, s.radius, 0.8f + 0.2f * l, false)
            }
            10 -> t?.let {
                fx.add(Fx(FX_SLASH, it.x, it.y - it.d.size, 0f, 0f, it.d.size + 14f, s.cor, 0.25f))
                dmgMob(it, reduce(p.atk * (1.3f + 0.2f * l) * vari(), it.d.def, false), false, C_GREEN)
                if (!it.dead) {
                    it.poison = 5f
                    it.poisonDps = p.atk * 0.08f * l
                    it.poisonTick = 1f
                }
            }
            11 -> {
                fx.add(Fx(FX_SLASH, px, py - 20f, 0f, 0f, s.radius, s.cor, 0.3f))
                fx.add(Fx(FX_RING, px, py, 0f, 0f, s.radius, col(0x99CFD8DC), 0.3f))
                areaDmg(px, py, s.radius, 0.9f + 0.18f * l, false)
            }
        }
    }

    private fun areaDmg(x: Float, y: Float, r: Float, mult: Float, magic: Boolean) {
        for (i in mobs.indices) {
            val m = mobs[i]
            if (m.dead || dist(x, y, m.x, m.y) - m.d.size > r) continue
            val base = if (magic) p.matk else p.atk
            dmgMob(m, reduce(base * mult * vari(), m.d.def, magic), false, C_ORANGE)
        }
    }

    private fun healP(h: Int) {
        p.hp = min(p.maxHp, p.hp + h)
        texts.add(FText(px, py - 70f, "+$h", C_GREEN, 1f, false))
    }

    // ================= ITENS =================
    fun drinkHp(manual: Boolean): Boolean {
        if (potT > 0f || dead) return false
        val missing = p.maxHp - p.hp
        if (missing <= 0) return false
        var pick = -1
        for (i in 2 downTo 0) if (p.potions[i] > 0 && POTIONS[i].hp <= missing * 1.15f) { pick = i; break }
        if (pick < 0) for (i in 0..2) if (p.potions[i] > 0) { pick = i; break }
        if (pick < 0) {
            if (manual) texts.add(FText(px, py - 85f, "Sem poções", C_GRAY, 1f, false))
            return false
        }
        p.potions[pick]--
        healP(POTIONS[pick].hp)
        snd(Audio.S_POTION)
        potT = 0.5f
        return true
    }

    fun drinkSp(manual: Boolean): Boolean {
        if (potT > 0f || dead) return false
        if (p.sp >= p.maxSp) return false
        if (p.potions[3] <= 0) {
            if (manual) texts.add(FText(px, py - 85f, "Sem poções de mana", C_GRAY, 1f, false))
            return false
        }
        p.potions[3]--
        p.sp = min(p.maxSp, p.sp + POTIONS[3].sp)
        snd(Audio.S_POTION, 1.2f)
        texts.add(FText(px, py - 70f, "+${POTIONS[3].sp} SP", C_BLUE, 1f, false))
        potT = 0.5f
        return true
    }

    fun buyPotion(i: Int, n: Int) {
        val cost = POTIONS[i].preco.toLong() * n
        if (p.zeny < cost) return
        p.zeny -= cost
        p.potions[i] += n
    }

    fun buyWeapon() {
        val nx = p.wTier + 1
        if (nx > 6 || p.lv < EQ_LV[nx] || p.zeny < WEAPON_PRICE[nx]) return
        p.zeny -= WEAPON_PRICE[nx]
        p.wTier = nx; p.wRef = 0
        if (p.wCard >= 0) { p.cards[p.wCard]++; p.wCard = -1 }
        addLog("Comprou ${p.weaponName()}!", C_GOLD)
    }

    fun buyArmor() {
        val nx = p.aTier + 1
        if (nx > 6 || p.lv < EQ_LV[nx] || p.zeny < ARMOR_PRICE[nx]) return
        p.zeny -= ARMOR_PRICE[nx]
        p.aTier = nx; p.aRef = 0
        if (p.aCard >= 0) { p.cards[p.aCard]++; p.aCard = -1 }
        addLog("Comprou ${p.armorName()}!", C_GOLD)
    }

    fun lootValue(): Long {
        var t = 0L
        for (i in p.loot.indices) t += p.loot[i].toLong() * MONSTERS[i].lootValue
        return t
    }

    fun sellLoot() {
        val v = lootValue()
        if (v <= 0) return
        p.zeny += v
        p.loot.fill(0)
        addLog("Vendeu espólios por $v zeny.", C_GOLD)
    }

    fun refine(weapon: Boolean) {
        val tier = if (weapon) p.wTier else p.aTier
        val ref = if (weapon) p.wRef else p.aRef
        if (ref >= 10) return
        val cost = refineCost(tier, ref)
        if (p.zeny < cost) return
        p.zeny -= cost
        val ok = rnd.nextInt(100) < REFINE_CHANCE[ref]
        val novo = if (ok) ref + 1 else max(0, ref - 1)
        if (weapon) p.wRef = novo else p.aRef = novo
        val nome = if (weapon) p.weaponName() else p.armorName()
        refineMsg = if (ok) "Sucesso! +$novo $nome" else "Falhou... o item caiu para +$novo"
        snd(if (ok) Audio.S_JOBUP else Audio.S_HIT_METAL, if (ok) 1f else 0.7f)
        addLog(refineMsg, if (ok) C_GOLD else C_RED)
    }

    fun insertCard(id: Int) {
        if (p.cards[id] <= 0) return
        val m = MONSTERS[id]
        p.cards[id]--
        if (m.card.arma) {
            if (p.wCard >= 0) p.cards[p.wCard]++
            p.wCard = id
        } else {
            if (p.aCard >= 0) p.cards[p.aCard]++
            p.aCard = id
        }
        addLog("${m.cardNome} equipada.", C_GOLD)
    }

    fun changeJob(j: Job) {
        p.job = j
        p.jlv = 1
        p.jexp = 0
        p.recalc()
        p.hp = p.maxHp
        p.sp = p.maxSp
        panel = Panel.NONE
        lvlFx = 2f
        snd(Audio.S_LEVELUP)
        texts.add(FText(px, py - 100f, j.nome.uppercase() + "!", C_GOLD, 2.5f, true))
        addLog("Agora você é um ${j.nome}! Sua arma virou ${p.weaponName()}.", C_GOLD)
    }

    /** Entra com um personagem salvo: zera o estado do jogo e começa no mapa dele. */
    fun loadChar(o: JSONObject) {
        p.fromJson(Player().toJson())
        p.fromJson(o)
        cd.fill(0f)
        bossT.fill(150f)
        dead = false
        panel = Panel.NONE
        report = null
        lvlFx = 0f
        atkAnim = 0f
        saveT = 0f
        log.clear()
        started = false
        start()
        selecting = false
    }

    fun resetAll() {
        val nome = p.nome
        p.fromJson(Player().toJson())
        p.nome = nome
        p.recalc()
        p.hp = p.maxHp; p.sp = p.maxSp
        cd.fill(0f)
        dead = false
        panel = Panel.NONE
        log.clear()
        changeMap(p.map, 2)
        onSave?.invoke()
    }

    // ================= MAPA / TOQUE =================
    fun tapWorld(wx: Float, wy: Float): Boolean {
        if (dead) return true
        mobAt(wx, wy)?.let {
            target = it; npcTarget = null; hasDest = false; pendingSkill = -1; manualT = 0f
            return true
        }
        npcAt(wx, wy)?.let {
            npcTarget = it; target = null; hasDest = false; pendingSkill = -1
            return true
        }
        moveTo(wx, wy)
        return false
    }

    fun moveTo(wx: Float, wy: Float) {
        destX = wx.coerceIn(30f, map.w - 30f)
        destY = wy.coerceIn(60f, map.h - 20f)
        hasDest = true
        target = null; npcTarget = null; pendingSkill = -1
        manualT = 1.5f
    }

    private fun mobAt(wx: Float, wy: Float): Mob? {
        var best: Mob? = null
        var bd = Float.MAX_VALUE
        for (i in mobs.indices) {
            val m = mobs[i]
            if (m.dead) continue
            val d = dist(wx, wy, m.x, m.y - m.d.size)
            if (d < m.d.size * 1.4f + 22f && d < bd) { bd = d; best = m }
        }
        return best
    }

    private fun npcAt(wx: Float, wy: Float): Npc? {
        for (i in npcs.indices) if (dist(wx, wy, npcs[i].x, npcs[i].y - 30f) < 45f) return npcs[i]
        return null
    }

    private fun interact(n: Npc) {
        if (n.tipo == null) {
            p.hp = p.maxHp; p.sp = p.maxSp
            snd(Audio.S_HEAL)
            fx.add(Fx(FX_RING, px, py - 25f, 0f, 0f, 50f, col(0xCC69F0AE), 0.6f))
            addLog("Curandeira: Pronto! Que a luz da Bifrost te guie.", C_GREEN)
        } else {
            refineMsg = ""
            panel = n.tipo
        }
    }

    /** entry: 0 = chega pelo lado oeste, 1 = pelo lado leste, 2 = centro. */
    fun changeMap(id: Int, entry: Int) {
        map = MAPS[id]
        p.map = id
        mobs.clear(); texts.clear(); fx.clear(); parts.clear()
        for (d in drops) d.on = false
        heroQueue.clear()
        hstate = HS_IDLE
        curSpeed = 0f
        atkMob = null
        hitStopT = 0f
        postKillT = 0f
        target = null; npcTarget = null; pendingSkill = -1; hasDest = false
        if (id > 0) {
            p.farmMap = id
            if (id > p.maxMap) p.maxMap = id
        }
        when {
            // floresta: chega pela estrada da esquerda (vindo da Vila) ou no cruzamento do centro
            id == 1 && entry != 2 -> { px = Forest.ENTRY_LEFT_X; py = Forest.ENTRY_LEFT_Y }
            id == 1 -> { px = Forest.CENTER_X; py = Forest.CENTER_Y }
            entry == 0 -> { px = 320f; py = map.h / 2 }
            entry == 1 -> { px = map.w - 320f; py = map.h / 2 }
            else -> { px = map.w / 2; py = map.h / 2 + (if (id == 0) 130f else 0f) }
        }
        // Vila: oeste -> Campo de Aprendizes, leste -> Campos de Aurora. Depois, cadeia linear.
        val e = 150f
        portals.clear()
        when (id) {
            0 -> {
                portals.add(Portal(e, map.h / 2, 1, 0))
                portals.add(Portal(map.w - e, map.h / 2, 2, 0))
            }
            // floresta: a estrada da esquerda volta para a Vila (as outras duas saídas ficam "em breve")
            1 -> portals.add(Portal(Forest.PORTAL_LEFT_X, Forest.PORTAL_LEFT_Y, 0, 0))
            2 -> {
                portals.add(Portal(e, map.h / 2, 0, 1))
                portals.add(Portal(map.w - e, map.h / 2, 3, 0))
            }
            else -> {
                portals.add(Portal(e, map.h / 2, id - 1, 1))
                if (id < MAPS.size - 1) portals.add(Portal(map.w - e, map.h / 2, id + 1, 0))
            }
        }
        npcs.clear()
        if (id == 0) {
            npcs.add(Npc("Curandeira", 800f, 400f, null, col(0xFFFFFFFF), col(0xFFF48FB1), 5))
            npcs.add(Npc("Mercador", 520f, 480f, Panel.LOJA, col(0xFF8D6E63), col(0xFF4E342E), 6))
            npcs.add(Npc("Ferreiro", 1080f, 480f, Panel.FERREIRO, col(0xFF5D4037), col(0xFF212121), 7))
            npcs.add(Npc("Mestre de Classes", 520f, 760f, Panel.MESTRE, col(0xFF283593), col(0xFFECEFF1), 8))
            npcs.add(Npc("Teleportadora", 1080f, 760f, Panel.TELEPORTE, col(0xFF00838F), col(0xFFFFCA28), 9))
        }
        decos = if (id == 1) emptyList() else genDecos(map)
        if (id > 0) repeat(map.maxMobs) { spawnMob(MONSTERS[map.mobs[it % map.mobs.size]]) }
        if (started) snd(Audio.S_TELEPORT)
        onMapChange?.invoke(id)
        addLog("${map.nome} (${map.faixa})", col(0xFFFFF59D))
    }

    fun addLog(s: String, cor: Int) {
        log.add(LogLine(s, cor, 0f))
        while (log.size > 6) log.removeAt(0)
    }

    fun applyOffline(secs: Long) {
        val r = Offline.simulate(this, secs) ?: return
        report = r
        if (!dead) panel = Panel.OFFLINE
    }

    private fun genDecos(m: MapDef): List<Deco> {
        val r = Random(m.id * 7919 + 13)
        val list = ArrayList<Deco>()
        fun free(x: Float, y: Float): Boolean {
            if ((x < 380f || x > m.w - 380f) && abs(y - m.h / 2) < 200f) return false
            if (dist(x, y, m.w / 2, m.h / 2) < 160f) return false
            return true
        }
        val (tall, flat) = when (m.theme) {
            0 -> intArrayOf(0, 10) to intArrayOf(20, 25)
            1 -> intArrayOf(0, 0, 10, 2) to intArrayOf(20, 21, 21)
            2 -> intArrayOf(1, 1, 0, 10) to intArrayOf(21, 26)
            3 -> intArrayOf(3, 3, 2) to intArrayOf(24, 13)
            4 -> intArrayOf(11, 11, 4, 2) to intArrayOf(23, 23)
            5 -> intArrayOf(5, 6, 6, 7) to intArrayOf(25, 13)
            else -> intArrayOf(12, 12, 7) to intArrayOf(22, 22, 13)
        }
        if (m.id == 0) {
            val houses = listOf(230f to 260f, 620f to 210f, 980f to 210f, 1370f to 260f, 230f to 960f, 1370f to 960f, 640f to 1000f, 960f to 1000f)
            houses.forEachIndexed { i, (x, y) -> list.add(Deco(x, y, 8, Style.HOUSE_SCALE, true, i)) }
            list.add(Deco(800f, 590f, 9, 1f, true, 0))
            // árvores só na faixa de grama fora das muretas
            var placed = 0
            var tries = 0
            while (placed < 26 && tries < 400) {
                tries++
                val side = r.nextInt(4)
                val x = when (side) { 0 -> 20f + r.nextFloat() * 40f; 1 -> m.w - 60f + r.nextFloat() * 40f; else -> 40f + r.nextFloat() * (m.w - 80f) }
                val y = when (side) { 2 -> 50f + r.nextFloat() * 40f; 3 -> m.h - 20f + r.nextFloat() * 15f; else -> 60f + r.nextFloat() * (m.h - 80f) }
                if (abs(y - m.h / 2) < 140f && (side == 0 || side == 1)) continue
                list.add(Deco(x, y, if (r.nextInt(3) == 0) 10 else 0, Style.TREE_SCALE * (0.8f + r.nextFloat() * 0.4f), true, r.nextInt(1000)))
                placed++
            }
            return list
        }
        val nTall = if (m.theme == 2) 120 else 75
        repeat(nTall) {
            val x = 40f + r.nextFloat() * (m.w - 80f)
            val y = 70f + r.nextFloat() * (m.h - 90f)
            if (free(x, y)) {
                val k = tall[r.nextInt(tall.size)]
                val s = (0.8f + r.nextFloat() * 0.6f) * (if (k == 0 || k == 1 || k == 11) Style.TREE_SCALE else 1f)
                list.add(Deco(x, y, k, s, true, r.nextInt(1000)))
            }
        }
        repeat(170) {
            val x = r.nextFloat() * m.w
            val y = r.nextFloat() * m.h
            list.add(Deco(x, y, flat[r.nextInt(flat.size)], 0.7f + r.nextFloat() * 0.8f, false, r.nextInt(1000)))
        }
        return list
    }
}
