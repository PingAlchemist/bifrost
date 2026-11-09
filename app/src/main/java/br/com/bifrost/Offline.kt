package br.com.bifrost

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Autobot offline: estima quantos monstros o personagem mataria no último mapa de caça
 * durante o tempo fora (máx. 12h), com 60% da eficiência do jogo ativo.
 * Considera dano, precisão, velocidade de ataque, dano recebido, regeneração e poções.
 */
object Offline {
    private const val EFF = 0.6f
    private const val CAP = 12 * 3600L
    private const val CARD_RATE = 0.0025f

    fun simulate(g: Game, secsIn: Long): OfflineReport? {
        if (secsIn < 60) return null
        val p = g.p
        val secs = min(secsIn, CAP)
        val map = MAPS[p.farmMap.coerceIn(1, MAPS.size - 1)]
        p.buff.fill(0f)
        p.recalc()
        val rnd = Random(System.nanoTime())

        val per = secs * EFF / map.mobs.size
        val regenPs = (p.maxHp * 0.012f + p.tot[VIT] * 0.2f) / 3f
        var potHp = 0f
        for (i in 0..2) potHp += p.potions[i] * POTIONS[i].hp
        var potUsedHp = 0f
        val classSkillLv = p.job.skills.filter { it != 0 }.sumOf { p.skills[it] }
        val skillBoost = 1f + 0.05f * classSkillLv

        var kills = 0L
        var exp = 0L
        var jexp = 0L
        var zeny = 0L
        var lootTotal = 0
        var potsGanhas = 0
        var weak = 0
        val cards = ArrayList<String>()
        val mul = (1f + p.expPct / 100f) * EXP_RATE

        for (id in map.mobs) {
            val d = MONSTERS[id]
            val base = if (p.job.magic) g.reduce(p.matk * 0.75f, d.def, true) else g.reduce(p.atk, d.def, false)
            val avg = if (p.job.magic) base
            else g.hitChance(d) * ((1 - p.crit) * base + p.crit * p.atk * 1.4f)
            val dps = max(0.01f, avg / p.delay * skillBoost)
            val fight = d.hp / dps
            val tpk = fight + 3f
            val dodge = (0.05f + (p.flee - d.hit) / 100f).coerceIn(0.05f, 0.9f)
            val mdmg = max(1f, d.atk * (1f - p.def / (p.def + 120f)) - p.tot[VIT] * 0.3f)
            val taken = fight / 1.6f * mdmg * (1 - dodge)
            if (taken > p.maxHp * 0.85f) {
                weak++
                continue
            }
            val deficit = max(0f, taken - regenPs * tpk)
            val k: Float = if (deficit <= 0f) per / tpk else {
                val full = per / tpk
                val k1 = min(full, potHp / deficit)
                potHp -= k1 * deficit
                potUsedHp += k1 * deficit
                val t2 = per - k1 * tpk
                k1 + max(0f, t2 / (tpk + deficit / regenPs))
            }
            val n = k.toLong()
            if (n <= 0) continue
            kills += n
            exp += (n * d.exp * mul).toLong()
            jexp += (n * d.jexp * mul).toLong()
            zeny += (n * d.zeny * ZENY_RATE).toLong()
            val l = sample(n * 0.35f * DROP_RATE, rnd)
            p.loot[id] += l
            lootTotal += l
            val c = sample(n * CARD_RATE * DROP_RATE, rnd)
            if (c > 0) {
                p.cards[id] += c
                cards.add("${d.cardNome} x$c")
            }
            val pd = sample(n * 0.03f * DROP_RATE, rnd)
            if (pd > 0) {
                p.potions[potionTierFor(d.lv)] += pd
                potsGanhas += pd
            }
        }

        var need = potUsedHp
        var used = 0
        for (i in 0..2) {
            while (need > 0f && p.potions[i] > 0) {
                p.potions[i]--
                need -= POTIONS[i].hp
                used++
            }
        }

        val got = g.gainExp(exp, jexp, quiet = true)
        p.zeny += zeny
        p.kills += kills
        p.recalc()
        p.hp = p.maxHp
        p.sp = p.maxSp

        val nota = when {
            weak == map.mobs.size -> "Os monstros de ${map.nome} são fortes demais para o bot. Volte para um mapa mais fácil antes de sair!"
            weak > 0 -> "Alguns monstros eram fortes demais e foram evitados pelo bot."
            used > 0 && potHp <= 1f -> "As poções acabaram; o bot descansou entre as lutas (rendeu menos)."
            else -> ""
        }
        return OfflineReport(secs, map.nome, kills, exp, jexp, zeny, lootTotal, used, potsGanhas, cards, got[0], got[1], nota)
    }

    private fun sample(e: Float, rnd: Random): Int {
        val f = floor(e)
        return f.toInt() + if (rnd.nextFloat() < e - f) 1 else 0
    }
}
