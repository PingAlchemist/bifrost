package br.com.bifrost

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

fun col(v: Long): Int = v.toInt()

/** Versão mostrada na tela inicial e na seleção (conferir no celular qual APK está instalado). */
const val VERSAO = "1.2.0"

/** TESTE: multiplicador de EXP (base e classe), online e offline. Volte para 1f quando terminar os testes. */
const val EXP_RATE = 15f

/** TESTE: multiplicador de drop (itens, poções e cartas; chance acima de 100% vira quantidade). Volte para 1f depois. */
const val DROP_RATE = 15f

/**
 * Estilo visual centralizado: paleta base (10 cores, tons pastel/terrosos), tamanhos e HUD.
 * Tamanhos estão em unidades de mundo; a tela escala por (altura/720) × zoom, então tudo é proporcional à tela.
 */
object Style {
    // ----- paleta base (máx. 10) -----
    val SAND = col(0xFFD8C39A)        // chão bege/areia
    val SAND_DARK = col(0xFFB59B70)   // terra batida dos caminhos
    val GRASS = col(0xFF8DAA55)       // grama verde-oliva suave
    val GRASS_DARK = col(0xFF6F8B40)
    val STONE = col(0xFFBAB3A5)       // lajotas e muretas
    val ROOF_RED = col(0xFFB4533C)
    val ROOF_BLUE = col(0xFF4E6EA6)
    val WOOD = col(0xFF7A5A3C)
    val CREAM = col(0xFFF3E8D0)       // paredes
    val WATER = col(0xFF7CC6E0)

    // ----- tamanhos (unidades de mundo) -----
    const val CHAR_SCALE = 1f
    const val NPC_SCALE = 1f
    const val TREE_SCALE = 1.15f
    const val HOUSE_SCALE = 1f
    const val OUTLINE = 1f
    const val ZOOM_DEFAULT = 1.6f
    val ZOOMS = floatArrayOf(1.2f, 1.6f, 2.0f, 2.4f)

    // ----- HUD -----
    val HUD_BG = col(0xEBF3F5F9)
    val HUD_BORDER = col(0xFF7F90AC)
    val HUD_TITLE_TOP = col(0xFF829DCB)
    val HUD_TITLE_BOT = col(0xFF5A77A3)
    val HUD_INK = col(0xFF2B3440)
    val HUD_INK2 = col(0xFF5D6B7A)
    val HUD_BTN = col(0xFF5874A0)
    val HUD_SLOT = col(0xF2283240)
    const val HUD_RADIUS = 6f
    const val FONT_S = 13f
    const val FONT_M = 16f
    const val FONT_L = 20f
}

// ---------- Atributos ----------
const val STR = 0
const val AGI = 1
const val VIT = 2
const val INT = 3
const val DEX = 4
const val LUK = 5
val STAT_NOMES = arrayOf("FOR", "AGI", "VIT", "INT", "DES", "SOR")
val STAT_DESC = arrayOf(
    "Ataque corpo a corpo", "Vel. de ataque e esquiva", "HP máx. e defesa",
    "Ataque mágico e SP", "Precisão e ataque à distância", "Chance de crítico"
)

fun statCost(v: Int) = (v - 1) / 10 + 2

