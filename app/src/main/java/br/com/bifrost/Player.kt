package br.com.bifrost

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

class Player {
    var nome = "Herói"
    var job = Job.APRENDIZ
    var lv = 1
    var jlv = 1
    var exp = 0L
    var jexp = 0L
    val stats = IntArray(6) { 1 }
    var statPts = 30
    var skillPts = 0
    val skills = IntArray(SKILLS.size)
    val autoSkill = BooleanArray(SKILLS.size) { true }
    val buff = FloatArray(SKILLS.size)
    var hp = 0
    var sp = 0
    var zeny = 200L
    val potions = IntArray(POTIONS.size).also { it[0] = 15 }
    val cards = IntArray(MONSTERS.size)
    val loot = IntArray(MONSTERS.size)
    var wTier = 0
    var wRef = 0
    var wCard = -1
    var aTier = 0
    var aRef = 0
    var aCard = -1
    var map = 1
    var farmMap = 1
    var maxMap = 1
    var autoMode = false
    var autoPot = true
    var zoom = Style.ZOOM_DEFAULT
    var fx = true
    var vibra = true
    var som = true
    var musica = true
    var lastTime = 0L
    var kills = 0L

    // ----- equipamentos com tier -----
    val gear = arrayOfNulls<GearItem>(N_SLOTS)
    val bag = ArrayList<GearItem>()
    var nextUid = 1L
    /** Venda automática por tier (1 a 4). */
    val autoSell = BooleanArray(4)

    // ----- derivados -----
    val tot = IntArray(6)
    val bon = IntArray(Bonus.entries.size)
    var maxHp = 1
    var maxSp = 1
    var atk = 1f
    var matk = 1f
    var def = 0f
    var hit = 0f
    var flee = 0f
    var crit = 0f
    var delay = 1f
    var speed = 165f
    var expPct = 0
    var mdef = 0f
    var penFis = 0f
    var penMag = 0f
    val gs = IntArray(St.entries.size)

    fun b(x: Bonus) = bon[x.ordinal]
    fun weaponName() = WEAPON_NAMES[job.weaponCat][wTier]
    fun armorName() = ARMOR_NAMES[aTier]
    fun jobBonus(i: Int) = jlv * job.bonusW[i] / 50

    fun recalc() {
        bon.fill(0)
        if (wCard >= 0) MONSTERS[wCard].let { bon[it.card.ordinal] += it.cardV }
        if (aCard >= 0) MONSTERS[aCard].let { bon[it.card.ordinal] += it.cardV }
        // equipamentos: a base da arma (ATK/MATK) e da armadura (DEF) substitui a da loja
        gs.fill(0)
        var wGear = -1f
        var aGear = -1f
        for (s in 0 until N_SLOTS) {
            val it = gear[s] ?: continue
            if (!it.tipo.serve(job)) continue
            val v = it.stats()
            if (s == 0) { wGear = (it.base.base[St.ATK.ordinal] + it.base.base[St.MATK.ordinal]).toFloat(); v[St.ATK.ordinal] = 0; v[St.MATK.ordinal] = 0 }
            if (s == 2) { aGear = it.base.base[St.DEF.ordinal].toFloat(); v[St.DEF.ordinal] -= it.base.base[St.DEF.ordinal] }
            for (k in v.indices) gs[k] += v[k]
        }
        val all = b(Bonus.ALL)
        for (i in 0 until 6) tot[i] = stats[i] + jobBonus(i) + all + gs[i]
        tot[STR] += b(Bonus.STR); tot[AGI] += b(Bonus.AGI); tot[VIT] += b(Bonus.VIT)
        tot[INT] += b(Bonus.INT); tot[DEX] += b(Bonus.DEX); tot[LUK] += b(Bonus.LUK)

        val eagle = if (buff[9] > 0f) skills[9] else 0
        val fury = if (buff[3] > 0f) skills[3] else 0
        val shadow = if (buff[12] > 0f) skills[12] else 0
        tot[DEX] += 2 * eagle

        val l = lv.toFloat()
        maxHp = ((35 + l * job.hpF + l * l * job.hpF * 0.05f) * (1 + tot[VIT] * 0.01f) * (1 + (b(Bonus.HP_PCT) + gs[St.HP_PCT.ordinal]) / 100f)).toInt()
        maxSp = ((10 + l * job.spF + l * l * job.spF * 0.012f) * (1 + tot[INT] * 0.01f) * (1 + (b(Bonus.SP_PCT) + gs[St.SP_PCT.ordinal]) / 100f)).toInt()

        val wBase = (if (wGear >= 0f) wGear else WEAPON_ATK[wTier].toFloat()) * (1 + wRef * 0.1f)
        val main = if (job.ranged && !job.magic) DEX else STR
        val sec = if (main == DEX) STR else DEX
        val ms = tot[main].toFloat()
        val statusAtk = ms + (ms / 10f) * (ms / 10f) + tot[sec] / 5f + tot[LUK] / 5f + l / 4f
        atk = (statusAtk + (if (job.magic) wBase * 0.3f else wBase)) * (1 + (b(Bonus.ATK_PCT) + gs[St.ATK_PCT.ordinal] + 4 * fury) / 100f)
        val iv = tot[INT].toFloat()
        matk = (iv + (iv / 7f) * (iv / 7f) + l / 4f + (if (job.magic) wBase else 0f)) * (1 + (b(Bonus.MATK_PCT) + gs[St.MATK_PCT.ordinal]) / 100f)
        def = (if (aGear >= 0f) aGear else ARMOR_DEF[aTier].toFloat()) * (1 + aRef * 0.1f) + tot[VIT] / 2f + b(Bonus.DEF) + gs[St.DEF.ordinal]
        mdef = gs[St.MDEF.ordinal].toFloat()
        penFis = gs[St.PEN_FIS.ordinal].coerceIn(0, Equip.penMax) / 100f
        penMag = gs[St.PEN_MAG.ordinal].coerceIn(0, Equip.penMax) / 100f
        hit = l + tot[DEX] + 20 + b(Bonus.HIT)
        flee = l + tot[AGI] + 20 + b(Bonus.FLEE) + gs[St.FLEE.ordinal] + 4 * shadow
        crit = (1 + tot[LUK] * 0.3f + b(Bonus.CRIT) + gs[St.CRIT.ordinal] + eagle) / 100f
        val aspd = b(Bonus.ASPD_PCT) + gs[St.ASPD.ordinal] + 2 * fury + 3 * shadow
        delay = max(0.22f, job.delay * (1 - tot[AGI] * 0.006f - tot[DEX] * 0.0015f) / (1 + aspd / 100f))
        speed = 165f * (1 + 0.03f * shadow)
        expPct = b(Bonus.EXP_PCT)
        hp = min(hp, maxHp)
        sp = min(sp, maxSp)
    }

