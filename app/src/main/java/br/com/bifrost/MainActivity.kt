package br.com.bifrost

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.EditText
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var game: Game
    private lateinit var view: GameView
    private lateinit var audio: Audio
    private val prefs by lazy { getSharedPreferences("bifrost", MODE_PRIVATE) }
    /** Slot do personagem em jogo (-1 = na tela de seleção). */
    private var slot = -1

    private fun key(i: Int) = "save$i"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // desenha sob o notch; o HUD se afasta usando as áreas seguras (GameView.onApplyWindowInsets)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        // mapa da floresta (camadas e colisão) antes de o jogo começar a spawnar monstros
        Forest.load(resources)
        game = Game()
        migrate()
        refreshSlots()
        game.selSlot = prefs.getInt("slotAtual", 0).coerceIn(0, SLOTS - 1)
        game.onSave = { save() }
        game.onPlay = { i -> enter(i) }
        game.onCreateChar = { i -> askName(i) }
        game.onDeleteChar = { i -> askDelete(i) }
        game.onSwitchChar = { switchChar() }
        game.onVibrate = { ms -> vibrate(ms) }
        audio = Audio(this)
        audio.setMusic(game.p.musica)
        game.sfx = { id, rate -> audio.play(id, rate) }
        game.onMapChange = { id -> audio.music(Audio.trackFor(id)) }
        game.onMusicSetting = { audio.setMusic(game.p.musica) }
        // o jogo começa na tela de seleção; game.loadChar() entra com o personagem escolhido
        view = GameView(this, game)
        setContentView(view)
        hideBars()
    }

    override fun onResume() {
        super.onResume()
        hideBars()
        if (slot >= 0) applyOffline()
        view.resume()
        audio.resume()
    }

    override fun onPause() {
        super.onPause()
        audio.pause()
        save()
    }

    override fun onDestroy() {
        audio.release()
        super.onDestroy()
    }

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") (getSystemService(VIBRATOR_SERVICE) as? Vibrator)
    }

    private fun vibrate(ms: Long) {
        runCatching {
            val v = vibrator ?: return
            if (v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun save() {
        if (slot < 0) return
        game.p.lastTime = System.currentTimeMillis()
        prefs.edit().putString(key(slot), game.p.toJson().toString()).apply()
    }

    /** Tempo fora conta só para o personagem em jogo (usa o lastTime salvo dele). */
    private fun applyOffline() {
        val now = System.currentTimeMillis()
        val last = game.p.lastTime
        if (last > 0L && now > last) game.applyOffline((now - last) / 1000)
        game.p.lastTime = now
    }

    // ----- personagens (até 3 slots, um save JSON por slot) -----

    /** Save antigo (um personagem só, chave "save") vira o slot 1. */
    private fun migrate() {
        val old = prefs.getString("save", null) ?: return
        val e = prefs.edit()
        if (prefs.getString(key(0), null) == null) e.putString(key(0), old)
        e.putString("save_backup", old).remove("save").putInt("slotAtual", 0).apply()
    }

    private fun refreshSlots() {
        for (i in 0 until SLOTS) {
            game.slots[i] = prefs.getString(key(i), null)?.let { s ->
                runCatching { Player().apply { fromJson(JSONObject(s)) } }.getOrNull()
            }
        }
    }

    private fun enter(i: Int) {
        val o = prefs.getString(key(i), null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        slot = i
        game.selSlot = i
        prefs.edit().putInt("slotAtual", i).apply()
        audio.setMusic(o.optBoolean("musica", true))
        game.loadChar(o)
        view.resetChar()
        applyOffline()
        save()
    }

    private fun switchChar() {
        save()
        slot = -1
        game.panel = Panel.NONE
        game.selecting = true
        audio.stopMusic()
        refreshSlots()
    }

    private fun askName(i: Int) {
        if (game.slots[i] != null) return
        val input = EditText(this).apply {
            hint = "Nome do personagem"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf(InputFilter.LengthFilter(14))
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle("Novo personagem (slot ${i + 1})")
            .setMessage("Todo herói começa como Aprendiz.")
            .setView(input)
            .setPositiveButton("Criar") { _, _ -> createChar(i, input.text.toString()) }
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.setOnShowListener { input.requestFocus() }
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dlg.show()
    }

    private fun createChar(i: Int, nome: String) {
        val n = nome.trim().replace(Regex("\\s+"), " ").ifEmpty { "Herói ${i + 1}" }
        val np = Player().apply { this.nome = n }
        prefs.edit().putString(key(i), np.toJson().toString()).putInt("slotAtual", i).apply()
        game.selSlot = i
        refreshSlots()
    }

    private fun askDelete(i: Int) {
        val sp = game.slots[i] ?: return
        AlertDialog.Builder(this)
            .setTitle("Apagar ${sp.nome}?")
            .setMessage("${sp.job.nome} · Base Lv ${sp.lv} / Job Lv ${sp.jlv}\nTodo o progresso deste personagem será perdido.")
            .setPositiveButton("Apagar") { _, _ ->
                prefs.edit().remove(key(i)).apply()
                refreshSlots()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (game.selecting && !game.title) game.title = true // seleção -> tela inicial
        else if (game.selecting) @Suppress("DEPRECATION") super.onBackPressed()
        else if (game.panel != Panel.NONE && game.panel != Panel.MORTE) game.panel = Panel.NONE
        else @Suppress("DEPRECATION") super.onBackPressed()
    }
}
