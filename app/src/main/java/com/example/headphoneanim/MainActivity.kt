Вот что произошло по каждому пункту и как это исправлено в коде ниже:

1. **Светлая полоса сверху (где часы, скорость сети и батарея):** на скриншоте видно, что темный фон оверлея (`#4D000000`) обрезался ровно под статус-баром, потому что Android по умолчанию не пускает обычные окна в зону выреза камеры (Notch/Cutout). Чтобы избавиться от этой полосы, я убрал ручную покраску фона и включил **системное аппаратное затемнение (`FLAG_DIM_BEHIND`)** + режим `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`. Теперь сам Android плавно и равномерно притемняет абсолютно весь экран целиком, включая статус-бар и навигационную полоску внизу.


2. **Красивая широкая стрелочка со скругленными углами:** прошлая галочка была обычным текстовым символом шрифта, поэтому выглядела узкой и топорной. Теперь она рисуется векторным путем (`Canvas` + `Paint.Cap.ROUND` + `Paint.Join.ROUND`) — это настоящая широкая, пухлая шеврон-стрелка с идеально круглыми краями, расположенная по центру сверху карточки (как в шторках iOS).


3. **Лишняя кнопка «Готово»:** полностью убрана. Теперь карточка выглядит чище и компактнее, а закрывается либо нажатием на широкую стрелку, либо тапом в любое место мимо карточки (причем закрывается с плавной анимацией уезда вниз, а не резким исчезновением).



Скопируй обновленный `MainActivity.kt` кнопкой 📋 в углу блока и замени им код на GitHub (`Ctrl + A` ➔ `Backspace` ➔ `Ctrl + V`):

