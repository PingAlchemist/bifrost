package br.com.bifrost

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Áudio 100% sintetizado em código (sem arquivos externos):
 * - efeitos: gerados uma vez, gravados em WAV no cache e tocados via SoundPool (baixa latência, sem alocação por frame);
 * - músicas: peças clássicas de domínio público arranjadas em estilo chiptune, renderizadas sob demanda e tocadas em loop.
 */
class Audio(ctx: Context) {
    companion object {
        const val SR = 22050
        const val VERSION = 1

        const val S_SWING = 0
        const val S_ARROW = 1
        const val S_MAGIC = 2
        const val S_MISS = 3
        const val S_HIT_SOFT = 4
        const val S_HIT_FLESH = 5
        const val S_HIT_WOOD = 6
        const val S_HIT_METAL = 7
        const val S_HIT_SPIRIT = 8
        const val S_HIT_SCALE = 9
        const val S_CRIT = 10
        const val S_HURT = 11
        const val S_POP = 12
        const val S_COIN = 13
        const val S_POTION = 14
        const val S_SKILL = 15
        const val S_HEAL = 16
        const val S_LEVELUP = 17
        const val S_JOBUP = 18
        const val S_TELEPORT = 19
        const val S_DEATH = 20
        const val S_CARD = 21
        const val N_SFX = 22

        const val T_MINUET = 0
        const val T_ODE = 1
        const val T_SERENATA = 2
        const val T_TURCA = 3
        const val T_GRIEG = 4

        /** Música por mapa. */
        fun trackFor(mapId: Int) = when (mapId) {
            0 -> T_MINUET
            1 -> T_ODE
            2, 3 -> T_SERENATA
            4, 5 -> T_TURCA
            else -> T_GRIEG
        }

        /** Material do golpe conforme o monstro. */
        fun hitFor(d: MonsterDef): Int = when (d.shape) {
            Shape.BLOB, Shape.MUSHROOM -> S_HIT_SOFT
            Shape.TREE -> S_HIT_WOOD
            Shape.GHOST -> S_HIT_SPIRIT
            Shape.DRAGON, Shape.SCORPION, Shape.SNAKE -> S_HIT_SCALE
            Shape.HUMANOID -> if (d.has(F_BONES) || d.has(F_HORNS) || (d.has(F_BULKY) && !d.has(F_FUR))) S_HIT_METAL else S_HIT_FLESH
            else -> S_HIT_FLESH
        }

        /** Monstros maiores soam mais graves. */
        fun pitchFor(d: MonsterDef): Float = (1.3f - d.size / 70f).coerceIn(0.7f, 1.3f)
    }

    private val dir = ctx.cacheDir
    private val pool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    private val ids = AtomicIntegerArray(N_SFX)
    private val last = LongArray(N_SFX)
    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var curTrack = -1
    private var wantTrack = -1
    private var musicOn = true
    private var paused = false

    init {
        exec.execute {
            for (i in 0 until N_SFX) {
                runCatching {
                    val f = file("sfx_$i")
                    if (!f.exists()) writeWav(f, Sfx.make(i))
                    ids.set(i, pool.load(f.path, 1))
                }
            }
        }
    }

    private fun file(name: String) = File(dir, "${name}_v$VERSION.wav")