// ---------- Classes ----------
enum class Job(
    val nome: String, val hpF: Float, val spF: Float, val delay: Float, val range: Float,
    val magic: Boolean, val ranged: Boolean, val weaponCat: Int, val hat: Int,
    val cor: Int, val cabelo: Int, val bonusW: IntArray, val skills: IntArray, val desc: String
) {
    APRENDIZ(
        "Aprendiz", 6f, 1.5f, 1.0f, 55f, false, false, 0, 0, col(0xFF8D6E63), col(0xFF5D4037),
        intArrayOf(0, 0, 0, 0, 0, 0), intArrayOf(0), "Todo herói começa aqui."
    ),
    GUERREIRO(
        "Guerreiro", 11f, 2f, 0.95f, 60f, false, false, 1, 1, col(0xFF1E5AA8), col(0xFF3E2723),
        intArrayOf(5, 2, 3, 0, 2, 0), intArrayOf(0, 1, 2, 3),
        "Muito HP e dano corpo a corpo. O mais fácil para upar no bot."
    ),
    MAGO(
        "Mago", 5f, 6.5f, 1.1f, 240f, true, true, 2, 2, col(0xFF6A1B9A), col(0xFFB0BEC5),
        intArrayOf(0, 2, 1, 6, 3, 0), intArrayOf(0, 4, 5, 6),
        "Magias devastadoras em área. Frágil: invista em INT e um pouco de VIT."
    ),
    CACADOR(
        "Caçador", 7.5f, 2.6f, 0.9f, 300f, false, true, 3, 3, col(0xFF2E7D32), col(0xFFFFB300),
        intArrayOf(1, 3, 1, 1, 5, 1), intArrayOf(0, 7, 8, 9),
        "Ataca de longe com arco. Dano vem da DES."
    ),
    LADINO(
        "Ladino", 8f, 2.6f, 0.7f, 55f, false, false, 0, 4, col(0xFF37474F), col(0xFFC62828),
        intArrayOf(3, 6, 1, 0, 1, 1), intArrayOf(0, 10, 11, 12),
        "Ataques muito rápidos, esquiva alta e veneno. Invista em AGI."
    );

    val jobMax: Int
        get() = if (this == APRENDIZ) 10 else 50
}

// ---------- Habilidades ----------
enum class SK { CURA, BUFF, AREA_SELF, AREA_ALVO, DANO }

class SkillDef(
    val id: Int, val nome: String, val sigla: String, val kind: SK, val maxLv: Int, val cd: Float,
    val range: Float, val radius: Float, val cor: Int, val spBase: Int, val spLv: Float,
    val dur: Float, val desc: (Int) -> String
) {
    fun sp(lv: Int) = spBase + (spLv * lv).toInt()
}

fun pct(f: Float) = "${(f * 100).toInt()}%"

val SKILLS = arrayOf(
    SkillDef(0, "Primeiros Socorros", "PS", SK.CURA, 5, 4f, 0f, 0f, col(0xFF66BB6A), 3, 1f, 0f) { lv ->
        "Cura ${10 * lv} + ${3 * lv}% do HP máx."
    },
    SkillDef(1, "Golpe Brutal", "GB", SK.DANO, 10, 0.8f, 70f, 0f, col(0xFFE53935), 7, 1f, 0f) { lv ->
        "Golpe de ${pct(1.5f + 0.3f * lv)} do ATK em um alvo."
    },
    SkillDef(2, "Impacto Sísmico", "IS", SK.AREA_SELF, 10, 3f, 0f, 160f, col(0xFF8D6E63), 14, 2f, 0f) { lv ->
        "Atinge todos ao redor com ${pct(1f + 0.2f * lv)} do ATK."
    },
    SkillDef(3, "Fúria", "FU", SK.BUFF, 10, 10f, 0f, 0f, col(0xFFFF7043), 20, 1f, 60f) { lv ->
        "Por 60s: ATK +${4 * lv}% e vel. de ataque +${2 * lv}%."
    },
    SkillDef(4, "Seta de Fogo", "SF", SK.DANO, 10, 0.9f, 260f, 0f, col(0xFFFF6F00), 10, 1f, 0f) { lv ->
        "Projétil de fogo com ${pct(1.2f + 0.35f * lv)} do MATK."
    },
    SkillDef(5, "Tempestade de Raios", "TR", SK.AREA_ALVO, 10, 3.5f, 260f, 150f, col(0xFFFFEB3B), 20, 3f, 0f) { lv ->
        "Raios na área do alvo: ${pct(1f + 0.25f * lv)} do MATK."
    },
    SkillDef(6, "Nova Gélida", "NG", SK.AREA_SELF, 10, 7f, 0f, 180f, col(0xFF4FC3F7), 24, 2f, 0f) { lv ->
        "Gelo ao redor: ${pct(0.8f + 0.15f * lv)} do MATK e congela por ${"%.1f".format(1.5f + 0.25f * lv)}s."
    },
    SkillDef(7, "Disparo Duplo", "DD", SK.DANO, 10, 0.8f, 320f, 0f, col(0xFF9CCC65), 8, 0.5f, 0f) { lv ->
        "Duas flechas de ${pct(0.9f + 0.15f * lv)} do ATK cada."
    },
    SkillDef(8, "Chuva de Flechas", "CF", SK.AREA_ALVO, 10, 3f, 320f, 140f, col(0xFF8BC34A), 15, 1f, 0f) { lv ->
        "Flechas na área do alvo: ${pct(0.8f + 0.2f * lv)} do ATK."
    },
    SkillDef(9, "Olho de Águia", "OA", SK.BUFF, 10, 10f, 0f, 0f, col(0xFFFFCA28), 25, 0f, 90f) { lv ->
        "Por 90s: DES +${2 * lv} e crítico +$lv%."
    },
    SkillDef(10, "Golpe Venenoso", "GV", SK.DANO, 10, 1f, 65f, 0f, col(0xFF7CB342), 10, 0.5f, 0f) { lv ->
        "Golpe de ${pct(1.3f + 0.2f * lv)} do ATK + veneno (${8 * lv}% do ATK/s por 5s)."
    },
    SkillDef(11, "Lâminas Giratórias", "LG", SK.AREA_SELF, 10, 2.5f, 0f, 140f, col(0xFFB0BEC5), 14, 1f, 0f) { lv ->
        "Gira atingindo todos ao redor: ${pct(0.9f + 0.18f * lv)} do ATK."
    },
    SkillDef(12, "Sombra Veloz", "SV", SK.BUFF, 10, 10f, 0f, 0f, col(0xFF7E57C2), 20, 0f, 60f) { lv ->
        "Por 60s: esquiva +${4 * lv}, vel. ataque +${3 * lv}%, movimento +${3 * lv}%."
    },
)

