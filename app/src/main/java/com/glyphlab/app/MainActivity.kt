package com.glyphlab.app

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Finds out whether an app can drive the Nothing Phone (3a) Lite's single Glyph Light.
 *
 * It tries every way we know of and writes what happened to a log you can copy:
 *  1. Nothing's official Glyph Developer Kit, registering as every supported phone in turn.
 *  2. Android's classic notification light (the Glyph Light is "inspired by" those).
 *  3. A look at the phone: model codes, Nothing's Glyph service, and the LEDs the system lists.
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var logView: TextView
    private val log = StringBuilder()
    private var glyph: GlyphManager? = null
    private var connected = false
    private var sessionOpen = false

    /** Every phone the toolkit knows, by its internal code. The (3a) Lite is not officially listed. */
    private val targets: List<Pair<String, String?>> by lazy {
        listOf(
            "Auto (no target)" to null,
            "Phone (3a) / (3a) Pro" to Glyph.DEVICE_24111,
            "Phone (4a)" to Glyph.DEVICE_25111,
            "Phone (4a) Pro" to Glyph.DEVICE_25111p,
            "Phone (4b)" to Glyph.DEVICE_25131,
            "Phone (3)" to Glyph.DEVICE_23112,
            "Phone (2a)" to Glyph.DEVICE_23111,
            "Phone (2a) Plus" to Glyph.DEVICE_23113,
            "Phone (2)" to Glyph.DEVICE_22111,
            "Phone (1)" to Glyph.DEVICE_20111,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContentView(buildUi())
        describePhone()
        connectGlyph()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { glyph?.turnOff() }
        runCatching { if (sessionOpen) glyph?.closeSession() }
        runCatching { glyph?.unInit() }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- the phone

    private fun describePhone() {
        line("== Phone ==")
        line("Maker: ${Build.MANUFACTURER} · Model: ${Build.MODEL} · Device: ${Build.DEVICE} · Product: ${Build.PRODUCT}")
        line("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        val service = runCatching { packageManager.getPackageInfo("com.nothing.thirdparty", 0) }.getOrNull()
        line("Nothing Glyph service (com.nothing.thirdparty): " + (service?.let { "installed, version ${it.versionName}" } ?: "NOT found"))
        val debug = Settings.Global.getString(contentResolver, "nt_glyph_interface_debug_enable")
        line("Glyph debug mode: " + when (debug) { "1" -> "ON"; null -> "off (never set)"; else -> "off ($debug)" })
        val known = listOf(
            "Phone (1)" to runCatching { Common.is20111() }.getOrDefault(false),
            "Phone (2)" to runCatching { Common.is22111() }.getOrDefault(false),
            "Phone (2a)" to runCatching { Common.is23111() }.getOrDefault(false),
            "Phone (2a) Plus" to runCatching { Common.is23113() }.getOrDefault(false),
            "Phone (3)" to runCatching { Common.is23112() }.getOrDefault(false),
            "Phone (3a)" to runCatching { Common.is24111() }.getOrDefault(false),
            "Phone (4a)" to runCatching { Common.is25111() }.getOrDefault(false),
            "Phone (4a) Pro" to runCatching { Common.is25111p() }.getOrDefault(false),
            "Phone (4b)" to runCatching { Common.is25131() }.getOrDefault(false),
        ).filter { it.second }.map { it.first }
        line("Toolkit recognises this phone as: " + known.ifEmpty { listOf("none of its phones") }.joinToString())
        val leds = runCatching { File("/sys/class/leds").list()?.sorted() }.getOrNull()
        line("LEDs the system lists: " + (leds?.ifEmpty { null }?.joinToString() ?: "can't see any (normal without root)"))
        line("")
    }

    // ---------------------------------------------------------------- Nothing's Glyph toolkit

    private fun connectGlyph() {
        val manager = runCatching { GlyphManager.getInstance(applicationContext) }.getOrElse {
            line("Glyph toolkit failed to start: ${it.javaClass.simpleName}: ${it.message}")
            return
        }
        glyph = manager
        line("Connecting to Nothing's Glyph service…")
        runCatching {
            manager.init(object : GlyphManager.Callback {
                override fun onServiceConnected(name: ComponentName?) {
                    connected = true
                    handler.post { line("Glyph service CONNECTED (${name?.flattenToShortString()})") }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    connected = false
                    sessionOpen = false
                    handler.post { line("Glyph service disconnected") }
                }
            })
        }.onFailure { line("init failed: ${it.javaClass.simpleName}: ${it.message}") }
        handler.postDelayed({ if (!connected) line("No connection after 3 s: Nothing's Glyph service didn't answer.") }, 3_000)
    }

    /** Register as [target], open a session, then light or breathe channel 0 for a few seconds. */
    private fun tryGlyph(label: String, target: String?, breathe: Boolean) {
        val manager = glyph ?: return line("Toolkit not available.")
        if (!connected) return line("[$label] Not connected to the Glyph service yet.")
        line("[$label] " + if (breathe) "breathing channel 0…" else "lighting channel 0…")
        runCatching {
            val ok = if (target == null) manager.register() else manager.register(target)
            line("  register = $ok")
            if (sessionOpen) runCatching { manager.closeSession() }
            manager.openSession()
            sessionOpen = true
            line("  session opened")
            val builder = manager.glyphFrameBuilder.buildChannel(0)
            val frame = if (breathe) builder.buildPeriod(1_200).buildCycles(3).buildInterval(10).build() else builder.build()
            if (breathe) manager.animate(frame) else manager.toggle(frame)
            line("  sent. Did the light on the back turn on? Turning off in 4 s.")
            handler.postDelayed({ runCatching { manager.turnOff() } }, 4_000)
        }.onFailure { line("  FAILED: ${it.javaClass.simpleName}: ${it.message}") }
    }

    private fun glyphOff() {
        runCatching { glyph?.turnOff() }.onFailure { line("turnOff failed: ${it.message}") }
        line("Sent: all off")
    }

    // ---------------------------------------------------------------- Android's notification light

    /** Posts a notification asking for a blinking red light, 5 s from now so the screen can be locked first. */
    private fun tryNotificationLight() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel("led_test", "LED test", NotificationManager.IMPORTANCE_DEFAULT).apply {
            enableLights(true)
            lightColor = Color.RED
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
        line("[Notification light] Lock the screen now; a notification asking for a blinking light arrives in 5 s.")
        handler.postDelayed({
            @Suppress("DEPRECATION")
            val n = Notification.Builder(this, "led_test")
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("Glyph Light Lab")
                .setContentText("Did the light on the back blink?")
                .setLights(Color.RED, 500, 500)
                .setAutoCancel(true)
                .build()
            runCatching { nm.notify(7, n) }
                .onSuccess { handler.post { line("  notification posted (light red, 0.5 s on / 0.5 s off)") } }
                .onFailure { handler.post { line("  couldn't post: ${it.message}") } }
        }, 5_000)
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi(): View {
        val dp = resources.displayMetrics.density
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (48 * dp).toInt(), (20 * dp).toInt(), (32 * dp).toInt())
            setBackgroundColor(Color.BLACK)
        }
        fun text(s: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
        }
        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label; isAllCaps = false; setTextColor(Color.WHITE); textSize = 14f
            background = GradientDrawable().apply { cornerRadius = 22 * dp; setColor(Color.rgb(32, 32, 32)) }
            setOnClickListener { onClick() }
        }
        fun row(vararg views: View) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            views.forEachIndexed { i, v ->
                addView(v, LinearLayout.LayoutParams(0, (44 * dp).toInt(), if (i == 0) 1.4f else 1f).apply {
                    setMargins((3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt())
                })
            }
        }

        column.addView(text("Glyph Light Lab", 26f, bold = true))
        column.addView(text("Tap each test, watch the light on the back of the phone, then copy the log.", 14f, Color.LTGRAY))

        column.addView(text("1 · Before the Glyph tests", 17f, bold = true))
        column.addView(text("Nothing only lets test apps use the Glyph lights in debug mode. Connect the phone to a computer " +
            "with USB debugging on and run:\nadb shell settings put global nt_glyph_interface_debug_enable 1\n" +
            "(It switches itself off after 48 hours.)", 13f, Color.LTGRAY))

        column.addView(text("2 · Nothing's Glyph toolkit", 17f, bold = true))
        column.addView(text("Pretend to be each Nothing phone in turn. Note which ones make the light turn on.", 13f, Color.LTGRAY))
        for ((label, target) in targets) {
            column.addView(row(
                text(label, 13f).apply { setPadding(0, 0, 0, 0) },
                button("Light") { tryGlyph(label, target, breathe = false) },
                button("Breathe") { tryGlyph(label, target, breathe = true) },
            ))
        }
        column.addView(button("All lights off") { glyphOff() }, LinearLayout.LayoutParams(-1, (44 * dp).toInt()).apply {
            topMargin = (6 * dp).toInt()
        })

        column.addView(text("3 · Android's notification light", 17f, bold = true))
        column.addView(text("Tap, lock the screen within 5 seconds, and watch the back.", 13f, Color.LTGRAY))
        column.addView(button("Blink with a notification") { tryNotificationLight() }, LinearLayout.LayoutParams(-1, (44 * dp).toInt()))

        column.addView(text("Log", 17f, bold = true))
        column.addView(button("Copy log") {
            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Glyph Light Lab", log.toString()))
            Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-1, (44 * dp).toInt()))
        logView = text("", 12f, Color.rgb(170, 230, 190)).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        column.addView(logView)
        return ScrollView(this).apply { addView(column); setBackgroundColor(Color.BLACK) }
    }

    private fun line(s: String) {
        val stamp = if (s.isBlank()) "" else SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()) + "  "
        log.append(stamp).append(s).append('\n')
        if (::logView.isInitialized) logView.text = log
    }
}
