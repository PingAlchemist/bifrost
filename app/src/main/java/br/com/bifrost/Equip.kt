package br.com.bifrost

import android.content.res.AssetManager
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ---------- Equipamentos com tier (números em assets/equipamentos.json) ----------

/** Atributos de item: os 17 que podem ser encantamento + ATK/MATK base de arma. */
enum class St(val rotulo: String, val pct: Boolean) {
    FOR("FOR", false), AGI("AGI", false), VIT("VIT", false), INT("INT", false), DES("DES", false), SOR("SOR", false),
    ATK_PCT("Ataque", true), MATK_PCT("Ataque mágico", true), PEN_FIS("Penetração física", true),
    PEN_MAG("Penetração mágica", true), CRIT("Crítico", true), ASPD("Vel. ataque", true),
    HP_PCT("HP máx.", true), SP_PCT("SP máx.", true), DEF("Defesa", false), MDEF("Defesa mágica", false),
    FLEE("Esquiva", false), ATK("ATK", false), MATK("MATK", false);

    fun texto(v: Int) = (if (v >= 0) "+" else "") + v + (if (pct) "% " else " ") + rotulo
}

/** Tipo de equipamento e o espaço (slot) que ocupa no personagem. */
enum class EqType(val nome: String, val slot: Int) {
    ARMA_FIS("Arma física", 0), ARMA_MAG("Arma mágica", 0), ELMO("Elmo", 1), ARMADURA("Armadura", 2),
    ESCUDO("Escudo", 3), BOTAS("Botas", 4), ACESSORIO("Acessório", 5);

    /** Arma física só para classes físicas, arma mágica só para o Mago; o resto serve para todos. */
    fun serve(j: Job) = when (this) {
        ARMA_FIS -> !j.magic
        ARMA_MAG -> j.magic
        else -> true
    }
}

const val N_SLOTS = 6
val SLOT_NOMES = arrayOf("Arma", "Elmo", "Armadura", "Escudo", "Botas", "Acessório")
const val TIER_MAX = 5

// resultado de Player.receive
const val GEAR_KEPT = 0
const val GEAR_AUTO_SOLD = 1
const val GEAR_FULL_SOLD = 2
const val GEAR_OVER = 3

class ItemBase(val id: Int, val nome: String, val tipo: EqType, val nivel: Int, val mapa: Int, val base: IntArray)

/** Item dropado: item base + tier (1..5) + encantamentos sorteados + id único. */
class GearItem(val uid: Long, val baseId: Int, val tier: Int, val ench: IntArray, val vals: IntArray) {
    val base: ItemBase get() = Equip.base(baseId)!!
    val nome: String get() = base.nome
    val tipo: EqType get() = base.tipo

    /** Soma dos atributos base e dos encantamentos. */
    fun stats(): IntArray {
        val s = base.base.copyOf()
        for (i in ench.indices) s[ench[i]] += vals[i]
        return s
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("u", uid); put("b", baseId); put("t", tier)
        put("e", JSONArray().also { a -> for (i in ench.indices) a.put(JSONArray().put(St.entries[ench[i]].name).put(vals[i])) })
    }

    companion object {
        fun fromJson(o: JSONObject): GearItem? {
            val b = o.optInt("b", -1)
            if (Equip.base(b) == null) return null
            val ea = o.optJSONArray("e") ?: JSONArray()
            val ids = ArrayList<Int>()
            val vs = ArrayList<Int>()
            for (i in 0 until ea.length()) {
                val p = ea.optJSONArray(i) ?: continue
                val st = runCatching { St.valueOf(p.optString(0)) }.getOrNull() ?: continue
                ids.add(st.ordinal); vs.add(p.optInt(1))
            }
            return GearItem(o.optLong("u"), b, o.optInt("t", 1).coerceIn(1, TIER_MAX), ids.toIntArray(), vs.toIntArray())
        }
    }
}