// ---------- Cartas ----------
enum class Bonus(val fmt: String, val arma: Boolean) {
    ATK_PCT("ATK +%d%%", true), MATK_PCT("MATK +%d%%", true), CRIT("Crítico +%d%%", true),
    HIT("Precisão +%d", true), ASPD_PCT("Vel. ataque +%d%%", true),
    STR("FOR +%d", true), INT("INT +%d", true), DEX("DES +%d", true),
    HP_PCT("HP máx. +%d%%", false), SP_PCT("SP máx. +%d%%", false), DEF("DEF +%d", false),
    FLEE("Esquiva +%d", false), AGI("AGI +%d", false), VIT("VIT +%d", false), LUK("SOR +%d", false),
    EXP_PCT("EXP +%d%%", false), ALL("Todos atributos +%d", true);

    fun texto(v: Int) = fmt.format(v)
}

// ---------- Monstros ----------
enum class Shape { BLOB, QUAD, MUSHROOM, INSECT, SCORPION, TREE, SNAKE, HUMANOID, GHOST, DRAGON }

const val F_CROWN = 1
const val F_EARS = 2
const val F_WOLF = 4
const val F_BANDAGE = 8
const val F_BONES = 16
const val F_HORNS = 32
const val F_BULKY = 64
const val F_DRESS = 128
const val F_NOWINGS = 256
const val F_FUR = 512

class MonsterDef(
    val id: Int, val nome: String, val lv: Int, val shape: Shape, val c1: Int, val c2: Int,
    val size: Float, val aggressive: Boolean, val speed: Float, val flags: Int, val loot: String,
    val card: Bonus, val cardV: Int, val boss: Boolean,
    val hp: Int, val atk: Int, val def: Int, val flee: Int, val hit: Int,
    val exp: Long, val jexp: Long, val zeny: Int, val lootValue: Int
) {
    fun has(f: Int) = flags and f != 0
    val cardNome = "Carta $nome"
    val label = "$nome Nv$lv"
}

