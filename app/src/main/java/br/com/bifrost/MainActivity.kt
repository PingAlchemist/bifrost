package br.com.bifrost

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var game: Game
    private lateinit var view: GameView
    private lateinit var audio: Audio
    private val prefs by lazy { getSharedPreferences("bifrost", MODE_PRIVATE) }

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
        prefs.getString("save", null)?.let { s ->
            runCatching { game.p.fromJson(JSONObject(s)) }
        }
        game.onSave = { save() }
        game.onVibrate = { ms -> vibrate(ms) }
        audio = Audio(this)
        audio.setMusic(game.p.musica)
        game.sfx = { id, rate -> audio.play(id, rate) }
        game.onMapChange = { id -> audio.music(Audio.trackFor(id)) }
        game.onMusicSetting = { audio.setMusic(game.p.musica) }
        game.start()
        view = GameView(this, game)
        setContentView(view)
        hideBars()
    }

    override fun onResume() {
        super.onResume()
        hideBars()
        val now = System.currentTimeMillis()
        val last = game.p.lastTime
        if (last > 0L && now > last) game.applyOffline((now - last) / 1000)
        game.p.lastTime = now
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
        game.p.lastTime = System.currentTimeMillis()
        prefs.edit().putString("save", game.p.toJson().toString()).apply()
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
        if (game.panel != Panel.NONE && game.panel != Panel.MORTE) game.panel = Panel.NONE
        else @Suppress("DEPRECATION") super.onBackPressed()
    }
}