object Equip {
    var tierNomes = arrayOf("Comum", "Incomum", "Raro", "Épico", "Lendário")
    var tierCores = intArrayOf(Color.WHITE, col(0xFF4CAF50), col(0xFF42A5F5), col(0xFFAB47BC), col(0xFFFF9800))
    /** [tier-1] = (mín, máx) de encantamentos. */
    var nEnch = arrayOf(intArrayOf(1, 1), intArrayOf(1, 2), intArrayOf(2, 2), intArrayOf(3, 3), intArrayOf(3, 3))
    /** Maior tier que pode ser gerado (drop, craft, debug). O 5 fica reservado. */
    var tierAtivo = 4
    /** [st][tier-1] = (mín, máx). */
    val faixa = Array(St.entries.size) { Array(TIER_MAX) { intArrayOf(1, 1) } }
    val porTipo = Array(EqType.entries.size) { IntArray(0) }
    var penMax = 60
    var mdefK = 120f
    var magicos = IntArray(0)
    // ----- drop (seção "drop" do JSON) -----
    var chance = floatArrayOf(0.015f, 0.003f, 0.0005f, 0.00005f, 0f)
    /** Tabela dos chefes: vazia = chefe não dropa equipamento (preparado para o futuro). */
    var chanceChefe = FloatArray(0)
    var chanceTipoClasse = 0.7f
    var escalaMapa = floatArrayOf(0.3f, 0.4f, 0.5f, 0.65f, 0.8f, 0.9f, 1f)
    var limiteBolsa = 100
    var vendaPadrao = BooleanArray(4)
    var valorVenda = intArrayOf(15, 40, 120, 400, 0)
    val itens = ArrayList<ItemBase>()
    private val byId = HashMap<Int, ItemBase>()

    fun base(id: Int) = byId[id]
    fun tierCor(t: Int) = tierCores[(t - 1).coerceIn(0, TIER_MAX - 1)]
    fun tierNome(t: Int) = tierNomes[(t - 1).coerceIn(0, TIER_MAX - 1)]