private fun mob(
    id: Int, nome: String, lv: Int, shape: Shape, c1: Long, c2: Long, size: Float,
    aggr: Boolean, speed: Float, flags: Int, loot: String, card: Bonus, cardV: Int,
    hpM: Float = 1f, atkM: Float = 1f, defM: Float = 1f, boss: Boolean = false, expM: Float = 1f
): MonsterDef {
    val l = lv.toDouble()
    val hp = ((30 + 6 * l.pow(1.45)) * hpM * (if (boss) 22 else 1)).toInt()
    val atk = max(1.0, (3 + 2.0 * l) * atkM * (if (boss) 2.0 else 1.0)).toInt()
    val def = (l * 0.5 * defM * (if (boss) 1.5 else 1.0)).toInt()
    val flee = (l * 1.25 + 15).toInt()
    val hit = (l * 1.3 + 25 + (if (boss) 25 else 0)).toInt()
    val exp = ((5 + 3.2 * l.pow(1.5)) * sqrt(hpM.toDouble()) * expM * (if (boss) 30 else 1)).toLong()
    val jexp = (exp * 0.75).toLong()
    val zeny = ((4 + l * 1.8) * (if (boss) 25 else 1)).toInt()
    val lootValue = (6 + l * 2.5).toInt()
    return MonsterDef(
        id, nome, lv, shape, col(c1), col(c2), size, aggr, speed, flags, loot, card, cardV, boss,
        hp, atk, def, flee, hit, exp, jexp, zeny, lootValue
    )
}

