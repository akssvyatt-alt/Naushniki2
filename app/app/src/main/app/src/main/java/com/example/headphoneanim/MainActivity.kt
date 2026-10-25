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
    private val handler = Handler(Looper.getMainLooper())
    private val autoCloseRunnable = Runnable { animateOutAndFinish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Запрос разрешения на уведомления для Android 13+
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 101)
        }

        // Запуск фонового сервиса отслеживания наушников
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
            handleIntent(intent)
        }
    }

    private fun setupUI() {
        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#66000000"))
            setOnClickListener { animateOutAndFinish() }
        }

        // Всплывающая карточка в нижней части экрана
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

        // Кнопка теста анимации
        val testBtn = Button(this).apply {
            text = "Тест анимации"
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply {
                cornerRadius = 40f
                setColor(Color.parseColor("#00E5FF"))
            }
            setOnClickListener {
                playConnectAnimation("TWS / Проводные наушники", "Аудиовыход готов к работе", false)
            }
        }
        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 20 }
        cardContainer.addView(testBtn, btnParams)

        // Кнопка выдачи разрешения поверх других окон (нужно для автозапуска из фона на Android 10+)
        if (!Settings.canDrawOverlays(this)) {
            val overlayBtn = Button(this).apply {
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
        val deviceName = intent.getStringExtra("device_name") ?: "Наушники"
        val deviceType = intent.getStringExtra("device_type") ?: "Аудиоустройство активно"
        val autoTriggered = intent.getBooleanExtra("auto_triggered", false)

        playConnectAnimation(deviceName, deviceType, autoTriggered)
    }

    private fun playConnectAnimation(name: String, type: String, autoClose: Boolean) {
        handler.removeCallbacks(autoCloseRunnable)
        titleText.text = name
        subtitleText.text = type

        // Плавный выезд карточки снизу с пружинным эффектом (Overshoot)
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
            handler.postDelayed(autoCloseRunnable, 4200)
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
        handler.postDelayed({ finish() }, 300)
    }
}

// Кастомный View для отрисовки плавной векторной анимации наушников и звуковых волн
class HeadphoneAnimationView(context: Context) : View(context) {

    private var phase = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headbandRect = RectF()
    private val leftCupRect = RectF()
    private val rightCupRect = RectF()

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        interpolator = null // Линейная фаза для непрерывных волн
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    fun startPulse() {
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = width * 0.45f

        // 1. Рисуем 3 расходящихся неоновых кольца (звуковые волны)
        for (i in 0..2) {
            val waveProgress = (phase + i / 3f) % 1f
            val radius = maxRadius * 0.35f + (maxRadius * 0.65f * waveProgress)
            val alpha = ((1f - waveProgress) * 160).toInt().coerceIn(0, 255)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f * (1f - waveProgress * 0.5f)
            paint.color = Color.argb(alpha, 0, 229, 255)
            canvas.drawCircle(cx, cy, radius, paint)
        }

        // 2. Центральный светящийся круг
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#2200E5FF")
        canvas.drawCircle(cx, cy, maxRadius * 0.42f, paint)

        // 3. Плавная левитация иконки наушников по синусоиде
        val offsetY = sin(phase * Math.PI * 2).toFloat() * 10f
        canvas.save()
        canvas.translate(0f, offsetY)

        // Дужка наушников
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 16f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.WHITE
        val arcSize = maxRadius * 0.28f
        headbandRect.set(cx - arcSize, cy - arcSize, cx + arcSize, cy + arcSize)
        canvas.drawArc(headbandRect, 180f, 180f, false, paint)

        // Амбушюры (чашки наушников)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#00E5FF")
        val cupW = 26f
        val cupH = 54f
        leftCupRect.set(cx - arcSize - cupW / 2, cy - 10f, cx - arcSize + cupW / 2, cy + cupH)
        rightCupRect.set(cx + arcSize - cupW / 2, cy - 10f, cx + arcSize + cupW / 2, cy + cupH)
        canvas.drawRoundRect(leftCupRect, 14f, 14f, paint)
        canvas.drawRoundRect(rightCupRect, 14f, 14f, paint)

        canvas.restore()
    }
}

// Фоновый сервис, который мгновенно реагирует на подключение проводных и Bluetooth наушников
class HeadphoneService : Service() {

    private lateinit var audioManager: AudioManager
    private var isReady = false

    private val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            // Пропускаем первичную инициализацию при старте сервиса
            if (!isReady || addedDevices == null) return

            for (device in addedDevices) {
                if (!device.isSink) continue
                val typeDesc = when (device.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Проводные наушники (3.5 мм)"
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_DEVICE -> "USB-C наушники / ЦАП"
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    26 -> "Bluetooth-гарнитура подключена" // 26 = TYPE_BLE_HEADSET
                    else -> null
                }

                if (typeDesc != null) {
                    val name = device.productName?.toString()?.takeIf { it.isNotBlank() }
                        ?: "Наушники подключены"
                    launchPopup(name, typeDesc)
                    break
                }
            }
        }
    }

    private fun launchPopup(name: String, type: String) {
        val popupIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("device_name", name)
            putExtra("device_type", type)
            putExtra("auto_triggered", true)
        }
        startActivity(popupIntent)
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.registerAudioDeviceCallback(audioCallback, Handler(Looper.getMainLooper()))

        // Включаем реакцию на новые подключения через 1 секунду после старта сервиса
        Handler(Looper.getMainLooper()).postDelayed({ isReady = true }, 1000)
    }

    private fun startForegroundNotification() {
        val channelId = "headphone_monitor_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Мониторинг наушников",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }
            .setContentTitle("Headphone Popup активен")
            .setContentText("Ожидание подключения наушников...")
            .setSmallIcon(android.R.drawable.stat_sys_headset)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioManager.unregisterAudioDeviceCallback(audioCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