    /** Toca um efeito. Mesmo efeito em menos de 45ms é ignorado (evita estouro em golpes de área). */
    fun play(id: Int, rate: Float = 1f, vol: Float = 1f) {
        if (id !in 0 until N_SFX) return
        val now = SystemClock.uptimeMillis()
        if (now - last[id] < 45) return
        last[id] = now
        val s = ids.get(id)
        if (s != 0) pool.play(s, vol, vol, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun music(track: Int) {
        wantTrack = track
        if (!musicOn || paused) return
        if (track == curTrack && player != null) return
        startTrack(track)
    }

    fun setMusic(on: Boolean) {
        musicOn = on
        if (!on) {
            stopPlayer()
            curTrack = -1
        } else if (wantTrack >= 0) music(wantTrack)
    }

    fun pause() {
        paused = true
        runCatching { player?.pause() }
    }

    fun resume() {
        paused = false
        val p = player
        if (p != null && musicOn) runCatching { p.start() }
        else if (musicOn && wantTrack >= 0) music(wantTrack)
    }

    fun release() {
        stopPlayer()
        runCatching { pool.release() }
        exec.shutdown()
    }

    private fun stopPlayer() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    private fun startTrack(track: Int) {
        stopPlayer()
        curTrack = track
        exec.execute {
            val f = file("music_$track")
            runCatching { if (!f.exists()) writeWav(f, Music.render(track)) }
            main.post {
                if (curTrack != track || !musicOn || paused || player != null || !f.exists()) return@post
                runCatching {
                    val mp = MediaPlayer()
                    mp.setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                    )
                    mp.setDataSource(f.path)
                    mp.isLooping = true
                    mp.setVolume(0.5f, 0.5f)
                    mp.prepare()
                    mp.start()
                    player = mp
                }
            }
        }
    }

    private fun writeWav(f: File, data: FloatArray) {
        val bb = ByteBuffer.allocate(44 + data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + data.size * 2); bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(1.toShort()); bb.putShort(1.toShort())
        bb.putInt(SR); bb.putInt(SR * 2); bb.putShort(2.toShort()); bb.putShort(16.toShort())
        bb.put("data".toByteArray()); bb.putInt(data.size * 2)
        for (v in data) bb.putShort((v.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        val tmp = File(f.path + ".tmp")
        FileOutputStream(tmp).use { it.write(bb.array()) }
        tmp.renameTo(f)
    }
}

// =====================================================================
// Síntese básica
// =====================================================================
private object Synth {
    const val SR = Audio.SR
    const val SINE = 0
    const val SQUARE = 1
    const val TRI = 2
    private val rnd = Random(1234)

    fun buf(sec: Float) = FloatArray((sec * SR).toInt())

    fun osc(w: Int, ph: Double): Float {
        val f = ph - floor(ph)
        return when (w) {
            SINE -> sin(2 * PI * f).toFloat()
            SQUARE -> if (f < 0.5) 0.8f else -0.8f
            else -> (if (f < 0.5) 4 * f - 1 else 3 - 4 * f).toFloat()
        }
    }

    fun tone(
        b: FloatArray, t0: Float, dur: Float, f0: Float, f1: Float, vol: Float, w: Int = SINE,
        decay: Float = 6f, attack: Float = 0.004f, vib: Float = 0f, vibHz: Float = 6f
    ) {
        val s0 = (t0 * SR).toInt()
        val n = (dur * SR).toInt()
        var ph = 0.0
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= b.size) break
            val t = i.toFloat() / SR
            val p = i.toFloat() / n
            var f = f0 + (f1 - f0) * p
            if (vib > 0f) f *= 1f + vib * sin(2 * PI * vibHz * t).toFloat()
            ph += f / SR
            val a = if (t < attack) t / attack else 1f
            val rel = if (p > 0.9f) (1f - p) * 10f else 1f
            b[idx] += osc(w, ph) * a * exp(-decay * t) * rel * vol
        }
    }

    /** Ruído filtrado (passa-baixa de 1 polo com corte variando de lp0 a lp1). */
    fun noise(b: FloatArray, t0: Float, dur: Float, vol: Float, decay: Float, lp0: Float, lp1: Float, attack: Float = 0.002f) {
        val s0 = (t0 * SR).toInt()
        val n = (dur * SR).toInt()
        var y = 0f
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= b.size) break
            val t = i.toFloat() / SR
            val p = i.toFloat() / n
            y += (lp0 + (lp1 - lp0) * p) * ((rnd.nextFloat() * 2f - 1f) - y)
            val e = (if (t < attack) t / attack else 1f) * exp(-decay * t) * (if (p > 0.85f) (1f - p) / 0.15f else 1f)
            b[idx] += y * e * vol
        }
    }

    /** Corda dedilhada (Karplus-Strong). */
    fun pluck(b: FloatArray, t0: Float, freq: Float, dur: Float, vol: Float) {
        val n = (SR / freq).toInt().coerceAtLeast(2)
        val ring = FloatArray(n) { rnd.nextFloat() * 2f - 1f }
        val s0 = (t0 * SR).toInt()
        val len = (dur * SR).toInt()
        var k = 0
        for (i in 0 until len) {
            val idx = s0 + i
            if (idx >= b.size) break
            val nx = (k + 1) % n
            val v = ring[k]
            ring[k] = (ring[k] + ring[nx]) * 0.497f
            b[idx] += v * vol * (1f - i.toFloat() / len)
            k = nx
        }
    }

    fun hz(midi: Int) = (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()

    fun normalize(b: FloatArray, peak: Float): FloatArray {
        var m = 0f
        for (v in b) m = max(m, abs(v))
        if (m > peak) {
            val k = peak / m
            for (i in b.indices) b[i] *= k
        }
        return b
    }
}