    fun load(am: AssetManager) {
        val o = JSONObject(am.open("equipamentos.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
        o.optJSONObject("tiers")?.let { t ->
            t.optJSONArray("nomes")?.let { a -> tierNomes = Array(TIER_MAX) { a.optString(it, tierNomes[it]) } }
            t.optJSONArray("cores")?.let { a -> tierCores = IntArray(TIER_MAX) { runCatching { Color.parseColor(a.optString(it)) }.getOrDefault(tierCores[it]) } }
            t.optJSONArray("encantamentos")?.let { a -> nEnch = Array(TIER_MAX) { pair(a.optJSONArray(it), nEnch[it]) } }
            tierAtivo = t.optInt("tierMaximoAtivo", 4).coerceIn(1, 4)
        }
        o.optJSONObject("encantamentos")?.let { e ->
            for (st in St.entries) e.optJSONArray(st.name)?.let { a ->
                for (k in 0 until TIER_MAX) faixa[st.ordinal][k] = pair(a.optJSONArray(k), faixa[st.ordinal][k])
            }
        }
        o.optJSONObject("porTipo")?.let { p ->
            for (ty in EqType.entries) p.optJSONArray(ty.name)?.let { a ->
                porTipo[ty.ordinal] = (0 until a.length()).mapNotNull { runCatching { St.valueOf(a.optString(it)).ordinal }.getOrNull() }
                    .distinct().toIntArray()
            }
        }
        o.optJSONObject("combate")?.let { c ->
            penMax = c.optInt("penetracaoMaxima", 60)
            mdefK = c.optDouble("mdefConstante", 120.0).toFloat()
            c.optJSONArray("monstrosMagicos")?.let { a -> magicos = IntArray(a.length()) { a.optInt(it) } }
        }
        o.optJSONObject("drop")?.let { d ->
            d.optJSONArray("chancePorTier")?.let { a -> chance = FloatArray(TIER_MAX) { a.optDouble(it, 0.0).toFloat() } }
            d.optJSONArray("chancePorTierChefe")?.let { a -> chanceChefe = FloatArray(a.length()) { a.optDouble(it, 0.0).toFloat() } }
            chanceTipoClasse = d.optDouble("chanceTipoDaClasse", 0.7).toFloat()
            d.optJSONArray("escalaPorMapa")?.let { a -> escalaMapa = FloatArray(a.length()) { a.optDouble(it, 1.0).toFloat() } }
            limiteBolsa = d.optInt("limiteBolsa", 100)
            d.optJSONArray("vendaAutomatica")?.let { a -> vendaPadrao = BooleanArray(4) { a.optBoolean(it, false) } }
            d.optJSONArray("valorVenda")?.let { a -> valorVenda = IntArray(TIER_MAX) { a.optInt(it, 0) } }
        }
        itens.clear(); byId.clear()
        o.optJSONArray("itens")?.let { a ->
            for (i in 0 until a.length()) {
                val it = a.optJSONObject(i) ?: continue
                val tipo = runCatching { EqType.valueOf(it.optString("tipo")) }.getOrNull() ?: continue
                val base = IntArray(St.entries.size)
                it.optJSONObject("base")?.let { b ->
                    for (k in b.keys()) runCatching { base[St.valueOf(k).ordinal] = b.optInt(k) }
                }
                val ib = ItemBase(it.optInt("id"), it.optString("nome"), tipo, it.optInt("nivel", 1), it.optInt("mapa", 1), base)
                itens.add(ib); byId[ib.id] = ib
            }
        }
    }

    private fun pair(a: JSONArray?, def: IntArray): IntArray =
        if (a == null || a.length() < 2) def else intArrayOf(a.optInt(0), maxOf(a.optInt(0), a.optInt(1)))

    /** Sorteia os encantamentos de um item (sem repetir) conforme o tier. */
    fun roll(b: ItemBase, tier: Int, uid: Long, rnd: Random): GearItem {
        val t = tier.coerceIn(1, TIER_MAX)
        val n = nEnch[t - 1].let { rnd.nextInt(it[0], it[1] + 1) }
        val pool = porTipo[b.tipo.ordinal].toMutableList()
        pool.shuffle(rnd)
        val ids = pool.take(n).toIntArray()
        val vs = IntArray(ids.size) { k -> faixa[ids[k]][t - 1].let { rnd.nextInt(it[0], it[1] + 1) } }
        return GearItem(uid, b.id, t, ids, vs)
    }

    fun isMagicMob(id: Int) = magicos.contains(id)

    /** Um sorteio por abate, do maior tier para o menor; 0 = nada. Só até o tier ativo (o 5 nunca cai). */
    fun rollTier(rnd: Random, boss: Boolean): Int {
        val tab = if (boss) chanceChefe else chance
        val r = rnd.nextDouble()
        var acc = 0.0
        for (t in minOf(tierAtivo, tab.size) downTo 1) {
            acc += tab[t - 1] * GEAR_DROP_RATE
            if (r < acc) return t
        }
        return 0
    }

    /** Item do mapa (campo "mapa"), com chance de ser de um tipo que a classe usa; encantamentos na escala do mapa. */
    fun rollDrop(mapId: Int, tier: Int, job: Job, uid: Long, rnd: Random): GearItem? {
        val doMapa = itens.filter { it.mapa == mapId }
        if (doMapa.isEmpty()) return null
        val daClasse = doMapa.filter { it.tipo.serve(job) }
        val pool = if (daClasse.isNotEmpty() && rnd.nextFloat() < chanceTipoClasse) daClasse else doMapa
        val it = roll(pool[rnd.nextInt(pool.size)], tier, uid, rnd)
        val esc = escalaMapa.getOrElse(mapId - 1) { 1f }
        for (k in it.vals.indices) it.vals[k] = maxOf(1, Math.round(it.vals[k] * esc))
        return it
    }

    fun sellValue(it: GearItem) = valorVenda.getOrElse(it.tier - 1) { 0 }.toLong() * maxOf(1, it.base.nivel)
}