```kotlin
package com.example.headphoneanim

import android.annotation.SuppressLint
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.*
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
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

// Кастомная широкая стрелочка со скругленными углами и краями (в стиле iOS)
class RoundedChevronButton(context: Context) : View(context) {
    private val density = context.resources.displayMetrics.density

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2F2F7")
        style = Paint.Style.FILL
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E8E93")
        style = Paint.Style.STROKE
        strokeWidth = 3.6f * density
        strokeCap = Paint.Cap.ROUND   // Закругленные кончики линии
        strokeJoin = Paint.Join.ROUND // Закругленный изгиб посередине
    }

    private val arrowPath = Path()
    private val bgRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // Рисуем мягкую капсулу-подложку
        bgRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(bgRect, h / 2f, h / 2f, bgPaint)

        // Рисуем широкую стрелочку вниз по центру
        val cx = w / 2f
        val cy = h / 2f
        val halfSpanX = 8.5f * density // Ширина размаха крыльев стрелки
        val halfSpanY = 3.2f * density // Высота стрелки

        arrowPath.reset()
        arrowPath.moveTo(cx - halfSpanX, cy - halfSpanY)
        arrowPath.lineTo(cx, cy + halfSpanY)
        arrowPath.lineTo(cx + halfSpanX, cy - halfSpanY)

        canvas.drawPath(arrowPath, arrowPaint)
    }
}

object PopupCardBuilder {
    fun createPopupView(
        context: Context,
        name: String,
        type: String,
        useDimBackground: Boolean,
        onClose: () -> Unit
    ): FrameLayout {
        val density = context.resources.displayMetrics.density
        var isClosing = false

        val rootLayout = FrameLayout(context).apply {
            // Для системного оверлея затемнение делает сам WindowManager (чтобы не было светлой полосы статус-бара)
            setBackgroundColor(if (useDimBackground) Color.parseColor("#55000000") else Color.TRANSPARENT)
        }

        val cardContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                (24 * density).toInt(),
                (14 * density).toInt(),
                (24 * density).toInt(),
                (24 * density).toInt()
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 36 * density
                setColor(Color.WHITE)
            }
            elevation = 24 * density
            isClickable = true
        }

        // Плавная анимация уезда карточки вниз перед удалением
        val triggerSmoothClose = {
            if (!isClosing) {
                isClosing = true
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(cardContainer, View.TRANSLATION_Y, cardContainer.translationY, 950f),
                        ObjectAnimator.ofFloat(cardContainer, View.ALPHA, 1f, 0f)
                    )
                    duration = 250
                    interpolator = DecelerateInterpolator()
                    start()
                }
                cardContainer.postDelayed({ onClose() }, 250)
            }
        }

        rootLayout.setOnClickListener { triggerSmoothClose() }

        // Широкая скругленная кнопка-стрелочка сверху по центру карточки
        val chevronBtn = RoundedChevronButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (48 * density).toInt(),
                (28 * density).toInt()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (10 * density).toInt()
            }
            setOnClickListener { triggerSmoothClose() }
        }
        cardContainer.addView(chevronBtn)

        // Заголовок (Модель наушников)
        val titleText = TextView(context).apply {
            text = name
            textSize = 22f
            setTextColor(Color.parseColor("#1C1C1E"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (4 * density).toInt())
        }
        cardContainer.addView(titleText)

        // Подзаголовок
        val subtitleText = TextView(context).apply {
            text = type
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (8 * density).toInt())
        }
        cardContainer.addView(subtitleText)

        // 3D-анимация WebP
        val animationView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val animSize = (225 * density).toInt()
        val animParams = LinearLayout.LayoutParams(animSize, animSize).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        cardContainer.addView(animationView, animParams)

        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ).apply {
            val margin = (16 * density).toInt()
            setMargins(margin, margin, margin, (24 * density).toInt())
        }

        rootLayout.addView(cardContainer, cardParams)

        // Анимация появления карточки снизу
        cardContainer.translationY = 900f
        cardContainer.alpha = 0f
        cardContainer.scaleX = 0.9f
        cardContainer.scaleY = 0.9f

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(cardContainer, View.TRANSLATION_Y, 900f, 0f),
                ObjectAnimator.ofFloat(cardContainer, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(cardContainer, View.SCALE_X, 0.9f, 1f),
                ObjectAnimator.ofFloat(cardContainer, View.SCALE_Y, 0.9f, 1f)
            )
            duration = 550
            interpolator = OvershootInterpolator(1.08f)
            start()
        }

        // Запуск WebP
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(context.resources, R.drawable.vivo_popup)
                val drawable = ImageDecoder.decodeDrawable(source)
                animationView.setImageDrawable(drawable)
                if (drawable is AnimatedImageDrawable) {
                    drawable.repeatCount = 0
                    drawable.start()
                }
            } catch (e: Exception) {
                animationView.setImageResource(R.drawable.vivo_popup)
            }
        } else {
            animationView.setImageResource(R.drawable.vivo_popup)
        }

        return rootLayout
    }
}

class MainActivity : Activity() {

    private lateinit var mainContainer: FrameLayout
    private var overlayBtn: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        setupSettingsScreen()
    }

    override fun onResume() {
        super.onResume()
        overlayBtn?.visibility = if (!Settings.canDrawOverlays(this)) View.VISIBLE else View.GONE
    }

    private fun setupSettingsScreen() {
        val density = resources.displayMetrics.density
        mainContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#F2F2F7"))
        }

        val settingsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt())
        }

        val statusTitle = TextView(this).apply {
            text = "Сервис поп-апа активен"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1C1C1E"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (8 * density).toInt())
        }
        settingsBox.addView(statusTitle)

        val statusSub = TextView(this).apply {
            text = "Разрешите показ поверх окон, чтобы поп-ап появлялся без открытия приложения"
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (28 * density).toInt())
        }
        settingsBox.addView(statusSub)

        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (52 * density).toInt()
        ).apply { bottomMargin = (14 * density).toInt() }

        val testBtn = Button(this).apply {
            text = "Тест белого поп-апа"
            textSize = 16f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(Color.parseColor("#007AFF"))
            }
            setOnClickListener {
                showTestPopupInApp()
            }
        }
        settingsBox.addView(testBtn, btnParams)

        overlayBtn = Button(this).apply {
            text = "Разрешить показ поверх окон"
            textSize = 16f
            isAllCaps = false
            setTextColor(Color.parseColor("#1C1C1E"))
            background = GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(Color.WHITE)
            }
            visibility = if (!Settings.canDrawOverlays(this@MainActivity)) View.VISIBLE else View.GONE
            setOnClickListener {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
        settingsBox.addView(overlayBtn, btnParams)

        mainContainer.addView(
            settingsBox,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        setContentView(mainContainer)
    }

    private fun showTestPopupInApp() {
        var popupView: View? = null
        popupView = PopupCardBuilder.createPopupView(
            this,
            "vivo TWS 5i",
            "Подключено",
            true
        ) {
            popupView?.let { mainContainer.removeView(it) }
        }
        mainContainer.addView(popupView)
    }
}

class HeadphoneService : Service() {

    private lateinit var audioManager: AudioManager
    private lateinit var windowManager: WindowManager
    private var currentOverlayView: View? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isReady = false
    private var lastTriggerTime = 0L
    private var lastConnectedBtName: String? = null

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

                val now = System.currentTimeMillis()
                if (now - lastTriggerTime < 2500L) return
                lastTriggerTime = now

                val typeDesc = when {
                    isWired -> "Проводные наушники"
                    isUsb -> "USB-C аудиоустройство"
                    else -> "Подключено"
                }

                mainHandler.postDelayed({
                    findRealHeadphoneName(isWired, isUsb) { realName ->
                        showSystemOverlayPopup(realName, typeDesc)
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

        if (!isWired && !isUsb) {
            if (!lastConnectedBtName.isNullOrBlank()) {
                callback(lastConnectedBtName!!)
                return
            }

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

        val fallback = when {
            isWired -> "Проводные наушники"
            isUsb -> "USB-C наушники"
            else -> "Беспроводные наушники"
        }
        callback(fallback)
    }

    private fun showSystemOverlayPopup(name: String, type: String) {
        if (!Settings.canDrawOverlays(this)) {
            val popupIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(popupIntent)
            return
        }

        removeCurrentOverlay()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND, // Аппаратное затемнение всего экрана вместе со статус-баром
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            dimAmount = 0.35f // Степень затемнения фона (35%)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val overlayView = PopupCardBuilder.createPopupView(this, name, type, false) {
            removeCurrentOverlay()
        }

        try {
            windowManager.addView(overlayView, params)
            currentOverlayView = overlayView
        } catch (_: Exception) {}
    }

    private fun removeCurrentOverlay() {
        currentOverlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            currentOverlayView = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
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
        removeCurrentOverlay()
        audioManager.unregisterAudioDeviceCallback(audioCallback)
        try {
            unregisterReceiver(btReceiver)
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

```