    fun addStat(i: Int, n: Int) {
        repeat(n) {
            val c = statCost(stats[i])
            if (stats[i] >= 99 || statPts < c) return
            statPts -= c
            stats[i]++
        }
    }

    fun resetStats() {
        var back = 0
        for (i in 0 until 6) {
            for (v in 1 until stats[i]) back += statCost(v)
            stats[i] = 1
        }
        statPts += back
    }

    fun resetSkills() {
        for (i in skills.indices) {
            skillPts += skills[i]
            skills[i] = 0
        }
    }

    // ----- salvamento -----
    fun toJson(): JSONObject = JSONObject().apply {
        put("v", 2)
        put("nome", nome)
        put("zoom", zoom.toDouble()); put("fx", fx); put("vibra", vibra)
        put("som", som); put("musica", musica)
        put("job", job.name); put("lv", lv); put("jlv", jlv); put("exp", exp); put("jexp", jexp)
        put("stats", ints(stats)); put("statPts", statPts); put("skillPts", skillPts)
        put("skills", ints(skills))
        put("autoSkill", JSONArray().also { a -> autoSkill.forEach { a.put(it) } })
        put("hp", hp); put("sp", sp); put("zeny", zeny)
        put("potions", ints(potions)); put("cards", ints(cards)); put("loot", ints(loot))
        put("wTier", wTier); put("wRef", wRef); put("wCard", wCard)
        put("aTier", aTier); put("aRef", aRef); put("aCard", aCard)
        put("map", map); put("farmMap", farmMap); put("maxMap", maxMap)
        put("autoMode", autoMode); put("autoPot", autoPot)
        put("lastTime", lastTime); put("kills", kills)
        put("gear", JSONArray().also { a -> gear.forEach { a.put(it?.toJson() ?: JSONObject.NULL) } })
        put("bag", JSONArray().also { a -> bag.forEach { a.put(it.toJson()) } })
        put("nextUid", nextUid)
        put("autoSell", JSONArray().also { a -> autoSell.forEach { a.put(it) } })
    }