val MONSTERS = arrayOf(
    // Campos de Aurora
    mob(0, "Gelatina Verde", 2, Shape.BLOB, 0xFF7CD957, 0xFF4CAF50, 16f, false, 45f, 0, "Geleia Pegajosa", Bonus.HP_PCT, 5),
    mob(1, "Coelho Saltitante", 5, Shape.QUAD, 0xFFF5F5F5, 0xFFF8BBD0, 15f, false, 85f, F_EARS, "Pelo Macio", Bonus.AGI, 2),
    mob(2, "Cogumelo Pintado", 8, Shape.MUSHROOM, 0xFFE53935, 0xFFFFF3E0, 17f, false, 40f, 0, "Esporo Venenoso", Bonus.INT, 2),
    mob(3, "Rei Gelatina", 14, Shape.BLOB, 0xFF9CCC65, 0xFFFFD54F, 38f, true, 50f, F_CROWN, "Coroa de Geleia", Bonus.HP_PCT, 15, boss = true),
    // Bosque Sussurrante
    mob(4, "Lobo Cinzento", 12, Shape.QUAD, 0xFF9E9E9E, 0xFF616161, 22f, true, 105f, F_WOLF, "Presa de Lobo", Bonus.ATK_PCT, 5),
    mob(5, "Vespa Gigante", 16, Shape.INSECT, 0xFFFFC107, 0xFF212121, 15f, true, 110f, 0, "Ferrão", Bonus.CRIT, 4),
    mob(6, "Ent Jovem", 21, Shape.TREE, 0xFF558B2F, 0xFF6D4C41, 26f, false, 35f, 0, "Casca Antiga", Bonus.DEF, 6, hpM = 1.4f),
    mob(7, "Lobo Alfa", 28, Shape.QUAD, 0xFF424242, 0xFFB71C1C, 36f, true, 115f, F_WOLF, "Pele do Alfa", Bonus.ATK_PCT, 12, boss = true),
    // Dunas de Sálix
    mob(8, "Escorpião Rubro", 27, Shape.SCORPION, 0xFFD84315, 0xFF5D1F0A, 20f, true, 80f, 0, "Garra de Escorpião", Bonus.DEX, 3),
    mob(9, "Serpente das Areias", 32, Shape.SNAKE, 0xFFD4A84B, 0xFFFFF59D, 20f, true, 90f, 0, "Escama Dourada", Bonus.FLEE, 8),
    mob(10, "Múmia Andarilha", 37, Shape.HUMANOID, 0xFFD7CCC8, 0xFF8D6E63, 22f, false, 45f, F_BANDAGE, "Bandagem Antiga", Bonus.VIT, 3, hpM = 1.3f),
    mob(11, "Faraó Esquecido", 46, Shape.HUMANOID, 0xFFD7CCC8, 0xFFFFC107, 34f, true, 60f, F_BANDAGE or F_CROWN, "Cetro do Faraó", Bonus.MATK_PCT, 12, boss = true),
    // Picos Gelados
    mob(12, "Golem de Gelo", 43, Shape.HUMANOID, 0xFF81D4FA, 0xFF0288D1, 28f, false, 40f, F_BULKY, "Cristal de Gelo", Bonus.DEF, 12, hpM = 1.5f, defM = 2f),
    mob(13, "Yeti", 49, Shape.HUMANOID, 0xFFECEFF1, 0xFF90A4AE, 28f, true, 85f, F_BULKY or F_FUR, "Pelo de Yeti", Bonus.STR, 4),
    mob(14, "Espírito Gélido", 55, Shape.GHOST, 0xFFB3E5FC, 0xFF01579B, 22f, true, 95f, 0, "Essência Gélida", Bonus.SP_PCT, 10),
    mob(15, "Rainha do Inverno", 65, Shape.HUMANOID, 0xFFE1F5FE, 0xFF4FC3F7, 34f, true, 70f, F_CROWN or F_DRESS, "Tiara de Gelo", Bonus.ASPD_PCT, 10, boss = true),
    // Ruínas Sombrias
    mob(16, "Esqueleto Soldado", 62, Shape.HUMANOID, 0xFFEEEEEE, 0xFF757575, 22f, true, 85f, F_BONES, "Osso Velho", Bonus.HIT, 15),
    mob(17, "Espectro", 69, Shape.GHOST, 0xFF9575CD, 0xFF311B92, 22f, true, 95f, 0, "Ectoplasma", Bonus.LUK, 5),
    mob(18, "Cavaleiro Caído", 77, Shape.HUMANOID, 0xFF37474F, 0xFFB71C1C, 26f, true, 80f, F_HORNS or F_BULKY, "Elmo Rachado", Bonus.ATK_PCT, 8, hpM = 1.3f),
    mob(19, "Lich Ancestral", 86, Shape.HUMANOID, 0xFFE0E0E0, 0xFF4A148C, 36f, true, 65f, F_BONES or F_CROWN or F_DRESS, "Filactério", Bonus.EXP_PCT, 15, boss = true),
    // Covil do Dragão
    mob(20, "Dragonete", 82, Shape.DRAGON, 0xFFEF6C00, 0xFFFFB74D, 22f, true, 100f, 0, "Escama de Dragão", Bonus.ASPD_PCT, 5),
    mob(21, "Salamandra Ígnea", 89, Shape.DRAGON, 0xFFD32F2F, 0xFFFFEB3B, 24f, true, 85f, F_NOWINGS, "Língua de Fogo", Bonus.MATK_PCT, 8),
    mob(22, "Wyvern", 95, Shape.DRAGON, 0xFF2E7D32, 0xFFA5D6A7, 30f, true, 110f, 0, "Asa de Wyvern", Bonus.CRIT, 8),
    mob(23, "Dragão Ancião", 99, Shape.DRAGON, 0xFF4E342E, 0xFFFFB300, 52f, true, 85f, F_HORNS, "Coração de Dragão", Bonus.ALL, 5, boss = true),
    // Campo de Aprendizes (todos passivos e fracos)
    mob(24, "Gotinha de Orvalho", 1, Shape.BLOB, 0xFF81D4FA, 0xFF4FC3F7, 13f, false, 40f, 0, "Gota Cristalina", Bonus.SP_PCT, 3, hpM = 0.6f, atkM = 0.35f, expM = 2f),
    mob(25, "Filhote de Coelho", 2, Shape.QUAD, 0xFFD7B899, 0xFFF8BBD0, 12f, false, 70f, F_EARS, "Tufo de Pelo", Bonus.LUK, 2, hpM = 0.7f, atkM = 0.35f, expM = 2f),
    mob(26, "Cogumelo Sonolento", 4, Shape.MUSHROOM, 0xFFFFA726, 0xFFFFF3E0, 14f, false, 30f, 0, "Chapéu de Cogumelo", Bonus.VIT, 2, hpM = 0.8f, atkM = 0.4f, expM = 1.8f),
)

// ---------- Mapas ----------
class MapDef(
    val id: Int, val nome: String, val faixa: String, val w: Float, val h: Float,
    val g1: Int, val g2: Int, val theme: Int, val mobs: IntArray, val boss: Int, val maxMobs: Int
)

