package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.data.preferences.AppPreferences
import com.example.translation.LiveSubtitleBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * YouTube, Instagram, TikTok vb. uygulamaların üzerinde sürüklenebilir yüzen alt yazı kutusu (Overlay).
 *
 * ANDROID 12+ KURALLARI:
 * 1. FLAG_NOT_TOUCHABLE kullanılmaz (sürüklenebilir kalır). Sadece FLAG_NOT_FOCUSABLE kullanılır.
 * 2. Window alpha değeri 0.8'i geçmez (alttaki uygulamaların dokunma engeli kuralı - Untrusted Touch).
 * 3. Settings.canDrawOverlays(context) izni olmadan açılmaz.
 */
class FloatingSubtitleView(
    private val context: Context,
    private val preferences: AppPreferences,
    private val onCloseClicked: () -> Unit
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var subtitleTextView: TextView? = null
    private var containerLayout: LinearLayout? = null
    private var backgroundDrawable: GradientDrawable? = null

    private val scope = CoroutineScope(Dispatchers.Main)
    private var observerJob: Job? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (!Settings.canDrawOverlays(context) || overlayView != null) return

        // Android 12+ Dokunma Kuralı Uyumlu Pencere Parametreleri
        val wmParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 180
            // Saydamlık 0.8'i geçmesin (Android 12+ dokunma kısıtlamasını engellemek için)
            alpha = 0.8f
        }

        val root = FrameLayout(context).apply {
            setPadding(8, 8, 8, 8)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 16, 28, 16)
        }
        containerLayout = container

        // Arka plan kutusu (En fazla 0.8f opaklık)
        val bgOpacity = preferences.getBgOpacity().coerceAtMost(0.8f)
        val alphaInt = (bgOpacity * 255).toInt().coerceIn(40, 204)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 32f
            setColor(Color.argb(alphaInt, 18, 24, 38))
            setStroke(2, Color.argb(100, 255, 255, 255))
        }
        backgroundDrawable = bg
        container.background = bg

        // Üst çubuk (Tutacak & Kapatma Butonu)
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dragIndicator = TextView(context).apply {
            text = "•• Video Alt Yazı ••"
            setTextColor(Color.argb(180, 200, 210, 230))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val closeButton = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(12, 4, 12, 4)
            setOnClickListener {
                onCloseClicked()
            }
        }

        header.addView(dragIndicator, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(closeButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        container.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Alt Yazı Metin Alanı (En fazla 2 satır)
        val textView = TextView(context).apply {
            text = "Canlı alt yazı başlatılıyor..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, preferences.getFontSize())
            setTextColor(preferences.getFontColor().toInt())
            maxLines = 2
            setPadding(8, 8, 8, 8)
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        subtitleTextView = textView

        val textParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        container.addView(textView, textParams)

        root.addView(container)
        overlayView = root

        // Sürükleme (Drag & Drop) Dinleyicisi
        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = wmParams.x
                    initialY = wmParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    wmParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    wmParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager.updateViewLayout(root, wmParams)
                    } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(root, wmParams)
            startObservingSubtitles()
        } catch (_: Exception) {
            overlayView = null
        }
    }

    private fun startObservingSubtitles() {
        observerJob?.cancel()
        observerJob = scope.launch {
            LiveSubtitleBuffer.currentSubtitle.collect { text ->
                subtitleTextView?.text = text
            }
        }
    }

    fun updateStyle(fontSizeSp: Float, fontColorHex: Long, opacity: Float) {
        subtitleTextView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
        subtitleTextView?.setTextColor(fontColorHex.toInt())
        val alphaInt = (opacity.coerceAtMost(0.8f) * 255).toInt().coerceIn(40, 204)
        backgroundDrawable?.setColor(Color.argb(alphaInt, 18, 24, 38))
        containerLayout?.invalidate()
    }

    fun hide() {
        observerJob?.cancel()
        observerJob = null
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        overlayView = null
        subtitleTextView = null
        containerLayout = null
    }
}
