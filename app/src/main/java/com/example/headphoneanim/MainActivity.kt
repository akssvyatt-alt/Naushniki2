package com.example.headphoneanim

import android.annotation.SuppressLint
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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

        // Запрашиваем разрешение на уведомления и чтение имени Bluetooth-устройств
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            permissionsToRequest.add("android.permission.POST_NOTIFICATIONS")
        }
        if (Build.VERSION.SDK_INT >= 31) {
            permissionsToRequest.add("android.permission.BLUETOOTH_CONNECT")
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest.toTypedArray(), 101)
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
            text = "Мониторинг наушников"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 8)
        }
        cardContainer.addView(titleText)

        subtitleText = TextView(this).apply {
            text = "Сервис готов к работе"
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
                playConnectAnimation("Тестовые наушники", "Проверка всплывающего окна", true)
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
        handler.postDelayed({ finish() }, 300)
    }
}

class HeadphoneAnimationView(context: Context) : View(context) {

    private var phase = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headbandRect = RectF()
    private val leftCupRect = RectF()
    private val rightCupRect = RectF()

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        interpolator = null
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

        for (i in 0..2) {
            val waveProgress = (phase + i / 3f) % 1f
            val radius = maxRadius * 0.35f + (maxRadius * 0.65f * waveProgress)
            val alpha = ((1f - waveProgress) * 160).toInt().coerceIn(0, 255)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f * (1f - waveProgress * 0.5f)
            paint.color = Color.argb(alpha, 0, 229, 255)
            canvas.drawCircle(cx, cy, radius, paint)
        }

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#2200E5FF")
        canvas.drawCircle(cx, cy, maxRadius * 0.42f, paint)

        val offsetY = sin(phase * Math.PI * 2).toFloat() * 10f
        canvas.save()
        canvas.translate(0f, offsetY)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 16f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.WHITE
        val arcSize = maxRadius * 0.28f
        headbandRect.set(cx - arcSize, cy - arcSize, cx + arcSize, cy + arcSize)
        canvas.drawArc(headbandRect, 180f, 180f, false, paint)

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

class HeadphoneService : Service() {

    private lateinit var audioManager: AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isReady = false
    private var lastTriggerTime = 0L
    private var lastConnectedBtName: String? = null

    // Слушатель прямых событий подключения Bluetooth, который ловит оригинальное имя наушников
    private val btReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
                intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            if (device != null && hasBtPermission()) {
                val name = device.alias ?: device.name
                if (!name.isNullOrBlank() && !isSystemBoardName(name)) {
                    lastConnectedBtName = name
                }
            }
        }
    }

    private val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            if (!isReady || addedDevices == null) return

            for (device in addedDevices) {
                if (!device.isSink) continue

                val isWired = device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                val isUsb = device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                        device.type == AudioDeviceInfo.TYPE_USB_DEVICE
                val isBluetooth = device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        device.type == 26 || device.type == 27

                if (!isWired && !isUsb && !isBluetooth) continue

                // Защита от двойного срабатывания (когда одновременно подключаются SCO и A2DP каналы)
                val now = System.currentTimeMillis()
                if (now - lastTriggerTime < 2500L) return
                lastTriggerTime = now

                val typeDesc = when {
                    isWired -> "Проводные наушники (3.5 мм)"
                    isUsb -> "USB-C аудиоустройство"
                    else -> "Bluetooth подключен"
                }

                // Ждем 500 мс, чтобы профиль A2DP окончательно инициализировал настоящее имя модели
                mainHandler.postDelayed({
                    findRealHeadphoneName(isWired, isUsb) { realName ->
                        launchPopup(realName, typeDesc)
                    }
                }, 500)

                break
            }
        }
    }

    private fun isSystemBoardName(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.isEmpty() ||
                trimmed.equals(Build.MODEL, ignoreCase = true) ||
                trimmed.equals(Build.DEVICE, ignoreCase = true) ||
                trimmed.equals(Build.PRODUCT, ignoreCase = true) ||
                trimmed.equals("I2405", ignoreCase = true) ||
                trimmed.matches(Regex("^[IV]\\d{4}[A-Z]?$", RegexOption.IGNORE_CASE))
    }

    private fun hasBtPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            checkSelfPermission("android.permission.BLUETOOTH_CONNECT") == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    private fun findRealHeadphoneName(isWired: Boolean, isUsb: Boolean, callback: (String) -> Unit) {
        // 1. Сканируем все активные выходные аудиоустройства и ищем то, где имя НЕ равно I2405
        val allOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (dev in allOutputs) {
            val validType = dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    dev.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    dev.type == 26 || dev.type == 27
            if (validType) {
                val candidate = dev.productName?.toString()?.trim() ?: ""
                if (!isSystemBoardName(candidate)) {
                    callback(candidate)
                    return
                }
            }
        }

        // 2. Если это Bluetooth и AudioManager все равно отдал I2405, берем имя из последнего события BluetoothDevice
        if (!isWired && !isUsb) {
            if (!lastConnectedBtName.isNullOrBlank()) {
                callback(lastConnectedBtName!!)
                return
            }

            // 3. Опрашиваем напрямую системный профиль Bluetooth A2DP
            if (hasBtPermission()) {
                val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter = btManager?.adapter
                if (adapter != null && adapter.isEnabled) {
                    adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                            val connected = proxy.connectedDevices
                            val btName = connected.firstOrNull()?.let { it.alias ?: it.name }
                            adapter.closeProfileProxy(profile, proxy)

                            if (!btName.isNullOrBlank() && !isSystemBoardName(btName)) {
                                callback(btName)
                            } else {
                                callback("Беспроводные наушники")
                            }
                        }

                        override fun onServiceDisconnected(profile: Int) {}
                    }, BluetoothProfile.A2DP)
                    return
                }
            }
        }

        // 4. Резервный вариант для обычных аналоговых 3.5 мм наушников (у которых физически нет чипа с именем)
        val fallback = when {
            isWired -> "Проводные наушники"
            isUsb -> "USB-C наушники"
            else -> "Беспроводные наушники"
        }
        callback(fallback)
    }

    private fun launchPopup(name: String, type: String) {
        val popupIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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
        audioManager.registerAudioDeviceCallback(audioCallback, mainHandler)

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(btReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(btReceiver, filter)
        }

        mainHandler.postDelayed({ isReady = true }, 1000)
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
        try {
            unregisterReceiver(btReceiver)
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