// =====================================================================
// Efeitos sonoros
// =====================================================================
private object Sfx {
    private val rnd = Random(99)

    fun make(id: Int): FloatArray = with(Synth) {
        val b: FloatArray
        when (id) {
            Audio.S_SWING -> { b = buf(0.16f); noise(b, 0f, 0.16f, 0.55f, 4f, 0.03f, 0.35f, 0.06f) }
            Audio.S_ARROW -> { b = buf(0.3f); pluck(b, 0f, 196f, 0.28f, 0.55f); noise(b, 0f, 0.12f, 0.18f, 10f, 0.2f, 0.5f) }
            Audio.S_MAGIC -> {
                b = buf(0.26f)
                tone(b, 0f, 0.25f, 1400f, 300f, 0.3f, SINE, 6f, 0.01f, 0.06f, 30f)
                tone(b, 0f, 0.2f, 2100f, 500f, 0.12f, TRI, 8f)
            }
            Audio.S_MISS -> { b = buf(0.12f); noise(b, 0f, 0.12f, 0.3f, 6f, 0.3f, 0.7f, 0.03f) }
            Audio.S_HIT_SOFT -> {
                b = buf(0.18f)
                tone(b, 0f, 0.16f, 280f, 90f, 0.75f, SINE, 16f)
                noise(b, 0f, 0.05f, 0.25f, 40f, 0.08f, 0.05f)
            }
            Audio.S_HIT_FLESH -> {
                b = buf(0.16f)
                tone(b, 0f, 0.14f, 170f, 55f, 0.85f, SINE, 20f)
                noise(b, 0f, 0.06f, 0.5f, 45f, 0.3f, 0.1f)
            }
            Audio.S_HIT_WOOD -> {
                b = buf(0.14f)
                tone(b, 0f, 0.12f, 620f, 600f, 0.5f, SINE, 35f)
                tone(b, 0f, 0.1f, 935f, 920f, 0.35f, SINE, 45f)
                noise(b, 0f, 0.03f, 0.3f, 60f, 0.5f, 0.3f)
            }
            Audio.S_HIT_METAL -> {
                b = buf(0.35f)
                val fs = floatArrayOf(820f, 1290f, 1950f, 2710f)
                for (i in fs.indices) tone(b, 0f, 0.34f, fs[i], fs[i] * 0.995f, 0.22f - i * 0.03f, SINE, 12f + i * 4f)
                noise(b, 0f, 0.03f, 0.3f, 50f, 0.7f, 0.5f)
            }
            Audio.S_HIT_SPIRIT -> {
                b = buf(0.25f)
                noise(b, 0f, 0.24f, 0.35f, 5f, 0.08f, 0.2f, 0.04f)
                tone(b, 0f, 0.22f, 900f, 520f, 0.18f, SINE, 6f, 0.02f, 0.03f, 9f)
            }
            Audio.S_HIT_SCALE -> {
                b = buf(0.12f)
                tone(b, 0f, 0.08f, 220f, 120f, 0.25f, SQUARE, 30f)
                noise(b, 0f, 0.08f, 0.5f, 35f, 0.6f, 0.3f)
            }
            Audio.S_CRIT -> {
                b = buf(0.3f)
                tone(b, 0f, 0.12f, 170f, 55f, 0.7f, SINE, 20f)
                tone(b, 0f, 0.28f, 1800f, 1750f, 0.3f, SINE, 10f)
                tone(b, 0.01f, 0.25f, 2700f, 2650f, 0.18f, SINE, 14f)
                noise(b, 0f, 0.05f, 0.4f, 40f, 0.5f, 0.2f)
            }
            Audio.S_HURT -> {
                b = buf(0.16f)
                tone(b, 0f, 0.14f, 330f, 140f, 0.25f, SQUARE, 12f)
                noise(b, 0f, 0.06f, 0.3f, 30f, 0.2f, 0.1f)
            }
            Audio.S_POP -> {
                b = buf(0.12f)
                tone(b, 0f, 0.1f, 380f, 950f, 0.45f, SINE, 18f)
                noise(b, 0f, 0.03f, 0.2f, 50f, 0.4f, 0.2f)
            }
            Audio.S_COIN -> {
                b = buf(0.3f)
                tone(b, 0f, 0.08f, 1320f, 1320f, 0.2f, SQUARE, 8f)
                tone(b, 0.07f, 0.22f, 1760f, 1760f, 0.2f, SQUARE, 9f)
            }
            Audio.S_POTION -> {
                b = buf(0.36f)
                for (k in 0..6) {
                    val f = 350f + rnd.nextFloat() * 450f
                    tone(b, k * 0.045f, 0.06f, f, f * 1.6f, 0.28f, SINE, 25f)
                }
            }
            Audio.S_SKILL -> {
                b = buf(0.42f)
                val fs = floatArrayOf(880f, 1109f, 1319f, 1760f)
                for (k in fs.indices) tone(b, k * 0.045f, 0.3f, fs[k], fs[k], 0.18f, SINE, 8f)
                noise(b, 0f, 0.35f, 0.06f, 4f, 0.7f, 0.9f, 0.05f)
            }
            Audio.S_HEAL -> {
                b = buf(0.7f)
                val fs = floatArrayOf(659f, 831f, 988f, 1319f)
                for (k in fs.indices) tone(b, k * 0.07f, 0.45f, fs[k], fs[k], 0.17f, SINE, 5f, 0.01f)
            }
            Audio.S_LEVELUP -> {
                b = buf(1.45f)
                val fs = floatArrayOf(523f, 659f, 784f, 1047f)
                for (k in fs.indices) tone(b, k * 0.1f, 0.22f, fs[k], fs[k], 0.3f, TRI, 5f)
                for (f in fs) tone(b, 0.42f, 0.95f, f, f, 0.16f, TRI, 2.5f)
                for (k in 0..7) tone(b, 0.42f + k * 0.06f, 0.12f, 2093f + k * 150f, 2093f + k * 150f, 0.06f, SINE, 20f)
            }
            Audio.S_JOBUP -> {
                b = buf(0.95f)
                val fs = floatArrayOf(784f, 988f, 1175f, 1568f)
                for (k in fs.indices) tone(b, k * 0.07f, 0.2f, fs[k], fs[k], 0.26f, TRI, 6f)
                tone(b, 0.3f, 0.6f, 1568f, 1568f, 0.15f, TRI, 3f)
                tone(b, 0.3f, 0.6f, 1175f, 1175f, 0.12f, TRI, 3f)
            }
            Audio.S_TELEPORT -> {
                b = buf(0.8f)
                tone(b, 0f, 0.75f, 250f, 1500f, 0.28f, SINE, 1.5f, 0.08f, 0.03f, 8f)
                tone(b, 0f, 0.75f, 375f, 2250f, 0.12f, SINE, 1.5f, 0.08f, 0.03f, 8f)
                noise(b, 0f, 0.75f, 0.08f, 1.5f, 0.6f, 0.9f, 0.1f)
            }
            Audio.S_DEATH -> {
                b = buf(1.15f)
                val fs = floatArrayOf(392f, 349f, 294f, 247f)
                for (k in fs.indices) tone(b, k * 0.22f, 0.32f, fs[k], fs[k], 0.3f, TRI, 4f)
            }
            else -> { // S_CARD
                b = buf(0.85f)
                val fs = floatArrayOf(1568f, 2093f, 2637f, 3136f)
                for (k in fs.indices) tone(b, k * 0.06f, 0.5f, fs[k], fs[k], 0.15f, SINE, 6f)
                tone(b, 0.25f, 0.55f, 1047f, 1047f, 0.12f, TRI, 4f)
            }
        }
        normalize(b, 0.9f)
    }
}