val MAPS = arrayOf(
    MapDef(0, "Vila Aurora", "Cidade", 1536f, 1024f, Style.SAND, Style.SAND_DARK, 0, intArrayOf(), -1, 0),
    MapDef(1, "Campo de Aprendizes", "Nv 1-8", 1672f, 941f, col(0xFF7CB342), col(0xFF75AA3E), 1, intArrayOf(24, 25, 26), -1, 12),
    MapDef(2, "Campos de Aurora", "Nv 6-15", 2400f, 1600f, Style.GRASS, col(0xFF86A24F), 1, intArrayOf(0, 1, 2), 3, 18),
    MapDef(3, "Bosque Sussurrante", "Nv 12-28", 2400f, 1600f, col(0xFF3D7A25), col(0xFF387221), 2, intArrayOf(4, 5, 6), 7, 18),
    MapDef(4, "Dunas de Sálix", "Nv 26-46", 2400f, 1600f, col(0xFFE6C88A), col(0xFFDFC07F), 3, intArrayOf(8, 9, 10), 11, 18),
    MapDef(5, "Picos Gelados", "Nv 42-65", 2400f, 1600f, col(0xFFE3EEF5), col(0xFFD9E7F0), 4, intArrayOf(12, 13, 14), 15, 16),
    MapDef(6, "Ruínas Sombrias", "Nv 60-86", 2400f, 1600f, col(0xFF4E4A5A), col(0xFF484454), 5, intArrayOf(16, 17, 18), 19, 16),
    MapDef(7, "Covil do Dragão", "Nv 80-99", 2400f, 1600f, col(0xFF4A2E26), col(0xFF422820), 6, intArrayOf(20, 21, 22), 23, 15),
)

// ---------- Equipamentos ----------
val EQ_LV = intArrayOf(1, 12, 25, 40, 55, 70, 85)
val WEAPON_ATK = intArrayOf(10, 28, 55, 90, 135, 185, 245)
val WEAPON_PRICE = intArrayOf(0, 800, 4000, 15000, 45000, 120000, 300000)
val ARMOR_DEF = intArrayOf(2, 8, 16, 27, 40, 55, 72)
val ARMOR_PRICE = intArrayOf(0, 600, 3000, 12000, 38000, 100000, 260000)

val WEAPON_NAMES = arrayOf(
    arrayOf("Faca Enferrujada", "Adaga", "Punhal Afiado", "Estilete Élfico", "Adaga de Prata", "Presa Sombria", "Lâmina do Crepúsculo"),
    arrayOf("Espada de Treino", "Espada Curta", "Espada Larga", "Montante", "Lâmina Rúnica", "Espada Flamejante", "Matadora de Dragões"),
    arrayOf("Vara de Madeira", "Cajado de Carvalho", "Cajado Arcano", "Cetro de Cristal", "Cajado Rúnico", "Cetro Abissal", "Cajado do Cosmos"),
    arrayOf("Arco Simples", "Arco Curto", "Arco Composto", "Arco Longo", "Arco Élfico", "Arco Tempestade", "Arco do Falcão Celeste"),
)
val ARMOR_NAMES = arrayOf(
    "Roupa de Algodão", "Couraça de Couro", "Gibão Reforçado", "Cota de Malha",
    "Armadura de Placas", "Armadura Rúnica", "Armadura Dracônica"
)

val REFINE_CHANCE = intArrayOf(100, 100, 100, 100, 60, 50, 40, 30, 20, 10)
fun refineCost(tier: Int, ref: Int) = (tier + 1) * (tier + 1) * 80 * (ref + 1)

// ---------- Consumíveis ----------
class PotionDef(val nome: String, val hp: Int, val sp: Int, val preco: Int, val cor: Int)

val POTIONS = arrayOf(
    PotionDef("Poção Pequena", 60, 0, 30, col(0xFFE53935)),
    PotionDef("Poção Média", 250, 0, 160, col(0xFFFF9800)),
    PotionDef("Poção Grande", 800, 0, 700, col(0xFFFFEE58)),
    PotionDef("Poção de Mana", 0, 120, 450, col(0xFF42A5F5)),
)

fun potionTierFor(lv: Int) = if (lv < 25) 0 else if (lv < 60) 1 else 2

// ---------- Curvas de EXP ----------
fun expNeed(lv: Int): Long = ((5 + 3.2 * lv.toDouble().pow(1.5)) * (10 + lv * 2.0)).toLong()

fun jobNeed(jlv: Int, job: Job): Long =
    if (job == Job.APRENDIZ) (expNeed(jlv) * 0.75).toLong()
    else (expNeed(max(1, (jlv * 1.8).toInt())) * 0.75).toLong()
