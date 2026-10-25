package com.example.headphoneanim

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.sin

class MainActivity : Activity() {

    private lateinit var cardContainer: LinearLayout
    private lateinit var waveView: HeadphoneAnimationView
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var testBtn: Button
    private var overlayBtn: Button? = null

    private val handler = Handler(Looper.getMainLooper())
    private val autoCloseRunnable = Runnable { animateOutAndFinish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 101)
        }

        val serviceIntent = Intent(this, HeadphoneService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setupUI()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handleIntent(intent)
        }
    }

    private fun setupUI() {
        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#66000000"))
            setOnClickListener { animateOutAndFinish() }
        }

        cardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(60, 60, 60, 60)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 72f
                setColor(Color.parseColor("#181824"))
                setStroke(3, Color.parseColor("#3300E5FF"))
            }
            isClickable = true
        }

        waveView = HeadphoneAnimationView(this)
        val waveParams = LinearLayout.LayoutParams(420, 420)
        cardContainer.addView(waveView, waveParams)

        titleText = TextView(this).apply {
            text = "Наушники подключены"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 8)
        }
        cardContainer.addView(titleText)

        subtitleText = TextView(this).apply {
            text = "Фоновый мониторинг активен"
            textSize = 14f
            setTextColor(Color.parseColor("#9E9EBA"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 36)
        }
        cardContainer.addView(subtitleText)

        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 20 }

        testBtn = Button(this).apply {
            text = "Тест анимации"
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply {
                cornerRadius = 40f
                setColor(Color.parseColor("#00E5FF"))
            }
            setOnClickListener {
                // При нажатии на тест имитируем реальное подключение (скрываем кнопку и закрываем через 4 сек)
                playConnectAnimation("TWS Наушники", "Аудиовыход готов к работе", true)
            }
        }
        cardContainer.addView(testBtn, btnParams)

        if (!Settings.canDrawOverlays(this)) {
            overlayBtn = Button(this).apply {
                text = "Разрешить показ поверх окон"
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = 40f
                    setColor(Color.parseColor("#2A2A3C"))
                }
                setOnClickListener {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
            cardContainer.addView(overlayBtn, btnParams)
        }

        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ).apply {
            setMargins(40, 40, 40, 60)
        }

        rootLayout.addView(cardContainer, cardParams)
        setContentView(rootLayout)
    }

    private fun handleIntent(intent: Intent) {
        val autoTriggered = intent.getBooleanExtra("auto_triggered", false)
        val deviceName = intent.getStringExtra("device_name") ?: "Наушники"
        val deviceType = intent.getStringExtra("device_type")
            ?: if (autoTriggered) "Аудиоустройство активно" else "Фоновый мониторинг активен"

        playConnectAnimation(deviceName, deviceType, autoTriggered)
    }

    private fun playConnectAnimation(name: String, type: String, autoClose: Boolean) {
        handler.removeCallbacks(autoCloseRunnable)
        titleText.text = name
        subtitleText.text = type

        // Если окно вызвано подключением наушников — прячем все кнопки
        if (autoClose) {
            testBtn.visibility = View.GONE
            overlayBtn?.visibility = View.GONE
        } else {
            testBtn.visibility = View.VISIBLE
            overlayBtn?.visibility = if (!Settings.canDrawOverlays(this)) View.VISIBLE else View.GONE
        }

        cardContainer.translationY = 900f
        cardContainer.alpha = 0f
        cardContainer.scaleX = 0.85f
        cardContainer.scaleY = 0.85f

        val slideUp = ObjectAnimator.ofFloat(cardContainer, View.TRANSLATION_Y, 900f, 0f)
        val fadeIn = ObjectAnimator.ofFloat(cardContainer, View.ALPHA, 0f, 1f)
        val scaleX = ObjectAnimator.ofFloat(cardContainer, View.SCALE_X, 0.85f, 1f)
        val scaleY = ObjectAnimator.ofFloat(cardContainer, View.SCALE_Y, 0.85f, 1f)

        AnimatorSet().apply {
            playTogether(slideUp, fadeIn, scaleX, scaleY)
            duration = 650
            interpolator = OvershootInterpolator(1.15f)
            start()
        }

        waveView.startPulse()

        if (autoClose) {
            handler.postDelayed(autoCloseRunnable, 4000)
        }
    }

    private fun animateOutAndFinish() {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(cardContainer, View.TRANSLATION_Y, 0f, 900f),
                ObjectAnimator.ofFloat(cardContainer, View.ALPHA, 1f, 0f)
            )
            duration = 300
            interpolator = DecelerateInterpolator()
            start()
        }
        handler.postDelayed({ finish()