// =====================================================================
// Músicas: peças clássicas de domínio público, arranjo chiptune (melodia + baixo + percussão leve)
// =====================================================================
private object Music {
    class N(val midi: Int, val start: Int, val len: Int) // tempo em semicolcheias

    private fun midi(s: String): Int {
        if (s == "R") return -1
        val letter = "C D EF G A B".indexOf(s[0])
        var i = 1
        var acc = 0
        if (s[i] == '#') { acc = 1; i++ } else if (s[i] == 'b') { acc = -1; i++ }
        return 12 * (s.substring(i).toInt() + 1) + letter + acc
    }

    /** "D5:4 G4:2 R:2 ..." (duração em semicolcheias). Retorna o fim. */
    private fun seq(s: String, start: Int, out: ArrayList<N>, tr: Int = 0): Int {
        var t = start
        for (tok in s.trim().split(Regex("\\s+"))) {
            val p = tok.split(':')
            val m = midi(p[0])
            val l = p[1].toInt()
            if (m >= 0) out.add(N(m + tr, t, l))
            t += l
        }
        return t
    }

    /** Baixo: uma fundamental por compasso, tocada no padrão de intervalos [pat] a cada [step]. */
    private fun bass(roots: String, barLen: Int, pat: IntArray, step: Int, start: Int, out: ArrayList<N>): Int {
        var t = start
        for (r in roots.trim().split(Regex("\\s+"))) {
            val m = midi(r)
            var k = 0
            while (k * step < barLen) {
                out.add(N(m + pat[k % pat.size], t + k * step, step))
                k++
            }
            t += barLen
        }
        return t
    }