    fun fromJson(o: JSONObject) {
        nome = o.optString("nome", "Herói").ifBlank { "Herói" }
        job = runCatching { Job.valueOf(o.optString("job", "APRENDIZ")) }.getOrDefault(Job.APRENDIZ)
        lv = o.optInt("lv", 1); jlv = o.optInt("jlv", 1)
        exp = o.optLong("exp", 0); jexp = o.optLong("jexp", 0)
        readInts(o.optJSONArray("stats"), stats)
        statPts = o.optInt("statPts", 30); skillPts = o.optInt("skillPts", 0)
        readInts(o.optJSONArray("skills"), skills)
        o.optJSONArray("autoSkill")?.let { a -> for (i in 0 until min(a.length(), autoSkill.size)) autoSkill[i] = a.optBoolean(i, true) }
        hp = o.optInt("hp", 0); sp = o.optInt("sp", 0); zeny = o.optLong("zeny", 200)
        readInts(o.optJSONArray("potions"), potions)
        readInts(o.optJSONArray("cards"), cards)
        readInts(o.optJSONArray("loot"), loot)
        wTier = o.optInt("wTier", 0).coerceIn(0, 6); wRef = o.optInt("wRef", 0); wCard = o.optInt("wCard", -1)
        aTier = o.optInt("aTier", 0).coerceIn(0, 6); aRef = o.optInt("aRef", 0); aCard = o.optInt("aCard", -1)
        map = o.optInt("map", 1)
        farmMap = o.optInt("farmMap", 1)
        maxMap = o.optInt("maxMap", 1)
        if (o.optInt("v", 1) < 2) {
            // v1 não tinha o Campo de Aprendizes (índice 1): desloca os mapas de caça
            if (map > 0) map += 1
            farmMap += 1
            maxMap += 1
        }
        map = map.coerceIn(0, MAPS.size - 1)
        farmMap = farmMap.coerceIn(1, MAPS.size - 1)
        maxMap = maxMap.coerceIn(1, MAPS.size - 1)
        zoom = o.optDouble("zoom", Style.ZOOM_DEFAULT.toDouble()).toFloat().coerceIn(1f, 3f)
        fx = o.optBoolean("fx", true)
        vibra = o.optBoolean("vibra", true)
        som = o.optBoolean("som", true)
        musica = o.optBoolean("musica", true)
        autoMode = o.optBoolean("autoMode", false); autoPot = o.optBoolean("autoPot", true)
        lastTime = o.optLong("lastTime", 0); kills = o.optLong("kills", 0)
        // equipamentos com tier (saves antigos não têm: começam vazios)
        gear.fill(null); bag.clear()
        o.optJSONArray("gear")?.let { a ->
            for (i in 0 until min(a.length(), N_SLOTS)) a.optJSONObject(i)?.let { gear[i] = GearItem.fromJson(it) }
        }
        o.optJSONArray("bag")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { j -> GearItem.fromJson(j)?.let { bag.add(it) } }
        }
        nextUid = o.optLong("nextUid", 1L)
        val av = o.optJSONArray("autoSell")
        for (i in autoSell.indices) autoSell[i] = av?.optBoolean(i, Equip.vendaPadrao[i]) ?: Equip.vendaPadrao[i]
        buff.fill(0f)
    }

    /**
     * Item novo chegando na bolsa. Venda automática do tier: vira zeny. Bolsa cheia: tiers 1 e 2 são vendidos,
     * 3 e 4 entram mesmo acima do limite. Devolve GEAR_* e põe o zeny da venda em lastSale.
     */
    fun receive(it: GearItem): Int {
        lastSale = 0L
        val t = it.tier
        if (t in 1..autoSell.size && autoSell[t - 1]) { sell(it); return GEAR_AUTO_SOLD }
        if (bag.size >= Equip.limiteBolsa) {
            if (t <= 2) { sell(it); return GEAR_FULL_SOLD }
            bag.add(it); return GEAR_OVER
        }
        bag.add(it)
        return GEAR_KEPT
    }

    var lastSale = 0L
        private set

    private fun sell(it: GearItem) {
        lastSale = Equip.sellValue(it)
        zeny += lastSale
    }

    /** Vende um item da bolsa (equipados nunca são vendidos). */
    fun sellFromBag(it: GearItem): Long {
        if (gear.any { g -> g === it } || !bag.remove(it)) return 0L
        sell(it)
        return lastSale
    }

    /** Equipa um item da bolsa; o que estava no espaço volta para a bolsa. */
    fun equip(it: GearItem): Boolean {
        if (lv < it.base.nivel || !it.tipo.serve(job) || !bag.remove(it)) return false
        val s = it.tipo.slot
        gear[s]?.let { old -> bag.add(old) }
        gear[s] = it
        recalc()
        return true
    }

    fun unequip(s: Int) {
        val it = gear[s] ?: return
        gear[s] = null
        bag.add(it)
        recalc()
    }

    /** Atributos do que está no espaço (para comparar). Sem item, arma/armadura da loja contam. */
    fun slotStats(s: Int): IntArray {
        gear[s]?.let { if (it.tipo.serve(job)) return it.stats() }
        val v = IntArray(St.entries.size)
        if (s == 0) v[(if (job.magic) St.MATK else St.ATK).ordinal] = WEAPON_ATK[wTier]
        if (s == 2) v[St.DEF.ordinal] = ARMOR_DEF[aTier]
        return v
    }

    private fun ints(a: IntArray) = JSONArray().also { j -> a.forEach { j.put(it) } }
    private fun readInts(j: JSONArray?, dst: IntArray) {
        if (j == null) return
        for (i in 0 until min(j.length(), dst.size)) dst[i] = j.optInt(i, dst[i])
    }
}