    fun render(track: Int): FloatArray {
        val mel = ArrayList<N>()
        val bas = ArrayList<N>()
        var bpm = 132f
        var len: Int
        var pluck = false
        var kickEvery = 4
        var hatEvery = 2
        when (track) {
            Audio.T_MINUET -> { // Minueto em Sol (Petzold), 3/4
                val a = "D5:4 G4:2 A4:2 B4:2 C5:2 D5:4 G4:4 G4:4 E5:4 C5:2 D5:2 E5:2 F#5:2 G5:4 G4:4 G4:4 " +
                        "C5:4 D5:2 C5:2 B4:2 A4:2 B4:4 C5:2 B4:2 A4:2 G4:2 "
                val m = a + "F#4:4 G4:2 A4:2 B4:2 G4:2 A4:12 " + a + "A4:4 B4:2 A4:2 G4:2 F#4:2 G4:12"
                len = seq(m, 0, mel)
                bass("G2 G2 C3 G2 C3 G2 D3 D3 G2 G2 C3 G2 C3 G2 D3 G2", 12, intArrayOf(0, 7, 12), 4, 0, bas)
                bpm = 138f; kickEvery = 12; hatEvery = 4
            }
            Audio.T_ODE -> { // Ode à Alegria (Beethoven), 4/4
                val a = "E4:4 E4:4 F4:4 G4:4 G4:4 F4:4 E4:4 D4:4 C4:4 C4:4 D4:4 E4:4 "
                val a1 = a + "E4:6 D4:2 D4:8 "
                val a2 = a + "D4:6 C4:2 C4:8 "
                val b = "D4:4 D4:4 E4:4 C4:4 D4:4 E4:2 F4:2 E4:4 C4:4 D4:4 E4:2 F4:2 E4:4 D4:4 C4:4 D4:4 G3:8 "
                len = seq(a1 + a2 + b + a2, 0, mel, 12)
                bass("C3 G2 C3 G2 C3 G2 C3 C3 G2 C3 G2 G2 C3 G2 C3 C3", 16, intArrayOf(0, 7, 12, 7), 4, 0, bas)
                bpm = 160f
            }
            Audio.T_SERENATA -> { // Pequena Serenata Noturna (Mozart), abertura, 4/4
                val s = "G4:4 R:2 D4:2 G4:4 R:2 D4:2 G4:2 D4:2 G4:2 B4:2 D5:8 " +
                        "C5:4 R:2 A4:2 C5:4 R:2 A4:2 C5:2 A4:2 F#4:2 A4:2 D4:8 "
                var t = seq(s, 0, mel)
                t = seq(s, t, mel, 12)
                len = seq("G4:2 B4:2 D5:2 G5:2 F#5:2 E5:2 D5:2 C5:2 B4:2 A4:2 G4:2 F#4:2 G4:8", t, mel)
                bass("G2 G2 D3 D3 G2 G2 D3 D3 D3 G2", 16, intArrayOf(0, 12), 2, 0, bas)
                bpm = 132f
            }
            Audio.T_TURCA -> { // Marcha Turca (Mozart), tema, 2/4
                val pick = "B4:1 A4:1 G#4:1 A4:1 "
                val body = "C5:2 R:2 D5:1 C5:1 B4:1 C5:1 E5:2 R:2 F5:1 E5:1 D#5:1 E5:1 " +
                        "B5:1 A5:1 G#5:1 A5:1 B5:1 A5:1 G#5:1 A5:1 C6:4 A5:2 C6:2 " +
                        "B5:2 A5:2 G#5:2 A5:2 E5:2 F5:2 D5:2 B4:2 C5:4 B4:4 A4:4 "
                var t = 0
                for (pass in 0..1) {
                    val st = t
                    t = seq(pick + body, t, mel)
                    bass("A2 A2 A2 A2 E2 D2 E2 A2", 8, intArrayOf(0, 12), 2, st + 4, bas)
                }
                len = t
                bpm = 126f
            }
            else -> { // Na Gruta do Rei da Montanha (Grieg), 4/4, pizzicato
                val p = "B3:2 C#4:2 D4:2 E4:2 F#4:2 D4:2 F#4:4 F4:2 C#4:2 F4:4 E4:2 C4:2 E4:4 " +
                        "B3:2 C#4:2 D4:2 E4:2 F#4:2 D4:2 F#4:2 B4:2 A4:2 F#4:2 D4:2 F#4:2 A4:8 "
                var t = 0
                val trs = intArrayOf(0, 7, 12, 19)
                for (tr in trs) {
                    val st = t
                    t = seq(p, t, mel, tr)
                    val roots = if (tr % 12 == 0) "B2 F#2 B2 D3" else "F#2 C#3 F#2 A2"
                    bass(roots, 16, intArrayOf(0, 0, 7, 0), 2, st, bas)
                }
                len = t
                bpm = 152f; pluck = true
            }
        }

        val unit = Audio.SR * 60f / bpm / 4f
        val total = (len * unit).toInt()
        val out = FloatArray(total)
        val lead = FloatArray(total)
        for (n in mel) voice(lead, n, unit, true, pluck)
        // suaviza o timbre quadrado da melodia
        var y = 0f
        for (i in lead.indices) { y += 0.55f * (lead[i] - y); lead[i] = y }
        for (n in bas) voice(out, n, unit, false, pluck)
        for (i in out.indices) out[i] += lead[i]
        var u = 0
        while (u < len) {
            val t = u * unit / Audio.SR
            if (u % kickEvery == 0) Synth.tone(out, t, 0.09f, 110f, 45f, 0.2f, Synth.SINE, 25f)
            else if (u % hatEvery == 0) Synth.noise(out, t, 0.03f, 0.05f, 60f, 0.9f, 0.9f)
            u += min(kickEvery, hatEvery)
        }
        for (i in out.indices) {
            val x = out[i]
            out[i] = x / (1f + abs(x) * 0.5f)
        }
        return Synth.normalize(out, 0.85f)
    }

    private fun voice(b: FloatArray, n: N, unit: Float, lead: Boolean, pluck: Boolean) {
        val s0 = (n.start * unit).toInt()
        val ln = (n.len * unit).toInt()
        val f = Synth.hz(n.midi)
        val vol = if (lead) 0.22f else 0.26f
        val rel = min((0.05f * Audio.SR).toInt(), max(1, ln / 3))
        var ph = 0.0
        for (i in 0 until ln) {
            val idx = s0 + i
            if (idx >= b.size) break
            val t = i.toFloat() / Audio.SR
            val vib = if (lead && t > 0.12f) 1f + 0.004f * sin(2 * PI * 5.5 * t).toFloat() else 1f
            ph += f * vib / Audio.SR
            val fr = ph - floor(ph)
            val s = if (lead) {
                (if (fr < 0.3) 0.6f else -0.6f) + 0.4f * sin(2 * PI * fr).toFloat()
            } else (if (fr < 0.5) 4 * fr - 1 else 3 - 4 * fr).toFloat()
            var e = if (i < 60) i / 60f else 1f
            e *= if (pluck) exp(-7f * t) else 0.65f + 0.35f * exp(-10f * t)
            if (i > ln - rel) e *= (ln - i).toFloat() / rel
            b[idx] += s * e * vol
        }
    }
}
